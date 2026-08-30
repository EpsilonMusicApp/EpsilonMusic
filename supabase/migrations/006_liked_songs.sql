-- 006_liked_songs.sql
-- Provider-aware liked songs.
--
-- Uniqueness is logical: (user_id, provider, song_id).
-- This means a user can like the same logical song once per provider, but
-- never twice under the same provider.

CREATE TABLE IF NOT EXISTS public.liked_songs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    provider        TEXT NOT NULL DEFAULT 'youtube'
                    CHECK (provider IN ('youtube', 'apple_music', 'spotify', 'local', 'unknown')),
    song_id         TEXT NOT NULL,
    -- Denormalized metadata (same rationale as playlist_tracks)
    title           TEXT,
    artist          TEXT,
    album           TEXT,
    duration_ms     INTEGER,
    thumbnail_url   TEXT,
    liked_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Logical uniqueness
    CONSTRAINT liked_songs_unique UNIQUE (user_id, provider, song_id)
);

CREATE INDEX IF NOT EXISTS liked_songs_user_idx ON public.liked_songs (user_id, liked_at DESC);
CREATE INDEX IF NOT EXISTS liked_songs_provider_song_idx ON public.liked_songs (provider, song_id);

-- =====================================================================
-- RLS: owner-only.
-- =====================================================================
ALTER TABLE public.liked_songs ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "liked_songs_owner_select" ON public.liked_songs;
CREATE POLICY "liked_songs_owner_select"
    ON public.liked_songs FOR SELECT
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "liked_songs_owner_insert" ON public.liked_songs;
CREATE POLICY "liked_songs_owner_insert"
    ON public.liked_songs FOR INSERT
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "liked_songs_owner_update" ON public.liked_songs;
CREATE POLICY "liked_songs_owner_update"
    ON public.liked_songs FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "liked_songs_owner_delete" ON public.liked_songs;
CREATE POLICY "liked_songs_owner_delete"
    ON public.liked_songs FOR DELETE
    USING (auth.uid() = user_id);
