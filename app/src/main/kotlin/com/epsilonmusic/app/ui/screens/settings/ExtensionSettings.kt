package com.epsilonmusic.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil3.compose.SubcomposeAsyncImage
import com.epsilonmusic.app.LocalPlayerAwareWindowInsets
import com.epsilonmusic.app.R
import com.epsilonmusic.app.constants.PreferExtensionAudioKey
import com.epsilonmusic.app.extensions.AddonEndpoint
import com.epsilonmusic.app.extensions.ExtensionManager
import com.epsilonmusic.app.ui.component.IconButton
import com.epsilonmusic.app.ui.component.Material3SettingsGroup
import com.epsilonmusic.app.ui.component.Material3SettingsItem
import com.epsilonmusic.app.ui.component.TextFieldDialog
import com.epsilonmusic.app.ui.utils.backToMain
import com.epsilonmusic.app.utils.rememberPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ExtensionSettingsViewModel @Inject constructor(
    private val extensionManager: ExtensionManager
) : ViewModel() {
    private val _addons = MutableStateFlow<List<AddonEndpoint>>(emptyList())
    val addons: StateFlow<List<AddonEndpoint>> = _addons

    init {
        loadAddons()
    }

    private fun loadAddons() {
        viewModelScope.launch {
            _addons.value = extensionManager.getAddons()
        }
    }

    fun addAddon(name: String, url: String) {
        viewModelScope.launch {
            extensionManager.addAddon(AddonEndpoint(UUID.randomUUID().toString(), name, url))
            loadAddons()
        }
    }

    fun toggleAddon(id: String, enabled: Boolean) {
        viewModelScope.launch {
            extensionManager.toggleAddon(id, enabled)
            loadAddons()
        }
    }

    fun removeAddon(id: String) {
        viewModelScope.launch {
            extensionManager.removeAddon(id)
            loadAddons()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionSettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ExtensionSettingsViewModel = hiltViewModel()
) {
    val addons by viewModel.addons.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    val (preferExtensionAudio, onPreferExtensionAudioChange) =
        rememberPreference(key = PreferExtensionAudioKey, defaultValue = true)
    val scrollState = rememberScrollState()

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
        Spacer(Modifier.height(16.dp))

        Material3SettingsGroup(
            title = "Audio Source Priority",
            items = listOf(
                Material3SettingsItem(
                    title = { Text("Prefer add-on audio") },
                    description = { Text("Check add-on servers first for lossless or high-quality audio, falling back to YouTube Music when they cannot provide a track") },
                    icon = painterResource(R.drawable.ic_apple_lossless),
                    trailingContent = {
                        Switch(
                            checked = preferExtensionAudio,
                            onCheckedChange = onPreferExtensionAudioChange,
                            modifier = Modifier.scale(0.8f)
                        )
                    },
                    onClick = { onPreferExtensionAudioChange(!preferExtensionAudio) }
                )
            )
        )

        Spacer(Modifier.height(16.dp))

        if (addons.isNotEmpty()) {
            Material3SettingsGroup(
                title = "Saved Add-ons",
                items = addons.map { addon ->
                    Material3SettingsItem(
                        title = { Text(addon.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        description = { Text(addon.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        customIcon = {
                            SubcomposeAsyncImage(
                                model = "${addon.url.removeSuffix("/")}/favicon.ico",
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                loading = {
                                    Icon(
                                        painter = painterResource(R.drawable.globe),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                error = {
                                    Icon(
                                        painter = painterResource(R.drawable.globe),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        },
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Switch(
                                    checked = addon.enabled,
                                    onCheckedChange = { viewModel.toggleAddon(addon.id, it) },
                                    modifier = Modifier.scale(0.8f)
                                )
                                androidx.compose.material3.IconButton(
                                    onClick = { viewModel.removeAddon(addon.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        painterResource(R.drawable.delete),
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        onClick = {}
                    )
                }
            )

            Spacer(Modifier.height(16.dp))
        }

        Material3SettingsGroup(
            title = "Add New",
            items = listOf(
                Material3SettingsItem(
                    title = { Text("Add custom server") },
                    description = { Text("Point the app at your own add-on server") },
                    icon = painterResource(R.drawable.add),
                    onClick = { showDialog = true }
                )
            )
        )

        Spacer(Modifier.height(32.dp))

        if (showDialog) {
            var name by remember { mutableStateOf(TextFieldValue("")) }
            var url by remember { mutableStateOf(TextFieldValue("")) }

            TextFieldDialog(
                title = { Text("Add Add-on") },
                textFields = listOf("Name" to name, "Server URL" to url),
                onTextFieldsChange = { index, value ->
                    if (index == 0) name = value
                    if (index == 1) url = value
                },
                onDismiss = { showDialog = false },
                onDoneMultiple = { inputs ->
                    val finalName = inputs.getOrNull(0)?.trim() ?: ""
                    val finalUrl = inputs.getOrNull(1)?.trim() ?: ""
                    if (finalUrl.isNotBlank()) {
                        viewModel.addAddon(finalName, finalUrl)
                        showDialog = false
                    }
                }
            )
        }

        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)))
    }

    TopAppBar(
        title = { Text("Add-on Servers") },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}
