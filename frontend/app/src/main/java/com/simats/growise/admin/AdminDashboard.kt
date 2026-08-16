package com.simats.growise.admin

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import com.simats.growise.R
import com.simats.growise.data.network.RetrofitClient
import com.simats.growise.ui.theme.GoldenYellow
import com.simats.growise.ui.theme.PeachBackground
import com.simats.growise.ui.theme.TerracottaPrimary
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun AdminDashboard(navController: NavController) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("AdminCache", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    var showSettingsDialog by remember { mutableStateOf(false) }

    var totalUsers by remember { mutableIntStateOf(sharedPref.getInt("totalUsers", 0)) }
    var activeFarmers by remember { mutableIntStateOf(sharedPref.getInt("activeFarmers", 0)) }
    var verifiedDrivers by remember { mutableIntStateOf(sharedPref.getInt("verifiedDrivers", 0)) }
    var activeDealsToday by remember { mutableIntStateOf(sharedPref.getInt("activeDealsToday", 0)) }

    var isLoading by remember { mutableStateOf(totalUsers == 0) }

    val c = Calendar.getInstance()
    val timeOfDay = c.get(Calendar.HOUR_OF_DAY)
    val greeting = when (timeOfDay) {
        in 0..11 -> "Good Morning"
        in 12..15 -> "Good Afternoon"
        in 16..20 -> "Good Evening"
        else -> "Good Night"
    }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val res = RetrofitClient.apiService.getAdminStats()
                if (res.isSuccessful && res.body() != null) {
                    val data = res.body()!!
                    totalUsers = data.totalUsers
                    activeFarmers = data.activeFarmers
                    verifiedDrivers = data.verifiedDrivers
                    activeDealsToday = data.activeDealsToday

                    sharedPref.edit().apply {
                        putInt("totalUsers", totalUsers)
                        putInt("activeFarmers", activeFarmers)
                        putInt("verifiedDrivers", verifiedDrivers)
                        putInt("activeDealsToday", activeDealsToday)
                    }.apply()
                }
            } catch (e: Exception) { }
            isLoading = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Restart)
    )

    Scaffold(
        containerColor = PeachBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()) // Removes the top inset padding
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // MATCHING FARMER HOME SCREEN TOP HEADER
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
                        color = TerracottaPrimary
                    )
                }
                IconButton(onClick = { showSettingsDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Configuration Profile Setup",
                        tint = Color.DarkGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "$greeting, Admin",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerracottaPrimary,
                    lineHeight = 34.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Platform Analytics", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_agri_loading),
                        contentDescription = "Loading",
                        tint = TerracottaPrimary,
                        modifier = Modifier.size(64.dp).rotate(rotation)
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AdminStatCard(title = "Total Users", count = totalUsers.toString(), icon = Icons.Filled.People, color = Color(0xFF1976D2), modifier = Modifier.weight(1f)) { navController.navigate("admin_list/users") }
                    AdminStatCard(title = "Total Farmers", count = activeFarmers.toString(), icon = Icons.Filled.Agriculture, color = Color(0xFF388E3C), modifier = Modifier.weight(1f)) { navController.navigate("admin_list/farmers") }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AdminStatCard(title = "Verified Drivers", count = verifiedDrivers.toString(), icon = Icons.Filled.LocalShipping, color = TerracottaPrimary, modifier = Modifier.weight(1f)) { navController.navigate("admin_list/drivers") }
                    AdminStatCard(title = "Today's Orders", count = activeDealsToday.toString(), icon = Icons.Filled.Handshake, color = GoldenYellow, modifier = Modifier.weight(1f)) { navController.navigate("admin_list/deals") }
                }

                Spacer(modifier = Modifier.height(32.dp))

                var graphFilter by remember { mutableStateOf("7 Days") }
                var graphData by remember { mutableStateOf(listOf(1f, 1f, 1f, 1f, 1f, 1f, 1f)) }
                var isGraphLoading by remember { mutableStateOf(true) }
                var tappedPoint by remember { mutableStateOf<Pair<Int, Float>?>(null) }
                var tapOffset by remember { mutableStateOf(Offset.Zero) }

                LaunchedEffect(graphFilter) {
                    isGraphLoading = true
                    tappedPoint = null
                    try {
                        val res = RetrofitClient.apiService.getGraphData(graphFilter)
                        if (res.isSuccessful && res.body() != null) {
                            graphData = res.body()!!.dataPoints ?: listOf(1f)
                        }
                    } catch (e: Exception) {}
                    isGraphLoading = false
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Platform Activity", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                    Row {
                        listOf("7 Days", "1 Month", "6 Months").forEach { filter ->
                            Text(
                                text = filter.replace(" Months", "M").replace(" Month", "M").replace(" Days", "D"),
                                fontSize = 12.sp,
                                fontWeight = if (graphFilter == filter) FontWeight.Bold else FontWeight.Normal,
                                color = if (graphFilter == filter) Color.White else TerracottaPrimary,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .background(if (graphFilter == filter) TerracottaPrimary else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable { graphFilter = filter }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Custom Sleek Canvas Line Chart for Platform Activity
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp),
                    border = BorderStroke(1.dp, GoldenYellow.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().height(240.dp).padding(bottom = 8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isGraphLoading) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_agri_loading),
                                contentDescription = "Loading",
                                tint = TerracottaPrimary,
                                modifier = Modifier.size(48.dp).align(Alignment.Center).rotate(rotation)
                            )
                        } else {
                            Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                val maxVal = graphData.maxOrNull()?.takeIf { it > 0 } ?: 100f
                                Column(
                                    modifier = Modifier.fillMaxHeight().padding(end = 8.dp, bottom = 4.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(maxVal.toInt().toString(), fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text((maxVal * 0.5).toInt().toString(), fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text("0", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                }
                                Box(modifier = Modifier.fillMaxSize()) {
                                    androidx.compose.foundation.Canvas(
                                        modifier = Modifier.fillMaxSize()
                                            .pointerInput(graphData) {
                                                detectTapGestures { offset ->
                                                    val maxPoint = graphData.maxOrNull()?.takeIf { it > 0 } ?: 1f
                                                    val width = size.width.toFloat()
                                                    val xStep = width / (graphData.size - 1).coerceAtLeast(1)
                                                    val tappedIndex = (offset.x / xStep).roundToInt().coerceIn(0, graphData.size - 1)
                                                    tappedPoint = Pair(tappedIndex, graphData[tappedIndex])

                                                    val actualX = tappedIndex * xStep
                                                    val actualY = size.height - ((graphData[tappedIndex] / maxPoint) * size.height)
                                                    tapOffset = Offset(actualX, actualY)
                                                }
                                            }
                                    ) {
                                        val dataPoints = graphData
                                        val maxPoint = dataPoints.maxOrNull()?.takeIf { it > 0 } ?: 100f
                                        val width = size.width
                                        val height = size.height
                                        val xStep = width / (dataPoints.size - 1).coerceAtLeast(1)

                                        val pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

                                        // Horizontal Grid Lines
                                        for (i in 0..2) {
                                            val y = height - (i * height / 2f)
                                            drawLine(
                                                color = Color.LightGray.copy(alpha = 0.5f),
                                                start = Offset(0f, y),
                                                end = Offset(width, y),
                                                strokeWidth = 2f,
                                                pathEffect = pathEffect
                                            )
                                        }

                                        // Vertical Reference Lines
                                        dataPoints.forEachIndexed { index, _ ->
                                            val x = index * xStep
                                            drawLine(
                                                color = Color.LightGray.copy(alpha = 0.3f),
                                                start = Offset(x, 0f),
                                                end = Offset(x, height),
                                                strokeWidth = 2f,
                                                pathEffect = pathEffect
                                            )
                                        }

                                        val path = androidx.compose.ui.graphics.Path()
                                        val fillPath = androidx.compose.ui.graphics.Path()

                                        dataPoints.forEachIndexed { index, value ->
                                            val x = index * xStep
                                            val y = height - ((value / maxPoint) * height)
                                            if (index == 0) {
                                                path.moveTo(x, y)
                                                fillPath.moveTo(x, height)
                                                fillPath.lineTo(x, y)
                                            } else {
                                                path.lineTo(x, y)
                                                fillPath.lineTo(x, y)
                                            }
                                        }
                                        fillPath.lineTo(width, height)
                                        fillPath.close()

                                        drawPath(
                                            path = fillPath,
                                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                colors = listOf(TerracottaPrimary.copy(alpha = 0.3f), Color.Transparent)
                                            )
                                        )
                                        drawPath(
                                            path = path,
                                            color = TerracottaPrimary,
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                                        )

                                        dataPoints.forEachIndexed { index, value ->
                                            val x = index * xStep
                                            val y = height - ((value / maxPoint) * height)
                                            drawCircle(color = Color.White, radius = 12f, center = androidx.compose.ui.geometry.Offset(x, y))
                                            drawCircle(color = GoldenYellow, radius = 10f, center = androidx.compose.ui.geometry.Offset(x, y))
                                            drawCircle(color = TerracottaPrimary, radius = 6f, center = androidx.compose.ui.geometry.Offset(x, y))
                                        }
                                    }
                                    // Tooltip Overlay
                                    tappedPoint?.let { (index, value) ->
                                        val daysAgo = (graphData.size - 1) - index
                                        val label = if (daysAgo == 0) "Today" else "$daysAgo days ago"
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .offset(y = (-8).dp)
                                                .background(Color(0xFF424242), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(label, color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                Text("${value.toInt()} Orders", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    "Tracking total order volume (Standard, Rescue, and Shared Pools) across the selected timeframe.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
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

@Composable
fun AdminStatCard(title: String, count: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp),
        border = BorderStroke(2.dp, GoldenYellow),
        modifier = modifier.height(120.dp).clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Box(modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(count, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                Text(title, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
            }
        }
    }
}