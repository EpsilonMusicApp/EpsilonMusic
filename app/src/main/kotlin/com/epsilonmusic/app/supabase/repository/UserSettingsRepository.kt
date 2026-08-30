package com.epsilonmusic.app.supabase.repository

import com.epsilonmusic.app.supabase.model.UserSettingsDto
import com.epsilonmusic.app.supabase.model.buildParams
import io.github.jan.supabase.postgrest.Postgrest
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs the user's roamed settings (theme, language, sort orders, etc.).
 *
 * Device-specific preferences (cache size, export dir, Listen Together session
 * tokens) are NOT mirrored here — they stay in DataStore.
 */
@Singleton
class UserSettingsRepository @Inject constructor(
    private val postgrest: Postgrest,
) {
    private val table get() = postgrest.from("user_settings")

    suspend fun get(): UserSettingsDto? = try {
        table.select().decodeSingle<UserSettingsDto>()
    } catch (e: Exception) {
        Timber.w(e, "Failed to fetch user_settings")
        null
    }

    /**
     * Upsert using the server-side `upsert_user_settings` RPC, which lets us
     * send only the fields we want to change.
     */
    suspend fun upsert(settings: UserSettingsDto): UserSettingsDto? = try {
        postgrest.rpc(
            function = "upsert_user_settings",
            parameters = buildParams {
                put("p_theme", settings.theme)
                put("p_pure_black", settings.pureBlack)
                put("p_dynamic_theme", settings.dynamicTheme)
                put("p_selected_theme_color", settings.selectedThemeColor)
                put("p_app_language", settings.appLanguage)
                put("p_content_language", settings.contentLanguage)
                put("p_content_country", settings.contentCountry)
                put("p_audio_quality", settings.audioQuality)
                put("p_download_quality", settings.downloadQuality)
                put("p_playback_engine", settings.playbackEngine)
                put("p_crossfade_enabled", settings.crossfadeEnabled)
                put("p_crossfade_duration", settings.crossfadeDuration)
                put("p_crossfade_gapless", settings.crossfadeGapless)
                put("p_automix_crossfade", settings.automixCrossfade)
                put("p_skip_silence", settings.skipSilence)
                put("p_audio_normalization", settings.audioNormalization)
                put("p_persistent_queue", settings.persistentQueue)
                put("p_remember_shuffle_repeat", settings.rememberShuffleRepeat)
                put("p_preload_next_song", settings.preloadNextSong)
                put("p_autoplay", settings.autoplay)
                put("p_pause_listen_history", settings.pauseListenHistory)
                put("p_pause_search_history", settings.pauseSearchHistory)
                put("p_hide_explicit", settings.hideExplicit)
                put("p_song_sort_type", settings.songSortType)
                put("p_song_sort_descending", settings.songSortDescending)
                put("p_artist_sort_type", settings.artistSortType)
                put("p_artist_sort_descending", settings.artistSortDescending)
                put("p_album_sort_type", settings.albumSortType)
                put("p_album_sort_descending", settings.albumSortDescending)
                put("p_playlist_sort_type", settings.playlistSortType)
                put("p_playlist_sort_descending", settings.playlistSortDescending)
                put("p_show_liked_playlist", settings.showLikedPlaylist)
                put("p_show_downloaded_playlist", settings.showDownloadedPlaylist)
                put("p_show_top_playlist", settings.showTopPlaylist)
                put("p_show_cached_playlist", settings.showCachedPlaylist)
                put("p_show_uploaded_playlist", settings.showUploadedPlaylist)
            },
        ).decodeAs<UserSettingsDto>()
    } catch (e: Exception) {
        Timber.e(e, "Failed to upsert user_settings")
        null
    }

    suspend fun delete() = try {
        table.delete()
        true
    } catch (e: Exception) {
        Timber.e(e, "Failed to delete user_settings")
        false
    }
}
