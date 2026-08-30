package com.music.epsilon.supabase.model

/**
 * Auto-generated constants for the Epsilon Music Supabase schema.
 *
 * This file mirrors the actual PostgreSQL schema (see supabase/SCHEMA.md).
 * Update it whenever a migration changes a table or column name.
 *
 * Generated from migrations 001–016.
 */
object EpsilonSchema {

    // ── Tables ───────────────────────────────────────────────────────────────
    const val TABLE_PROFILES = "profiles"
    const val TABLE_USER_SETTINGS = "user_settings"
    const val TABLE_PLAYLISTS = "playlists"
    const val TABLE_PLAYLIST_TRACKS = "playlist_tracks"
    const val TABLE_LIKED_SONGS = "liked_songs"
    const val TABLE_SAVED_ALBUMS = "saved_albums"
    const val TABLE_SAVED_ARTISTS = "saved_artists"
    const val TABLE_LISTENING_HISTORY = "listening_history"
    const val TABLE_RECENTLY_PLAYED = "recently_played"
    const val TABLE_DEVICES = "devices"
    const val TABLE_USER_SYNC_STATE = "user_sync_state"

    // ── Storage buckets ──────────────────────────────────────────────────────
    const val BUCKET_AVATARS = "epsilon-avatars"
    const val BUCKET_PLAYLIST_ART = "epsilon-playlist-art"

    // ── RPC functions ────────────────────────────────────────────────────────
    const val RPC_LIKE_SONG = "like_song"
    const val RPC_UNLIKE_SONG = "unlike_song"
    const val RPC_RECORD_PLAY = "record_play"
    const val RPC_MOVE_PLAYLIST_TRACK = "move_playlist_track"
    const val RPC_GET_RECENTLY_PLAYED = "get_recently_played"
    const val RPC_GET_USER_SYNC_STATE = "get_user_sync_state"
    const val RPC_UPSERT_USER_SETTINGS = "upsert_user_settings"
    const val RPC_REGISTER_DEVICE = "register_device"

    // ── Common column names ──────────────────────────────────────────────────
    const val COL_ID = "id"
    const val COL_USER_ID = "user_id"
    const val COL_PROVIDER = "provider"
    const val COL_SONG_ID = "song_id"
    const val COL_ALBUM_ID = "album_id"
    const val COL_ARTIST_ID = "artist_id"
    const val COL_PLAYLIST_ID = "playlist_id"
    const val COL_CREATED_AT = "created_at"
    const val COL_UPDATED_AT = "updated_at"
    const val COL_DELETED_AT = "deleted_at"

    // ── profiles ─────────────────────────────────────────────────────────────
    object Profiles {
        const val ID = "id"
        const val USERNAME = "username"
        const val DISPLAY_NAME = "display_name"
        const val AVATAR_URL = "avatar_url"
        const val BIO = "bio"
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"
    }

    // ── user_settings ────────────────────────────────────────────────────────
    object UserSettings {
        const val ID = "id"
        const val USER_ID = "user_id"
        const val THEME = "theme"
        const val PURE_BLACK = "pure_black"
        const val SELECTED_THEME_COLOR = "selected_theme_color"
        const val DYNAMIC_THEME = "dynamic_theme"
        const val APP_LANGUAGE = "app_language"
        const val CONTENT_LANGUAGE = "content_language"
        const val CONTENT_COUNTRY = "content_country"
        const val AUDIO_QUALITY = "audio_quality"
        const val DOWNLOAD_QUALITY = "download_quality"
        const val PLAYBACK_ENGINE = "playback_engine"
        const val CROSSFADE_ENABLED = "crossfade_enabled"
        const val CROSSFADE_DURATION = "crossfade_duration"
        const val CROSSFADE_GAPLESS = "crossfade_gapless"
        const val AUTOMIX_CROSSFADE = "automix_crossfade"
        const val SKIP_SILENCE = "skip_silence"
        const val AUDIO_NORMALIZATION = "audio_normalization"
        const val PERSISTENT_QUEUE = "persistent_queue"
        const val REMEMBER_SHUFFLE_REPEAT = "remember_shuffle_repeat"
        const val PRELOAD_NEXT_SONG = "preload_next_song"
        const val AUTOPLAY = "autoplay"
        const val PAUSE_LISTEN_HISTORY = "pause_listen_history"
        const val PAUSE_SEARCH_HISTORY = "pause_search_history"
        const val HIDE_EXPLICIT = "hide_explicit"
        const val SONG_SORT_TYPE = "song_sort_type"
        const val SONG_SORT_DESCENDING = "song_sort_descending"
        const val ARTIST_SORT_TYPE = "artist_sort_type"
        const val ARTIST_SORT_DESCENDING = "artist_sort_descending"
        const val ALBUM_SORT_TYPE = "album_sort_type"
        const val ALBUM_SORT_DESCENDING = "album_sort_descending"
        const val PLAYLIST_SORT_TYPE = "playlist_sort_type"
        const val PLAYLIST_SORT_DESCENDING = "playlist_sort_descending"
        const val SHOW_LIKED_PLAYLIST = "show_liked_playlist"
        const val SHOW_DOWNLOADED_PLAYLIST = "show_downloaded_playlist"
        const val SHOW_TOP_PLAYLIST = "show_top_playlist"
        const val SHOW_CACHED_PLAYLIST = "show_cached_playlist"
        const val SHOW_UPLOADED_PLAYLIST = "show_uploaded_playlist"
        const val PREFERENCES = "preferences"
        const val SCHEMA_VERSION = "schema_version"
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"
    }

    // ── playlists ────────────────────────────────────────────────────────────
    object Playlists {
        const val ID = "id"
        const val USER_ID = "user_id"
        const val TITLE = "title"
        const val DESCRIPTION = "description"
        const val ARTWORK_URL = "artwork_url"
        const val IS_PUBLIC = "is_public"
        const val SORT_ORDER = "sort_order"
        const val SHARE_SLUG = "share_slug"
        const val LAST_SYNCED_AT = "last_synced_at"
        const val DELETED_AT = "deleted_at"
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"
    }

    // ── playlist_tracks ──────────────────────────────────────────────────────
    object PlaylistTracks {
        const val ID = "id"
        const val PLAYLIST_ID = "playlist_id"
        const val PROVIDER = "provider"
        const val SONG_ID = "song_id"
        const val TITLE = "title"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val DURATION_MS = "duration_ms"
        const val THUMBNAIL_URL = "thumbnail_url"
        const val SET_VIDEO_ID = "set_video_id"
        const val POSITION = "position"
        const val ADDED_AT = "added_at"
        const val UPDATED_AT = "updated_at"
    }

    // ── liked_songs ──────────────────────────────────────────────────────────
    object LikedSongs {
        const val ID = "id"
        const val USER_ID = "user_id"
        const val PROVIDER = "provider"
        const val SONG_ID = "song_id"
        const val TITLE = "title"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val DURATION_MS = "duration_ms"
        const val THUMBNAIL_URL = "thumbnail_url"
        const val LIKED_AT = "liked_at"
    }

    // ── saved_albums ─────────────────────────────────────────────────────────
    object SavedAlbums {
        const val ID = "id"
        const val USER_ID = "user_id"
        const val PROVIDER = "provider"
        const val ALBUM_ID = "album_id"
        const val TITLE = "title"
        const val ARTIST = "artist"
        const val YEAR = "year"
        const val THUMBNAIL_URL = "thumbnail_url"
        const val SONG_COUNT = "song_count"
        const val DURATION_MS = "duration_ms"
        const val SAVED_AT = "saved_at"
    }

    // ── saved_artists ────────────────────────────────────────────────────────
    object SavedArtists {
        const val ID = "id"
        const val USER_ID = "user_id"
        const val PROVIDER = "provider"
        const val ARTIST_ID = "artist_id"
        const val NAME = "name"
        const val THUMBNAIL_URL = "thumbnail_url"
        const val CHANNEL_ID = "channel_id"
        const val SAVED_AT = "saved_at"
    }

    // ── listening_history ────────────────────────────────────────────────────
    object ListeningHistory {
        const val ID = "id"
        const val USER_ID = "user_id"
        const val PROVIDER = "provider"
        const val SONG_ID = "song_id"
        const val TITLE = "title"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val DURATION_MS = "duration_ms"
        const val THUMBNAIL_URL = "thumbnail_url"
        const val EVENT_TYPE = "event_type"
        const val LISTENED_MS = "listened_ms"
        const val PLAYED_AT = "played_at"
        const val DEVICE_ID = "device_id"
        const val CREATED_AT = "created_at"
    }

    // ── recently_played ──────────────────────────────────────────────────────
    object RecentlyPlayed {
        const val ID = "id"
        const val USER_ID = "user_id"
        const val PROVIDER = "provider"
        const val SONG_ID = "song_id"
        const val TITLE = "title"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val DURATION_MS = "duration_ms"
        const val THUMBNAIL_URL = "thumbnail_url"
        const val LAST_PLAYED_AT = "last_played_at"
        const val PLAY_COUNT = "play_count"
        const val CREATED_AT = "created_at"
    }

    // ── devices ──────────────────────────────────────────────────────────────
    object Devices {
        const val ID = "id"
        const val USER_ID = "user_id"
        const val DEVICE_FINGERPRINT = "device_fingerprint"
        const val DEVICE_NAME = "device_name"
        const val DEVICE_TYPE = "device_type"
        const val PLATFORM = "platform"
        const val APP_VERSION = "app_version"
        const val OS_VERSION = "os_version"
        const val LAST_SEEN_AT = "last_seen_at"
        const val LAST_SEEN_IP = "last_seen_ip"
        const val PUSH_TOKEN = "push_token"
        const val IS_ACTIVE = "is_active"
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"
    }

    // ── user_sync_state ──────────────────────────────────────────────────────
    object UserSyncState {
        const val USER_ID = "user_id"
        const val ENTITY = "entity"
        const val LAST_SYNCED_AT = "last_synced_at"
        const val LAST_SYNCED_VERSION = "last_synced_version"
        const val LAST_SYNC_STATUS = "last_sync_status"
        const val LAST_ERROR = "last_error"
        const val UPDATED_AT = "updated_at"
    }

    // ── Entity enum values for user_sync_state.entity ────────────────────────
    object SyncEntity {
        const val PLAYLISTS = "playlists"
        const val PLAYLIST_TRACKS = "playlist_tracks"
        const val LIKED_SONGS = "liked_songs"
        const val SAVED_ALBUMS = "saved_albums"
        const val SAVED_ARTISTS = "saved_artists"
        const val LISTENING_HISTORY = "listening_history"
        const val RECENTLY_PLAYED = "recently_played"
        const val USER_SETTINGS = "user_settings"
        const val DEVICES = "devices"
    }
}
