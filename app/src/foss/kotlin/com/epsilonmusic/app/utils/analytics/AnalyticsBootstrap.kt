package com.epsilonmusic.app.utils.analytics

import android.content.Context

/**
 * FOSS flavor: no-op. No Firebase SDK on this classpath; the shared [Analytics]
 * facade stays disabled and every call is a no-op.
 */
object AnalyticsBootstrap {
    fun install(context: Context) = Unit
}
