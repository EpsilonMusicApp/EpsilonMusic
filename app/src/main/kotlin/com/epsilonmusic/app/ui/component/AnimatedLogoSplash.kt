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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
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
 * The music-note logo is "drawn" stroke-by-stroke using PathMeasure
 * to extract a partial path, then fills in, holds briefly, and fades out.
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
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(180.dp)
            ) {
                // Glow background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
                    modifier = Modifier
                        .size(120.dp)
                        .padding(8.dp)
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
 * Uses PathMeasure to extract a partial path for the drawing effect.
 */
private fun DrawScope.drawMusicNote(
    drawProgress: Float,
    fillAlpha: Float,
    color: Color
) {
    val canvasSize = size.minDimension
    val scaleFactor = canvasSize / 960f

    // Build the full music-note path at original viewport coordinates (960x960)
    val fullPath = Path().apply {
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

    // Measure the path and extract a partial segment for the drawing animation
    val pathMeasure = PathMeasure(fullPath, false)
    val totalLength = pathMeasure.length
    val drawLength = totalLength * drawProgress
    val partialPath = Path()
    pathMeasure.getSegment(0f, drawLength, partialPath, true)

    // Scale the drawing to fit the canvas
    scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
        if (drawProgress < 1f) {
            // Phase 1: draw the partial stroke
            drawPath(
                path = partialPath,
                color = color,
                style = Stroke(
                    width = 12f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        } else {
            // Phase 2+: filled note with stroke outline
            drawPath(
                path = fullPath,
                color = color.copy(alpha = fillAlpha),
                style = Fill
            )
            drawPath(
                path = fullPath,
                color = color,
                style = Stroke(
                    width = 6f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
