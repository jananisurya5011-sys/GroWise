package com.simats.growise.farmer

import android.Manifest
import android.location.Geocoder
import android.location.LocationManager // FIX: Added for hardware checks
import android.content.Context // FIX: Added for accessing system services
import android.widget.Toast // FIX: Added for the timeout alert
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.simats.growise.R
import com.simats.growise.data.model.DiagnosticRecord
import com.simats.growise.data.network.WeatherRetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

import java.util.Locale


@Composable
fun FHomeScreen(
    navController: androidx.navigation.NavController,
    onWeatherClick: () -> Unit = {},
    onSmartCultivationClick: () -> Unit = {},
    onDiagnoseCropClick: () -> Unit = {},
    onHarvestManagerClick: () -> Unit = {},
    onRentEquipmentClick: () -> Unit = {},
    onDiagnosticHistoryClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showSettingsDialog by remember { mutableStateOf(false) }

    val sharedPref = remember { context.getSharedPreferences("GroWiseSession", android.content.Context.MODE_PRIVATE) }
    val userEmail = remember { sharedPref.getString("USER_EMAIL", "farmer@growise.com") ?: "farmer@growise.com" }

    var farmerName by remember { mutableStateOf(sharedPref.getString("FARMER_NAME", "Farmer") ?: "Farmer") }
    var greetingLine by remember { mutableStateOf("Hello") }

    // Instant read from SharedPreferences ensures state survives tab switching
    var locationHasSuccessfullySynced by remember { mutableStateOf(sharedPref.getBoolean("LOC_SYNCED", false)) }
    var cityName by remember { mutableStateOf(sharedPref.getString("CITY_NAME", "Sync Location Needed") ?: "Sync Location Needed") }
    var currentTemperature by remember { mutableStateOf(sharedPref.getString("TEMP", "--°C") ?: "--°C") }
    var weatherCondition by remember { mutableStateOf(sharedPref.getString("WEATHER_COND", "TAP PIN TO SYNC") ?: "TAP PIN TO SYNC") }

    var weatherIcon by remember { mutableStateOf(Icons.Filled.Cloud) }
    var isLocationSearching by remember { mutableStateOf(false) }

    // FIX: States to hold dynamic reminder count and loading animation trigger
    var pendingTasksCount by remember { mutableStateOf(0) }
    var isCheckingSchedule by remember { mutableStateOf(true) }

    // State for Diagnostic History Ledger Card array binding
    // State for Diagnostic History Ledger Card array binding
    var recentDiagnostics by remember { mutableStateOf<List<DiagnosticRecord>>(emptyList()) }
    var isCheckingHistory by remember { mutableStateOf(true) } // Added state for ledger loading animation

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    LaunchedEffect(Unit) {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        greetingLine = when (currentHour) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..20 -> "Good Evening"
            else -> "Good Night"
        }

        try {
            val response = com.simats.growise.data.network.RetrofitClient.apiService.retrieveProfileFields(userEmail)
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                if (!data.name.isNullOrEmpty()) {
                    farmerName = data.name
                    sharedPref.edit().putString("FARMER_NAME", data.name).apply()
                }
            }
        } catch (e: Exception) {}

        // FIX: Background fetch to calculate pending tasks using strict Calendar Date logic
        try {
            isCheckingSchedule = true
            val cropResponse = com.simats.growise.data.network.RetrofitClient.apiService.fetchActiveCrops(com.simats.growise.data.model.EmailRequest(email = userEmail))
            if (cropResponse.success && cropResponse.active_crops != null) {
                var count = 0
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                val now = java.util.Date()

                for (crop in cropResponse.active_crops) {
                    val daysPassed = try {
                        val startStr = crop.startDate.substringBefore(".")
                        val start = if (startStr.isNotEmpty()) sdf.parse(startStr) else now
                        if (start != null) ((now.time - start.time) / (1000 * 60 * 60 * 24)).toInt() else 0
                    } catch (e: Exception) { 0 }

                    // Only count as pending if the actual calendar day has arrived
                    val hasPending = crop.roadmap.any { it.day == crop.currentDay && it.day <= (daysPassed + 1) }
                    if (hasPending) count++
                }
                pendingTasksCount = count
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isCheckingSchedule = false
        }

        // Fetch saved results from backend history ledger endpoint
        try {
            isCheckingHistory = true
            val historyResponse = com.simats.growise.data.network.RetrofitClient.apiService.fetchDiagnosisHistory(com.simats.growise.data.model.EmailRequest(email = userEmail))
            if (historyResponse.success && historyResponse.history != null) {
                // Filter out locally deleted items so the home screen count is perfectly accurate
                val deletedKeys = sharedPref.getStringSet("DELETED_LOGS", setOf()) ?: setOf()
                recentDiagnostics = historyResponse.history.filter { !deletedKeys.contains("${it.date}_${it.disease}") }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isCheckingHistory = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {

            // FIX: Explicitly verify if the hardware GPS provider is operational before beginning the sweep
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

            if (!isGpsEnabled) {
                Toast.makeText(context, "Please turn on your GPS location switch.", Toast.LENGTH_LONG).show()
                currentTemperature = "0°C"
                weatherCondition = "GPS IS OFF"
                cityName = "Location Disabled"
                locationHasSuccessfullySynced = true // Force show error states inside card fields

                sharedPref.edit().apply {
                    putBoolean("LOC_SYNCED", true)
                    putString("CITY_NAME", cityName)
                    putString("TEMP", currentTemperature)
                    putString("WEATHER_COND", weatherCondition)
                }.apply()
                return@rememberLauncherForActivityResult
            }

            isLocationSearching = true
            try {
                // High Precision current location instead of stale lastLocation
                val cancellationTokenSource = CancellationTokenSource()

                // FIX: 8-Second GPS Timeout Upgrade
                coroutineScope.launch {
                    delay(8000)
                    if (isLocationSearching) {
                        cancellationTokenSource.cancel()
                        isLocationSearching = false
                        Toast.makeText(context, "Weak GPS Signal. Try again outside.", Toast.LENGTH_LONG).show()
                    }
                }

                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                    .addOnSuccessListener { location ->
                        coroutineScope.launch {
                            delay(1000)
                            if (location != null) {
                                val geocoder = Geocoder(context, Locale.getDefault())
                                try {
                                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                                    if (!addresses.isNullOrEmpty()) {
                                        cityName = "${addresses[0].locality}, ${addresses[0].adminArea}"
                                    }
                                } catch (e: Exception) {
                                    cityName = "Location Sync Error"
                                }

                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val response = WeatherRetrofitClient.apiService.getCurrentWeather(location.latitude, location.longitude)

                                        try {
                                            val telemetryPayload = com.simats.growise.data.model.TelemetryRequest(
                                                email = userEmail,
                                                lat = location.latitude.toString(),
                                                lon = location.longitude.toString()
                                            )
                                            com.simats.growise.data.network.RetrofitClient.apiService.saveLocationTelemetry(telemetryPayload)
                                        } catch (e: Exception) {}

                                        withContext(Dispatchers.Main) {
                                            response.current_weather?.let { weather ->
                                                currentTemperature = "${weather.temperature.toInt()}°C"
                                                val parsed = when (weather.weathercode) {
                                                    0 -> Pair("CLEAR SKY", Icons.Filled.WbSunny)
                                                    1, 2, 3 -> Pair("PARTLY CLOUDY", Icons.Filled.Cloud)
                                                    45, 48 -> Pair("FOGGY", Icons.Filled.Cloud)
                                                    51, 53, 55 -> Pair("DRIZZLE", Icons.Filled.Cloud)
                                                    61, 63, 65 -> Pair("RAIN", Icons.Filled.Cloud)
                                                    else -> Pair("UNKNOWN", Icons.Filled.Cloud)
                                                }
                                                weatherCondition = parsed.first
                                                weatherIcon = parsed.second
                                            }
                                            isLocationSearching = false
                                            locationHasSuccessfullySynced = true

                                            sharedPref.edit().apply {
                                                putBoolean("LOC_SYNCED", true)
                                                putString("CITY_NAME", cityName)
                                                putString("TEMP", currentTemperature)
                                                putString("WEATHER_COND", weatherCondition)
                                                putString("LAT", location.latitude.toString())
                                                putString("LON", location.longitude.toString())
                                            }.apply()
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isLocationSearching = false
                                        }
                                    }
                                }
                            } else {
                                isLocationSearching = false
                            }
                        }
                    }
            } catch (e: SecurityException) {
                isLocationSearching = false
            }
        }
    }

    // --- PREMIUM RADAR PING & FROSTED GLASS ANIMATION ---
    if (isLocationSearching) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val infiniteTransition = rememberInfiniteTransition()

            // Outer radar wave (expands and fades)
            val radarScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 3.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
            val radarAlpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                )
            )

            // Inner pin pulse
            val pinScale by infiniteTransition.animateFloat(
                initialValue = 0.9f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.85f),
                                Color.White.copy(alpha = 0.95f)
                            )
                        )
                    ), // Premium heavy frosted glass gradient
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        // Expanding Radar Ring 1
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .scale(radarScale)
                                .clip(CircleShape)
                                .background(com.simats.growise.ui.theme.GoldenYellow.copy(alpha = radarAlpha * 0.4f))
                        )
                        // Expanding Radar Ring 2 (Delayed visual effect)
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .scale(radarScale * 0.6f)
                                .clip(CircleShape)
                                .background(com.simats.growise.ui.theme.TerracottaPrimary.copy(alpha = radarAlpha * 0.2f))
                        )
                        // Central Bouncing Pin
                        Icon(
                            painter = painterResource(id = R.drawable.ic_location_pin),
                            contentDescription = "Syncing",
                            tint = com.simats.growise.ui.theme.TerracottaPrimary,
                            modifier = Modifier
                                .size(56.dp)
                                .scale(pinScale)
                        )
                    }
                    Spacer(modifier = Modifier.height(36.dp))
                    Text(
                        text = "Locking GPS Coordinates...",
                        color = com.simats.growise.ui.theme.TerracottaPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.simats.growise.ui.theme.PeachBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Growise Brand Custom Logo",
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GroWise",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = com.simats.growise.ui.theme.TerracottaPrimary
                )
            }
            IconButton(onClick = { showSettingsDialog = true }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Configuration Profile Setup",
                    tint = com.simats.growise.ui.theme.TextDark
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "$greetingLine, $farmerName",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = com.simats.growise.ui.theme.TerracottaPrimary,
                lineHeight = 34.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- DYNAMIC WEATHER CARD ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (locationHasSuccessfullySynced) {
                        onWeatherClick()
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, Color(0xFFF9EFE9))
        ) {
            if (locationHasSuccessfullySynced) {
                // SYNCED UI
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = weatherIcon,
                        contentDescription = "Weather Icon",
                        tint = com.simats.growise.ui.theme.GoldenYellow,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentTemperature,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = com.simats.growise.ui.theme.TerracottaPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = weatherCondition,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = com.simats.growise.ui.theme.TextMuted,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_location_pin),
                                    contentDescription = "Location Synced",
                                    tint = com.simats.growise.ui.theme.TerracottaPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = cityName,
                                    fontSize = 13.sp,
                                    color = com.simats.growise.ui.theme.TerracottaPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Interactive re-sync trigger layer anchored below historical logs
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable {
                                        locationPermissionLauncher.launch(
                                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                        )
                                    }
                                    .background(com.simats.growise.ui.theme.PeachBackground, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_agri_loading),
                                    contentDescription = "Force Re-sync Current GPS Location",
                                    tint = com.simats.growise.ui.theme.TerracottaPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Update Location",
                                    fontSize = 11.sp,
                                    color = com.simats.growise.ui.theme.TerracottaPrimary,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            } else {
                // PREMIUM UNSYNCED UI
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(com.simats.growise.ui.theme.PeachBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_location_pin),
                            contentDescription = "Weather Icon",
                            tint = com.simats.growise.ui.theme.TerracottaPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Tap to sync current location",
                        fontSize = 15.sp,
                        color = com.simats.growise.ui.theme.TextDark,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Enable GPS to view live weather",
                        fontSize = 12.sp,
                        color = com.simats.growise.ui.theme.TextMuted,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                HomeDashboardActionTile("Diagnose\nCrop", Icons.Filled.Eco, onDiagnoseCropClick)
            }
            Box(modifier = Modifier.weight(1f)) {
                HomeDashboardActionTile("List\nProduce", Icons.Filled.Inventory, onHarvestManagerClick)
            }
            Box(modifier = Modifier.weight(1f)) {
                HomeDashboardActionTile("Rent\nEquipment", Icons.Filled.Agriculture, onRentEquipmentClick)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSmartCultivationClick() },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.5.dp, com.simats.growise.ui.theme.GoldenYellow.copy(alpha = 0.8f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(com.simats.growise.ui.theme.PeachBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Eco,
                        contentDescription = null,
                        tint = com.simats.growise.ui.theme.GoldenYellow,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Smart Cultivation",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = com.simats.growise.ui.theme.TextDark
                    )
                    Spacer(modifier = Modifier.height(2.dp))

                    if (isCheckingSchedule) {
                        // Loading animation to hide blank flash
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val infiniteTransition = rememberInfiniteTransition()
                            val rotation by infiniteTransition.animateFloat(
                                initialValue = 0f, targetValue = 360f,
                                animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing))
                            )
                            Icon(
                                painter = painterResource(id = R.drawable.ic_agri_loading),
                                contentDescription = "Loading",
                                tint = com.simats.growise.ui.theme.TerracottaPrimary,
                                modifier = Modifier.size(14.dp).rotate(rotation)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Checking schedule...", fontSize = 13.sp, color = com.simats.growise.ui.theme.TerracottaPrimary, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            text = if (pendingTasksCount > 0) "You have $pendingTasksCount Pending Tasks" else "AI-Driven Growth Roadmap",
                            fontWeight = if (pendingTasksCount > 0) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (pendingTasksCount > 0) com.simats.growise.ui.theme.TerracottaPrimary else com.simats.growise.ui.theme.TextMuted
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Open Roadmap",
                    tint = com.simats.growise.ui.theme.TerracottaPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- SINGLE SAVED DIAGNOSTICS CARD ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDiagnosticHistoryClick() },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.5.dp, com.simats.growise.ui.theme.GoldenYellow.copy(alpha = 0.8f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(com.simats.growise.ui.theme.PeachBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = com.simats.growise.ui.theme.GoldenYellow,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Saved Diagnostics",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = com.simats.growise.ui.theme.TextDark
                    )
                    Spacer(modifier = Modifier.height(2.dp))

                    if (isCheckingHistory) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val infiniteTransition = rememberInfiniteTransition()
                            val rotation by infiniteTransition.animateFloat(
                                initialValue = 0f, targetValue = 360f,
                                animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing))
                            )
                            Icon(
                                painter = painterResource(id = R.drawable.ic_agri_loading),
                                contentDescription = "Loading",
                                tint = com.simats.growise.ui.theme.TerracottaPrimary,
                                modifier = Modifier.size(14.dp).rotate(rotation)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Checking saved logs...", fontSize = 13.sp, color = com.simats.growise.ui.theme.TerracottaPrimary, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            text = if (recentDiagnostics.isNotEmpty()) "${recentDiagnostics.size} Saved Logs Found" else "No Saved Logs Yet",
                            fontWeight = if (recentDiagnostics.isNotEmpty()) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (recentDiagnostics.isNotEmpty()) com.simats.growise.ui.theme.TerracottaPrimary else com.simats.growise.ui.theme.TextMuted
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Open History",
                    tint = com.simats.growise.ui.theme.TerracottaPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Box(modifier = Modifier.fillMaxWidth().height(80.dp))
    }

    if (showSettingsDialog) {
        com.simats.growise.common.SettingsDialog(
            onDismiss = { showSettingsDialog = false },
            onNavigateToChangePassword = {
                showSettingsDialog = false
                navController.navigate("change_password")
            }
        )
    }
}

@Composable
fun HomeDashboardActionTile(title: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Color(0xFFF9EFE9))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(com.simats.growise.ui.theme.PeachBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = com.simats.growise.ui.theme.GoldenYellow, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.TextDark,
                lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
            )
        }
    }
}