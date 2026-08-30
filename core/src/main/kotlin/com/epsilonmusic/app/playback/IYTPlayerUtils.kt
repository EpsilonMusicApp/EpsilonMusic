package com.epsilonmusic.app.playback

import android.content.Context

interface IYTPlayerUtils {
    suspend fun getStreamInfo(videoId: String, context: Context): Any?
    suspend fun getAudioConfig(videoId: String): Any?
}
