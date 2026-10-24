

package com.epsilonmusic.app.lyrics

import android.content.Context
import android.util.LruCache
import com.epsilonmusic.app.constants.LyricsProviderOrderKey
import com.epsilonmusic.app.constants.PreferredLyricsProvider
import com.epsilonmusic.app.constants.PreferredLyricsProviderKey
import com.epsilonmusic.app.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.epsilonmusic.app.extensions.toEnum
import com.epsilonmusic.app.models.MediaMetadata
import com.epsilonmusic.app.playback.LyricsWithProvider
import com.epsilonmusic.app.utils.NetworkConnectivityObserver
import com.epsilonmusic.app.utils.dataStore
import com.epsilonmusic.app.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    
    private suspend fun resolveLyricsProviders(): List<LyricsProvider> {
        val preferences = context.dataStore.data.first()
        val orderString = preferences[LyricsProviderOrderKey].orEmpty()

        if (orderString.isNotBlank()) {
            return LyricsProviderRegistry.getOrderedProviders(orderString)
        }

        
        val preferredEnum = preferences[PreferredLyricsProviderKey]
            .toEnum(PreferredLyricsProvider.YOULYPLUS)
        val preferredName = LyricsProviderRegistry.getProviderNameForEnum(preferredEnum)
        val defaultOrder = LyricsProviderRegistry.getDefaultProviderOrder()
        val migratedOrder = listOf(preferredName) + defaultOrder.filter { it != preferredName }
        return migratedOrder.mapNotNull { LyricsProviderRegistry.getProviderByName(it) }
    }



    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<LyricsWithProvider>>()
    private val fetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentLyricsJob: Job? = null

    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
        currentLyricsJob?.cancel()

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return LyricsWithProvider(cached.lyrics, cached.providerName)
        }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }
        
        if (!isNetworkAvailable) {
            return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
        }

        // Shared fetches live in fetchScope, not the first caller's scope, so one caller being
        // cancelled (e.g. collectLatest on a song switch) can't fail the others awaiting it.
        val created =
            fetchScope.async(start = CoroutineStart.LAZY) { fetchFirstSyncedLyrics(mediaMetadata) }
        val existing = inFlightRequests.putIfAbsent(mediaMetadata.id, created)
        val shared =
            existing
                ?: created.also {
                    it.invokeOnCompletion { inFlightRequests.remove(mediaMetadata.id, created) }
                    it.start()
                }
        if (existing != null) created.cancel()
        return shared.await()
    }

    private suspend fun fetchFirstSyncedLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
        val providers = resolveLyricsProviders().filter { it.isEnabled(context) }
        if (providers.isEmpty()) return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")

        return coroutineScope {
            val channel = Channel<LyricsWithProvider?>(providers.size)
            providers.forEach { provider ->
                launch {
                    val lyrics = fetchLyricsFromProvider(provider, mediaMetadata)
                    val result =
                        lyrics
                            ?.takeIf { it != LYRICS_NOT_FOUND && it.isNotBlank() }
                            ?.let { LyricsWithProvider(it, provider.name) }
                    channel.send(result)
                }
            }

            val receivedUnsynced = mutableListOf<LyricsWithProvider>()
            repeat(providers.size) {
                val result = channel.receive() ?: return@repeat
                if (result.lyrics?.let(LyricsUtils::hasSyncedLyrics) == true) {
                    coroutineContext.cancelChildren()
                    cache.put(mediaMetadata.id, listOf(LyricsResult(result.providerName, result.lyrics.orEmpty())))
                    return@coroutineScope result
                }
                receivedUnsynced.add(result)
            }

            val best = receivedUnsynced.firstOrNull() ?: LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
            if (best.lyrics != null && best.lyrics != LYRICS_NOT_FOUND) {
                cache.put(mediaMetadata.id, listOf(LyricsResult(best.providerName, best.lyrics.orEmpty())))
            }
            best
        }
    }

    private suspend fun fetchLyricsFromProvider(
        provider: LyricsProvider,
        mediaMetadata: MediaMetadata,
    ): String? =
        withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
            try {
                provider
                    .getLyrics(
                        mediaMetadata.id,
                        mediaMetadata.title,
                        mediaMetadata.artists.joinToString { it.name },
                        mediaMetadata.duration,
                        mediaMetadata.album?.title,
                    )
                    .getOrElse {
                        if (it is CancellationException) throw it
                        reportException(it)
                        null
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                null
            }
        }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }
        
        if (!isNetworkAvailable) {
            return
        }

        val allResult = java.util.concurrent.CopyOnWriteArrayList<LyricsResult>()
        val providers = resolveLyricsProviders()
        currentLyricsJob = CoroutineScope(SupervisorJob()).launch {
            val jobs = providers.mapNotNull { provider ->
                if (provider.isEnabled(context)) {
                    launch {
                        try {
                            provider.getAllLyrics(mediaId, songTitle, songArtists, duration, album) { lyrics ->
                                val result = LyricsResult(provider.name, lyrics)
                                allResult += result
                                callback(result)
                            }
                        } catch (e: Exception) {
                            reportException(e)
                        }
                    }
                } else null
            }
            jobs.forEach { it.join() }
            cache.put(cacheKey, allResult.toList())
        }

        currentLyricsJob?.join()
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    companion object {
        private const val MAX_CACHE_SIZE = 20
        private const val PROVIDER_TIMEOUT_MS = 5_000L
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

