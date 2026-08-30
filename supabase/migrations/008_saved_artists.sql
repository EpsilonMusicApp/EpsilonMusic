-- 008_saved_artists.sql
-- Provider-aware artist subscriptions / bookmarks.

CREATE TABLE IF NOT EXISTS public.saved_artists (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    provider            TEXT NOT NULL DEFAULT 'youtube'
                        CHECK (provider IN ('youtube', 'apple_music', 'spotify', 'local', 'unknown')),
    artist_id           TEXT NOT NULL,
    -- Denormalized metadata
    name                TEXT,
    thumbnail_url       TEXT,
    channel_id          TEXT,
    saved_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT saved_artists_unique UNIQUE (user_id, provider, artist_id)
);

CREATE INDEX IF NOT EXISTS saved_artists_user_idx ON public.saved_artists (user_id, saved_at DESC);
CREATE INDEX IF NOT EXISTS saved_artists_provider_artist_idx ON public.saved_artists (provider, artist_id);

-- =====================================================================
-- RLS: owner-only.
-- =====================================================================
ALTER TABLE public.saved_artists ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "saved_artists_owner_select" ON public.saved_artists;
CREATE POLICY "saved_artists_owner_select"
    ON public.saved_artists FOR SELECT
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "saved_artists_owner_insert" ON public.saved_artists;
CREATE POLICY "saved_artists_owner_insert"
    ON public.saved_artists FOR INSERT
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "saved_artists_owner_update" ON public.saved_artists;
CREATE POLICY "saved_artists_owner_update"
    ON public.saved_artists FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "saved_artists_owner_delete" ON public.saved_artists;
CREATE POLICY "saved_artists_owner_delete"
    ON public.saved_artists FOR DELETE
    USING (auth.uid() = user_id);
