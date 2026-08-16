package com.simats.growise.driver

import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore
import com.simats.growise.ui.theme.GoldenYellow
import com.simats.growise.ui.theme.GroWiseTheme
import com.simats.growise.ui.theme.PeachBackground
import com.simats.growise.ui.theme.TerracottaPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class DriverActiveTripActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val orderId = intent.getStringExtra("ORDER_ID") ?: ""
        val pickupLat = intent.getDoubleExtra("PICKUP_LAT", 0.0)
        val pickupLon = intent.getDoubleExtra("PICKUP_LON", 0.0)
        val dropLat = intent.getDoubleExtra("DROP_LAT", 0.0)
        val dropLon = intent.getDoubleExtra("DROP_LON", 0.0)

        setContent {
            GroWiseTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = PeachBackground) {
                    if (orderId.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error: Order ID not found.", color = Color.Red) }
                    else DriverTripScreen(orderId, pickupLat, pickupLon, dropLat, dropLon, onFinishTrip = { finish() })
                }
            }
        }
    }
}

suspend fun getOsrmRoute(start: GeoPoint, end: GeoPoint): Pair<List<GeoPoint>, String> = withContext(Dispatchers.IO) {
    try {
        val url = java.net.URL("https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=polyline")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.setRequestProperty("User-Agent", "GroWise/1.0 (Android)")
        val response = conn.inputStream.bufferedReader().readText()
        val routeObj = org.json.JSONObject(response).getJSONArray("routes").getJSONObject(0)
        val polyline = routeObj.getString("geometry")
        val durationSecs = routeObj.getDouble("duration")
        val etaStr = "${(durationSecs / 60).toInt()} Mins"

        val poly = ArrayList<GeoPoint>()
        var index = 0; val len = polyline.length; var lat = 0; var lng = 0
        while (index < len) {
            var b: Int; var shift = 0; var result = 0
            do { b = polyline[index++].code - 63; result = result or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            shift = 0; result = 0
            do { b = polyline[index++].code - 63; result = result or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            poly.add(GeoPoint(lat.toDouble() / 1E5, lng.toDouble() / 1E5))
        }
        Pair(poly, etaStr)
    } catch(e: Exception) { Pair(emptyList(), "--") }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverTripScreen(orderId: String, pickupLat: Double, pickupLon: Double, dropLat: Double, dropLon: Double, onFinishTrip: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()
    val sharedPref = remember { context.getSharedPreferences("GroWiseSession", Context.MODE_PRIVATE) }
    val driverEmail = sharedPref.getString("USER_EMAIL", "") ?: ""
    val driverName = sharedPref.getString("U_FULL_NAME", "Driver") ?: "Driver"

    val isPool = orderId.startsWith("POOL")
    val collectionName = "orders"

    var orderData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var currentStatus by remember { mutableStateOf("PENDING_DRIVER") }

    var dbPickupOtp by remember { mutableStateOf("") }
    var dbDropOtp by remember { mutableStateOf("") }

    var farmerOtpInput by remember { mutableStateOf("") }
    var userOtpInput by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    var driverLat by remember { mutableDoubleStateOf(0.0) }
    var driverLon by remember { mutableDoubleStateOf(0.0) }
    var routePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var liveEta by remember { mutableStateOf("--") }
    val view = LocalView.current

    var timerRunning by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableIntStateOf(600) }
    var hasCalled by remember { mutableStateOf(false) }

    var phoneMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(Unit) { Configuration.getInstance().userAgentValue = context.packageName }

    LaunchedEffect(orderData) {
        orderData?.let { data ->
            val emailsToFetch = listOfNotNull(
                data["farmerEmail"] as? String,
                data["userEmail"] as? String,
                data["hostEmail"] as? String,
                data["coLoaderEmail"] as? String
            ).distinct()

            val newPhoneMap = mutableMapOf<String, String>()
            for (email in emailsToFetch) {
                if (email.isNotEmpty()) {
                    try {
                        val snap = db.collection("users").document(email).get().await()
                        newPhoneMap[email] = snap.getString("phone") ?: ""
                    } catch (e: Exception) {}
                }
            }
            phoneMap = newPhoneMap
        }
    }

    LaunchedEffect(orderId) {
        db.collection(collectionName).document(orderId).addSnapshotListener { snap, _ ->
            if (snap != null && snap.exists()) {
                val data = snap.data
                orderData = data
                currentStatus = data?.get("status") as? String ?: "PENDING_DRIVER"

                dbPickupOtp = data?.get("pickupOtp") as? String ?: ""
                dbDropOtp = data?.get("dropOtp") as? String ?: ""

                if (currentStatus == "DELIVERED") {
                    Toast.makeText(context, "Trip Finished!", Toast.LENGTH_LONG).show(); onFinishTrip()
                } else if (currentStatus.contains("CANCELLED")) {
                    Toast.makeText(context, "Trip Cancelled due to timeout.", Toast.LENGTH_LONG).show(); onFinishTrip()
                }
            }
        }
    }

    LaunchedEffect(timerRunning) {
        if (timerRunning) {
            if (isPool) {
                while (currentStatus != "DELIVERED") {
                    val dispatchTs = orderData?.get("dispatchTimestamp") as? Long ?: 0L
                    val currentTs = System.currentTimeMillis()
                    val diff = (dispatchTs - currentTs) / 1000
                    timeLeft = if (diff > 0) diff.toInt() else 0
                    if (timeLeft <= 0) break
                    delay(1000)
                }
            } else {
                while (timeLeft > 0 && currentStatus != "DELIVERED") {
                    delay(1000); timeLeft--
                }
            }

            if (timeLeft <= 0 && (currentStatus == "WAITING_AT_PICKUP" || currentStatus == "WAITING_AT_PICKUP_A")) {
                val blame = if (hasCalled) "CANCELLED_FARMER_FAULT" else "CANCELLED_DRIVER_FAULT"

                val batch = db.batch()
                batch.update(db.collection(collectionName).document(orderId), "status", blame)

                if (isPool) {
                    val tFare = orderData?.get("totalPayment") as? Double ?: 0.0
                    val cFare = orderData?.get("coLoaderPayment") as? Double ?: 0.0
                    val fA_email = orderData?.get("hostEmail") as? String ?: ""
                    val fB_email = orderData?.get("coLoaderEmail") as? String

                    if (tFare > 0 && fA_email.isNotEmpty()) {
                        batch.update(db.collection("wallets").document(fA_email), "balance", com.google.firebase.firestore.FieldValue.increment(tFare))
                        batch.set(db.collection("transactions").document(), mapOf("email" to fA_email, "type" to "ESCROW_REFUND", "title" to "Refund for Expired Pool", "amount" to tFare, "isCredit" to true, "timestamp" to System.currentTimeMillis()))
                    }
                    if (cFare > 0 && fB_email != null) {
                        batch.update(db.collection("wallets").document(fB_email), "balance", com.google.firebase.firestore.FieldValue.increment(cFare))
                        batch.set(db.collection("transactions").document(), mapOf("email" to fB_email, "type" to "ESCROW_REFUND", "title" to "Refund for Expired Pool", "amount" to cFare, "isCredit" to true, "timestamp" to System.currentTimeMillis()))
                    }
                }
                batch.commit().addOnSuccessListener {
                    Toast.makeText(context, "Order Cancelled due to timeout.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms.entries.all { it.value }) {
            try {
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
                val locationCallback = object : LocationCallback() {
                    override fun onLocationResult(res: LocationResult) {
                        res.lastLocation?.let { loc ->
                            driverLat = loc.latitude; driverLon = loc.longitude
                            if (currentStatus != "PENDING_DRIVER" && currentStatus != "DELIVERED") {
                                db.collection(collectionName).document(orderId).update("driverLat", driverLat, "driverLon", driverLon)
                            }
                        }
                    }
                }
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
                if (!isPool) {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { l ->
                        if (l != null) {
                            coroutineScope.launch {
                                val routeData = getOsrmRoute(GeoPoint(l.latitude, l.longitude), GeoPoint(pickupLat, pickupLon))
                                routePoints = routeData.first
                                liveEta = routeData.second
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
                Toast.makeText(context, "Location Services Error. Please verify Google Play Services.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    LaunchedEffect(Unit) { locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }

    Scaffold(
        containerColor = PeachBackground,
        topBar = { TopAppBar(title = { Text("Active Delivery", fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = PeachBackground)) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                AndroidView(
                    factory = { ctx -> MapView(ctx).apply { setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); controller.setZoom(16.0) } },
                    update = { mapView ->
                        mapView.overlays.clear()
                        if (routePoints.isNotEmpty()) {
                            val line = Polyline().apply { setPoints(routePoints); outlinePaint.color = android.graphics.Color.BLUE; outlinePaint.strokeWidth = 10f }
                            mapView.overlays.add(line)
                        }

                        val vehicleStr = (orderData?.get("vehicleType") as? String ?: "Mini-Truck").lowercase()
                        val driverIconRes = when {
                            vehicleStr.contains("two") || vehicleStr.contains("bike") -> com.simats.growise.R.drawable.ic_3d_bike
                            vehicleStr.contains("three") || vehicleStr.contains("auto") -> com.simats.growise.R.drawable.ic_3d_auto
                            vehicleStr.contains("lorry") || vehicleStr.contains("heavy") -> com.simats.growise.R.drawable.ic_3d_lorry
                            else -> com.simats.growise.R.drawable.ic_3d_minitruck
                        }

                        if (isPool && orderData != null) {
                            val pA_Lat = orderData!!["pickupLat"] as? Double ?: 0.0
                            val pA_Lon = orderData!!["pickupLon"] as? Double ?: 0.0
                            val dA_Lat = orderData!!["dropLat"] as? Double ?: 0.0
                            val dA_Lon = orderData!!["dropLon"] as? Double ?: 0.0

                            if (pA_Lat != 0.0) mapView.overlays.add(Marker(mapView).apply { position = GeoPoint(pA_Lat, pA_Lon); title = "Host Pickup"; icon = context.getDrawable(com.simats.growise.R.drawable.ic_3d_farmer) })
                            if (dA_Lat != 0.0) mapView.overlays.add(Marker(mapView).apply { position = GeoPoint(dA_Lat, dA_Lon); title = "Host Drop"; icon = context.getDrawable(com.simats.growise.R.drawable.ic_3d_home) })

                            if (orderData!!["coLoaderEmail"] != null) {
                                val pB_Lat = orderData!!["coLoaderPickupLat"] as? Double ?: 0.0
                                val pB_Lon = orderData!!["coLoaderPickupLon"] as? Double ?: 0.0
                                val dB_Lat = orderData!!["coLoaderDropLat"] as? Double ?: 0.0
                                val dB_Lon = orderData!!["coLoaderDropLon"] as? Double ?: 0.0
                                if (pB_Lat != 0.0) mapView.overlays.add(Marker(mapView).apply { position = GeoPoint(pB_Lat, pB_Lon); title = "Co-Loader Pickup"; icon = context.getDrawable(com.simats.growise.R.drawable.ic_3d_farmer_b) })
                                if (dB_Lat != 0.0) mapView.overlays.add(Marker(mapView).apply { position = GeoPoint(dB_Lat, dB_Lon); title = "Co-Loader Drop"; icon = context.getDrawable(com.simats.growise.R.drawable.ic_3d_home_b) })
                            }
                        } else {
                            if (pickupLat != 0.0) {
                                val pm = Marker(mapView).apply { position = GeoPoint(pickupLat, pickupLon); title = "Pickup"; icon = context.getDrawable(com.simats.growise.R.drawable.ic_3d_farmer) }
                                mapView.overlays.add(pm)
                            }
                            if (dropLat != 0.0) {
                                val dm = Marker(mapView).apply { position = GeoPoint(dropLat, dropLon); title = "Drop"; icon = context.getDrawable(com.simats.growise.R.drawable.ic_3d_home) }
                                mapView.overlays.add(dm)
                            }
                        }

                        if (driverLat != 0.0) {
                            val drm = Marker(mapView).apply { position = GeoPoint(driverLat, driverLon); title = "You"; icon = context.getDrawable(driverIconRes) }
                            mapView.overlays.add(drm)
                            mapView.controller.setCenter(GeoPoint(driverLat, driverLon))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Card(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(16.dp)) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {

                    val tFare = orderData?.get(if (isPool) "totalPayment" else "transportFare") as? Double ?: 0.0
                    val cFare = orderData?.get("coLoaderPayment") as? Double ?: 0.0
                    val totalEarnings = tFare + cFare

                    val isTargetCoLoaderTop = currentStatus.endsWith("_B")
                    val displayCropName = if (isPool && isTargetCoLoaderTop) {
                        orderData?.get("coLoaderCropName") as? String ?: "Crop"
                    } else {
                        orderData?.get("cropName") as? String ?: "Crop"
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(displayCropName, fontSize = 20.sp, fontWeight = FontWeight.Black, color = TerracottaPrimary)
                        Text("Earnings: ₹${String.format("%.0f", totalEarnings)}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
                    }
                    if (timerRunning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Timeout Warning: ${String.format("%02d:%02d", timeLeft/60, timeLeft%60)}", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    LaunchedEffect(currentStatus, driverLat, driverLon) {
                        if (currentStatus.contains("EN_ROUTE") || currentStatus.contains("IN_TRANSIT")) {
                            while(true) {
                                val tLat = if (currentStatus == "EN_ROUTE_TO_PICKUP" || currentStatus == "EN_ROUTE_TO_PICKUP_A") pickupLat else if (currentStatus == "IN_TRANSIT" || currentStatus == "IN_TRANSIT_TO_DROP_A") dropLat else if (currentStatus == "EN_ROUTE_TO_PICKUP_B") orderData?.get("coLoaderPickupLat") as? Double ?: 0.0 else if (currentStatus == "IN_TRANSIT_TO_DROP_B") orderData?.get("coLoaderDropLat") as? Double ?: 0.0 else 0.0
                                val tLon = if (currentStatus == "EN_ROUTE_TO_PICKUP" || currentStatus == "EN_ROUTE_TO_PICKUP_A") pickupLon else if (currentStatus == "IN_TRANSIT" || currentStatus == "IN_TRANSIT_TO_DROP_A") dropLon else if (currentStatus == "EN_ROUTE_TO_PICKUP_B") orderData?.get("coLoaderPickupLon") as? Double ?: 0.0 else if (currentStatus == "IN_TRANSIT_TO_DROP_B") orderData?.get("coLoaderDropLon") as? Double ?: 0.0 else 0.0

                                if (tLat != 0.0 && driverLat != 0.0) {
                                    val rData = getOsrmRoute(GeoPoint(driverLat, driverLon), GeoPoint(tLat, tLon))
                                    routePoints = rData.first
                                    liveEta = rData.second
                                }
                                delay(180000L) // 3 Mins Ticker
                            }
                        }
                    }

                    if (currentStatus.contains("EN_ROUTE") || currentStatus.contains("IN_TRANSIT")) {
                        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFE8F5E9), RoundedCornerShape(12.dp)).padding(8.dp), contentAlignment = Alignment.Center) {
                            Text("Live ETA: $liveEta", fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32), fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    val hasCoLoader = isPool && orderData?.get("coLoaderEmail") != null
                    if (hasCoLoader && currentStatus != "PENDING_DRIVER" && currentStatus != "PENDING_CO_LOADER" && currentStatus != "DELIVERED") {
                        val isTargetCoLoader = currentStatus.endsWith("_B")
                        val targetEmail = if (isTargetCoLoader) orderData?.get("coLoaderEmail") as? String ?: "" else orderData?.get("hostEmail") as? String ?: ""
                        val targetRole = if (isTargetCoLoader) "Co-Loader" else "Farmer/Host"
                        val fPhoneToCall = phoneMap[targetEmail] ?: ""

                        var fName by remember { mutableStateOf(targetRole) }
                        var fImg by remember { mutableStateOf("") }

                        LaunchedEffect(targetEmail) {
                            if (targetEmail.isNotEmpty()) {
                                db.collection("users").document(targetEmail).get().addOnSuccessListener { d ->
                                    fName = d.getString("name") ?: targetRole
                                    fImg = d.getString("profileImageUrl") ?: ""
                                }
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9F5)), border = BorderStroke(1.dp, GoldenYellow.copy(alpha = 0.4f))) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (fImg.isNotEmpty()) {
                                    val safeUrl = if (fImg.startsWith("http")) fImg else "${com.simats.growise.data.network.RetrofitClient.BASE_URL}$fImg"
                                    AsyncImage(model = safeUrl, contentDescription = null, modifier = Modifier.size(50.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                } else {
                                    androidx.compose.foundation.Image(painter = androidx.compose.ui.res.painterResource(com.simats.growise.R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(50.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("$fName ($targetRole)", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TerracottaPrimary)
                                    Text(targetEmail, fontSize = 12.sp, color = Color.DarkGray)
                                    val dynamicCropName = orderData?.get(if (isTargetCoLoader) "coLoaderCropName" else "cropName") as? String ?: "Crop"
                                    Text("Crop: $dynamicCropName", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$fPhoneToCall"))) }, modifier = Modifier.background(GoldenYellow.copy(alpha=0.2f), CircleShape)) {
                                    Icon(Icons.Filled.Phone, null, tint = TerracottaPrimary)
                                }
                            }
                        }
                    }

                    when (currentStatus) {
                        "PENDING_DRIVER", "PENDING_CO_LOADER" -> {
                            Button(
                                onClick = {
                                    db.collection("users").document(driverEmail).get().addOnSuccessListener { dSnap ->
                                        val fetchedDriverId = dSnap.getString("driverId") ?: "GW-D0000"
                                        val realDriverName = dSnap.getString("name") ?: driverName
                                        val nextState = if (isPool) "EN_ROUTE_TO_PICKUP_A" else "EN_ROUTE_TO_PICKUP"
                                        db.collection(collectionName).document(orderId).update(
                                            mapOf(
                                                "driverEmail" to driverEmail,
                                                "driverName" to realDriverName,
                                                "driverId" to fetchedDriverId,
                                                "status" to nextState
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)
                            ) { Text("Start Trip", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        // ==================== OLD STANDARD LOGIC (BIKE/AUTO) ====================
                        "EN_ROUTE_TO_PICKUP" -> {
                            val results = FloatArray(1)
                            android.location.Location.distanceBetween(driverLat, driverLon, pickupLat, pickupLon, results)
                            val distance = results[0]
                            val isNear = distance <= 500f || true

                            Button(
                                onClick = {
                                    if (isNear) {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        db.collection("orders").document(orderId).update("status", "WAITING_AT_PICKUP")
                                        timerRunning = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isNear) TerracottaPrimary else Color.LightGray)
                            ) { Text(if (isNear) "I Have Arrived" else "Arriving soon... (${distance.toInt()}m)", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        "WAITING_AT_PICKUP" -> {
                            val hasCoLoaderInside = isPool && orderData?.get("coLoaderEmail") != null
                            val fPhone = phoneMap[orderData?.get("farmerEmail") as? String ?: ""] ?: ""
                            OtpFieldBox("Farmer OTP", farmerOtpInput, onValueChange = { farmerOtpInput = it }, onCallClick = {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$fPhone")))
                            }, showCallIcon = !hasCoLoaderInside)
                            Button(
                                onClick = {
                                    if (farmerOtpInput == dbPickupOtp && farmerOtpInput.isNotEmpty()) {
                                        coroutineScope.launch {
                                            db.collection("orders").document(orderId).update("status", "IN_TRANSIT")
                                            val routeData = getOsrmRoute(GeoPoint(driverLat, driverLon), GeoPoint(dropLat, dropLon))
                                            routePoints = routeData.first
                                            liveEta = routeData.second
                                            timerRunning = false; timeLeft = 600; hasCalled = false
                                        }
                                    } else Toast.makeText(context, "Invalid OTP!", Toast.LENGTH_SHORT).show()
                                }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)
                            ) { Text("Verify & Head to Drop", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                        }
                        "IN_TRANSIT" -> {
                            val results = FloatArray(1)
                            android.location.Location.distanceBetween(driverLat, driverLon, dropLat, dropLon, results)
                            val distance = results[0]
                            val isNear = distance <= 500f || true

                            Button(
                                onClick = {
                                    if (isNear) {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        db.collection("orders").document(orderId).update("status", "WAITING_AT_DROP")
                                        timerRunning = true
                                        routePoints = emptyList()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isNear) Color(0xFF1565C0) else Color.LightGray)
                            ) { Text(if (isNear) "I Have Arrived at Drop" else "Arriving soon... (${distance.toInt()}m)", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        "WAITING_AT_DROP" -> {
                            val hasCoLoaderInside = isPool && orderData?.get("coLoaderEmail") != null
                            val uPhone = phoneMap[orderData?.get("userEmail") as? String ?: ""] ?: ""
                            OtpFieldBox("Buyer OTP", userOtpInput, onValueChange = { userOtpInput = it }, onCallClick = {
                                hasCalled = true; context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$uPhone")))
                            }, showCallIcon = !hasCoLoaderInside)
                            Button(
                                onClick = {
                                    if (userOtpInput == dbDropOtp && userOtpInput.isNotEmpty()) {
                                        isProcessing = true
                                        val batch = db.batch()
                                        val orderRef = db.collection("orders").document(orderId)
                                        batch.update(orderRef, "status", "DELIVERED")

                                        val cropValue = (orderData?.get("cropValue") as? Number)?.toDouble() ?: 0.0
                                        val transportFare = (orderData?.get("transportFare") as? Number)?.toDouble() ?: 0.0
                                        val fEmail = orderData?.get("farmerEmail") as? String ?: ""
                                        val cropName = orderData?.get("cropName") as? String ?: "Crop"

                                        if (fEmail.isNotEmpty() && cropValue > 0) {
                                            val fWalletRef = db.collection("wallets").document(fEmail)
                                            batch.set(fWalletRef, mapOf<String, Any>("balance" to com.google.firebase.firestore.FieldValue.increment(cropValue)), com.google.firebase.firestore.SetOptions.merge())
                                            val fTxRef = db.collection("transactions").document()
                                            batch.set(fTxRef, mapOf<String, Any>("email" to fEmail, "title" to "Payout for $cropName", "amount" to cropValue, "isCredit" to true, "timestamp" to System.currentTimeMillis()))
                                        }

                                        if (driverEmail.isNotEmpty() && transportFare > 0) {
                                            val dWalletRef = db.collection("wallets").document(driverEmail)
                                            batch.set(dWalletRef, mapOf<String, Any>("balance" to com.google.firebase.firestore.FieldValue.increment(transportFare)), com.google.firebase.firestore.SetOptions.merge())
                                            val dTxRef = db.collection("transactions").document()
                                            batch.set(dTxRef, mapOf<String, Any>("email" to driverEmail, "type" to "PAYOUT", "title" to "Transport Fare for $cropName", "amount" to transportFare, "isCredit" to true, "timestamp" to System.currentTimeMillis()))
                                        }

                                        val uEmail = orderData?.get("userEmail") as? String ?: ""
                                        if (uEmail.isNotEmpty()) {
                                            db.collection("transactions").whereEqualTo("orderId", orderId).whereEqualTo("type", "ESCROW_LOCK").get().addOnSuccessListener { snaps ->
                                                for (doc in snaps.documents) {
                                                    batch.update(doc.reference, "type", "PAID_ORDER", "title", "Paid for $cropName Order", "isCredit", false)
                                                }
                                                batch.commit().addOnSuccessListener {
                                                    isProcessing = false
                                                    Toast.makeText(context, "Drop Completed & Payments Released!", Toast.LENGTH_LONG).show()
                                                    onFinishTrip()
                                                }.addOnFailureListener {
                                                    isProcessing = false
                                                    Toast.makeText(context, "Network Error Processing Payout", Toast.LENGTH_SHORT).show()
                                                }
                                            }.addOnFailureListener {
                                                isProcessing = false
                                                Toast.makeText(context, "Network Error fetching Escrow", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            batch.commit().addOnSuccessListener {
                                                isProcessing = false
                                                Toast.makeText(context, "Drop Completed & Payments Released!", Toast.LENGTH_LONG).show()
                                                onFinishTrip()
                                            }.addOnFailureListener {
                                                isProcessing = false
                                                Toast.makeText(context, "Network Error Processing Payout", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else Toast.makeText(context, "Invalid OTP!", Toast.LENGTH_SHORT).show()
                                }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                if (isProcessing) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                else Text("Complete Drop", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }

                        // ==================== NEW MULTI-STOP LOGIC (POOL) ====================
                        "EN_ROUTE_TO_PICKUP_A" -> {
                            val results = FloatArray(1)
                            val tLat = orderData?.get("pickupLat") as? Double ?: 0.0
                            val tLon = orderData?.get("pickupLon") as? Double ?: 0.0
                            android.location.Location.distanceBetween(driverLat, driverLon, tLat, tLon, results)
                            val isNear = results[0] <= 500f || true
                            Button(
                                onClick = {
                                    if (isNear) {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        db.collection(collectionName).document(orderId).update("status", "WAITING_AT_PICKUP_A")
                                        timerRunning = true
                                    }
                                }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isNear) TerracottaPrimary else Color.LightGray)
                            ) { Text(if (isNear) "Arrived at Host Pickup" else "Arriving... (${results[0].toInt()}m)", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        "WAITING_AT_PICKUP_A" -> {
                            val correctOtp = orderData?.get("pickupOtp_A") as? String ?: ""
                            val hasCoLoaderInside = orderData?.get("coLoaderEmail") != null
                            val hPhone = phoneMap[orderData?.get("hostEmail") as? String ?: ""] ?: ""
                            OtpFieldBox("Host Farmer OTP", farmerOtpInput, onValueChange = { farmerOtpInput = it }, onCallClick = {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$hPhone")))
                            }, showCallIcon = !hasCoLoaderInside)
                            Button(
                                onClick = {
                                    if (farmerOtpInput == correctOtp && farmerOtpInput.isNotEmpty()) {
                                        farmerOtpInput = ""
                                        timerRunning = false
                                        coroutineScope.launch {
                                            val hasCoLoaderInside = orderData?.get("coLoaderEmail") != null
                                            val nextState = if (hasCoLoaderInside) "EN_ROUTE_TO_PICKUP_B" else "IN_TRANSIT_TO_DROP_A"
                                            val nLat = orderData?.get(if(hasCoLoaderInside) "coLoaderPickupLat" else "dropLat") as? Double ?: 0.0
                                            val nLon = orderData?.get(if(hasCoLoaderInside) "coLoaderPickupLon" else "dropLon") as? Double ?: 0.0
                                            val routeData = getOsrmRoute(GeoPoint(driverLat, driverLon), GeoPoint(nLat, nLon))
                                            routePoints = routeData.first; liveEta = routeData.second
                                            db.collection(collectionName).document(orderId).update("status", nextState)
                                        }
                                    } else Toast.makeText(context, "Invalid OTP!", Toast.LENGTH_SHORT).show()
                                }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)
                            ) { Text("Verify Host Pickup", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        "EN_ROUTE_TO_PICKUP_B" -> {
                            val results = FloatArray(1)
                            val tLat = orderData?.get("coLoaderPickupLat") as? Double ?: 0.0
                            val tLon = orderData?.get("coLoaderPickupLon") as? Double ?: 0.0
                            android.location.Location.distanceBetween(driverLat, driverLon, tLat, tLon, results)
                            val isNear = results[0] <= 500f || true
                            Button(
                                onClick = {
                                    if (isNear) { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); db.collection(collectionName).document(orderId).update("status", "WAITING_AT_PICKUP_B") }
                                }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isNear) TerracottaPrimary else Color.LightGray)
                            ) { Text(if (isNear) "Arrived at Co-Loader Pickup" else "Arriving... (${results[0].toInt()}m)", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        "WAITING_AT_PICKUP_B" -> {
                            val correctOtp = orderData?.get("pickupOtp_B") as? String ?: ""
                            val hasCoLoaderInside = orderData?.get("coLoaderEmail") != null
                            val cPhone = phoneMap[orderData?.get("coLoaderEmail") as? String ?: ""] ?: ""
                            OtpFieldBox("Co-Loader Farmer OTP", farmerOtpInput, onValueChange = { farmerOtpInput = it }, onCallClick = {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cPhone")))
                            }, showCallIcon = !hasCoLoaderInside)
                            Button(
                                onClick = {
                                    if (farmerOtpInput == correctOtp && farmerOtpInput.isNotEmpty()) {
                                        farmerOtpInput = ""
                                        coroutineScope.launch {
                                            val nLat = orderData?.get("dropLat") as? Double ?: 0.0
                                            val nLon = orderData?.get("dropLon") as? Double ?: 0.0
                                            val routeData = getOsrmRoute(GeoPoint(driverLat, driverLon), GeoPoint(nLat, nLon))
                                            routePoints = routeData.first; liveEta = routeData.second
                                            db.collection(collectionName).document(orderId).update("status", "IN_TRANSIT_TO_DROP_A")
                                        }
                                    } else Toast.makeText(context, "Invalid OTP!", Toast.LENGTH_SHORT).show()
                                }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)
                            ) { Text("Verify Co-Loader Pickup", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        "IN_TRANSIT_TO_DROP_A" -> {
                            val results = FloatArray(1)
                            val tLat = orderData?.get("dropLat") as? Double ?: 0.0
                            val tLon = orderData?.get("dropLon") as? Double ?: 0.0
                            android.location.Location.distanceBetween(driverLat, driverLon, tLat, tLon, results)
                            val isNear = results[0] <= 500f || true
                            Button(
                                onClick = {
                                    if (isNear) { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); db.collection(collectionName).document(orderId).update("status", "WAITING_AT_DROP_A"); routePoints = emptyList() }
                                }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isNear) Color(0xFF1565C0) else Color.LightGray)
                            ) { Text(if (isNear) "Arrived at Host Drop" else "Arriving... (${results[0].toInt()}m)", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        "WAITING_AT_DROP_A" -> {
                            val correctOtp = orderData?.get("dropOtp_A") as? String ?: ""
                            val hasCoLoaderInside = orderData?.get("coLoaderEmail") != null
                            val hPhone = phoneMap[orderData?.get("hostEmail") as? String ?: ""] ?: ""
                            OtpFieldBox("Host Drop OTP", userOtpInput, onValueChange = { userOtpInput = it }, onCallClick = {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$hPhone")))
                            }, showCallIcon = !hasCoLoaderInside)
                            Button(
                                onClick = {
                                    if (userOtpInput == correctOtp && userOtpInput.isNotEmpty()) {
                                        userOtpInput = ""
                                        val hasCoLoaderInside = orderData?.get("coLoaderEmail") != null
                                        if (hasCoLoaderInside) {
                                            coroutineScope.launch {
                                                val nLat = orderData?.get("coLoaderDropLat") as? Double ?: 0.0
                                                val nLon = orderData?.get("coLoaderDropLon") as? Double ?: 0.0
                                                val routeData = getOsrmRoute(GeoPoint(driverLat, driverLon), GeoPoint(nLat, nLon))
                                                routePoints = routeData.first; liveEta = routeData.second
                                                db.collection(collectionName).document(orderId).update("status", "IN_TRANSIT_TO_DROP_B")
                                            }
                                        } else {
                                            executePoolPayout(db, orderId, orderData, driverEmail, context, onFinishTrip)
                                        }
                                    } else Toast.makeText(context, "Invalid OTP!", Toast.LENGTH_SHORT).show()
                                }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) { Text("Verify Host Drop", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        "IN_TRANSIT_TO_DROP_B" -> {
                            val results = FloatArray(1)
                            val tLat = orderData?.get("coLoaderDropLat") as? Double ?: 0.0
                            val tLon = orderData?.get("coLoaderDropLon") as? Double ?: 0.0
                            android.location.Location.distanceBetween(driverLat, driverLon, tLat, tLon, results)
                            val isNear = results[0] <= 500f || true
                            Button(
                                onClick = {
                                    if (isNear) { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); db.collection(collectionName).document(orderId).update("status", "WAITING_AT_DROP_B"); routePoints = emptyList() }
                                }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isNear) Color(0xFF1565C0) else Color.LightGray)
                            ) { Text(if (isNear) "Arrived at Co-Loader Drop" else "Arriving... (${results[0].toInt()}m)", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        "WAITING_AT_DROP_B" -> {
                            val correctOtp = orderData?.get("dropOtp_B") as? String ?: ""
                            val hasCoLoaderInside = orderData?.get("coLoaderEmail") != null
                            val cPhone = phoneMap[orderData?.get("coLoaderEmail") as? String ?: ""] ?: ""
                            OtpFieldBox("Co-Loader Drop OTP", userOtpInput, onValueChange = { userOtpInput = it }, onCallClick = {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cPhone")))
                            }, showCallIcon = !hasCoLoaderInside)
                            Button(
                                onClick = {
                                    if (userOtpInput == correctOtp && userOtpInput.isNotEmpty()) {
                                        executePoolPayout(db, orderId, orderData, driverEmail, context, onFinishTrip)
                                    } else Toast.makeText(context, "Invalid OTP!", Toast.LENGTH_SHORT).show()
                                }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) { Text("Verify Co-Loader Drop & Finish", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OtpFieldBox(label: String, value: String, onValueChange: (String) -> Unit, onCallClick: () -> Unit, showCallIcon: Boolean = true) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9F5)), border = BorderStroke(1.dp, GoldenYellow.copy(alpha = 0.4f))) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(TerracottaPrimary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Storefront, null, tint = TerracottaPrimary) }
            Spacer(modifier = Modifier.width(16.dp))
            OutlinedTextField(
                value = value, onValueChange = { if(it.length <= 4) onValueChange(it) },
                placeholder = { Text(label, color = Color.Gray) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TerracottaPrimary, unfocusedBorderColor = Color.LightGray)
            )
            if (showCallIcon) {
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(
                    onClick = onCallClick,
                    modifier = Modifier.size(48.dp).background(GoldenYellow.copy(alpha=0.1f), RoundedCornerShape(12.dp))
                ) { Icon(Icons.Filled.Call, tint = GoldenYellow, contentDescription = "Call") }
            }
        }
    }
}

fun executePoolPayout(db: FirebaseFirestore, orderId: String, orderData: Map<String, Any>?, driverEmail: String, context: Context, onFinish: () -> Unit) {
    val batch = db.batch()
    batch.update(db.collection("orders").document(orderId), "status", "DELIVERED")

    val tFare = orderData?.get("totalPayment") as? Double ?: 0.0
    val cFare = orderData?.get("coLoaderPayment") as? Double ?: 0.0
    val totalEarnings = tFare + cFare

    if (driverEmail.isNotEmpty() && totalEarnings > 0) {
        val dWalletRef = db.collection("wallets").document(driverEmail)
        batch.set(dWalletRef, mapOf<String, Any>("balance" to com.google.firebase.firestore.FieldValue.increment(totalEarnings)), com.google.firebase.firestore.SetOptions.merge())
        val dTxRef = db.collection("transactions").document()
        batch.set(dTxRef, mapOf<String, Any>("email" to driverEmail, "type" to "DRIVER_PAYOUT", "title" to "Logistics Earnings (Pool Delivery)", "amount" to totalEarnings, "isCredit" to true, "timestamp" to System.currentTimeMillis()))
    }

    db.collection("transactions").whereEqualTo("orderId", orderId).whereEqualTo("type", "ESCROW_LOCK").get().addOnSuccessListener { snaps ->
        for (doc in snaps.documents) {
            batch.update(doc.reference, "type", "PAID_ORDER", "title", "Paid to Logistics Driver", "isCredit", false)
        }
        batch.commit().addOnSuccessListener {
            Toast.makeText(context, "All Drops Completed & Payments Released!", Toast.LENGTH_LONG).show()
            onFinish()
        }.addOnFailureListener {
            Toast.makeText(context, "Network Error Processing Payout", Toast.LENGTH_SHORT).show()
        }
    }.addOnFailureListener {
        batch.commit().addOnSuccessListener {
            Toast.makeText(context, "All Drops Completed & Payments Released!", Toast.LENGTH_LONG).show()
            onFinish()
        }
    }
}