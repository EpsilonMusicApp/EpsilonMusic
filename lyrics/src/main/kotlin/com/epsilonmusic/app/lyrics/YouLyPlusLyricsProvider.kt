

package com.epsilonmusic.app.lyrics

import android.content.Context
import com.music.youlyplus.YouLyPlus
import com.epsilonmusic.app.constants.EnableYouLyPlusKey
import com.epsilonmusic.app.utils.dataStore
import com.epsilonmusic.app.utils.get

object YouLyPlusLyricsProvider : LyricsProvider {
    override val name = "YouLyPlus"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableYouLyPlusKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = YouLyPlus.getLyrics(title, artist, duration, album, id)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        YouLyPlus.getAllLyrics(title, artist, duration, album, id, null, callback)
    }
}

