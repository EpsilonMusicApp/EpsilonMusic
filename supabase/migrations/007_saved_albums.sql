-- 007_saved_albums.sql
-- Provider-aware album saves (library bookmarks).
-- Same logical-uniqueness approach as liked_songs.

CREATE TABLE IF NOT EXISTS public.saved_albums (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    provider            TEXT NOT NULL DEFAULT 'youtube'
                        CHECK (provider IN ('youtube', 'apple_music', 'spotify', 'local', 'unknown')),
    album_id            TEXT NOT NULL,
    -- Denormalized metadata
    title               TEXT,
    artist              TEXT,
    year                INTEGER CHECK (year IS NULL OR year BETWEEN 1900 AND 2100),
    thumbnail_url       TEXT,
    song_count          INTEGER,
    duration_ms         INTEGER,
    saved_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT saved_albums_unique UNIQUE (user_id, provider, album_id)
);

CREATE INDEX IF NOT EXISTS saved_albums_user_idx ON public.saved_albums (user_id, saved_at DESC);
CREATE INDEX IF NOT EXISTS saved_albums_provider_album_idx ON public.saved_albums (provider, album_id);

-- =====================================================================
-- RLS: owner-only.
-- =====================================================================
ALTER TABLE public.saved_albums ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "saved_albums_owner_select" ON public.saved_albums;
CREATE POLICY "saved_albums_owner_select"
    ON public.saved_albums FOR SELECT
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "saved_albums_owner_insert" ON public.saved_albums;
CREATE POLICY "saved_albums_owner_insert"
    ON public.saved_albums FOR INSERT
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "saved_albums_owner_update" ON public.saved_albums;
CREATE POLICY "saved_albums_owner_update"
    ON public.saved_albums FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "saved_albums_owner_delete" ON public.saved_albums;
CREATE POLICY "saved_albums_owner_delete"
    ON public.saved_albums FOR DELETE
    USING (auth.uid() = user_id);
