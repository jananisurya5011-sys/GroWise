// DriverHome.kt
package com.simats.growise.driver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.rotate
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import com.google.firebase.firestore.FirebaseFirestore
import com.simats.growise.R
import com.simats.growise.data.model.AvailableLoadResponse
import com.simats.growise.data.network.RetrofitClient
import com.simats.growise.ui.theme.GoldenYellow
import com.simats.growise.ui.theme.PeachBackground
import com.simats.growise.ui.theme.TerracottaPrimary
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun DriverHome(navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPref = remember { context.getSharedPreferences("GroWiseSession", Context.MODE_PRIVATE) }
    val driverEmail = sharedPref.getString("USER_EMAIL", "") ?: ""
    var driverName by remember { mutableStateOf(sharedPref.getString("U_FULL_NAME", "Driver") ?: "Driver") }
    val rawVehicle = sharedPref.getString("DRIVER_VEHICLE_TYPE", "Auto") ?: "Auto"
    val myVehicleType = remember {
        when(rawVehicle) {
            "Two-Wheeler" -> "Bike"
            "Three-Wheeler" -> "Auto"
            else -> rawVehicle
        }
    }
    val db = FirebaseFirestore.getInstance()

    var availableLoads by remember { mutableStateOf<List<AvailableLoadResponse>>(emptyList()) }
    var declinedOrders by remember { mutableStateOf(sharedPref.getStringSet("DECLINED_ORDERS", emptySet())?.toSet() ?: emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var driverLat by remember { mutableStateOf(0.0) }
    var driverLon by remember { mutableStateOf(0.0) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var activeOrderId by remember { mutableStateOf<String?>(null) }
    var activeOrderData by remember { mutableStateOf<Map<String, Any>?>(null) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Restart))

    val activeStatuses = listOf(
        "EN_ROUTE_TO_PICKUP", "WAITING_AT_PICKUP", "IN_TRANSIT", "WAITING_AT_DROP",
        "EN_ROUTE_TO_PICKUP_A", "WAITING_AT_PICKUP_A", "EN_ROUTE_TO_PICKUP_B", "WAITING_AT_PICKUP_B",
        "IN_TRANSIT_TO_DROP_A", "WAITING_AT_DROP_A", "IN_TRANSIT_TO_DROP_B", "WAITING_AT_DROP_B"
    )

    LaunchedEffect(driverEmail) {
        if (driverEmail.isNotEmpty()) {
            // Unified query for all active orders
            db.collection("orders").whereEqualTo("driverEmail", driverEmail).whereIn("status", activeStatuses)
                .addSnapshotListener { snap, _ ->
                    if (snap != null && !snap.isEmpty) {
                        activeOrderId = snap.documents[0].id
                        activeOrderData = snap.documents[0].data
                    } else {
                        activeOrderId = null
                        activeOrderData = null
                    }
                }
        }
    }

    val fetchFallbackLoads = {
        isLoading = true
        db.collection("orders").whereIn("status", listOf("PENDING_DRIVER", "PENDING_CO_LOADER")).get().addOnSuccessListener { snap ->
            val combined = snap.documents.mapNotNull { doc ->
                val isPool = doc.id.startsWith("POOL")

                if (isPool) {
                    val tFare = doc.getDouble("totalPayment") ?: 0.0
                    val cFare = doc.getDouble("coLoaderPayment") ?: 0.0
                    val totalEarnings = tFare + cFare

                    AvailableLoadResponse(
                        orderId = doc.getString("orderId") ?: "",
                        farmerEmail = doc.getString("hostEmail") ?: "",
                        userEmail = if (doc.getString("coLoaderEmail") != null) "Co-loader System" else "",
                        cropName = doc.getString("cropName") ?: "",
                        weightKg = doc.getDouble("weightKg") ?: 0.0,
                        transportFare = totalEarnings,
                        pickupAddress = doc.getString("pickupAddress") ?: "",
                        dropAddress = doc.getString("dropAddress") ?: "",
                        pickupLat = doc.getDouble("pickupLat") ?: 0.0,
                        pickupLon = doc.getDouble("pickupLon") ?: 0.0,
                        dropLat = doc.getDouble("dropLat") ?: 0.0,
                        dropLon = doc.getDouble("dropLon") ?: 0.0,
                        distanceKm = 0.0,
                        vehicleType = doc.getString("vehicleType") ?: "Any",
                        status = doc.getString("status") ?: "PENDING_DRIVER",
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    )
                } else {
                    AvailableLoadResponse(
                        orderId = doc.getString("orderId") ?: "",
                        farmerEmail = doc.getString("farmerEmail") ?: "",
                        userEmail = doc.getString("userEmail") ?: "",
                        cropName = doc.getString("cropName") ?: "",
                        weightKg = doc.getDouble("weightKg") ?: 0.0,
                        transportFare = doc.getDouble("transportFare") ?: 0.0,
                        pickupAddress = doc.getString("pickupAddress") ?: "",
                        dropAddress = doc.getString("dropAddress") ?: "",
                        pickupLat = doc.getDouble("pickupLat") ?: 0.0,
                        pickupLon = doc.getDouble("pickupLon") ?: 0.0,
                        dropLat = doc.getDouble("dropLat") ?: 0.0,
                        dropLon = doc.getDouble("dropLon") ?: 0.0,
                        distanceKm = doc.getDouble("distanceKm") ?: 0.0,
                        vehicleType = doc.getString("vehicleType") ?: "Any",
                        status = doc.getString("status") ?: "PENDING_DRIVER",
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    )
                }
            }

            availableLoads = combined.filter { load ->
                if (declinedOrders.contains(load.orderId)) return@filter false
                load.vehicleType.contains(myVehicleType, ignoreCase = true)
            }
            isLoading = false
        }.addOnFailureListener { isLoading = false }
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms.entries.all { it.value }) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
                if (loc != null) {
                    driverLat = loc.latitude
                    driverLon = loc.longitude
                }
                coroutineScope.launch {
                    try {
                        val res = RetrofitClient.apiService.getAvailableLoads(driverLat, driverLon, driverEmail)
                        if (res.isSuccessful && res.body()?.success == true) {
                            val allLoads = res.body()?.loads ?: emptyList()
                            availableLoads = allLoads.filter { load ->
                                load.vehicleType.contains(myVehicleType, ignoreCase = true)
                            }
                            isLoading = false
                        } else {
                            fetchFallbackLoads()
                        }
                    } catch (e: Exception) {
                        fetchFallbackLoads()
                    }
                }
            }
        } else {
            isLoading = false
        }
    }

    val checkGpsAndFetch = {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        if (!locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
            Toast.makeText(context, "GPS is off. Please enable it to fetch nearby orders.", Toast.LENGTH_LONG).show()
            context.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            isLoading = false
        } else {
            isLoading = true
            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    LaunchedEffect(Unit) { checkGpsAndFetch() }

    val c = Calendar.getInstance()
    val greeting = when (c.get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good Morning"
        in 12..15 -> "Good Afternoon"
        in 16..20 -> "Good Evening"
        else -> "Good Night"
    }

    Scaffold(containerColor = PeachBackground) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp)
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
                        color = TerracottaPrimary
                    )
                    if (activeOrderId == null) {
                        IconButton(onClick = { checkGpsAndFetch() }) {
                            Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh Load Board", tint = TerracottaPrimary)
                        }
                    }
                }
                IconButton(onClick = { showSettingsDialog = true }) {
                    Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Settings, contentDescription = "Settings", tint = Color.DarkGray)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                Text(text = "$greeting, $driverName", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TerracottaPrimary, lineHeight = 34.sp)
                Text("Registered Vehicle: $myVehicleType", color = Color.Gray, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (activeOrderId != null) {
                Spacer(modifier = Modifier.height(40.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(16.dp),
                    border = BorderStroke(2.dp, GoldenYellow.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.White, PeachBackground.copy(alpha = 0.5f))
                            )
                        ).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(80.dp).background(TerracottaPrimary.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.LockClock, contentDescription = null, tint = TerracottaPrimary, modifier = Modifier.size(40.dp))
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Delivery In Progress",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "You are currently handling an active order (${activeOrderData?.get("cropName") ?: "Crop Pool"}). Please complete it before accepting new loads.",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = {
                                val intent = Intent(context, DriverActiveTripActivity::class.java).apply {
                                    putExtra("ORDER_ID", activeOrderId)
                                    putExtra("PICKUP_LAT", (activeOrderData?.get("pickupLat") as? Double) ?: 0.0)
                                    putExtra("PICKUP_LON", (activeOrderData?.get("pickupLon") as? Double) ?: 0.0)
                                    putExtra("DROP_LAT", (activeOrderData?.get("dropLat") as? Double) ?: 0.0)
                                    putExtra("DROP_LON", (activeOrderData?.get("dropLon") as? Double) ?: 0.0)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)
                        ) {
                            Text("View Delivery Details", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = {
                            db.collection("orders").document(activeOrderId!!).update("status", "PENDING_DRIVER", "driverEmail", null, "driverName", null)
                        }) {
                            Text("Dev Test Key: Reset Order", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Text("Available Loads in Radius", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize().padding(bottom = 100.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_agri_loading),
                                contentDescription = "Loading", tint = TerracottaPrimary,
                                modifier = Modifier.size(72.dp).rotate(rotation)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Scanning active logs for $myVehicleType loads...", color = TerracottaPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                } else if (availableLoads.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(bottom = 60.dp), contentAlignment = Alignment.Center) {
                        Text("No matched loads for your vehicle class right now.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(availableLoads) { load ->
                            DriverLoadCard(load, driverLat, driverLon, onDecline = { id ->
                                val newSet = declinedOrders.toMutableSet().apply { add(id) }
                                sharedPref.edit().putStringSet("DECLINED_ORDERS", newSet).apply()
                                declinedOrders = newSet
                                availableLoads = availableLoads.filter { it.orderId != id }

                                db.collection("orders").document(id).update("declinedBy", com.google.firebase.firestore.FieldValue.arrayUnion(driverEmail))
                            })
                        }
                        item { Spacer(modifier = Modifier.height(100.dp)) }
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

@Composable
fun DriverLoadCard(load: AvailableLoadResponse, driverLat: Double, driverLon: Double, onDecline: (String) -> Unit) {
    val context = LocalContext.current
    val timeDiffMinutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - load.timestamp)
    val isExpiringSoon = timeDiffMinutes >= 45

    val formatter = java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
    val postedTime = formatter.format(java.util.Date(load.timestamp))

    val results = FloatArray(1)
    android.location.Location.distanceBetween(driverLat, driverLon, load.pickupLat, load.pickupLon, results)
    val driverToPickupKm = results[0] / 1000.0

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (isExpiringSoon) Color(0xFFFFF3F3) else Color.White),
        elevation = CardDefaults.cardElevation(6.dp),
        border = BorderStroke(2.dp, if (isExpiringSoon) Color.Red.copy(alpha = 0.5f) else GoldenYellow.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            val isPool = load.orderId.startsWith("POOL")
            val hasCoLoader = isPool && load.userEmail == "Co-loader System"

            if (hasCoLoader) {
                Box(modifier = Modifier.background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp).padding(bottom = 8.dp)) {
                    Text("✅ Co-Loader Joined - Detour Included", color = Color(0xFF2E7D32), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if(isPool) "Crop Pool: ${load.cropName}" else load.cropName, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.Black)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Scale, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${load.weightKg} kg", fontSize = 14.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = TerracottaPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${String.format(Locale.US, "%.1f", driverToPickupKm)} km to Pickup", fontSize = 13.sp, color = TerracottaPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Box(modifier = Modifier.background(if (isExpiringSoon) Color(0xFFFFCDD2) else PeachBackground, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text("₹${String.format(Locale.US, "%.0f", load.transportFare)}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = if (isExpiringSoon) Color.Red else TerracottaPrimary)
                    }
                    if (hasCoLoader) {
                        Text("+ Detour Cost", color = Color(0xFF2E7D32), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            if (isExpiringSoon) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color.Red.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(8.dp).fillMaxWidth()) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("High Priority - Waiting ${timeDiffMinutes}m", color = Color.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFF0F0F0))
            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFF9F9F9), RoundedCornerShape(12.dp)).padding(12.dp)) {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Circle, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(12.dp).padding(top = 4.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (hasCoLoader) "Host Pickup Location" else "Pickup Location", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text(load.pickupAddress, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray, lineHeight = 18.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = TerracottaPrimary, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (hasCoLoader) "Host & Co-Loader Drops" else "Drop-off Location", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text(if (hasCoLoader) "Multiple Stops Active (See Map)" else load.dropAddress, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray, lineHeight = 18.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("Posted on $postedTime", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(
                    onClick = { onDecline(load.orderId) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TerracottaPrimary),
                    border = BorderStroke(1.dp, TerracottaPrimary)
                ) {
                    Text("Decline", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        val intent = Intent(context, DriverActiveTripActivity::class.java).apply {
                            putExtra("ORDER_ID", load.orderId)
                            putExtra("PICKUP_LAT", load.pickupLat)
                            putExtra("PICKUP_LON", load.pickupLon)
                            putExtra("DROP_LAT", load.dropLat)
                            putExtra("DROP_LON", load.dropLon)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isExpiringSoon) Color.Red else TerracottaPrimary)
                ) {
                    Text("Accept Now", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}