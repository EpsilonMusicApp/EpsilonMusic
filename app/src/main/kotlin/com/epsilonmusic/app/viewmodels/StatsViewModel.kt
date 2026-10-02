

package com.epsilonmusic.app.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.epsilonmusic.app.constants.HideVideoSongsKey
import com.epsilonmusic.app.constants.statToPeriod
import com.epsilonmusic.app.db.MusicDatabase
import com.epsilonmusic.app.db.entities.EventWithSong
import com.epsilonmusic.app.ui.screens.OptionStats
import com.epsilonmusic.app.utils.dataStore
import com.epsilonmusic.app.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
) : ViewModel() {
    val selectedOption = MutableStateFlow(OptionStats.CONTINUOUS)
    val indexChips = MutableStateFlow(0)

    val totalPlayTime =
        combine(
            selectedOption,
            indexChips,
        ) { selection, t -> Pair(selection, t) }
            .flatMapLatest { (selection, t) ->
                val fromTimeStamp = statToPeriod(selection, t)
                val toTimeStamp = if (selection == OptionStats.CONTINUOUS || t == 0) {
                    LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
                } else {
                    statToPeriod(selection, t - 1)
                }
                database.getTotalPlayTimeInRange(fromTimeStamp, toTimeStamp)
            }.map { it ?: 0L }
            .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val allTimePlayTime = database.getTotalPlayTimeInRange(0, Long.MAX_VALUE)
        .map { it ?: 0L }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val uniqueSongsCount =
        combine(
            selectedOption,
            indexChips,
        ) { selection, t -> Pair(selection, t) }
            .flatMapLatest { (selection, t) ->
                val fromTimeStamp = statToPeriod(selection, t)
                val toTimeStamp = if (selection == OptionStats.CONTINUOUS || t == 0) {
                    LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
                } else {
                    statToPeriod(selection, t - 1)
                }
                database.getUniqueSongCountInRange(fromTimeStamp, toTimeStamp)
            }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val uniqueArtistsCount =
        combine(
            selectedOption,
            indexChips,
        ) { selection, t -> Pair(selection, t) }
            .flatMapLatest { (selection, t) ->
                val fromTimeStamp = statToPeriod(selection, t)
                val toTimeStamp = if (selection == OptionStats.CONTINUOUS || t == 0) {
                    LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
                } else {
                    statToPeriod(selection, t - 1)
                }
                database.getUniqueArtistCountInRange(fromTimeStamp, toTimeStamp)
            }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val uniqueAlbumsCount =
        combine(
            selectedOption,
            indexChips,
        ) { selection, t -> Pair(selection, t) }
            .flatMapLatest { (selection, t) ->
                val fromTimeStamp = statToPeriod(selection, t)
                val toTimeStamp = if (selection == OptionStats.CONTINUOUS || t == 0) {
                    LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
                } else {
                    statToPeriod(selection, t - 1)
                }
                database.getUniqueAlbumCountInRange(fromTimeStamp, toTimeStamp)
            }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val allTimeSongsCount = database.getUniqueSongCountInRange(0, Long.MAX_VALUE)
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val allTimeArtistsCount = database.getUniqueArtistCountInRange(0, Long.MAX_VALUE)
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val allTimeAlbumsCount = database.getUniqueAlbumCountInRange(0, Long.MAX_VALUE)
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val mostPlayedSongsStats =
        combine(
            selectedOption,
            indexChips,
            context.dataStore.data.map { (try { it[HideVideoSongsKey] } catch(e: Exception) { null }) ?: false }.distinctUntilChanged()
        ) { first, second, third -> Triple(first, second, third) }
            .flatMapLatest { (selection, t, hideVideoSongs) ->
                database
                    .mostPlayedSongsStats(
                        fromTimeStamp = statToPeriod(selection, t),
                        limit = -1,
                        toTimeStamp =
                        if (selection == OptionStats.CONTINUOUS || t == 0) {
                            LocalDateTime
                                .now()
                                .toInstant(
                                    ZoneOffset.UTC,
                                ).toEpochMilli()
                        } else {
                            statToPeriod(selection, t - 1)
                        },
                    ).map { songs ->
                        if (hideVideoSongs) songs.filter { !it.isVideo } else songs
                    }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mostPlayedSongs =
        combine(
            selectedOption,
            indexChips,
            context.dataStore.data.map { (try { it[HideVideoSongsKey] } catch(e: Exception) { null }) ?: false }.distinctUntilChanged()
        ) { first, second, third -> Triple(first, second, third) }
            .flatMapLatest { (selection, t, hideVideoSongs) ->
                database
                    .mostPlayedSongs(
                        fromTimeStamp = statToPeriod(selection, t),
                        limit = -1,
                        toTimeStamp =
                        if (selection == OptionStats.CONTINUOUS || t == 0) {
                            LocalDateTime
                                .now()
                                .toInstant(
                                    ZoneOffset.UTC,
                                ).toEpochMilli()
                        } else {
                            statToPeriod(selection, t - 1)
                        },
                    ).map { songs ->
                        if (hideVideoSongs) songs.filter { !it.song.isVideo } else songs
                    }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mostPlayedArtists =
        combine(
            selectedOption,
            indexChips,
        ) { first, second -> Pair(first, second) }
            .flatMapLatest { (selection, t) ->
                database
                    .mostPlayedArtists(
                        statToPeriod(selection, t),
                        limit = -1,
                        toTimeStamp =
                        if (selection == OptionStats.CONTINUOUS || t == 0) {
                            LocalDateTime
                                .now()
                                .toInstant(
                                    ZoneOffset.UTC,
                                ).toEpochMilli()
                        } else {
                            statToPeriod(selection, t - 1)
                        },
                    ).map { artists ->
                        artists.filter { it.artist.isYouTubeArtist }
                    }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mostPlayedAlbums =
        combine(
            selectedOption,
            indexChips,
        ) { first, second -> Pair(first, second) }
            .flatMapLatest { (selection, t) ->
                database.mostPlayedAlbums(
                    statToPeriod(selection, t),
                    limit = -1,
                    toTimeStamp =
                    if (selection == OptionStats.CONTINUOUS || t == 0) {
                        LocalDateTime
                            .now()
                            .toInstant(
                                ZoneOffset.UTC,
                            ).toEpochMilli()
                    } else {
                        statToPeriod(selection, t - 1)
                    },
                )
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val firstEvent =
        database
            .firstEvent()
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /**
     * Dominant listening "vibe" for the currently selected stats period, with a
     * comparison against the previous period. Songs are categorized from their
     * title/artist keywords (phonk, lofi/chill, sad, romantic, high energy, mixed).
     */
    val vibeSummary =
        combine(
            selectedOption,
            indexChips,
        ) { selection, t -> Pair(selection, t) }
            .flatMapLatest { (selection, t) ->
                val fromTimeStamp = statToPeriod(selection, t)
                val toTimeStamp = if (selection == OptionStats.CONTINUOUS || t == 0) {
                    LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
                } else {
                    statToPeriod(selection, t - 1)
                }
                val previousFromTimeStamp = statToPeriod(selection, t + 1)
                val hasPreviousPeriod = previousFromTimeStamp < fromTimeStamp

                if (hasPreviousPeriod) {
                    combine(
                        database.eventsForPeriod(fromTimeStamp, toTimeStamp),
                        database.eventsForPeriod(previousFromTimeStamp, fromTimeStamp),
                    ) { currentEvents, previousEvents ->
                        computeVibeSummary(currentEvents, previousEvents, hasPreviousPeriod)
                    }
                } else {
                    database.eventsForPeriod(fromTimeStamp, toTimeStamp).map { currentEvents ->
                        computeVibeSummary(currentEvents, emptyList(), false)
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, VibeSummary())

    init {
        viewModelScope.launch {
            mostPlayedArtists.collect { artists ->
                artists
                    .map { it.artist }
                    .filter {
                        it.thumbnailUrl == null || Duration.between(
                            it.lastUpdateTime,
                            LocalDateTime.now()
                        ) > Duration.ofDays(10)
                    }.forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query {
                                update(artist, artistPage)
                            }
                        }
                    }
            }
        }
        viewModelScope.launch {
            mostPlayedAlbums.collect { albums ->
                albums
                    .filter {
                        it.album.songCount == 0
                    }.forEach { album ->
                        YouTube
                            .album(album.id)
                            .onSuccess { albumPage ->
                                database.query {
                                    update(album.album, albumPage, album.artists)
                                }
                            }.onFailure {
                                reportException(it)
                                if (it.message?.contains("NOT_FOUND") == true) {
                                    database.query {
                                        delete(album.album)
                                    }
                                }
                            }
                    }
            }
        }
    }
}

/** Dominant listening vibe for a stats period, plus change vs the previous period. */
data class VibeSummary(
    val dominantVibe: String = "🎧 Mixed",
    val dominantVibePlayTime: Long = 0L,
    val previousVibePlayTime: Long = 0L,
    val percentageChange: Int = 0,
    val hasPreviousPeriod: Boolean = false,
)

private fun categorizeVibe(songTitle: String, artistName: String): String {
    val text = "$songTitle $artistName".lowercase()
    return when {
        text.contains("phonk") || text.contains("drift") -> "🔥 Phonk"
        text.contains("lofi") || text.contains("chill") || text.contains("slowed") || text.contains("reverb") -> "🌙 Chill"
        text.contains("sad") || text.contains("broken") || text.contains("lonely") -> "💔 Sad"
        text.contains("love") || text.contains("romantic") || text.contains("heart") -> "❤️ Romantic"
        text.contains("bass") || text.contains("remix") || text.contains("hardstyle") || text.contains("edm") -> "⚡ High Energy"
        else -> "🎧 Mixed"
    }
}

private fun computeVibeSummary(
    currentEvents: List<EventWithSong>,
    previousEvents: List<EventWithSong>,
    hasPreviousPeriod: Boolean,
): VibeSummary {
    fun vibeOf(events: List<EventWithSong>): Map<String, Long> =
        events
            .groupBy { e ->
                val song = e.song
                if (song != null) {
                    categorizeVibe(
                        song.title,
                        song.artists.joinToString { it.name },
                    )
                } else {
                    "🎧 Mixed"
                }
            }
            .mapValues { (_, grouped) -> grouped.sumOf { it.event.playTime } }

    val currentVibes = vibeOf(currentEvents)
    val dominant = currentVibes.maxByOrNull { it.value }
        ?: return VibeSummary(hasPreviousPeriod = hasPreviousPeriod)

    val previousPlayTime = if (hasPreviousPeriod) {
        vibeOf(previousEvents)[dominant.key] ?: 0L
    } else {
        0L
    }
    val change = when {
        !hasPreviousPeriod || previousPlayTime == 0L -> 100
        else -> ((dominant.value - previousPlayTime).toDouble() / previousPlayTime * 100).toInt()
    }
    return VibeSummary(
        dominantVibe = dominant.key,
        dominantVibePlayTime = dominant.value,
        previousVibePlayTime = previousPlayTime,
        percentageChange = change,
        hasPreviousPeriod = hasPreviousPeriod,
    )
}
