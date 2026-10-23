package com.epsilonmusic.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.epsilonmusic.app.R

/**
 * Epsilon brand mark for the home top bar: the ring emblem from the launcher
 * icon, set in ink on a peach gradient chip (the "Peach Pop" treatment from
 * the Bold icon set). The chip gives the mark a consistent presence on both
 * light and dark surfaces.
 *
 * @param size chip size (the emblem sits inside at ~82%).
 */
@Composable
fun EpsilonBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    // One-shot entrance pop when the mark first appears (app open / activity recreation).
    val entrance = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = entrance.value
                scaleY = entrance.value
            }
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFC8B2),
                        Color(0xFFFAA08C),
                        Color(0xFFE87664),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_epsilon_emblem),
            contentDescription = null,
            modifier = Modifier.size(size * 0.82f),
            colorFilter = ColorFilter.tint(Color(0xFF0F0F11)),
        )
    }
}
