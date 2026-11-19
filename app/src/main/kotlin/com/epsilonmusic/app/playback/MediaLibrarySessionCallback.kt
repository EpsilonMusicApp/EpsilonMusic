

package com.epsilonmusic.app.playback

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import java.security.MessageDigest
import java.text.Normalizer
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.pages.HomePage
import com.epsilonmusic.app.R
import com.epsilonmusic.app.constants.AutoplayOnAutomotiveConnectKey
import com.epsilonmusic.app.constants.DataSyncIdKey
import com.epsilonmusic.app.constants.HideExplicitKey
import com.epsilonmusic.app.constants.HideVideoSongsKey
import com.epsilonmusic.app.constants.MediaSessionConstants
import com.epsilonmusic.app.constants.SongSortType
import com.epsilonmusic.app.db.MusicDatabase
import com.epsilonmusic.app.db.entities.PlaylistEntity
import com.epsilonmusic.app.db.entities.Song
import com.epsilonmusic.app.extensions.toMediaItem
import com.epsilonmusic.app.extensions.toggleRepeatMode
import com.epsilonmusic.app.models.toMediaMetadata
import com.epsilonmusic.app.playback.queues.Queue
import com.epsilonmusic.app.playback.queues.YouTubeQueue
import com.epsilonmusic.app.utils.AutomotiveArtwork.withAutomotiveArtworkUri
import com.epsilonmusic.app.utils.dataStore
import com.epsilonmusic.app.utils.get
import com.epsilonmusic.app.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@androidx.annotation.OptIn(UnstableApi::class)
class MediaLibrarySessionCallback
@Inject
constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val downloadUtil: DownloadUtil,
) : MediaLibrarySession.Callback {
    private val scope = CoroutineScope(Dispatchers.Main) + Job()
    lateinit var service: MusicService
    var toggleLike: () -> Unit = {}
    var toggleStartRadio: () -> Unit = {}
    var toggleLibrary: () -> Unit = {}

    /**
     * Invoked when an automotive controller connects. Used to warm the cover of the
     * item already playing, which otherwise never goes through a media-item
     * transition while the car is connected.
     */
    var onAutomotiveConnected: () -> Unit = {}

    /** Cached YouTube Home snapshot (see [youtubeHome]) and when it was fetched. */
    private var cachedHome: HomePage? = null
    private var cachedHomeAtMs = 0L

    /** Disk copy of [cachedHome], so the shelves survive process restarts. */
    private val homeStore by lazy { AutoHomeSnapshotStore(context) }
    /** Account the cached snapshot belongs to (see [homeAccountKey]); null until first use. */
    private var homeAccount: String? = null
    /** True while [cachedHome] is the restored copy and still needs a fresh fetch. */
    private var cachedHomeFromDisk = false
    /** Wall-clock time each shelf was last returned by YouTube, by [homeSectionKey]. */
    private var homeLastSeenMs: Map<String, Long> = emptyMap()
    private val homeRefreshing = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Serializes the Home snapshot refresh so concurrent misses cannot race. */
    private val homeMutex = Mutex()

    /**
     * Package names [MediaSession.isAutomotiveController] and [MediaSession.isAutoCompanionController]
     * treat as automotive/companion surfaces. Media3 documents both methods as comparing
     * only [MediaSession.ControllerInfo.getPackageName] — a value that isn't necessarily backed
     * by the calling UID on every connection path — and explicitly says neither is a security
     * check. [isVerifiedAutomotiveController] re-derives the same intent but cross-checks the
     * claimed package name against [PackageManager]'s actual UID for it, which *is* tied to the
     * real calling process.
     */
    private val trustedAutomotivePackages = setOf(
        "com.google.android.projection.gearhead", // Android Auto
        "com.google.android.gms.car",              // Android Auto (legacy package)
        "com.android.systemui",                    // Android Automotive OS system UI
    )

    /**
     * True only if [controller] is both claiming a trusted automotive/companion package name
     * *and* is actually running as that package's UID — closing the gap left by
     * [MediaSession.isAutomotiveController]/[MediaSession.isAutoCompanionController], which
     * Media3 documents as non-authoritative string comparisons an unverified controller could
     * satisfy by merely reporting one of these package names.
     */
    private fun isVerifiedAutomotiveController(controller: MediaSession.ControllerInfo): Boolean {
        val claimedPackage = controller.packageName
        if (claimedPackage !in trustedAutomotivePackages) return false
        val actualUid = try {
            context.packageManager.getPackageUid(claimedPackage, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }
        return actualUid == controller.uid
    }

    /**
     * Whether [controller] is a surface allowed to mutate playback state via the toggle
     * custom commands (like/library/shuffle/repeat/radio): a trusted controller (the app
     * itself, systemui, or an app holding MEDIA_CONTENT_CONTROL / an enabled notification
     * listener), the system media notification, or a UID-verified Android Auto / Auto
     * companion controller. [MusicService] is an exported
     * [MediaLibraryService][androidx.media3.session.MediaLibraryService], so without this
     * check any app on the device could bind to the session and invoke these
     * state-changing commands.
     */
    private fun isAuthorizedController(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): Boolean =
        controller.isTrusted ||
            session.isMediaNotificationController(controller) ||
            isVerifiedAutomotiveController(controller)

    /**
     * Negotiates connection capabilities for an incoming controller.
     *
     * Exposes the toggle custom commands ([SessionCommand]s for like, start-radio, library,
     * shuffle and repeat) only to [isAuthorizedController] controllers — the system
     * notification / mini player and the lock screen drive playback exclusively through
     * these commands, so they still need to reach those surfaces, but an arbitrary
     * unauthorized app must not be able to invoke them.
     */
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val connectionResult = super.onConnect(session, controller)
        val availableSessionCommands = if (isAuthorizedController(session, controller)) {
            connectionResult.availableSessionCommands
                .buildUpon()
                .add(MediaSessionConstants.CommandToggleLike)
                .add(MediaSessionConstants.CommandToggleStartRadio)
                .add(MediaSessionConstants.CommandToggleLibrary)
                .add(MediaSessionConstants.CommandToggleShuffle)
                .add(MediaSessionConstants.CommandToggleRepeatMode)
                .build()
        } else {
            connectionResult.availableSessionCommands
        }

        // The reported package name alone is not verified; require the UID check
        // so an arbitrary controller cannot trigger the artwork warm-up.
        if (isVerifiedAutomotiveController(controller)) {
            onAutomotiveConnected()
        }
        if (isAutomotiveBrowser(controller)) {
            prefetchHome()
        }

        return MediaSession.ConnectionResult.accept(
            availableSessionCommands,
            connectionResult.availablePlayerCommands,
        )
    }

    /**
     * Handles custom session commands such as toggle-like, toggle-shuffle and toggle-repeat.
     *
     * Toggle commands are re-checked against [isAuthorizedController] as defense in depth —
     * [onConnect] should already keep them out of an unauthorized controller's
     * available-commands set, but a controller that somehow still sends one is rejected here.
     */
    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        val isToggleCommand = customCommand.customAction == MediaSessionConstants.ACTION_TOGGLE_LIKE ||
            customCommand.customAction == MediaSessionConstants.ACTION_TOGGLE_START_RADIO ||
            customCommand.customAction == MediaSessionConstants.ACTION_TOGGLE_LIBRARY ||
            customCommand.customAction == MediaSessionConstants.ACTION_TOGGLE_SHUFFLE ||
            customCommand.customAction == MediaSessionConstants.ACTION_TOGGLE_REPEAT_MODE
        if (isToggleCommand && !isAuthorizedController(session, controller)) {
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_PERMISSION_DENIED))
        }
        when (customCommand.customAction) {
            MediaSessionConstants.ACTION_TOGGLE_LIKE -> toggleLike()
            MediaSessionConstants.ACTION_TOGGLE_START_RADIO -> toggleStartRadio()
            MediaSessionConstants.ACTION_TOGGLE_LIBRARY -> toggleLibrary()
            MediaSessionConstants.ACTION_TOGGLE_SHUFFLE -> session.player.shuffleModeEnabled =
                !session.player.shuffleModeEnabled

            MediaSessionConstants.ACTION_TOGGLE_REPEAT_MODE -> session.player.toggleRepeatMode()
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    /**
     * Handles playback resumption requests (Android Auto / Automotive OS reconnect).
     *
     * When the [AutoplayOnAutomotiveConnectKey] setting is on and the caller is an
     * automotive controller, the current player queue (restored from disk with
     * playWhenReady=false) is offered back so Auto can resume it without manual taps.
     * Every other case keeps the old never-completing behavior so nothing can
     * unexpectedly start playback on controller reconnect.
     */
    @Deprecated("Deprecated in MediaLibrarySession.Callback")
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaItemsWithStartPosition> {
        if (!isAutomotiveBrowser(controller) && !isVerifiedAutomotiveController(controller)) {
            return SettableFuture.create<MediaItemsWithStartPosition>()
        }
        return scope.future(Dispatchers.Main) {
            val autoplay = context.dataStore.get(AutoplayOnAutomotiveConnectKey, false)
            if (!autoplay) {
                // Never complete, exactly like before: no implicit playback.
                awaitCancellation()
            }
            if (!::service.isInitialized) {
                return@future MediaItemsWithStartPosition(emptyList(), 0, C.TIME_UNSET)
            }
            val player = service.player
            if (player.mediaItemCount <= 0) {
                return@future MediaItemsWithStartPosition(emptyList(), 0, C.TIME_UNSET)
            }
            val index = player.currentMediaItemIndex
                .takeIf { it != C.INDEX_UNSET }
                ?.coerceIn(0, player.mediaItemCount - 1) ?: 0
            val items = List(player.mediaItemCount) { player.getMediaItemAt(it) }
            MediaItemsWithStartPosition(items, index, player.currentPosition)
        }
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(
            LibraryResult.ofItem(
                rootMediaItem(),
                params.withContentStyleHints(),
            ),
        )

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val isAutomotive = isAutomotiveBrowser(browser)
        return scope.future(Dispatchers.IO) {
            // Automotive "Suggested" / "Recent" tabs arrive as LibraryParams flags on the
            // root, not as distinct parentIds. Serve them from the Home snapshot / local
            // recents instead of an empty list so Auto shows recommended music.
            if (parentId == MusicService.ROOT && params?.isSuggested == true) {
                return@future LibraryResult.ofItemList(
                    // Suggested/Recent used to skip the artwork rewrite, so their covers
                    // were URI-only and unreliable on automotive surfaces.
                    withAutomotiveArtworkIfNeeded(isAutomotive, suggestedHomeSongs().paginate(page, pageSize)),
                    params.withContentStyleHints(),
                )
            }
            if (parentId == MusicService.ROOT && params?.isRecent == true) {
                val recent = database.songsByCreateDateAsc().first().take(50)
                    .map { it.toMediaItem(MusicService.SONG) }
                return@future LibraryResult.ofItemList(
                    withAutomotiveArtworkIfNeeded(isAutomotive, recent.paginate(page, pageSize)),
                    params.withContentStyleHints(),
                )
            }
            val children =
                when (parentId) {
                    MusicService.ROOT -> rootChildren()
                    MusicService.HOME -> homeSections()

                    MusicService.SONG -> database.songsByCreateDateAsc().first()
                        .map { it.toMediaItem(parentId) }

                    MusicService.ARTIST ->
                        database.artistsByCreateDateAsc().first().map { artist ->
                            browsableMediaItem(
                                "${MusicService.ARTIST}/${artist.id}",
                                artist.artist.name,
                                context.resources.getQuantityString(
                                    R.plurals.n_song,
                                    artist.songCount,
                                    artist.songCount
                                ),
                                artist.artist.thumbnailUrl?.toUri(),
                                MediaMetadata.MEDIA_TYPE_ARTIST,
                                singleItemStyle = artistItemStyle(isAutomotive),
                            )
                        }

                    MusicService.ALBUM ->
                        database.albumsByCreateDateAsc().first().map { album ->
                            browsableMediaItem(
                                "${MusicService.ALBUM}/${album.id}",
                                album.album.title,
                                album.artists.joinToString {
                                    it.name
                                },
                                album.album.thumbnailUrl?.toUri(),
                                MediaMetadata.MEDIA_TYPE_ALBUM,
                                singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
                            )
                        }

                    MusicService.PLAYLIST -> {
                        val likedSongCount = database.likedSongsCount().first()
                        val downloadedSongCount = downloadUtil.downloads.value.size
                        val youtubePlaylists = try {
                            YouTube.home().getOrNull()?.sections
                                ?.flatMap { it.items }
                                ?.filterIsInstance<PlaylistItem>()
                                ?.take(10)
                                ?: emptyList()
                        } catch (e: Exception) {
                            reportException(e)
                            emptyList()
                        }

                        listOf(
                            likedSongsMediaItem(likedSongCount),
                            downloadedSongsMediaItem(downloadedSongCount),
                        ) +
                            database.playlistsByCreateDateAsc().first().map { playlist ->
                                playlist.toBrowsableMediaItem()
                            } +
                            youtubePlaylists.map { ytPlaylist ->
                                ytPlaylist.toBrowsableMediaItem()
                            }
                    }

                    else ->
                        when {
                            parentId == MusicService.HOME_SECTION ||
                                parentId.startsWith("${MusicService.HOME_SECTION}/") -> {
                                val sectionId = parentId.removePrefix(MusicService.HOME_SECTION).removePrefix("/")
                                listOf(shuffleMediaItem(parentId)) + homeSectionSongs(sectionId, parentId)
                            }
                            parentId.startsWith("${MusicService.ARTIST}/") ->
                                database.artistSongsByCreateDateAsc(parentId.removePrefix("${MusicService.ARTIST}/"))
                                    .first().map {
                                        it.toMediaItem(parentId)
                                    }

                            parentId.startsWith("${MusicService.ALBUM}/") ->
                                database.albumSongs(parentId.removePrefix("${MusicService.ALBUM}/"))
                                    .first().map {
                                        it.toMediaItem(parentId)
                                    }

                            parentId.startsWith("${MusicService.PLAYLIST}/") -> {
                                val playlistId = parentId.removePrefix("${MusicService.PLAYLIST}/")
                                val songs = when (playlistId) {
                                    PlaylistEntity.LIKED_PLAYLIST_ID -> database.likedSongs(
                                        SongSortType.CREATE_DATE,
                                        true
                                    )

                                    PlaylistEntity.DOWNLOADED_PLAYLIST_ID -> {
                                        val downloads = downloadUtil.downloads.value
                                        database
                                            .allSongs()
                                            .flowOn(Dispatchers.IO)
                                            .map { songs ->
                                                songs.filter {
                                                    downloads[it.id]?.state == Download.STATE_COMPLETED
                                                }
                                            }.map { songs ->
                                                songs
                                                    .map { it to downloads[it.id] }
                                                    .sortedBy { it.second?.updateTimeMs ?: 0L }
                                                    .map { it.first }
                                            }
                                    }

                                    else ->
                                        database.playlistSongs(playlistId).map { list ->
                                            list.map { it.song }
                                        }
                                }.first()

                                listOf(shuffleMediaItem(parentId)) + songs.map { it.toMediaItem(parentId) }
                            }

                            parentId.startsWith("${MusicService.YOUTUBE_ARTIST}/") -> {
                                val artistId = parentId.removePrefix("${MusicService.YOUTUBE_ARTIST}/")
                                YouTube.artist(artistId).getOrNull()?.sections.orEmpty().flatMap { section ->
                                    section.items
                                        .withHideSettings()
                                        .filterNot { it is ArtistItem }
                                        .mapNotNull { it.toSearchMediaItem(parentId) }
                                        .take(HOME_SHELF_PREVIEW_SIZE)
                                        .map { it.withGroupTitle(section.title) }
                                }
                            }

                            parentId.startsWith("${MusicService.YOUTUBE_PLAYLIST}/") -> {
                                val playlistId = parentId.removePrefix("${MusicService.YOUTUBE_PLAYLIST}/")
                                try {
                                    val songs = YouTube.playlist(playlistId).getOrNull()?.songs
                                        ?.take(100)
                                        ?.filterExplicit(context.dataStore.get(HideExplicitKey, false))
                                        ?.filterVideoSongs(context.dataStore.get(HideVideoSongsKey, false) || context.dataStore.get(com.epsilonmusic.app.constants.DataSaverEnabledKey, false))
                                        ?: emptyList()

                                    listOf(shuffleMediaItem(parentId)) + songs.map { it.toMediaItem(parentId) }
                                } catch (e: Exception) {
                                    reportException(e)
                                    emptyList()
                                }
                            }

                            else -> emptyList()
                        }
                }

            LibraryResult.ofItemList(
                withAutomotiveArtworkIfNeeded(isAutomotive, children.paginate(page, pageSize)),
                params.withContentStyleHints(),
            )
        }
    }

    /**
     * Points each item's artwork at the local provider (see [AutomotiveArtworkProvider]).
     * Android Auto ignores `https://` icon URIs, and embedding the bytes only fit
     * a bounded part of each page, so covers were missing on later items and on
     * some surfaces. A `content://` URI lets every surface load every cover lazily.
     */
    private fun withAutomotiveArtworkIfNeeded(
        isAutomotive: Boolean,
        items: List<MediaItem>
    ): List<MediaItem> = if (isAutomotive) withAutomotiveArtwork(items) else items

    private fun withAutomotiveArtwork(items: List<MediaItem>): List<MediaItem> =
        items.map { it.withAutomotiveArtworkUri(context) }

    private fun isAutomotiveBrowser(browser: MediaSession.ControllerInfo): Boolean =
        browser.packageName in trustedAutomotivePackages

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val isAutomotive = isAutomotiveBrowser(browser)
        return scope.future(Dispatchers.IO) {
            getMediaItem(mediaId, isAutomotive)
                ?.let { item -> if (isAutomotive) withAutomotiveArtwork(listOf(item)).first() else item }
                ?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
        }
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        session.notifySearchResultChanged(browser, query, 1, params)
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val isAutomotive = isAutomotiveBrowser(browser)
        return scope.future(Dispatchers.IO) {
            if (query.isEmpty()) {
                return@future LibraryResult.ofItemList(emptyList(), params.withContentStyleHints())
            }

            try {
                // YouTube's own ranking, as in the app's top result: grouping by kind and searching each
                // kind apart buried what was asked for, and a long list is cut short while driving.
                // The query is encoded so that a "/" in it cannot split the ids of its results.
                val parentId = "${MusicService.SEARCH}/${Uri.encode(query)}"
                val ranked = rankedOnlineResults(query)
                rememberSearchSongs(ranked)
                val library = rankedLibrarySongs(query)
                // An exact library match leads, and its copy among the online results goes.
                val exactLibrary = library.takeWhile { isExactMatch(it, query) }
                val exactIds = exactLibrary.map { it.id }.toSet()
                val online = ranked.filterNot { it.id in exactIds }.mapNotNull { it.toSearchMediaItem(parentId) }
                val otherLibrary =
                    library.drop(exactLibrary.size)
                        .filterNot { song -> ranked.any { it.id == song.id } }
                        .take(SEARCH_LOCAL_LIMIT)
                fun List<Song>.rows() =
                    map { it.toMediaItem(path = parentId, isPlayable = true, isBrowsable = false) }
                val searchResults =
                    (exactLibrary.rows() +
                        online.take(SEARCH_TOP_RESULTS) +
                        otherLibrary.rows() +
                        online.drop(SEARCH_TOP_RESULTS))
                        .take(SEARCH_RESULT_LIMIT)

                LibraryResult.ofItemList(withAutomotiveArtworkIfNeeded(isAutomotive, searchResults.paginate(page, pageSize)), params.withContentStyleHints())
            } catch (e: Exception) {
                reportException(e)
                LibraryResult.ofItemList(emptyList(), params.withContentStyleHints())
            }
        }
    }

    /** Results by relevance, without what the user hides. */
    private suspend fun rankedOnlineResults(query: String): List<YTItem> =
        YouTube.searchSummary(query)
            .onFailure(::reportException)
            .getOrNull()
            ?.ranked
            .orEmpty()
            .withHideSettings()

    /** Without explicit songs and videos when the user hides them. */
    private fun <T : YTItem> List<T>.withHideSettings(): List<T> =
        filterExplicit(context.dataStore.get(HideExplicitKey, false))
            .filterVideoSongs(
                context.dataStore.get(HideVideoSongsKey, false) ||
                    context.dataStore.get(com.epsilonmusic.app.constants.DataSaverEnabledKey, false)
            )

    /** [search] again when its first answer is empty: the first request after a start can be. */
    private suspend fun <T> retryWhenEmpty(search: suspend () -> List<T>): List<T> {
        var results = search()
        repeat(VOICE_SEARCH_RETRIES) {
            if (results.isEmpty()) {
                delay(VOICE_SEARCH_RETRY_MS)
                results = search()
            }
        }
        return results
    }

    /**
     * Songs of recent searches, to play the one chosen without searching again. Kept across
     * searches (bounded), as a list on screen can be older than the latest search.
     */
    private val searchSongs =
        java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, SongItem>(SEARCH_SONG_CACHE, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SongItem>?) =
                    size > SEARCH_SONG_CACHE
            }
        )

    private suspend fun rememberSearchSongs(results: List<YTItem>) {
        val songs = results.filterIsInstance<SongItem>()
        songs.forEach { searchSongs[it.id] = it }
        // Like and Play next act on database rows.
        runCatching { database.query { songs.forEach { insert(it.toMediaMetadata()) } } }
    }

    /** The song id inside a search result's media id: search/<encoded query>/<song id>. */
    private fun searchSongId(mediaId: String): String? =
        mediaId.split("/", limit = 3).getOrNull(2)?.takeIf { it.isNotEmpty() }

    private fun songIdOf(mediaId: String): String =
        if (mediaId.startsWith("${MusicService.SEARCH}/")) {
            searchSongId(mediaId) ?: mediaId.substringAfterLast('/')
        } else {
            mediaId.substringAfterLast('/')
        }

    private fun YTItem.toSearchMediaItem(parentId: String): MediaItem? =
        when (this) {
            is SongItem -> toMediaItem(parentId)
            is AlbumItem -> toBrowsableMediaItem()
            is PlaylistItem -> toBrowsableMediaItem()
            is ArtistItem ->
                browsableMediaItem(
                    "${MusicService.YOUTUBE_ARTIST}/$id",
                    title,
                    context.getString(R.string.artists),
                    thumbnail?.toUri(),
                    MediaMetadata.MEDIA_TYPE_ARTIST,
                    singleItemStyle = artistItemStyle(true),
                )
        }

    private fun AlbumItem.toBrowsableMediaItem() =
        browsableMediaItem(
            "${MusicService.YOUTUBE_PLAYLIST}/$playlistId",
            title,
            listOfNotNull(
                context.getString(R.string.albums),
                artists?.joinToString { it.name }?.takeIf { it.isNotEmpty() },
            ).joinToString(" \u2022 "),
            thumbnail.toUri(),
            MediaMetadata.MEDIA_TYPE_ALBUM,
            singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
        )

    /** Lowercase words without accents or punctuation, so "Più" matches "piu". */
    private fun searchWords(text: String): List<String> =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }

    private fun isExactMatch(song: Song, query: String): Boolean =
        searchWords(song.song.title) == searchWords(query)

    /**
     * Library songs holding every word of [query] in their title and artists, best first. Songs only
     * found through a matching album or playlist name had nothing to do with what was asked.
     */
    private suspend fun rankedLibrarySongs(query: String): List<Song> {
        val words = searchWords(query)
        if (words.isEmpty()) return emptyList()
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideos =
            context.dataStore.get(HideVideoSongsKey, false) ||
                context.dataStore.get(com.epsilonmusic.app.constants.DataSaverEnabledKey, false)
        return database
            .allSongs()
            .first()
            .filter { song ->
                if (hideExplicit && song.song.explicit) return@filter false
                if (hideVideos && song.song.isVideo) return@filter false
                val text = searchWords(song.song.title + " " + song.artists.joinToString(" ") { it.name })
                words.all { it in text }
            }
            .sortedBy { song ->
                val title = searchWords(song.song.title)
                when {
                    title == words -> 0
                    title.take(words.size) == words -> 1
                    words.all { it in title } -> 2
                    else -> 3
                }
            }
    }

    /** A playlist, album or show as a queue: mixes as YouTube radios, shows forward in time. */
    private suspend fun youtubeCollectionQueue(playlistId: String): MediaItemsWithStartPosition? {
        if (playlistId.startsWith("RD")) {
            val radio =
                YouTube.next(WatchEndpoint(playlistId = playlistId)).getOrNull()?.items.orEmpty()
                    .withHideSettings()
            if (radio.isNotEmpty()) {
                return MediaItemsWithStartPosition(radio.map { it.toMediaItem() }, 0, C.TIME_UNSET)
            }
        }
        val page = YouTube.playlist(playlistId).getOrNull() ?: return null
        // Later pages too: related songs would otherwise follow the first one.
        var songs = page.songs
        var continuation = page.songsContinuation
        while (continuation != null && songs.size < VOICE_COLLECTION_LIMIT) {
            val more = YouTube.playlistContinuation(continuation).getOrNull() ?: break
            songs = songs + more.songs
            continuation = more.continuation
        }
        songs = songs.withHideSettings()
        if (songs.isEmpty()) return null
        return MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, 0, C.TIME_UNSET)
    }

    /**
     * "Play X" by voice: Gemini and the Assistant send a search with no media id. With a media focus
     * in the request the first of that kind plays, otherwise the best match, with the user's own
     * playlists first when one has that exact name. The spoken text is not parsed, so this works the
     * same in every language.
     */
    suspend fun voiceSearchQueue(
        rawQuery: String?,
        extras: Bundle?,
    ): MediaItemsWithStartPosition? {
        // A request can start the service: the queue being restored would replace what it asks for.
        if (::service.isInitialized) service.awaitQueueRestored()
        val focus = extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS)
        fun extra(key: String) = extras?.getString(key)?.trim()?.takeIf { it.isNotEmpty() }
        val title = extra(MediaStore.EXTRA_MEDIA_TITLE)
        val artist = extra(MediaStore.EXTRA_MEDIA_ARTIST)
        val album = extra(MediaStore.EXTRA_MEDIA_ALBUM)
        val playlist = extra(MEDIA_EXTRA_PLAYLIST)
        val kind =
            when (focus) {
                MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE -> "playlist"
                MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> "album"
                MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> "artist"
                MediaStore.Audio.Media.ENTRY_CONTENT_TYPE -> "song"
                else -> null
            }
        val query =
            when (kind) {
                "playlist" -> playlist
                "album" -> album
                "artist" -> artist
                else -> null
            }
                ?: rawQuery?.let(::withoutAppName)?.takeIf { it.isNotEmpty() }
                ?: listOfNotNull(title, artist).joinToString(" ")
        if (query.isBlank()) return currentOrSuggestedQueue()
        return when (kind) {
            "playlist" -> libraryPlaylistQueue(query, exact = false) ?: firstOfKind(query, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST)
            "album" -> firstOfKind(query, YouTube.SearchFilter.FILTER_ALBUM, artist)
            "artist" -> firstOfKind(query, YouTube.SearchFilter.FILTER_ARTIST)
            // A song was asked for: never a playlist that happens to share its name.
            "song" -> bestVoiceMatches(query, artist).filterIsInstance<SongItem>().firstNotNullOfOrNull { queueOf(it) }
            else ->
                libraryPlaylistQueue(query, exact = true)
                    ?: bestVoiceMatches(query, artist).firstNotNullOfOrNull { queueOf(it) }
                    ?: rankedLibrarySongs(query).firstOrNull()?.let {
                        MediaItemsWithStartPosition(listOf(it.toMediaItem()), 0, C.TIME_UNSET)
                    }
        }
    }

    /**
     * Results for a spoken request, the most literal first: a song titled exactly as asked beats
     * YouTube's top result, which can be the album or artist and would start from something else.
     */
    private suspend fun bestVoiceMatches(query: String, artist: String? = null): List<YTItem> {
        val words = searchWords(query)
        val ranked = retryWhenEmpty { rankedOnlineResults(query) }
        val exact = ranked.filter { searchWords(it.title) == words }
        val ordered =
            exact.filterIsInstance<SongItem>().filterNot { it.isVideoSong } +
                exact.filterIsInstance<SongItem>() +
                exact +
                ranked.filterIsInstance<SongItem>().filter { song ->
                    val title = searchWords(song.title)
                    words.all { it in title }
                } +
                ranked
        return byArtistFirst(ordered.distinctBy { it.id }, artist)
    }

    /** With an artist named in the request, that artist's results come first. */
    private fun byArtistFirst(items: List<YTItem>, artist: String?): List<YTItem> {
        val wanted = artist?.let(::searchWords)?.takeIf { it.isNotEmpty() } ?: return items
        fun matches(item: YTItem): Boolean {
            val names =
                when (item) {
                    is SongItem -> item.artists.map { it.name }
                    is AlbumItem -> item.artists.orEmpty().map { it.name }
                    is ArtistItem -> listOf(item.title)
                    is PlaylistItem -> listOfNotNull(item.author?.name)
                }
            return names.any { searchWords(it) == wanted }
        }
        return items.filter(::matches) + items.filterNot(::matches)
    }

    /**
     * The request without "… on Epsilon Music": the assistant can leave the app's name, and the short
     * word before it, in the text it passes. The name is the app's own label, so this needs no word
     * list; the word before it is dropped only when it is short, as prepositions are.
     */
    private fun withoutAppName(text: String): String {
        val label = context.applicationInfo.loadLabel(context.packageManager).toString()
        val name = searchWords(label).joinToString("\\s*") { Regex.escape(it) }
        if (name.isEmpty()) return text.trim()
        return text.replace(Regex("""(?i)(\s+\S{1,4})?\s+$name[\s.!?]*$"""), "").trim()
    }

    private suspend fun firstOfKind(
        query: String,
        filter: YouTube.SearchFilter,
        artist: String? = null,
    ): MediaItemsWithStartPosition? {
        val results =
            retryWhenEmpty {
                YouTube.search(query, filter).getOrNull()?.items.orEmpty().withHideSettings()
            }
        return byArtistFirst(results, artist).firstNotNullOfOrNull { queueOf(it) }
    }

    private suspend fun queueOf(item: YTItem): MediaItemsWithStartPosition? =
        when (item) {
            // One song: the service continues it with related songs.
            is SongItem -> MediaItemsWithStartPosition(listOf(item.toMediaItem()), 0, C.TIME_UNSET)
            is AlbumItem -> youtubeCollectionQueue(item.playlistId)
            is PlaylistItem -> youtubeCollectionQueue(item.id)
            is ArtistItem ->
                item.radioEndpoint?.let { YouTube.next(it).getOrNull()?.items?.withHideSettings() }
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { songs ->
                        MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, 0, C.TIME_UNSET)
                    }
        }

    private suspend fun libraryPlaylistQueue(query: String, exact: Boolean): MediaItemsWithStartPosition? {
        val words = searchWords(query)
        // Names are compared without accents or punctuation, which the database search would miss.
        val match =
            database.playlistsByCreateDateAsc().first().firstOrNull { searchWords(it.playlist.name) == words }
                ?: database.searchPlaylists(query).first().firstOrNull().takeUnless { exact }
                ?: return null
        val songs = database.playlistSongs(match.id).first().map { it.song }
        if (songs.isEmpty()) return null
        return MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, 0, C.TIME_UNSET)
    }

    /** "Play music" with nothing named: what is queued, or else the suggestions, shuffled. */
    private suspend fun currentOrSuggestedQueue(): MediaItemsWithStartPosition? {
        val current =
            withContext(Dispatchers.Main) {
                val player = service.player
                if (player.mediaItemCount <= 0) {
                    null
                } else {
                    MediaItemsWithStartPosition(
                        List(player.mediaItemCount) { player.getMediaItemAt(it) },
                        player.currentMediaItemIndex.coerceAtLeast(0),
                        player.currentPosition,
                    )
                }
            }
        if (current != null) return current
        val songs =
            suggestedHomeSongs()
                .filter { it.mediaMetadata.isPlayable == true && !it.mediaId.endsWith(MusicService.SHUFFLE_ACTION) }
                .map { song ->
                    val id = song.mediaId.substringAfterLast("/")
                    database.song(id).first()?.toMediaItem() ?: song
                }
        if (songs.isEmpty()) return null
        return MediaItemsWithStartPosition(songs.shuffled(), 0, C.TIME_UNSET)
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaItemsWithStartPosition> =
        scope.future(Dispatchers.IO) {
            val source = SessionQueueSource()
            resolveMediaItems(mediaItems, startIndex, startPositionMs, source).also { result ->
                if (result.mediaItems.isNotEmpty() && ::service.isInitialized) {
                    withContext(Dispatchers.Main) {
                        service.releaseQueueToSession()
                        source.queue?.let(service::adoptSessionQueue)
                    }
                }
                // The cover of what starts playing is ready for the first metadata update.
                result.mediaItems.getOrNull(result.startIndex)?.let { item ->
                    if (::service.isInitialized) service.warmArtwork(item.mediaMetadata.artworkUri)
                }
            }
        }

    /** Where the resolved items came from, when more of it can follow them (a mix). */
    private class SessionQueueSource(var queue: Queue? = null)

    private suspend fun resolveMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
        source: SessionQueueSource = SessionQueueSource(),
    ): MediaItemsWithStartPosition = coroutineScope {
            val defaultResult = MediaItemsWithStartPosition(emptyList(), startIndex, startPositionMs)
            val requested = mediaItems.firstOrNull() ?: return@coroutineScope defaultResult
            if (requested.mediaId.isEmpty()) {
                return@coroutineScope try {
                    voiceSearchQueue(requested.requestMetadata.searchQuery, requested.requestMetadata.extras)
                        ?: defaultResult
                } catch (e: Exception) {
                    reportException(e)
                    defaultResult
                }
            }
            val path = requested.mediaId.split("/")

            when (path.firstOrNull()) {
                MusicService.SONG -> {
                    val songId = path.getOrNull(1) ?: return@coroutineScope defaultResult
                    val allSongs = database.songsByCreateDateAsc().first()
                    val index = allSongs.indexOfFirst { it.id == songId }.takeIf { it != -1 } ?: 0
                    val start = maxOf(0, index - 100)
                    val end = minOf(allSongs.size, index + 100)
                    MediaItemsWithStartPosition(
                        allSongs.subList(start, end).map { it.toMediaItem() },
                        index - start,
                        startPositionMs
                    )
                }

                MusicService.ARTIST -> {
                    val songId = path.getOrNull(2) ?: return@coroutineScope defaultResult
                    val artistId = path.getOrNull(1) ?: return@coroutineScope defaultResult
                    val songs = database.artistSongsByCreateDateAsc(artistId).first()
                    MediaItemsWithStartPosition(
                        songs.map { it.toMediaItem() },
                        songs.indexOfFirst { it.id == songId }.takeIf { it != -1 } ?: 0,
                        startPositionMs
                    )
                }

                MusicService.ALBUM -> {
                    val songId = path.getOrNull(2) ?: return@coroutineScope defaultResult
                    val albumId = path.getOrNull(1) ?: return@coroutineScope defaultResult
                    val albumWithSongs = database.albumWithSongs(albumId).first() ?: return@coroutineScope defaultResult
                    MediaItemsWithStartPosition(
                        albumWithSongs.songs.map { it.toMediaItem() },
                        albumWithSongs.songs.indexOfFirst { it.id == songId }.takeIf { it != -1 } ?: 0,
                        startPositionMs
                    )
                }

                MusicService.PLAYLIST -> {
                    val songId = path.getOrNull(2) ?: return@coroutineScope defaultResult
                    val playlistId = path.getOrNull(1) ?: return@coroutineScope defaultResult
                    val songs = when (playlistId) {
                        PlaylistEntity.LIKED_PLAYLIST_ID -> database.likedSongs(SongSortType.CREATE_DATE, descending = true)
                        PlaylistEntity.DOWNLOADED_PLAYLIST_ID -> {
                            val downloads = downloadUtil.downloads.value
                            database
                                .allSongs()
                                .flowOn(Dispatchers.IO)
                                .map { songs ->
                                    songs.filter {
                                        downloads[it.id]?.state == Download.STATE_COMPLETED
                                    }
                                }.map { songs ->
                                    songs
                                        .map { it to downloads[it.id] }
                                        .sortedBy { it.second?.updateTimeMs ?: 0L }
                                        .map { it.first }
                                }
                        }
                        else -> database.playlistSongs(playlistId).map { list ->
                            list.map { it.song }
                        }
                    }.first()

                    
                    if (songId == MusicService.SHUFFLE_ACTION) {
                        MediaItemsWithStartPosition(
                            songs.shuffled().take(200).map { it.toMediaItem() },
                            0,
                            C.TIME_UNSET
                        )
                    } else {
                        val index = songs.indexOfFirst { it.id == songId }.takeIf { it != -1 } ?: 0
                        val start = maxOf(0, index - 100)
                        val end = minOf(songs.size, index + 100)
                        MediaItemsWithStartPosition(
                            songs.subList(start, end).map { it.toMediaItem() },
                            index - start,
                            startPositionMs
                        )
                    }
                }

                MusicService.YOUTUBE_PLAYLIST -> {
                    val songId = path.getOrNull(2) ?: return@coroutineScope defaultResult
                    val playlistId = path.getOrNull(1) ?: return@coroutineScope defaultResult

                    // Mixes ("RD…", e.g. Mixtapes for you) are radios: started like YouTube Music does,
                    // through the watch endpoint, they come in a fresh personalized order instead of
                    // the same fixed list from its first song every time. The mix's queue goes on to the
                    // service, which loads its next pages as they are needed, as in the app.
                    if (playlistId.startsWith("RD")) {
                        val startId = songId.takeUnless { it == MusicService.SHUFFLE_ACTION }
                        val mix = YouTubeQueue(WatchEndpoint(videoId = startId, playlistId = playlistId))
                        val first =
                            try {
                                mix.getInitialStatus()
                            } catch (e: Exception) {
                                reportException(e)
                                null
                            }
                        if (first != null && first.items.isNotEmpty()) {
                            source.queue = mix
                            if (startId != null) {
                                return@coroutineScope MediaItemsWithStartPosition(
                                    first.items,
                                    first.mediaItemIndex,
                                    C.TIME_UNSET,
                                )
                            }
                            // The first page holds mostly the same songs every time: a few more pages,
                            // shuffled together, make each start different.
                            val items = first.items.toMutableList()
                            withTimeoutOrNull(MIX_EXTRA_PAGES_TIMEOUT_MS) {
                                repeat(MIX_EXTRA_PAGES) {
                                    if (!mix.hasNextPage()) return@withTimeoutOrNull
                                    // A page that fails leaves the mix to start with what it has.
                                    items += runCatching { mix.nextPage() }.getOrElse { return@withTimeoutOrNull }
                                }
                            }
                            return@coroutineScope MediaItemsWithStartPosition(
                                items.distinctBy { it.mediaId }.shuffled(),
                                0,
                                C.TIME_UNSET,
                            )
                        }
                    }

                    val songs = try {
                        YouTube.playlist(playlistId).getOrNull()?.songs?.map {
                            it.toMediaItem()
                        } ?: emptyList()
                    } catch (e: Exception) {
                        reportException(e)
                        return@coroutineScope defaultResult
                    }

                    
                    if (songId == MusicService.SHUFFLE_ACTION) {
                        MediaItemsWithStartPosition(
                            songs.shuffled(),
                            0,
                            C.TIME_UNSET
                        )
                    } else {
                        MediaItemsWithStartPosition(
                            songs,
                            songs.indexOfFirst { it.mediaId.endsWith(songId) }.takeIf { it != -1 } ?: 0,
                            C.TIME_UNSET
                        )
                    }
                }

                MusicService.YOUTUBE_ARTIST -> {
                    val artistId = path.getOrNull(1) ?: return@coroutineScope defaultResult
                    val songId = path.getOrNull(2) ?: return@coroutineScope defaultResult
                    val songs =
                        YouTube.artist(artistId).getOrNull()?.sections.orEmpty()
                            .flatMap { it.items.withHideSettings() }
                            .filterIsInstance<SongItem>()
                            .distinctBy { it.id }
                    val index = songs.indexOfFirst { it.id == songId }
                    if (index == -1) return@coroutineScope defaultResult
                    MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, index, C.TIME_UNSET)
                }

                MusicService.HOME_SECTION -> {
                    val sectionId = path.getOrNull(1) ?: return@coroutineScope defaultResult
                    val songId = path.lastOrNull() ?: return@coroutineScope defaultResult
                    val parentId = mediaItems.firstOrNull()?.mediaId?.removeSuffix("/$songId")
                        ?: "${MusicService.HOME_SECTION}/$sectionId"
                    // Drop the shuffle marker itself: it is a command, not a playable queue item.
                    val sectionSongs =
                        if (sectionId == SUGGESTED_SECTION_ID) suggestedHomeSongs() else homeSectionSongs(sectionId, parentId)
                    val songs = sectionSongs.filter { it.mediaMetadata.isPlayable == true && !it.mediaId.endsWith(MusicService.SHUFFLE_ACTION) }
                    if (songs.isEmpty()) return@coroutineScope defaultResult
                    val playable = songs.map { song ->
                        val id = song.mediaId.substringAfterLast("/")
                        database.song(id).first()?.toMediaItem() ?: song
                    }
                    if (songId == MusicService.SHUFFLE_ACTION) {
                        MediaItemsWithStartPosition(playable.shuffled(), 0, C.TIME_UNSET)
                    } else {
                        MediaItemsWithStartPosition(
                            playable,
                            playable.indexOfFirst { it.mediaId.endsWith(songId) }.takeIf { it != -1 } ?: 0,
                            C.TIME_UNSET
                        )
                    }
                }
                MusicService.HOME -> {
                    // Reached via the shuffle marker under suggested songs: play shuffled.
                    val songs = suggestedHomeSongs()
                        .filter { it.mediaMetadata.isPlayable == true && !it.mediaId.endsWith(MusicService.SHUFFLE_ACTION) }
                        .map { song ->
                            val id = song.mediaId.substringAfterLast("/")
                            database.song(id).first()?.toMediaItem() ?: song
                        }
                    if (songs.isEmpty()) return@coroutineScope defaultResult
                    MediaItemsWithStartPosition(songs.shuffled(), 0, C.TIME_UNSET)
                }
                MusicService.SEARCH -> {
                    // Exactly the result chosen; the service continues it with related songs. Library ids
                    // can hold slashes (content URIs), so the id is everything after the query.
                    val songId = searchSongId(requested.mediaId) ?: return@coroutineScope defaultResult
                    val item =
                        searchSongs[songId]?.toMediaItem() ?: database.song(songId).first()?.toMediaItem()
                            ?: return@coroutineScope defaultResult
                    MediaItemsWithStartPosition(listOf(item), 0, C.TIME_UNSET)
                }

                else -> defaultResult
            }
        }

    private fun drawableUri(
        @DrawableRes id: Int,
    ) = Uri
        .Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.resources.getResourcePackageName(id))
        .appendPath(context.resources.getResourceTypeName(id))
        .appendPath(context.resources.getResourceEntryName(id))
        .build()

    private fun rootMediaItem() = MediaItem
        .Builder()
        .setMediaId(MusicService.ROOT)
        .setMediaMetadata(
            MediaMetadata
                .Builder()
                .setTitle(context.getString(R.string.app_name))
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build(),
        ).build()

    private fun rootChildren() = listOf(
        browsableMediaItem(
            MusicService.HOME,
            context.getString(R.string.home),
            null,
            drawableUri(R.drawable.home_filled),
            MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
            singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_GRID_ITEM,
        ),
        browsableMediaItem(
            MusicService.SONG,
            context.getString(R.string.songs),
            null,
            drawableUri(R.drawable.music_note),
            MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
            singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_GRID_ITEM,
        ),
        browsableMediaItem(
            MusicService.ARTIST,
            context.getString(R.string.artists),
            null,
            drawableUri(R.drawable.artist),
            MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
            singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_GRID_ITEM,
        ),
        browsableMediaItem(
            MusicService.ALBUM,
            context.getString(R.string.albums),
            null,
            drawableUri(R.drawable.album),
            MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
            singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_GRID_ITEM,
        ),
        browsableMediaItem(
            MusicService.PLAYLIST,
            context.getString(R.string.playlists),
            null,
            drawableUri(R.drawable.queue_music),
            MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
            singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_GRID_ITEM,
        ),
    )

    /** Puts [this] under a titled group header in Android Auto lists. */
    private fun MediaItem.withGroupTitle(title: String): MediaItem {
        val extras = Bundle(mediaMetadata.extras ?: Bundle()).apply {
            putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, title)
        }
        return buildUpon().setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build()).build()
    }

    private fun artistItemStyle(isAutomotive: Boolean) =
        if (isAutomotive) {
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM
        } else {
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
        }

    /**
     * One Home snapshot per [HOME_CACHE_TTL_MS], shared by every automotive browse
     * call.
     *
     * YouTube's Home feed is personalized per request: two consecutive fetches can
     * return different shelves, or the same shelves in a different order, which
     * made shelves such as "Quick picks" appear and disappear between visits.
     * Every fetch is therefore merged into one snapshot ([mergeHomeSnapshots])
     * that is also persisted ([AutoHomeSnapshotStore]), so the set of shelves and
     * their order survive both refreshes and process restarts.
     *
     * The refresh is serialized by [homeMutex] because `onGetChildren` and
     * `onSetMediaItems` run on independent IO coroutines: without it, two
     * concurrent misses could install two different snapshots. The TTL uses
     * [SystemClock.elapsedRealtime] so a wall-clock adjustment cannot expire it.
     */
    private suspend fun youtubeHome(): HomePage? {
        val account = homeAccountKey()
        val (home, isFresh) =
            homeMutex.withLock {
                useHomeAccount(account)
                cachedHome to isCachedHomeFresh()
            }
        if (isFresh) return home
        // A stale snapshot (restored from disk, or past its TTL) is served right
        // away so Auto never waits on several Home pages; the refresh only adds
        // shelves or updates their content, positions stay put.
        if (home != null) {
            refreshHomeInBackground()
            return home
        }
        return refreshHome()
    }

    /**
     * Identifies the signed-in account, so one account's personalized shelves are
     * never shown to another. Read from DataStore rather than YouTube's in-memory
     * data sync id, which is still unset for a moment after a cold start.
     */
    private fun homeAccountKey(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(context.dataStore.get(DataSyncIdKey, "").toByteArray())
            .joinToString("") { "%02x".format(it) }

    /**
     * Makes [cachedHome] belong to [account]: on first use, or after a sign-out or
     * account switch inside this process, the snapshot is replaced by the one
     * stored for [account] (if any). Callers hold [homeMutex].
     */
    private fun useHomeAccount(account: String) {
        if (homeAccount == account) return
        homeAccount = account
        cachedHome = null
        cachedHomeAtMs = 0L
        cachedHomeFromDisk = false
        homeLastSeenMs = emptyMap()
        val snapshot = homeStore.load(account) ?: return
        // The age limit applies to the restored copy too, or a shelf could outlive
        // it whenever the refresh keeps failing.
        val wallNow = System.currentTimeMillis()
        val sections =
            snapshot.sections.filter {
                wallNow - (snapshot.lastSeenMs[homeSectionKey(it)] ?: 0L) < HOME_SECTION_MAX_AGE_MS
            }
        if (sections.isEmpty()) return
        val keptKeys = sections.map(::homeSectionKey).toSet()
        cachedHome = HomePage(chips = null, sections = sections)
        homeLastSeenMs = snapshot.lastSeenMs.filterKeys { it in keptKeys }
        cachedHomeFromDisk = true
    }

    private fun isCachedHomeFresh(): Boolean =
        cachedHome != null &&
            !cachedHomeFromDisk &&
            SystemClock.elapsedRealtime() - cachedHomeAtMs < HOME_CACHE_TTL_MS

    private fun refreshHomeInBackground() {
        if (!homeRefreshing.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            try {
                refreshHome()
            } finally {
                homeRefreshing.set(false)
            }
        }
    }

    /**
     * Fetches Home, merges it into the snapshot and persists the result.
     *
     * [homeMutex] is held only to check freshness and to install the result, not
     * during the network fetch, so browse calls keep getting the stale snapshot
     * immediately while a refresh is running.
     */
    private suspend fun refreshHome(): HomePage? {
        val account = homeAccountKey()
        homeMutex.withLock {
            useHomeAccount(account)
            if (isCachedHomeFresh()) return cachedHome
        }
        val fetched = fetchFullHome()
        return homeMutex.withLock {
            useHomeAccount(account)
            if (fetched == null || fetched.sections.isEmpty()) {
                // Offline or YouTube failing: the restored snapshot still gives Auto its shelves.
                return@withLock cachedHome
            }
            // Another caller may have installed a fresh snapshot during the fetch.
            if (isCachedHomeFresh()) return@withLock cachedHome
            val wallNow = System.currentTimeMillis()
            val lastSeen = homeLastSeenMs.toMutableMap()
            fetched.sections.forEach { lastSeen[homeSectionKey(it)] = wallNow }
            // Keep shelves YouTube has not returned for a while from piling up forever.
            val merged =
                mergeHomeSnapshots(cachedHome, fetched).let { page ->
                    page.copy(
                        sections =
                            page.sections.filter {
                                wallNow - (lastSeen[homeSectionKey(it)] ?: wallNow) < HOME_SECTION_MAX_AGE_MS
                            }
                    )
                }
            val keptKeys = merged.sections.map(::homeSectionKey).toSet()
            homeLastSeenMs = lastSeen.filterKeys { it in keptKeys }
            cachedHome = merged
            cachedHomeAtMs = SystemClock.elapsedRealtime()
            cachedHomeFromDisk = false
            homeStore.save(account, merged.sections, ::homeSectionKey, homeLastSeenMs)
            merged
        }
    }

    /**
     * The first Home page plus its continuations.
     *
     * `YouTube.home()` returns only the first batch of shelves; the rest (often
     * including the "Mixed for you" mixes) arrive through continuations, and
     * which shelves land in the first batch varies per request. Serving only
     * the first page made shelves swap in and out between visits.
     */
    private suspend fun fetchFullHome(): HomePage? {
        // Bounded, or a stalled connection would keep the refresh (and the
        // homeRefreshing flag) active forever.
        val first =
            try {
                withTimeoutOrNull(HOME_FIRST_PAGE_TIMEOUT_MS) { YouTube.home().getOrNull() }
            } catch (e: Exception) {
                reportException(e)
                null
            } ?: return null
        val sections = first.sections.toMutableList()
        var continuation = first.continuation
        withTimeoutOrNull(HOME_CONTINUATION_TIMEOUT_MS) {
            var pages = 1
            while (continuation != null && pages < HOME_MAX_PAGES) {
                val next = YouTube.home(continuation).getOrNull() ?: break
                sections += next.sections
                continuation = next.continuation
                pages++
            }
        }
        return first.copy(sections = sections.distinctBy(::homeSectionKey), continuation = null)
    }

    /**
     * Keeps every shelf seen during this process alive and in a stable position:
     * shelves already shown keep their slot (with fresh content when the new fetch
     * has them), new shelves are appended, and a shelf missing from one fetch is
     * not dropped. Auto then always shows the same full set of categories.
     */
    private fun mergeHomeSnapshots(previous: HomePage?, fresh: HomePage): HomePage {
        if (previous == null) return fresh
        val freshByKey = fresh.sections.associateBy(::homeSectionKey)
        val previousKeys = previous.sections.map(::homeSectionKey).toSet()
        val sections =
            previous.sections.map { freshByKey[homeSectionKey(it)] ?: it } +
                fresh.sections.filter { homeSectionKey(it) !in previousKeys }
        return fresh.copy(sections = sections)
    }

    /**
     * Stable id of a shelf inside the Auto tree. It used to be the shelf's index in
     * the feed, which pointed at a different shelf as soon as the feed changed.
     * Encoded, so a "/" in a title cannot split the media id.
     */
    private fun homeSectionKey(section: HomePage.Section): String = Uri.encode(section.title.trim())

    /** Resolves a shelf by [homeSectionKey], or by index for ids from older builds. */
    private suspend fun homeSection(sectionId: String): HomePage.Section? {
        val sections = youtubeHome()?.sections ?: return null
        return sections.firstOrNull { homeSectionKey(it) == sectionId }
            ?: sectionId.toIntOrNull()?.let { sections.getOrNull(it) }
    }

    /** Fetches Home ahead of the first browse so Auto does not wait for every page. */
    private fun prefetchHome() {
        scope.launch(Dispatchers.IO) { youtubeHome() }
    }

    /**
     * YouTube Home sections (Quick picks, Keep listening, charts...) as browsable
     * Auto nodes. Previously Auto only showed local Songs/Artists/Albums/Playlists,
     * so YouTube-generated playlists and recommendations were invisible.
     */
    private suspend fun homeSections(): List<MediaItem> {
        return try {
            val home = youtubeHome()
                ?.filterExplicit(context.dataStore.get(HideExplicitKey, false))
                ?.filterVideoSongs(
                    context.dataStore.get(HideVideoSongsKey, false) ||
                        context.dataStore.get(com.epsilonmusic.app.constants.DataSaverEnabledKey, false)
                ) ?: return emptyList()
            home.sections.mapNotNull { section ->
                val artwork = sectionArtwork(section)
                // A shelf whose children homeSectionSongs() cannot render (album-only,
                // artist-only, ...) is a dead end on Automotive: it opens to nothing.
                // Artwork alone does not make a shelf browsable, so require usable
                // children instead of artwork.
                if (section.items.none { it is SongItem || it is PlaylistItem }) {
                    return@mapNotNull null
                }
                browsableMediaItem(
                    "${MusicService.HOME_SECTION}/${homeSectionKey(section)}",
                    section.title,
                    section.label ?: "${section.items.size} items",
                    artwork?.toUri(),
                    MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                    singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
                )
            }
        } catch (e: Exception) {
            reportException(e)
            emptyList()
        }
    }

    /**
     * Header thumbnail when present, otherwise the first item's artwork. Home
     * shelves commonly ship without a header thumbnail, which is why Auto showed
     * a warning icon instead of a cover.
     */
    private fun sectionArtwork(section: HomePage.Section): String? =
        section.thumbnail
            ?: when (val first = section.items.firstOrNull()) {
                is SongItem -> first.thumbnail
                is PlaylistItem -> first.thumbnail
                is AlbumItem -> first.thumbnail
                is ArtistItem -> first.thumbnail
                else -> null
            }

    private suspend fun homeSectionSongs(sectionId: String, parentId: String): List<MediaItem> {
        return try {
            val section = homeSection(sectionId) ?: return emptyList()
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideos = context.dataStore.get(HideVideoSongsKey, false) ||
                context.dataStore.get(com.epsilonmusic.app.constants.DataSaverEnabledKey, false)
            // Filter video songs on the YTItem list (same predicate as elsewhere);
            // filtering MediaItems afterwards would be a no-op.
            section.items.filterVideoSongs(hideVideos).flatMap { item ->
                when (item) {
                    is SongItem -> {
                        if (hideExplicit && item.explicit) return@flatMap emptyList()
                        // Cache online metadata so playback resolves even if DB missed it.
                        try { database.query { insert(item.toMediaMetadata()) } } catch (_: Exception) {}
                        listOf(item.toMediaItem(parentId))
                    }
                    is PlaylistItem -> listOf(item.toBrowsableMediaItem())
                    else -> emptyList()
                }
            }.take(100)
        } catch (e: Exception) {
            reportException(e)
            emptyList()
        }
    }

    private suspend fun suggestedHomeSongs(): List<MediaItem> {
        return try {
            val home = youtubeHome()
                ?.filterExplicit(context.dataStore.get(HideExplicitKey, false))
                ?.filterVideoSongs(
                    context.dataStore.get(HideVideoSongsKey, false) ||
                        context.dataStore.get(com.epsilonmusic.app.constants.DataSaverEnabledKey, false)
                ) ?: return emptyList()
            val songs = home.sections.flatMap { it.items }.filterIsInstance<SongItem>().take(50)
            songs.forEach { item ->
                try { database.query { insert(item.toMediaMetadata()) } } catch (_: Exception) {}
            }
            listOf(shuffleMediaItem(MusicService.HOME)) +
                songs.map { it.toMediaItem("${MusicService.HOME_SECTION}/$SUGGESTED_SECTION_ID") }
        } catch (e: Exception) {
            reportException(e)
            emptyList()
        }
    }

    private suspend fun getMediaItem(mediaId: String, isAutomotive: Boolean = false): MediaItem? {
        val path = mediaId.split("/")
        val type = path.firstOrNull() ?: return null

        return when (type) {
            MusicService.ROOT -> rootMediaItem()
            MusicService.HOME -> browsableMediaItem(
                MusicService.HOME,
                context.getString(R.string.home),
                null,
                drawableUri(R.drawable.home_filled),
                MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_GRID_ITEM,
            )
            MusicService.HOME_SECTION -> {
                val sectionId = path.getOrNull(1) ?: return null
                if (path.size == 2) {
                    homeSections().firstOrNull { it.mediaId == mediaId }
                } else {
                    val songId = path.lastOrNull() ?: return null
                    if (songId == MusicService.SHUFFLE_ACTION) {
                        shuffleMediaItem(mediaId.removeSuffix("/$songId"))
                    } else {
                        database.song(songId).first()?.toMediaItem(mediaId.removeSuffix("/$songId"))
                            ?: homeSectionOnlineSong(sectionId, songId, mediaId.removeSuffix("/$songId"))
                    }
                }
            }
            MusicService.SONG -> {
                if (path.size == 1) {
                    rootChildren().first { it.mediaId == MusicService.SONG }
                } else {
                    database.song(path[1]).first()?.toMediaItem(MusicService.SONG)
                }
            }

            MusicService.ARTIST -> {
                val artistId = path.getOrNull(1) ?: return null
                if (path.size == 2) {
                    database.artist(artistId).first()?.let { artist ->
                        browsableMediaItem(
                            "${MusicService.ARTIST}/${artist.id}",
                            artist.artist.name,
                            context.resources.getQuantityString(
                                R.plurals.n_song,
                                artist.songCount,
                                artist.songCount
                            ),
                            artist.artist.thumbnailUrl?.toUri(),
                            MediaMetadata.MEDIA_TYPE_ARTIST,
                            singleItemStyle = artistItemStyle(isAutomotive),
                        )
                    }
                } else {
                    val songId = path.lastOrNull() ?: return null
                    database.song(songId).first()?.toMediaItem("${MusicService.ARTIST}/$artistId")
                }
            }

            MusicService.ALBUM -> {
                val albumId = path.getOrNull(1) ?: return null
                if (path.size == 2) {
                    database.album(albumId).first()?.let { album ->
                        browsableMediaItem(
                            "${MusicService.ALBUM}/${album.id}",
                            album.album.title,
                            album.artists.joinToString { it.name },
                            album.album.thumbnailUrl?.toUri(),
                            MediaMetadata.MEDIA_TYPE_ALBUM,
                            singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
                        )
                    }
                } else {
                    val songId = path.lastOrNull() ?: return null
                    database.song(songId).first()?.toMediaItem("${MusicService.ALBUM}/$albumId")
                }
            }

            MusicService.PLAYLIST -> {
                val playlistId = path.getOrNull(1) ?: return null
                if (path.size == 2) {
                    playlistMediaItem(playlistId)
                } else {
                    val songId = path.lastOrNull() ?: return null
                    if (songId == MusicService.SHUFFLE_ACTION) {
                        shuffleMediaItem("${MusicService.PLAYLIST}/$playlistId")
                    } else {
                        database.song(songId).first()?.toMediaItem("${MusicService.PLAYLIST}/$playlistId")
                    }
                }
            }

            MusicService.YOUTUBE_PLAYLIST -> {
                val playlistId = path.getOrNull(1) ?: return null
                if (path.size == 2) {
                    youtubePlaylistMediaItem(playlistId)
                } else {
                    val songId = path.lastOrNull() ?: return null
                    if (songId == MusicService.SHUFFLE_ACTION) {
                        shuffleMediaItem("${MusicService.YOUTUBE_PLAYLIST}/$playlistId")
                    } else {
                        database.song(songId).first()?.toMediaItem("${MusicService.YOUTUBE_PLAYLIST}/$playlistId")
                            ?: youtubePlaylistSongMediaItem(playlistId, songId)
                    }
                }
            }

            MusicService.SEARCH -> {
                val songId = path.lastOrNull() ?: return null
                database.song(songId).first()?.toMediaItem(
                    mediaId.removeSuffix("/$songId")
                )
            }

            else -> database.song(mediaId).first()?.toMediaItem()
        }
    }

    private suspend fun playlistMediaItem(playlistId: String): MediaItem? =
        when (playlistId) {
            PlaylistEntity.LIKED_PLAYLIST_ID -> likedSongsMediaItem(database.likedSongsCount().first())
            PlaylistEntity.DOWNLOADED_PLAYLIST_ID -> downloadedSongsMediaItem(downloadUtil.downloads.value.size)
            else -> database.playlist(playlistId).first()?.toBrowsableMediaItem()
        }

    private suspend fun youtubePlaylistMediaItem(playlistId: String): MediaItem =
        try {
            YouTube.playlist(playlistId).getOrNull()?.playlist?.toBrowsableMediaItem()
        } catch (e: Exception) {
            reportException(e)
            null
        } ?: browsableMediaItem(
            "${MusicService.YOUTUBE_PLAYLIST}/$playlistId",
            playlistId,
            "YouTube Music",
            null,
            MediaMetadata.MEDIA_TYPE_PLAYLIST,
            singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
        )

    private suspend fun youtubePlaylistSongMediaItem(
        playlistId: String,
        songId: String,
    ): MediaItem? =
        try {
            YouTube.playlist(playlistId).getOrNull()?.songs
                ?.firstOrNull { it.id == songId }
                ?.toMediaItem("${MusicService.YOUTUBE_PLAYLIST}/$playlistId")
        } catch (e: Exception) {
            reportException(e)
            null
        }

    private suspend fun homeSectionOnlineSong(
        sectionId: String,
        songId: String,
        parentId: String,
    ): MediaItem? {
        if (sectionId == SUGGESTED_SECTION_ID) {
            return suggestedHomeSongs()
                .firstOrNull { it.mediaId.endsWith("/$songId") }
        }
        return try {
            val item = homeSection(sectionId)
                ?.items?.filterIsInstance<SongItem>()
                ?.firstOrNull { it.id == songId } ?: return null
            try { database.query { insert(item.toMediaMetadata()) } } catch (_: Exception) {}
            item.toMediaItem(parentId)
        } catch (e: Exception) {
            reportException(e)
            null
        }
    }

    private fun likedSongsMediaItem(songCount: Int) = browsableMediaItem(
        "${MusicService.PLAYLIST}/${PlaylistEntity.LIKED_PLAYLIST_ID}",
        context.getString(R.string.liked_songs),
        context.resources.getQuantityString(
            R.plurals.n_song,
            songCount,
            songCount
        ),
        drawableUri(R.drawable.favorite),
        MediaMetadata.MEDIA_TYPE_PLAYLIST,
        singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM,
    )

    private fun downloadedSongsMediaItem(songCount: Int) = browsableMediaItem(
        "${MusicService.PLAYLIST}/${PlaylistEntity.DOWNLOADED_PLAYLIST_ID}",
        context.getString(R.string.downloaded_songs),
        context.resources.getQuantityString(
            R.plurals.n_song,
            songCount,
            songCount
        ),
        drawableUri(R.drawable.download),
        MediaMetadata.MEDIA_TYPE_PLAYLIST,
        singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM,
    )

    private fun com.epsilonmusic.app.db.entities.Playlist.toBrowsableMediaItem() = browsableMediaItem(
        "${MusicService.PLAYLIST}/$id",
        playlist.name,
        context.resources.getQuantityString(
            R.plurals.n_song,
            songCount,
            songCount
        ),
        thumbnails.firstOrNull()?.toUri(),
        MediaMetadata.MEDIA_TYPE_PLAYLIST,
        singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
    )

    private fun PlaylistItem.toBrowsableMediaItem() = browsableMediaItem(
        "${MusicService.YOUTUBE_PLAYLIST}/$id",
        title,
        author?.name ?: "YouTube Music",
        thumbnail?.toUri(),
        MediaMetadata.MEDIA_TYPE_PLAYLIST,
        singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
    )

    private fun shuffleMediaItem(parentId: String) = MediaItem.Builder()
        .setMediaId("$parentId/${MusicService.SHUFFLE_ACTION}")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(context.getString(R.string.shuffle))
                .setArtworkUri(drawableUri(R.drawable.shuffle))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(playableMediaItemExtras())
                .build()
        ).build()

    private fun browsableMediaItem(
        id: String,
        title: String,
        subtitle: String?,
        iconUri: Uri?,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_MUSIC,
        singleItemStyle: Int = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM,
    ) = MediaItem
        .Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata
                .Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtist(subtitle)
                .setArtworkUri(iconUri)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .setMediaType(mediaType)
                .setExtras(
                    contentStyleExtras(
                        browsableStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
                        playableStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
                        singleItemStyle = singleItemStyle,
                    )
                )
                .build(),
        ).build()

    private fun Song.toMediaItem(path: String, isPlayable: Boolean = true, isBrowsable: Boolean = false): MediaItem {
        return MediaItem
            .Builder()
            .setMediaId("$path/$id")
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(song.title)
                    .setSubtitle(artists.joinToString { it.name })
                    .setArtist(artists.joinToString { it.name })
                    .setArtworkUri(song.thumbnailUrl?.toUri())
                    .setIsPlayable(isPlayable)
                    .setIsBrowsable(isBrowsable)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(playableMediaItemExtras())
                    .build(),
            ).build()
    }

    private fun SongItem.toMediaItem(path: String) = MediaItem.Builder()
        .setMediaId("$path/$id")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(artists.joinToString(", ") { it.name })
                .setArtist(artists.joinToString(", ") { it.name })
                .setArtworkUri(thumbnail.toUri())
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(playableMediaItemExtras())
                .build()
        )
        .build()

    private fun MediaLibraryService.LibraryParams?.withContentStyleHints(): MediaLibraryService.LibraryParams {
        val extras = Bundle(this?.extras ?: Bundle()).apply {
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
        }

        return MediaLibraryService.LibraryParams.Builder()
            .setOffline(this?.isOffline ?: false)
            .setRecent(this?.isRecent ?: false)
            .setSuggested(this?.isSuggested ?: false)
            .setExtras(extras)
            .build()
    }

    private fun playableMediaItemExtras() = contentStyleExtras(
        singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
    )

    private fun contentStyleExtras(
        browsableStyle: Int? = null,
        playableStyle: Int? = null,
        singleItemStyle: Int? = null,
    ) = Bundle().apply {
        browsableStyle?.let {
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, it)
        }
        playableStyle?.let {
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, it)
        }
        singleItemStyle?.let {
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM, it)
        }
    }

    private fun <T> List<T>.paginate(page: Int, pageSize: Int): List<T> {
        if (page < 0 || pageSize < 1 || isEmpty()) return emptyList()
        if (pageSize == Int.MAX_VALUE) return this

        val fromIndex = page.toLong() * pageSize
        if (fromIndex >= size) return emptyList()

        return subList(fromIndex.toInt(), minOf(fromIndex.toInt() + pageSize, size))
    }

    private companion object {
        /**
         * How long a YouTube Home snapshot is reused for automotive browse calls
         * before it is refreshed (and merged, see [mergeHomeSnapshots]).
         */
        const val HOME_CACHE_TTL_MS = 5 * 60 * 1000L
        const val HOME_MAX_PAGES = 6
        const val HOME_FIRST_PAGE_TIMEOUT_MS = 10_000L
        /** A shelf YouTube has not returned for this long is dropped from the snapshot. */
        const val HOME_SECTION_MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
        const val HOME_CONTINUATION_TIMEOUT_MS = 12_000L
        const val SUGGESTED_SECTION_ID = "suggested"
        const val HOME_SHELF_PREVIEW_SIZE = 8
        const val SEARCH_LOCAL_LIMIT = 3
        const val SEARCH_TOP_RESULTS = 3
        const val SEARCH_RESULT_LIMIT = 30
        const val VOICE_SEARCH_RETRIES = 2
        const val VOICE_SEARCH_RETRY_MS = 1_500L
        const val VOICE_COLLECTION_LIMIT = 200
        const val SEARCH_SONG_CACHE = 300
        const val MEDIA_EXTRA_PLAYLIST = "android.intent.extra.playlist"
        const val MIX_EXTRA_PAGES = 3
        const val MIX_EXTRA_PAGES_TIMEOUT_MS = 4_000L
    }
}
