package com.epsilonmusic.app.supabase.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Provider enum — mirrors the `provider` TEXT CHECK constraint on the server.
 * Adding a new provider later does NOT require a schema migration because the
 * server column accepts any value in the CHECK list, but adding a new value
 * here means we should also add it to the server-side CHECK constraint.
 */
@Serializable
enum class MusicProvider(val id: String) {
    YOUTUBE("youtube"),
    APPLE_MUSIC("apple_music"),
    SPOTIFY("spotify"),
    LOCAL("local"),
    UNKNOWN("unknown");

    companion object {
        fun fromId(id: String?): MusicProvider = entries.firstOrNull { it.id == id } ?: YOUTUBE
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// profiles
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class ProfileDto(
    val id: String,
    val username: String? = null,
    val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val bio: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

// ─────────────────────────────────────────────────────────────────────────────
// user_settings
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class UserSettingsDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    val theme: String = "system",
    @SerialName("pure_black") val pureBlack: Boolean = false,
    @SerialName("selected_theme_color") val selectedThemeColor: String? = null,
    @SerialName("dynamic_theme") val dynamicTheme: Boolean = true,
    @SerialName("app_language") val appLanguage: String = "en",
    @SerialName("content_language") val contentLanguage: String = "en",
    @SerialName("content_country") val contentCountry: String = "US",
    @SerialName("audio_quality") val audioQuality: String = "auto",
    @SerialName("download_quality") val downloadQuality: String = "youtube",
    @SerialName("playback_engine") val playbackEngine: String = "auto",
    @SerialName("crossfade_enabled") val crossfadeEnabled: Boolean = false,
    @SerialName("crossfade_duration") val crossfadeDuration: Int = 8,
    @SerialName("crossfade_gapless") val crossfadeGapless: Boolean = false,
    @SerialName("automix_crossfade") val automixCrossfade: Boolean = false,
    @SerialName("skip_silence") val skipSilence: Boolean = false,
    @SerialName("audio_normalization") val audioNormalization: Boolean = true,
    @SerialName("persistent_queue") val persistentQueue: Boolean = true,
    @SerialName("remember_shuffle_repeat") val rememberShuffleRepeat: Boolean = true,
    @SerialName("preload_next_song") val preloadNextSong: Boolean = true,
    val autoplay: Boolean = true,
    @SerialName("pause_listen_history") val pauseListenHistory: Boolean = false,
    @SerialName("pause_search_history") val pauseSearchHistory: Boolean = false,
    @SerialName("hide_explicit") val hideExplicit: Boolean = false,
    @SerialName("song_sort_type") val songSortType: String = "CREATE_DATE",
    @SerialName("song_sort_descending") val songSortDescending: Boolean = false,
    @SerialName("artist_sort_type") val artistSortType: String = "NAME",
    @SerialName("artist_sort_descending") val artistSortDescending: Boolean = false,
    @SerialName("album_sort_type") val albumSortType: String = "YEAR",
    @SerialName("album_sort_descending") val albumSortDescending: Boolean = true,
    @SerialName("playlist_sort_type") val playlistSortType: String = "CREATE_DATE",
    @SerialName("playlist_sort_descending") val playlistSortDescending: Boolean = true,
    @SerialName("show_liked_playlist") val showLikedPlaylist: Boolean = true,
    @SerialName("show_downloaded_playlist") val showDownloadedPlaylist: Boolean = true,
    @SerialName("show_top_playlist") val showTopPlaylist: Boolean = true,
    @SerialName("show_cached_playlist") val showCachedPlaylist: Boolean = true,
    @SerialName("show_uploaded_playlist") val showUploadedPlaylist: Boolean = true,
    val preferences: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// playlists
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class PlaylistDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val description: String? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("is_public") val isPublic: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("share_slug") val shareSlug: String? = null,
    @SerialName("last_synced_at") val lastSyncedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class PlaylistUpsertDto(
    val title: String,
    val description: String? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("is_public") val isPublic: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

// ─────────────────────────────────────────────────────────────────────────────
// playlist_tracks
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class PlaylistTrackDto(
    val id: String,
    @SerialName("playlist_id") val playlistId: String,
    val provider: String = "youtube",
    @SerialName("song_id") val songId: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerialName("duration_ms") val durationMs: Int? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("set_video_id") val setVideoId: String? = null,
    val position: Int,
    @SerialName("added_at") val addedAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class PlaylistTrackInsertDto(
    @SerialName("playlist_id") val playlistId: String,
    val provider: String = "youtube",
    @SerialName("song_id") val songId: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerialName("duration_ms") val durationMs: Int? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("set_video_id") val setVideoId: String? = null,
    val position: Int,
)

// ─────────────────────────────────────────────────────────────────────────────
// liked_songs / saved_albums / saved_artists
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class LikedSongDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val provider: String = "youtube",
    @SerialName("song_id") val songId: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerialName("duration_ms") val durationMs: Int? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("liked_at") val likedAt: String,
)

@Serializable
data class SavedAlbumDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val provider: String = "youtube",
    @SerialName("album_id") val albumId: String,
    val title: String? = null,
    val artist: String? = null,
    val year: Int? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("song_count") val songCount: Int? = null,
    @SerialName("duration_ms") val durationMs: Int? = null,
    @SerialName("saved_at") val savedAt: String,
)

@Serializable
data class SavedArtistDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val provider: String = "youtube",
    @SerialName("artist_id") val artistId: String,
    val name: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("channel_id") val channelId: String? = null,
    @SerialName("saved_at") val savedAt: String,
)

// ─────────────────────────────────────────────────────────────────────────────
// listening_history / recently_played
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class ListeningHistoryDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val provider: String = "youtube",
    @SerialName("song_id") val songId: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerialName("duration_ms") val durationMs: Int? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("event_type") val eventType: String = "played",
    @SerialName("listened_ms") val listenedMs: Int = 0,
    @SerialName("played_at") val playedAt: String,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class RecentlyPlayedDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val provider: String = "youtube",
    @SerialName("song_id") val songId: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerialName("duration_ms") val durationMs: Int? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("last_played_at") val lastPlayedAt: String,
    @SerialName("play_count") val playCount: Int = 1,
    @SerialName("created_at") val createdAt: String,
)

// ─────────────────────────────────────────────────────────────────────────────
// devices
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class DeviceDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("device_fingerprint") val deviceFingerprint: String,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("device_type") val deviceType: String = "android",
    val platform: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("os_version") val osVersion: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String,
    @SerialName("push_token") val pushToken: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

// ─────────────────────────────────────────────────────────────────────────────
// get_user_sync_state RPC payload
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class SyncStateDto(
    val playlists: List<PlaylistDto> = emptyList(),
    @SerialName("playlist_tracks") val playlistTracks: List<PlaylistTrackDto> = emptyList(),
    @SerialName("liked_songs") val likedSongs: List<LikedSongDto> = emptyList(),
    @SerialName("saved_albums") val savedAlbums: List<SavedAlbumDto> = emptyList(),
    @SerialName("saved_artists") val savedArtists: List<SavedArtistDto> = emptyList(),
    @SerialName("recently_played") val recentlyPlayed: List<RecentlyPlayedDto> = emptyList(),
    @SerialName("user_settings") val userSettings: UserSettingsDto? = null,
    val profile: ProfileDto? = null,
    @SerialName("server_time") val serverTime: String,
)
