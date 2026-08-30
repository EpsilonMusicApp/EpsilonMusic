

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.epsilonmusic.app.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epsilonmusic.app.constants.AddToPlaylistSortDescendingKey
import com.epsilonmusic.app.constants.AddToPlaylistSortTypeKey
import com.epsilonmusic.app.constants.PlaylistSortType
import com.epsilonmusic.app.db.MusicDatabase
import com.epsilonmusic.app.extensions.toEnum
import com.epsilonmusic.app.utils.SyncUtils
import com.epsilonmusic.app.utils.dataStore
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
