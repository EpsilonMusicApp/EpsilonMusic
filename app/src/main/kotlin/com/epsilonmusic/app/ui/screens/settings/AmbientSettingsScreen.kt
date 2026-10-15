package com.epsilonmusic.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.epsilonmusic.app.R
import com.epsilonmusic.app.constants.AmbientArtScaleKey
import com.epsilonmusic.app.constants.AmbientShowArtKey
import com.epsilonmusic.app.constants.AmbientShowArtistKey
import com.epsilonmusic.app.constants.AmbientShowLyricsKey
import com.epsilonmusic.app.constants.AmbientShowTitleKey
import com.epsilonmusic.app.constants.AmbientSpacingKey
import com.epsilonmusic.app.constants.LyricsAnimationStyle
import com.epsilonmusic.app.constants.LyricsAnimationStyleKey
import com.epsilonmusic.app.constants.LyricsLineSpacingKey
import com.epsilonmusic.app.constants.LyricsTextSizeKey
import com.epsilonmusic.app.ui.component.Material3SettingsGroup
import com.epsilonmusic.app.ui.component.Material3SettingsItem
import com.epsilonmusic.app.utils.rememberEnumPreference
import com.epsilonmusic.app.utils.rememberPreference

@Composable
fun AmbientSettingsContent(modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()

    var showArt by rememberPreference(AmbientShowArtKey, true)
    var artScale by rememberPreference(AmbientArtScaleKey, 0.85f)
    var showTitle by rememberPreference(AmbientShowTitleKey, false)
    var showArtist by rememberPreference(AmbientShowArtistKey, false)
    var showLyrics by rememberPreference(AmbientShowLyricsKey, true)
    var spacing by rememberPreference(AmbientSpacingKey, 32f)

    var lyricsTextSize by rememberPreference(LyricsTextSizeKey, 24f)
    var lyricsLineSpacing by rememberPreference(LyricsLineSpacingKey, 1.3f)
    var lyricsAnimationStyle by rememberEnumPreference(LyricsAnimationStyleKey, LyricsAnimationStyle.epsilonmusic_1)

    Column(modifier = modifier.verticalScroll(scrollState)) {
        Material3SettingsGroup(
            scrollState = scrollState,
            title = stringResource(R.string.ambient_display_options),
            items =
                listOf(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.ambient_show_art)) },
                        description = { Text(stringResource(R.string.ambient_show_art_desc)) },
                        icon = painterResource(R.drawable.image),
                        trailingContent = {
                            Switch(checked = showArt, onCheckedChange = { showArt = it })
                        },
                        onClick = { showArt = !showArt },
                    ),
                ) + (
                    if (showArt) {
                        listOf(
                            Material3SettingsItem(
                                title = { Text(stringResource(R.string.ambient_art_size)) },
                                description = { Text("${(artScale * 100).toInt()}%") },
                                icon = painterResource(R.drawable.image),
                                trailingContent = {
                                    Slider(
                                        value = artScale,
                                        onValueChange = { artScale = it },
                                        valueRange = 0.3f..1.0f,
                                        modifier = Modifier.width(120.dp),
                                    )
                                },
                                onClick = {},
                            ),
                        )
                    } else {
                        emptyList()
                    }
                    ) + listOf(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.ambient_spacing)) },
                        description = { Text("${spacing.toInt()} dp") },
                        icon = painterResource(R.drawable.image),
                        trailingContent = {
                            Slider(
                                value = spacing,
                                onValueChange = { spacing = it },
                                valueRange = 0f..100f,
                                modifier = Modifier.width(120.dp),
                            )
                        },
                        onClick = {},
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.ambient_show_title)) },
                        description = { Text(stringResource(R.string.ambient_show_title_desc)) },
                        icon = painterResource(R.drawable.music_note),
                        trailingContent = {
                            Switch(checked = showTitle, onCheckedChange = { showTitle = it })
                        },
                        onClick = { showTitle = !showTitle },
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.ambient_show_artist)) },
                        description = { Text(stringResource(R.string.ambient_show_artist_desc)) },
                        icon = painterResource(R.drawable.person),
                        trailingContent = {
                            Switch(checked = showArtist, onCheckedChange = { showArtist = it })
                        },
                        onClick = { showArtist = !showArtist },
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.ambient_show_lyrics)) },
                        description = { Text(stringResource(R.string.ambient_show_lyrics_desc)) },
                        icon = painterResource(R.drawable.lyrics),
                        trailingContent = {
                            Switch(checked = showLyrics, onCheckedChange = { showLyrics = it })
                        },
                        onClick = { showLyrics = !showLyrics },
                    ),
                ),
        )

        if (showLyrics) {
            Spacer(modifier = Modifier.height(16.dp))
            Material3SettingsGroup(
                scrollState = scrollState,
                title = stringResource(R.string.ambient_lyrics_customization),
                items =
                    listOf(
                        Material3SettingsItem(
                            title = { Text(stringResource(R.string.ambient_lyrics_font_size)) },
                            description = { Text("${lyricsTextSize.toInt()} sp") },
                            icon = painterResource(R.drawable.lyrics),
                            trailingContent = {
                                Slider(
                                    value = lyricsTextSize,
                                    onValueChange = { lyricsTextSize = it },
                                    valueRange = 12f..48f,
                                    modifier = Modifier.width(120.dp),
                                )
                            },
                            onClick = {},
                        ),
                        Material3SettingsItem(
                            title = { Text(stringResource(R.string.ambient_lyrics_line_spacing)) },
                            description = { Text(String.format("%.1f", lyricsLineSpacing)) },
                            icon = painterResource(R.drawable.lyrics),
                            trailingContent = {
                                Slider(
                                    value = lyricsLineSpacing,
                                    onValueChange = { lyricsLineSpacing = it },
                                    valueRange = 0.5f..2.5f,
                                    modifier = Modifier.width(120.dp),
                                )
                            },
                            onClick = {},
                        ),
                        Material3SettingsItem(
                            title = { Text(stringResource(R.string.ambient_lyrics_animation_style)) },
                            description = { Text(lyricsAnimationStyle.name) },
                            icon = painterResource(R.drawable.music_note),
                            trailingContent = {
                                Button(
                                    onClick = {
                                        val values = LyricsAnimationStyle.values()
                                        lyricsAnimationStyle =
                                            values[(lyricsAnimationStyle.ordinal + 1) % values.size]
                                    },
                                ) {
                                    Text(stringResource(R.string.ambient_lyrics_animation_next))
                                }
                            },
                            onClick = {
                                val values = LyricsAnimationStyle.values()
                                lyricsAnimationStyle =
                                    values[(lyricsAnimationStyle.ordinal + 1) % values.size]
                            },
                        ),
                    ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbientSettingsScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ambient_settings)) },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        AmbientSettingsContent(modifier = Modifier.fillMaxSize().padding(innerPadding))
    }
}
