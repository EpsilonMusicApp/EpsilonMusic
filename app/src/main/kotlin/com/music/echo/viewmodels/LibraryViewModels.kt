

@file:OptIn(ExperimentalCoroutinesApi::class)

package echo.music.***REMOVED***.viewmodels

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import echo.music.***REMOVED***.constants.AlbumFilter
import echo.music.***REMOVED***.constants.AlbumFilterKey
import echo.music.***REMOVED***.constants.AlbumSortDescendingKey
import echo.music.***REMOVED***.constants.AlbumSortType
import echo.music.***REMOVED***.constants.AlbumSortTypeKey
import echo.music.***REMOVED***.constants.ArtistFilter
import echo.music.***REMOVED***.constants.ArtistFilterKey
import echo.music.***REMOVED***.constants.ArtistSongSortDescendingKey
import echo.music.***REMOVED***.constants.ArtistSongSortType
import echo.music.***REMOVED***.constants.ArtistSongSortTypeKey
import echo.music.***REMOVED***.constants.ArtistSortDescendingKey
import echo.music.***REMOVED***.constants.ArtistSortType
import echo.music.***REMOVED***.constants.ArtistSortTypeKey
import echo.music.***REMOVED***.constants.ExportedSongIdsKey
import echo.music.***REMOVED***.constants.HideExplicitKey
import echo.music.***REMOVED***.constants.HideVideoSongsKey
import echo.music.***REMOVED***.constants.HideYoutubeShortsKey
import echo.music.***REMOVED***.constants.LibraryFilter
import echo.music.***REMOVED***.constants.PlaylistSortDescendingKey
import echo.music.***REMOVED***.constants.PlaylistSortType
import echo.music.***REMOVED***.constants.PlaylistSortTypeKey
import echo.music.***REMOVED***.constants.SongFilter
import echo.music.***REMOVED***.constants.SongFilterKey
import echo.music.***REMOVED***.constants.SongSortDescendingKey
import echo.music.***REMOVED***.constants.SongSortType
import echo.music.***REMOVED***.constants.SongSortTypeKey
import echo.music.***REMOVED***.constants.TopSize
import echo.music.***REMOVED***.db.MusicDatabase
import echo.music.***REMOVED***.extensions.filterExplicit
import echo.music.***REMOVED***.extensions.filterExplicitAlbums
import echo.music.***REMOVED***.extensions.filterVideoSongs
import echo.music.***REMOVED***.extensions.filterYoutubeShorts
import echo.music.***REMOVED***.extensions.toEnum
import echo.music.***REMOVED***.playback.DownloadUtil
import echo.music.***REMOVED***.utils.SyncUtils
import echo.music.***REMOVED***.utils.dataStore
import echo.music.***REMOVED***.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class LibrarySongsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allSongs =
        context.dataStore.data
            .map {
                Triple(
                    Triple(
                        (try { it[SongFilterKey] } catch(e: Exception) { null }).toEnum(SongFilter.LIKED),
                        (try { it[SongSortTypeKey] } catch(e: Exception) { null }).toEnum(SongSortType.CREATE_DATE),
                        ((try { it[SongSortDescendingKey] } catch(e: Exception) { null }) ?: true),
                    ),
                    (try { it[ExportedSongIdsKey] } catch(e: Exception) { null }) ?: "",
                    Pair((try { it[HideExplicitKey] } catch(e: Exception) { null }) ?: false, (try { it[HideVideoSongsKey] } catch(e: Exception) { null }) ?: false)
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filterSort, exportedSongIds, hideConfig) ->
                val (filter, sortType, descending) = filterSort
                val (hideExplicit, hideVideoSongs) = hideConfig
                when (filter) {
                    SongFilter.LIBRARY -> database.songs(sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    SongFilter.LIKED -> database.likedSongs(sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    SongFilter.DOWNLOADED -> database.downloadedSongs(sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    SongFilter.UPLOADED -> database.uploadedSongs(sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    SongFilter.EXPORTED -> {
                        val ids = exportedSongIds.split(",").filter { it.isNotBlank() }
                        database.getSongsByIdsFlow(ids).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun syncLikedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongs() }
    }

    fun syncLibrarySongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLibrarySongs() }
    }

    fun syncUploadedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncUploadedSongs() }
    }
}

@HiltViewModel
class LibraryArtistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allArtists =
        context.dataStore.data
            .map {
                Triple(
                    (try { it[ArtistFilterKey] } catch(e: Exception) { null }).toEnum(ArtistFilter.LIKED),
                    (try { it[ArtistSortTypeKey] } catch(e: Exception) { null }).toEnum(ArtistSortType.CREATE_DATE),
                    (try { it[ArtistSortDescendingKey] } catch(e: Exception) { null }) ?: true,
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filter, sortType, descending) ->
                when (filter) {
                    ArtistFilter.LIKED -> database.artistsBookmarked(sortType, descending)
                    ArtistFilter.LIBRARY -> database.artists(sortType, descending)
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncArtistsSubscriptions() }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allArtists.collect { artists ->
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
    }
}

@HiltViewModel
class LibraryAlbumsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allAlbums =
        context.dataStore.data
            .map {
                Pair(
                    Triple(
                        (try { it[AlbumFilterKey] } catch(e: Exception) { null }).toEnum(AlbumFilter.LIKED),
                        (try { it[AlbumSortTypeKey] } catch(e: Exception) { null }).toEnum(AlbumSortType.CREATE_DATE),
                        (try { it[AlbumSortDescendingKey] } catch(e: Exception) { null }) ?: true,
                    ),
                    (try { it[HideExplicitKey] } catch(e: Exception) { null }) ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filterSort, hideExplicit) ->
                val (filter, sortType, descending) = filterSort
                when (filter) {
                    AlbumFilter.LIKED -> database.albumsLiked(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                    AlbumFilter.LIBRARY -> database.albums(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                    AlbumFilter.UPLOADED -> database.albumsUploaded(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedAlbums() }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allAlbums.collect { albums ->
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

@HiltViewModel
class LibraryPlaylistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allPlaylists =
        context.dataStore.data
            .map {
                Triple(
                    (try { it[PlaylistSortTypeKey] } catch(e: Exception) { null }).toEnum(PlaylistSortType.CREATE_DATE),
                    (try { it[PlaylistSortDescendingKey] } catch(e: Exception) { null }) ?: true,
                    (try { it[HideYoutubeShortsKey] } catch(e: Exception) { null }) ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending, hideYoutubeShorts) ->
                database.playlists(sortType, descending).map { it.filterYoutubeShorts(hideYoutubeShorts) }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncSavedPlaylists() }
    }

    val topValue =
        context.dataStore.data
            .map { (try { it[TopSize] } catch(e: Exception) { null }) ?: "50" }
            .distinctUntilChanged()
}

@HiltViewModel
class ArtistSongsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val artistId = savedStateHandle.get<String>("artistId")!!
    val artist =
        database
            .artist(artistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val songs =
        context.dataStore.data
            .map {
                Triple(
                    (try { it[ArtistSongSortTypeKey] } catch(e: Exception) { null }).toEnum(ArtistSongSortType.CREATE_DATE) to ((try { it[ArtistSongSortDescendingKey] } catch(e: Exception) { null })
                        ?: true),
                    (try { it[HideExplicitKey] } catch(e: Exception) { null }) ?: false,
                    (try { it[HideVideoSongsKey] } catch(e: Exception) { null }) ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (sortDesc, hideExplicit, hideVideoSongs) ->
                val (sortType, descending) = sortDesc
                database.artistSongs(artistId, sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@HiltViewModel
class LibraryMixViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    val syncAllLibrary = {
         viewModelScope.launch(Dispatchers.IO) {
             syncUtils.tryAutoSync()
         }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            syncUtils.performFullSyncSuspend()
            _isRefreshing.value = false
        }
    }

    val topValue =
        context.dataStore.data
            .map { (try { it[TopSize] } catch(e: Exception) { null }) ?: "50" }
            .distinctUntilChanged()
    var artists =
        database
            .artistsBookmarked(
                ArtistSortType.CREATE_DATE,
                true,
            ).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var albums = context.dataStore.data
        .map { (try { it[HideExplicitKey] } catch(e: Exception) { null }) ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.albumsLiked(AlbumSortType.CREATE_DATE, true).map { it.filterExplicitAlbums(hideExplicit) }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var playlists = context.dataStore.data
        .map { (try { it[HideYoutubeShortsKey] } catch(e: Exception) { null }) ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideYoutubeShorts ->
            database.playlists(PlaylistSortType.CREATE_DATE, true).map { it.filterYoutubeShorts(hideYoutubeShorts) }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            albums.collect { albums ->
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
        viewModelScope.launch(Dispatchers.IO) {
            artists.collect { artists ->
                artists
                    .map { it.artist }
                    .filter {
                        it.thumbnailUrl == null ||
                                Duration.between(
                                    it.lastUpdateTime,
                                    LocalDateTime.now(),
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
    }
}

@HiltViewModel
class LibraryViewModel
@Inject
constructor() : ViewModel() {
    private val curScreen = mutableStateOf(LibraryFilter.LIBRARY)
    val filter: MutableState<LibraryFilter> = curScreen
}
