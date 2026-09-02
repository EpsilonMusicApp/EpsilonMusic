package com.epsilonmusic.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epsilonmusic.app.R
import kotlinx.coroutines.delay

/**
 * Aesthetic animated logo splash shown when the app opens.
 *
 * The logo scales up from 0.6 to 1.0 with a spring-like ease, fades in from
 * 0 to 1, and a subtle glow pulse continues in the background. After 1.2s
 * the splash auto-dismisses via [onAnimationEnd].
 *
 * The animation is intentionally short (1.2s) so it doesn't annoy users who
 * open the app frequently. It only shows on cold start (not on task switch).
 */
@Composable
fun AnimatedLogoSplash(
    onAnimationEnd: () -> Unit
) {
    var animateIn by remember { mutableStateOf(false) }
    var dismiss by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Start the entrance animation on the next frame
        delay(50)
        animateIn = true
        // Hold for 1.2s total, then dismiss
        delay(1200)
        dismiss = true
        delay(300) // wait for fade-out
        onAnimationEnd()
    }

    val scale by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0.6f,
        animationSpec = tween(durationMillis = 600, easing = { t ->
            // Overshoot easing for a bouncy entrance
            val c1 = 1.70158f
            val c3 = c1 + 1f
            1f + c3 * Math.pow((t - 1).toDouble(), 3.0).toFloat() + c1 * Math.pow((t - 1).toDouble(), 2.0).toFloat()
        }),
        label = "logoScale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (dismiss) 0f else if (animateIn) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "logoAlpha"
    )

    // Subtle infinite glow pulse
    val infiniteTransition = rememberInfiniteTransition(label = "glowPulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glowRotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha * 0.15f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Rotating glow ring behind the logo
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .rotate(rotation)
                        .alpha(glowAlpha * 0.4f)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // The app logo
                Image(
                    painter = painterResource(R.drawable.ic_launcher_nobg),
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .scale(scale)
                )
            }

            // App name with fade-in
            Text(
                text = "Epsilon Music",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .alpha(if (animateIn) 1f else 0f)
                    .padding(top = 16.dp)
            )
        }
    }
}
