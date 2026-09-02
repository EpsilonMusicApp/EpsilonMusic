package com.epsilonmusic.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Aesthetic drawing animation splash shown on cold start.
 *
 * The music-note logo is "drawn" stroke-by-stroke using a PathEffect
 * dash animation, then fills in, holds briefly, and fades out.
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
        // Phase 1: draw the path (0 -> 1) over 1200ms
        val drawDuration = 1200L
        val drawSteps = 60
        for (i in 0..drawSteps) {
            drawProgress = i.toFloat() / drawSteps
            delay(drawDuration / drawSteps)
        }
        drawProgress = 1f

        // Phase 2: fade in the fill + text (250ms)
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

            Box(
                contentAlignment = Alignment.Center
            ) {
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

                // The drawn music note — using DrawScope.scale to fit the path
                Canvas(
                    modifier = Modifier.padding(16.dp)
                ) {
                    drawMusicNote(
                        drawProgress = drawProgress,
                        fillAlpha = fillAlpha,
                        color = primaryColor
                    )
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

/**
 * Draws the music-note logo with a stroke-reveal animation.
 * The path coordinates are from @drawable/music_note.xml (viewport 960x960).
 * The drawing is scaled to fit the DrawScope using [DrawScope.scale].
 */
private fun DrawScope.drawMusicNote(
    drawProgress: Float,
    fillAlpha: Float,
    color: Color
) {
    val canvasSize = size.minDimension
    val scale = canvasSize / 960f

    // Build the music-note path at original viewport coordinates (960x960)
    val notePath = Path().apply {
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

    // Use DrawScope.scale to fit the 960x960 path into the canvas.
    // The pivot is (0, 0) so the path scales from the top-left corner.
    scale(scale, scale, pivot = Offset.Zero) {
        if (drawProgress < 1f) {
            // Phase 1: stroke draw animation using dashPathEffect
            val pathLength = 3000f
            val revealedLength = pathLength * drawProgress
            val dashEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(revealedLength, pathLength),
                phase = 0f
            )
            drawPath(
                path = notePath,
                color = color,
                style = Stroke(
                    width = 8f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = dashEffect
                )
            )
        } else {
            // Phase 2+: filled note with stroke outline
            drawPath(
                path = notePath,
                color = color.copy(alpha = fillAlpha),
                style = Fill
            )
            drawPath(
                path = notePath,
                color = color,
                style = Stroke(
                    width = 4f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
