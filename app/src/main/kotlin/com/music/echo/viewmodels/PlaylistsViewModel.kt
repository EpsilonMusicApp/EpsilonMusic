

@file:OptIn(ExperimentalCoroutinesApi::class)

package echo.music.***REMOVED***.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import echo.music.***REMOVED***.constants.AddToPlaylistSortDescendingKey
import echo.music.***REMOVED***.constants.AddToPlaylistSortTypeKey
import echo.music.***REMOVED***.constants.PlaylistSortType
import echo.music.***REMOVED***.db.MusicDatabase
import echo.music.***REMOVED***.extensions.toEnum
import echo.music.***REMOVED***.utils.SyncUtils
import echo.music.***REMOVED***.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allPlaylists =
        context.dataStore.data
            .map {
                (try { it[AddToPlaylistSortTypeKey] } catch(e: Exception) { null }).toEnum(PlaylistSortType.CREATE_DATE) to ((try { it[AddToPlaylistSortDescendingKey] } catch(e: Exception) { null })
                    ?: true)
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
                database.playlists(sortType, descending)
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    
    suspend fun sync() {
        syncUtils.syncSavedPlaylists()
    }
}
