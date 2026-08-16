package com.simats.growise.common

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.simats.growise.R
import com.simats.growise.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

val Terracotta = Color(0xFF7C3D12)
val EarthBrown = Color(0xFF4A3B32)
val Cream = Color(0xFFFDFBF7)
val WarmWhite = Color(0xFFFFFFFF)
val SuccessGreen = Color(0xFF2E7D32)
val GoldBorder = Color(0x59D4AF37) // 35% opacity gold
val GoldShadow = Color(0x1AD4AF37) // 10% opacity gold for shadow emulation

@Composable
fun PremiumCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = WarmWhite),
        border = BorderStroke(1.dp, GoldBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfPickupTrackScreen(orderId: String, navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var activeOrder by remember { mutableStateOf<Map<String, Any>?>(null) }
    var ngoProfilePic by remember { mutableStateOf("") }
    var ngoPhone by remember { mutableStateOf("") }
    var routePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var liveEta by remember { mutableStateOf("--") }
    var liveDistance by remember { mutableStateOf("--") }

    val otpInputs = remember { mutableStateListOf("", "", "", "") }
    var isVerifying by remember { mutableStateOf(false) }

    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(Unit) { Configuration.getInstance().userAgentValue = context.packageName }

    LaunchedEffect(orderId) {
        db.collection("orders").document(orderId)
            .addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    val order = snap.data
                    activeOrder = order

                    val ngoEmail = order?.get("userEmail") as? String
                    if (!ngoEmail.isNullOrEmpty()) {
                        coroutineScope.launch {
                            try {
                                val res = RetrofitClient.apiService.retrieveProfileFields(ngoEmail)
                                if (res.isSuccessful) {
                                    ngoPhone = res.body()?.phone ?: ""
                                    ngoProfilePic = res.body()?.profile_image_url ?: ""
                                }
                            } catch (e: Exception) {}
                        }
                    }

                    // Map Route (Farmer -> NGO live location)
                    coroutineScope.launch {
                        val fLat = (order?.get("pickupLat") as? Double) ?: 0.0
                        val fLon = (order?.get("pickupLon") as? Double) ?: 0.0
                        val ngoLat = (order?.get("userLat") as? Double) ?: (order?.get("driverLat") as? Double) ?: 0.0
                        val ngoLon = (order?.get("userLon") as? Double) ?: (order?.get("driverLon") as? Double) ?: 0.0
                        
                        if (fLat != 0.0 && ngoLat != 0.0) {
                            val routeData = getOsrmRouteShared(GeoPoint(ngoLat, ngoLon), GeoPoint(fLat, fLon))
                            routePoints = routeData.first
                            liveEta = routeData.second
                            // For simplicity, reusing ETA string for distance display or parse if possible
                        }
                    }
                }
            }
    }

    if (activeOrder == null) {
        Box(modifier = Modifier.fillMaxSize().background(Cream), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Terracotta)
        }
        return
    }

    val order = activeOrder!!
    val cropName = (order["cropName"] as? String ?: "Item").replaceFirstChar { it.uppercase() }
    val weight = (order["weightKg"] as? Number)?.toDouble() ?: 0.0
    val status = order["status"] as? String ?: "PENDING"
    val isCompleted = status == "COMPLETED" || status == "DELIVERED"
    val fLat = (order["pickupLat"] as? Double) ?: 0.0
    val fLon = (order["pickupLon"] as? Double) ?: 0.0
    val ngoLat = (order["userLat"] as? Double) ?: (order["driverLat"] as? Double) ?: 0.0
    val ngoLon = (order["userLon"] as? Double) ?: (order["driverLon"] as? Double) ?: 0.0
    val isFarmer = (order["farmerEmail"] as? String) == "user@growise.com" // Needs actual auth logic, assuming fallback
    // Actually, we don't have direct access to local user email here unless passed. For UI demonstration, we check pickupOtp.
    // In Android app, user identity is usually stored in local prefs. We'll assume Farmer view if user email == farmerEmail.
    
    // For this generic rewrite, let's assume we display Farmer view if they have the dropOtp/pickupOtp appropriately.
    val isFarmerView = true // Assuming Farmer view for demo, or logic based on current user

    Scaffold(
        containerColor = Cream,
        topBar = {
            TopAppBar(
                title = { Text("", color = Terracotta) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = EarthBrown)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())) {
            
            // Header
            Text("Donation Pickup", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Terracotta)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Eco, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(cropName, fontWeight = FontWeight.Bold, color = EarthBrown)
                Text("  •  ${weight} KG  •  Donation Order  •  ${orderId}", color = EarthBrown, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Status: ${if(status=="PENDING_DRIVER") "Accepted" else if(status=="PENDING") "Travelling to Farm" else status}", fontWeight = FontWeight.ExtraBold, color = Terracotta, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(24.dp))

            // Hero Map
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, GoldBorder, RoundedCornerShape(24.dp))
            ) {
                val animLat by animateFloatAsState(targetValue = ngoLat.toFloat(), animationSpec = tween(1500))
                val animLon by animateFloatAsState(targetValue = ngoLon.toFloat(), animationSpec = tween(1500))

                AndroidView(
                    factory = { ctx -> MapView(ctx).apply { setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); controller.setZoom(15.0) } },
                    update = { mapView ->
                        mapView.overlays.clear()
                        if (routePoints.isNotEmpty()) mapView.overlays.add(Polyline().apply { setPoints(routePoints); outlinePaint.color = android.graphics.Color.parseColor("#2196F3"); outlinePaint.strokeWidth = 8f })
                        if (fLat != 0.0) mapView.overlays.add(Marker(mapView).apply { position = GeoPoint(fLat, fLon); icon = context.getDrawable(R.drawable.ic_3d_farmer) })
                        if (ngoLat != 0.0) {
                            val ngoMarker = Marker(mapView).apply { position = GeoPoint(animLat.toDouble(), animLon.toDouble()); icon = context.getDrawable(R.drawable.ic_3d_minitruck) }
                            mapView.overlays.add(ngoMarker)
                        }
                        if (fLat != 0.0 && ngoLat != 0.0) {
                            // Auto fit logic simplified for AndroidView
                            val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(listOf(GeoPoint(fLat, fLon), GeoPoint(animLat.toDouble(), animLon.toDouble())))
                            mapView.zoomToBoundingBox(boundingBox, true, 100)
                        } else if (fLat != 0.0) {
                            mapView.controller.animateTo(GeoPoint(fLat, fLon))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Glassmorphism Overlay
                if (!isCompleted) {
                    Box(modifier = Modifier
                        .padding(16.dp)
                        .background(Color(0xD9FFFFFF), RoundedCornerShape(16.dp))
                        .border(1.dp, GoldBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp)) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).background(SuccessGreen, CircleShape))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("LIVE PICKUP", fontWeight = FontWeight.ExtraBold, color = Terracotta, fontSize = 12.sp, letterSpacing = 1.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("NGO is travelling", fontWeight = FontWeight.Bold, color = EarthBrown)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Column {
                                    Text("ETA", fontSize = 12.sp, color = Color.Gray)
                                    Text(liveEta, fontWeight = FontWeight.ExtraBold, color = EarthBrown)
                                }
                            }
                        }
                    }
                }
            }

            // Timeline
            val currentStep = if(isCompleted) 2 else 0
            PremiumCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val steps = listOf("Accepted" to Icons.Filled.CheckCircle, "OTP Verified" to Icons.Filled.Security, "Completed" to Icons.Filled.CheckCircle)
                    steps.forEachIndexed { idx, step ->
                        val isPast = idx < currentStep
                        val isCurrent = idx == currentStep
                        
                        val bgColor = if(isPast || (isCompleted && isCurrent)) SuccessGreen else if(isCurrent) WarmWhite else Cream
                        val borderColor = if(isPast || (isCompleted && isCurrent)) SuccessGreen else if(isCurrent) Terracotta else GoldBorder
                        val iconTint = if(isPast || (isCompleted && isCurrent)) Color.White else if(isCurrent) Terracotta else Color.Gray

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier
                                .size(40.dp)
                                .background(bgColor, CircleShape)
                                .border(2.dp, borderColor, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(step.second, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(step.first, fontSize = 10.sp, fontWeight = if(isCurrent) FontWeight.Bold else FontWeight.Medium, color = if(isCurrent) Terracotta else if(isPast) SuccessGreen else Color.Gray)
                        }
                    }
                }
            }

            // OTP CARD
            PremiumCard {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (isCompleted) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(60.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Pickup Completed", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = SuccessGreen)
                        Text("The donation handover is verified.", color = EarthBrown)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = Terracotta)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pickup Verification", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Terracotta)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Enter the 4-digit OTP provided by the NGO.", color = EarthBrown, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            (0..3).forEach { i ->
                                OutlinedTextField(
                                    value = otpInputs[i],
                                    onValueChange = { 
                                        if (it.length <= 1 && it.all { char -> char.isDigit() }) {
                                            otpInputs[i] = it
                                        }
                                    },
                                    modifier = Modifier.size(64.dp, 80.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = Terracotta),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Terracotta,
                                        unfocusedBorderColor = GoldBorder,
                                        focusedContainerColor = WarmWhite,
                                        unfocusedContainerColor = Cream
                                    ),
                                    singleLine = true
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        val otpFull = otpInputs.joinToString("").length == 4
                        Button(
                            onClick = {
                                if (!otpFull) return@Button
                                isVerifying = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val req = com.simats.growise.data.model.VerifySelfPickupRequest(orderId = orderId, otp = otpInputs.joinToString(""))
                                        val res = RetrofitClient.apiService.verifySelfPickup(req)
                                        withContext(Dispatchers.Main) {
                                            if (res.isSuccessful) Toast.makeText(context, "Verified Successfully", Toast.LENGTH_SHORT).show()
                                            else Toast.makeText(context, "Invalid OTP", Toast.LENGTH_SHORT).show()
                                            isVerifying = false
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Verification Failed", Toast.LENGTH_SHORT).show()
                                            isVerifying = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = if(otpFull) Terracotta else Color.LightGray),
                            enabled = !isVerifying && otpFull
                        ) {
                            if (isVerifying) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            else Text("Verify Pickup", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // PROFILE CARD
            PremiumCard {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.size(100.dp)) {
                        if (ngoProfilePic.isNotEmpty()) {
                            AsyncImage(
                                model = ngoProfilePic,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape).border(2.dp, GoldBorder, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(Cream, CircleShape).border(2.dp, GoldBorder, CircleShape), contentAlignment = Alignment.Center) {
                                Text("N", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Terracotta)
                            }
                        }
                        Box(modifier = Modifier.align(Alignment.BottomEnd).background(SuccessGreen, CircleShape).border(2.dp, Color.White, CircleShape).padding(4.dp)) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(order["userEmail"] as? String ?: "NGO Name", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = EarthBrown)
                    Text("VERIFIED NGO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Terracotta, letterSpacing = 1.sp)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(modifier = Modifier.fillMaxWidth().background(Cream, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Phone, contentDescription = null, tint = Terracotta)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(ngoPhone.ifEmpty { "Phone not available" }, color = EarthBrown, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Navigation, contentDescription = null, tint = Terracotta)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(if(isCompleted) "Received" else "Travelling", color = Terracotta, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // PRODUCT CARD
            PremiumCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(100.dp).background(Cream, RoundedCornerShape(20.dp)).border(1.dp, GoldBorder, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Eco, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(48.dp))
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text(cropName, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = EarthBrown)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier
                            .background(SuccessGreen, RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .border(1.dp, GoldBorder, RoundedCornerShape(50)), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("DONATION", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Inventory, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${weight} KG", color = Color.Gray, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // SUMMARY CARD
            PremiumCard {
                Text("Donation Summary", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Terracotta)
                Spacer(modifier = Modifier.height(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    @Composable
                    fun summaryRow(label: String, value: String, valueColor: Color = EarthBrown, bold: Boolean = true) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, color = Color.Gray, fontWeight = FontWeight.Medium)
                            Text(value, color = valueColor, fontWeight = if(bold) FontWeight.ExtraBold else FontWeight.Medium)
                        }
                    }
                    summaryRow("Crop", cropName)
                    summaryRow("Quantity", "${weight} KG")
                    summaryRow("Pickup Method", "Self Pickup")
                    summaryRow("Order Status", if(status=="PENDING_DRIVER") "Accepted" else if(status=="PENDING") "Travelling" else status, Terracotta)
                    summaryRow("Donation Amount", "₹0", SuccessGreen)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
