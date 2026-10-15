package com.epsilonmusic.app.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.epsilonmusic.app.R
import com.epsilonmusic.app.constants.CustomFontPathKey
import com.epsilonmusic.app.constants.FontChoice
import com.epsilonmusic.app.constants.SelectedFontKey
import com.epsilonmusic.app.ui.component.IconButton
import com.epsilonmusic.app.ui.component.Material3SettingsGroup
import com.epsilonmusic.app.ui.component.Material3SettingsItem
import com.epsilonmusic.app.ui.theme.GoogleSansFontFamily
import com.epsilonmusic.app.ui.theme.OutfitFontFamily
import com.epsilonmusic.app.ui.theme.PlusJakartaSansFontFamily
import com.epsilonmusic.app.ui.theme.SansFlexFontFamily
import com.epsilonmusic.app.ui.theme.resolveFontFamily
import com.epsilonmusic.app.utils.rememberPreference
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontSelectionScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val (selectedFont, onSelectedFontChange) = rememberPreference(SelectedFontKey, FontChoice.SYSTEM.value)
    val (customFontPath, onCustomFontPathChange) = rememberPreference(CustomFontPathKey, "")

    val fontPickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            val file = File(context.filesDir, "custom_font.ttf")
                            context.contentResolver.openInputStream(it)?.use { input ->
                                FileOutputStream(file).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            onCustomFontPathChange(file.absolutePath)
                            onSelectedFontChange(FontChoice.CUSTOM.value)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

    fun launchFontPicker() {
        try {
            fontPickerLauncher.launch("*/*")
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.font_picker_unavailable),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val activeFontFamily = remember(selectedFont, customFontPath) {
        resolveFontFamily(selectedFont, customFontPath)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_font), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.navigateUp() },
                        onLongClick = { navController.popBackStack() }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null
                        )
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

            // Typography preview card
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.typography_preview).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.preview_text_quote),
                        fontFamily = activeFontFamily,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = stringResource(R.string.font_preview_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Material3SettingsGroup(
                title = stringResource(R.string.font_selection),
                items = listOf(
                    Material3SettingsItem(
                        customIcon = {
                            RadioButton(
                                selected = selectedFont == FontChoice.SYSTEM.value,
                                onClick = { onSelectedFontChange(FontChoice.SYSTEM.value) }
                            )
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.font_system),
                                fontFamily = FontFamily.Default
                            )
                        },
                        description = {
                            Text(
                                text = stringResource(R.string.font_system_desc),
                                fontFamily = FontFamily.Default
                            )
                        },
                        onClick = { onSelectedFontChange(FontChoice.SYSTEM.value) }
                    ),
                    Material3SettingsItem(
                        customIcon = {
                            RadioButton(
                                selected = selectedFont == FontChoice.GOOGLE_SANS.value,
                                onClick = { onSelectedFontChange(FontChoice.GOOGLE_SANS.value) }
                            )
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.font_google_sans),
                                fontFamily = GoogleSansFontFamily
                            )
                        },
                        description = {
                            Text(
                                text = stringResource(R.string.font_google_sans_desc),
                                fontFamily = GoogleSansFontFamily
                            )
                        },
                        onClick = { onSelectedFontChange(FontChoice.GOOGLE_SANS.value) }
                    ),
                    Material3SettingsItem(
                        customIcon = {
                            RadioButton(
                                selected = selectedFont == FontChoice.SANS_FLEX.value,
                                onClick = { onSelectedFontChange(FontChoice.SANS_FLEX.value) }
                            )
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.font_sans_flex),
                                fontFamily = SansFlexFontFamily
                            )
                        },
                        description = {
                            Text(
                                text = stringResource(R.string.font_sans_flex_desc),
                                fontFamily = SansFlexFontFamily
                            )
                        },
                        onClick = { onSelectedFontChange(FontChoice.SANS_FLEX.value) }
                    ),
                    Material3SettingsItem(
                        customIcon = {
                            RadioButton(
                                selected = selectedFont == FontChoice.OUTFIT.value,
                                onClick = { onSelectedFontChange(FontChoice.OUTFIT.value) }
                            )
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.font_outfit),
                                fontFamily = OutfitFontFamily
                            )
                        },
                        description = {
                            Text(
                                text = stringResource(R.string.font_outfit_desc),
                                fontFamily = OutfitFontFamily
                            )
                        },
                        onClick = { onSelectedFontChange(FontChoice.OUTFIT.value) }
                    ),
                    Material3SettingsItem(
                        customIcon = {
                            RadioButton(
                                selected = selectedFont == FontChoice.PLUS_JAKARTA_SANS.value,
                                onClick = { onSelectedFontChange(FontChoice.PLUS_JAKARTA_SANS.value) }
                            )
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.font_plus_jakarta_sans),
                                fontFamily = PlusJakartaSansFontFamily
                            )
                        },
                        description = {
                            Text(
                                text = stringResource(R.string.font_plus_jakarta_sans_desc),
                                fontFamily = PlusJakartaSansFontFamily
                            )
                        },
                        onClick = { onSelectedFontChange(FontChoice.PLUS_JAKARTA_SANS.value) }
                    ),
                    Material3SettingsItem(
                        customIcon = {
                            RadioButton(
                                selected = selectedFont == FontChoice.CUSTOM.value,
                                onClick = null
                            )
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.font_custom),
                                fontFamily = FontFamily.Default
                            )
                        },
                        description = {
                            Text(
                                text = stringResource(R.string.font_custom_desc),
                                fontFamily = FontFamily.Default
                            )
                        },
                        trailingContent = {
                            TextButton(onClick = { launchFontPicker() }) {
                                Text(stringResource(R.string.import_custom_font))
                            }
                        },
                        onClick = {
                            if (selectedFont != FontChoice.CUSTOM.value) {
                                if (customFontPath.isNotEmpty() && File(customFontPath).exists()) {
                                    onSelectedFontChange(FontChoice.CUSTOM.value)
                                } else {
                                    launchFontPicker()
                                }
                            } else {
                                launchFontPicker()
                            }
                        }
                    )
                )
            )

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}
