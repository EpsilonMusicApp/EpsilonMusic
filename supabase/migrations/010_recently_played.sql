-- 010_recently_played.sql
-- A compact, deduplicated "recently played" surface.
--
-- Without this table, the Android app would have to download thousands of
-- listening_history rows just to show "Recently Played" on the home screen.
-- Instead, we maintain a per-user capped list of the most-recently-played
-- unique (provider, song_id) pairs — automatically, via a trigger.
--
-- Cap = 100 unique songs per user.

CREATE TABLE IF NOT EXISTS public.recently_played (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    provider        TEXT NOT NULL DEFAULT 'youtube'
                    CHECK (provider IN ('youtube', 'apple_music', 'spotify', 'local', 'unknown')),
    song_id         TEXT NOT NULL,
    -- Denormalized metadata snapshot at last-play time
    title           TEXT,
    artist          TEXT,
    album           TEXT,
    duration_ms     INTEGER,
    thumbnail_url   TEXT,
    last_played_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    play_count      INTEGER NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT recently_played_unique UNIQUE (user_id, provider, song_id)
);

CREATE INDEX IF NOT EXISTS recently_played_user_last_idx
    ON public.recently_played (user_id, last_played_at DESC);

-- =====================================================================
-- Trigger: whenever a new listening_history row is inserted, upsert it into
-- recently_played and bump play_count.
-- =====================================================================
CREATE OR REPLACE FUNCTION public.upsert_recently_played()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.recently_played
        (user_id, provider, song_id, title, artist, album, duration_ms, thumbnail_url, last_played_at, play_count)
    VALUES
        (NEW.user_id, NEW.provider, NEW.song_id, NEW.title, NEW.artist, NEW.album,
         NEW.duration_ms, NEW.thumbnail_url, NEW.played_at, 1)
    ON CONFLICT (user_id, provider, song_id) DO UPDATE
       SET last_played_at = EXCLUDED.last_played_at,
           play_count     = public.recently_played.play_count + 1,
           title          = COALESCE(EXCLUDED.title, public.recently_played.title),
           artist         = COALESCE(EXCLUDED.artist, public.recently_played.artist),
           album          = COALESCE(EXCLUDED.album, public.recently_played.album),
           duration_ms    = COALESCE(EXCLUDED.duration_ms, public.recently_played.duration_ms),
           thumbnail_url  = COALESCE(EXCLUDED.thumbnail_url, public.recently_played.thumbnail_url);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS listening_history_upsert_recent ON public.listening_history;
CREATE TRIGGER listening_history_upsert_recent
    AFTER INSERT ON public.listening_history
    FOR EACH ROW
    EXECUTE FUNCTION public.upsert_recently_played();

-- Cap the recently_played table at 100 rows per user (drop oldest beyond cap)
CREATE OR REPLACE FUNCTION public.cap_recently_played()
RETURNS TRIGGER AS $$
BEGIN
    DELETE FROM public.recently_played
     WHERE user_id = NEW.user_id
       AND id NOT IN (
           SELECT id FROM public.recently_played
            WHERE user_id = NEW.user_id
            ORDER BY last_played_at DESC
            LIMIT 100
       );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS recently_played_cap ON public.recently_played;
CREATE TRIGGER recently_played_cap
    AFTER INSERT OR UPDATE ON public.recently_played
    FOR EACH ROW
    EXECUTE FUNCTION public.cap_recently_played();

-- =====================================================================
-- RLS: owner-only.
-- =====================================================================
ALTER TABLE public.recently_played ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "recently_played_owner_select" ON public.recently_played;
CREATE POLICY "recently_played_owner_select"
    ON public.recently_played FOR SELECT
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "recently_played_owner_insert" ON public.recently_played;
CREATE POLICY "recently_played_owner_insert"
    ON public.recently_played FOR INSERT
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "recently_played_owner_update" ON public.recently_played;
CREATE POLICY "recently_played_owner_update"
    ON public.recently_played FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "recently_played_owner_delete" ON public.recently_played;
CREATE POLICY "recently_played_owner_delete"
    ON public.recently_played FOR DELETE
    USING (auth.uid() = user_id);
