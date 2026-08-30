-- 005_playlist_tracks.sql
-- Normalized, ordered tracks inside a cloud playlist.
--
-- IMPORTANT: because Epsilon aggregates music from multiple providers (currently
-- YouTube Music, but extensible to Apple Music / Spotify / local files / etc.),
-- the "song_id" only makes sense in combination with "provider".
--
-- We store enough denormalized metadata (title, artist, album, duration,
-- thumbnail_url) so that a second device can render the playlist without first
-- re-fetching every track from the provider. These are intentionally denormalized
-- and not authoritative — the provider is always the source of truth for the
-- song's actual metadata.
--
-- `set_video_id` is YouTube-Music specific — kept as nullable so other providers
-- don't have to fake a value.

CREATE TABLE IF NOT EXISTS public.playlist_tracks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    playlist_id     UUID NOT NULL REFERENCES public.playlists(id) ON DELETE CASCADE,
    -- Track identity (composite uniqueness within a playlist)
    provider        TEXT NOT NULL DEFAULT 'youtube'
                    CHECK (provider IN ('youtube', 'apple_music', 'spotify', 'local', 'unknown')),
    song_id         TEXT NOT NULL,
    -- Denormalized metadata for offline display
    title           TEXT,
    artist          TEXT,
    album           TEXT,
    duration_ms     INTEGER,
    thumbnail_url   TEXT,
    -- YouTube-specific
    set_video_id    TEXT,
    -- Ordering & timestamps
    position        INTEGER NOT NULL DEFAULT 0,
    added_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Prevent duplicate (provider, song_id) within the same playlist.
    -- If duplicates are desired in the future, drop this constraint and rely on
    -- the explicit `id` PK for stable identity.
    CONSTRAINT playlist_tracks_unique_song UNIQUE (playlist_id, provider, song_id),
    CONSTRAINT playlist_tracks_position_nonneg CHECK (position >= 0)
);

-- Indexes for the most common query patterns
CREATE INDEX IF NOT EXISTS playlist_tracks_playlist_position_idx
    ON public.playlist_tracks (playlist_id, position);
CREATE INDEX IF NOT EXISTS playlist_tracks_playlist_added_idx
    ON public.playlist_tracks (playlist_id, added_at);
CREATE INDEX IF NOT EXISTS playlist_tracks_provider_song_idx
    ON public.playlist_tracks (provider, song_id);

DROP TRIGGER IF EXISTS playlist_tracks_set_updated_at ON public.playlist_tracks;
CREATE TRIGGER playlist_tracks_set_updated_at
    BEFORE UPDATE ON public.playlist_tracks
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

-- Re-pack positions to be gapless after a delete.
CREATE OR REPLACE FUNCTION public.repack_playlist_positions()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE public.playlist_tracks
       SET position = sub.new_position
      FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY playlist_id ORDER BY position, added_at) - 1 AS new_position
        FROM public.playlist_tracks
        WHERE playlist_id = COALESCE(NEW.playlist_id, OLD.playlist_id)
      ) sub
     WHERE playlist_tracks.id = sub.id
       AND playlist_tracks.position <> sub.new_position;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS playlist_tracks_repack_after_delete ON public.playlist_tracks;
CREATE TRIGGER playlist_tracks_repack_after_delete
    AFTER DELETE ON public.playlist_tracks
    FOR EACH STATEMENT
    EXECUTE FUNCTION public.repack_playlist_positions();

-- =====================================================================
-- RLS: a track is accessible iff its parent playlist is accessible.
-- We use a SECURITY DEFINER helper to keep policy expressions simple.
-- =====================================================================
ALTER TABLE public.playlist_tracks ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "playlist_tracks_select" ON public.playlist_tracks;
CREATE POLICY "playlist_tracks_select"
    ON public.playlist_tracks FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.playlists p
            WHERE p.id = playlist_tracks.playlist_id
              AND (
                p.user_id = auth.uid()
                OR (p.is_public = true AND p.deleted_at IS NULL)
              )
        )
    );

DROP POLICY IF EXISTS "playlist_tracks_owner_insert" ON public.playlist_tracks;
CREATE POLICY "playlist_tracks_owner_insert"
    ON public.playlist_tracks FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.playlists p
            WHERE p.id = playlist_tracks.playlist_id
              AND p.user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "playlist_tracks_owner_update" ON public.playlist_tracks;
CREATE POLICY "playlist_tracks_owner_update"
    ON public.playlist_tracks FOR UPDATE
    USING (
        EXISTS (
            SELECT 1 FROM public.playlists p
            WHERE p.id = playlist_tracks.playlist_id
              AND p.user_id = auth.uid()
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.playlists p
            WHERE p.id = playlist_tracks.playlist_id
              AND p.user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "playlist_tracks_owner_delete" ON public.playlist_tracks;
CREATE POLICY "playlist_tracks_owner_delete"
    ON public.playlist_tracks FOR DELETE
    USING (
        EXISTS (
            SELECT 1 FROM public.playlists p
            WHERE p.id = playlist_tracks.playlist_id
              AND p.user_id = auth.uid()
        )
    );
