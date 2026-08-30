package com.epsilonmusic.app.playback

import com.epsilonmusic.app.db.entities.SongEntity

interface ISyncUtils {
    fun likeSong(song: SongEntity)
}
