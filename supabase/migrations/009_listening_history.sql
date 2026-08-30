-- 009_listening_history.sql
-- Per-playback-event history. Each row = one playback event
-- (song started, song substantially played, song completed).
-- We do NOT log every position update — only meaningful events.

CREATE TABLE IF NOT EXISTS public.listening_history (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    provider            TEXT NOT NULL DEFAULT 'youtube'
                        CHECK (provider IN ('youtube', 'apple_music', 'spotify', 'local', 'unknown')),
    song_id             TEXT NOT NULL,
    -- Denormalized metadata (so historical events remain displayable even if
    -- the provider later removes the song)
    title               TEXT,
    artist              TEXT,
    album               TEXT,
    duration_ms         INTEGER,
    thumbnail_url       TEXT,
    -- Event details
    event_type          TEXT NOT NULL DEFAULT 'played'
                        CHECK (event_type IN ('started', 'played', 'completed', 'skipped')),
    -- How many milliseconds the user actually listened during this event
    listened_ms         INTEGER NOT NULL DEFAULT 0 CHECK (listened_ms >= 0),
    -- When the playback happened (NOT necessarily when the row was inserted)
    played_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Origin device (references devices.id, but nullable so events from before
    -- device registration still get logged)
    device_id           UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Primary access pattern: list user history (paginated, most recent first)
CREATE INDEX IF NOT EXISTS listening_history_user_played_idx
    ON public.listening_history (user_id, played_at DESC);
-- Secondary: lookup "did user play this song?"
CREATE INDEX IF NOT EXISTS listening_history_user_song_idx
    ON public.listening_history (user_id, provider, song_id, played_at DESC);
-- Retention / cleanup
CREATE INDEX IF NOT EXISTS listening_history_played_at_idx
    ON public.listening_history (played_at);

-- =====================================================================
-- RLS: owner-only.
-- =====================================================================
ALTER TABLE public.listening_history ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "listening_history_owner_select" ON public.listening_history;
CREATE POLICY "listening_history_owner_select"
    ON public.listening_history FOR SELECT
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "listening_history_owner_insert" ON public.listening_history;
CREATE POLICY "listening_history_owner_insert"
    ON public.listening_history FOR INSERT
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "listening_history_owner_update" ON public.listening_history;
CREATE POLICY "listening_history_owner_update"
    ON public.listening_history FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "listening_history_owner_delete" ON public.listening_history;
CREATE POLICY "listening_history_owner_delete"
    ON public.listening_history FOR DELETE
    USING (auth.uid() = user_id);

-- =====================================================================
-- Retention policy: prune history older than 1 year, automatically.
-- Runs hourly via pg_cron if available; otherwise the client should call
-- the function periodically.
-- =====================================================================
CREATE OR REPLACE FUNCTION public.prune_old_listening_history(retention_days INTEGER DEFAULT 365)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM public.listening_history
     WHERE played_at < NOW() - (retention_days || ' days')::INTERVAL
       AND user_id IS NOT NULL;
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Schedule the prune job (no-op if pg_cron extension is not enabled)
DO $_$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
        PERFORM cron.schedule(
            'prune_listening_history_hourly',
            '0 * * * *',
            $cmd$SELECT public.prune_old_listening_history(365);$cmd$
        );
    END IF;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pg_cron not available, skipping prune schedule: %', SQLERRM;
END $_$;
