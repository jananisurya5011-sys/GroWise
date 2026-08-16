package com.simats.growise.common

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.simats.growise.ui.theme.GoldenYellow
import com.simats.growise.ui.theme.PeachBackground
import com.simats.growise.ui.theme.TerracottaPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderHistoryScreen(userEmail: String, navController: NavController) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    var orderHistoryList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var selectedOrder by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var selectedDate by remember { mutableStateOf<Date?>(null) }
    val calendar = Calendar.getInstance()

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.set(year, month, dayOfMonth)
            selectedDate = cal.time
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val historyStatuses = listOf("DELIVERED", "COMPLETED", "CANCELLED_BY_USER", "CANCELLED_FARMER_FAULT", "CANCELLED_NGO", "CANCELLED_SYSTEM", "CANCELLED_DRIVER_FAULT")

    fun fetchOrderHistory() {
        db.collection("orders").whereIn("status", historyStatuses).get().addOnSuccessListener { snap ->
            val docs = snap.documents.mapNotNull { it.data }
            orderHistoryList = docs.filter {
                it["userEmail"] == userEmail || it["farmerEmail"] == userEmail ||
                        it["hostEmail"] == userEmail || it["coLoaderEmail"] == userEmail
            }.sortedByDescending { it["timestamp"] as? Long ?: 0L }
        }
    }

    LaunchedEffect(userEmail) {
        db.collection("orders").whereIn("status", historyStatuses).addSnapshotListener { snap, _ ->
            if (snap != null) {
                val docs = snap.documents.mapNotNull { it.data }
                orderHistoryList = docs.filter {
                    it["userEmail"] == userEmail || it["farmerEmail"] == userEmail ||
                            it["hostEmail"] == userEmail || it["coLoaderEmail"] == userEmail
                }.sortedByDescending { it["timestamp"] as? Long ?: 0L }
            }
        }
    }

    val filteredOrders = if (selectedDate != null) {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val selectedDateStr = dateFormat.format(selectedDate!!)
        orderHistoryList.filter { dateFormat.format(Date(it["timestamp"] as? Long ?: 0L)) == selectedDateStr }
    } else orderHistoryList

    Scaffold(
        containerColor = PeachBackground,
        topBar = {
            TopAppBar(
                title = { Text("Order History Vault", fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TerracottaPrimary) } },
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            isRefreshing = true; fetchOrderHistory(); delay(500); isRefreshing = false
                            Toast.makeText(context, "History Synced", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TerracottaPrimary)
                    }
                    if (selectedDate != null) {
                        TextButton(onClick = { selectedDate = null }, contentPadding = PaddingValues(0.dp)) {
                            Text("Clear", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = { datePickerDialog.show() }) {
                        Icon(Icons.Filled.DateRange, contentDescription = "Filter Date", tint = TerracottaPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PeachBackground)
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp)) {
            Spacer(modifier = Modifier.height(16.dp))

            if (filteredOrders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(64.dp), tint = TerracottaPrimary.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No Order History", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.DarkGray)
                        Text(if(selectedDate == null) "Your past orders will be saved here." else "No orders found for this date.", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredOrders, key = { it["orderId"].toString() }) { order ->
                        val orderIdStr = order["orderId"]?.toString() ?: ""
                        val isNgoRescue = order["orderType"] == "NGO_RESCUE" || order["isDonation"] == true || orderIdStr.startsWith("GW-DON-")
                        val isPool = order.containsKey("hostEmail")
                        val crop = if (isNgoRescue) "Donation" else if (isPool) "Crop Pool" else "Standard Order"

                        val total = if (!isPool) {
                            (order["totalPaid"] as? Number)?.toDouble() ?: 0.0
                        } else {
                            if (order["coLoaderEmail"] == userEmail) {
                                (order["coLoaderPayment"] as? Number)?.toDouble() ?: 0.0
                            } else {
                                (order["totalPayment"] as? Number)?.toDouble() ?: (order["totalPaid"] as? Number)?.toDouble() ?: 0.0
                            }
                        }

                        val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(order["timestamp"] as? Long ?: System.currentTimeMillis()))
                        val orderId = order["orderId"] as? String ?: ""

                        SwipeToDeleteCard(
                            onDelete = {
                                if (orderId.isNotEmpty()) {
                                    db.collection("orders").document(orderId).delete()
                                    Toast.makeText(context, "Order permanently deleted", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { selectedOrder = order },
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, if (isNgoRescue) Color(0xFFE65100).copy(alpha = 0.5f) else GoldenYellow.copy(alpha = 0.5f)),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(50.dp).background(if (isNgoRescue) Color(0xFFFFF3E0) else PeachBackground, CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(if (isNgoRescue) Icons.Filled.VolunteerActivism else Icons.Filled.LocalMall, contentDescription = null, tint = if (isNgoRescue) Color(0xFFE65100) else TerracottaPrimary)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(crop, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = if (isNgoRescue) Color(0xFFE65100) else Color.Black)
                                        Text("Delivered on $date", fontSize = 12.sp, color = Color.Gray)
                                    }
                                    Text("₹${String.format(Locale.US, "%.0f", total)}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = if (isNgoRescue) Color.Gray else Color(0xFF2E7D32))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Filled.ChevronRight, contentDescription = "View Bill", tint = Color.LightGray)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedOrder != null) {
            Dialog(onDismissRequest = { selectedOrder = null }) {
                Card(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(16.dp)
                ) {
                    val orderIdStr = selectedOrder!!["orderId"]?.toString() ?: ""
                    val isNgoRescue = selectedOrder!!["orderType"] == "NGO_RESCUE" || selectedOrder!!["isDonation"] == true || orderIdStr.startsWith("GW-DON-")
                    Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isNgoRescue) "DONATION RECORD" else "TAX INVOICE", fontWeight = FontWeight.Black, fontSize = 22.sp, color = TerracottaPrimary, letterSpacing = 2.sp)
                            IconButton(onClick = { selectedOrder = null }) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF0F0F0))

                        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF9F9F9), RoundedCornerShape(12.dp)).padding(12.dp)) {
                                Column {
                                    Text(if (isNgoRescue) "Transport Method" else "Delivery Partner", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (isNgoRescue) Icons.Filled.DirectionsWalk else Icons.Filled.LocalShipping, contentDescription = null, tint = TerracottaPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))

                                        if (isNgoRescue) {
                                            val transportMode = selectedOrder!!["vehicleType"]?.toString() ?: "Self-Service"
                                            val isSelfService = transportMode.equals("Self-Service", ignoreCase = true) || transportMode.equals("Self Pickup", ignoreCase = true)
                                            Text(if (isSelfService) "Self-Service Pick-up" else "GroWise Delivery ($transportMode)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                        } else {
                                            var realDriverName by remember { mutableStateOf("Fetching...") }
                                            var fetchedDriverId by remember { mutableStateOf("GW-D...") }
                                            LaunchedEffect(selectedOrder!!["driverEmail"]) {
                                                db.collection("users").document(selectedOrder!!["driverEmail"].toString()).get().addOnSuccessListener { dSnap ->
                                                    realDriverName = dSnap.getString("name") ?: selectedOrder!!["driverName"].toString()
                                                    fetchedDriverId = dSnap.getString("driverId") ?: "GW-D0000"
                                                }
                                            }
                                            Text("$realDriverName (ID: $fetchedDriverId)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Handover: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(selectedOrder!!["timestamp"] as? Long ?: System.currentTimeMillis()))}", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            val isPool = selectedOrder!!.containsKey("hostEmail")
                            val orderId = selectedOrder!!["orderId"] as? String ?: ""
                            Text("Record ID: $orderId", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TerracottaPrimary)
                            Spacer(modifier = Modifier.height(8.dp))

                            if (isNgoRescue) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Filled.Circle, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(12.dp).padding(top = 4.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Pickup Location (Farmer: ${selectedOrder!!["farmerEmail"]})", fontSize = 11.sp, color = Color.Gray)
                                        Text("${selectedOrder!!["pickupAddress"]}", fontSize = 13.sp, color = Color.Black)
                                    }
                                }
                            } else if (!isPool) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Filled.Circle, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(12.dp).padding(top = 4.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Pickup (Farmer: ${selectedOrder!!["farmerEmail"]})", fontSize = 11.sp, color = Color.Gray)
                                        Text("${selectedOrder!!["pickupAddress"]}", fontSize = 13.sp, color = Color.Black)
                                    }
                                }
                                Box(modifier = Modifier.padding(start = 5.dp, top = 4.dp, bottom = 4.dp).width(2.dp).height(20.dp).background(Color.LightGray))
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = TerracottaPrimary, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text("Drop-off (Buyer: ${selectedOrder!!["userEmail"]})", fontSize = 11.sp, color = Color.Gray)
                                        Text("${selectedOrder!!["dropAddress"]}", fontSize = 13.sp, color = Color.Black)
                                    }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Filled.Circle, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(12.dp).padding(top = 4.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Host Pickup (${selectedOrder!!["hostEmail"]})", fontSize = 11.sp, color = Color.Gray)
                                        Text("${selectedOrder!!["pickupAddress"]}", fontSize = 13.sp, color = Color.Black)
                                    }
                                }
                                if (selectedOrder!!.containsKey("coLoaderEmail") && selectedOrder!!["coLoaderEmail"] != null) {
                                    Box(modifier = Modifier.padding(start = 5.dp, top = 4.dp, bottom = 4.dp).width(2.dp).height(20.dp).background(Color.LightGray))
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(Icons.Filled.Circle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(12.dp).padding(top = 4.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Co-Loader Pickup (${selectedOrder!!["coLoaderEmail"]})", fontSize = 11.sp, color = Color(0xFF2E7D32))
                                            Text("${selectedOrder!!["coLoaderPickupAddress"]}", fontSize = 13.sp, color = Color.Black)
                                        }
                                    }
                                }
                                Box(modifier = Modifier.padding(start = 5.dp, top = 4.dp, bottom = 4.dp).width(2.dp).height(20.dp).background(Color.LightGray))
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = TerracottaPrimary, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text("Host Drop", fontSize = 11.sp, color = Color.Gray)
                                        Text("${selectedOrder!!["dropAddress"]}", fontSize = 13.sp, color = Color.Black)
                                    }
                                }
                                if (selectedOrder!!.containsKey("coLoaderEmail") && selectedOrder!!["coLoaderEmail"] != null) {
                                    Box(modifier = Modifier.padding(start = 5.dp, top = 4.dp, bottom = 4.dp).width(2.dp).height(20.dp).background(Color.LightGray))
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp).padding(top = 2.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text("Co-Loader Drop", fontSize = 11.sp, color = Color(0xFF2E7D32))
                                            Text("${selectedOrder!!["coLoaderDropAddress"]}", fontSize = 13.sp, color = Color.Black)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(if (isNgoRescue) "DONATION SUMMARY" else "BILL SUMMARY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))

                            if (isNgoRescue) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Donated Produce", fontSize = 14.sp, color = Color.DarkGray)
                                    Text("${selectedOrder!!["cropName"]}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Weight Handed Over", fontSize = 14.sp, color = Color.DarkGray)
                                    Text("${selectedOrder!!["weightKg"]} kg", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                val transportPaid = (selectedOrder!!["totalPaid"] as? Number)?.toDouble() ?: 0.0
                                if (transportPaid > 0.0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Transport Fare", fontSize = 14.sp, color = Color.DarkGray)
                                        Text("₹${String.format(Locale.US, "%.2f", transportPaid)}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                                Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF0F0F0))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total Paid", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                                    Text("₹${String.format(Locale.US, "%.2f", transportPaid)}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (transportPaid == 0.0) Color.Gray else Color(0xFF2E7D32))
                                }
                            } else if (!isPool) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${selectedOrder!!["cropName"]} (${selectedOrder!!["weightKg"]} kg)", fontSize = 14.sp, color = Color.DarkGray)
                                    Text("₹${selectedOrder!!["cropValue"]}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Transport Fare (${selectedOrder!!["distanceKm"]} km)", fontSize = 14.sp, color = Color.DarkGray)
                                    Text("₹${selectedOrder!!["transportFare"]}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                                Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF0F0F0))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total Paid", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                                    Text("₹${selectedOrder!!["totalPaid"]}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
                                }
                            } else {
                                val tPayment = (selectedOrder!!["totalPayment"] as? Number)?.toDouble() ?: 0.0
                                val cPayment = (selectedOrder!!["coLoaderPayment"] as? Number)?.toDouble() ?: 0.0
                                val originalHostAmount = tPayment + cPayment // Math deduction: Before refund

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Host Original Lock (${selectedOrder!!["weightKg"]} kg)", fontSize = 14.sp, color = Color.DarkGray)
                                    Text("₹${String.format("%.2f", originalHostAmount)}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                                if (selectedOrder!!.containsKey("coLoaderEmail") && selectedOrder!!["coLoaderEmail"] != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Co-Loader Add (${selectedOrder!!["coLoaderWeightKg"]} kg + Detour)", fontSize = 14.sp, color = Color(0xFF2E7D32))
                                        Text("₹${String.format("%.2f", cPayment)}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF2E7D32))
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Host Escrow Refund", fontSize = 14.sp, color = Color.Red)
                                        Text("- ₹${String.format("%.2f", cPayment)}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Red)
                                    }
                                }
                                Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF0F0F0))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Final Host Paid", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                    Text("₹${String.format("%.2f", tPayment)}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                if (selectedOrder!!.containsKey("coLoaderEmail") && selectedOrder!!["coLoaderEmail"] != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Final Co-Loader Paid", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                        Text("₹${String.format("%.2f", cPayment)}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF0F0F0))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Driver Total Earnings", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary)
                                    Text("₹${String.format("%.2f", tPayment + cPayment)}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                generateAndSavePdf(context, selectedOrder!!)
                                selectedOrder = null
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = "Download", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isNgoRescue) "Download Rescue Record" else "Download PDF Invoice", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// Custom Zero-Dependency Premium Swipe-To-Delete Implementation
@Composable
fun SwipeToDeleteCard(
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(targetValue = offsetX)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        if (offsetX + dragAmount < 0) { // Restrict to Left Swipe Only
                            offsetX += dragAmount
                        }
                    },
                    onDragEnd = {
                        if (offsetX < -300f) { // Deletion Trigger Threshold
                            offsetX = -1000f // Slide off screen
                            onDelete()
                        } else {
                            offsetX = 0f // Snap back safely
                        }
                    }
                )
            }
    ) {
        // Red Background with Delete Icon underneath
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 12.dp)
                .background(Color(0xFFD32F2F), RoundedCornerShape(16.dp))
                .padding(16.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
        }

        // Foreground Card Overlay
        Box(modifier = Modifier.offset { IntOffset(animatedOffsetX.toInt(), 0) }) {
            content()
        }
    }
}

fun generateAndSavePdf(context: Context, orderData: Map<String, Any>) {
    val pdfDocument = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(400, 600, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas
    val paint = Paint()

    val isNgoRescue = orderData["orderType"] == "NGO_RESCUE" || orderData["isDonation"] == true

    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 24f
    paint.color = android.graphics.Color.rgb(186, 68, 38)
    canvas.drawText(if (isNgoRescue) "GroWise Rescue Record" else "GroWise Tax Invoice", 100f, 50f, paint)

    paint.color = android.graphics.Color.LTGRAY
    paint.strokeWidth = 2f
    canvas.drawLine(20f, 70f, 380f, 70f, paint)

    paint.color = android.graphics.Color.BLACK
    paint.textSize = 14f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

    var yPos = 110f
    val lineSpacing = 25f

    canvas.drawText("Order ID: ${orderData["orderId"]}", 20f, yPos, paint); yPos += lineSpacing
    canvas.drawText("Date: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(orderData["timestamp"] as? Long ?: System.currentTimeMillis()))}", 20f, yPos, paint); yPos += lineSpacing
    canvas.drawText("Item: ${orderData["cropName"]} (${orderData["weightKg"]} kg)", 20f, yPos, paint); yPos += lineSpacing
    yPos += 10f

    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText(if (isNgoRescue) "Transport Method:" else "Driver Details:", 20f, yPos, paint); yPos += lineSpacing
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

    if (isNgoRescue) {
        canvas.drawText("Method: NGO Self-Service Pick-up", 20f, yPos, paint); yPos += lineSpacing
    } else {
        canvas.drawText("Name: ${orderData["driverName"]}", 20f, yPos, paint); yPos += lineSpacing
        val driverIdStr = orderData["driverId"] as? String ?: "GW-D0000"
        canvas.drawText("ID: $driverIdStr", 20f, yPos, paint); yPos += lineSpacing
        canvas.drawText("Vehicle: ${orderData["vehicleType"]}", 20f, yPos, paint); yPos += lineSpacing
    }
    yPos += 10f

    val isPool = orderData.containsKey("hostEmail")
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText("Billing Breakdown:", 20f, yPos, paint); yPos += lineSpacing
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

    if (isNgoRescue) {
        canvas.drawText("Rescue Handover: Rs. 0.00", 20f, yPos, paint); yPos += lineSpacing
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Total Paid: Rs. 0.00", 20f, yPos + 10f, paint)
    } else if (!isPool) {
        canvas.drawText("Crop Value: Rs. ${orderData["cropValue"]}", 20f, yPos, paint); yPos += lineSpacing
        canvas.drawText("Transport Fare: Rs. ${orderData["transportFare"]}", 20f, yPos, paint); yPos += lineSpacing
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Total Paid: Rs. ${orderData["totalPaid"]}", 20f, yPos + 10f, paint)
    } else {
        val tPayment = (orderData["totalPayment"] as? Number)?.toDouble() ?: 0.0
        val cPayment = (orderData["coLoaderPayment"] as? Number)?.toDouble() ?: 0.0
        val originalHostAmount = tPayment + cPayment

        canvas.drawText("Host Original Lock: Rs. ${String.format("%.2f", originalHostAmount)}", 20f, yPos, paint); yPos += lineSpacing
        if (orderData.containsKey("coLoaderEmail") && orderData["coLoaderEmail"] != null) {
            canvas.drawText("Co-Loader Weight + Detour: Rs. ${String.format("%.2f", cPayment)}", 20f, yPos, paint); yPos += lineSpacing
            canvas.drawText("Host Escrow Refunded: - Rs. ${String.format("%.2f", cPayment)}", 20f, yPos, paint); yPos += lineSpacing
        }
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Final Host Paid: Rs. ${String.format("%.2f", tPayment)}", 20f, yPos + 10f, paint); yPos += lineSpacing
        if (orderData.containsKey("coLoaderEmail") && orderData["coLoaderEmail"] != null) {
            canvas.drawText("Final Co-Loader Paid: Rs. ${String.format("%.2f", cPayment)}", 20f, yPos + 10f, paint); yPos += lineSpacing
        }
        canvas.drawText("Driver Total Earnings: Rs. ${String.format("%.2f", tPayment + cPayment)}", 20f, yPos + 10f, paint)
    }

    pdfDocument.finishPage(page)

    try {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GroWise_${if(isNgoRescue) "Rescue" else "Invoice"}_${orderData["orderId"]}.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        Toast.makeText(context, "Document Saved to Downloads Folder!", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error saving PDF: ${e.message}", Toast.LENGTH_SHORT).show()
    } finally {
        pdfDocument.close()
    }
}