

package com.epsilonmusic.app.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.epsilonmusic.app.constants.HideVideoSongsKey
import com.epsilonmusic.app.constants.PlaylistSongSortDescendingKey
import com.epsilonmusic.app.constants.PlaylistSongSortType
import com.epsilonmusic.app.constants.PlaylistSongSortTypeKey
import com.epsilonmusic.app.db.MusicDatabase
import com.epsilonmusic.app.db.entities.PlaylistSong
import com.epsilonmusic.app.extensions.reversed
import com.epsilonmusic.app.extensions.toEnum
import com.epsilonmusic.app.models.toMediaMetadata
import com.epsilonmusic.app.utils.SyncUtils
import com.epsilonmusic.app.utils.dataStore
import com.epsilonmusic.app.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LocalPlaylistViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val playlistId = savedStateHandle.get<String>("playlistId")!!
    val playlist =
        database
            .playlist(playlistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val playlistSongs: StateFlow<List<PlaylistSong>> =
        combine(
            database.playlistSongs(playlistId),
            context.dataStore.data
                .map {
                    Triple(
                        (try { it[PlaylistSongSortTypeKey] } catch(e: Exception) { null }).toEnum(PlaylistSongSortType.CUSTOM),
                        (try { it[PlaylistSongSortDescendingKey] } catch(e: Exception) { null }) ?: true,
                        (try { it[HideVideoSongsKey] } catch(e: Exception) { null }) ?: false
                    )
                }.distinctUntilChanged(),
        ) { songs, (sortType, sortDescending, hideVideoSongs) ->
            val filteredSongs = if (hideVideoSongs) {
                songs.filter { !it.song.song.isVideo }
            } else {
                songs
            }
            when (sortType) {
                PlaylistSongSortType.CUSTOM -> filteredSongs
                PlaylistSongSortType.CREATE_DATE -> filteredSongs.sortedBy { it.map.id }
                PlaylistSongSortType.NAME -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    filteredSongs.sortedWith(compareBy(collator) { it.song.song.title })
                }
                PlaylistSongSortType.ARTIST -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    filteredSongs
                        .sortedWith(compareBy(collator) { song -> song.song.artists.joinToString("") { it.name } })
                        .groupBy { it.song.album?.title }
                        .flatMap { (_, songsByAlbum) ->
                            songsByAlbum.sortedBy {
                                it.song.artists.joinToString(
                                    ""
                                ) { it.name }
                            }
                        }
                }

                PlaylistSongSortType.PLAY_TIME -> filteredSongs.sortedBy { it.song.song.totalPlayTime }
            }.reversed(sortDescending && sortType != PlaylistSongSortType.CUSTOM)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            // Defensive: any failure inside the sync trigger must NOT crash the app.
            // The sync itself runs in SyncUtils.syncScope (which has its own
            // CoroutineExceptionHandler), but we still wrap the trigger here so a
            // future regression in syncUtils cannot take down the playlist screen.
            try {
                playlist.first { it != null }?.playlist?.browseId?.let { browseId ->
                    syncUtils.syncPlaylist(browseId, playlistId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
            }
        }

        viewModelScope.launch {
            try {
                val sortedSongs =
                    playlistSongs.first().sortedWith(compareBy({ it.map.position }, { it.map.id }))
                database.transaction {
                    sortedSongs.forEachIndexed { index, playlistSong ->
                        if (playlistSong.map.position != index) {
                            update(playlistSong.map.copy(position = index))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Position normalization is best-effort; do not crash the app on failure.
                Timber.e(e, "Failed to normalize playlist song positions")
            }
        }
    }

    private val _suggestions = MutableStateFlow<List<SongItem>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    private var hasFetchedSuggestions = false

    fun fetchSuggestions() {
        if (hasFetchedSuggestions) return
        hasFetchedSuggestions = true

        viewModelScope.launch(Dispatchers.IO) {
            val songs = playlistSongs.value
            if (songs.isNotEmpty()) {
                val lastSong = songs.last().song.song
                if (lastSong.id.isNotEmpty()) {
                    YouTube.next(WatchEndpoint(videoId = lastSong.id)).onSuccess { nextResult ->
                        val existingIds = songs.map { it.song.song.id }.toSet()
                        _suggestions.value = nextResult.items
                            .filterIsInstance<SongItem>()
                            .filterNot { it.id in existingIds }
                            .take(10)
                    }
                }
            }
        }
    }

    fun addSuggestedSong(song: SongItem) {
        viewModelScope.launch(Dispatchers.IO) {
            playlist.value?.let { currentPlaylist ->
                database.transaction {
                    insert(song.toMediaMetadata())
                    addSongToPlaylist(currentPlaylist, listOf(song.id))
                }
                _suggestions.value = _suggestions.value.filter { it.id != song.id }
            }
        }
    }
}
