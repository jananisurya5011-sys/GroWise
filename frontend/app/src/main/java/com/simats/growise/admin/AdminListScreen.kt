package com.simats.growise.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.simats.growise.R
import com.simats.growise.ui.theme.GoldenYellow
import com.simats.growise.ui.theme.PeachBackground
import com.simats.growise.ui.theme.TerracottaPrimary
import com.simats.growise.ui.theme.TextDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.simats.growise.data.network.RetrofitClient
import com.simats.growise.data.model.AdminListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminListScreen(navController: NavController, listType: String) {
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }

    var allItems by remember { mutableStateOf<List<AdminListItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Restart)
    )

    fun fetchAdminList() {
        coroutineScope.launch {
            isLoading = true
            try {
                val res = RetrofitClient.apiService.getAdminList(listType)
                if (res.isSuccessful && res.body() != null) {
                    allItems = res.body()!!.items ?: emptyList()
                }
            } catch (e: Exception) {}
            isLoading = false
        }
    }

    LaunchedEffect(listType) {
        fetchAdminList()
    }

    val filteredItems = allItems.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.email.contains(searchQuery, ignoreCase = true) ||
                it.id.contains(searchQuery, ignoreCase = true)
    }

    val title = when(listType) {
        "users" -> "Total Users"
        "farmers" -> "Total Farmers"
        "drivers" -> "Verified Drivers"
        "deals" -> "Today's Orders"
        else -> "Registry"
    }

    val titleIcon = when(listType) {
        "users" -> Icons.Filled.People
        "farmers" -> Icons.Filled.Agriculture
        "drivers" -> Icons.Filled.LocalShipping
        "deals" -> Icons.Filled.Handshake
        else -> Icons.Filled.List
    }

    Scaffold(
        containerColor = PeachBackground,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TerracottaPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PeachBackground)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text("Search by name, email, or ID...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Filled.Search, tint = TerracottaPrimary, contentDescription = "Search") },
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = GoldenYellow,
                    unfocusedBorderColor = Color.Transparent
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_agri_loading),
                        contentDescription = "Loading",
                        tint = TerracottaPrimary,
                        modifier = Modifier.size(48.dp).align(Alignment.Center).rotate(rotation)
                    )
                } else if (filteredItems.isEmpty()) {
                    Text("No records found.", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                        items(filteredItems) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(), // Removed the .clickable modifier
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(2.dp),
                                border = BorderStroke(1.dp, GoldenYellow.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(48.dp).background(PeachBackground, CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(titleIcon, contentDescription = null, tint = TerracottaPrimary)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(item.email, fontSize = 13.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.wrapContentWidth()) {
                                        // Condition added to completely hide the ID for users and farmers
                                        if (listType != "users" && listType != "farmers") {
                                            Text(item.id, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = TerracottaPrimary)
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                        Text(item.status, fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
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