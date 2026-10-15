@file:OptIn(ExperimentalMaterial3Api::class)

package com.epsilonmusic.app.ui.screens.generate

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.epsilonmusic.app.LocalPlayerAwareWindowInsets
import com.epsilonmusic.app.R
import com.epsilonmusic.app.ai.AiPlaylistGenerator
import com.epsilonmusic.app.ai.weather.IpLocationRepository
import com.epsilonmusic.app.ai.weather.LocationProvider
import com.epsilonmusic.app.ai.weather.WeatherInfo
import com.epsilonmusic.app.ai.weather.WeatherRepository
import com.epsilonmusic.app.ai.weather.WeatherUiState
import com.epsilonmusic.app.constants.AiProviderKey
import com.epsilonmusic.app.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface GenerateUiState {
    data object Idle : GenerateUiState
    data class Generating(val log: String) : GenerateUiState
    data class Success(val playlistId: String) : GenerateUiState
    data class Error(val message: String) : GenerateUiState
}

@Composable
fun GenerateScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val aiProvider by rememberPreference(AiProviderKey, "OpenRouter")

    var prompt by remember { mutableStateOf("") }
    var numSongs by remember { mutableFloatStateOf(15f) }
    var uiState by remember { mutableStateOf<GenerateUiState>(GenerateUiState.Idle) }
    var generationJob by remember { mutableStateOf<Job?>(null) }

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

    fun activeWeatherInfo(): WeatherInfo? =
        if (weatherEnabled && weatherUiState is WeatherUiState.Success) {
            (weatherUiState as WeatherUiState.Success).data
        } else null

    fun startGeneration() {
        val canGenerate = prompt.isNotBlank() || activeWeatherInfo() != null
        if (!canGenerate || uiState is GenerateUiState.Generating) return
        generationJob?.cancel()
        generationJob = coroutineScope.launch {
            uiState = GenerateUiState.Generating("Initializing...")
            val playlistId = AiPlaylistGenerator.generatePlaylist(
                context = context,
                userPrompt = prompt,
                numberOfSongs = numSongs.toInt(),
                weatherInfo = activeWeatherInfo(),
                onLog = { log ->
                    withContext(Dispatchers.Main) {
                        uiState = GenerateUiState.Generating(log)
                    }
                },
            )
            uiState = if (playlistId != null) {
                GenerateUiState.Success(playlistId)
            } else {
                GenerateUiState.Error("")
            }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        uiState = GenerateUiState.Idle
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.generate_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        contentWindowInsets = LocalPlayerAwareWindowInsets.current,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(20.dp),
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.generate_header_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.generate_header_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Info grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(16.dp),
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.generate_provider_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = aiProvider,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(16.dp),
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.generate_length_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.generate_song_count_value, numSongs.toInt()),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (val state = uiState) {
                is GenerateUiState.Idle -> {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text(stringResource(R.string.generate_prompt_label)) },
                        placeholder = { Text(stringResource(R.string.generate_prompt_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Weather toggle row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
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
                        Spacer(modifier = Modifier.height(8.dp))
                        when (val weatherState = weatherUiState) {
                            is WeatherUiState.Loading -> {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
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
                                            text = weatherState.data.weatherEmoji,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text(
                                            text = weatherState.data.summaryBadgeText,
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
                                            text = weatherState.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        if (weatherState.isPermissionDenied) {
                                            Button(
                                                onClick = {
                                                    if (weatherState.isPermanentlyDenied) {
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
                                                    if (weatherState.isPermanentlyDenied) "Open Settings"
                                                    else "Enable Location"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.generate_song_count, numSongs.toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = numSongs,
                            onValueChange = { numSongs = it },
                            valueRange = 5f..50f,
                            steps = 44,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                            .padding(20.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = stringResource(R.string.generate_how_it_works),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.generate_how_1),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.generate_how_2),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.generate_how_3),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { startGeneration() },
                        enabled = prompt.isNotBlank() || activeWeatherInfo() != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.generate_button),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }
                }

                is GenerateUiState.Generating -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = state.log,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            OutlinedButton(
                                onClick = { cancelGeneration() },
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }
                }

                is GenerateUiState.Success -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.generate_success_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.generate_success_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    navController.navigate("local_playlist/${state.playlistId}")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(24.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.generate_open_playlist),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    uiState = GenerateUiState.Idle
                                    startGeneration()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(24.dp),
                            ) {
                                Text(stringResource(R.string.generate_another))
                            }
                        }
                    }
                }

                is GenerateUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.generate_error_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.message.ifBlank { stringResource(R.string.generate_error_default) },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                TextButton(onClick = { uiState = GenerateUiState.Idle }) {
                                    Text(stringResource(R.string.cancel))
                                }
                                Button(
                                    onClick = { startGeneration() },
                                    shape = RoundedCornerShape(20.dp),
                                ) {
                                    Text(stringResource(R.string.try_again))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
