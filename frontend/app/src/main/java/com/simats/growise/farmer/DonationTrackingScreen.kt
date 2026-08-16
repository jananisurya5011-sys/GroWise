package com.simats.growise.farmer

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.simats.growise.ui.theme.GoldenYellow
import com.simats.growise.ui.theme.PeachBackground
import com.simats.growise.ui.theme.TerracottaPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationTrackingScreen(navController: NavController) {
    val context = LocalContext.current
    val sharedPref = context.getSharedPreferences("GroWiseSession", android.content.Context.MODE_PRIVATE)
    val userEmail = sharedPref.getString("USER_EMAIL", "") ?: ""
    val db = FirebaseFirestore.getInstance()

    var activeDonations by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(userEmail) {
        db.collection("orders")
            .whereEqualTo("farmerEmail", userEmail)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val allDocs = snap.documents.mapNotNull { it.data }
                    activeDonations = allDocs.filter { doc ->
                        val isDonation = (doc["orderId"] as? String)?.startsWith("GW-DON-") == true || doc["isDonation"] == true
                        val status = doc["status"] as? String ?: ""
                        val vType = doc["vehicleType"] as? String ?: ""
                        val isSelf = vType.equals("Self Pickup", true) || vType.equals("Self", true) || vType.equals("Self-Service", true)
                        isDonation && isSelf && status !in listOf("COMPLETED", "DECLINED", "REJECTED", "WITHDRAWN", "CANCELLED")
                    }.sortedByDescending { (it["timestamp"] as? Number)?.toLong() ?: 0L }
                    isLoading = false
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Donation Tracking", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PeachBackground)
            )
        },
        containerColor = PeachBackground
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TerracottaPrimary)
            }
        } else if (activeDonations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Active Donations", color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(activeDonations) { donation ->
                    DonationTrackingItemCard(donation = donation, navController = navController)
                }
            }
        }
    }
}

@Composable
fun DonationTrackingItemCard(donation: Map<String, Any>, navController: NavController) {
    val orderId = donation["orderId"] as? String ?: ""
    val status = donation["status"] as? String ?: ""
    val cropName = donation["cropName"] as? String ?: "Produce"
    val weightKg = (donation["weightKg"] as? Number)?.toDouble() ?: 0.0
    val ngoEmail = donation["userEmail"] as? String ?: ""
    val timestamp = (donation["timestamp"] as? Number)?.toLong() ?: 0L
    
    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
    val dateStr = if (timestamp > 0) sdf.format(java.util.Date(timestamp)) else "Unknown Date"
    
    var ngoName by remember { mutableStateOf(ngoEmail.split("@").firstOrNull() ?: "NGO") }

    LaunchedEffect(ngoEmail) {
        if (ngoEmail.isNotBlank()) {
            try {
                val res = RetrofitClient.apiService.retrieveProfileFields(ngoEmail)
                if (res.isSuccessful) {
                    val body = res.body()
                    if (body != null) {
                        ngoName = body.name.takeIf { it.isNotBlank() } ?: ngoName
                    }
                }
            } catch (e: Exception) {}
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clickable { navController.navigate("donation_detail/$orderId") },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(ngoName, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = TerracottaPrimary)
                Box(
                    modifier = Modifier.background(if (status == "READY_FOR_PICKUP") Color(0xFFFFF3E0) else Color(0xFFE3F2FD), RoundedCornerShape(16.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val statusText = if (status == "READY_FOR_PICKUP") "Waiting for Pickup" else status
                    Text(statusText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (status == "READY_FOR_PICKUP") Color(0xFFE65100) else Color(0xFF1565C0))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Product", fontSize = 12.sp, color = Color.Gray)
                    Text(cropName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.DarkGray)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Quantity", fontSize = 12.sp, color = Color.Gray)
                    Text("$weightKg KG", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2E7D32))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFEEEEEE))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Requested: $dateStr", fontSize = 11.sp, color = Color.Gray)
        }
    }
}


