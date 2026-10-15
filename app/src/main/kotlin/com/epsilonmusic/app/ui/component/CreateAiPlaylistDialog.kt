package com.epsilonmusic.app.ui.component

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.epsilonmusic.app.R
import com.epsilonmusic.app.ai.AiPlaylistGenerator
import com.epsilonmusic.app.ai.weather.IpLocationRepository
import com.epsilonmusic.app.ai.weather.LocationProvider
import com.epsilonmusic.app.ai.weather.WeatherInfo
import com.epsilonmusic.app.ai.weather.WeatherRepository
import com.epsilonmusic.app.ai.weather.WeatherUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CreateAiPlaylistDialog(
    onDismiss: () -> Unit,
    onPlaylistCreated: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var prompt by remember { mutableStateOf("") }
    var numSongs by remember { mutableFloatStateOf(15f) }
    var isGenerating by remember { mutableStateOf(false) }
    var generationLog by remember { mutableStateOf("Initializing...") }
    var errorLog by remember { mutableStateOf<String?>(null) }

    var weatherEnabled by remember { mutableStateOf(false) }
    var weatherUiState by remember { mutableStateOf<WeatherUiState>(WeatherUiState.Idle) }
    var fetchWeatherJob by remember { mutableStateOf<Job?>(null) }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun fetchWeather(withPermission: Boolean) {
        fetchWeatherJob?.cancel()
        weatherUiState = WeatherUiState.Loading
        fetchWeatherJob = coroutineScope.launch {
            var lat: Double? = null
            var lon: Double? = null

            if (withPermission) {
                val location = LocationProvider.getLocation(context)
                if (location != null) {
                    lat = location.latitude
                    lon = location.longitude
                }
            }

            // Fall back to IP-based location when GPS is unavailable or denied
            if (lat == null || lon == null) {
                IpLocationRepository.fetchIpLocation().onSuccess { ipLoc ->
                    lat = ipLoc.latitude
                    lon = ipLoc.longitude
                }
            }

            val currentLat = lat
            val currentLon = lon
            if (currentLat != null && currentLon != null) {
                WeatherRepository.fetchWeather(currentLat, currentLon).fold(
                    onSuccess = { weatherInfo ->
                        weatherUiState = WeatherUiState.Success(weatherInfo)
                    },
                    onFailure = { error ->
                        weatherUiState = WeatherUiState.Error(
                            message = error.localizedMessage
                                ?: "Failed to fetch weather data. Check your connection.",
                            isPermissionDenied = !withPermission
                        )
                    }
                )
            } else {
                weatherUiState = WeatherUiState.Error(
                    message = "Could not determine location from GPS or IP lookup.",
                    isPermissionDenied = !withPermission
                )
            }
        }
    }

    fun setWeatherEnabled(enabled: Boolean) {
        weatherEnabled = enabled
        if (enabled) {
            fetchWeather(hasLocationPermission())
        } else {
            fetchWeatherJob?.cancel()
            fetchWeatherJob = null
            weatherUiState = WeatherUiState.Idle
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val isGranted = fineGranted || coarseGranted

        val activity = context as? Activity
        val isPermanentlyDenied = !isGranted && activity?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(
                it, Manifest.permission.ACCESS_FINE_LOCATION
            ) && !ActivityCompat.shouldShowRequestPermissionRationale(
                it, Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } == true

        if (isPermanentlyDenied) {
            weatherEnabled = true
            weatherUiState = WeatherUiState.Error(
                message = "Location permission is required for precise weather.",
                isPermissionDenied = true,
                isPermanentlyDenied = true
            )
        } else {
            // Permission granted, or denied without "don't ask again":
            // enable weather (GPS when granted, IP fallback otherwise).
            setWeatherEnabled(true)
        }
    }

    fun requestWeatherEnable() {
        if (hasLocationPermission()) {
            setWeatherEnabled(true)
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isGenerating) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Create with AI",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!isGenerating && errorLog == null) {
                    // Weather toggle row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable {
                                if (!weatherEnabled) requestWeatherEnable()
                                else setWeatherEnabled(false)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use current weather",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Shape the playlist around local weather",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = weatherEnabled,
                            onCheckedChange = { checked ->
                                if (checked) requestWeatherEnable()
                                else setWeatherEnabled(false)
                            }
                        )
                    }

                    // Weather preview card
                    if (weatherEnabled) {
                        when (val state = weatherUiState) {
                            is WeatherUiState.Loading -> {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Fetching local weather…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            is WeatherUiState.Success -> {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = state.data.weatherEmoji,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text(
                                            text = state.data.summaryBadgeText,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            is WeatherUiState.Error -> {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        if (state.isPermissionDenied) {
                                            Button(
                                                onClick = {
                                                    if (state.isPermanentlyDenied) {
                                                        val intent = Intent(
                                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                                        ).apply {
                                                            data = Uri.fromParts(
                                                                "package",
                                                                context.packageName,
                                                                null
                                                            )
                                                        }
                                                        context.startActivity(intent)
                                                    } else {
                                                        locationPermissionLauncher.launch(
                                                            arrayOf(
                                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                                            )
                                                        )
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Text(
                                                    if (state.isPermanentlyDenied) "Open Settings"
                                                    else "Enable Location"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            WeatherUiState.Idle -> {}
                        }
                    }

                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = {
                            Text(
                                if (weatherEnabled) "Extra vibes or artists (optional)"
                                else "What kind of playlist do you want?"
                            )
                        },
                        placeholder = {
                            Text(
                                if (weatherEnabled) "Optional — leave blank to let the weather decide"
                                else "e.g. upbeat workout pop songs"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Number of songs: ${numSongs.toInt()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = numSongs,
                            onValueChange = { numSongs = it },
                            valueRange = 5f..50f,
                            steps = 44
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        val weatherActive =
                            weatherEnabled && weatherUiState is WeatherUiState.Success
                        Button(
                            onClick = {
                                val activeWeather: WeatherInfo? =
                                    (weatherUiState as? WeatherUiState.Success)?.data
                                        ?.takeIf { weatherEnabled }
                                if (prompt.isNotBlank() || activeWeather != null) {
                                    isGenerating = true
                                    errorLog = null
                                    coroutineScope.launch {
                                        val newPlaylistId = AiPlaylistGenerator.generatePlaylist(
                                            context = context,
                                            userPrompt = prompt,
                                            numberOfSongs = numSongs.toInt(),
                                            weatherInfo = activeWeather,
                                            onLog = { log ->
                                                withContext(Dispatchers.Main) {
                                                    generationLog = log
                                                }
                                            }
                                        )
                                        if (newPlaylistId != null) {
                                            onPlaylistCreated(newPlaylistId)
                                        } else {
                                            isGenerating = false
                                            errorLog = "Failed to generate playlist. Check logs or settings."
                                        }
                                    }
                                }
                            },
                            enabled = prompt.isNotBlank() || weatherActive
                        ) {
                            Text("Generate")
                        }
                    }
                } else if (isGenerating) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        Text(
                            text = generationLog,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (errorLog != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = errorLog!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Last Log: $generationLog",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = { errorLog = null }) {
                            Text(stringResource(R.string.try_again))
                        }
                    }
                }
            }
        }
    }
}
