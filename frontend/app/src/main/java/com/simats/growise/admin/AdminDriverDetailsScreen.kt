package com.simats.growise.admin

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simats.growise.R
import com.simats.growise.data.model.AdminVerificationRequest
import com.simats.growise.data.model.PendingDriverResponse
import com.simats.growise.data.network.RetrofitClient
import com.simats.growise.ui.theme.GoldenYellow
import com.simats.growise.ui.theme.PeachBackground
import com.simats.growise.ui.theme.TerracottaPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDriverDetailsScreen(driverEmail: String, navController: NavController) {
    val coroutineScope = rememberCoroutineScope()
    var driver by remember { mutableStateOf<PendingDriverResponse?>(null) }
    val baseUrl = RetrofitClient.BASE_URL.removeSuffix("/")

    // Multi-state Engine Variables
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectReason by remember { mutableStateOf("") }

    var isProcessing by remember { mutableStateOf(false) }
    var processingText by remember { mutableStateOf("") }

    var showFinalResult by remember { mutableStateOf(false) }
    var resultType by remember { mutableStateOf("") } // "APPROVED" or "REJECTED"
    var resultMessage by remember { mutableStateOf("") } // Will hold the generated ID or generic message

    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Restart)
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(animation = tween(500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse)
    )

    LaunchedEffect(driverEmail) {
        try {
            val res = RetrofitClient.apiService.getPendingDrivers()
            if (res.isSuccessful && res.body() != null) {
                val list = res.body()!!.drivers ?: emptyList()
                driver = list.find { it.email == driverEmail }
            }
        } catch (e: Exception) {}
    }

    if (showRejectDialog) {
        Dialog(onDismissRequest = { showRejectDialog = false; rejectReason = "" }) {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(8.dp)) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                    Text("Reject Application", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = rejectReason, onValueChange = { rejectReason = it },
                        label = { Text("Reason for Rejection") },
                        modifier = Modifier.fillMaxWidth().height(120.dp), shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            showRejectDialog = false
                            processingText = "Rejecting Driver..."
                            isProcessing = true
                            coroutineScope.launch {
                                val req = AdminVerificationRequest(driverEmail, "REJECT", rejectReason)
                                val res = RetrofitClient.apiService.verifyDriver(req)
                                if (res.isSuccessful) {
                                    resultType = "REJECTED"
                                    resultMessage = "Driver has been successfully removed from the pending queue."
                                    delay(800) // Let animation play
                                    isProcessing = false
                                    showFinalResult = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { Text("Confirm Rejection", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    if (isProcessing) {
        Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(PeachBackground.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painter = painterResource(id = R.drawable.ic_agri_loading), contentDescription = "Loading", tint = TerracottaPrimary, modifier = Modifier.size(80.dp).rotate(rotation))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(processingText, color = TerracottaPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }

    if (showFinalResult) {
        val isApprove = resultType == "APPROVED"
        val bgColor = if (isApprove) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        val iconColor = if (isApprove) Color(0xFF2E7D32) else Color.Red
        val titleText = if (isApprove) "Driver Approved!" else "Driver Rejected"

        Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(PeachBackground), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    // Heartbeat Animation Icon
                    Box(modifier = Modifier.size(100.dp).scale(pulseScale).background(bgColor, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(imageVector = if (isApprove) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, tint = iconColor, modifier = Modifier.size(60.dp))
                    }
                    Spacer(modifier = Modifier.height(32.dp))

                    // Golden Outline Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(2.dp, GoldenYellow),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(titleText, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = iconColor)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(resultMessage, fontSize = 16.sp, color = Color.DarkGray, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)
                            ) { Text("Back to Pending", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }

    if (!showFinalResult && !isProcessing) {
        Scaffold(containerColor = PeachBackground) { innerPadding ->
            if (driver == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(painter = painterResource(id = R.drawable.ic_agri_loading), contentDescription = "Loading", tint = TerracottaPrimary, modifier = Modifier.size(64.dp).rotate(rotation))
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding()).verticalScroll(rememberScrollState())) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(140.dp).background(Brush.verticalGradient(colors = listOf(TerracottaPrimary, GoldenYellow))).padding(horizontal = 16.dp, vertical = 24.dp)
                    ) {
                        IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.TopStart)) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Text("Driver Evaluation", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp))
                    }

                    Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                        Text(driver!!.name, fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.Black)
                        Text(driver!!.email, fontSize = 15.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(24.dp))

                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column { Text("Phone Number", fontSize = 11.sp, color = Color.Gray); Text(driver!!.phone, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary) }
                                    Column(horizontalAlignment = Alignment.End) { Text("Vehicle Type", fontSize = 11.sp, color = Color.Gray); Text(driver!!.vehicleType, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary) }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Divider(color = Color(0xFFF9EFE9))
                                Spacer(modifier = Modifier.height(16.dp))
                                Column { Text("Govt ID", fontSize = 11.sp, color = Color.Gray); Text(driver!!.aadhaarNumber, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary) }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                        Text("Verification Documents", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Driving License", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(modifier = Modifier.fillMaxWidth().height(200.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.LightGray)) {
                            AsyncImage(model = baseUrl + driver!!.licenseUrl, contentDescription = "License", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text("RC Book", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(modifier = Modifier.fillMaxWidth().height(200.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.LightGray)) {
                            AsyncImage(model = baseUrl + driver!!.rcBookUrl, contentDescription = "RC", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }

                        Spacer(modifier = Modifier.height(40.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(onClick = { showRejectDialog = true }, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, Color.Red)) {
                                Icon(Icons.Filled.Close, contentDescription = null, tint = Color.Red)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reject", color = Color.Red, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    processingText = "Generating Driver ID..."
                                    isProcessing = true
                                    coroutineScope.launch {
                                        val req = AdminVerificationRequest(driverEmail, "APPROVE")
                                        val res = RetrofitClient.apiService.verifyDriver(req)
                                        if (res.isSuccessful) {
                                            resultType = "APPROVED"
                                            // Extract "Approved. ID: GW-DXXXX" from backend response
                                            resultMessage = res.body()?.message ?: "Driver Approved Successfully."
                                            delay(800)
                                            isProcessing = false
                                            showFinalResult = true
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)), shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Approve", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }
    }
}