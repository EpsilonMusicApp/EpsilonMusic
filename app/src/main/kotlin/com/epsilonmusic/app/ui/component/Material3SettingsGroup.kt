

package com.epsilonmusic.app.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp


import com.epsilonmusic.app.ui.utils.scrollToOnHighlight
import com.epsilonmusic.app.utils.listItemShape
import androidx.compose.foundation.ScrollState

/**
 * Grouped settings list styled after the expressive grouped-list design:
 * smooth-cornered translucent cards (rounded top on the first row, rounded
 * bottom on the last, flat in between) with neutral Material 3 ListItem rows.
 */
@Composable
fun Material3SettingsGroup(
    title: String? = null,
    compact: Boolean = false,
    scrollState: ScrollState? = null,
    items: List<Material3SettingsItem>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
            )
        }

        items.forEachIndexed { index, item ->
            val shape = listItemShape(index, items.size, 16.dp)
            val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = shape,
                color = when {
                    item.isHighlighted -> MaterialTheme.colorScheme.primaryContainer
                    isDarkTheme -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                }
            ) {
                SettingsListItemRow(item = item, compact = compact, scrollState = scrollState)
            }
        }
    }
}


@Composable
private fun SettingsListItemRow(
    item: Material3SettingsItem,
    compact: Boolean = false,
    scrollState: ScrollState? = null
) {
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val iconTint = when {
        !item.enabled -> disabledColor
        item.isHighlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = item.enabled && item.onClick != null,
                onClick = { item.onClick?.invoke() }
            )
            .then(
                if (scrollState != null)
                    Modifier.scrollToOnHighlight(scrollState, item.isHighlighted)
                else Modifier
            ),
        leadingContent = item.customIcon
            ?: item.icon?.let { icon ->
                {
                    if (item.tintIcon) {
                        if (item.showBadge) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = MaterialTheme.colorScheme.error)
                                }
                            ) {
                                Icon(
                                    painter = icon,
                                    contentDescription = null,
                                    tint = iconTint
                                )
                            }
                        } else {
                            Icon(
                                painter = icon,
                                contentDescription = null,
                                tint = iconTint
                            )
                        }
                    } else {
                        Image(
                            painter = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(if (compact) 34.dp else 40.dp)
                                .clip(item.iconShape ?: RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            },
        headlineContent = {
            ProvideTextStyle(
                MaterialTheme.typography.bodyLarge.copy(
                    color = if (!item.enabled)
                        disabledColor
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            ) {
                item.title()
            }
        },
        supportingContent = item.description?.let { desc ->
            {
                ProvideTextStyle(
                    MaterialTheme.typography.bodyMedium.copy(
                        color = if (!item.enabled)
                            disabledColor
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    desc()
                }
            }
        },
        trailingContent = item.trailingContent?.let { trailing ->
            {
                ProvideTextStyle(
                    MaterialTheme.typography.bodyLarge.copy(
                        color = if (!item.enabled)
                            disabledColor
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    trailing()
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
}


data class Material3SettingsItem(
    val icon: Painter? = null,
    val customIcon: (@Composable () -> Unit)? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val showBadge: Boolean = false,
    val isHighlighted: Boolean = false,
    val tintIcon: Boolean = true,
    val iconShape: Shape? = null,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null
)
