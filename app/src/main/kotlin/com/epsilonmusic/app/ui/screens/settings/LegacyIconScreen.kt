@file:OptIn(ExperimentalMaterial3Api::class)

package com.epsilonmusic.app.ui.screens.settings

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.epsilonmusic.app.R
import com.epsilonmusic.app.constants.EnableLegacyIconKey
import com.epsilonmusic.app.constants.LegacyIconVariantKey
import com.epsilonmusic.app.utils.IconUtils
import com.epsilonmusic.app.utils.rememberPreference
import kotlinx.coroutines.launch

private data class LegacyIconOption(
    val variant: Int,
    val titleRes: Int,
    val descRes: Int,
    val previewRes: Int,
)

/**
 * Picker page for the launcher icon: the current default icon plus the
 * available legacy icon designs. Selecting an option switches the enabled
 * launcher activity-alias immediately and offers an app restart so the new
 * icon shows up right away.
 */
@Composable
fun LegacyIconScreen(
    navController: NavController,
    activity: Activity,
    snackbarHostState: SnackbarHostState,
) {
    val coroutineScope = rememberCoroutineScope()

    val (enableLegacyIcon, onEnableLegacyIconChange) = rememberPreference(
        EnableLegacyIconKey,
        defaultValue = false
    )
    val (legacyIconVariant, onLegacyIconVariantChange) = rememberPreference(
        LegacyIconVariantKey,
        defaultValue = IconUtils.LEGACY_CLASSIC
    )

    val selectedVariant = if (enableLegacyIcon) legacyIconVariant else IconUtils.LEGACY_OFF

    val options = remember {
        listOf(
            LegacyIconOption(
                variant = IconUtils.LEGACY_OFF,
                titleRes = R.string.legacy_icon_default,
                descRes = R.string.legacy_icon_default_desc,
                previewRes = R.mipmap.ic_launcher_foreground
            ),
            LegacyIconOption(
                variant = IconUtils.LEGACY_CLASSIC,
                titleRes = R.string.legacy_icon_classic,
                descRes = R.string.legacy_icon_classic_desc,
                previewRes = R.mipmap.legacy_icon_foreground
            ),
            LegacyIconOption(
                variant = IconUtils.LEGACY_VARIANT_1,
                titleRes = R.string.legacy_icon_1,
                descRes = R.string.legacy_icon_1_desc,
                previewRes = R.mipmap.legacy_icon_1_foreground
            ),
            LegacyIconOption(
                variant = IconUtils.LEGACY_VARIANT_2,
                titleRes = R.string.legacy_icon_2,
                descRes = R.string.legacy_icon_2_desc,
                previewRes = R.mipmap.legacy_icon_2_background
            ),
            LegacyIconOption(
                variant = IconUtils.LEGACY_VARIANT_3,
                titleRes = R.string.legacy_icon_3,
                descRes = R.string.legacy_icon_3_desc,
                previewRes = R.mipmap.legacy_icon_3_foreground
            ),
        )
    }

    fun selectIcon(variant: Int) {
        if (variant == IconUtils.LEGACY_OFF) {
            onEnableLegacyIconChange(false)
        } else {
            onEnableLegacyIconChange(true)
            onLegacyIconVariantChange(variant)
        }
        try {
            IconUtils.setIcon(activity, true, variant)
        } catch (_: Exception) {
            // Component manipulation is best-effort; the preference is still stored
        }
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Icon updated, restart to apply",
                actionLabel = "Restart"
            )
            if (result == SnackbarResult.ActionPerformed) {
                val packageManager = activity.packageManager
                val intent = packageManager.getLaunchIntentForPackage(activity.packageName)
                val componentName = intent?.component
                val mainIntent = Intent.makeRestartActivityTask(componentName)
                activity.startActivity(mainIntent)
                Runtime.getRuntime().exit(0)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.legacy_icon), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            options.forEach { option ->
                val isSelected = option.variant == selectedVariant
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .clickable { selectIcon(option.variant) }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(option.previewRes),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(option.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(option.descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RadioButton(
                        selected = isSelected,
                        onClick = { selectIcon(option.variant) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
