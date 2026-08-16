package com.simats.growise.farmer

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.simats.growise.R
import com.simats.growise.data.network.RetrofitClient
import com.simats.growise.ui.theme.PeachBackground
import com.simats.growise.ui.theme.TerracottaPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationDetailScreen(orderId: String, navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()
    var donation by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var ngoName by remember { mutableStateOf("NGO") }
    var ngoContact by remember { mutableStateOf("") }
    var ngoProfilePic by remember { mutableStateOf("") }

    LaunchedEffect(orderId) {
        db.collection("orders").document(orderId)
            .addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    val data = snap.data
                    donation = data
                    
                    val ngoEmail = data?.get("userEmail") as? String ?: ""
                    if (ngoEmail.isNotBlank()) {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val res = RetrofitClient.apiService.retrieveProfileFields(ngoEmail)
                                if (res.isSuccessful) {
                                    val body = res.body()
                                    if (body != null) {
                                        withContext(Dispatchers.Main) {
                                            ngoName = body.name.takeIf { it.isNotBlank() } ?: ngoEmail.split("@").firstOrNull() ?: "NGO"
                                            ngoContact = body.phone.orEmpty()
                                            ngoProfilePic = body.profile_image_url ?: body.profileImage.orEmpty()
                                        }
                                    }
                                }
                            } catch (e: Exception) {}
                        }
                    }
                }
                isLoading = false
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Donation Details", fontWeight = FontWeight.Bold, color = Color.DarkGray) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.DarkGray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.background(Color(0xFFF9FAFB))
            )
        },
        containerColor = Color(0xFFF9FAFB)
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TerracottaPrimary)
            }
        } else if (donation == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Donation not found", color = Color.Gray, fontSize = 16.sp)
            }
        } else {
            val d = donation!!
            val cropName = d["cropName"] as? String ?: "Produce"
            val weightKg = (d["weightKg"] as? Number)?.toDouble() ?: 0.0
            val status = d["status"] as? String ?: ""
            val pickupAddress = d["pickupAddress"] as? String ?: "Not provided"
            val timestamp = (d["timestamp"] as? Number)?.toLong() ?: 0L
            val actualOtp = d["pickupOtp"] as? String ?: ""
            val imageUrl = d["imageUrl"] as? String ?: d["cropImage"] as? String ?: d["image"] as? String ?: ""
            
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val dateStr = if (timestamp > 0) sdf.format(Date(timestamp)) else "Unknown Date"

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Main Info Card
                Card(
                    modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // NGO Header
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = RetrofitClient.BASE_URL + ngoProfilePic,
                                contentDescription = "NGO Profile",
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF3F4F6)),
                                contentScale = ContentScale.Crop,
                                error = painterResource(id = R.drawable.app_logo),
                                placeholder = painterResource(id = R.drawable.app_logo)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Donation requested by", fontSize = 12.sp, color = Color.Gray)
                                Text(ngoName, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = TerracottaPrimary)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Product Details with Image
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF9FAFB), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFFF3F4F6), RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = RetrofitClient.BASE_URL + imageUrl,
                                contentDescription = "Product Image",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE5E7EB)),
                                contentScale = ContentScale.Crop,
                                error = painterResource(id = R.drawable.app_logo),
                                placeholder = painterResource(id = R.drawable.app_logo)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(cropName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.DarkGray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(modifier = Modifier.background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Text("$weightKg KG", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF2E7D32))
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // Address & Date
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(36.dp).background(PeachBackground, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = TerracottaPrimary, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Pickup Location", fontSize = 12.sp, color = Color.Gray)
                                Text(pickupAddress, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color.DarkGray)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (ngoContact.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(36.dp).background(Color(0xFFE3F2FD), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Phone, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Contact NGO", fontSize = 12.sp, color = Color.Gray)
                                    Text(ngoContact, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color.DarkGray)
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Timeline Card
                Card(
                    modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Donation Status", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.DarkGray, modifier = Modifier.padding(bottom = 20.dp))
                        
                        val step1 = true
                        val step2 = status == "READY_FOR_PICKUP" || status == "COMPLETED"
                        val step3 = status == "COMPLETED"

                        Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp)) {
                            DonationTimelineStep("Approved", "Farmer has accepted the request", step1, isLast = false)
                            DonationTimelineStep("Waiting for Pickup", "Awaiting NGO to arrive for pickup", step2, isLast = false)
                            DonationTimelineStep("Completed", "Donation successfully handed over", step3, isLast = true)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (status == "READY_FOR_PICKUP") {
                    DonationDetailOtpVerification(
                        orderId = orderId, 
                        actualOtp = actualOtp, 
                        onVerified = {
                            navController.popBackStack()
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun DonationTimelineStep(label: String, desc: String, isCompleted: Boolean, isLast: Boolean) {
    val circleColor by animateColorAsState(targetValue = if (isCompleted) TerracottaPrimary else Color(0xFFE5E7EB), animationSpec = tween(500))
    
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.height(IntrinsicSize.Min)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp)) {
            if (isCompleted) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = circleColor, modifier = Modifier.size(24.dp))
            } else {
                Box(modifier = Modifier.size(18.dp).border(2.dp, circleColor, CircleShape))
            }
            if (!isLast) {
                Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(if (isCompleted) TerracottaPrimary else Color(0xFFF3F4F6)).padding(vertical = 4.dp))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = if (isCompleted) FontWeight.Bold else FontWeight.Medium,
                color = if (isCompleted) Color.DarkGray else Color.Gray
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun DonationDetailOtpVerification(orderId: String, actualOtp: String, onVerified: () -> Unit) {
    var otpValue by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isVerifying by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    
    val focusRequester = remember { FocusRequester() }

    Card(
        modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(48.dp).background(PeachBackground, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = TerracottaPrimary, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Verify Pickup OTP", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.DarkGray)
            Text("Order ID: $orderId", fontSize = 14.sp, color = TerracottaPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Enter the 4-digit code provided by the NGO", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 24.dp))
            
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { focusRequester.requestFocus() }
            ) {
                BasicTextField(
                    value = otpValue,
                    onValueChange = { 
                        if (it.length <= 4) {
                            otpValue = it.filter { char -> char.isDigit() }
                            hasError = false
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.matchParentSize().alpha(0.01f).focusRequester(focusRequester),
                    decorationBox = { innerTextField -> innerTextField() }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (i in 0 until 4) {
                        val isFocused = otpValue.length == i
                        val char = otpValue.getOrNull(i)?.toString() ?: ""
                        
                        val borderColor by animateColorAsState(
                            targetValue = if (hasError) Color.Red else if (isFocused) TerracottaPrimary else if (char.isNotEmpty()) Color(0xFF4CAF50) else Color(0xFFE0E0E0)
                        )
                        val borderWidth by animateDpAsState(targetValue = if (isFocused || hasError) 2.dp else 1.dp)
                        
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color(0xFFF9FAFB), RoundedCornerShape(16.dp))
                                .border(width = borderWidth, color = borderColor, shape = RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (hasError) Color.Red else Color.DarkGray
                            )
                        }
                    }
                }
            }
            
            AnimatedVisibility(visible = hasError) {
                Text(
                    text = "Invalid OTP. Please try again.",
                    color = Color.Red,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(28.dp))
            
            Button(
                onClick = {
                    if (otpValue == actualOtp && actualOtp.isNotEmpty()) {
                        isVerifying = true
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val db = FirebaseFirestore.getInstance()
                                db.collection("orders").document(orderId).update(
                                    mapOf(
                                        "status" to "COMPLETED",
                                        "pickupOtpVerified" to true
                                    )
                                ).await()
                                withContext(Dispatchers.Main) {
                                    isVerifying = false
                                    Toast.makeText(context, "Donation Completed Successfully!", Toast.LENGTH_SHORT).show()
                                    onVerified()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isVerifying = false
                                    Toast.makeText(context, "Error completing donation", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        hasError = true
                    }
                },
                enabled = otpValue.length == 4 && !isVerifying,
                modifier = Modifier.fillMaxWidth().height(56.dp).shadow(4.dp, RoundedCornerShape(16.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TerracottaPrimary,
                    disabledContainerColor = Color(0xFFFFCCBC)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                } else {
                    Text("Verify & Complete Donation", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
