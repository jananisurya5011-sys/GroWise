package com.simats.growise.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.window.Dialog
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.simats.growise.R
import com.simats.growise.ui.theme.GoldenYellow
import com.simats.growise.ui.theme.PeachBackground
import com.simats.growise.ui.theme.TerracottaPrimary
import com.simats.growise.data.network.RetrofitClient
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

suspend fun getOsrmRouteShared(start: GeoPoint, end: GeoPoint): Pair<List<GeoPoint>, String> = withContext(Dispatchers.IO) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedTrackScreen(currentUserEmail: String, role: String, navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var activeOrder by remember { mutableStateOf<Map<String, Any>?>(null) }
    var driverPhone by remember { mutableStateOf("") }
    var driverEmail by remember { mutableStateOf("") }
    var driverProfilePic by remember { mutableStateOf("") }
    var routePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var liveEta by remember { mutableStateOf("--") }
    var mapVisible by remember { mutableStateOf(false) }

    var ngoOtpInput by remember { mutableStateOf("") }
    var activeDonationsCount by remember { mutableIntStateOf(0) }
    var showCelebration by remember { mutableStateOf(false) }

    val isFarmer = role == "farmer"
    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(Unit) { Configuration.getInstance().userAgentValue = context.packageName }

    LaunchedEffect(currentUserEmail) {
        val queryField = if (isFarmer) "farmerEmail" else "userEmail"

        db.collection("orders").whereEqualTo(queryField, currentUserEmail)
            .whereIn("status", listOf("PENDING_DRIVER", "EN_ROUTE_TO_PICKUP", "WAITING_AT_PICKUP", "IN_TRANSIT", "WAITING_AT_DROP", "NGO_PENDING_PICKUP", "PICKUP_PENDING", "PENDING_FARMER_APPROVAL", "PENDING_NGO_LOGISTICS_SELECTION", "READY_FOR_PICKUP"))
            .addSnapshotListener { snap, _ ->
                if (snap != null && !snap.isEmpty) {
                    val allDocs = snap.documents.mapNotNull { it.data }
                    val standardOrders = allDocs.filter { doc ->
                        val isDon = (doc["orderId"] as? String)?.startsWith("GW-DON-") == true || doc["isDonation"] == true
                        val vType = doc["vehicleType"] as? String ?: ""
                        val isSelf = vType.equals("Self Pickup", true) || vType.equals("Self", true) || vType.equals("Self-Service", true)
                        
                        !isDon || !isSelf
                    }
                    if (isFarmer) {
                        val activeDons = allDocs.filter { doc ->
                            val isDon = (doc["orderId"] as? String)?.startsWith("GW-DON-") == true || doc["isDonation"] == true
                            val vType = doc["vehicleType"] as? String ?: ""
                            val isSelf = vType.equals("Self Pickup", true) || vType.equals("Self", true) || vType.equals("Self-Service", true)
                            val status = doc["status"] as? String ?: ""
                            isDon && isSelf && status !in listOf("COMPLETED", "DECLINED", "REJECTED", "WITHDRAWN", "CANCELLED")
                        }
                        activeDonationsCount = activeDons.size
                    } else {
                        activeDonationsCount = 0
                    }

                    val order = standardOrders.maxByOrNull { (it["timestamp"] as? Number)?.toLong() ?: 0L }
                    activeOrder = order

                    val email = order?.get("driverEmail") as? String
                    if (!email.isNullOrEmpty()) {
                        driverEmail = email
                        coroutineScope.launch {
                            try {
                                val res = RetrofitClient.apiService.retrieveProfileFields(email)
                                if (res.isSuccessful) {
                                    driverPhone = res.body()?.phone ?: ""
                                    driverProfilePic = res.body()?.profile_image_url ?: ""
                                }
                            } catch (e: Exception) {}
                        }
                    }
                    if (order?.get("status") != "NGO_PENDING_PICKUP") {
                        coroutineScope.launch {
                            val pLat = (order?.get("pickupLat") as? Double) ?: 0.0; val pLon = (order?.get("pickupLon") as? Double) ?: 0.0
                            val dLat = (order?.get("dropLat") as? Double) ?: 0.0; val dLon = (order?.get("dropLon") as? Double) ?: 0.0
                            val drLat = (order?.get("driverLat") as? Double) ?: 0.0; val drLon = (order?.get("driverLon") as? Double) ?: 0.0
                            val currentStart = if (drLat != 0.0) GeoPoint(drLat, drLon) else GeoPoint(pLat, pLon)
                            val currentEnd = if (order?.get("status") == "IN_TRANSIT" || order?.get("status") == "WAITING_AT_DROP") GeoPoint(dLat, dLon) else GeoPoint(pLat, pLon)
                            val routeData = getOsrmRouteShared(currentStart, currentEnd)
                            routePoints = routeData.first
                            liveEta = routeData.second
                        }
                    }
                } else activeOrder = null
            }
    }

    if (showCelebration) {
        Dialog(onDismissRequest = {}) {
            val scale by rememberInfiniteTransition().animateFloat(initialValue = 0.8f, targetValue = 1.2f, animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse))
            Column(modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(24.dp)).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Success", tint = Color(0xFF2E7D32), modifier = Modifier.size(100.dp).scale(scale))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Rescue Verified!", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary)
                Text("Stock deducted and handover complete.", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
            }
        }
        LaunchedEffect(Unit) {
            delay(2500)
            showCelebration = false
        }
    }

    if (activeOrder != null) {
        val orderId = activeOrder!!["orderId"] as? String ?: ""
        val vehicleType = activeOrder!!["vehicleType"] as? String ?: ""
        val isDonation = orderId.startsWith("GW-DON-") || activeOrder!!["isDonation"] == true
        val isSelfPickup = vehicleType.equals("Self Pickup", ignoreCase = true) || vehicleType.equals("Self", ignoreCase = true) || vehicleType.equals("Self-Service", ignoreCase = true)
        
        if (isDonation && isSelfPickup) {
            com.simats.growise.common.SelfPickupTrackScreen(orderId = orderId, navController = navController)
            return
        }
    }

    Scaffold(
        containerColor = PeachBackground,
        topBar = { TopAppBar(title = { Text("Order Tracking", fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = PeachBackground)) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(modifier = Modifier.weight(1f).height(90.dp).clickable { navController.navigate("wallet") }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0)), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "Wallet", tint = Color(0xFF2E7D32), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Wallet", fontWeight = FontWeight.Bold, color = Color.DarkGray)
                    }
                }
                Card(modifier = Modifier.weight(1f).height(90.dp).clickable { navController.navigate("history") }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFE0E0E0)), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.ReceiptLong, contentDescription = "History", tint = TerracottaPrimary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Order History", fontWeight = FontWeight.Bold, color = Color.DarkGray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isFarmer) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable { navController.navigate("crop_pool") }.shadow(8.dp, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)),
                    border = BorderStroke(2.dp, Color(0xFFFFD700)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(50.dp).background(Color(0xFFFFECB3), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.LocalShipping, contentDescription = "Pool", tint = Color(0xFFF57F17), modifier = Modifier.size(30.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Join Logistics Pool", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFFF57F17))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Share transport costs with nearby farmers instantly.", color = Color.DarkGray, fontSize = 13.sp)
                        }
                        Icon(Icons.Filled.ArrowForwardIos, contentDescription = "Go", tint = Color(0xFFF57F17), modifier = Modifier.size(20.dp))
                    }
                }
                
                // NEW DONATION TRACKING CARD (FARMER ONLY)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .shadow(if (activeDonationsCount > 0) 8.dp else 2.dp, RoundedCornerShape(16.dp))
                        .clickable(enabled = activeDonationsCount > 0) {
                            navController.navigate("donation_tracking")
                        },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.5.dp, GoldenYellow),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(50.dp).background(PeachBackground, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.VolunteerActivism, contentDescription = "Donations", tint = TerracottaPrimary, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Donation Tracking", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = TerracottaPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            if (activeDonationsCount > 0) {
                                Text("$activeDonationsCount Active Donations", color = Color(0xFF2E7D32), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Tap to manage", color = Color.Gray, fontSize = 12.sp)
                            } else {
                                Text("No Active Donations", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("Your donation requests will appear here.", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }
                        if (activeDonationsCount > 0) {
                            Icon(Icons.Filled.ArrowForwardIos, contentDescription = "Go", tint = TerracottaPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Text("Active Orders", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TerracottaPrimary, modifier = Modifier.padding(bottom = 16.dp))

            if (activeOrder == null) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Card(modifier = Modifier.fillMaxWidth().height(220.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, GoldenYellow), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.size(80.dp).background(PeachBackground, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = TerracottaPrimary, modifier = Modifier.size(40.dp))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No Active Orders", fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary, fontSize = 18.sp)
                            Text("Your current live trips will track here.", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                val status = activeOrder!!["status"] as? String ?: "PENDING_DRIVER"
                val orderId = activeOrder!!["orderId"]?.toString() ?: ""

                if (status == "PENDING_DRIVER") {
                    val vehicle = activeOrder!!["vehicleType"] as? String ?: "Any"
                    val dropOtp = activeOrder!!["dropOtp"] as? String ?: "----"
                    val pickupOtp = activeOrder!!["pickupOtp"] as? String ?: "----"

                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(2.dp, GoldenYellow), elevation = CardDefaults.cardElevation(12.dp)) {
                        Column(modifier = Modifier.background(brush = androidx.compose.ui.graphics.Brush.verticalGradient(colors = listOf(Color.White, PeachBackground.copy(alpha = 0.5f)))).padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.size(72.dp).background(GoldenYellow.copy(alpha=0.2f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.HourglassEmpty, contentDescription = null, tint = TerracottaPrimary, modifier = Modifier.size(36.dp))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Pending Driver", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Color.Black)
                            Text("Vehicle Type: $vehicle", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(24.dp))

                            val otpToShow = if (isFarmer) pickupOtp else dropOtp
                            val titleToShow = if (isFarmer) "YOUR FARM PICKUP OTP" else "YOUR SECURE DROP OTP"
                            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF9F5), RoundedCornerShape(12.dp)).border(1.dp, GoldenYellow.copy(alpha=0.4f), RoundedCornerShape(12.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(titleToShow, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TerracottaPrimary)
                                    Text(otpToShow, fontSize = 32.sp, fontWeight = FontWeight.Black, color = TerracottaPrimary, letterSpacing = 8.sp)
                                }
                            }
                        }
                    }
                } else {
                    val driverNameDisplay = activeOrder!!["driverName"] as? String ?: "Assigning..."
                    val driverIdStr = activeOrder!!["driverId"] as? String ?: "GW-D0000"
                    val vehicle = activeOrder!!["vehicleType"] as? String ?: "Any"
                    val dropOtp = activeOrder!!["dropOtp"] as? String ?: "----"
                    val pickupOtp = activeOrder!!["pickupOtp"] as? String ?: "----"
                    var timerRunning by remember { mutableStateOf(false) }
                    var timeLeft by remember { mutableIntStateOf(600) }

                    LaunchedEffect(status) {
                        if (isFarmer && status == "WAITING_AT_PICKUP") timerRunning = true
                        else if (!isFarmer && status == "WAITING_AT_DROP") timerRunning = true
                        else timerRunning = false
                    }

                    LaunchedEffect(timerRunning) {
                        if (timerRunning) {
                            while(timeLeft > 0 && timerRunning) { delay(1000); timeLeft-- }
                            if (timeLeft == 0 && orderId.isNotEmpty()) {
                                db.collection("orders").document(orderId).update("flags", FieldValue.increment(1))
                            }
                        }
                    }

                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(12.dp)) {
                        Column(modifier = Modifier.background(brush = androidx.compose.ui.graphics.Brush.verticalGradient(colors = listOf(Color.White, PeachBackground.copy(alpha = 0.3f)))).padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(56.dp).clip(CircleShape).border(2.dp, GoldenYellow, CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                                    if (driverProfilePic.isNotEmpty()) {
                                        AsyncImage(
                                            model = "${RetrofitClient.BASE_URL.removeSuffix("/")}/${driverProfilePic.removePrefix("/")}",
                                            contentDescription = null,
                                            modifier = Modifier.size(56.dp).clip(CircleShape),
                                            contentScale = ContentScale.Crop,
                                            error = painterResource(id = R.drawable.app_logo),
                                            placeholder = painterResource(id = R.drawable.app_logo)
                                        )
                                    } else {
                                        Image(
                                            painter = painterResource(id = R.drawable.app_logo),
                                            contentDescription = "Driver Logo",
                                            modifier = Modifier.size(40.dp).clip(CircleShape)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(driverNameDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.Black)
                                    Text("ID: $driverIdStr", color = Color(0xFF1976D2), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = { if(driverPhone.isNotEmpty()) context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$driverPhone"))) }, modifier = Modifier.size(48.dp).background(Color(0xFFE8F5E9), CircleShape)) {
                                    Icon(Icons.Filled.Phone, contentDescription = "Call", tint = Color(0xFF2E7D32))
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))

                            if (status == "IN_TRANSIT" || status == "WAITING_AT_DROP") {
                                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFE8F5E9), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF81C784), RoundedCornerShape(12.dp)).padding(12.dp), contentAlignment = Alignment.Center) {
                                    Text("Pickup done! Coming for drop 🚚", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            Button(onClick = { mapVisible = !mapVisible }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)) {
                                Text(if(mapVisible) "Hide Live Map" else "View Order Details / Map", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            AnimatedVisibility(visible = mapVisible) {
                                val pLat = (activeOrder!!["pickupLat"] as? Double) ?: 0.0; val pLon = (activeOrder!!["pickupLon"] as? Double) ?: 0.0
                                val dLat = (activeOrder!!["dropLat"] as? Double) ?: 0.0; val dLon = (activeOrder!!["dropLon"] as? Double) ?: 0.0
                                val drLat = (activeOrder!!["driverLat"] as? Double) ?: 0.0; val drLon = (activeOrder!!["driverLon"] as? Double) ?: 0.0
                                val animLat by androidx.compose.animation.core.animateFloatAsState(targetValue = drLat.toFloat(), animationSpec = androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.LinearEasing))
                                val animLon by androidx.compose.animation.core.animateFloatAsState(targetValue = drLon.toFloat(), animationSpec = androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.LinearEasing))
                                val isWaiting = status == "WAITING_AT_PICKUP" || status == "WAITING_AT_DROP"
                                val infinitePulse = androidx.compose.animation.core.rememberInfiniteTransition()
                                val pulseAlpha by infinitePulse.animateFloat(initialValue = 0.3f, targetValue = 1f, animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(800), androidx.compose.animation.core.RepeatMode.Reverse))
                                val mapBorderColor = if (isWaiting) Color.Red.copy(alpha = pulseAlpha) else GoldenYellow

                                Box(modifier = Modifier.fillMaxWidth().height(220.dp).padding(bottom = 16.dp).clip(RoundedCornerShape(16.dp)).border(if (isWaiting) 4.dp else 2.dp, mapBorderColor, RoundedCornerShape(16.dp))) {
                                    AndroidView(
                                        factory = { ctx -> MapView(ctx).apply { setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); controller.setZoom(16.0) } },
                                        update = { mapView ->
                                            mapView.overlays.clear()
                                            if (routePoints.isNotEmpty()) mapView.overlays.add(Polyline().apply { setPoints(routePoints); outlinePaint.color = android.graphics.Color.BLUE; outlinePaint.strokeWidth = 8f })
                                            val driverIconRes = when {
                                                vehicle.lowercase().contains("two") || vehicle.lowercase().contains("bike") -> com.simats.growise.R.drawable.ic_3d_bike
                                                vehicle.lowercase().contains("three") || vehicle.lowercase().contains("auto") -> com.simats.growise.R.drawable.ic_3d_auto
                                                vehicle.lowercase().contains("lorry") || vehicle.lowercase().contains("heavy") -> com.simats.growise.R.drawable.ic_3d_lorry
                                                else -> com.simats.growise.R.drawable.ic_3d_minitruck
                                            }
                                            if(pLat != 0.0) mapView.overlays.add(Marker(mapView).apply { position = GeoPoint(pLat, pLon); title = "Pickup"; icon = context.getDrawable(com.simats.growise.R.drawable.ic_3d_farmer) })
                                            if(dLat != 0.0) mapView.overlays.add(Marker(mapView).apply { position = GeoPoint(dLat, dLon); title = "Drop"; icon = context.getDrawable(com.simats.growise.R.drawable.ic_3d_home) })
                                            if(drLat != 0.0) {
                                                val driverMarker = Marker(mapView).apply { position = GeoPoint(animLat.toDouble(), animLon.toDouble()); title = "Driver"; icon = context.getDrawable(driverIconRes) }
                                                mapView.overlays.add(driverMarker)
                                                mapView.controller.animateTo(GeoPoint(animLat.toDouble(), animLon.toDouble()))
                                            } else if (pLat != 0.0) mapView.controller.animateTo(GeoPoint(pLat, pLon))
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            val premiumStatusText = when (status) {
                                "EN_ROUTE_TO_PICKUP", "WAITING_AT_PICKUP" -> "Driver Assigned & Arriving 🚚"
                                "IN_TRANSIT", "WAITING_AT_DROP" -> "Order Picked Up! En Route 🛣️"
                                "DELIVERED" -> "Order Completed ✅"
                                else -> "Tracking Order..."
                            }

                            if (status != "IN_TRANSIT" && status != "WAITING_AT_DROP") {
                                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF1F8E9), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF81C784), RoundedCornerShape(12.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(premiumStatusText, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32), fontSize = 16.sp, textAlign = TextAlign.Center)
                                        if (status == "EN_ROUTE_TO_PICKUP" || status == "IN_TRANSIT") {
                                            Text("Live ETA: $liveEta", color = Color.DarkGray, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            val otpToShow = if (isFarmer) pickupOtp else dropOtp
                            val titleToShow = if (isFarmer) "YOUR FARM PICKUP OTP" else "YOUR SECURE DROP OTP"

                            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF9F5), RoundedCornerShape(12.dp)).border(1.dp, GoldenYellow.copy(alpha=0.4f), RoundedCornerShape(12.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(titleToShow, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TerracottaPrimary)
                                    Text(otpToShow, fontSize = 32.sp, fontWeight = FontWeight.Black, color = TerracottaPrimary, letterSpacing = 8.sp)
                                    if (timerRunning) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Driver is waiting: ${String.format("%02d:%02d", timeLeft/60, timeLeft%60)}", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}