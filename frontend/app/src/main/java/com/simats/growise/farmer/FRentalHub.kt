package com.simats.growise.farmer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.simats.growise.R
import com.simats.growise.data.model.RentalItemResponse
import com.simats.growise.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

// Helper function to calculate distance dynamically on the frontend
fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0 // Earth radius in kilometers
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FRentalHub(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPref = remember { context.getSharedPreferences("GroWiseSession", Context.MODE_PRIVATE) }
    val userEmail = remember { sharedPref.getString("USER_EMAIL", "farmer@growise.com") ?: "farmer@growise.com" }
    val userLat = remember { sharedPref.getString("LAT", "0.0")?.toDoubleOrNull() ?: 0.0 }
    val userLon = remember { sharedPref.getString("LON", "0.0")?.toDoubleOrNull() ?: 0.0 }

    val baseUrl = remember { RetrofitClient.BASE_URL.removeSuffix("/") }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // State Variables
    var activeTab by remember { mutableStateOf(0) } // 0 = Browse Hub, 1 = My Equipments
    var isRefreshing by remember { mutableStateOf(false) }
    var isPublishing by remember { mutableStateOf(false) }

    var browseListings by remember { mutableStateOf<List<RentalItemResponse>>(emptyList()) }
    var myListings by remember { mutableStateOf<List<RentalItemResponse>>(emptyList()) }

    // Filters
    val categories = listOf("All", "Tractors", "Harvesters", "Implements", "Drones", "Vehicles")
    var selectedFilterCategory by remember { mutableStateOf(categories[0]) }

    val sortOptions = listOf("Nearest", "Price: Low to High")
    var selectedSort by remember { mutableStateOf(sortOptions[0]) }
    var isSortExpanded by remember { mutableStateOf(false) }

    // Add Modal States
    var showAddModal by remember { mutableStateOf(false) }
    var eqName by remember { mutableStateOf("") }
    var ratePerHour by remember { mutableStateOf("") }
    var ratePerDay by remember { mutableStateOf("") }
    var assetLat by remember { mutableStateOf("") }
    var assetLon by remember { mutableStateOf("") }
    var isFetchingAssetLocation by remember { mutableStateOf(false) }

    val addCategories = listOf("Tractors", "Harvesters", "Implements", "Drones", "Vehicles")
    var selectedAddCategory by remember { mutableStateOf(addCategories[0]) }
    var isAddCatExpanded by remember { mutableStateOf(false) }

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val assetLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isFetchingAssetLocation = true
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        isFetchingAssetLocation = false
                        if (location != null) {
                            assetLat = location.latitude.toString()
                            assetLon = location.longitude.toString()
                        }
                    }
                    .addOnFailureListener { isFetchingAssetLocation = false }
            } catch (e: SecurityException) { isFetchingAssetLocation = false }
        }
    }

    // Helpers
    fun getBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT < 28) {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            } else {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            }
        } catch (e: Exception) { null }
    }

    fun getFileFromBitmap(bitmap: Bitmap): File? {
        return try {
            val file = File(context.cacheDir, "rental_${System.currentTimeMillis()}.png")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            file
        } catch (e: Exception) { null }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { capturedBitmap = getBitmapFromUri(it) }
    }

    // Refresh Data Functions
    fun fetchBrowseData(showLoader: Boolean = false) {
        coroutineScope.launch(Dispatchers.IO) {
            if (showLoader) withContext(Dispatchers.Main) { isRefreshing = true }
            try {
                val items = RetrofitClient.apiService.fetchRentalItems(userLat, userLon, selectedFilterCategory, selectedSort)
                withContext(Dispatchers.Main) { browseListings = items.filter { it.email != userEmail } }
            } catch (e: Exception) {}
            finally { withContext(Dispatchers.Main) { isRefreshing = false } }
        }
    }

    fun fetchMyData() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val items = RetrofitClient.apiService.fetchMyRentalItems(com.simats.growise.data.model.EmailRequest(email = userEmail))
                withContext(Dispatchers.Main) { myListings = items }
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(activeTab, selectedFilterCategory, selectedSort) {
        if (activeTab == 0) fetchBrowseData(true) else fetchMyData()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "agri_loading")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "rotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Asset Rentals", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFF2C1810)) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF2C1810)) } },
                actions = {
                    Surface(
                        onClick = { showAddModal = true },
                        shape = RoundedCornerShape(20.dp),
                        color = com.simats.growise.ui.theme.TerracottaPrimary,
                        shadowElevation = 4.dp,
                        modifier = Modifier.padding(end = 12.dp).height(38.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Post Asset", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = com.simats.growise.ui.theme.PeachBackground),
                modifier = Modifier.shadow(elevation = 2.dp, spotColor = Color(0x1A000000))
            )
        },
        containerColor = com.simats.growise.ui.theme.PeachBackground
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // --- Premium Pill Tab Selector ---
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).background(Color.White, RoundedCornerShape(30.dp)).border(1.dp, Color(0xFFF0E5DB), RoundedCornerShape(30.dp)).padding(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(if (activeTab == 0) com.simats.growise.ui.theme.TerracottaPrimary else Color.Transparent).clickable { activeTab = 0 }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("Browse Hub", color = if (activeTab == 0) Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(if (activeTab == 1) com.simats.growise.ui.theme.GoldenYellow else Color.Transparent).clickable { activeTab = 1 }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("My Equipments", color = if (activeTab == 1) Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            AnimatedVisibility(visible = isRefreshing) {
                Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFF0E5DE)).padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(id = R.drawable.ic_agri_loading), contentDescription = "Loading", modifier = Modifier.size(28.dp).rotate(rotationAngle))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Syncing Live Rentals...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A463D))
                }
            }

            if (activeTab == 0) {
                // --- BROWSE HUB ---
                Column {
                    LazyRow(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories) { cat ->
                            Surface(
                                onClick = { selectedFilterCategory = cat },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedFilterCategory == cat) com.simats.growise.ui.theme.TerracottaPrimary else Color.White,
                                border = if (selectedFilterCategory != cat) BorderStroke(1.dp, Color(0xFFEFE8E2)) else null,
                                shadowElevation = if (selectedFilterCategory == cat) 4.dp else 0.dp
                            ) {
                                Text(cat, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = if (selectedFilterCategory == cat) Color.White else Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${browseListings.size} Items Found", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF2C1810))
                        Box {
                            Row(modifier = Modifier.clickable { isSortExpanded = true }.background(Color.White, RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFEFE8E2), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Sort, contentDescription = null, tint = com.simats.growise.ui.theme.TerracottaPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(selectedSort, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A463D))
                            }
                            DropdownMenu(expanded = isSortExpanded, onDismissRequest = { isSortExpanded = false }, modifier = Modifier.background(Color.White)) {
                                sortOptions.forEach { opt -> DropdownMenuItem(text = { Text(opt, fontWeight = FontWeight.Medium) }, onClick = { selectedSort = opt; isSortExpanded = false }) }
                            }
                        }
                    }

                    if (browseListings.isEmpty() && !isRefreshing) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No rentals found.", color = Color.Gray, fontWeight = FontWeight.Medium) }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(browseListings, key = { it.id }) { item -> BrowseCard(item, baseUrl, context) }
                        }
                    }
                }
            } else {
                // --- MY EQUIPMENTS ---
                if (isRefreshing && myListings.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(painter = painterResource(id = R.drawable.ic_agri_loading), contentDescription = "Loading", modifier = Modifier.size(40.dp).rotate(rotationAngle))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Loading your equipments...", color = Color.Gray, fontWeight = FontWeight.Medium)
                        }
                    }
                } else if (myListings.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("You haven't posted any equipment.", color = Color.Gray, fontWeight = FontWeight.Medium) }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(myListings, key = { it.id }) { item ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val res = RetrofitClient.apiService.deleteRentalItem(com.simats.growise.data.model.ItemIdRequest(itemId = item.id))
                                                withContext(Dispatchers.Main) { if (res.success) fetchMyData() }
                                            } catch (e: Exception) {}
                                        }
                                        true
                                    } else false
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Color(0xFFD32F2F)).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Delete Post", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }
                                },
                                content = {
                                    MyEquipmentCard(
                                        item = item,
                                        baseUrl = baseUrl,
                                        onToggleLock = { isLocked ->
                                            coroutineScope.launch(Dispatchers.IO) {
                                                try {
                                                    val res = RetrofitClient.apiService.toggleRentalLock(com.simats.growise.data.model.ToggleRentalRequest(itemId = item.id, lockStatus = isLocked))
                                                    withContext(Dispatchers.Main) { if (res.success) fetchMyData() }
                                                } catch (e: Exception) {}
                                            }
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- PREMIUM ADD MODAL DIALOG ---
        if (showAddModal) {
            Dialog(onDismissRequest = { showAddModal = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 24.dp),
                    shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Post Equipment", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2C1810))
                            IconButton(onClick = { showAddModal = false }) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFF7D685E)) }
                        }
                        Spacer(modifier = Modifier.height(18.dp))

                        Box(modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFF7F2EE)).border(BorderStroke(1.5.dp, com.simats.growise.ui.theme.GoldenYellow.copy(alpha = 0.8f)), RoundedCornerShape(20.dp)).clickable { galleryLauncher.launch("image/*") }, contentAlignment = Alignment.Center) {
                            if (capturedBitmap != null) {
                                Image(bitmap = capturedBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = com.simats.growise.ui.theme.TerracottaPrimary, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Upload Equipment Photo", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A463D))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val softInputColors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF7F2EE), unfocusedContainerColor = Color(0xFFF7F2EE), disabledContainerColor = Color(0xFFF7F2EE),
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
                            focusedLabelColor = com.simats.growise.ui.theme.TerracottaPrimary, unfocusedLabelColor = Color(0xFF7D685E)
                        )

                        ExposedDropdownMenuBox(expanded = isAddCatExpanded, onExpandedChange = { isAddCatExpanded = !isAddCatExpanded }, modifier = Modifier.fillMaxWidth()) {
                            TextField(value = selectedAddCategory, onValueChange = {}, readOnly = true, label = { Text("Category") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAddCatExpanded) }, colors = softInputColors, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(56.dp).menuAnchor())
                            ExposedDropdownMenu(expanded = isAddCatExpanded, onDismissRequest = { isAddCatExpanded = false }, modifier = Modifier.background(Color.White)) {
                                addCategories.forEach { cat -> DropdownMenuItem(text = { Text(cat, fontWeight = FontWeight.Medium) }, onClick = { selectedAddCategory = cat; isAddCatExpanded = false }) }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        TextField(value = eqName, onValueChange = { eqName = it }, label = { Text("Equipment Name") }, placeholder = { Text("e.g., Mahindra Tractor") }, colors = softInputColors, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true)

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextField(value = ratePerHour, onValueChange = { ratePerHour = it }, label = { Text("Rate / Hour (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = softInputColors, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp), singleLine = true)
                            TextField(value = ratePerDay, onValueChange = { ratePerDay = it }, label = { Text("Rate / Day (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = softInputColors, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp), singleLine = true)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // New Lat/Lon fields with Live Fetch Button
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextField(value = assetLat, onValueChange = { assetLat = it }, label = { Text("Latitude") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = softInputColors, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp), singleLine = true)
                            TextField(value = assetLon, onValueChange = { assetLon = it }, label = { Text("Longitude") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = softInputColors, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp), singleLine = true)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                    isFetchingAssetLocation = true
                                    try {
                                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                            .addOnSuccessListener { location ->
                                                isFetchingAssetLocation = false
                                                if (location != null) {
                                                    assetLat = location.latitude.toString()
                                                    assetLon = location.longitude.toString()
                                                }
                                            }
                                            .addOnFailureListener { isFetchingAssetLocation = false }
                                    } catch (e: SecurityException) { isFetchingAssetLocation = false }
                                } else {
                                    assetLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF7F2EE), contentColor = com.simats.growise.ui.theme.TerracottaPrimary)
                        ) {
                            if (isFetchingAssetLocation) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = com.simats.growise.ui.theme.TerracottaPrimary, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Fetch Current GPS", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        if (isPublishing) {
                            Box(modifier = Modifier.fillMaxWidth().height(52.dp).background(Color(0xFFF7F2EE), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_agri_loading),
                                    contentDescription = "Loading",
                                    modifier = Modifier.size(32.dp).rotate(rotationAngle)
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (eqName.isNotBlank() && ratePerHour.isNotBlank() && ratePerDay.isNotBlank() && capturedBitmap != null) {
                                        isPublishing = true
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val file = getFileFromBitmap(capturedBitmap!!)
                                                var imagePart: MultipartBody.Part? = null
                                                if (file != null) {
                                                    val reqFile = file.asRequestBody("image/png".toMediaTypeOrNull())
                                                    imagePart = MultipartBody.Part.createFormData("image", file.name, reqFile)
                                                }

                                                val finalLat = if (assetLat.isNotBlank()) assetLat else "0.0"
                                                val finalLon = if (assetLon.isNotBlank()) assetLon else "0.0"

                                                val eBody = userEmail.toRequestBody("text/plain".toMediaTypeOrNull())
                                                val nBody = eqName.toRequestBody("text/plain".toMediaTypeOrNull())
                                                val cBody = selectedAddCategory.toRequestBody("text/plain".toMediaTypeOrNull())
                                                val rphBody = ratePerHour.toRequestBody("text/plain".toMediaTypeOrNull())
                                                val rpdBody = ratePerDay.toRequestBody("text/plain".toMediaTypeOrNull())
                                                val latBody = finalLat.toRequestBody("text/plain".toMediaTypeOrNull())
                                                val lonBody = finalLon.toRequestBody("text/plain".toMediaTypeOrNull())

                                                val res = RetrofitClient.apiService.addRentalItemWithImage(eBody, nBody, cBody, rphBody, rpdBody, latBody, lonBody, imagePart)
                                                withContext(Dispatchers.Main) {
                                                    isPublishing = false
                                                    if (res.success) {
                                                        eqName = ""; ratePerHour = ""; ratePerDay = ""; assetLat = ""; assetLon = ""; capturedBitmap = null; showAddModal = false
                                                        activeTab = 1
                                                        fetchMyData()
                                                    }
                                                }
                                            } catch (e: Exception) { isPublishing = false }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp).shadow(6.dp, RoundedCornerShape(16.dp), spotColor = com.simats.growise.ui.theme.TerracottaPrimary),
                                shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = com.simats.growise.ui.theme.TerracottaPrimary, contentColor = Color.White)
                            ) {
                                Text("Post Equipment", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BrowseCard(item: RentalItemResponse, baseUrl: String, context: Context) {
    val coroutineScope = rememberCoroutineScope()
    val sharedPref = remember { context.getSharedPreferences("GroWiseSession", Context.MODE_PRIVATE) }
    val userEmail = sharedPref.getString("USER_EMAIL", "") ?: ""
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var showDistanceModal by remember { mutableStateOf(false) }
    var dynamicDistanceText by remember { mutableStateOf<String?>(null) }
    var isFetchingLocation by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "rotation"
    )

    // Permission launcher for Current Location in Card
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isFetchingLocation = true
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        isFetchingLocation = false
                        if (location != null) {
                            val dist = calculateHaversineDistance(location.latitude, location.longitude, item.latitude, item.longitude)
                            dynamicDistanceText = String.format(Locale.US, "%.1f km away (Live)", dist)
                            showDistanceModal = false
                        } else {
                            dynamicDistanceText = "Live location not found"
                        }
                    }
                    .addOnFailureListener {
                        isFetchingLocation = false
                        dynamicDistanceText = "Failed to fetch GPS"
                    }
            } catch (e: SecurityException) {
                isFetchingLocation = false
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.5.dp, com.simats.growise.ui.theme.GoldenYellow.copy(alpha = 0.8f)), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(90.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, Color(0xFFD6C8BE), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    if (item.imageUrl.isNotEmpty()) AsyncImage(model = baseUrl + item.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                    else Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.equipmentName, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF2C1810), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (item.ratePerHour > 0 && item.ratePerDay > 0) {
                        Text(text = "₹${item.ratePerHour}/hr • ₹${item.ratePerDay}/day", fontSize = 15.sp, color = com.simats.growise.ui.theme.TerracottaPrimary, fontWeight = FontWeight.ExtraBold)
                    } else if (item.ratePerHour > 0) {
                        Text(text = "₹${item.ratePerHour}/hr", fontSize = 15.sp, color = com.simats.growise.ui.theme.TerracottaPrimary, fontWeight = FontWeight.ExtraBold)
                    } else if (item.ratePerDay > 0) {
                        Text(text = "₹${item.ratePerDay}/day", fontSize = 15.sp, color = com.simats.growise.ui.theme.TerracottaPrimary, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))


                }
            }
            Divider(modifier = Modifier.padding(vertical = 16.dp), color = Color(0xFFF0E5DB))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.ownerProfileUrl.isNotEmpty()) AsyncImage(model = baseUrl + item.ownerProfileUrl, contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    else Box(modifier = Modifier.size(36.dp).background(Color(0xFFE0E0E0), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = item.ownerName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C1810))
                            Spacer(modifier = Modifier.width(4.dp))
                            if (item.isVerified) Icon(Icons.Filled.Verified, contentDescription = "Verified", tint = Color(0xFF1976D2), modifier = Modifier.size(14.dp))
                        }
                        Text(text = "Asset Owner", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                if (item.isLocked) {
                    Button(
                        onClick = { /* Disabled action */ },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBDBDBD), contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), modifier = Modifier.height(38.dp),
                        enabled = false
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("In Use", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconButton(
                            onClick = {
                                if(item.ownerPhone.isNotEmpty()) {
                                    val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:${item.ownerPhone}") }
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.background(Color(0xFFE0E0E0), CircleShape).size(38.dp)
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = "Call", tint = com.simats.growise.ui.theme.TerracottaPrimary, modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = {
                                if (item.latitude != 0.0 && item.longitude != 0.0) {
                                    try {
                                        val uri = Uri.parse("google.navigation:q=${item.latitude},${item.longitude}")
                                        val intent = Intent(Intent.ACTION_VIEW, uri)
                                        intent.setPackage("com.google.android.apps.maps")
                                        if (intent.resolveActivity(context.packageManager) != null) {
                                            context.startActivity(intent)
                                        } else {
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${item.latitude},${item.longitude}"))
                                            context.startActivity(browserIntent)
                                        }
                                    } catch (e: Exception) {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${item.latitude},${item.longitude}"))
                                        context.startActivity(browserIntent)
                                    }
                                }
                            },
                            modifier = Modifier.background(Color(0xFFE8F5E9), CircleShape).size(38.dp)
                        ) {
                            Icon(Icons.Filled.Navigation, contentDescription = "Navigate", tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // --- Distance Calculation Premium Modal ---
        if (showDistanceModal) {
            Dialog(onDismissRequest = { if (!isFetchingLocation) showDistanceModal = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                if (isFetchingLocation) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Image(painter = painterResource(id = R.drawable.ic_agri_loading), contentDescription = "Loading", modifier = Modifier.size(64.dp).rotate(rotationAngle))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Calculating exact distance...", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                } else {
                    // Evaluate stored SharedPreferences
                    val pHomeLat = sharedPref.getString("HOME_LAT", "0.0")?.toDoubleOrNull() ?: 0.0
                    val pHomeLon = sharedPref.getString("HOME_LON", "0.0")?.toDoubleOrNull() ?: 0.0
                    val pFarmLat = sharedPref.getString("FARM_LAT", "0.0")?.toDoubleOrNull() ?: 0.0
                    val pFarmLon = sharedPref.getString("FARM_LON", "0.0")?.toDoubleOrNull() ?: 0.0
                    val pIsSame = sharedPref.getBoolean("IS_SAME_ADDRESS", false)

                    val validHome = pHomeLat != 0.0 && pHomeLon != 0.0
                    val validFarm = pFarmLat != 0.0 && pFarmLon != 0.0

                    // True if user checked 'Same Address' OR only one address is provided OR both are identical
                    val isSingleAddress = pIsSame || (validHome && !validFarm) || (!validHome && validFarm) || (validHome && validFarm && pHomeLat == pFarmLat && pHomeLon == pFarmLon)

                    Card(
                        modifier = Modifier.fillMaxWidth(0.9f),
                        shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Fetch Distance From", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2C1810))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Select a location point to calculate distance to this equipment.", fontSize = 13.sp, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(modifier = Modifier.height(24.dp))

                            // Always show Current Live Location
                            Button(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                        isFetchingLocation = true
                                        try {
                                            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                                .addOnSuccessListener { location ->
                                                    isFetchingLocation = false
                                                    if (location != null) {
                                                        val dist = calculateHaversineDistance(location.latitude, location.longitude, item.latitude, item.longitude)
                                                        dynamicDistanceText = String.format(Locale.US, "%.1f km away (Live)", dist)
                                                        showDistanceModal = false
                                                    } else {
                                                        dynamicDistanceText = "Live GPS unavailable"
                                                        showDistanceModal = false
                                                    }
                                                }
                                                .addOnFailureListener {
                                                    isFetchingLocation = false
                                                    dynamicDistanceText = "Failed to fetch GPS"
                                                    showDistanceModal = false
                                                }
                                        } catch (e: SecurityException) { isFetchingLocation = false }
                                    } else {
                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = com.simats.growise.ui.theme.TerracottaPrimary)
                            ) {
                                Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("📍 Current Live Location", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (isSingleAddress && (validHome || validFarm)) {
                                // Show only ONE button if they have identical/single addresses
                                val targetLat = if (validFarm) pFarmLat else pHomeLat
                                val targetLon = if (validFarm) pFarmLon else pHomeLon

                                Button(
                                    onClick = {
                                        val dist = calculateHaversineDistance(targetLat, targetLon, item.latitude, item.longitude)
                                        dynamicDistanceText = String.format(Locale.US, "%.1f km away (Saved)", dist)
                                        showDistanceModal = false
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = com.simats.growise.ui.theme.TerracottaPrimary)
                                ) {
                                    Text("📍 Saved Address", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                }
                            } else if (!isSingleAddress && validHome && validFarm) {
                                // Show BOTH buttons if they have distinct valid addresses
                                Button(
                                    onClick = {
                                        val dist = calculateHaversineDistance(pHomeLat, pHomeLon, item.latitude, item.longitude)
                                        dynamicDistanceText = String.format(Locale.US, "%.1f km away (Home)", dist)
                                        showDistanceModal = false
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = com.simats.growise.ui.theme.TerracottaPrimary)
                                ) {
                                    Text("🏡 Saved Home Address", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        val dist = calculateHaversineDistance(pFarmLat, pFarmLon, item.latitude, item.longitude)
                                        dynamicDistanceText = String.format(Locale.US, "%.1f km away (Farm)", dist)
                                        showDistanceModal = false
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = com.simats.growise.ui.theme.TerracottaPrimary)
                                ) {
                                    Text("🚜 Saved Farm Address", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = { showDistanceModal = false }) {
                                Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MyEquipmentCard(item: RentalItemResponse, baseUrl: String, onToggleLock: (Boolean) -> Unit) {
    // Local state for instant UI update
    var isLockedState by remember { mutableStateOf(item.isLocked) }

    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.5.dp, com.simats.growise.ui.theme.GoldenYellow.copy(alpha = 0.8f)), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, Color(0xFFD6C8BE), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                if (item.imageUrl.isNotEmpty()) AsyncImage(model = baseUrl + item.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                else Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(36.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.equipmentName, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF2C1810))
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "₹${item.ratePerHour}/hr • ₹${item.ratePerDay}/day", fontSize = 14.sp, color = com.simats.growise.ui.theme.TerracottaPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = item.category, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
            }

            // Added feature: Lock the truck for the owner so others can't call
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 8.dp)) {
                Switch(
                    checked = isLockedState,
                    onCheckedChange = { isChecked ->
                        isLockedState = isChecked // Update UI instantly
                        onToggleLock(isChecked)    // Sync with backend
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFD32F2F), // Red for Locked/In Use
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFF4CAF50) // Green for Available
                    ),
                    modifier = Modifier.scale(0.8f)
                )
                Text(
                    text = if (item.isLocked) "Locked" else "Available",
                    fontSize = 11.sp,
                    color = if (item.isLocked) Color(0xFFD32F2F) else Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}