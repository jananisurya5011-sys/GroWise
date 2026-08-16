package com.simats.growise.driver

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.simats.growise.R
import com.simats.growise.data.network.RetrofitClient
import com.simats.growise.ui.theme.GoldenYellow
import com.simats.growise.ui.theme.PeachBackground
import com.simats.growise.ui.theme.TerracottaPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverDocumentVaultScreen(userEmail: String, navController: NavController) {
    val coroutineScope = rememberCoroutineScope()
    var licenseUrl by remember { mutableStateOf("") }
    var rcBookUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val baseUrl = RetrofitClient.BASE_URL.removeSuffix("/")

    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Restart)
    )

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val response = RetrofitClient.apiService.retrieveProfileFields(userEmail)
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    licenseUrl = data.licenseUrl ?: ""
                    rcBookUrl = data.rcBookUrl ?: ""
                }
            } catch (e: Exception) {}
            isLoading = false
        }
    }

    Scaffold(
        containerColor = PeachBackground,
        topBar = {
            TopAppBar(
                title = { Text("Document Vault", fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TerracottaPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PeachBackground)
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(id = R.drawable.ic_agri_loading), contentDescription = "Loading", tint = TerracottaPrimary, modifier = Modifier.size(64.dp).rotate(rotation))
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Your Verified Documents", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.Black)
                Text("These documents are securely stored and verified by platform administrators.", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                Spacer(modifier = Modifier.height(32.dp))

                Text("Driving License", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TerracottaPrimary)
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth().height(250.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, GoldenYellow), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    if (licenseUrl.isNotEmpty()) {
                        AsyncImage(model = baseUrl + licenseUrl, contentDescription = "License", modifier = Modifier.fillMaxSize().padding(8.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No License Found", color = Color.Gray) }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                Text("Vehicle RC Book", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TerracottaPrimary)
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth().height(250.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, GoldenYellow), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    if (rcBookUrl.isNotEmpty()) {
                        AsyncImage(model = baseUrl + rcBookUrl, contentDescription = "RC Book", modifier = Modifier.fillMaxSize().padding(8.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No RC Book Found", color = Color.Gray) }
                    }
                }
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}