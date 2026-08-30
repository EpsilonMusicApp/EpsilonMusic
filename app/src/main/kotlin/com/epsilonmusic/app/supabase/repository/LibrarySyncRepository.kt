package com.epsilonmusic.app.supabase.repository

import com.epsilonmusic.app.supabase.model.LikedSongDto
import com.epsilonmusic.app.supabase.model.MusicProvider
import com.epsilonmusic.app.supabase.model.SavedAlbumDto
import com.epsilonmusic.app.supabase.model.SavedArtistDto
import com.epsilonmusic.app.supabase.model.buildParams
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Library: liked songs + saved albums + saved artists.
 *
 * Each entity is provider-aware — a user can like the same logical song once
 * per provider (e.g. once on YouTube Music, once on Apple Music). The
 * uniqueness constraint is enforced server-side.
 */
@Singleton
class LibrarySyncRepository @Inject constructor(
    private val postgrest: Postgrest,
) {
    // ── Liked songs ──────────────────────────────────────────────────────────

    suspend fun listLikedSongs(limit: Int = 500): List<LikedSongDto> = try {
        postgrest.from("liked_songs").select {
            order("liked_at", Order.DESCENDING)
            range(0L, (limit - 1).toLong())
        }.decodeList<LikedSongDto>()
    } catch (e: Exception) {
        Timber.w(e, "listLikedSongs failed")
        emptyList()
    }

    suspend fun isLiked(provider: MusicProvider, songId: String): Boolean = try {
        postgrest.from("liked_songs").select {
            filter {
                eq("provider", provider.id)
                eq("song_id", songId)
            }
        }.decodeList<LikedSongDto>().isNotEmpty()
    } catch (e: Exception) {
        Timber.w(e, "isLiked failed")
        false
    }

    suspend fun like(
        provider: MusicProvider,
        songId: String,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        durationMs: Int? = null,
        thumbnailUrl: String? = null,
    ): LikedSongDto? = try {
        postgrest.rpc(
            function = "like_song",
            parameters = buildParams {
                put("p_song_id", songId)
                put("p_provider", provider.id)
                put("p_title", title)
                put("p_artist", artist)
                put("p_album", album)
                put("p_duration_ms", durationMs)
                put("p_thumbnail_url", thumbnailUrl)
            },
        ).decodeAs<LikedSongDto>()
    } catch (e: Exception) {
        Timber.e(e, "like failed")
        null
    }

    suspend fun unlike(provider: MusicProvider, songId: String): Boolean = try {
        postgrest.rpc(
            function = "unlike_song",
            parameters = buildParams {
                put("p_song_id", songId)
                put("p_provider", provider.id)
            },
        ).decodeAs<Boolean>()
    } catch (e: Exception) {
        Timber.e(e, "unlike failed")
        false
    }

    // ── Saved albums ─────────────────────────────────────────────────────────

    suspend fun listSavedAlbums(): List<SavedAlbumDto> = try {
        postgrest.from("saved_albums").select {
            order("saved_at", Order.DESCENDING)
        }.decodeList<SavedAlbumDto>()
    } catch (e: Exception) {
        Timber.w(e, "listSavedAlbums failed")
        emptyList()
    }

    suspend fun saveAlbum(
        provider: MusicProvider,
        albumId: String,
        title: String? = null,
        artist: String? = null,
        year: Int? = null,
        thumbnailUrl: String? = null,
        songCount: Int? = null,
        durationMs: Int? = null,
    ): SavedAlbumDto? = try {
        // Insert with only the fields the client should provide — user_id is
        // enforced by RLS (auth.uid() = user_id) on the server.
        postgrest.from("saved_albums").insert(buildParams {
            put("provider", provider.id)
            put("album_id", albumId)
            put("title", title)
            put("artist", artist)
            put("year", year)
            put("thumbnail_url", thumbnailUrl)
            put("song_count", songCount)
            put("duration_ms", durationMs)
        }) {
            select()
        }.decodeSingleOrNull<SavedAlbumDto>()
    } catch (e: Exception) {
        Timber.e(e, "saveAlbum failed")
        null
    }

    suspend fun unsaveAlbum(provider: MusicProvider, albumId: String): Boolean = try {
        postgrest.from("saved_albums").delete {
            filter {
                eq("provider", provider.id)
                eq("album_id", albumId)
            }
        }
        true
    } catch (e: Exception) {
        Timber.e(e, "unsaveAlbum failed")
        false
    }

    // ── Saved artists ────────────────────────────────────────────────────────

    suspend fun listSavedArtists(): List<SavedArtistDto> = try {
        postgrest.from("saved_artists").select {
            order("saved_at", Order.DESCENDING)
        }.decodeList<SavedArtistDto>()
    } catch (e: Exception) {
        Timber.w(e, "listSavedArtists failed")
        emptyList()
    }

    suspend fun saveArtist(
        provider: MusicProvider,
        artistId: String,
        name: String? = null,
        thumbnailUrl: String? = null,
        channelId: String? = null,
    ): SavedArtistDto? = try {
        postgrest.from("saved_artists").insert(buildParams {
            put("provider", provider.id)
            put("artist_id", artistId)
            put("name", name)
            put("thumbnail_url", thumbnailUrl)
            put("channel_id", channelId)
        }) {
            select()
        }.decodeSingleOrNull<SavedArtistDto>()
    } catch (e: Exception) {
        Timber.e(e, "saveArtist failed")
        null
    }

    suspend fun unsaveArtist(provider: MusicProvider, artistId: String): Boolean = try {
        postgrest.from("saved_artists").delete {
            filter {
                eq("provider", provider.id)
                eq("artist_id", artistId)
            }
        }
        true
    } catch (e: Exception) {
        Timber.e(e, "unsaveArtist failed")
        false
    }
}
