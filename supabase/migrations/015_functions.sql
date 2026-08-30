-- 015_functions.sql
-- RPC functions exposed via PostgREST (under /rest/v1/rpc/).
-- Each function is SECURITY DEFINER so it can run privileged logic while
-- respecting RLS-equivalent ownership checks via auth.uid().

-- =====================================================================
-- 1. like_song(p_provider, p_song_id, p_metadata)
-- Idempotent upsert of a liked song. Returns the liked_songs row.
-- =====================================================================
CREATE OR REPLACE FUNCTION public.like_song(
    p_song_id         TEXT,
    p_provider        TEXT DEFAULT 'youtube',
    p_title           TEXT DEFAULT NULL,
    p_artist          TEXT DEFAULT NULL,
    p_album           TEXT DEFAULT NULL,
    p_duration_ms     INTEGER DEFAULT NULL,
    p_thumbnail_url   TEXT DEFAULT NULL
)
RETURNS public.liked_songs AS $$
DECLARE
    row public.liked_songs;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '42501';
    END IF;
    INSERT INTO public.liked_songs (user_id, provider, song_id, title, artist, album, duration_ms, thumbnail_url)
    VALUES (auth.uid(), p_provider, p_song_id, p_title, p_artist, p_album, p_duration_ms, p_thumbnail_url)
    ON CONFLICT (user_id, provider, song_id) DO UPDATE
       SET title         = COALESCE(EXCLUDED.title, public.liked_songs.title),
           artist        = COALESCE(EXCLUDED.artist, public.liked_songs.artist),
           album         = COALESCE(EXCLUDED.album, public.liked_songs.album),
           duration_ms   = COALESCE(EXCLUDED.duration_ms, public.liked_songs.duration_ms),
           thumbnail_url = COALESCE(EXCLUDED.thumbnail_url, public.liked_songs.thumbnail_url),
           liked_at      = NOW()
    RETURNING * INTO row;
    RETURN row;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- =====================================================================
-- 2. unlike_song(p_provider, p_song_id)
-- =====================================================================
CREATE OR REPLACE FUNCTION public.unlike_song(
    p_song_id   TEXT,
    p_provider  TEXT DEFAULT 'youtube'
)
RETURNS BOOLEAN AS $$
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '42501';
    END IF;
    DELETE FROM public.liked_songs
     WHERE user_id = auth.uid()
       AND provider = p_provider
       AND song_id = p_song_id;
    RETURN FOUND;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- =====================================================================
-- 3. record_play(p_provider, p_song_id, p_event_type, p_listened_ms, p_metadata, p_played_at, p_device_id)
-- Single entry point for the Android client to log a playback event.
-- Side effect: trigger on listening_history upserts into recently_played.
-- =====================================================================
CREATE OR REPLACE FUNCTION public.record_play(
    p_song_id         TEXT,
    p_provider        TEXT DEFAULT 'youtube',
    p_event_type      TEXT DEFAULT 'played',
    p_listened_ms     INTEGER DEFAULT 0,
    p_title           TEXT DEFAULT NULL,
    p_artist          TEXT DEFAULT NULL,
    p_album           TEXT DEFAULT NULL,
    p_duration_ms     INTEGER DEFAULT NULL,
    p_thumbnail_url   TEXT DEFAULT NULL,
    p_played_at       TIMESTAMPTZ DEFAULT NOW(),
    p_device_id       UUID DEFAULT NULL
)
RETURNS public.listening_history AS $$
DECLARE
    row public.listening_history;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '42501';
    END IF;
    INSERT INTO public.listening_history
        (user_id, provider, song_id, event_type, listened_ms,
         title, artist, album, duration_ms, thumbnail_url, played_at, device_id)
    VALUES
        (auth.uid(), p_provider, p_song_id, p_event_type, p_listened_ms,
         p_title, p_artist, p_album, p_duration_ms, p_thumbnail_url, p_played_at, p_device_id)
    RETURNING * INTO row;
    RETURN row;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- =====================================================================
-- 4. move_playlist_track(p_playlist_id, p_from_position, p_to_position)
-- Atomic re-position of a track inside a playlist.
-- Re-packs all positions to be gapless afterwards.
-- =====================================================================
CREATE OR REPLACE FUNCTION public.move_playlist_track(
    p_playlist_id     UUID,
    p_from_position   INTEGER,
    p_to_position     INTEGER
)
RETURNS VOID AS $$
DECLARE
    pl_owner UUID;
BEGIN
    SELECT user_id INTO pl_owner FROM public.playlists WHERE id = p_playlist_id;
    IF pl_owner IS NULL THEN
        RAISE EXCEPTION 'Playlist not found' USING ERRCODE = 'P0002';
    END IF;
    IF pl_owner <> auth.uid() THEN
        RAISE EXCEPTION 'Not owner of playlist' USING ERRCODE = '42501';
    END IF;
    IF p_from_position = p_to_position THEN RETURN; END IF;

    -- Shift positions in the affected range
    IF p_from_position < p_to_position THEN
        UPDATE public.playlist_tracks
           SET position = position - 1
         WHERE playlist_id = p_playlist_id
           AND position > p_from_position
           AND position <= p_to_position;
        UPDATE public.playlist_tracks
           SET position = p_to_position
         WHERE playlist_id = p_playlist_id
           AND position = p_from_position - 1;  -- already shifted
        -- Actually the row we want to move was at p_from_position; after the
        -- shift above it's now at p_from_position - 1, but we want to fix that:
        UPDATE public.playlist_tracks
           SET position = p_to_position
         WHERE playlist_id = p_playlist_id
           AND position = p_from_position - 1;
    ELSE
        UPDATE public.playlist_tracks
           SET position = position + 1
         WHERE playlist_id = p_playlist_id
           AND position >= p_to_position
           AND position < p_from_position;
        UPDATE public.playlist_tracks
           SET position = p_to_position
         WHERE playlist_id = p_playlist_id
           AND position = p_from_position + 1;  -- shifted up by 1 in prev step
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- =====================================================================
-- 5. get_recently_played(p_limit)
-- Returns compact recently-played list for the current user.
-- =====================================================================
CREATE OR REPLACE FUNCTION public.get_recently_played(p_limit INTEGER DEFAULT 50)
RETURNS SETOF public.recently_played AS $$
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '42501';
    END IF;
    RETURN QUERY
        SELECT * FROM public.recently_played
         WHERE user_id = auth.uid()
         ORDER BY last_played_at DESC
         LIMIT LEAST(GREATEST(p_limit, 1), 100);
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER;

-- =====================================================================
-- 6. get_user_sync_state(p_since TIMESTAMPTZ DEFAULT NULL)
-- Returns a compact diff of all user-owned data changed since `p_since`.
-- This is the workhorse RPC for cross-device sync: the Android app calls
-- this once after connecting, then merges the result into local Room tables.
-- =====================================================================
CREATE OR REPLACE FUNCTION public.get_user_sync_state(p_since TIMESTAMPTZ DEFAULT NULL)
RETURNS JSONB AS $$
DECLARE
    result         JSONB := '{}'::jsonb;
    v_playlists    JSONB;
    v_tracks       JSONB;
    v_liked        JSONB;
    v_albums       JSONB;
    v_artists      JSONB;
    v_recent       JSONB;
    v_settings     JSONB;
    v_profile      JSONB;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '42501';
    END IF;

    SELECT COALESCE(jsonb_agg(to_jsonb(p)), '[]'::jsonb)
      INTO v_playlists
      FROM public.playlists p
     WHERE p.user_id = auth.uid()
       AND (p_since IS NULL OR p.updated_at >= p_since)
       AND p.deleted_at IS NULL;

    SELECT COALESCE(jsonb_agg(to_jsonb(pt)), '[]'::jsonb)
      INTO v_tracks
      FROM public.playlist_tracks pt
      JOIN public.playlists p ON p.id = pt.playlist_id
     WHERE p.user_id = auth.uid()
       AND (p_since IS NULL OR pt.updated_at >= p_since);

    SELECT COALESCE(jsonb_agg(to_jsonb(ls)), '[]'::jsonb)
      INTO v_liked
      FROM public.liked_songs ls
     WHERE ls.user_id = auth.uid()
       AND (p_since IS NULL OR ls.liked_at >= p_since);

    SELECT COALESCE(jsonb_agg(to_jsonb(sa)), '[]'::jsonb)
      INTO v_albums
      FROM public.saved_albums sa
     WHERE sa.user_id = auth.uid()
       AND (p_since IS NULL OR sa.saved_at >= p_since);

    SELECT COALESCE(jsonb_agg(to_jsonb(sar)), '[]'::jsonb)
      INTO v_artists
      FROM public.saved_artists sar
     WHERE sar.user_id = auth.uid()
       AND (p_since IS NULL OR sar.saved_at >= p_since);

    SELECT COALESCE(jsonb_agg(to_jsonb(rp)), '[]'::jsonb)
      INTO v_recent
      FROM public.recently_played rp
     WHERE rp.user_id = auth.uid()
       AND (p_since IS NULL OR rp.last_played_at >= p_since);

    SELECT to_jsonb(us) INTO v_settings
      FROM public.user_settings us
     WHERE us.user_id = auth.uid();

    SELECT to_jsonb(p) INTO v_profile
      FROM public.profiles p
     WHERE p.id = auth.uid();

    result := jsonb_build_object(
        'playlists',       v_playlists,
        'playlist_tracks', v_tracks,
        'liked_songs',     v_liked,
        'saved_albums',    v_albums,
        'saved_artists',   v_artists,
        'recently_played', v_recent,
        'user_settings',   COALESCE(v_settings, 'null'::jsonb),
        'profile',         COALESCE(v_profile, 'null'::jsonb),
        'server_time',     to_jsonb(NOW())
    );

    RETURN result;
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER;

-- =====================================================================
-- 7. upsert_user_settings(...)
-- Convenience wrapper for the Android client — accepts a sparse payload
-- (only non-null fields are updated).
-- =====================================================================
CREATE OR REPLACE FUNCTION public.upsert_user_settings(
    p_theme                    TEXT DEFAULT NULL,
    p_pure_black               BOOLEAN DEFAULT NULL,
    p_selected_theme_color     TEXT DEFAULT NULL,
    p_dynamic_theme            BOOLEAN DEFAULT NULL,
    p_app_language             TEXT DEFAULT NULL,
    p_content_language         TEXT DEFAULT NULL,
    p_content_country          TEXT DEFAULT NULL,
    p_audio_quality            TEXT DEFAULT NULL,
    p_download_quality         TEXT DEFAULT NULL,
    p_playback_engine          TEXT DEFAULT NULL,
    p_crossfade_enabled        BOOLEAN DEFAULT NULL,
    p_crossfade_duration       INTEGER DEFAULT NULL,
    p_crossfade_gapless        BOOLEAN DEFAULT NULL,
    p_automix_crossfade        BOOLEAN DEFAULT NULL,
    p_skip_silence             BOOLEAN DEFAULT NULL,
    p_audio_normalization      BOOLEAN DEFAULT NULL,
    p_persistent_queue         BOOLEAN DEFAULT NULL,
    p_remember_shuffle_repeat  BOOLEAN DEFAULT NULL,
    p_preload_next_song        BOOLEAN DEFAULT NULL,
    p_autoplay                 BOOLEAN DEFAULT NULL,
    p_pause_listen_history     BOOLEAN DEFAULT NULL,
    p_pause_search_history     BOOLEAN DEFAULT NULL,
    p_hide_explicit            BOOLEAN DEFAULT NULL,
    p_song_sort_type           TEXT DEFAULT NULL,
    p_song_sort_descending     BOOLEAN DEFAULT NULL,
    p_artist_sort_type         TEXT DEFAULT NULL,
    p_artist_sort_descending   BOOLEAN DEFAULT NULL,
    p_album_sort_type          TEXT DEFAULT NULL,
    p_album_sort_descending    BOOLEAN DEFAULT NULL,
    p_playlist_sort_type       TEXT DEFAULT NULL,
    p_playlist_sort_descending BOOLEAN DEFAULT NULL,
    p_show_liked_playlist      BOOLEAN DEFAULT NULL,
    p_show_downloaded_playlist BOOLEAN DEFAULT NULL,
    p_show_top_playlist        BOOLEAN DEFAULT NULL,
    p_show_cached_playlist     BOOLEAN DEFAULT NULL,
    p_show_uploaded_playlist   BOOLEAN DEFAULT NULL,
    p_preferences              JSONB DEFAULT NULL
)
RETURNS public.user_settings AS $$
DECLARE
    row public.user_settings;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '42501';
    END IF;

    INSERT INTO public.user_settings (user_id)
    VALUES (auth.uid())
    ON CONFLICT (user_id) DO NOTHING;

    UPDATE public.user_settings SET
        theme                    = COALESCE(p_theme, theme),
        pure_black               = COALESCE(p_pure_black, pure_black),
        selected_theme_color     = COALESCE(p_selected_theme_color, selected_theme_color),
        dynamic_theme            = COALESCE(p_dynamic_theme, dynamic_theme),
        app_language             = COALESCE(p_app_language, app_language),
        content_language         = COALESCE(p_content_language, content_language),
        content_country          = COALESCE(p_content_country, content_country),
        audio_quality            = COALESCE(p_audio_quality, audio_quality),
        download_quality         = COALESCE(p_download_quality, download_quality),
        playback_engine          = COALESCE(p_playback_engine, playback_engine),
        crossfade_enabled        = COALESCE(p_crossfade_enabled, crossfade_enabled),
        crossfade_duration       = COALESCE(p_crossfade_duration, crossfade_duration),
        crossfade_gapless        = COALESCE(p_crossfade_gapless, crossfade_gapless),
        automix_crossfade        = COALESCE(p_automix_crossfade, automix_crossfade),
        skip_silence             = COALESCE(p_skip_silence, skip_silence),
        audio_normalization      = COALESCE(p_audio_normalization, audio_normalization),
        persistent_queue         = COALESCE(p_persistent_queue, persistent_queue),
        remember_shuffle_repeat  = COALESCE(p_remember_shuffle_repeat, remember_shuffle_repeat),
        preload_next_song        = COALESCE(p_preload_next_song, preload_next_song),
        autoplay                 = COALESCE(p_autoplay, autoplay),
        pause_listen_history     = COALESCE(p_pause_listen_history, pause_listen_history),
        pause_search_history     = COALESCE(p_pause_search_history, pause_search_history),
        hide_explicit            = COALESCE(p_hide_explicit, hide_explicit),
        song_sort_type           = COALESCE(p_song_sort_type, song_sort_type),
        song_sort_descending     = COALESCE(p_song_sort_descending, song_sort_descending),
        artist_sort_type         = COALESCE(p_artist_sort_type, artist_sort_type),
        artist_sort_descending   = COALESCE(p_artist_sort_descending, artist_sort_descending),
        album_sort_type          = COALESCE(p_album_sort_type, album_sort_type),
        album_sort_descending    = COALESCE(p_album_sort_descending, album_sort_descending),
        playlist_sort_type       = COALESCE(p_playlist_sort_type, playlist_sort_type),
        playlist_sort_descending = COALESCE(p_playlist_sort_descending, playlist_sort_descending),
        show_liked_playlist      = COALESCE(p_show_liked_playlist, show_liked_playlist),
        show_downloaded_playlist = COALESCE(p_show_downloaded_playlist, show_downloaded_playlist),
        show_top_playlist        = COALESCE(p_show_top_playlist, show_top_playlist),
        show_cached_playlist     = COALESCE(p_show_cached_playlist, show_cached_playlist),
        show_uploaded_playlist   = COALESCE(p_show_uploaded_playlist, show_uploaded_playlist),
        preferences              = CASE
                                    WHEN p_preferences IS NULL THEN preferences
                                    ELSE preferences || p_preferences
                                   END
    WHERE user_id = auth.uid()
    RETURNING * INTO row;

    RETURN row;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- =====================================================================
-- 8. register_device(p_fingerprint, p_name, p_app_version, p_platform, p_push_token)
-- Idempotent device registration.
-- =====================================================================
CREATE OR REPLACE FUNCTION public.register_device(
    p_fingerprint  TEXT,
    p_name         TEXT DEFAULT NULL,
    p_app_version  TEXT DEFAULT NULL,
    p_platform     TEXT DEFAULT 'android',
    p_push_token   TEXT DEFAULT NULL
)
RETURNS public.devices AS $$
DECLARE
    row public.devices;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Not authenticated' USING ERRCODE = '42501';
    END IF;
    INSERT INTO public.devices
        (user_id, device_fingerprint, device_name, app_version, platform, push_token, last_seen_at, is_active)
    VALUES
        (auth.uid(), p_fingerprint, p_name, p_app_version, p_platform, p_push_token, NOW(), true)
    ON CONFLICT (user_id, device_fingerprint) DO UPDATE
       SET device_name   = COALESCE(EXCLUDED.device_name, public.devices.device_name),
           app_version   = COALESCE(EXCLUDED.app_version, public.devices.app_version),
           platform      = COALESCE(EXCLUDED.platform, public.devices.platform),
           push_token    = COALESCE(EXCLUDED.push_token, public.devices.push_token),
           last_seen_at  = NOW(),
           is_active     = true
    RETURNING * INTO row;
    RETURN row;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
