package com.simats.growise.user

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simats.growise.R
import com.simats.growise.data.model.InventoryItemResponse
import com.simats.growise.data.model.ProfileDetailsResponse
import com.simats.growise.data.model.ReviewRequest
import com.simats.growise.data.model.ReviewResponse
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private var cachedProfileDetails: ProfileDetailsResponse? = null
private var cachedFarmerInventory: List<InventoryItemResponse> = emptyList()
private var currentFarmerSession: String = ""

private fun calculateDiscount(basePrice: Double, expiryDateStr: String): Pair<Double, Int> {
    if (expiryDateStr.isEmpty()) return Pair(basePrice, 0)
    return try {
        val format = if (expiryDateStr.contains("-")) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val expiryDate = format.parse(expiryDateStr) ?: return Pair(basePrice, 0)
        val diffInMillies = expiryDate.time - System.currentTimeMillis()
        val diffInHours = TimeUnit.MILLISECONDS.toHours(diffInMillies)
        when {
            diffInHours in 73..96 -> Pair(basePrice * 0.7, 30)
            diffInHours in 49..72 -> Pair(basePrice * 0.5, 50)
            else -> Pair(basePrice, 0)
        }
    } catch (e: Exception) { Pair(basePrice, 0) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UFarmerMenuScreen(navController: NavController, farmerEmail: String) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPref = remember { context.getSharedPreferences("GroWiseSession", Context.MODE_PRIVATE) }
    val userEmail = remember { sharedPref.getString("USER_EMAIL", "") ?: "" }
    val userName = remember { sharedPref.getString("USER_NAME", "User") ?: "User" }
    val db = FirebaseFirestore.getInstance()
    val gson = Gson()

    val useCache = currentFarmerSession == farmerEmail
    var farmerProfile by remember { mutableStateOf(if (useCache) cachedProfileDetails else null) }
    var inventory by remember { mutableStateOf(if (useCache) cachedFarmerInventory else emptyList()) }
    var isLoading by remember { mutableStateOf(!useCache) }
    var searchQuery by remember { mutableStateOf("") }
    val categories = listOf("All", "Vegetables", "Fruits", "Grains", "Flowers")
    var selectedCategory by remember { mutableStateOf("All") }
    var isFavorite by remember { mutableStateOf(false) }

    val cachedReviewsJson = sharedPref.getString("REVIEWS_$farmerEmail", "[]")
    val listType = object : TypeToken<List<ReviewResponse>>() {}.type
    var reviewsList by remember { mutableStateOf<List<ReviewResponse>>(gson.fromJson(cachedReviewsJson, listType) ?: emptyList()) }
    var averageRating by remember { mutableStateOf(if (reviewsList.isNotEmpty()) reviewsList.map { it.rating }.average() else 0.0) }
    var isReviewsLoading by remember { mutableStateOf(reviewsList.isEmpty()) }

    var showReviewDialog by remember { mutableStateOf(false) }
    var reviewTextState by remember { mutableStateOf("") }
    var ratingState by remember { mutableStateOf(0) }
    var editingReviewId by remember { mutableStateOf<String?>(null) }

    var showNegotiationDialog by remember { mutableStateOf(false) }
    var selectedItemForDeal by remember { mutableStateOf<InventoryItemResponse?>(null) }
    var inputKgStr by remember { mutableStateOf("") }
    var inputTargetPriceStr by remember { mutableStateOf("") }
    var negotiationStep by remember { mutableStateOf(1) } // 1: Qty, 2: Price
    var isSendingInquiry by remember { mutableStateOf(false) }

    val baseUrl = remember { RetrofitClient.BASE_URL.removeSuffix("/") }
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing)))

    fun fetchReviews() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val list = RetrofitClient.apiService.fetchReviews(com.simats.growise.data.model.FarmerEmailRequest(farmerEmail = farmerEmail))
                withContext(Dispatchers.Main) {
                    reviewsList = list
                    averageRating = if (list.isNotEmpty()) list.map { it.rating }.average() else 0.0
                    sharedPref.edit().putString("REVIEWS_$farmerEmail", gson.toJson(list)).apply()
                    isReviewsLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isReviewsLoading = false }
            }
        }
    }

    LaunchedEffect(farmerEmail) {
        if (!useCache) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val profileRes = RetrofitClient.apiService.retrieveProfileFields(farmerEmail)
                    if (profileRes.isSuccessful) farmerProfile = profileRes.body()

                    val allItems = RetrofitClient.apiService.fetchMarketItems()
                    inventory = allItems.filter { item ->
                        var diffInHours = 1000L
                        if (item.expiryDate.isNotEmpty()) {
                            try {
                                val format = if (item.expiryDate.contains("-")) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                val expDate = format.parse(item.expiryDate)
                                if (expDate != null) {
                                    diffInHours = TimeUnit.MILLISECONDS.toHours(expDate.time - System.currentTimeMillis())
                                }
                            } catch (e: Exception) {}
                        }
                        item.availableKg > 0.0 && item.email == farmerEmail && !item.donatedToNgo && diffInHours > 48
                    }

                    withContext(Dispatchers.Main) {
                        cachedProfileDetails = farmerProfile
                        cachedFarmerInventory = inventory
                        currentFarmerSession = farmerEmail
                        isLoading = false
                    }
                } catch (e: Exception) { withContext(Dispatchers.Main) { isLoading = false } }
            }
        }
        fetchReviews()
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, userEmail, farmerEmail) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val favRes = RetrofitClient.apiService.getFavorites(com.simats.growise.data.model.EmailRequest(email = userEmail))
                        if (favRes.isSuccessful) {
                            val favList = favRes.body() ?: emptyList()
                            val exists = favList.any { it.farmerEmail == farmerEmail }
                            withContext(Dispatchers.Main) { isFavorite = exists }
                        }
                    } catch (e: Exception) {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun toggleFavorite() {
        val previousState = isFavorite
        isFavorite = !isFavorite // Optimistic UI update
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val fName = farmerProfile?.name ?: farmerEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                val fImage = farmerProfile?.profile_image_url ?: ""
                val req = com.simats.growise.data.model.ToggleFavoriteRequest(userEmail, farmerEmail, fName, fImage)
                val res = RetrofitClient.apiService.toggleFavorite(req)
                if (!res.isSuccessful) {
                    withContext(Dispatchers.Main) { isFavorite = previousState } // Revert if failed
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isFavorite = previousState } // Revert if network error
            }
        }
    }

    val filteredInventory = inventory.filter {
        (selectedCategory == "All" || it.category.equals(selectedCategory, ignoreCase = true)) &&
                it.cropName.contains(searchQuery, ignoreCase = true)
    }

    if (showReviewDialog) {
        Dialog(onDismissRequest = { showReviewDialog = false; editingReviewId = null; reviewTextState = ""; ratingState = 0 }) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.RateReview, contentDescription = "Review", tint = TerracottaPrimary, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(if (editingReviewId == null) "Write a Review" else "Edit Review", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TerracottaPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        (1..5).forEach { star ->
                            Icon(imageVector = if (star <= ratingState) Icons.Filled.Star else Icons.Outlined.StarBorder, contentDescription = "Star", tint = GoldenYellow, modifier = Modifier.size(36.dp).clickable { ratingState = star })
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = reviewTextState, onValueChange = { reviewTextState = it },
                        modifier = Modifier.fillMaxWidth().height(100.dp), placeholder = { Text("Share your experience...", color = Color.Gray) },
                        shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TerracottaPrimary, unfocusedBorderColor = PeachBackground)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showReviewDialog = false; editingReviewId = null; ratingState = 0 }) { Text("Cancel", color = Color.Gray) }
                        Button(
                            onClick = {
                                if (ratingState == 0) { Toast.makeText(context, "Select at least 1 star", Toast.LENGTH_SHORT).show(); return@Button }
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val req = ReviewRequest(id = editingReviewId, farmerEmail = farmerEmail, userEmail = userEmail, userName = userName, rating = ratingState.toDouble(), text = reviewTextState, timestamp = System.currentTimeMillis())
                                        if (editingReviewId != null) RetrofitClient.apiService.updateReview(req) else RetrofitClient.apiService.addReview(req)
                                        withContext(Dispatchers.Main) { showReviewDialog = false; editingReviewId = null; ratingState = 0; fetchReviews() }
                                    } catch (e: Exception) {}
                                }
                            },
                            enabled = ratingState > 0 && reviewTextState.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary, contentColor = Color.White, disabledContainerColor = Color.LightGray)
                        ) { Text("Post Review", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    if (showNegotiationDialog && selectedItemForDeal != null) {
        val item = selectedItemForDeal!!
        val (finalBasePrice, _) = calculateDiscount(item.pricePerKg, item.expiryDate)

        Dialog(onDismissRequest = { showNegotiationDialog = false; negotiationStep = 1; inputKgStr = ""; inputTargetPriceStr = "" }) {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(8.dp)) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val fullUrl = if (item.imageUrl.startsWith("http")) item.imageUrl else "${baseUrl}${item.imageUrl}"
                        AsyncImage(model = fullUrl, contentDescription = null, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(item.cropName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.LocalOffer, contentDescription = "Price", tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Market Rate: ₹${String.format(Locale.US, "%.2f", finalBasePrice)}/kg", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    if (negotiationStep == 1) {
                        Text("Step 1: Required Quantity", fontWeight = FontWeight.Bold, color = TerracottaPrimary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        val kgValue = inputKgStr.toDoubleOrNull() ?: 0.0

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { if (kgValue > item.moq) inputKgStr = String.format(Locale.US, "%.1f", kgValue - 1) }, modifier = Modifier.background(PeachBackground, CircleShape)) { Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = TerracottaPrimary) }
                            OutlinedTextField(
                                value = inputKgStr, onValueChange = { inputKgStr = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(120.dp).padding(horizontal = 8.dp), textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                                shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TerracottaPrimary, unfocusedBorderColor = GoldenYellow),
                                placeholder = { Text("Kg", color = Color.LightGray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                            )
                            IconButton(onClick = { if (kgValue < item.availableKg) inputKgStr = String.format(Locale.US, "%.1f", kgValue + 1) else Toast.makeText(context, "Max stock reached", Toast.LENGTH_SHORT).show() }, modifier = Modifier.background(PeachBackground, CircleShape)) { Icon(Icons.Filled.Add, contentDescription = "Increase", tint = TerracottaPrimary) }
                        }

                        val isValidQty = kgValue >= item.moq && kgValue <= item.availableKg

                        if (kgValue > 0 && !isValidQty) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warning, contentDescription = "Warning", tint = Color.Red, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (kgValue < item.moq) "Minimum order is ${item.moq} kg" else "Only ${item.availableKg} kg available", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        AnimatedVisibility(visible = isValidQty) {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp).background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp)).border(1.dp, Color(0xFF2E7D32), RoundedCornerShape(8.dp)).padding(12.dp), contentAlignment = Alignment.Center) {
                                Text("Market Total: ₹${String.format(Locale.US, "%.2f", kgValue * finalBasePrice)}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF2E7D32))
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { showNegotiationDialog = false; inputKgStr = "" }) { Text("Cancel", color = Color.Gray) }
                            Button(
                                onClick = { negotiationStep = 2 },
                                enabled = isValidQty,
                                colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary, contentColor = Color.White, disabledContainerColor = Color.LightGray)
                            ) { Text("Next", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    } else if (negotiationStep == 2) {
                        val kgValue = inputKgStr.toDoubleOrNull() ?: 0.0
                        Text("Step 2: Price Negotiation", fontWeight = FontWeight.Bold, color = TerracottaPrimary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(modifier = Modifier.fillMaxWidth().background(PeachBackground, RoundedCornerShape(8.dp)).padding(12.dp)) {
                            Column {
                                Text("Required: $kgValue kg", fontSize = 13.sp, color = TextDark)
                                Text("Market Rate: ₹${String.format(Locale.US, "%.2f", finalBasePrice)}/kg", fontSize = 13.sp, color = TextDark)
                                Text("Total Market Value: ₹${String.format(Locale.US, "%.2f", kgValue * finalBasePrice)}", fontWeight = FontWeight.Bold, color = TerracottaPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = inputTargetPriceStr, onValueChange = { inputTargetPriceStr = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), label = { Text("Your Target Price (per kg)") },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TerracottaPrimary, unfocusedBorderColor = GoldenYellow),
                            leadingIcon = { Icon(Icons.Filled.LocalOffer, contentDescription = "Price", tint = TerracottaPrimary) }
                        )

                        val targetValue = inputTargetPriceStr.toDoubleOrNull() ?: 0.0
                        if (targetValue > 0) {
                            val diff = finalBasePrice - targetValue
                            val total = targetValue * kgValue
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp)).border(1.dp, Color(0xFF2E7D32), RoundedCornerShape(8.dp)).padding(12.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (diff > 0) "Requesting a discount of ₹${String.format(Locale.US, "%.2f", diff)}/kg" else "Offering above market rate", color = Color(0xFF2E7D32), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Your Expected Total: ₹${String.format(Locale.US, "%.2f", total)}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF2E7D32))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { negotiationStep = 1; inputTargetPriceStr = "" }) { Text("Back", color = Color.Gray) }
                            Button(
                                onClick = {
                                    if (targetValue <= 0) return@Button
                                    isSendingInquiry = true
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val chatId = if (userEmail < farmerEmail) "${userEmail}_$farmerEmail" else "${farmerEmail}_$userEmail"
                                            val messageData = hashMapOf(
                                                "senderId" to userEmail,
                                                "receiverId" to farmerEmail,
                                                "type" to "INQUIRY_CARD",
                                                "cropName" to item.cropName,
                                                "imageUrl" to item.imageUrl,
                                                "kg" to kgValue,
                                                "basePrice" to finalBasePrice,
                                                "targetPrice" to targetValue,
                                                "totalPrice" to (kgValue * targetValue),
                                                "itemId" to item.id,
                                                "status" to "PENDING",
                                                "timestamp" to System.currentTimeMillis()
                                            )
                                            db.collection("chats").document(chatId).collection("messages").add(messageData).await()
                                            db.collection("chats").document(chatId).set(hashMapOf(
                                                "userEmail" to userEmail,
                                                "farmerEmail" to farmerEmail,
                                                "lastMessage" to "Sent a Deal Request for ${item.cropName}",
                                                "timestamp" to System.currentTimeMillis(),
                                                "unreadCountFarmer" to FieldValue.increment(1)
                                            ), SetOptions.merge()).await()

                                            withContext(Dispatchers.Main) {
                                                isSendingInquiry = false
                                                showNegotiationDialog = false
                                                navController.navigate("deals/${farmerEmail}")
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) { isSendingInquiry = false }
                                        }
                                    }
                                },
                                enabled = targetValue > 0 && !isSendingInquiry,
                                colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary, contentColor = Color.White, disabledContainerColor = Color.LightGray)
                            ) {
                                if (isSendingInquiry) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                else Text("Send Deal", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(containerColor = PeachBackground, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().background(PeachBackground).verticalScroll(rememberScrollState()).padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextDark) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        val name = farmerProfile?.name ?: farmerEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                        Text(text = name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.width(6.dp))
                        Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = "Verified Badge", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { toggleFavorite() }) { Icon(imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = "Favorite", tint = TerracottaPrimary, modifier = Modifier.size(28.dp)) }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search for crops...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Filled.Search, tint = TerracottaPrimary, contentDescription = "Search") }, shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedBorderColor = GoldenYellow, unfocusedBorderColor = Color.Transparent), singleLine = true
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().height(300.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painter = painterResource(id = R.drawable.ic_agri_loading), contentDescription = "Loading", tint = TerracottaPrimary, modifier = Modifier.size(56.dp).rotate(rotation))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Fetching farmer products...", color = TerracottaPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    Card(modifier = Modifier.fillMaxWidth().border(2.dp, GoldenYellow, RoundedCornerShape(20.dp)), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val profileUrl = farmerProfile?.profile_image_url
                                val fullUrl = if (!profileUrl.isNullOrEmpty()) { if (profileUrl.startsWith("http")) profileUrl else "$baseUrl/${profileUrl.removePrefix("/")}" } else null
                                if (fullUrl != null) { AsyncImage(model = fullUrl, contentDescription = "Profile", modifier = Modifier.size(64.dp).clip(CircleShape).border(2.dp, GoldenYellow, CircleShape), contentScale = ContentScale.Crop) }
                                else { Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(PeachBackground), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Person, contentDescription = "Profile", tint = TerracottaPrimary, modifier = Modifier.size(32.dp)) } }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    val farmAddr = farmerProfile?.farm_address ?: "Location not updated"
                                    val homeAddr = farmerProfile?.home_address ?: ""
                                    val isSame = farmerProfile?.is_same_address ?: true
                                    val farmLat = farmerProfile?.farmLat ?: 0.0
                                    val farmLon = farmerProfile?.farmLon ?: 0.0
                                    val homeLat = farmerProfile?.homeLat ?: 0.0
                                    val homeLon = farmerProfile?.homeLon ?: 0.0

                                    @Composable
                                    fun AddressRow(title: String, address: String, lat: Double, lon: Double) {
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF9EFE9)).clickable {
                                            if (lat != 0.0 && lon != 0.0) {
                                                val uri = Uri.parse("geo:${lat},${lon}?q=${lat},${lon}(${Uri.encode(address)})")
                                                try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (e: Exception) { Toast.makeText(context, "Maps app not found", Toast.LENGTH_SHORT).show() }
                                            } else { Toast.makeText(context, "Precise coordinates not available", Toast.LENGTH_SHORT).show() }
                                        }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.LocationOn, contentDescription = "Location", tint = TerracottaPrimary, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TerracottaPrimary)
                                                Text(text = address, fontSize = 12.sp, color = TextDark, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                    if (isSame || homeAddr.isEmpty()) { AddressRow("Registered Address", farmAddr, farmLat, farmLon) }
                                    else { AddressRow("Farm Address", farmAddr, farmLat, farmLon); AddressRow("Home Address", homeAddr, homeLat, homeLon) }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Star, contentDescription = "Rating", tint = GoldenYellow, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = if (reviewsList.isNotEmpty()) String.format(Locale.US, "%.1f (%d Reviews)", averageRating, reviewsList.size) else "0.0 (No Reviews)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = PeachBackground)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Soil Type", fontSize = 11.sp, color = TextMuted); Text(farmerProfile?.soil_type ?: "N/A", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TerracottaPrimary) }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Acreage", fontSize = 11.sp, color = TextMuted); Text(farmerProfile?.total_acreage ?: "N/A", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TerracottaPrimary) }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Member Since", fontSize = 11.sp, color = TextMuted); Text(farmerProfile?.member_since ?: "N/A", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TerracottaPrimary) }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(categories) { category ->
                            Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (selectedCategory == category) TerracottaPrimary else Color.White).border(1.dp, if (selectedCategory == category) Color.Transparent else TerracottaPrimary.copy(alpha = 0.3f), RoundedCornerShape(20.dp)).clickable { selectedCategory = category }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(text = category, color = if (selectedCategory == category) Color.White else TerracottaPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Fresh Harvest", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (filteredInventory.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { Text("No crops found.", color = Color.Gray, fontSize = 14.sp) }
                    } else {
                        LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(420.dp)) {
                            items(filteredInventory) { item ->
                                val (discountedPrice, discountPercent) = calculateDiscount(item.pricePerKg, item.expiryDate)
                                Card(
                                    modifier = Modifier.fillMaxWidth().height(260.dp).clickable { selectedItemForDeal = item; showNegotiationDialog = true; negotiationStep = 1 },
                                    shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, GoldenYellow.copy(alpha = 0.5f)), elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                        Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                                            val fullUrl = if (item.imageUrl.startsWith("http")) item.imageUrl else "${baseUrl}${item.imageUrl}"
                                            AsyncImage(model = fullUrl, contentDescription = item.cropName, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(PeachBackground), contentScale = ContentScale.Crop)
                                            if (discountPercent > 0) { Box(modifier = Modifier.padding(4.dp).background(Color(0xFFD32F2F), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("$discountPercent% OFF", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) } }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                                            Text(item.cropName, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Column {
                                                    if (discountPercent > 0) { Text("₹${String.format(Locale.US, "%.2f", item.pricePerKg)}", fontSize = 12.sp, color = Color.Gray, textDecoration = TextDecoration.LineThrough) }
                                                    Text("₹${String.format(Locale.US, "%.2f", discountedPrice)}/kg", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF2E7D32))
                                                }
                                                IconButton(onClick = { selectedItemForDeal = item; showNegotiationDialog = true; negotiationStep = 1 }, modifier = Modifier.size(32.dp).background(PeachBackground, CircleShape)) { Icon(Icons.Filled.ChatBubble, contentDescription = "Deal", tint = TerracottaPrimary, modifier = Modifier.size(16.dp)) }
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                if (item.availableKg <= 10.0) {
                                                    Text("Only ${item.availableKg} kg left!", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                                                } else {
                                                    Text("Stock: ${item.availableKg} kg", fontSize = 11.sp, color = Color.Gray)
                                                }
                                                Text("MOQ: ${item.moq} kg", fontSize = 12.sp, color = TextMuted)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Farmer Reviews", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
                        IconButton(onClick = { showReviewDialog = true }, modifier = Modifier.background(PeachBackground, CircleShape).size(32.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Review", tint = TerracottaPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (isReviewsLoading) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(painter = painterResource(id = R.drawable.ic_agri_loading), contentDescription = "Loading", tint = TerracottaPrimary, modifier = Modifier.size(36.dp).rotate(rotation))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Syncing latest reviews...", color = TerracottaPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    } else if (reviewsList.isEmpty()) {
                        Text("No reviews yet. Be the first to review!", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 80.dp))
                    } else {
                        Column {
                            reviewsList.forEach { review ->
                                val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(review.timestamp))
                                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, PeachBackground)) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (review.userProfileImage.isNotEmpty()) {
                                                    AsyncImage(
                                                        model = "$baseUrl/${review.userProfileImage.removePrefix("/")}",
                                                        contentDescription = "User",
                                                        modifier = Modifier.size(36.dp).clip(CircleShape).border(1.dp, GoldenYellow, CircleShape),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(PeachBackground), contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Filled.Person, contentDescription = "User", tint = TerracottaPrimary, modifier = Modifier.size(20.dp))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(review.userName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                                                    Text(dateStr, fontSize = 10.sp, color = Color.Gray)
                                                }
                                            }
                                            if (review.userEmail == userEmail) {
                                                Row {
                                                    IconButton(onClick = { editingReviewId = review.id; reviewTextState = review.text; ratingState = review.rating.toInt(); showReviewDialog = true }, modifier = Modifier.size(24.dp)) {
                                                        Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = TerracottaPrimary, modifier = Modifier.size(16.dp))
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    IconButton(
                                                        onClick = {
                                                            coroutineScope.launch(Dispatchers.IO) {
                                                                try {
                                                                    RetrofitClient.apiService.deleteReview(com.simats.growise.data.model.ReviewIdRequest(reviewId = review.id))
                                                                    withContext(Dispatchers.Main) { fetchReviews() }
                                                                } catch(e: Exception){}
                                                            }
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(16.dp)) }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row { repeat(5) { star -> Icon(if (star < review.rating) Icons.Filled.Star else Icons.Outlined.StarBorder, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(16.dp)) } }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(review.text, fontSize = 13.sp, color = TextDark)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }
}