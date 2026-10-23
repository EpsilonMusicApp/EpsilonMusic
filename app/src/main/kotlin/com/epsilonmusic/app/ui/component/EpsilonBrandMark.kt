package com.epsilonmusic.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.epsilonmusic.app.R

/**
 * Epsilon brand mark for the home top bar: the ring emblem from the app icon,
 * tinted with the theme's on-surface color so it follows the active theme —
 * light on dark surfaces, dark on light ones.
 *
 * @param glyphSize height of the emblem glyph (width follows the glyph's aspect).
 */
@Composable
fun EpsilonBrandMark(
    modifier: Modifier = Modifier,
    glyphSize: Dp = 30.dp,
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
            .size(glyphSize + 4.dp)
            .graphicsLayer {
                scaleX = entrance.value
                scaleY = entrance.value
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_epsilon_emblem),
            contentDescription = null,
            modifier = Modifier.size(glyphSize),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
        )
    }
}
