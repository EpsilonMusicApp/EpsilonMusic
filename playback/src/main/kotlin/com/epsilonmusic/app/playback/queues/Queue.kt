

package com.epsilonmusic.app.playback.queues

import androidx.media3.common.MediaItem
import com.epsilonmusic.app.extensions.metadata
import com.epsilonmusic.app.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        /**
         * Filter out explicit tracks. CRITICAL: also recalculates [mediaItemIndex]
         * so it still points to the SAME song in the filtered list.
         *
         * Previously this function only filtered [items] and left [mediaItemIndex]
         * unchanged — which caused the "wrong song plays" bug: if filtering removed
         * songs BEFORE the currently-selected index, the index would point to a
         * different song in the filtered list (or go out of bounds), and the player
         * would start playing the wrong track.
         */
        fun filterExplicit(enabled: Boolean = true): Status {
            if (!enabled) return this
            val selectedItem = items.getOrNull(mediaItemIndex)
            val filteredItems = items.filterExplicit()
            val newIndex = if (selectedItem != null) {
                filteredItems.indexOfFirst { it.mediaId == selectedItem.mediaId }
                    .coerceAtLeast(0)
            } else {
                mediaItemIndex.coerceIn(0, (filteredItems.size - 1).coerceAtLeast(0))
            }
            return copy(items = filteredItems, mediaItemIndex = newIndex)
        }

        /**
         * Filter out video-only tracks. CRITICAL: also recalculates [mediaItemIndex]
         * so it still points to the SAME song in the filtered list.
         * See [filterExplicit] for the rationale.
         */
        fun filterVideoSongs(disableVideos: Boolean = false): Status {
            if (!disableVideos) return this
            val selectedItem = items.getOrNull(mediaItemIndex)
            val filteredItems = items.filterVideoSongs(true)
            val newIndex = if (selectedItem != null) {
                filteredItems.indexOfFirst { it.mediaId == selectedItem.mediaId }
                    .coerceAtLeast(0)
            } else {
                mediaItemIndex.coerceIn(0, (filteredItems.size - 1).coerceAtLeast(0))
            }
            return copy(items = filteredItems, mediaItemIndex = newIndex)
        }
    }
}

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.metadata?.explicit == true
        }
    } else {
        this
    }

fun List<MediaItem>.filterVideoSongs(disableVideos: Boolean = false) =
    if (disableVideos) {
        filterNot { it.metadata?.isVideoSong == true }
    } else {
        this
    }
