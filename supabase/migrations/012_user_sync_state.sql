-- 012_user_sync_state.sql
-- Per-entity sync watermark. Lets the client ask "give me everything that
-- changed since X" with a single round-trip.

CREATE TABLE IF NOT EXISTS public.user_sync_state (
    user_id             UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    entity              TEXT NOT NULL
                        CHECK (entity IN ('playlists', 'playlist_tracks', 'liked_songs',
                                          'saved_albums', 'saved_artists',
                                          'listening_history', 'recently_played',
                                          'user_settings', 'devices')),
    last_synced_at      TIMESTAMPTZ,
    last_synced_version BIGINT NOT NULL DEFAULT 0,
    last_sync_status    TEXT NOT NULL DEFAULT 'idle'
                        CHECK (last_sync_status IN ('idle', 'syncing', 'success', 'error')),
    last_error          TEXT,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, entity)
);

CREATE INDEX IF NOT EXISTS user_sync_state_user_idx ON public.user_sync_state (user_id);

DROP TRIGGER IF EXISTS user_sync_state_set_updated_at ON public.user_sync_state;
CREATE TRIGGER user_sync_state_set_updated_at
    BEFORE UPDATE ON public.user_sync_state
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

-- =====================================================================
-- RLS: owner-only.
-- =====================================================================
ALTER TABLE public.user_sync_state ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "user_sync_state_owner_select" ON public.user_sync_state;
CREATE POLICY "user_sync_state_owner_select"
    ON public.user_sync_state FOR SELECT
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "user_sync_state_owner_insert" ON public.user_sync_state;
CREATE POLICY "user_sync_state_owner_insert"
    ON public.user_sync_state FOR INSERT
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "user_sync_state_owner_update" ON public.user_sync_state;
CREATE POLICY "user_sync_state_owner_update"
    ON public.user_sync_state FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "user_sync_state_owner_delete" ON public.user_sync_state;
CREATE POLICY "user_sync_state_owner_delete"
    ON public.user_sync_state FOR DELETE
    USING (auth.uid() = user_id);
