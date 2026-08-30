-- 003_user_settings.sql
-- Cloud-synchronized user preferences.
--
-- Design principle: only settings that genuinely make sense to roam between
-- devices live here. Device-specific stuff (cache size, export dir URI, etc.)
-- stays in the local Android DataStore and is NOT mirrored to the cloud.
--
-- Schema strategy:
--   - Strongly typed columns for queryable / important values
--   - JSONB `preferences` blob for evolving settings without schema churn
--   - `schema_version` so the client can refuse/upgrade old payloads safely

CREATE TABLE IF NOT EXISTS public.user_settings (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID NOT NULL UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE
                                            REFERENCES public.profiles(id) ON DELETE CASCADE,
    -- Appearance
    theme                       TEXT NOT NULL DEFAULT 'system'
                                CHECK (theme IN ('system', 'light', 'dark')),
    pure_black                  BOOLEAN NOT NULL DEFAULT false,
    selected_theme_color        TEXT,
    dynamic_theme               BOOLEAN NOT NULL DEFAULT true,
    -- Language & content
    app_language                TEXT NOT NULL DEFAULT 'en',
    content_language            TEXT NOT NULL DEFAULT 'en',
    content_country             TEXT NOT NULL DEFAULT 'US',
    -- Playback
    audio_quality               TEXT NOT NULL DEFAULT 'auto'
                                CHECK (audio_quality IN ('auto', 'opus', 'aac', 'high', 'low')),
    download_quality            TEXT NOT NULL DEFAULT 'youtube'
                                CHECK (download_quality IN ('youtube', 'opus', 'aac', 'mp3')),
    playback_engine             TEXT NOT NULL DEFAULT 'auto'
                                CHECK (playback_engine in ('auto', 'potoken', 'bravepipe')),
    crossfade_enabled           BOOLEAN NOT NULL DEFAULT false,
    crossfade_duration          INTEGER NOT NULL DEFAULT 8 CHECK (crossfade_duration BETWEEN 0 AND 12),
    crossfade_gapless           BOOLEAN NOT NULL DEFAULT false,
    automix_crossfade           BOOLEAN NOT NULL DEFAULT false,
    skip_silence                BOOLEAN NOT NULL DEFAULT false,
    audio_normalization         BOOLEAN NOT NULL DEFAULT true,
    persistent_queue            BOOLEAN NOT NULL DEFAULT true,
    remember_shuffle_repeat     BOOLEAN NOT NULL DEFAULT true,
    preload_next_song           BOOLEAN NOT NULL DEFAULT true,
    autoplay                    BOOLEAN NOT NULL DEFAULT true,
    -- Library / history
    pause_listen_history        BOOLEAN NOT NULL DEFAULT false,
    pause_search_history        BOOLEAN NOT NULL DEFAULT false,
    hide_explicit               BOOLEAN NOT NULL DEFAULT false,
    -- Sorting (synced)
    song_sort_type              TEXT NOT NULL DEFAULT 'CREATE_DATE',
    song_sort_descending        BOOLEAN NOT NULL DEFAULT false,
    artist_sort_type            TEXT NOT NULL DEFAULT 'NAME',
    artist_sort_descending      BOOLEAN NOT NULL DEFAULT false,
    album_sort_type             TEXT NOT NULL DEFAULT 'YEAR',
    album_sort_descending       BOOLEAN NOT NULL DEFAULT true,
    playlist_sort_type          TEXT NOT NULL DEFAULT 'CREATE_DATE',
    playlist_sort_descending    BOOLEAN NOT NULL DEFAULT true,
    -- Show/hide library shelves
    show_liked_playlist         BOOLEAN NOT NULL DEFAULT true,
    show_downloaded_playlist    BOOLEAN NOT NULL DEFAULT true,
    show_top_playlist           BOOLEAN NOT NULL DEFAULT true,
    show_cached_playlist        BOOLEAN NOT NULL DEFAULT true,
    show_uploaded_playlist      BOOLEAN NOT NULL DEFAULT true,
    -- JSONB for evolving / less-queryable prefs (lyrics provider order, etc.)
    preferences                 JSONB NOT NULL DEFAULT '{}'::jsonb,
    schema_version              INTEGER NOT NULL DEFAULT 1,
    -- Timestamps
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS user_settings_user_id_idx ON public.user_settings (user_id);

DROP TRIGGER IF EXISTS user_settings_set_updated_at ON public.user_settings;
CREATE TRIGGER user_settings_set_updated_at
    BEFORE UPDATE ON public.user_settings
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

-- Auto-create a default user_settings row when a new profile is created.
-- (Triggered by the handle_new_user trigger that runs after auth.users INSERT.)
CREATE OR REPLACE FUNCTION public.handle_new_profile()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.user_settings (user_id)
    VALUES (NEW.id)
    ON CONFLICT (user_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_profile_created ON public.profiles;
CREATE TRIGGER on_profile_created
    AFTER INSERT ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_new_profile();

-- =====================================================================
-- RLS: user_settings is strictly owner-only.
-- =====================================================================
ALTER TABLE public.user_settings ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "user_settings_owner_select" ON public.user_settings;
CREATE POLICY "user_settings_owner_select"
    ON public.user_settings FOR SELECT
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "user_settings_owner_insert" ON public.user_settings;
CREATE POLICY "user_settings_owner_insert"
    ON public.user_settings FOR INSERT
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "user_settings_owner_update" ON public.user_settings;
CREATE POLICY "user_settings_owner_update"
    ON public.user_settings FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "user_settings_owner_delete" ON public.user_settings;
CREATE POLICY "user_settings_owner_delete"
    ON public.user_settings FOR DELETE
    USING (auth.uid() = user_id);
