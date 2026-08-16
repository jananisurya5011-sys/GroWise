package com.simats.growise.user

import android.Manifest
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolunteerActivism
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.gson.Gson
import com.simats.growise.R
import com.simats.growise.data.model.FavoriteFarmerResponse
import com.simats.growise.data.model.InventoryItemResponse
import com.simats.growise.data.network.RetrofitClient
import com.simats.growise.data.network.WeatherRetrofitClient
import com.simats.growise.ui.theme.GoldenYellow
import com.simats.growise.ui.theme.PeachBackground
import com.simats.growise.ui.theme.TerracottaPrimary
import com.simats.growise.ui.theme.TextDark
import com.simats.growise.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

// Global cache variables to prevent re-fetching when swapping screens
private var cachedMarketItems: List<InventoryItemResponse> = emptyList()
private var cachedProfiles: Map<String, String> = emptyMap()
private var lastFetchTime: Long = 0
private var cachedDonationsCount: Int = 0

// Dynamic Expiry Calculation Logic

// Removed local calculateDiscount logic
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UHomeScreen(
    navController: androidx.navigation.NavController,
    onWeatherClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onDonationHubClick: () -> Unit = {},
    onFarmerClick: (String) -> Unit = {},
    onItemClick: (String) -> Unit = {},
    onAddFavoriteClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showSettingsDialog by remember { mutableStateOf(false) }
    val sharedPref = remember { context.getSharedPreferences("GroWiseSession", Context.MODE_PRIVATE) }
    val gson = remember { Gson() }

    val userEmail = remember { sharedPref.getString("USER_EMAIL", "user@growise.com") ?: "user@growise.com" }
    var userName by remember { mutableStateOf(sharedPref.getString("USER_NAME", "User") ?: "User") }
    val userRole = remember { sharedPref.getString("ROLE", "user") ?: "user" }
    val isNgo = remember { userRole.equals("ngo", ignoreCase = true) }

    var greetingLine by remember { mutableStateOf("Hello") }

    val baseUrl = remember { RetrofitClient.BASE_URL.removeSuffix("/") }

    var marketItems by remember { mutableStateOf(cachedMarketItems) }
    var farmerProfiles by remember { mutableStateOf(cachedProfiles) }
    var isLoadingData by remember { mutableStateOf(cachedMarketItems.isEmpty()) }

    var isCheckingDonations by remember { mutableStateOf(cachedDonationsCount == 0) }
    var pendingDonationsCount by remember { mutableStateOf(cachedDonationsCount) }

    var locationHasSuccessfullySynced by remember { mutableStateOf(sharedPref.getBoolean("LOC_SYNCED", false)) }
    var cityName by remember { mutableStateOf(sharedPref.getString("CITY_NAME", "Sync Location Needed") ?: "Sync Location Needed") }
    var currentTemperature by remember { mutableStateOf(sharedPref.getString("TEMP", "--°C") ?: "--°C") }
    var weatherCondition by remember { mutableStateOf(sharedPref.getString("WEATHER_COND", "TAP PIN TO SYNC") ?: "TAP PIN TO SYNC") }

    var weatherIcon by remember { mutableStateOf(Icons.Filled.Cloud) }
    var isLocationSearching by remember { mutableStateOf(false) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val infiniteTransition = rememberInfiniteTransition()
    val loadingRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing))
    )

    LaunchedEffect(Unit) {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        greetingLine = when (currentHour) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..20 -> "Good Evening"
            else -> "Good Night"
        }

        try {
            val response = RetrofitClient.apiService.retrieveProfileFields(userEmail)
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                if (!data.name.isNullOrEmpty()) {
                    userName = data.name
                    sharedPref.edit().putString("USER_NAME", data.name).apply()
                }
            }
        } catch (e: Exception) {}

        val currentTime = System.currentTimeMillis()
        if (cachedMarketItems.isEmpty() || (currentTime - lastFetchTime > 300000)) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    isLoadingData = true
                    val allItems = RetrofitClient.apiService.fetchMarketItems()

                    val distinctEmails = allItems.map { it.email }.distinct().filter { it.isNotEmpty() }
                    val profilesMap = mutableMapOf<String, String>()
                    for (emailStr in distinctEmails) {
                        try {
                            val profileRes = RetrofitClient.apiService.retrieveProfileFields(emailStr)
                            if (profileRes.isSuccessful && profileRes.body() != null) {
                                val imageUrl = profileRes.body()?.profile_image_url ?: ""
                                profilesMap[emailStr] = imageUrl
                            }
                        } catch (e: Exception) {}
                    }

                    withContext(Dispatchers.Main) {
                        cachedMarketItems = allItems.filter { item ->
                            var diffInHours = 1000L
                            if (item.expiryDate.isNotEmpty()) {
                                try {
                                    val format = if (item.expiryDate.contains("-")) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                    val expDate = format.parse(item.expiryDate)
                                    if (expDate != null) {
                                        diffInHours = TimeUnit.MILLISECONDS.toHours(expDate.time - System.currentTimeMillis())
                                    }
                                } catch (e: Exception) {}
                            }
                            item.availableKg > 0.0 && !item.donatedToNgo && diffInHours > 48
                        }
                        cachedProfiles = profilesMap
                        lastFetchTime = System.currentTimeMillis()

                        farmerProfiles = cachedProfiles
                        marketItems = cachedMarketItems
                        isLoadingData = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isLoadingData = false
                    }
                }
            }
        } else {
            isLoadingData = false
        }

        // Run NGO calculation on a fully independent thread so it never waits for or fails with Market data
        if (isNgo) {
            coroutineScope.launch(Dispatchers.IO) {
                isCheckingDonations = true
                try {
                    val savedLat = sharedPref.getString("LAT", "13.0827")?.toDoubleOrNull() ?: 13.0827
                    val savedLon = sharedPref.getString("LON", "80.2707")?.toDoubleOrNull() ?: 80.2707
                    val url = java.net.URL("$baseUrl/api/ngo/rescue-feed")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true
                    val reqBody = org.json.JSONObject().apply { put("lat", savedLat); put("lon", savedLon) }
                    connection.outputStream.write(reqBody.toString().toByteArray(Charsets.UTF_8))

                    if (connection.responseCode == 200) {
                        val reader = java.io.BufferedReader(java.io.InputStreamReader(connection.inputStream))
                        val responseStr = reader.readText()
                        reader.close()
                        val jsonArray = org.json.JSONArray(responseStr)
                        val declinedSet = sharedPref.getStringSet("DECLINED_RESCUES", setOf()) ?: setOf()

                        var activeCount = 0
                        val currentTs = System.currentTimeMillis()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val expStr = obj.optString("expiryDate", "")
                            var diffHours = 1000.0
                            if (expStr.isNotEmpty()) {
                                try {
                                    val format = if (expStr.contains("-")) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                    val expDate = format.parse(expStr)
                                    if (expDate != null) diffHours = (expDate.time - currentTs) / 3600000.0
                                } catch (e: Exception) {}
                            }
                            if (!declinedSet.contains(obj.optString("id")) && diffHours > 0) {
                                activeCount++
                            }
                        }
                        withContext(Dispatchers.Main) {
                            pendingDonationsCount = activeCount
                            cachedDonationsCount = activeCount
                            isCheckingDonations = false
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            pendingDonationsCount = 0
                            cachedDonationsCount = 0
                            isCheckingDonations = false
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isCheckingDonations = false
                    }
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {

            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

            if (!isGpsEnabled) {
                Toast.makeText(context, "Please turn on your GPS location switch.", Toast.LENGTH_LONG).show()
                currentTemperature = "0°C"
                weatherCondition = "GPS IS OFF"
                cityName = "Location Disabled"
                locationHasSuccessfullySynced = true
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
                val cancellationTokenSource = CancellationTokenSource()
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
                                            val req = com.simats.growise.data.model.TelemetryRequest(email = userEmail, lat = location.latitude.toString(), lon = location.longitude.toString())
                                            RetrofitClient.apiService.saveLocationTelemetry(req)
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
                                        withContext(Dispatchers.Main) { isLocationSearching = false }
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

    val activeFarmers: List<Pair<String, String>> = remember(marketItems) {
        marketItems.map { item: InventoryItemResponse -> item.email }
            .distinct()
            .filter { it.isNotEmpty() }
            .map { emailStr: String ->
                val name = emailStr.substringBefore("@").replaceFirstChar { it.uppercase() }
                Pair(emailStr, name)
            }
    }

    // Cache Initialization
    val favListType = object : com.google.gson.reflect.TypeToken<List<com.simats.growise.data.model.FavoriteFarmerResponse>>() {}.type
    val cachedFavsJson = sharedPref.getString("CACHED_FAV_FARMERS_$userEmail", "[]")
    val initialFavs: List<com.simats.growise.data.model.FavoriteFarmerResponse> = try { gson.fromJson(cachedFavsJson, favListType) ?: emptyList() } catch (e: Exception) { emptyList() }

    var favoriteFarmers by remember { mutableStateOf(initialFavs) }
    var isFavoritesLoading by remember { mutableStateOf(initialFavs.isEmpty()) } // Show loading only if cache is empty
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, userEmail) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val favRes = RetrofitClient.apiService.getFavorites(com.simats.growise.data.model.EmailRequest(email = userEmail))
                        if (favRes.isSuccessful) {
                            val fetchedFavs = favRes.body() ?: emptyList()
                            withContext(Dispatchers.Main) {
                                favoriteFarmers = fetchedFavs
                                isFavoritesLoading = false
                                sharedPref.edit().putString("CACHED_FAV_FARMERS_$userEmail", gson.toJson(fetchedFavs)).apply()
                            }
                        } else {
                            withContext(Dispatchers.Main) { isFavoritesLoading = false }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { isFavoritesLoading = false }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (isLocationSearching) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val radarScale by infiniteTransition.animateFloat(
                initialValue = 1f, targetValue = 3.5f,
                animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Restart)
            )
            val radarAlpha by infiniteTransition.animateFloat(
                initialValue = 1f, targetValue = 0f,
                animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Restart)
            )
            val pinScale by infiniteTransition.animateFloat(
                initialValue = 0.9f, targetValue = 1.15f,
                animationSpec = infiniteRepeatable(animation = tween(600, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(colors = listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.95f)))),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(64.dp).scale(radarScale).clip(CircleShape).background(GoldenYellow.copy(alpha = radarAlpha * 0.4f)))
                        Box(modifier = Modifier.size(64.dp).scale(radarScale * 0.6f).clip(CircleShape).background(TerracottaPrimary.copy(alpha = radarAlpha * 0.2f)))
                        Icon(painter = painterResource(id = R.drawable.ic_location_pin), contentDescription = "Syncing", tint = TerracottaPrimary, modifier = Modifier.size(56.dp).scale(pinScale))
                    }
                    Spacer(modifier = Modifier.height(36.dp))
                    Text(text = "Locking GPS Coordinates...", color = TerracottaPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = 0.5.sp)
                }
            }
        }
    }

    Scaffold(
        containerColor = PeachBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PeachBackground)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 0.dp)
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
                        Text("GroWise", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary)
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(imageVector = Icons.Filled.Settings, contentDescription = "Configuration Profile Setup", tint = TextDark)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                    Text(text = "$greetingLine, $userName", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TerracottaPrimary, lineHeight = 34.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (locationHasSuccessfullySynced) onWeatherClick()
                            else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, Color(0xFFF9EFE9))
                ) {
                    if (locationHasSuccessfullySynced) {
                        Row(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = weatherIcon, contentDescription = "Weather Icon", tint = GoldenYellow, modifier = Modifier.size(54.dp))
                            Spacer(modifier = Modifier.width(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = currentTemperature, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = weatherCondition, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 0.5.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(painter = painterResource(id = R.drawable.ic_location_pin), contentDescription = "Location Synced", tint = TerracottaPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = cityName, fontSize = 13.sp, color = TerracottaPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clickable { locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
                                            .background(PeachBackground, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(painter = painterResource(id = R.drawable.ic_agri_loading), contentDescription = "Force Re-sync", tint = TerracottaPrimary, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = "Update Location", fontSize = 11.sp, color = TerracottaPrimary, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(PeachBackground), contentAlignment = Alignment.Center) {
                                Icon(painter = painterResource(id = R.drawable.ic_location_pin), contentDescription = "Weather Icon", tint = TerracottaPrimary, modifier = Modifier.size(32.dp))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(text = "Tap to sync current location", fontSize = 15.sp, color = TextDark, fontWeight = FontWeight.ExtraBold)
                            Text(text = "Enable GPS to view live weather", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }

                AnimatedVisibility(visible = isNgo) {
                    Column {
                        Spacer(modifier = Modifier.height(24.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onDonationHubClick() },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.5.dp, GoldenYellow.copy(alpha = 0.8f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(PeachBackground), contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.Filled.VolunteerActivism, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Rescue Donations", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = TextDark)
                                    Spacer(modifier = Modifier.height(2.dp))

                                    if (isCheckingDonations) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(painter = painterResource(id = R.drawable.ic_agri_loading), contentDescription = "Loading", tint = TerracottaPrimary, modifier = Modifier.size(14.dp).rotate(loadingRotation))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = "Checking entries...", fontSize = 13.sp, color = TerracottaPrimary, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Text(
                                            text = if (pendingDonationsCount > 0) "$pendingDonationsCount Donations Active" else "No Donations Active",
                                            fontWeight = if (pendingDonationsCount > 0) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = if (pendingDonationsCount > 0) TerracottaPrimary else TextMuted
                                        )
                                    }
                                }
                                Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = "Open", tint = TerracottaPrimary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Active Local Farmers", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextDark, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(modifier = Modifier.height(12.dp))

                if (isLoadingData) {
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        Icon(painter = painterResource(id = R.drawable.ic_agri_loading), contentDescription = "Loading Farmers", tint = TerracottaPrimary, modifier = Modifier.size(36.dp).rotate(loadingRotation))
                    }
                } else if (activeFarmers.isEmpty()) {
                    Text("No local farmers active.", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 24.dp))
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(activeFarmers, key = { it.first }) { farmerPair ->
                            val email = farmerPair.first
                            val name = farmerPair.second
                            Card(
                                modifier = Modifier.width(150.dp).wrapContentHeight().clickable { onFarmerClick(email) },
                                shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.5.dp, GoldenYellow), elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    val profileUrl = farmerProfiles[email]
                                    val fullProfileUrl = if (!profileUrl.isNullOrEmpty()) {
                                        if (profileUrl.startsWith("http")) profileUrl else "${baseUrl}/${profileUrl.removePrefix("/")}"
                                    } else null

                                    if (fullProfileUrl != null) {
                                        AsyncImage(model = fullProfileUrl, contentDescription = name, modifier = Modifier.size(76.dp).clip(CircleShape).border(2.dp, GoldenYellow, CircleShape), contentScale = ContentScale.Crop)
                                    } else {
                                        Box(modifier = Modifier.size(76.dp).clip(CircleShape).background(PeachBackground), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Filled.Person, contentDescription = name, tint = TerracottaPrimary, modifier = Modifier.size(40.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                        Text("Verified", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = "Verified Badge", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Fresh Market Harvests", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextDark, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(modifier = Modifier.height(12.dp))

                if (isLoadingData) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Icon(painter = painterResource(id = R.drawable.ic_agri_loading), contentDescription = "Loading Harvests", tint = TerracottaPrimary, modifier = Modifier.size(36.dp).rotate(loadingRotation))
                    }
                } else if (marketItems.isEmpty()) {
                    Text("No active crops listed.", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 24.dp))
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(marketItems) { item ->
                            val farmerName = item.email.substringBefore("@").replaceFirstChar { it.uppercase() }

                            // Apply Dynamic Expiry Discount Logic from backend Status
                            val discountPercent = when (item.expiryStatus) {
                                "Near Expiry" -> 30
                                "Clearance Sale" -> 50
                                else -> 0
                            }
                            val discountedPrice = if (discountPercent > 0) item.pricePerKg * (1.0 - (discountPercent / 100.0)) else item.pricePerKg

                            // UPDATED TO PERFECT VERTICAL ALIGNMENT WITH FIXED HEIGHT
                            Card(
                                modifier = Modifier.width(170.dp).height(240.dp).clickable { onItemClick(item.email) },
                                shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.5.dp, GoldenYellow), elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                    Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                                        AsyncImage(
                                            model = baseUrl + item.imageUrl, contentDescription = item.cropName,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(PeachBackground),
                                            contentScale = ContentScale.Crop
                                        )
                                        if (discountPercent > 0) {
                                            Box(modifier = Modifier.padding(4.dp).background(Color(0xFFD32F2F), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                Text("$discountPercent% OFF", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                                        Text(item.cropName, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextDark, maxLines = 1, overflow = TextOverflow.Ellipsis)

                                        Column {
                                            if (discountPercent > 0) {
                                                Text("₹${String.format(Locale.US, "%.2f", item.pricePerKg)}", fontSize = 11.sp, color = Color.Gray, textDecoration = TextDecoration.LineThrough)
                                            }
                                            Text("₹${String.format(Locale.US, "%.2f", discountedPrice)}/kg", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2E7D32))

                                            // FIX: Dynamic Low Stock Alert
                                            if (item.availableKg <= 10.0) {
                                                Text("Only ${item.availableKg} kg left!", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                                            } else {
                                                Text("Stock: ${item.availableKg} kg", fontSize = 11.sp, color = Color.Gray)
                                            }
                                        }

                                        Text("By $farmerName", fontSize = 12.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "My Favorite Farmers", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextDark, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(modifier = Modifier.height(12.dp))

                if (isFavoritesLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(painter = painterResource(id = R.drawable.ic_agri_loading), contentDescription = "Loading Favorites", tint = TerracottaPrimary, modifier = Modifier.size(36.dp).rotate(loadingRotation))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Syncing favorites...", color = TerracottaPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (favoriteFarmers.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .width(150.dp)
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.White)
                                        .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(20.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF5F5F5)), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Filled.PersonOff, contentDescription = "No Favorites", tint = Color.Gray)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("No Favorite Farmers Added", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        } else {
                            items(favoriteFarmers) { favData ->
                                val favEmail = favData.farmerEmail
                                val favName = favData.farmerName
                                val favImageUrl = favData.profileImageUrl

                                Card(
                                    modifier = Modifier.width(150.dp).wrapContentHeight().clickable { onFarmerClick(favEmail) },
                                    shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.5.dp, GoldenYellow), elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        val fullProfileUrl = if (favImageUrl.isNotEmpty()) {
                                            if (favImageUrl.startsWith("http")) favImageUrl else "${baseUrl}/${favImageUrl.removePrefix("/")}"
                                        } else null

                                        Box(contentAlignment = Alignment.BottomEnd) {
                                            if (fullProfileUrl != null) {
                                                AsyncImage(model = fullProfileUrl, contentDescription = favName, modifier = Modifier.size(76.dp).clip(CircleShape).border(2.dp, GoldenYellow, CircleShape), contentScale = ContentScale.Crop)
                                            } else {
                                                Box(modifier = Modifier.size(76.dp).clip(CircleShape).background(PeachBackground), contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Filled.Person, contentDescription = favName, tint = TerracottaPrimary, modifier = Modifier.size(40.dp))
                                                }
                                            }
                                            Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.White).border(1.dp, GoldenYellow, CircleShape), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Filled.Favorite, contentDescription = "Fav", tint = TerracottaPrimary, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(favName, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
}