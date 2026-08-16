package com.simats.growise.farmer

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.simats.growise.R
import com.simats.growise.data.model.InventoryItemResponse
import com.simats.growise.data.network.RetrofitClient
import com.simats.growise.ui.theme.TerracottaPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

fun getDiffInHours(expiryDateStr: String): Long {
    if (expiryDateStr.isEmpty()) return 1000L
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val expDate = format.parse(expiryDateStr) ?: return 1000L
        TimeUnit.MILLISECONDS.toHours(expDate.time - System.currentTimeMillis())
    } catch (e: Exception) { 1000L }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FListProduct(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPref = remember { context.getSharedPreferences("GroWiseSession", Context.MODE_PRIVATE) }
    val userEmail = remember { sharedPref.getString("USER_EMAIL", "farmer@growise.com") ?: "farmer@growise.com" }

    val baseUrl = remember { RetrofitClient.BASE_URL.removeSuffix("/") }

    var activeListings by remember { mutableStateOf<List<InventoryItemResponse>>(emptyList()) }
    var alertBannerMessage by remember { mutableStateOf<String?>(null) }
    var isSynchronizing by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    var showAddModal by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<InventoryItemResponse?>(null) }

    val categories = listOf("Vegetables", "Fruits", "Grains", "Spices", "Flowers")
    var selectedCategory by remember { mutableStateOf(categories[0]) }
    var isCategoryDropdownExpanded by remember { mutableStateOf(false) }

    var cropName by remember { mutableStateOf("") }
    var pricePerKg by remember { mutableStateOf("") }
    var availableKg by remember { mutableStateOf("") }
    var moq by remember { mutableStateOf("0") }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val todayStr = remember { dateFormat.format(Date()) }
    var harvestDate by remember { mutableStateOf(todayStr) }
    var expiryDate by remember { mutableStateOf("") }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }

    val calendar = remember { Calendar.getInstance() }
    val harvestDatePicker = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                harvestDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
    val expiryDatePicker = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                expiryDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

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
            val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}.png")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            file
        } catch (e: Exception) { null }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            capturedImageUri = it
            capturedBitmap = getBitmapFromUri(it)
        }
    }

    val softInputColors = TextFieldDefaults.colors(
        focusedContainerColor = Color(0xFFF7F2EE),
        unfocusedContainerColor = Color(0xFFF7F2EE),
        disabledContainerColor = Color(0xFFF7F2EE),
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        cursorColor = Color(0xFFD85A38),
        focusedLabelColor = Color(0xFFD85A38),
        unfocusedLabelColor = Color(0xFF7D685E)
    )

    fun refreshInventoryFeed(showLoadingAnimation: Boolean = false) {
        coroutineScope.launch(Dispatchers.IO) {
            if (showLoadingAnimation) withContext(Dispatchers.Main) { isRefreshing = true }
            try {
                val items = RetrofitClient.apiService.fetchInventory(com.simats.growise.data.model.EmailRequest(email = userEmail))
                withContext(Dispatchers.Main) {
                    activeListings = items
                    isRefreshing = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isRefreshing = false }
            }
        }
    }

    LaunchedEffect(Unit) { refreshInventoryFeed(showLoadingAnimation = true) }

    val infiniteTransition = rememberInfiniteTransition(label = "agri_loading")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My Inventory",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = Color(0xFF2C1810)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF2C1810))
                    }
                },
                actions = {
                    Surface(
                        onClick = { showAddModal = true },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFD85A38),
                        shadowElevation = 4.dp,
                        modifier = Modifier.padding(end = 12.dp).height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("List Produce", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFFFFBF7)),
                modifier = Modifier.shadow(elevation = 2.dp, spotColor = Color(0x1A000000))
            )
        },
        containerColor = Color(0xFFFFFBF7)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = isRefreshing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF0E5DE))
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_agri_loading),
                            contentDescription = "Loading",
                            modifier = Modifier.size(28.dp).rotate(rotationAngle)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Syncing Live Batches...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A463D))
                    }
                }

                var selectedTabIndex by remember { mutableIntStateOf(0) }
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color(0xFFFFFBF7),
                    contentColor = TerracottaPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = TerracottaPrimary
                        )
                    }
                ) {
                    Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("Active Batches", fontWeight = FontWeight.Bold) })
                    Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("Donation Hub", fontWeight = FontWeight.Bold) })
                }

                val activeBatches = activeListings.filter { !it.donatedToNgo && getDiffInHours(it.expiryDate) > 48 }
                val donationBatches = activeListings.filter { it.donatedToNgo || getDiffInHours(it.expiryDate) <= 48 }
                val displayList = if (selectedTabIndex == 0) activeBatches else donationBatches

                if (displayList.isEmpty() && !isRefreshing) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.5.dp, Color(0xFFE5D5C5)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier.size(80.dp).background(Color(0xFFF7F2EE), CircleShape).border(1.dp, Color(0xFFD6C8BE), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.app_logo),
                                        contentDescription = null,
                                        modifier = Modifier.size(54.dp).clip(CircleShape)
                                    )
                                }
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = if (selectedTabIndex == 0) "No Active Batches" else "No Donations",
                                    color = Color(0xFF2C1810),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (selectedTabIndex == 0) "Your market grid is empty. Publish your first surplus crop." else "Crops nearing 48 hrs to expiry will auto-move here.",
                                    color = Color(0xFF7D685E),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                if (selectedTabIndex == 0) {
                                    Button(
                                        onClick = { showAddModal = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD85A38)),
                                        shape = RoundedCornerShape(16.dp),
                                        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                                    ) {
                                        Icon(Icons.Filled.AddCircleOutline, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "List Your First Batch Now",
                                            fontSize = 15.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
                    ) {
                        item {
                            AnimatedVisibility(
                                visible = alertBannerMessage != null,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = Color(0xFFD85A38))
                                        .clickable { alertBannerMessage = null },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD85A38)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.LocalActivity, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = alertBannerMessage ?: "",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (selectedTabIndex == 0) "Live Batches" else "Rescue Pings",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF2C1810)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { refreshInventoryFeed(showLoadingAnimation = true) }
                                ) {
                                    Text(
                                        text = "${displayList.size} Items",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD85A38)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color(0xFF7D685E), modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        items(displayList, key = { it.id }) { listing ->
                            val status = listing.expiryStatus
                            val isNearExpiry = status == "Near Expiry"
                            val isClearance = status == "Clearance Sale"
                            val isNGOFeed = status == "ACTIVE NGO FEED"
                            
                            val availableQty = listing.availableKg
                            val displayQty = availableQty

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp), spotColor = Color(0x0F000000)),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.5.dp, Color(0xFFE5D5C5))
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(Color(0xFFF7F2EE))
                                                .border(1.5.dp, Color(0xFFE5D5C5), RoundedCornerShape(14.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (listing.imageUrl.isNotEmpty()) {
                                                AsyncImage(
                                                    model = baseUrl + listing.imageUrl,
                                                    contentDescription = listing.cropName,
                                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Image(
                                                    painter = painterResource(id = R.drawable.app_logo),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(40.dp),
                                                    contentScale = ContentScale.Fit
                                                )
                                            }
                                            if (isNearExpiry && selectedTabIndex == 0) {
                                                Box(modifier = Modifier.align(Alignment.TopStart).padding(2.dp).background(Color(0xFFFF9800), RoundedCornerShape(6.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                                    Text("30% OFF", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                }
                                            } else if (isClearance && selectedTabIndex == 0) {
                                                Box(modifier = Modifier.align(Alignment.TopStart).padding(2.dp).background(Color(0xFFD32F2F), RoundedCornerShape(6.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                                    Text("50% OFF", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = listing.cropName,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 17.sp,
                                                    color = Color(0xFF2C1810),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (isNGOFeed) {
                                                    Text(
                                                        text = "NGO Donation",
                                                        fontSize = 15.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold
                                                    )
                                                } else if (isNearExpiry || isClearance) {
                                                    Text(
                                                        text = "₹${listing.pricePerKg}",
                                                        fontSize = 13.sp, color = Color(0xFF9E8B80),
                                                        textDecoration = TextDecoration.LineThrough
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "₹${listing.discountedPrice}/kg",
                                                        fontSize = 16.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.ExtraBold
                                                    )
                                                } else {
                                                    Text(
                                                        text = "₹${listing.pricePerKg}/kg",
                                                        fontSize = 15.sp, color = Color(0xFF5A463D), fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))

                                            if (isNGOFeed) {
                                                Text(
                                                    text = "Stock: $displayQty kg available",
                                                    fontSize = 12.sp, color = Color(0xFF7D685E), fontWeight = FontWeight.Medium
                                                )
                                            } else {
                                                Text(
                                                    text = "Stock: $displayQty kg • MOQ: ${listing.moq} kg",
                                                    fontSize = 12.sp, color = Color(0xFF7D685E), fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }

                                        if (selectedTabIndex == 0) {
                                            if (!isNGOFeed) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .background(Color(0xFFF7F2EE), CircleShape)
                                                        .clickable { editingItem = listing },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Color(0xFFD85A38), modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        } else {
                                            if (getDiffInHours(listing.expiryDate) <= 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .background(Color(0xFFFFEBEE), CircleShape)
                                                        .clickable {
                                                            activeListings = activeListings.filter { it.id != listing.id }
                                                            coroutineScope.launch(Dispatchers.IO) {
                                                                try { RetrofitClient.apiService.deleteInventoryItem(com.simats.growise.data.model.ItemIdRequest(itemId = listing.id)) } catch (e: Exception) {}
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(20.dp))
                                                }
                                            } else {
                                                val badgeColor = when (status) {
                                                    "ACTIVE NGO FEED" -> Color(0xFF7C3AED) // NGO Purple
                                                    "Clearance Sale" -> Color(0xFFD32F2F) // Deep Orange-Red
                                                    "Near Expiry" -> Color(0xFFFF9800) // Orange
                                                    else -> Color(0xFF4CAF50) // Green
                                                }
                                                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(badgeColor).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                    Text(text = status.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                                }
                                            }
                                        }
                                    }

                                    if (selectedTabIndex == 0 && (isNearExpiry || isClearance)) {
                                        HorizontalDivider(color = Color(0xFFEFE8E2), thickness = 1.dp)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFFFF3E0))
                                                .clickable {
                                                    activeListings = activeListings.map { if (it.id == listing.id) it.copy(donatedToNgo = true) else it }
                                                    alertBannerMessage = "Rescue Ping triggered! Moved to Donation Hub."
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        try {
                                                            val url = java.net.URL("$baseUrl/api/ngo/donate-item")
                                                            val conn = url.openConnection() as java.net.HttpURLConnection
                                                            conn.requestMethod = "POST"
                                                            conn.setRequestProperty("Content-Type", "application/json")
                                                            conn.doOutput = true
                                                            val reqBody = org.json.JSONObject().apply { put("itemId", listing.id) }
                                                            conn.outputStream.write(reqBody.toString().toByteArray(Charsets.UTF_8))
                                                            conn.responseCode
                                                        } catch(e: Exception) {}
                                                    }
                                                }
                                                .padding(vertical = 14.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.VolunteerActivism, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Donate to NGO (Rescue Ping)", color = Color(0xFFE65100), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
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

        // --- PREMIUM ADD PRODUCT MODAL DIALOG ---
        if (showAddModal) {
            Dialog(
                onDismissRequest = { showAddModal = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 24.dp).shadow(24.dp, RoundedCornerShape(28.dp)),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("New Product Batch", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2C1810))
                            IconButton(onClick = { showAddModal = false }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFF7D685E))
                            }
                        }
                        Text("Fill batch details to publish to live market grids", fontSize = 12.sp, color = Color(0xFF7D685E), modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(18.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFF7F2EE))
                                .border(1.5.dp, Color(0xFFE5D5C5), RoundedCornerShape(20.dp))
                                .clickable { galleryLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (capturedBitmap != null) {
                                Image(bitmap = capturedBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = Color(0xFFD85A38), modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Tap to Upload Crop Photo", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A463D))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        ExposedDropdownMenuBox(
                            expanded = isCategoryDropdownExpanded,
                            onExpandedChange = { isCategoryDropdownExpanded = !isCategoryDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextField(
                                value = selectedCategory, onValueChange = {}, readOnly = true,
                                label = { Text("Produce Category") },
                                leadingIcon = { Icon(Icons.Filled.Layers, contentDescription = null, tint = Color(0xFFD85A38)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCategoryDropdownExpanded) },
                                colors = softInputColors,
                                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = isCategoryDropdownExpanded, onDismissRequest = { isCategoryDropdownExpanded = false }, modifier = Modifier.background(Color.White)) {
                                categories.forEach { cat ->
                                    DropdownMenuItem(text = { Text(cat, fontWeight = FontWeight.Medium) }, onClick = { selectedCategory = cat; isCategoryDropdownExpanded = false })
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextField(
                            value = cropName, onValueChange = { cropName = it },
                            label = { Text("Crop Name") }, placeholder = { Text("e.g., Organic Red Tomatoes") },
                            leadingIcon = { Icon(Icons.Filled.LocalOffer, contentDescription = null, tint = Color(0xFFD85A38)) },
                            colors = softInputColors,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextField(
                                value = pricePerKg, onValueChange = { pricePerKg = it },
                                label = { Text("Price/kg") }, placeholder = { Text("₹0.00") },
                                leadingIcon = { Icon(Icons.Filled.CurrencyRupee, contentDescription = null, tint = Color(0xFFD85A38)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = softInputColors,
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), singleLine = true
                            )
                            TextField(
                                value = availableKg, onValueChange = { availableKg = it },
                                label = { Text("Stock (kg)") }, placeholder = { Text("0.0") },
                                leadingIcon = { Icon(Icons.Filled.Scale, contentDescription = null, tint = Color(0xFFD85A38)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = softInputColors,
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextField(
                                value = moq, onValueChange = { moq = it },
                                label = { Text("MOQ (kg)") }, placeholder = { Text("0") },
                                leadingIcon = { Icon(Icons.Filled.Inventory2, contentDescription = null, tint = Color(0xFFD85A38)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = softInputColors,
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), singleLine = true
                            )
                            Box(modifier = Modifier.weight(1f).clickable { harvestDatePicker.show() }) {
                                TextField(
                                    value = harvestDate, onValueChange = {}, readOnly = true, enabled = false,
                                    label = { Text("Harvest Date") }, leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = Color(0xFFD85A38)) },
                                    colors = softInputColors.copy(disabledTextColor = Color(0xFF2C1810), disabledLabelColor = Color(0xFFD85A38), disabledIndicatorColor = Color.Transparent),
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(modifier = Modifier.fillMaxWidth().clickable { expiryDatePicker.show() }) {
                            TextField(
                                value = expiryDate, onValueChange = {}, readOnly = true, enabled = false,
                                label = { Text("Expiry (YYYY-MM-DD)") }, placeholder = { Text("YYYY-MM-DD") },
                                leadingIcon = { Icon(Icons.Filled.EventBusy, contentDescription = null, tint = Color(0xFFD85A38)) },
                                colors = softInputColors.copy(disabledTextColor = Color(0xFF2C1810), disabledLabelColor = Color(0xFFD85A38), disabledIndicatorColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Button(
                            onClick = {
                                val cap = availableKg.toDoubleOrNull() ?: 0.0
                                val parsedMoq = moq.toDoubleOrNull() ?: 0.0
                                val finalMoq = if (parsedMoq > cap && cap > 0) cap else parsedMoq

                                if (cropName.isNotBlank() && pricePerKg.isNotBlank() && availableKg.isNotBlank() && expiryDate.isNotBlank()) {
                                    isSynchronizing = true
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            var imagePart: MultipartBody.Part? = null
                                            if (capturedBitmap != null) {
                                                val file = getFileFromBitmap(capturedBitmap!!)
                                                if (file != null) {
                                                    val requestFile = file.asRequestBody("image/png".toMediaTypeOrNull())
                                                    imagePart = MultipartBody.Part.createFormData("image", file.name, requestFile)
                                                }
                                            }

                                            val emailBody = userEmail.toRequestBody("text/plain".toMediaTypeOrNull())
                                            val catBody = selectedCategory.toRequestBody("text/plain".toMediaTypeOrNull())
                                            val nameBody = cropName.toRequestBody("text/plain".toMediaTypeOrNull())
                                            val priceBody = pricePerKg.toRequestBody("text/plain".toMediaTypeOrNull())
                                            val availBody = cap.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                                            val moqBody = finalMoq.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                                            val harvBody = harvestDate.toRequestBody("text/plain".toMediaTypeOrNull())
                                            val expBody = expiryDate.toRequestBody("text/plain".toMediaTypeOrNull())

                                            val res = RetrofitClient.apiService.addInventoryItemWithImage(
                                                emailBody, catBody, nameBody, priceBody, availBody, moqBody, harvBody, expBody, imagePart
                                            )

                                            withContext(Dispatchers.Main) {
                                                isSynchronizing = false
                                                if (res.success) {
                                                    cropName = ""; pricePerKg = ""; availableKg = ""; moq = "0"; capturedBitmap = null; capturedImageUri = null
                                                    showAddModal = false
                                                    refreshInventoryFeed(showLoadingAnimation = true)
                                                }
                                            }
                                        } catch (e: Exception) { isSynchronizing = false }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD85A38), contentColor = Color.White),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                            enabled = !isSynchronizing
                        ) {
                            if (isSynchronizing) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_agri_loading),
                                    contentDescription = "Loading",
                                    modifier = Modifier.size(24.dp).rotate(rotationAngle)
                                )
                            } else {
                                Text("Publish to Live Market", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // --- PREMIUM EDIT MODAL DIALOG ---
        if (editingItem != null) {
            var editPrice by remember { mutableStateOf(if(editingItem!!.pricePerKg == 0.0) "" else editingItem!!.pricePerKg.toString()) }
            var editStock by remember { mutableStateOf(if(editingItem!!.availableKg == 0.0) "" else editingItem!!.availableKg.toString()) }
            var editMoq by remember { mutableStateOf(if(editingItem!!.moq == 0.0) "0" else editingItem!!.moq.toString()) }
            var editHarvest by remember { mutableStateOf(editingItem!!.harvestDate) }
            var editExpiry by remember { mutableStateOf(editingItem!!.expiryDate) }

            var editCapturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
            val editGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                uri?.let { editCapturedBitmap = getBitmapFromUri(it) }
            }

            val editHarvestDatePicker = remember {
                val calParts = editHarvest.split("-")
                val y = calParts.getOrNull(0)?.toIntOrNull() ?: calendar.get(Calendar.YEAR)
                val m = (calParts.getOrNull(1)?.toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)) - 1
                val d = calParts.getOrNull(2)?.toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH)
                DatePickerDialog(context, { _, year, month, dayOfMonth -> editHarvest = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth) }, y, m, d)
            }

            val editExpiryDatePicker = remember {
                val calParts = editExpiry.split("-")
                val y = calParts.getOrNull(0)?.toIntOrNull() ?: calendar.get(Calendar.YEAR)
                val m = (calParts.getOrNull(1)?.toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)) - 1
                val d = calParts.getOrNull(2)?.toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH)
                DatePickerDialog(context, { _, year, month, dayOfMonth -> editExpiry = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth) }, y, m, d)
            }

            Dialog(
                onDismissRequest = { editingItem = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 24.dp).shadow(24.dp, RoundedCornerShape(28.dp)),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Edit ${editingItem!!.cropName}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2C1810))
                            IconButton(onClick = { editingItem = null }) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFF7D685E)) }
                        }
                        Text("Update crop details and replacement photos below.", fontSize = 12.sp, color = Color(0xFF7D685E), modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(18.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFF7F2EE))
                                .border(1.5.dp, Color(0xFFE5D5C5), RoundedCornerShape(20.dp))
                                .clickable { editGalleryLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (editCapturedBitmap != null) {
                                Image(bitmap = editCapturedBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else if (editingItem!!.imageUrl.isNotEmpty()) {
                                AsyncImage(model = baseUrl + editingItem!!.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                Box(modifier = Modifier.fillMaxSize().background(Color(0x40000000)), contentAlignment = Alignment.Center) {
                                    Text("Tap to Change Photo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = Color(0xFFD85A38), modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Tap to Upload New Photo", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A463D))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextField(
                                value = editPrice, onValueChange = { editPrice = it },
                                label = { Text("Price/kg") }, leadingIcon = { Icon(Icons.Filled.CurrencyRupee, contentDescription = null, tint = Color(0xFFD85A38)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = softInputColors,
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), singleLine = true
                            )
                            TextField(
                                value = editStock, onValueChange = { editStock = it },
                                label = { Text("Stock (kg)") }, leadingIcon = { Icon(Icons.Filled.Scale, contentDescription = null, tint = Color(0xFFD85A38)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = softInputColors,
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextField(
                                value = editMoq, onValueChange = { editMoq = it },
                                label = { Text("MOQ (kg)") }, leadingIcon = { Icon(Icons.Filled.Inventory2, contentDescription = null, tint = Color(0xFFD85A38)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = softInputColors,
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), singleLine = true
                            )
                            Box(modifier = Modifier.weight(1f).clickable { editHarvestDatePicker.show() }) {
                                TextField(
                                    value = editHarvest, onValueChange = {}, readOnly = true, enabled = false,
                                    label = { Text("Harvest Date") }, leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = Color(0xFFD85A38)) },
                                    colors = softInputColors.copy(disabledTextColor = Color(0xFF2C1810), disabledLabelColor = Color(0xFFD85A38), disabledIndicatorColor = Color.Transparent),
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(modifier = Modifier.fillMaxWidth().clickable { editExpiryDatePicker.show() }) {
                            TextField(
                                value = editExpiry, onValueChange = {}, readOnly = true, enabled = false,
                                label = { Text("Expiry (YYYY-MM-DD)") }, leadingIcon = { Icon(Icons.Filled.EventBusy, contentDescription = null, tint = Color(0xFFD85A38)) },
                                colors = softInputColors.copy(disabledTextColor = Color(0xFF2C1810), disabledLabelColor = Color(0xFFD85A38), disabledIndicatorColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))
                        var isSavingEdit by remember { mutableStateOf(false) }

                        Button(
                            onClick = {
                                isSavingEdit = true
                                val p = editPrice.toDoubleOrNull() ?: editingItem!!.pricePerKg
                                val s = editStock.toDoubleOrNull() ?: editingItem!!.availableKg
                                val m = editMoq.toDoubleOrNull() ?: editingItem!!.moq

                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        var imagePart: MultipartBody.Part? = null
                                        if (editCapturedBitmap != null) {
                                            val file = getFileFromBitmap(editCapturedBitmap!!)
                                            if (file != null) {
                                                val requestFile = file.asRequestBody("image/png".toMediaTypeOrNull())
                                                imagePart = MultipartBody.Part.createFormData("image", file.name, requestFile)
                                            }
                                        }

                                        val idBody = editingItem!!.id.toRequestBody("text/plain".toMediaTypeOrNull())
                                        val priceBody = p.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                                        val availBody = s.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                                        val moqBody = m.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                                        val harvBody = editHarvest.toRequestBody("text/plain".toMediaTypeOrNull())
                                        val expBody = editExpiry.toRequestBody("text/plain".toMediaTypeOrNull())

                                        val res = RetrofitClient.apiService.updateInventoryItemWithImage(
                                            idBody, priceBody, availBody, moqBody, harvBody, expBody, imagePart
                                        )

                                        withContext(Dispatchers.Main) {
                                            isSavingEdit = false
                                            if (res.success) {
                                                editingItem = null
                                                refreshInventoryFeed(showLoadingAnimation = true)
                                            }
                                        }
                                    } catch (e: Exception) { isSavingEdit = false }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                            enabled = !isSavingEdit
                        ) {
                            if (isSavingEdit) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_agri_loading),
                                    contentDescription = "Loading",
                                    modifier = Modifier.size(24.dp).rotate(rotationAngle)
                                )
                            } else {
                                Text("Save Changes", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}