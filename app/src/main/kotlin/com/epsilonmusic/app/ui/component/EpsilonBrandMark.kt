package com.epsilonmusic.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.epsilonmusic.app.R

/**
 * Bright Epsilon brand mark for the home top bar: the music-note glyph from the
 * app icon, rendered in pure white — no container, no gradient, just the mark.
 *
 * On light surfaces a soft layered shadow keeps the white glyph legible; on dark
 * surfaces the note floats clean and bright.
 *
 * @param glyphSize height of the note glyph (width follows the glyph's aspect).
 */
@Composable
fun EpsilonBrandMark(
    modifier: Modifier = Modifier,
    glyphSize: Dp = 30.dp,
) {
    val isLightSurface = MaterialTheme.colorScheme.surface.luminance() >= 0.5f

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
        if (isLightSurface) {
            LightSurfaceShadow(glyphSize)
        }
        Image(
            painter = painterResource(R.drawable.ic_epsilon_mark),
            contentDescription = null,
            modifier = Modifier.size(glyphSize),
            colorFilter = ColorFilter.tint(Color.White),
        )
    }
}

/**
 * Keeps the pure-white glyph visible on light surfaces: three stacked ink-tinted
 * copies of the glyph offset slightly downward. [Modifier.blur] softens them into
 * a standard drop shadow on Android 12+; below that it is a no-op and the stack
 * reads as a crisp offset print shadow instead.
 */
@Composable
private fun BoxScope.LightSurfaceShadow(glyphSize: Dp) {
    val ink = Color(0xFF1F1B2E)
    val layers = listOf(0.75f to 0.085f, 1.5f to 0.085f, 2.25f to 0.10f)
    layers.forEach { (offsetY, layerAlpha) ->
        Image(
            painter = painterResource(R.drawable.ic_epsilon_mark),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(glyphSize)
                .offset(y = offsetY.dp)
                .blur(2.dp)
                .alpha(layerAlpha),
            colorFilter = ColorFilter.tint(ink),
        )
    }
}
