package com.music.echo.supabase.repository

import com.music.echo.supabase.model.MusicProvider
import com.music.echo.supabase.model.PlaylistDto
import com.music.echo.supabase.model.PlaylistTrackDto
import com.music.echo.supabase.model.PlaylistTrackInsertDto
import com.music.echo.supabase.model.PlaylistUpsertDto
import com.music.echo.supabase.model.buildParams
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud playlist CRUD. Operations target the `playlists` and `playlist_tracks`
 * tables; all RLS checks happen server-side.
 *
 * The local Room database is the source of truth for offline playback. Cloud
 * playlists are mirrored here so the user can roam between devices; the
 * [SyncManager] reconciles the two.
 */
@Singleton
class CloudPlaylistRepository @Inject constructor(
    private val postgrest: Postgrest,
) {
    private val playlistsTable get() = postgrest.from("playlists")
    private val tracksTable get() = postgrest.from("playlist_tracks")

    suspend fun listPlaylists(): List<PlaylistDto> = try {
        playlistsTable.select {
            filter { eq("deleted_at", null) }
            order("updated_at", Order.DESCENDING)
        }.decodeList<PlaylistDto>()
    } catch (e: Exception) {
        Timber.w(e, "listPlaylists failed")
        emptyList()
    }

    suspend fun getPlaylist(id: UUID): PlaylistDto? = try {
        playlistsTable.select {
            filter { eq("id", id.toString()) }
        }.decodeSingleOrNull<PlaylistDto>()
    } catch (e: Exception) {
        Timber.w(e, "getPlaylist failed")
        null
    }

    suspend fun createPlaylist(payload: PlaylistUpsertDto): PlaylistDto? = try {
        // Insert with only the user-provided fields — user_id is auto-derived
        // from auth.uid() by the RLS WITH CHECK policy.
        postgrest.from("playlists").insert(buildParams {
            put("title", payload.title)
            payload.description?.let { put("description", it) }
            payload.artworkUrl?.let { put("artwork_url", it) }
            put("is_public", payload.isPublic)
            put("sort_order", payload.sortOrder)
        }) {
            select()
        }.decodeSingleOrNull<PlaylistDto>()
    } catch (e: Exception) {
        Timber.e(e, "createPlaylist failed")
        null
    }

    suspend fun updatePlaylist(id: UUID, payload: PlaylistUpsertDto): PlaylistDto? = try {
        playlistsTable.update(buildParams {
            put("title", payload.title)
            payload.description?.let { put("description", it) }
            payload.artworkUrl?.let { put("artwork_url", it) }
            put("is_public", payload.isPublic)
            put("sort_order", payload.sortOrder)
        }) {
            filter { eq("id", id.toString()) }
            select()
        }.decodeSingleOrNull<PlaylistDto>()
    } catch (e: Exception) {
        Timber.e(e, "updatePlaylist failed")
        null
    }

    suspend fun softDeletePlaylist(id: UUID): Boolean = try {
        playlistsTable.update({
            set("deleted_at", "now()")
        }) {
            filter { eq("id", id.toString()) }
        }
        true
    } catch (e: Exception) {
        Timber.e(e, "softDeletePlaylist failed")
        false
    }

    // ── Tracks ───────────────────────────────────────────────────────────────

    suspend fun listTracks(playlistId: UUID): List<PlaylistTrackDto> = try {
        tracksTable.select {
            filter { eq("playlist_id", playlistId.toString()) }
            order("position", Order.ASCENDING)
        }.decodeList<PlaylistTrackDto>()
    } catch (e: Exception) {
        Timber.w(e, "listTracks failed")
        emptyList()
    }

    suspend fun addTrack(track: PlaylistTrackInsertDto): PlaylistTrackDto? = try {
        tracksTable.insert(track) {
            select()
        }.decodeSingleOrNull<PlaylistTrackDto>()
    } catch (e: Exception) {
        Timber.e(e, "addTrack failed")
        null
    }

    suspend fun addTracksBatch(tracks: List<PlaylistTrackInsertDto>): List<PlaylistTrackDto> = try {
        tracksTable.insert(tracks) {
            select()
        }.decodeList<PlaylistTrackDto>()
    } catch (e: Exception) {
        Timber.e(e, "addTracksBatch failed (${tracks.size} tracks)")
        emptyList()
    }

    suspend fun removeTrack(trackRowId: UUID): Boolean = try {
        tracksTable.delete {
            filter { eq("id", trackRowId.toString()) }
        }
        true
    } catch (e: Exception) {
        Timber.e(e, "removeTrack failed")
        false
    }

    suspend fun moveTrack(playlistId: UUID, from: Int, to: Int): Boolean = try {
        postgrest.rpc(
            function = "move_playlist_track",
            parameters = buildParams {
                put("p_playlist_id", playlistId.toString())
                put("p_from_position", from)
                put("p_to_position", to)
            },
        )
        true
    } catch (e: Exception) {
        Timber.e(e, "moveTrack failed")
        false
    }

    suspend fun clearPlaylist(playlistId: UUID): Boolean = try {
        tracksTable.delete {
            filter { eq("playlist_id", playlistId.toString()) }
        }
        true
    } catch (e: Exception) {
        Timber.e(e, "clearPlaylist failed")
        false
    }

    /**
     * Convenience: remove all tracks matching a (provider, songId) tuple
     * across all of the user's playlists. Useful when "removing a song from
     * library" should also purge it from any playlists.
     */
    suspend fun removeTrackFromAllPlaylists(provider: MusicProvider, songId: String): Int = try {
        val deleted = tracksTable.delete(
            returning = io.github.jan.supabase.postgrest.query.Returning.REPRESENTATION,
        ) {
            filter {
                eq("provider", provider.id)
                eq("song_id", songId)
            }
        }.decodeList<PlaylistTrackDto>()
        deleted.size
    } catch (e: Exception) {
        Timber.e(e, "removeTrackFromAllPlaylists failed")
        0
    }
}
