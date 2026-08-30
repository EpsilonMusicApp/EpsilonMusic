package com.epsilonmusic.app.supabase.repository

import com.epsilonmusic.app.supabase.model.MusicProvider
import com.epsilonmusic.app.supabase.model.RecentlyPlayedDto
import com.epsilonmusic.app.supabase.model.buildParams
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listening history & recently-played access.
 *
 * The local Room `event` table remains the source of truth for offline stats.
 * This repository ships playback events to the cloud via the `record_play` RPC,
 * which also auto-updates `recently_played` via a server-side trigger.
 */
@Singleton
class HistorySyncRepository @Inject constructor(
    private val postgrest: Postgrest,
) {

    /**
     * Record a playback event on the server. Idempotent in the sense that
     * calling it twice for the same playback creates two events — but that's
     * intentional: each play is a distinct event.
     */
    suspend fun recordPlay(
        provider: MusicProvider,
        songId: String,
        eventType: String = "played",
        listenedMs: Int = 0,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        durationMs: Int? = null,
        thumbnailUrl: String? = null,
        deviceId: UUID? = null,
    ): Boolean = try {
        postgrest.rpc(
            function = "record_play",
            parameters = buildParams {
                put("p_song_id", songId)
                put("p_provider", provider.id)
                put("p_event_type", eventType)
                put("p_listened_ms", listenedMs)
                put("p_title", title)
                put("p_artist", artist)
                put("p_album", album)
                put("p_duration_ms", durationMs)
                put("p_thumbnail_url", thumbnailUrl)
                deviceId?.let { put("p_device_id", it.toString()) }
            },
        )
        true
    } catch (e: Exception) {
        Timber.w(e, "recordPlay failed (will be retried by SyncManager)")
        false
    }

    suspend fun listRecentlyPlayed(limit: Int = 50): List<RecentlyPlayedDto> = try {
        postgrest.from("recently_played").select {
            order("last_played_at", Order.DESCENDING)
            range(0, (limit - 1).coerceAtLeast(0))
        }.decodeList<RecentlyPlayedDto>()
    } catch (e: Exception) {
        Timber.w(e, "listRecentlyPlayed failed")
        emptyList()
    }

    suspend fun listListeningHistory(limit: Int = 200): List<com.epsilonmusic.app.supabase.model.ListeningHistoryDto> = try {
        postgrest.from("listening_history").select {
            order("played_at", Order.DESCENDING)
            range(0, (limit - 1).coerceAtLeast(0))
        }.decodeList<com.epsilonmusic.app.supabase.model.ListeningHistoryDto>()
    } catch (e: Exception) {
        Timber.w(e, "listListeningHistory failed")
        emptyList()
    }

    suspend fun clearHistory(): Boolean = try {
        postgrest.from("listening_history").delete()
        postgrest.from("recently_played").delete()
        true
    } catch (e: Exception) {
        Timber.e(e, "clearHistory failed")
        false
    }
}
