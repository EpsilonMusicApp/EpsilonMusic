package com.epsilonmusic.app.spotify

import com.epsilonmusic.app.db.MusicDatabase
import com.epsilonmusic.app.models.SimilarRecommendation
import com.epsilonmusic.app.spotifyimport.SpotifyImportRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoadSpotifyRecommendationsUseCase @Inject constructor(
    private val spotifyImportRepository: SpotifyImportRepository,
    private val database: MusicDatabase,
    private val spotifyPlaybackResolver: SpotifyPlaybackResolver
) {
    suspend operator fun invoke(
        hideExplicit: Boolean,
        fromTimeStamp: Long
    ): List<SimilarRecommendation>? = withContext(Dispatchers.IO) {
        val session = spotifyImportRepository.restoreSession()
        if (!session.isAuthenticated) return@withContext null

        val localArtists = database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
            .filter { it.artist.isYouTubeArtist }.shuffled().take(3)
        val localSongs = database.mostPlayedSongs(fromTimeStamp, limit = 10).first()
            .filter { it.album != null }.shuffled().take(2)

        if (localArtists.isEmpty() && localSongs.isEmpty()) return@withContext null

        val recommendations = mutableListOf<SimilarRecommendation>()

        coroutineScope {
            localArtists.map { artist ->
                async {
                    try {
                        val spotifyArtistId = getSpotifyArtistId(artist.artist.name) ?: return@async null
                        val result = Spotify.recommendations(seedArtistIds = listOf(spotifyArtistId), limit = 8).getOrNull()
                        val tracks = result?.tracks.orEmpty()
                        if (tracks.isEmpty()) return@async null
                        val resolved = spotifyPlaybackResolver.resolveAndFilter(tracks, hideExplicit)
                        if (resolved.isEmpty()) return@async null
                        SimilarRecommendation(title = artist, items = resolved)
                    } catch (e: Exception) { null }
                }
            }.awaitAll().filterNotNull().let { recommendations.addAll(it) }
        }

        coroutineScope {
            localSongs.map { song ->
                async {
                    try {
                        val spotifyTrackId = getSpotifyTrackId(song.song.title, song.artists.firstOrNull()?.name) ?: return@async null
                        val result = Spotify.recommendations(seedTrackIds = listOf(spotifyTrackId), limit = 8).getOrNull()
                        val tracks = result?.tracks.orEmpty()
                        if (tracks.isEmpty()) return@async null
                        val resolved = spotifyPlaybackResolver.resolveAndFilter(tracks, hideExplicit)
                        if (resolved.isEmpty()) return@async null
                        SimilarRecommendation(title = song, items = resolved)
                    } catch (e: Exception) { null }
                }
            }.awaitAll().filterNotNull().let { recommendations.addAll(it) }
        }

        recommendations.ifEmpty { null }
    }

    private suspend fun getSpotifyArtistId(artistName: String): String? {
        return try {
            val result = Spotify.search(artistName, types = listOf("artist"), limit = 5).getOrNull()
            result?.artists?.items?.firstOrNull()?.id
        } catch (e: Exception) { null }
    }

    private suspend fun getSpotifyTrackId(trackName: String, artistName: String?): String? {
        return try {
            val query = if (artistName != null) "$trackName $artistName" else trackName
            val result = Spotify.search(query, types = listOf("track"), limit = 5).getOrNull()
            result?.tracks?.items?.firstOrNull()?.id
        } catch (e: Exception) { null }
    }
}
