package com.simats.growise.admin

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simats.growise.R
import com.simats.growise.data.model.PendingDriverResponse
import com.simats.growise.data.network.RetrofitClient
import com.simats.growise.ui.theme.GoldenYellow
import com.simats.growise.ui.theme.PeachBackground
import com.simats.growise.ui.theme.TerracottaPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminVerifications(navController: NavController) {
    val coroutineScope = rememberCoroutineScope()
    var pendingList by remember { mutableStateOf<List<PendingDriverResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Restart)
    )

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val res = RetrofitClient.apiService.getPendingDrivers()
                if (res.isSuccessful && res.body() != null) {
                    pendingList = res.body()!!.drivers ?: emptyList()
                }
            } catch (e: Exception) {}
            isLoading = false
        }
    }

    Scaffold(
        containerColor = PeachBackground,
        topBar = { TopAppBar(title = { Text("Driver Approvals", fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = PeachBackground)) }
    ) { innerPadding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painter = painterResource(id = R.drawable.ic_agri_loading), contentDescription = "Loading", tint = TerracottaPrimary, modifier = Modifier.size(64.dp).rotate(rotation))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Fetching secure driver records...", color = TerracottaPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        } else if (pendingList.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(4.dp),
                    border = BorderStroke(2.dp, GoldenYellow)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No Driver Entries",
                            color = TerracottaPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(innerPadding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(pendingList) { driver ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { navController.navigate("admin_driver_details/${driver.email}") },
                        shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(4.dp), border = BorderStroke(2.dp, GoldenYellow)
                    ) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(50.dp).background(PeachBackground, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.LocalShipping, tint = TerracottaPrimary, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(driver.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.Black)
                                Text(driver.vehicleType, fontSize = 13.sp, color = TerracottaPrimary, fontWeight = FontWeight.Bold)
                                Text("Aadhaar: ${driver.aadhaarNumber}", fontSize = 12.sp, color = Color.Gray)
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = "View", tint = Color.LightGray)
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }
    }
}