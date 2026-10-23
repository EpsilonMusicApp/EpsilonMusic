package com.epsilonmusic.app.ai

import android.content.Context
import com.music.innertube.YouTube
import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.epsilonmusic.app.db.InternalDatabase
import com.epsilonmusic.app.db.entities.PlaylistEntity
import com.epsilonmusic.app.db.entities.PlaylistSongMap
import com.epsilonmusic.app.db.entities.SongEntity
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * Builds the "My Taste Mix" playlist from the user's own listening history:
 * the most played songs (liked ones first) seed YouTube's radio/related
 * expansion, and the result replaces the fixed local playlist.
 */
object TasteMixHelper {
    private const val PLAYLIST_NAME = "My Taste Mix"
    private const val MAX_SONGS = 25

    suspend fun generateTasteMix(
        context: Context,
        onLog: (suspend (String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val database = InternalDatabase.newInstance(context)

        onLog?.invoke("Analyzing your taste...")
        val topSongs = database.topSongs(50).firstOrNull().orEmpty()
        if (topSongs.isEmpty()) {
            onLog?.invoke("No listening history available yet")
            return@withContext false
        }

        val likedIds =
            database.likedSongsByPlayTimeAsc().firstOrNull().orEmpty()
                .map { it.song.id }
                .toSet()
        // topSongs is already ordered by total play time; liked songs seed first.
        val seeds =
            topSongs
                .sortedByDescending { if (it.song.id in likedIds) 1 else 0 }
                .take(5)

        onLog?.invoke("Building your mix...")
        val expanded = mutableListOf<SongItem>()
        val seenKeys = mutableSetOf<String>()
        fun keyOf(item: SongItem) =
            "${item.title.trim().lowercase()}|${item.artists.joinToString(", ") { it.name }.trim().lowercase()}"

        for (seed in seeds) {
            val nextResult =
                runCatching {
                    YouTube.next(WatchEndpoint(videoId = seed.song.id)).getOrNull()
                }.getOrNull()

            val candidates = mutableListOf<SongItem>()
            nextResult?.items?.let { candidates.addAll(it) }
            nextResult?.relatedEndpoint?.let { endpoint ->
                runCatching { YouTube.related(endpoint).getOrNull()?.songs }
                    .getOrNull()
                    ?.let { candidates.addAll(it) }
            }

            for (item in candidates) {
                if (seenKeys.add(keyOf(item))) {
                    expanded.add(item)
                }
            }
            if (expanded.size >= MAX_SONGS) break
        }

        val finalSongs =
            if (expanded.size >= 5) {
                expanded.take(MAX_SONGS)
            } else {
                val fallback =
                    topSongs.take(MAX_SONGS).map { song ->
                        SongItem(
                            id = song.song.id,
                            title = song.song.title,
                            artists = song.artists.map { Artist(name = it.name, id = null) },
                            album = null,
                            duration = song.song.duration,
                            thumbnail = song.song.thumbnailUrl.orEmpty()
                        )
                    }
                (expanded + fallback).distinctBy { it.id }.take(MAX_SONGS)
            }

        if (finalSongs.isEmpty()) {
            onLog?.invoke("Could not generate a mix from your history")
            return@withContext false
        }

        onLog?.invoke("Updating playlist...")
        var playlist =
            database.searchPlaylists(PLAYLIST_NAME).firstOrNull()
                ?.find { it.playlist.name == PLAYLIST_NAME }
                ?.playlist
        if (playlist == null) {
            playlist =
                PlaylistEntity(
                    name = PLAYLIST_NAME,
                    bookmarkedAt = LocalDateTime.now(),
                    isLocal = true,
                    isEditable = true
                )
            database.insert(playlist)
        } else {
            database.clearPlaylist(playlist.id)
        }

        finalSongs.forEachIndexed { index, songItem ->
            database.insert(
                SongEntity(
                    id = songItem.id,
                    title = songItem.title,
                    duration = songItem.duration ?: 0,
                    thumbnailUrl = songItem.thumbnail
                )
            )
            database.insert(
                PlaylistSongMap(
                    playlistId = playlist.id,
                    songId = songItem.id,
                    position = index
                )
            )
        }

        onLog?.invoke("Done!")
        true
    }
}
