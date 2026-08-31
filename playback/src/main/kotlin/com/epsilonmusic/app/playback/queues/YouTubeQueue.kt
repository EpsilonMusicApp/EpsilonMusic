

package com.epsilonmusic.app.playback.queues

import androidx.media3.common.MediaItem
import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import com.epsilonmusic.app.extensions.toMediaItem
import com.epsilonmusic.app.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubeQueue(
    private var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private var continuation: String? = null
    private var retryCount = 0
    private val maxRetries = 3

    override suspend fun getInitialStatus(): Queue.Status {
        return withContext(IO) {
            var lastException: Throwable? = null
            
            
            for (attempt in 0..maxRetries) {
                try {
                    val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    retryCount = 0
                    
                    val mediaItems = nextResult.items.map { it.toMediaItem() }
                    
                    // Determine the correct starting index. YouTube's "selected" flag
                    // is not always reliable — if we have a preloadItem (the song the
                    // user tapped), find its index in the returned list. This prevents
                    // the wrong song from playing when YouTube returns a radio queue
                    // where the "selected" flag is missing or points to a different song.
                    var index = nextResult.currentIndex ?: 0
                    if (preloadItem != null) {
                        val preloadId = preloadItem.id
                        val foundIndex = mediaItems.indexOfFirst { it.mediaId == preloadId }
                        if (foundIndex >= 0) {
                            index = foundIndex
                        }
                    }
                    // Safety: clamp index to valid range
                    index = index.coerceIn(0, (mediaItems.size - 1).coerceAtLeast(0))
                    
                    return@withContext Queue.Status(
                        title = nextResult.title,
                        items = mediaItems,
                        mediaItemIndex = index,
                    )
                } catch (e: Exception) {
                    lastException = e
                    
                    if (attempt == 0 && endpoint.videoId != null && endpoint.playlistId == null) {
                        endpoint = WatchEndpoint(
                            videoId = endpoint.videoId,
                            playlistId = "RDAMVM${endpoint.videoId}"
                        )
                    }
                }
            }
            throw lastException ?: Exception("Failed to get initial status")
        }
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        return withContext(IO) {
            var lastException: Throwable? = null
            
            for (attempt in 0..maxRetries) {
                try {
                    val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    retryCount = 0
                    return@withContext nextResult.items.map { it.toMediaItem() }
                } catch (e: Exception) {
                    lastException = e
                    retryCount++
                    if (retryCount >= maxRetries) {
                        continuation = null 
                    }
                }
            }
            throw lastException ?: Exception("Failed to get next page")
        }
    }

    companion object {
        
        fun radio(song: MediaMetadata): YouTubeQueue {
            return YouTubeQueue(
                WatchEndpoint(videoId = song.id),
                song
            )
        }
    }
}
