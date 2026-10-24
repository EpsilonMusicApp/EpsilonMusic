package com.epsilonmusic.app.playback

import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec
import com.epsilonmusic.app.utils.PlayerClient
import com.epsilonmusic.app.utils.YTPlayerUtils.PlaybackData

internal data class ResolvedStream(
  val url: String,
  val expiresAtMs: Long,
  val headers: Map<String, String>,
  val isFromExtension: Boolean = false,
) {
  fun resolve(dataSpec: DataSpec): DataSpec =
    dataSpec.buildUpon()
      .setUri(url.toUri())
      .setHttpRequestHeaders(dataSpec.httpRequestHeaders + headers)
      .build()

  companion object {
    fun from(
      playbackData: PlaybackData,
      nowMs: Long = System.currentTimeMillis(),
      isFromExtension: Boolean = false,
    ): ResolvedStream =
      ResolvedStream(
        url = playbackData.streamUrl,
        expiresAtMs = nowMs + playbackData.streamExpiresInSeconds * 1000L,
        headers = PlayerClient.forStreamUrl(playbackData.streamUrl).mediaHeaders(),
        isFromExtension = isFromExtension,
      )
  }
}
