package com.simats.growise.user

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.simats.growise.data.network.RetrofitClient
import com.simats.growise.ui.theme.GoldenYellow
import com.simats.growise.ui.theme.PeachBackground
import com.simats.growise.ui.theme.TerracottaPrimary
import com.simats.growise.ui.theme.TextDark
import com.simats.growise.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.text.SimpleDateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NGOFeedScreen(navController: NavController, userEmail: String) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("GroWiseSession", Context.MODE_PRIVATE) }

    var items by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var declinedRescues by remember { mutableStateOf(sharedPref.getStringSet("DECLINED_RESCUES", mutableSetOf()) ?: mutableSetOf()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()

    val lat = sharedPref.getString("LAT", "13.0827")?.toDoubleOrNull() ?: 13.0827
    val lon = sharedPref.getString("LON", "80.2707")?.toDoubleOrNull() ?: 80.2707
    val baseUrl = remember { RetrofitClient.BASE_URL.removeSuffix("/") }

    var showRescueDialog by remember { mutableStateOf(false) }
    var selectedRescueItem by remember { mutableStateOf<JSONObject?>(null) }
    var inputKgStr by remember { mutableStateOf("") }
    var isSendingRequest by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/api/ngo/rescue-feed")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val reqBody = JSONObject().apply {
                    put("lat", lat)
                    put("lon", lon)
                }

                connection.outputStream.write(reqBody.toString().toByteArray(Charsets.UTF_8))

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val responseStr = reader.readText()
                    reader.close()

                    val jsonArray = JSONArray(responseStr)
                    val parsedList = mutableListOf<JSONObject>()
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
                        if (!declinedRescues.contains(obj.optString("id")) && diffHours > 0) {
                            parsedList.add(obj)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        items = parsedList
                        isLoading = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        errorMsg = "Failed to load feed."
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMsg = e.message ?: "Network error"
                    isLoading = false
                }
            }
        }
    }

    if (showRescueDialog && selectedRescueItem != null) {
        val item = selectedRescueItem!!
        val availableKg = item.optDouble("availableKg", 0.0)
        val cropName = item.optString("cropName", "Produce")
        val imageUrl = item.optString("imageUrl", "")
        val farmerEmail = item.optString("email", "")
        val itemId = item.optString("id", "")

        Dialog(onDismissRequest = { showRescueDialog = false; inputKgStr = "" }) {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(8.dp)) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = baseUrl + imageUrl, contentDescription = null, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(PeachBackground), contentScale = ContentScale.Crop)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(cropName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.VolunteerActivism, contentDescription = "Rescue", tint = TerracottaPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Donation Hub", color = TerracottaPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    Text("Quantity Required (kg)", fontWeight = FontWeight.Bold, color = TerracottaPrimary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    val kgValue = inputKgStr.toDoubleOrNull() ?: 0.0

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { if (kgValue > 1.0) inputKgStr = String.format(Locale.US, "%.1f", kgValue - 1) }, modifier = Modifier.background(PeachBackground, CircleShape)) { Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = TerracottaPrimary) }
                        OutlinedTextField(
                            value = inputKgStr, onValueChange = { inputKgStr = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(120.dp).padding(horizontal = 8.dp), textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                            shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TerracottaPrimary, unfocusedBorderColor = GoldenYellow),
                            placeholder = { Text("Kg", color = Color.LightGray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                        )
                        IconButton(onClick = { if (kgValue < availableKg) inputKgStr = String.format(Locale.US, "%.1f", kgValue + 1) }, modifier = Modifier.background(PeachBackground, CircleShape)) { Icon(Icons.Filled.Add, contentDescription = "Increase", tint = TerracottaPrimary) }
                    }

                    val isValidQty = kgValue > 0 && kgValue <= availableKg

                    if (kgValue > 0 && !isValidQty) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Only $availableKg kg available to rescue.", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { showRescueDialog = false; inputKgStr = "" }) { Text("Cancel", color = Color.Gray) }
                        Button(
                            onClick = {
                                isSendingRequest = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val req = com.simats.growise.data.model.DonationRequest(
                                            ngoEmail = userEmail,
                                            farmerEmail = farmerEmail,
                                            itemId = itemId,
                                            requestedQuantity = kgValue
                                        )
                                        val response = com.simats.growise.data.network.RetrofitClient.apiService.requestDonation(req)
                                        
                                        withContext(Dispatchers.Main) {
                                            isSendingRequest = false
                                            if (response.isSuccessful && response.body()?.success == true) {
                                                showRescueDialog = false
                                                items = items.filter { it.optString("id") != itemId }
                                                inputKgStr = ""
                                                Toast.makeText(context, "Donation request sent!", Toast.LENGTH_SHORT).show()
                                                navController.navigate("orderHistory")
                                            } else {
                                                Toast.makeText(context, "Failed to send request", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) { 
                                            isSendingRequest = false 
                                            Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            enabled = isValidQty && !isSendingRequest,
                            colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary, contentColor = Color.White)
                        ) {
                            if (isSendingRequest) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("Send Request", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VolunteerActivism, contentDescription = "Rescue", tint = TerracottaPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hyperlocal Rescue", fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary, fontSize = 22.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TerracottaPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PeachBackground)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PeachBackground)
                .padding(paddingValues)
        ) {
            if (isLoading) {
                val loadingRotation by rememberInfiniteTransition().animateFloat(
                    initialValue = 0f, targetValue = 360f,
                    animationSpec = infiniteRepeatable(animation = tween(1000, easing = androidx.compose.animation.core.LinearEasing))
                )
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = com.simats.growise.R.drawable.ic_agri_loading),
                        contentDescription = "Loading",
                        tint = TerracottaPrimary,
                        modifier = Modifier.size(56.dp).rotate(loadingRotation)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Scanning hyperlocal area...", color = TerracottaPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            } else if (errorMsg.isNotEmpty()) {
                Card(modifier = Modifier.align(Alignment.Center).padding(32.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Text(errorMsg, color = Color.Red, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                }
            } else if (items.isEmpty()) {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.VolunteerActivism, contentDescription = "Empty", tint = GoldenYellow, modifier = Modifier.size(100.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No rescue items nearby.", color = TextMuted, fontWeight = FontWeight.Medium, fontSize = 18.sp)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(items, key = { it.optString("id") }) { item ->
                        val id = item.optString("id")
                        val cropName = item.optString("cropName", "Unknown Produce")
                        val farmerName = item.optString("farmerName", "Local Farmer")
                        val availableKg = item.optDouble("availableKg", 0.0)
                        val imageUrl = item.optString("imageUrl", "")
                        val expStr = item.optString("expiryDate", "")

                        var diffInHours = 1000.0
                        if (expStr.isNotEmpty()) {
                            try {
                                val format = if (expStr.contains("-")) java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()) else java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                                val expDate = format.parse(expStr)
                                if (expDate != null) {
                                    diffInHours = (expDate.time - System.currentTimeMillis()) / 3600000.0
                                }
                            } catch (e: Exception) {}
                        }

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    val newSet = declinedRescues.toMutableSet()
                                    newSet.add(id)
                                    sharedPref.edit().putStringSet("DECLINED_RESCUES", newSet).apply()
                                    declinedRescues = newSet
                                    items = items.filter { it.optString("id") != id }
                                    true
                                } else false
                            }
                        )
                        
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Box(modifier = Modifier.fillMaxSize().padding(bottom = 16.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFD32F2F)).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Close, contentDescription = "Ignore", tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Ignore", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            },
                            content = {
                                Card(
                                    modifier = Modifier.fillMaxWidth().height(140.dp).padding(bottom = 16.dp).shadow(2.dp, RoundedCornerShape(20.dp)),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.5.dp, GoldenYellow)
                                ) {
                                    Row(modifier = Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(100.dp).clip(RoundedCornerShape(14.dp)).background(PeachBackground)) {
                                            if (imageUrl.isNotEmpty()) {
                                                AsyncImage(model = baseUrl + imageUrl, contentDescription = cropName, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            } else {
                                                Icon(Icons.Filled.LocalMall, contentDescription = "Produce", modifier = Modifier.size(40.dp).align(Alignment.Center), tint = TerracottaPrimary.copy(alpha = 0.5f))
                                            }
                                            val expiryStatus = item.optString("expiryStatus", "Fresh Batch")
                                            val badgeColor = when (expiryStatus) {
                                                "Expired" -> Color.Gray
                                                "ACTIVE NGO FEED" -> Color(0xFF7C3AED) // NGO Purple
                                                "Clearance Sale" -> Color(0xFFD32F2F) // Deep Orange-Red
                                                "Near Expiry" -> Color(0xFFFF9800) // Orange
                                                else -> Color(0xFF4CAF50) // Green
                                            }

                                            Box(modifier = Modifier.align(Alignment.TopStart).padding(4.dp).background(badgeColor, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Filled.Timer, contentDescription = "Expiring", tint = Color.White, modifier = Modifier.size(10.dp))
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(expiryStatus.uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp)
                                                }
                                            }

                                            LaunchedEffect(diffInHours) {
                                                if (diffInHours <= 0) {
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        try { RetrofitClient.apiService.deleteInventoryItem(com.simats.growise.data.model.ItemIdRequest(itemId = id)) } catch (e: Exception) {}
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                                            Column {
                                                Text(cropName, fontSize = 18.sp, fontWeight = FontWeight.Black, color = TextDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Filled.Person, contentDescription = "Farmer", tint = TextMuted, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(farmerName, color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Available: $availableKg kg", color = TerracottaPrimary, fontWeight = FontWeight.Black, fontSize = 13.sp)
                                            }

                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = { selectedRescueItem = item; showRescueDialog = true },
                                                    modifier = Modifier.weight(1f).height(36.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text("Request Qty", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}