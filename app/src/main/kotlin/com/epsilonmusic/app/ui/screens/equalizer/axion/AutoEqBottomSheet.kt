package com.epsilonmusic.app.ui.screens.equalizer.axion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.epsilonmusic.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL
import java.net.URLEncoder

/**
 * A headphone model entry from the bundled AutoEq index.
 */
@Serializable
data class AutoEqItem(val name: String, val path: String)

/**
 * Bottom sheet for browsing AutoEq headphone profiles. Profiles are downloaded
 * from the AutoEq project's public results and mapped onto this app's 10
 * equalizer bands when applied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoEqBottomSheet(
    onDismissRequest: () -> Unit,
    onApplyEq: (FloatArray) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<AutoEqItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var applyLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val jsonStr =
                    context.resources
                        .openRawResource(R.raw.autoeq_index)
                        .bufferedReader()
                        .use { it.readText() }
                items = Json { ignoreUnknownKeys = true }.decodeFromString(jsonStr)
            } catch (e: Exception) {
                android.util.Log.e("AutoEq", "Failed to load AutoEq index", e)
            } finally {
                isLoading = false
            }
        }
    }

    val filteredItems =
        remember(query, items) {
            if (query.isBlank()) items.take(100)
            else items.filter { it.name.contains(query, ignoreCase = true) }.take(100)
        }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.autoeq_profiles),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.autoeq_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            when {
                applyLoading || isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(filteredItems) { item ->
                            ListItem(
                                headlineContent = { Text(item.name) },
                                supportingContent = { Text(item.path.split("/").firstOrNull().orEmpty()) },
                                modifier =
                                    Modifier.clickable {
                                        applyLoading = true
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val nameEncoded =
                                                    URLEncoder.encode(item.name, "UTF-8").replace("+", "%20")
                                                val url =
                                                    "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/${item.path}/${nameEncoded}%20GraphicEQ.txt"
                                                val response = URL(url).readText()
                                                // Parse graphic EQ string: "GraphicEQ: 20 -0.3; 21 -0.2; ..."
                                                val parsedBands =
                                                    response
                                                        .removePrefix("GraphicEQ:")
                                                        .split(";")
                                                        .mapNotNull { entry ->
                                                            val parts =
                                                                entry.trim().split(" ").filter { it.isNotBlank() }
                                                            if (parts.size >= 2) {
                                                                val freq = parts[0].toFloatOrNull()
                                                                val gain = parts[1].toFloatOrNull()
                                                                if (freq != null && gain != null) freq to gain
                                                                else null
                                                            } else {
                                                                null
                                                            }
                                                        }

                                                // Map onto this app's 10 bands by nearest frequency.
                                                val targetFrequencies =
                                                    listOf(
                                                        31f,
                                                        62f,
                                                        125f,
                                                        250f,
                                                        500f,
                                                        1000f,
                                                        2000f,
                                                        4000f,
                                                        8000f,
                                                        16000f,
                                                    )
                                                val resultGains = FloatArray(targetFrequencies.size)
                                                for (i in targetFrequencies.indices) {
                                                    val targetFreq = targetFrequencies[i]
                                                    val closest =
                                                        parsedBands.minByOrNull {
                                                            kotlin.math.abs(it.first - targetFreq)
                                                        }
                                                    resultGains[i] = closest?.second ?: 0f
                                                }
                                                withContext(Dispatchers.Main) {
                                                    onApplyEq(resultGains)
                                                    onDismissRequest()
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("AutoEq", "Failed to apply AutoEq profile", e)
                                                withContext(Dispatchers.Main) { applyLoading = false }
                                            }
                                        }
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}
