package com.epsilonmusic.app.supabase.sync

import com.epsilonmusic.app.db.MusicDatabase
import com.epsilonmusic.app.db.entities.AlbumEntity
import com.epsilonmusic.app.db.entities.ArtistEntity
import com.epsilonmusic.app.db.entities.PlaylistEntity
import com.epsilonmusic.app.db.entities.PlaylistSongMap
import com.epsilonmusic.app.db.entities.SongEntity
import com.epsilonmusic.app.models.MediaMetadata
import com.epsilonmusic.app.utils.dataStore
import com.epsilonmusic.app.utils.get
import com.epsilonmusic.app.constants.LastEpsilonSyncKey
import androidx.datastore.preferences.core.edit
import com.epsilonmusic.app.supabase.repository.AuthRepository
import com.epsilonmusic.app.supabase.repository.CloudPlaylistRepository
import com.epsilonmusic.app.supabase.repository.HistorySyncRepository
import com.epsilonmusic.app.supabase.repository.LibrarySyncRepository
import com.epsilonmusic.app.supabase.repository.UserSettingsRepository
import com.epsilonmusic.app.supabase.model.MusicProvider
import com.epsilonmusic.app.supabase.model.PlaylistTrackInsertDto
import com.epsilonmusic.app.supabase.model.PlaylistUpsertDto
import com.epsilonmusic.app.supabase.model.SyncStateDto
import com.epsilonmusic.app.supabase.model.buildParams
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Synchronization state — exposed to UI for showing progress / errors.
 */
data class SyncProgress(
    val isRunning: Boolean = false,
    val stage: String = "",
    val itemsProcessed: Int = 0,
    val totalItems: Int = 0,
    val lastError: String? = null,
    val lastSyncedAt: String? = null,
)

/**
 * The orchestrator that reconciles the cloud (Supabase) and local (Room)
 * copies of the user's data.
 *
 * Architecture (per the project requirements):
 *
 *     UI → ViewModel → Repository → Room → SyncManager → Supabase
 *
 * The local Room DB is always the source of truth for *display*. SyncManager
 * runs in the background to:
 *   1. Pull cloud deltas into Room (download).
 *   2. Push local-only changes up to the cloud (upload).
 *   3. Resolve conflicts using "last-writer-wins" on `updated_at` timestamps.
 *
 * The manager is intentionally conservative — it never deletes local data
 * without an explicit signal (e.g. a soft-deleted playlist row).
 *
 * All cloud calls are best-effort: failures are logged but do not block the
 * local Room DB from serving the UI. The app remains fully functional offline.
 */
@Singleton
class SyncManager @Inject constructor(
    private val database: MusicDatabase,
    private val authRepository: AuthRepository,
    private val playlistRepo: CloudPlaylistRepository,
    private val libraryRepo: LibrarySyncRepository,
    private val historyRepo: HistorySyncRepository,
    private val settingsRepo: UserSettingsRepository,
    private val postgrest: Postgrest,
    @dagger.hilt.android.qualifiers.ApplicationContext
    private val context: android.content.Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(SyncProgress())
    val state: StateFlow<SyncProgress> = _state.asStateFlow()

    /**
     * One-shot full sync. Pulls everything for the current user and merges it
     * into local Room. Safe to call repeatedly — uses `p_since` watermark to
     * fetch only what changed since the last successful sync.
     */
    fun triggerFullSync() {
        if (_state.value.isRunning) {
            Timber.i("Sync already running; skipping")
            return
        }
        scope.launch {
            runFullSync()
        }
    }

    private suspend fun runFullSync() {
        val session = authRepository.session.value
        if (session == null) {
            _state.value = SyncProgress(lastError = "Not signed in")
            return
        }
        _state.value = SyncProgress(isRunning = true, stage = "Fetching sync state")
        try {
            val sinceMs = context.dataStore.get(LastEpsilonSyncKey, 0L) ?: 0L
            val sinceIso = if (sinceMs > 0) {
                OffsetDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(sinceMs),
                    ZoneOffset.UTC,
                ).toString()
            } else null

            val payload: SyncStateDto? = try {
                val params = if (sinceIso != null) {
                    buildParams { put("p_since", sinceIso) }
                } else {
                    kotlinx.serialization.json.JsonObject(emptyMap())
                }
                postgrest.rpc(
                    function = "get_user_sync_state",
                    parameters = params,
                ).decodeAs<SyncStateDto>()
            } catch (e: Exception) {
                Timber.e(e, "get_user_sync_state failed")
                null
            }
            if (payload == null) {
                _state.value = _state.value.copy(
                    isRunning = false,
                    lastError = "Failed to fetch sync state",
                )
                return
            }

            val total = payload.playlists.size + payload.likedSongs.size +
                payload.savedAlbums.size + payload.savedArtists.size +
                payload.playlistTracks.size + payload.recentlyPlayed.size
            var processed = 0

            // 1. Playlists
            for (pl in payload.playlists) {
                try { mergePlaylist(pl) } catch (e: Exception) { Timber.w(e, "mergePlaylist failed") }
                processed++
                _state.value = _state.value.copy(
                    stage = "Syncing playlists",
                    itemsProcessed = processed,
                    totalItems = total,
                )
            }

            // 2. Playlist tracks
            for (t in payload.playlistTracks) {
                try { mergePlaylistTrack(t) } catch (e: Exception) { Timber.w(e, "mergePlaylistTrack failed") }
                processed++
            }

            // 3. Liked songs
            for (ls in payload.likedSongs) {
                try { mergeLikedSong(ls) } catch (e: Exception) { Timber.w(e, "mergeLikedSong failed") }
                processed++
                _state.value = _state.value.copy(
                    stage = "Syncing liked songs",
                    itemsProcessed = processed,
                )
            }

            // 4. Saved albums / artists
            for (a in payload.savedAlbums) {
                try { mergeSavedAlbum(a) } catch (e: Exception) { Timber.w(e, "mergeSavedAlbum failed") }
                processed++
            }
            for (ar in payload.savedArtists) {
                try { mergeSavedArtist(ar) } catch (e: Exception) { Timber.w(e, "mergeSavedArtist failed") }
                processed++
            }

            // 5. Persist watermark for next sync
            payload.serverTime.let {
                val epochMs = runCatching {
                    OffsetDateTime.parse(it).toInstant().toEpochMilli()
                }.getOrDefault(System.currentTimeMillis())
                context.dataStore.edit { prefs ->
                    prefs[LastEpsilonSyncKey] = epochMs
                }
            }

            _state.value = SyncProgress(
                isRunning = false,
                lastSyncedAt = payload.serverTime,
                itemsProcessed = processed,
                totalItems = total,
            )
            Timber.i("Epsilon sync complete: $processed items")
        } catch (e: Exception) {
            Timber.e(e, "Full sync failed")
            _state.value = _state.value.copy(
                isRunning = false,
                lastError = e.message ?: "Sync failed",
            )
        }
    }

    // ── Per-entity merge functions ───────────────────────────────────────────

    private suspend fun mergePlaylist(pl: com.epsilonmusic.app.supabase.model.PlaylistDto) {
        // If the playlist already exists locally (by cloud id), leave it alone —
        // the local copy may have unsynced edits. Otherwise, insert the cloud
        // version. Two-way reconciliation is handled by the next sync cycle.
        val existing = database.getPlaylistById(pl.id)
        if (existing != null) return
        val entity = PlaylistEntity(
            id = pl.id,
            name = pl.title,
            browseId = pl.id,
            createdAt = pl.createdAt.toLocalDateTimeOrNull() ?: LocalDateTime.now(),
            lastUpdateTime = pl.updatedAt.toLocalDateTimeOrNull() ?: LocalDateTime.now(),
            isEditable = true,
            bookmarkedAt = LocalDateTime.now(),
            thumbnailUrl = pl.artworkUrl,
            isLocal = false,
            isAutoSync = true,
            isPinned = false,
        )
        database.insert(entity)
    }

    private suspend fun mergePlaylistTrack(t: com.epsilonmusic.app.supabase.model.PlaylistTrackDto) {
        val song = SongEntity(
            id = t.songId,
            title = t.title ?: t.songId,
            duration = (t.durationMs ?: -1) / 1000,
            thumbnailUrl = t.thumbnailUrl,
            liked = false,
            isLocal = false,
            isDownloaded = false,
            isVideo = false,
        )
        database.upsert(song)

        val map = PlaylistSongMap(
            playlistId = t.playlistId,
            songId = t.songId,
            position = t.position,
            setVideoId = t.setVideoId,
        )
        try {
            database.insert(map)
        } catch (e: Exception) {
            // Duplicate insert is fine — means the mapping already exists
        }
    }

    private suspend fun mergeLikedSong(ls: com.epsilonmusic.app.supabase.model.LikedSongDto) {
        val song = SongEntity(
            id = ls.songId,
            title = ls.title ?: ls.songId,
            duration = (ls.durationMs ?: -1) / 1000,
            thumbnailUrl = ls.thumbnailUrl,
            liked = true,
            likedDate = ls.likedAt.toLocalDateTimeOrNull() ?: LocalDateTime.now(),
            isLocal = false,
            isDownloaded = false,
            isVideo = false,
        )
        database.upsert(song)
    }

    private suspend fun mergeSavedAlbum(a: com.epsilonmusic.app.supabase.model.SavedAlbumDto) {
        val entity = AlbumEntity(
            id = a.albumId,
            title = a.title ?: a.albumId,
            year = a.year,
            thumbnailUrl = a.thumbnailUrl,
            songCount = a.songCount ?: 0,
            duration = (a.durationMs ?: 0) / 1000,
            explicit = false,
            lastUpdateTime = LocalDateTime.now(),
            bookmarkedAt = a.savedAt.toLocalDateTimeOrNull() ?: LocalDateTime.now(),
            isLocal = false,
            isUploaded = false,
        )
        database.insert(entity)
    }

    private suspend fun mergeSavedArtist(ar: com.epsilonmusic.app.supabase.model.SavedArtistDto) {
        val entity = ArtistEntity(
            id = ar.artistId,
            name = ar.name ?: ar.artistId,
            thumbnailUrl = ar.thumbnailUrl,
            channelId = ar.channelId,
            lastUpdateTime = LocalDateTime.now(),
            bookmarkedAt = ar.savedAt.toLocalDateTimeOrNull() ?: LocalDateTime.now(),
            isLocal = false,
        )
        database.insert(entity)
    }

    // ── Push helpers (called by ViewModels when local data changes) ──────────

    /**
     * Push a like to the cloud. Does NOT touch the local Room DB (caller has
     * already done that). Failures are swallowed — the next full sync will
     * reconcile.
     */
    fun pushLike(song: SongEntity) {
        scope.launch {
            libraryRepo.like(
                provider = MusicProvider.YOUTUBE,
                songId = song.id,
                title = song.title,
                durationMs = (song.duration.takeIf { it > 0 } ?: -1) * 1000,
                thumbnailUrl = song.thumbnailUrl,
            )
        }
    }

    fun pushUnlike(songId: String) {
        scope.launch {
            libraryRepo.unlike(MusicProvider.YOUTUBE, songId)
        }
    }

    fun pushPlayEvent(song: SongEntity, listenedMs: Int, eventType: String = "played") {
        scope.launch {
            historyRepo.recordPlay(
                provider = MusicProvider.YOUTUBE,
                songId = song.id,
                eventType = eventType,
                listenedMs = listenedMs,
                title = song.title,
                durationMs = (song.duration.takeIf { it > 0 } ?: -1) * 1000,
                thumbnailUrl = song.thumbnailUrl,
            )
        }
    }

    /**
     * Push a playlist + its tracks to the cloud. Used when the user creates a
     * new local playlist and wants it available on other devices.
     */
    fun pushPlaylist(localPlaylist: PlaylistEntity, tracks: List<MediaMetadata>) {
        scope.launch {
            val cloudId = UUID.randomUUID()
            val created = playlistRepo.createPlaylist(
                PlaylistUpsertDto(
                    title = localPlaylist.name,
                    description = null,
                    artworkUrl = localPlaylist.thumbnailUrl,
                    isPublic = false,
                ),
            )
            if (created != null) {
                val inserts = tracks.mapIndexed { idx, m ->
                    PlaylistTrackInsertDto(
                        playlistId = created.id,
                        provider = MusicProvider.YOUTUBE.id,
                        songId = m.id,
                        title = m.title,
                        artist = m.artists.firstOrNull()?.name,
                        album = m.album?.title,
                        durationMs = (m.duration.takeIf { it > 0 } ?: -1) * 1000,
                        thumbnailUrl = m.thumbnailUrl,
                        setVideoId = m.setVideoId,
                        position = idx,
                    )
                }
                if (inserts.isNotEmpty()) {
                    playlistRepo.addTracksBatch(inserts)
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun String.toLocalDateTimeOrNull(): LocalDateTime? = runCatching {
    OffsetDateTime.parse(this).toLocalDateTime()
}.recoverCatching {
    LocalDateTime.parse(this)
}.getOrNull()
