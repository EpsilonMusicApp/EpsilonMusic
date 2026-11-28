package com.epsilonmusic.app.playback

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.epsilonmusic.app.utils.AutomotiveArtwork.withAutomotiveArtworkUri

/**
 * The player exposed through the [androidx.media3.session.MediaSession].
 *
 * Android Auto and the instrument cluster read the now-playing cover from the
 * legacy session metadata (album-art / display-icon URI plus an optional
 * bitmap), which Media3 builds from [getMediaMetadata]. The items carry
 * `https://` cover URLs, which those surfaces do not load, so the cover showed
 * up only where the bitmap happened to be used. This swaps the URI for the
 * local [com.epsilonmusic.app.utils.AutomotiveArtworkProvider] one at the
 * session boundary only: the app UI keeps using the ExoPlayer directly and is
 * unaffected.
 *
 * It also adds the current song's synced lyrics as `lyricInfo` for ColorOS /
 * OxygenOS lock-screen and AOD lyrics (see [OplusLyricInfo]).
 */
class SessionArtworkPlayer(
    private val context: Context,
    player: Player,
    private val lyricInfo: SessionLyricInfo? = null,
) : ForwardingPlayer(player) {

    private val listeners = mutableSetOf<Player.Listener>()

    override fun getMediaMetadata(): MediaMetadata =
        withLyricInfo(super.getMediaMetadata().withAutomotiveArtworkUri(context))

    /**
     * Media3 copies String extras into the platform session metadata, where
     * ColorOS SystemUI reads `lyricInfo`. [MediaMetadata.equals] ignores extras,
     * so the session would not republish an extras-only change (lyrics usually
     * arrive after the track started); the station field takes part in equals,
     * is not exported to the platform session and gets an invisible suffix
     * (appended to any existing station) that alternates with every new payload.
     */
    private fun withLyricInfo(metadata: MediaMetadata): MediaMetadata {
        val info = lyricInfo?.current(super.getCurrentMediaItem()?.mediaId) ?: return metadata
        val extras = Bundle(metadata.extras ?: Bundle()).apply { putString(OplusLyricInfo.METADATA_KEY, info.json) }
        return metadata.buildUpon()
            .setExtras(extras)
            .setStation("${metadata.station ?: ""}${info.stamp}")
            .build()
    }

    /** Republishes the session metadata after [SessionLyricInfo] changed. */
    fun onLyricInfoChanged() {
        val dispatch = Runnable {
            val metadata = getMediaMetadata()
            listeners.toList().forEach { it.onMediaMetadataChanged(metadata) }
        }
        if (Looper.myLooper() == applicationLooper) dispatch.run() else Handler(applicationLooper).post(dispatch)
    }

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
        super.removeListener(listener)
    }

    override fun getCurrentMediaItem(): MediaItem? =
        super.getCurrentMediaItem()?.withAutomotiveArtworkUri(context)
}
