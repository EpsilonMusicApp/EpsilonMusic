package com.epsilonmusic.app.ui.screens.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epsilonmusic.app.R
import com.epsilonmusic.app.epsilonmusic.updater.getLatestAvailableVersion

/**
 * Compact, animated "update available" banner for the top of the Settings home.
 *
 * Design notes:
 * - small fixed footprint (~64dp tall) — replaces the old broken full-res
 *   launcher-icon row that rendered at 1080dp and letter-stacked its text
 * - brand mark chip with a soft sonar-ring ping draws the eye without being
 *   flashy; a gently pulsing NEW chip adds urgency on the title line
 * - press feedback (spring scale) matches the About screen rows
 */
@Composable
fun UpdateAvailableBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val latestVersion = remember { getLatestAvailableVersion(context)?.removePrefix("v") }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // ---- animations -------------------------------------------------------
    val pulse = rememberInfiniteTransition(label = "updateBannerPulse")
    // 0 -> 1 sawtooth driving the sonar ring (expands and fades out, repeats)
    val ringProgress by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing)
        ),
        label = "ringProgress"
    )
    // gentle breathing for the NEW chip
    val newChipAlpha by pulse.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "newChipAlpha"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bannerPressScale"
    )

    val bannerShape = RoundedCornerShape(20.dp)
    val chipShape = RoundedCornerShape(13.dp)
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(bannerShape)
            .background(primary.copy(alpha = if (isDark) 0.16f else 0.07f))
            .border(
                width = 1.dp,
                color = primary.copy(alpha = if (isDark) 0.30f else 0.22f),
                shape = bannerShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Brand mark chip with sonar ping
            Box(contentAlignment = Alignment.Center) {
                // expanding ring
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .graphicsLayer {
                            val p = ringProgress
                            scaleX = 1f + 0.34f * p
                            scaleY = 1f + 0.34f * p
                            alpha = 0.42f * (1f - p)
                        }
                        .clip(chipShape)
                        .border(1.5.dp, primary, chipShape)
                )
                // chip
                Surface(
                    shape = chipShape,
                    color = primary.copy(alpha = if (isDark) 0.20f else 0.12f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(R.drawable.ic_epsilon_logo),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            colorFilter = ColorFilter.tint(primary)
                        )
                    }
                }
            }

            // Title + subtitle
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.update_available_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = primary.copy(alpha = if (isDark) 0.22f else 0.14f),
                        modifier = Modifier.graphicsLayer { alpha = newChipAlpha }
                    ) {
                        Text(
                            text = stringResource(R.string.update_banner_new_chip),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
                            color = primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = if (!latestVersion.isNullOrEmpty()) {
                        stringResource(R.string.update_banner_version, latestVersion)
                    } else {
                        stringResource(R.string.update_banner_tap_to_view)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Trailing arrow
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
        }
    }
}
