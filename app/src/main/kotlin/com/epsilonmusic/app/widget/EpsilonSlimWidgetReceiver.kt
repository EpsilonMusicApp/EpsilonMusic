/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.epsilonmusic.app.widget

/**
 * AppWidgetProvider for the Slim widget design.
 * Rendering is handled by [EpsilonMusicWidgetManager]; this receiver only
 * identifies the widget, inheriting update/action handling from [MusicWidgetReceiver].
 */
class EpsilonSlimWidgetReceiver : MusicWidgetReceiver()
