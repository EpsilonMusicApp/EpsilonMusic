

package com.epsilonmusic.app.constants

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val CONTENT_TYPE_HEADER = 0
const val CONTENT_TYPE_LIST = 1
const val CONTENT_TYPE_SONG = 2
const val CONTENT_TYPE_ARTIST = 3
const val CONTENT_TYPE_ALBUM = 4
const val CONTENT_TYPE_PLAYLIST = 5

val FloatingToolbarHeight = 72.dp
val FloatingToolbarHorizontalPadding = 16.dp
val FloatingToolbarBottomPadding = 12.dp
val NavigationBarHeight = FloatingToolbarHeight
val SlimNavBarHeight = 64.dp
val MiniPlayerHeight = 64.dp
val MinMiniPlayerHeight = 16.dp
val MiniPlayerBottomSpacing = 8.dp 
val QueuePeekHeight = 64.dp
val AppBarHeight = 64.dp

val ListItemHeight = 64.dp
val SuggestionItemHeight = 56.dp
val SearchFilterHeight = 48.dp
val ListThumbnailSize = 48.dp
// Increased the size gap so the Grid cell size setting is visually obvious.
// Previously BIG=128+24=152dp and SMALL=128-24=104dp (only 48dp difference),
// which often resulted in the same number of columns after GridCells.Adaptive
// rounded down. Now BIG=152dp and SMALL=92dp (60dp difference) — the user
// will clearly see more / fewer columns when toggling the setting.
val SmallGridThumbnailHeight = 92.dp
val GridThumbnailHeight = 152.dp
val AlbumThumbnailSize = 144.dp

val ThumbnailCornerRadius = 6.dp 

val PlayerHorizontalPadding = 32.dp

val NavigationBarAnimationSpec = spring<Dp>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow
)

val BottomSheetAnimationSpec = spring<Dp>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

val BottomSheetSoftAnimationSpec = spring<Dp>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow
)
