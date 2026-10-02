package com.epsilonmusic.app.ui.screens.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.epsilonmusic.app.constants.BlockedArtistsKey
import com.epsilonmusic.app.utils.dataStore
import com.epsilonmusic.app.utils.rememberPreference
import kotlinx.coroutines.launch

/**
 * Management screen for the blocked-artists set. Entries are stored as
 * `artistId||artistName`; artists can be blocked from their own page and are
 * filtered out of search, home, suggestions and related content everywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedArtistsScreen(navController: NavController) {
    val context = LocalContext.current
    val blockedArtists by rememberPreference(key = BlockedArtistsKey, defaultValue = emptySet<String>())
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocked Artists") },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (blockedArtists.isEmpty()) {
            Text(
                text = "No blocked artists.\nBlock an artist from their page to hide them from search, home and recommendations.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(blockedArtists.toList()) { index, entry ->
                    val id = entry.substringBefore("||")
                    val name = if (entry.contains("||")) entry.substringAfter("||") else "Unknown Artist"

                    val shape = when {
                        blockedArtists.size == 1 -> RoundedCornerShape(24.dp)
                        index == 0 -> RoundedCornerShape(
                            topStart = 24.dp,
                            topEnd = 24.dp,
                            bottomStart = 4.dp,
                            bottomEnd = 4.dp
                        )
                        index == blockedArtists.size - 1 -> RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 4.dp,
                            bottomStart = 24.dp,
                            bottomEnd = 24.dp
                        )
                        else -> RoundedCornerShape(4.dp)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = shape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                Icon(
                                    Icons.Default.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            headlineContent = {
                                Text(name, style = MaterialTheme.typography.titleMedium)
                            },
                            supportingContent = {
                                Text(
                                    "ID: $id",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                            },
                            trailingContent = {
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            context.dataStore.edit { prefs ->
                                                val current = prefs[BlockedArtistsKey] ?: emptySet()
                                                prefs[BlockedArtistsKey] = current - entry
                                            }
                                        }
                                    }
                                ) {
                                    Text("Unblock")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
