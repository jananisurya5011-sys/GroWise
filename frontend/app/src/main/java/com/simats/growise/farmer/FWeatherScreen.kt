package com.simats.growise.farmer

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.simats.growise.R
import com.simats.growise.data.network.WeatherResponse
import com.simats.growise.data.network.WeatherRetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FWeatherScreen(onBackClick: () -> Unit) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("GroWiseSession", android.content.Context.MODE_PRIVATE) }
    val gson = remember { Gson() }

    val cityName = remember { sharedPref.getString("CITY_NAME", "Unknown Location") ?: "Unknown Location" }
    val savedLat = remember { sharedPref.getString("LAT", "0.0")?.toDoubleOrNull() ?: 0.0 }
    val savedLon = remember { sharedPref.getString("LON", "0.0")?.toDoubleOrNull() ?: 0.0 }
    val cachedWeatherJson = remember { sharedPref.getString("CACHED_WEATHER", null) }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Temperature", "Precipitation", "Wind")

    var isLoading by remember { mutableStateOf(true) }
    var weatherData by remember { mutableStateOf<WeatherResponse?>(null) }

    LaunchedEffect(Unit) {
        // Instant Offline Loading (Cache Upgrade)
        if (cachedWeatherJson != null) {
            try {
                weatherData = gson.fromJson(cachedWeatherJson, WeatherResponse::class.java)
                isLoading = false
            } catch (e: Exception) {}
        }

        // Background Sync
        if (savedLat != 0.0 && savedLon != 0.0) {
            try {
                val data = WeatherRetrofitClient.apiService.getFullWeather(savedLat, savedLon)
                withContext(Dispatchers.Main) {
                    weatherData = data
                    sharedPref.edit().putString("CACHED_WEATHER", gson.toJson(data)).apply()
                    isLoading = false
                }
            } catch (e: Exception) {
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.simats.growise.ui.theme.PeachBackground)
            .padding(top = 16.dp)
    ) {
        // --- 1. PREMIUM HEADER ACTION TOP BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = com.simats.growise.ui.theme.TerracottaPrimary)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(Icons.Filled.LocationOn, contentDescription = "Location Pin", tint = com.simats.growise.ui.theme.TerracottaPrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = cityName,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = com.simats.growise.ui.theme.TextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        if (isLoading) {
            val infiniteTransition = rememberInfiniteTransition()
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart)
            )
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_agri_loading),
                    contentDescription = "Loading Weather",
                    tint = com.simats.growise.ui.theme.TerracottaPrimary,
                    modifier = Modifier.size(64.dp).rotate(rotation)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // --- 2. CURRENT METRICS HERO PANEL (DYNAMIC METRICS FIX) ---
                val currentTemp = weatherData?.current_weather?.temperature?.toInt()?.toString() ?: "--"
                val humidity = weatherData?.hourly?.relativehumidity_2m?.firstOrNull() ?: 50
                val wind = weatherData?.current_weather?.windspeed?.toInt() ?: 10

                // FIX: Replaced static "0%" rainfall with dynamic reading
                val rainfallRaw = weatherData?.hourly?.precipitation?.firstOrNull() ?: 0.0
                val rainfallDisplay = if (rainfallRaw > 0.0) "${rainfallRaw}mm" else "0mm"

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, shape = RoundedCornerShape(28.dp)),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFFDF7F2))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WbSunny,
                                contentDescription = "Weather Graphic",
                                tint = com.simats.growise.ui.theme.GoldenYellow,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = currentTemp,
                                    fontSize = 56.sp,
                                    fontWeight = FontWeight.Black,
                                    color = com.simats.growise.ui.theme.TerracottaPrimary
                                )
                                Text(
                                    text = "°C",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = com.simats.growise.ui.theme.GoldenYellow,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.weight(0.8f)
                        ) {
                            Text("Rainfall: $rainfallDisplay", fontSize = 12.sp, color = com.simats.growise.ui.theme.TextMuted, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Humidity: $humidity%", fontSize = 12.sp, color = com.simats.growise.ui.theme.TextMuted, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Wind: $wind km/h", fontSize = 12.sp, color = com.simats.growise.ui.theme.TextMuted, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- 3. GOOGLE-STYLE TABS SYSTEM ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    tabs.forEachIndexed { index, title ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = index },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = title,
                                fontSize = 14.sp,
                                fontWeight = if (selectedTab == index) FontWeight.ExtraBold else FontWeight.Bold,
                                color = if (selectedTab == index) com.simats.growise.ui.theme.TerracottaPrimary else com.simats.growise.ui.theme.TextMuted
                            )
                            if (selectedTab == index) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .height(4.dp)
                                        .width(54.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(com.simats.growise.ui.theme.TerracottaPrimary)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // --- 4. HIGH-END MULTI-MODE DYNAMIC SPLINE GRAPH ---
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, shape = RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val times = listOf("Now", "+3h", "+6h", "+9h", "+12h", "+15h")
                            times.forEach { time ->
                                Text(text = time, fontSize = 11.sp, color = com.simats.growise.ui.theme.TextMuted, fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        ) {
                            val width = size.width
                            val height = size.height

                            // FIX: Start hourly graph points from current hour so "Now" dynamically matches present temperature
                            val curHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

                            val displayData: List<Double> = when (selectedTab) {
                                1 -> {
                                    val rawPrecip = weatherData?.hourly?.precipitation ?: List(48) { 0.0 }
                                    listOf(
                                        rawPrecip.getOrNull(curHour) ?: 0.0, rawPrecip.getOrNull(curHour + 3) ?: 0.0,
                                        rawPrecip.getOrNull(curHour + 6) ?: 0.0, rawPrecip.getOrNull(curHour + 9) ?: 0.0,
                                        rawPrecip.getOrNull(curHour + 12) ?: 0.0, rawPrecip.getOrNull(curHour + 15) ?: 0.0
                                    )
                                }
                                2 -> {
                                    val rawWind = weatherData?.hourly?.windspeed_10m ?: List(48) { wind.toDouble() }
                                    listOf(
                                        rawWind.getOrNull(curHour) ?: wind.toDouble(), rawWind.getOrNull(curHour + 3) ?: wind.toDouble(),
                                        rawWind.getOrNull(curHour + 6) ?: wind.toDouble(), rawWind.getOrNull(curHour + 9) ?: wind.toDouble(),
                                        rawWind.getOrNull(curHour + 12) ?: wind.toDouble(), rawWind.getOrNull(curHour + 15) ?: wind.toDouble()
                                    )
                                }
                                else -> {
                                    val rawTemps = weatherData?.hourly?.temperature_2m ?: List(48) { 25.0 }
                                    listOf(
                                        rawTemps.getOrNull(curHour) ?: (weatherData?.current_weather?.temperature ?: 25.0),
                                        rawTemps.getOrNull(curHour + 3) ?: 26.0,
                                        rawTemps.getOrNull(curHour + 6) ?: 27.0,
                                        rawTemps.getOrNull(curHour + 9) ?: 28.0,
                                        rawTemps.getOrNull(curHour + 12) ?: 26.0,
                                        rawTemps.getOrNull(curHour + 15) ?: 24.0
                                    )
                                }
                            }

                            val maxValue = displayData.maxOrNull() ?: 40.0
                            val minValue = displayData.minOrNull() ?: 0.0
                            val range = (maxValue - minValue).coerceAtLeast(1.0)

                            val points = displayData.mapIndexed { index, value ->
                                val x = (width / 5) * index
                                val normalizedY = 1f - ((value - minValue) / range).toFloat()
                                val y = height * 0.15f + (normalizedY * height * 0.7f)
                                Offset(x, y)
                            }

                            val path = Path().apply {
                                moveTo(points[0].x, points[0].y)
                                for (i in 0 until points.size - 1) {
                                    val p1 = points[i]
                                    val p2 = points[i + 1]
                                    cubicTo((p1.x + p2.x) / 2f, p1.y, (p1.x + p2.x) / 2f, p2.y, p2.x, p2.y)
                                }
                            }

                            drawPath(
                                path = path,
                                color = com.simats.growise.ui.theme.TerracottaPrimary,
                                style = Stroke(width = 6f)
                            )

                            points.forEach { point ->
                                drawCircle(
                                    color = com.simats.growise.ui.theme.GoldenYellow,
                                    radius = 12f,
                                    center = point
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 6f,
                                    center = point
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- 5. FORECAST CHIPS ROADMAP GRID ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFF9EFE9))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val daysOfWeek = listOf("Today", "Tmrw", "Day 3", "Day 4", "Day 5")
                        val currentTempInt = weatherData?.current_weather?.temperature?.toInt() ?: 25

                        daysOfWeek.forEachIndexed { index, dayName ->
                            var maxT = weatherData?.daily?.temperature_2m_max?.getOrNull(index)?.toInt() ?: 30
                            var minT = weatherData?.daily?.temperature_2m_min?.getOrNull(index)?.toInt() ?: 22

                            // FIX: Dynamic bounding for "Today" (index 0) to prevent mismatch with current temperature
                            if (index == 0) {
                                maxT = maxOf(maxT, currentTempInt)
                                minT = minOf(minT, currentTempInt)
                            }

                            val code = weatherData?.daily?.weathercode?.getOrNull(index) ?: 0
                            val icon = if (code <= 3) Icons.Filled.WbSunny else Icons.Filled.Cloud

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (index == 0) com.simats.growise.ui.theme.PeachBackground else Color.Transparent)
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Text(
                                    text = dayName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = com.simats.growise.ui.theme.TextDark
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (icon == Icons.Filled.WbSunny) com.simats.growise.ui.theme.GoldenYellow else com.simats.growise.ui.theme.TerracottaPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "$maxT°",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = com.simats.growise.ui.theme.TextDark
                                )
                                Text(
                                    text = "$minT°",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = com.simats.growise.ui.theme.TextMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- 6. DYNAMIC ADVISORY SYSTEM METEOROLOGICAL ENGINE ---
                val parsedTemp = weatherData?.current_weather?.temperature ?: 0.0
                val parsedWind = weatherData?.current_weather?.windspeed ?: 0.0

                if (parsedTemp >= 35.0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = com.simats.growise.ui.theme.ErrorRed.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, com.simats.growise.ui.theme.ErrorRed.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.WarningAmber, contentDescription = "Alert", tint = com.simats.growise.ui.theme.ErrorRed)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Excessive Heat Advisory", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.ErrorRed)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Severe thermal impact measured across fields. Adjust evapotranspiration rates and execute targeted shade irrigation cycles immediately.", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = com.simats.growise.ui.theme.TextMuted, lineHeight = 18.sp)
                        }
                    }
                } else if (parsedTemp <= 15.0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = com.simats.growise.ui.theme.TerracottaPrimary.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, com.simats.growise.ui.theme.TerracottaPrimary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Cloud, contentDescription = "Alert", tint = com.simats.growise.ui.theme.TerracottaPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Heavy Precipitation / Cold", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.TerracottaPrimary)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Increased storm metrics discovered. Clear field drainage gates immediately to block localized waterlogging vector points.", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = com.simats.growise.ui.theme.TextMuted, lineHeight = 18.sp)
                        }
                    }
                } else if (parsedWind > 20.0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = com.simats.growise.ui.theme.ErrorRed.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, com.simats.growise.ui.theme.ErrorRed.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Air, contentDescription = "Wind Alert", tint = com.simats.growise.ui.theme.ErrorRed)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("High Wind Advisory", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.ErrorRed)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Wind speeds currently exceed 20 km/h. Pause any planned aerial or boom pesticide spraying to prevent chemical drift. Secure loose greenhouse covers.", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = com.simats.growise.ui.theme.TextMuted, lineHeight = 18.sp)
                        }
                    }
                } else if (humidity < 30) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = com.simats.growise.ui.theme.TerracottaPrimary.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, com.simats.growise.ui.theme.TerracottaPrimary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.WaterDrop, contentDescription = "Dry Alert", tint = com.simats.growise.ui.theme.TerracottaPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Dry Air Warning", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.TerracottaPrimary)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Relative humidity has dropped below 30%. Evapotranspiration is accelerating. Increase your baseline irrigation volumes for shallow-rooted crops.", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = com.simats.growise.ui.theme.TextMuted, lineHeight = 18.sp)
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, com.simats.growise.ui.theme.GoldenYellow.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = "Optimal Symbol", tint = com.simats.growise.ui.theme.GoldenYellow)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Favorable Conditions", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.TextDark)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Current temperature is $currentTemp°C with moderate $wind km/h winds and $humidity% humidity. Ideal window detected for open-field fertilizer enrichment and topsoil preparation.", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = com.simats.growise.ui.theme.TextMuted, lineHeight = 18.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}