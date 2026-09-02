package com.epsilonmusic.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Aesthetic drawing animation splash shown on cold start.
 *
 * The music-note logo is "drawn" stroke-by-stroke using a PathEffect
 * dash animation, then fills in, holds briefly, and fades out.
 *
 * Animation timeline (total ~2.0s):
 *   0.0s – 1.2s : path draws from start to end (stroke reveal)
 *   1.2s – 1.5s : fill fades in
 *   1.5s – 1.8s : hold
 *   1.8s – 2.0s : fade out everything
 *
 * The path is the music-note shape from @drawable/music_note.xml
 * (viewport 960x960), scaled to fit the canvas.
 */
@Composable
fun AnimatedLogoSplash(
    onAnimationEnd: () -> Unit
) {
    var drawProgress by remember { mutableStateOf(0f) }
    var fillAlpha by remember { mutableStateOf(0f) }
    var splashAlpha by remember { mutableStateOf(1f) }
    var textAlpha by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        // Phase 1: draw the path (0 → 1) over 1200ms
        val drawDuration = 1200L
        val drawSteps = 60
        for (i in 0..drawSteps) {
            drawProgress = i.toFloat() / drawSteps
            delay(drawDuration / drawSteps)
        }
        drawProgress = 1f

        // Phase 2: fade in the fill + text (200ms)
        delay(50)
        textAlpha = 1f
        val fillDuration = 250L
        val fillSteps = 25
        for (i in 0..fillSteps) {
            fillAlpha = i.toFloat() / fillSteps
            delay(fillDuration / fillSteps)
        }
        fillAlpha = 1f

        // Phase 3: hold (300ms)
        delay(300)

        // Phase 4: fade out everything (300ms)
        val fadeDuration = 300L
        val fadeSteps = 30
        for (i in 0..fadeSteps) {
            splashAlpha = 1f - (i.toFloat() / fadeSteps)
            delay(fadeDuration / fadeSteps)
        }
        splashAlpha = 0f
        delay(50)
        onAnimationEnd()
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(splashAlpha)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.08f),
                        surfaceColor
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Drawing animation canvas
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Glow halo behind the logo (subtle pulse)
                val infiniteTransition = rememberInfiniteTransition(label = "glowPulse")
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.15f,
                    targetValue = 0.35f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "glowAlpha"
                )
                val glowRotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(6000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "glowRotation"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize(0.6f)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    Color.Transparent,
                                    primaryColor.copy(alpha = glowAlpha * 0.3f),
                                    primaryColor.copy(alpha = glowAlpha * 0.6f),
                                    primaryColor.copy(alpha = glowAlpha * 0.3f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // The drawn music note
                Canvas(
                    modifier = Modifier.padding(16.dp)
                ) {
                    val canvasSize = size.minDimension
                    val scale = canvasSize / 960f

                    // Build the music-note path (from music_note.xml)
                    val notePath = Path().apply {
                        // M400,840 Q334,840 287,793 Q240,746 240,680
                        // Q240,614 287,567 Q334,520 400,520
                        // Q423,520 442.5,525.5 Q462,531 480,542
                        // L480,120 L720,120 L720,280 L560,280
                        // L560,680 Q560,746 513,793 Q466,840 400,840 Z
                        moveTo(400f, 840f)
                        quadTo(334f, 840f, 287f, 793f)
                        quadTo(240f, 746f, 240f, 680f)
                        quadTo(240f, 614f, 287f, 567f)
                        quadTo(334f, 520f, 400f, 520f)
                        quadTo(423f, 520f, 442.5f, 525.5f)
                        quadTo(462f, 531f, 480f, 542f)
                        lineTo(480f, 120f)
                        lineTo(720f, 120f)
                        lineTo(720f, 280f)
                        lineTo(560f, 280f)
                        lineTo(560f, 680f)
                        quadTo(560f, 746f, 513f, 793f)
                        quadTo(466f, 840f, 400f, 840f)
                        close()
                    }

                    // Scale the path to fit the canvas
                    notePath.transform { matrix ->
                        matrix.scale(scale, scale)
                        notePath
                    }

                    // Phase 1: stroke draw animation
                    // PathEffect.dashPathEffect with a large dash and a gap that shrinks
                    // as drawProgress goes from 0 to 1 — this "reveals" the path.
                    val pathLength = 3000f // approximate perimeter of the music note
                    val revealedLength = pathLength * drawProgress
                    val dashEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(revealedLength, pathLength),
                        phase = 0f
                    )

                    if (drawProgress < 1f) {
                        // Still drawing — show stroke only
                        drawPath(
                            path = notePath,
                            color = primaryColor,
                            style = Stroke(
                                width = 8f * scale,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                                pathEffect = dashEffect
                            )
                        )
                    } else {
                        // Drawing complete — show filled note with stroke
                        drawPath(
                            path = notePath,
                            color = primaryColor.copy(alpha = fillAlpha),
                            style = androidx.compose.ui.graphics.drawscope.Fill
                        )
                        drawPath(
                            path = notePath,
                            color = primaryColor,
                            style = Stroke(
                                width = 4f * scale,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }

            // App name with Inter-inspired styling
            Text(
                text = "Epsilon Music",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.02).sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .alpha(textAlpha)
                    .padding(top = 24.dp)
            )
        }
    }
}
