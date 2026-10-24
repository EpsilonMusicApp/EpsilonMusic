package com.epsilonmusic.app.musicflow

import androidx.datastore.preferences.core.Preferences
import com.epsilonmusic.app.constants.CanvasThumbnailAnimationKey
import com.epsilonmusic.app.constants.DataSaverEnabledKey
import com.epsilonmusic.app.constants.MusicFlowAnimatedBackgroundKey
import com.epsilonmusic.app.constants.MusicFlowAutoNextKey
import com.epsilonmusic.app.constants.MusicFlowAutoplayKey
import com.epsilonmusic.app.constants.MusicFlowCellularPrefetchKey
import com.epsilonmusic.app.constants.MusicFlowEnabledKey
import com.epsilonmusic.app.constants.MusicFlowHideRecentKey
import com.epsilonmusic.app.constants.MusicFlowLyricsKey

data class MusicFlowPreferences(
  val enabled: Boolean = true,
  val autoplay: Boolean = true,
  val autoNext: Boolean = true,
  val animatedBackground: Boolean = false,
  val lyrics: Boolean = true,
  val cellularPrefetch: Boolean = true,
  val hideRecent: Boolean = true,
  val dataSaver: Boolean = false,
) {
  companion object {
    fun from(preferences: Preferences) =
      MusicFlowPreferences(
        enabled = preferences[MusicFlowEnabledKey] ?: true,
        autoplay = preferences[MusicFlowAutoplayKey] ?: true,
        autoNext = preferences[MusicFlowAutoNextKey] ?: true,
        animatedBackground =
          preferences[MusicFlowAnimatedBackgroundKey]
            ?: preferences[CanvasThumbnailAnimationKey]
            ?: false,
        lyrics = preferences[MusicFlowLyricsKey] ?: true,
        cellularPrefetch = preferences[MusicFlowCellularPrefetchKey] ?: true,
        hideRecent = preferences[MusicFlowHideRecentKey] ?: true,
        dataSaver = preferences[DataSaverEnabledKey] ?: false,
      )
  }
}
