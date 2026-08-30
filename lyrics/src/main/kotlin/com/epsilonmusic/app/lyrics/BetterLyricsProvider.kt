

package com.epsilonmusic.app.lyrics

import android.content.Context
import com.epsilonmusic.app.betterlyrics.BetterLyrics
import com.epsilonmusic.app.constants.EnableBetterLyricsKey
import com.epsilonmusic.app.utils.dataStore
import com.epsilonmusic.app.utils.get

object BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableBetterLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = BetterLyrics.getLyrics(title, artist, duration, album)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        BetterLyrics.getAllLyrics(title, artist, duration, album, callback)
    }
}
