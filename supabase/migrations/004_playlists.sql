-- 004_playlists.sql
-- User-owned cloud playlists.
-- The audio itself is NEVER stored here — only metadata about the playlist
-- (title, description, artwork URL) and the tracks it contains (in 005).

CREATE TABLE IF NOT EXISTS public.playlists (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    title           TEXT NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
    description     TEXT CHECK (description IS NULL OR char_length(description) <= 2000),
    artwork_url     TEXT,
    is_public       BOOLEAN NOT NULL DEFAULT false,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    -- A locally-generated browseId-like handle for sharing links
    share_slug      TEXT UNIQUE,
    -- Sync metadata
    last_synced_at  TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS playlists_user_id_idx ON public.playlists (user_id);
CREATE INDEX IF NOT EXISTS playlists_user_updated_idx ON public.playlists (user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS playlists_public_idx ON public.playlists (is_public, created_at DESC) WHERE is_public = true AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS playlists_share_slug_idx ON public.playlists (share_slug) WHERE share_slug IS NOT NULL;

DROP TRIGGER IF EXISTS playlists_set_updated_at ON public.playlists;
CREATE TRIGGER playlists_set_updated_at
    BEFORE UPDATE ON public.playlists
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

-- =====================================================================
-- RLS: owner-only by default; public playlists readable by anyone.
-- =====================================================================
ALTER TABLE public.playlists ENABLE ROW LEVEL SECURITY;

-- SELECT: owner OR (public AND not deleted)
DROP POLICY IF EXISTS "playlists_select" ON public.playlists;
CREATE POLICY "playlists_select"
    ON public.playlists FOR SELECT
    USING (
        auth.uid() = user_id
        OR (is_public = true AND deleted_at IS NULL)
    );

DROP POLICY IF EXISTS "playlists_owner_insert" ON public.playlists;
CREATE POLICY "playlists_owner_insert"
    ON public.playlists FOR INSERT
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "playlists_owner_update" ON public.playlists;
CREATE POLICY "playlists_owner_update"
    ON public.playlists FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "playlists_owner_delete" ON public.playlists;
CREATE POLICY "playlists_owner_delete"
    ON public.playlists FOR DELETE
    USING (auth.uid() = user_id);
