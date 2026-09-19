package com.epsilonmusic.app.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.epsilonmusic.app.R

/**
 * Brand colors for the Epsilon mark — deliberately fixed (not theme-aware) so the
 * logo reads identically in light and dark themes, like a real brand asset.
 */
private val BrandViolet = Color(0xFF7C3AED)
private val BrandPink = Color(0xFFEC4899)
private val BrandRose = Color(0xFFF43F5E)

/**
 * Bright Epsilon brand mark for the home top bar: a small app-icon-style chip with a
 * vivid violet-to-pink-to-rose diagonal gradient, a glossy top sheen, the white
 * note+pin glyph and a subtle ambient "breathing" glow.
 *
 * @param chipSize diameter of the gradient chip; the glow extends slightly beyond it.
 * @param glowEnabled ambient breathing glow. Disable for static contexts (e.g. previews).
 */
@Composable
fun EpsilonBrandMark(
    modifier: Modifier = Modifier,
    chipSize: Dp = 34.dp,
    glowEnabled: Boolean = true,
) {
    val chipShape = RoundedCornerShape(chipSize * 0.29f)

    val infiniteTransition = rememberInfiniteTransition(label = "brandGlow")
    val glowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "brandGlowPhase",
    )

    // One-shot entrance pop when the mark first appears (app open / activity recreation).
    val entrance = remember { Animatable(0.6f) }
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
        modifier = modifier.size(chipSize + 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (glowEnabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        // Ambient breathing: gentle scale + alpha oscillation, intentionally
                        // subtle (alpha stays <= 0.30) so it reads as light, not a blink.
                        scaleX = 0.88f + 0.20f * glowPhase
                        scaleY = 0.88f + 0.20f * glowPhase
                        alpha = 0.16f + 0.14f * glowPhase
                    }
                    .clip(chipShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(BrandPink.copy(alpha = 0.5f), Color.Transparent),
                        ),
                    ),
            )
        }

        Box(
            modifier = Modifier
                .size(chipSize)
                .graphicsLayer {
                    scaleX = entrance.value
                    scaleY = entrance.value
                }
                .clip(chipShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(BrandViolet, BrandPink, BrandRose),
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    ),
                ),
        ) {
            // Glossy top sheen — a soft white fade over the upper half of the chip.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.White.copy(alpha = 0.24f),
                                0.55f to Color.Transparent,
                            ),
                        ),
                    ),
            )
            Image(
                painter = painterResource(R.drawable.ic_epsilon_mark),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(chipSize * 0.66f),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
    }
}
