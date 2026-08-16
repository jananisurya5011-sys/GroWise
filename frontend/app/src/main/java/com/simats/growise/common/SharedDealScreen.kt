package com.simats.growise.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import android.os.Build
import android.widget.Toast
import java.net.URL
import java.net.HttpURLConnection
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONObject
import com.simats.growise.data.model.toSafeChatMessage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.asAndroidBitmap
import dev.shreyaspatil.capturable.Capturable
import dev.shreyaspatil.capturable.controller.rememberCaptureController
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.simats.growise.R
import com.simats.growise.data.model.ChatMessage
import com.simats.growise.data.model.ChatThread
import com.simats.growise.data.model.DealAnalysisRequest
import com.simats.growise.data.model.ProfileDetailsResponse
import com.simats.growise.data.network.RetrofitClient
import com.simats.growise.ui.theme.GoldenYellow
import com.simats.growise.ui.theme.PeachBackground
import com.simats.growise.ui.theme.TerracottaPrimary
import com.simats.growise.ui.theme.TextDark
import com.simats.growise.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

// Add safety helpers for Firebase/Gson Float/Double serialization
fun Double.safe(): Double = if (this.isNaN() || this.isInfinite()) 0.0 else this
fun Any?.safeAny(): Any {
    if (this is Double && (this.isNaN() || this.isInfinite())) return 0.0
    if (this is Float && (this.isNaN() || this.isInfinite())) return 0.0
    return this ?: 0.0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedDealScreen(currentUserEmail: String, targetEmail: String, role: String, onBackClick: () -> Unit, onChatClick: (String) -> Unit = {}, onOrderClick: (String) -> Unit = {}) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPref =
        remember { context.getSharedPreferences("GroWiseSession", Context.MODE_PRIVATE) }
    val db = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val baseUrl = remember { RetrofitClient.BASE_URL.removeSuffix("/") }
    val isFarmer = role == "farmer"

    val dealViewModel: com.simats.growise.ui.viewmodel.DealViewModel = viewModel()
    val transitionState by dealViewModel.transitionState.collectAsState()

    LaunchedEffect(transitionState) {
        when (transitionState) {
            is com.simats.growise.ui.viewmodel.DealTransitionState.Success -> {
                Toast.makeText(context, "Operation successful", Toast.LENGTH_SHORT).show()
                dealViewModel.resetState()
            }
            is com.simats.growise.ui.viewmodel.DealTransitionState.Error -> {
                Toast.makeText(context, (transitionState as com.simats.growise.ui.viewmodel.DealTransitionState.Error).message, Toast.LENGTH_SHORT).show()
                dealViewModel.resetState()
            }
            else -> {}
        }
    }
    val goldenBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFD700),
            Color(0xFFFDB931),
            Color(0xFFFFE066)
        )
    )

    if (targetEmail.isEmpty()) {
        val gson = com.google.gson.Gson()
        val cachedThreads =
            sharedPref.getString("CACHE_THREADS_${role.uppercase()}_$currentUserEmail", "[]")
        val cachedProfiles =
            sharedPref.getString("CACHE_PROFILES_${role.uppercase()}_$currentUserEmail", "{}")

        var chatList by remember {
            mutableStateOf<List<ChatThread>>(
                gson.fromJson(
                    cachedThreads,
                    object : com.google.gson.reflect.TypeToken<List<ChatThread>>() {}.type
                ) ?: emptyList()
            )
        }
        var selectedFilter by remember { mutableStateOf("All") }
        var profilePictures by remember {
            mutableStateOf<Map<String, String>>(
                gson.fromJson(
                    cachedProfiles,
                    object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
                ) ?: emptyMap()
            )
        }
        var isListLoading by remember { mutableStateOf(chatList.isEmpty()) }

        val infiniteTransition = rememberInfiniteTransition()
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "loading_rot"
        )

        LaunchedEffect(currentUserEmail) {
            val queryField = if (isFarmer) "farmerEmail" else "userEmail"
            db.collection("chats").whereEqualTo(queryField, currentUserEmail)
                .addSnapshotListener { snap, _ ->
                    if (snap != null) {
                        val threads = snap.documents.mapNotNull { doc ->
                            val otherEmail =
                                doc.getString(if (isFarmer) "userEmail" else "farmerEmail") ?: ""
                            val lm = doc.getString("lastMessage") ?: ""
                            val ts = doc.getLong("timestamp") ?: 0L
                            val unread =
                                doc.getLong(if (isFarmer) "unreadCountFarmer" else "unreadCountUser")
                                    ?.toInt() ?: 0

                            ChatThread(
                                id = doc.id,
                                farmerEmail = otherEmail,
                                lastMessage = lm,
                                unreadCountUser = if (!isFarmer) unread else 0,
                                unreadCountFarmer = if (isFarmer) unread else 0,
                                timestamp = ts,
                                type = "MARKETPLACE"
                            )
                        }.sortedByDescending { it.timestamp }
                        chatList = threads
                        sharedPref.edit().putString(
                            "CACHE_THREADS_${role.uppercase()}_$currentUserEmail",
                            gson.toJson(threads)
                        ).apply()
                        isListLoading = false

                        coroutineScope.launch(Dispatchers.IO) {
                            val map = mutableMapOf<String, String>()
                            for (t in threads) {
                                try {
                                    val res =
                                        RetrofitClient.apiService.retrieveProfileFields(t.farmerEmail)
                                    if (res.isSuccessful) res.body()?.profile_image_url?.let {
                                        map[t.farmerEmail] = it
                                    }
                                } catch (e: Exception) {
                                }
                            }
                            withContext(Dispatchers.Main) {
                                profilePictures = map
                                sharedPref.edit().putString(
                                    "CACHE_PROFILES_${role.uppercase()}_$currentUserEmail",
                                    gson.toJson(map)
                                ).apply()
                            }
                        }
                    }
                }
        }

        val filteredChats = when (selectedFilter) {
            "Unread" -> chatList.filter { (if (isFarmer) it.unreadCountFarmer else it.unreadCountUser) > 0 }
            "Accepted" -> chatList.filter {
                it.lastMessage.contains(
                    "Accepted",
                    ignoreCase = true
                ) || it.lastMessage.contains(
                    "Payment",
                    ignoreCase = true
                ) || it.lastMessage.contains(
                    "Invoice",
                    ignoreCase = true
                ) || it.lastMessage.contains("Locked", ignoreCase = true)
            }

            else -> chatList
        }

        Scaffold(
            containerColor = PeachBackground,
            topBar = {
                TopAppBar(title = {
                    Text(
                        if (isFarmer) "Active Deals" else "Deal Inbox",
                        fontWeight = FontWeight.ExtraBold,
                        color = TerracottaPrimary,
                        fontSize = 24.sp
                    )
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = PeachBackground))
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp)
            ) {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = { Text("Search...", color = Color.Gray) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = TerracottaPrimary
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = GoldenYellow,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = selectedFilter == "All",
                        onClick = { selectedFilter = "All" },
                        label = { Text("All", fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TerracottaPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == "Unread",
                        onClick = { selectedFilter = "Unread" },
                        label = { Text("Unread") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TerracottaPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == "Accepted",
                        onClick = { selectedFilter = "Accepted" },
                        label = { Text("Accepted") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TerracottaPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))

                if (isListLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_agri_loading),
                                contentDescription = "Loading",
                                tint = TerracottaPrimary,
                                modifier = Modifier.size(64.dp).rotate(rotation)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Syncing market deals...",
                                color = TerracottaPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                } else if (filteredChats.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(140.dp)
                                    .background(Color.White, CircleShape)
                                    .border(2.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.ChatBubbleOutline,
                                    contentDescription = "Empty",
                                    tint = TerracottaPrimary.copy(alpha = 0.4f),
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                "No Active Deals",
                                color = TerracottaPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Your negotiation history and live chats\nwill appear here.",
                                color = TextMuted,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(filteredChats) { thread ->
                            val dateStr = SimpleDateFormat(
                                "dd MMM, hh:mm a",
                                Locale.getDefault()
                            ).format(Date(thread.timestamp))
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    db.collection("chats").document(thread.id).update(
                                        if (isFarmer) "unreadCountFarmer" else "unreadCountUser",
                                        0
                                    )
                                    onChatClick(thread.farmerEmail)
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, PeachBackground)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val profileUrl = profilePictures[thread.farmerEmail]
                                    if (!profileUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = "$baseUrl/${profileUrl.removePrefix("/")}",
                                            contentDescription = null,
                                            modifier = Modifier.size(56.dp).clip(CircleShape).border(2.dp, GoldenYellow, CircleShape),
                                            contentScale = ContentScale.Crop,
                                            error = painterResource(id = R.drawable.app_logo),
                                            placeholder = painterResource(id = R.drawable.app_logo)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.size(50.dp)
                                                .background(PeachBackground, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.app_logo),
                                                contentDescription = "Profile Logo",
                                                modifier = Modifier.size(32.dp).clip(CircleShape)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    thread.farmerEmail.substringBefore("@")
                                                        .replaceFirstChar { it.uppercase() },
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextDark,
                                                    fontSize = 16.sp
                                                )
                                            }
                                            Text(dateStr, fontSize = 10.sp, color = Color.Gray)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                thread.lastMessage,
                                                fontSize = 13.sp,
                                                color = Color.Gray,
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f)
                                            )
                                            val unread =
                                                if (isFarmer) thread.unreadCountFarmer else thread.unreadCountUser
                                            if (unread > 0) {
                                                Box(
                                                    modifier = Modifier.size(22.dp)
                                                        .background(Color(0xFF4CAF50), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        unread.toString(),
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
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
        }
    } else {
        val gson = com.google.gson.Gson()
        val chatId =
            if (currentUserEmail < targetEmail) "${currentUserEmail}_$targetEmail" else "${targetEmail}_$currentUserEmail"
        val farmerE = if (isFarmer) currentUserEmail else targetEmail
        val userE = if (isFarmer) targetEmail else currentUserEmail

        val cachedMsgs = sharedPref.getString("CACHE_CHAT_$chatId", "[]")
        var chatMessages by remember {
            mutableStateOf<List<ChatMessage>>(
                gson.fromJson(
                    cachedMsgs,
                    object : com.google.gson.reflect.TypeToken<List<ChatMessage>>() {}.type
                ) ?: emptyList()
            )
        }
        var isChatLoading by remember { mutableStateOf(chatMessages.isEmpty()) }
        val listState = rememberLazyListState()

        LaunchedEffect(chatMessages.size) {
            if (chatMessages.isNotEmpty()) {
                listState.animateScrollToItem(chatMessages.size - 1)
            }
        }

        val infiniteTransition = rememberInfiniteTransition()
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "chat_rot"
        )

        var messageText by remember { mutableStateOf("") }
        var otherPhone by remember { mutableStateOf("") }
        var otherProfileUrl by remember { mutableStateOf("") }
        var myProfile by remember { mutableStateOf<ProfileDetailsResponse?>(null) }

        var currentLat by remember { mutableStateOf(0.0) }
        var currentLon by remember { mutableStateOf(0.0) }

        var showAIBottomSheet by remember { mutableStateOf(false) }
        var aiCropInput by remember { mutableStateOf("") }
        var aiLocationInput by remember { mutableStateOf("") }
        var aiMinPrice by remember { mutableStateOf<Double?>(null) }
        var aiMaxPrice by remember { mutableStateOf<Double?>(null) }
        var aiReason by remember { mutableStateOf<String?>(null) }
        var aiIsLoading by remember { mutableStateOf(false) }

        var showAddressDialog by remember { mutableStateOf(false) }
        var manualAddressInput by remember { mutableStateOf("") }
        var latInputStr by remember { mutableStateOf("") }
        var lonInputStr by remember { mutableStateOf("") }
        var pendingAddressMsg by remember { mutableStateOf<ChatMessage?>(null) }

        var showFullScreenPayment by remember { mutableStateOf(false) }
        var paymentStep by remember { mutableIntStateOf(0) }
        var pendingInvoiceItem by remember { mutableStateOf<ChatMessage?>(null) }

        var showCounterDialog by remember { mutableStateOf(false) }
        var counterPriceInput by remember { mutableStateOf("") }
        var counterReasonInput by remember { mutableStateOf("") }
        var targetMsgToCounter by remember { mutableStateOf<ChatMessage?>(null) }

        var isRecording by remember { mutableStateOf(false) }
        var isRecordingLocked by remember { mutableStateOf(false) }
        var recordTime by remember { mutableStateOf(0) }
        var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
        var audioFilePath by remember { mutableStateOf("") }

        val fusedLocationClient =
            remember { LocationServices.getFusedLocationProviderClient(context) }
        val locationLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
                if (perms.entries.all { it.value }) {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { loc ->
                            if (loc != null) {
                                currentLat = loc.latitude
                                currentLon = loc.longitude
                                try {
                                    val geocoder = Geocoder(context, Locale.getDefault())
                                    val addresses =
                                        geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                                    if (!addresses.isNullOrEmpty()) manualAddressInput =
                                        addresses[0].getAddressLine(0)
                                } catch (e: Exception) {
                                }
                            } else {
                                Toast.makeText(context, "GPS not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
            }

        val galleryLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) {
                    val msgData = hashMapOf(
                        "senderId" to currentUserEmail,
                        "receiverId" to targetEmail,
                        "type" to "TEXT",
                        "text" to "🖼️ [Image Attached]",
                        "timestamp" to System.currentTimeMillis()
                    )
                    db.collection("chats").document(chatId).collection("messages").add(msgData)
                    db.collection("chats").document(chatId).set(
                        hashMapOf(
                            "lastMessage" to "🖼️ Image Attachment",
                            "timestamp" to System.currentTimeMillis(),
                            (if (isFarmer) "unreadCountUser" else "unreadCountFarmer") to FieldValue.increment(
                                1
                            )
                        ), SetOptions.merge()
                    )
                }
            }

        LaunchedEffect(isRecording) {
            if (isRecording) {
                recordTime = 0
                while (isRecording) {
                    delay(1000); recordTime++
                }
            }
        }

        LaunchedEffect(targetEmail) {
            db.collection("chats").document(chatId)
                .update(if (isFarmer) "unreadCountFarmer" else "unreadCountUser", 0)
            try {
                val res = RetrofitClient.apiService.retrieveProfileFields(targetEmail)
                if (res.isSuccessful) {
                    otherPhone = res.body()?.phone ?: ""
                    otherProfileUrl = res.body()?.profile_image_url ?: ""
                }
                val myRes = RetrofitClient.apiService.retrieveProfileFields(currentUserEmail)
                if (myRes.isSuccessful) {
                    myProfile = myRes.body()
                    currentLat = myRes.body()?.farmLat ?: myRes.body()?.homeLat ?: 0.0
                    currentLon = myRes.body()?.farmLon ?: myRes.body()?.homeLon ?: 0.0
                }
            } catch (e: Exception) {
            }
            var currentMsgs = listOf<ChatMessage>()
            var currentOrders = listOf<ChatMessage>()
            
            fun mergeAndSet() {
                val realOrderIds = currentMsgs.filter { it.type in listOf("DONATION_ORDER_CARD", "INVOICE_CARD", "RECEIPT_CARD") }.map { it.orderId.takeIf { id -> id.isNotBlank() } ?: it.dealId }.toSet()
                val filteredOrders = currentOrders.filter { it.orderId !in realOrderIds }
                val combined = (currentMsgs + filteredOrders).sortedBy { it.timestamp }
                
                val dealTypes = listOf("deal", "DONATION_REQUEST", "DONATION_ORDER_CARD", "INVOICE_CARD", "RECEIPT_CARD", "COUNTER_CARD", "LOGISTICS_CHOICE", "DECLINED_CARD")
                val dealCards = combined.filter { it.type in dealTypes }
                val dealMap = dealCards.groupBy { it.orderId.takeIf { id -> !id.isNullOrEmpty() } ?: it.dealId.takeIf { id -> !id.isNullOrEmpty() } ?: it.id }.mapValues { entry ->
                    entry.value.maxByOrNull { it.timestamp }
                }
                val validDealMsgIds = dealMap.values.mapNotNull { it?.id }.toSet()

                val filteredMsgs = combined.filter {
                    if (it.type == "DONATION_EVENT") {
                        false
                    } else if (it.type in dealTypes) {
                        validDealMsgIds.contains(it.id)
                    } else {
                        true
                    }
                }
                
                val finalFilteredMsgs = filteredMsgs.map { msg ->
                    if (msg.type == "DONATION_ORDER_CARD" && msg.imageUrl.isEmpty()) {
                        val reqMsg = combined.find { it.type == "DONATION_REQUEST" && (it.dealId == msg.orderId || it.id == msg.orderId) }
                        if (reqMsg != null && reqMsg.imageUrl.isNotEmpty()) {
                            msg.copy(imageUrl = reqMsg.imageUrl)
                        } else msg
                    } else msg
                }
                chatMessages = finalFilteredMsgs
                sharedPref.edit().putString("CACHE_CHAT_$chatId", gson.toJson(filteredMsgs)).apply()
                isChatLoading = false
            }

            db.collection("chats").document(chatId).collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING).addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val msgs = snap.documents.mapNotNull {
                        it.toSafeChatMessage()
                    }
                    currentMsgs = msgs
                    mergeAndSet()
                }
            }

            db.collection("orders").whereEqualTo("chatId", chatId)
                .addSnapshotListener { snap, _ ->
                    if (snap != null) {
                        val orders = snap.documents.mapNotNull { doc ->
                            val map = doc.data ?: return@mapNotNull null
                            val status = map["status"] as? String ?: ""
                            
                            val itemId = map["itemId"] as? String ?: ""
                            
                            val possibleImage = map["cropImage"] as? String
                                ?: map["image"] as? String
                                ?: map["imageUrl"] as? String
                                ?: map["productImage"] as? String
                                ?: ""
                            
                            ChatMessage(
                                id = doc.id,
                                type = "DONATION_ORDER_CARD",
                                itemId = itemId,
                                orderId = map["orderId"] as? String ?: "",
                                dealId = map["orderId"] as? String ?: "",
                                senderId = map["userEmail"] as? String ?: "",
                                receiverId = map["farmerEmail"] as? String ?: "",
                                cropName = map["cropName"] as? String ?: "",
                                kg = (map["weightKg"] as? Number)?.toDouble() ?: 0.0,
                                rawTimestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                                status = status,
                                imageUrl = possibleImage,
                                pickupAddress = map["pickupAddress"] as? String ?: "",
                                dropAddress = map["dropAddress"] as? String ?: "",
                                rawPickupLat = map["pickupLat"] ?: 0.0,
                                rawPickupLon = map["pickupLon"] ?: 0.0,
                                rawDropLat = map["dropLat"] ?: 0.0,
                                rawDropLon = map["dropLon"] ?: 0.0,
                                vehicleType = map["vehicleType"] as? String ?: "",
                                pickupOtp = map["pickupOtp"] as? String ?: "",
                                dropOtp = map["dropOtp"] as? String ?: ""
                            )
                        }
                        currentOrders = orders
                        mergeAndSet()
                    }
                }
        }

        if (showFullScreenPayment && !isFarmer) {
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                val transition = rememberInfiniteTransition()
                val pulse by transition.animateFloat(
                    initialValue = 0.9f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        tween(600, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse
                    ),
                    label = "pulse"
                )
                val rot by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            1200,
                            easing = LinearEasing
                        ), repeatMode = RepeatMode.Restart
                    ),
                    label = "rot"
                )

                LaunchedEffect(Unit) {
                    delay(2000)
                    paymentStep = 1
                    delay(1500)

                    pendingInvoiceItem?.let { inv ->
                        val isDonation = inv.dealType == "DONATION" || inv.type == "DONATION_REQUEST"
                        
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                if (isDonation) {
                                    val req = com.simats.growise.data.model.PayDonationInvoiceRequest(
                                        orderId = inv.orderId.takeIf { it.isNotEmpty() } ?: inv.dealId.takeIf { !it.isNullOrEmpty() } ?: inv.id,
                                        ngoEmail = userE,
                                        transportFare = inv.transportCost
                                    )
                                    val jsonReq = com.google.gson.Gson().toJson(req)
                                    Log.d("ORDER_WORKFLOW", "Donation Payment Started - Request: $jsonReq")
                                    val res = RetrofitClient.apiService.payDonationInvoice(req)
                                    if (res.isSuccessful && res.body() != null) {
                                        Log.d("ORDER_WORKFLOW", "Donation Payment Successful")
                                    } else {
                                        val errBody = res.errorBody()?.string()
                                        Log.e("ORDER_WORKFLOW", "Donation Payment Failed: $errBody")
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Payment Failed: $errBody", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    val req = com.simats.growise.data.model.OrderRequest(
                                        orderId = "", // Backend will generate it
                                        farmerEmail = farmerE,
                                        userEmail = userE,
                                        cropName = inv.cropName,
                                        weightKg = inv.kg,
                                        cropValue = (inv.totalPrice - inv.transportCost),
                                        transportFare = inv.transportCost,
                                        totalPaid = inv.totalPrice,
                                        isDonation = false,
                                        pickupAddress = inv.pickupAddress,
                                        dropAddress = inv.dropAddress,
                                        pickupLat = inv.pickupLat,
                                        pickupLon = inv.pickupLon,
                                        dropLat = inv.dropLat,
                                        dropLon = inv.dropLon,
                                        distanceKm = inv.distanceKm ?: 0.0,
                                        vehicleType = inv.vehicleType ?: "Any",
                                        pickupOtp = "",
                                        dropOtp = "",
                                        dealId = inv.dealId.takeIf { !it.isNullOrEmpty() } ?: inv.id,
                                        itemId = inv.itemId ?: "",
                                        chatId = chatId
                                    )
                                    val jsonOrderReq = com.google.gson.Gson().toJson(req)
                                    Log.d("ORDER_WORKFLOW", "Payment Started - Request: $jsonOrderReq")
                                    val res = RetrofitClient.apiService.createOrder(req)
                                    if (res.isSuccessful && res.body() != null) {
                                        Log.d("ORDER_WORKFLOW", "Order Created Successfully: ${res.body()}")
                                        // Backend transaction handled everything (wallet deduction, inventory deduction, 
                                        // order creation, and chat document update to RECEIPT_CARD).
                                    } else {
                                        val errBody = res.errorBody()?.string()
                                        Log.e("ORDER_WORKFLOW", "Order Creation Failed: $errBody")
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Payment Failed: $errBody", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Payment Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }

                        pendingInvoiceItem = null
                    }
                    showFullScreenPayment = false
                    paymentStep = 0
                }

                val loadingQuotes = listOf(
                    "Securing your transaction...",
                    "Empowering local farmers...",
                    "Routing to optimal logistics...",
                    "Verifying escrow funds..."
                )
                var quoteIndex by remember { mutableIntStateOf(0) }
                LaunchedEffect(paymentStep) {
                    if (paymentStep == 0) {
                        while (true) {
                            delay(1200); quoteIndex = (quoteIndex + 1) % loadingQuotes.size
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(if (paymentStep == 0) Color.White else Color(0xFF2E7D32)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (paymentStep == 0) {
                            Box(
                                modifier = Modifier.size(100.dp)
                                    .background(PeachBackground, CircleShape)
                                    .border(2.dp, TerracottaPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_agri_loading),
                                    contentDescription = "Processing",
                                    tint = TerracottaPrimary,
                                    modifier = Modifier.size(60.dp).rotate(rot)
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                loadingQuotes[quoteIndex],
                                color = TerracottaPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "₹${
                                    String.format(
                                        Locale.US,
                                        "%.2f",
                                        pendingInvoiceItem?.totalPrice ?: 0.0
                                    )
                                }",
                                color = Color(0xFF2E7D32),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(100.dp).scale(pulse)
                                    .background(Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Done",
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(60.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                "Payment Successful!",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                "Routing to Logistics...",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showAddressDialog && pendingAddressMsg != null) {
            Dialog(onDismissRequest = { showAddressDialog = false }) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(2.dp, GoldenYellow)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            if (isFarmer) "Confirm Pickup Location" else "Confirm Drop Location",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = TerracottaPrimary
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        myProfile?.let { profile ->
                            val hasHome = !profile.home_address.isNullOrEmpty()
                            val hasFarm = !profile.farm_address.isNullOrEmpty()
                            val isSame = profile.is_same_address

                            if (hasHome) {
                                OutlinedButton(
                                    onClick = {
                                        manualAddressInput = profile.home_address!!
                                        currentLat = profile.homeLat ?: 0.0
                                        currentLon = profile.homeLon ?: 0.0
                                        latInputStr = currentLat.toString()
                                        lonInputStr = currentLon.toString()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TerracottaPrimary),
                                    border = BorderStroke(1.dp, GoldenYellow)
                                ) { Text(if (isSame) "Use Primary Address" else "Use Home Address") }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            if (hasFarm && !isSame) {
                                OutlinedButton(
                                    onClick = {
                                        manualAddressInput = profile.farm_address!!
                                        currentLat = profile.farmLat ?: 0.0
                                        currentLon = profile.farmLon ?: 0.0
                                        latInputStr = currentLat.toString()
                                        lonInputStr = currentLon.toString()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TerracottaPrimary),
                                    border = BorderStroke(1.dp, GoldenYellow)
                                ) { Text("Use Farm Address") }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        // THEMED HIGH VISIBILITY BUTTON
                        Button(
                            onClick = {
                                locationLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                                coroutineScope.launch {
                                    delay(2000); latInputStr = currentLat.toString(); lonInputStr =
                                    currentLon.toString()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.GpsFixed,
                                contentDescription = "GPS",
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Auto-Fetch Live Location",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = manualAddressInput,
                            onValueChange = { manualAddressInput = it },
                            label = { Text(if (isFarmer) "Pickup Address" else "Delivery Address") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = latInputStr,
                                onValueChange = {
                                    latInputStr =
                                        it.filter { char -> char.isDigit() || char == '.' || char == '-' }
                                },
                                label = { Text("Latitude") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = lonInputStr,
                                onValueChange = {
                                    lonInputStr =
                                        it.filter { char -> char.isDigit() || char == '.' || char == '-' }
                                },
                                label = { Text("Longitude") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { showAddressDialog = false }) {
                                Text(
                                    "Cancel",
                                    color = Color.Gray
                                )
                            }
                            Button(
                                onClick = {
                                    currentLat = latInputStr.toDoubleOrNull() ?: 0.0
                                    currentLon = lonInputStr.toDoubleOrNull() ?: 0.0

                                    if (currentLat != 0.0 && currentLon != 0.0) {
                                        val msg = pendingAddressMsg!!
                                        val latestMsg = chatMessages.find { it.id == msg.id } ?: msg
                                        val myName = myProfile?.name ?: currentUserEmail.substringBefore("@")
                                        val origId = latestMsg.dealId.takeIf { !it.isNullOrEmpty() } ?: latestMsg.id
                                        val isDonation = latestMsg.type == "DONATION_ORDER_CARD"

                                        if (isDonation) {
                                            if (isFarmer) {
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    try {
                                                        db.collection("orders").document(origId).update(
                                                            mapOf(
                                                                "pickupAddress" to manualAddressInput,
                                                                "pickupLat" to currentLat,
                                                                "pickupLon" to currentLon,
                                                                "status" to "WAITING_FOR_TRANSPORT_SELECTION"
                                                            )
                                                        )
                                                        db.collection("chats").document(chatId).collection("messages").document(origId).update(
                                                            mapOf(
                                                                "pickupAddress" to manualAddressInput,
                                                                "pickupLat" to currentLat,
                                                                "pickupLon" to currentLon,
                                                                "status" to "WAITING_FOR_TRANSPORT_SELECTION"
                                                            )
                                                        )
                                                        
                                                        val ts = System.currentTimeMillis()
                                                        val msgId = db.collection("chats").document(chatId).collection("messages").document().id
                                                        db.collection("chats").document(chatId).collection("messages").document(msgId).set(
                                                            mapOf(
                                                                "type" to "DONATION_EVENT",
                                                                "event" to "FARMER_SET_PICKUP",
                                                                "dealId" to origId,
                                                                "timestamp" to ts
                                                            )
                                                        )
                                                    } catch (e: Exception) { e.printStackTrace() }
                                                }
                                                showAddressDialog = false
                                            } else {
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    try {
                                                        val req = com.simats.growise.data.model.LogisticsFareRequest(
                                                            weight = latestMsg.kg,
                                                            pickupLat = latestMsg.pickupLat,
                                                            pickupLon = latestMsg.pickupLon,
                                                            dropLat = currentLat,
                                                            dropLon = currentLon
                                                        )
                                                        val res = RetrofitClient.apiService.calculateFare(req)
                                                        if (res.isSuccessful && res.body() != null) {
                                                            val data = res.body()!!
                                                            val reqLogistics = com.simats.growise.data.model.ConfirmDonationLogisticsRequest(
                                                                orderId = origId,
                                                                ngoEmail = currentUserEmail,
                                                                vehicleType = data.vehicleType,
                                                                transportFare = data.suggestedFare,
                                                                dropAddress = manualAddressInput,
                                                                dropLat = currentLat,
                                                                dropLon = currentLon,
                                                                distanceKm = data.distanceKm
                                                            )
                                                            RetrofitClient.apiService.confirmDonationLogistics(reqLogistics)
                                                        }
                                                    } catch (e: Exception) { e.printStackTrace() }
                                                }
                                                showAddressDialog = false
                                            }
                                        } else if (isFarmer) {
                                            if (latestMsg.dropLat != 0.0) {
                                                // Buyer already set drop location; Farmer sets pickup and calculates fare
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    try {
                                                        val req = com.simats.growise.data.model.LogisticsFareRequest(
                                                            weight = latestMsg.kg,
                                                            pickupLat = currentLat,
                                                            pickupLon = currentLon,
                                                            dropLat = latestMsg.dropLat,
                                                            dropLon = latestMsg.dropLon
                                                        )
                                                        val res = RetrofitClient.apiService.calculateFare(req)
                                                        if (res.isSuccessful && res.body() != null) {
                                                            val data = res.body()!!
                                                            try {
                                                                val finalTotal = latestMsg.totalPrice + data.suggestedFare
                                                                val payload = com.simats.growise.data.model.TransitionDealRequest(
                                                                    chatId = chatId,
                                                                    dealId = origId,
                                                                    type = "INVOICE_CARD",
                                                                    status = "PENDING",
                                                                    logisticsType = "GRO-WISE",
                                                                    distanceKm = data.distanceKm,
                                                                    vehicleType = data.vehicleType,
                                                                    transportCost = data.suggestedFare,
                                                                    totalPrice = finalTotal,
                                                                    dropLat = latestMsg.dropLat,
                                                                    dropLon = latestMsg.dropLon,
                                                                    dropAddress = (latestMsg.dropAddress.takeIf { !it.isNullOrBlank() } ?: "Delivery Address"),
                                                                    pickupLat = currentLat,
                                                                    pickupLon = currentLon,
                                                                    pickupAddress = manualAddressInput,
                                                                    farmerName = myName
                                                                )
                                                                dealViewModel.transitionDeal(payload)
                                                            } catch (e: Exception) {
                                                                e.printStackTrace()
                                                            }
                                                        } else {
                                                            val errorStr = res.errorBody()?.string() ?: ""
                                                            if (errorStr.contains("OVER_LIMIT", ignoreCase = true)) {
                                                                val payload = com.simats.growise.data.model.TransitionDealRequest(
                                                                    chatId = chatId,
                                                                    dealId = origId,
                                                                    type = "TEXT",
                                                                    senderId = "SYSTEM",
                                                                    receiverId = currentUserEmail,
                                                                    text = "Declined: Limits Exceeded. Please use Crop Pool.",
                                                                    timestamp = System.currentTimeMillis(),
                                                                    status = "DECLINED"
                                                                )
                                                                dealViewModel.transitionDeal(payload)
                                                            } else {
                                                                val errMsg = if(errorStr.contains("Exceeds maximum limit", ignoreCase = true)) "Transport unavailable: Exceeds maximum limit of 40 km and 500 kg." else "Logistics calculation failed."
                                                                val payload = com.simats.growise.data.model.TransitionDealRequest(
                                                                    chatId = chatId,
                                                                    dealId = origId,
                                                                    type = "TEXT",
                                                                    senderId = "SYSTEM",
                                                                    receiverId = currentUserEmail,
                                                                    text = "⚠️ $errMsg",
                                                                    timestamp = System.currentTimeMillis()
                                                                )
                                                                dealViewModel.transitionDeal(payload)
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                            } else {
                                                // Farmer sets pickup first, waiting for buyer
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    try {
                                                        val payload = com.simats.growise.data.model.TransitionDealRequest(
                                                            chatId = chatId,
                                                            dealId = origId,
                                                            status = "LOCATION_UPDATED",
                                                            pickupAddress = manualAddressInput,
                                                            pickupLat = currentLat,
                                                            pickupLon = currentLon,
                                                            farmerName = myName
                                                        )
                                                        val jsonRequest = com.google.gson.Gson().toJson(payload)
                                                        Log.d("ORDER_WORKFLOW", "Farmer Pickup Saved - Request: $jsonRequest")
                                                        val res = dealViewModel.transitionDeal(payload)
                                                        Log.d("ORDER_WORKFLOW", "Farmer Pickup Response: Status: ${res.code()}, Body: ${res.body()}, Error: ${res.errorBody()?.string()}")
                                                    } catch (e: Exception) {
                                                        Log.e("TRANSITION_ERROR", "Failed", e)
                                                        e.printStackTrace()
                                                    }
                                                }
                                            }
                                        } else {
                                            if (latestMsg.pickupLat != 0.0) {
                                                // Farmer already set pickup location; Buyer sets drop and calculates fare
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    try {
                                                        val req = com.simats.growise.data.model.LogisticsFareRequest(
                                                            weight = latestMsg.kg,
                                                            pickupLat = latestMsg.pickupLat,
                                                            pickupLon = latestMsg.pickupLon,
                                                            dropLat = currentLat,
                                                            dropLon = currentLon
                                                        )
                                                        val res = RetrofitClient.apiService.calculateFare(req)
                                                        if (res.isSuccessful && res.body() != null) {
                                                            val data = res.body()!!
                                                            val finalTotal = latestMsg.totalPrice + data.suggestedFare
                                                            val payload = com.simats.growise.data.model.TransitionDealRequest(
                                                                chatId = chatId,
                                                                dealId = origId,
                                                                type = "INVOICE_CARD",
                                                                status = "PENDING",
                                                                logisticsType = "GRO-WISE",
                                                                distanceKm = data.distanceKm,
                                                                vehicleType = data.vehicleType,
                                                                transportCost = data.suggestedFare,
                                                                totalPrice = finalTotal,
                                                                dropLat = currentLat,
                                                                dropLon = currentLon,
                                                                dropAddress = manualAddressInput,
                                                                pickupLat = latestMsg.pickupLat,
                                                                pickupLon = latestMsg.pickupLon,
                                                                pickupAddress = (latestMsg.pickupAddress.takeIf { !it.isNullOrBlank() } ?: "Pickup Address"),
                                                                userName = myName,
                                                                timestamp = System.currentTimeMillis()
                                                            )
                                                            val jsonRequest = com.google.gson.Gson().toJson(payload)
                                                            Log.d("ORDER_WORKFLOW", "Invoice Generated - Request: $jsonRequest")
                                                            val resInvoice = dealViewModel.transitionDeal(payload)
                                                            Log.d("ORDER_WORKFLOW", "Invoice Response: Status: ${resInvoice.code()}, Body: ${resInvoice.body()}, Error: ${resInvoice.errorBody()?.string()}")
                                                        } else {
                                                            val errorStr = res.errorBody()?.string() ?: ""
                                                            if (errorStr.contains("OVER_LIMIT", ignoreCase = true)) {
                                                                val payload = com.simats.growise.data.model.TransitionDealRequest(
                                                                    chatId = chatId,
                                                                    dealId = origId,
                                                                    type = "TEXT",
                                                                    senderId = "SYSTEM",
                                                                    receiverId = currentUserEmail,
                                                                    text = "Declined: Limits Exceeded. Please use Crop Pool.",
                                                                    timestamp = System.currentTimeMillis(),
                                                                    status = "DECLINED"
                                                                )
                                                                dealViewModel.transitionDeal(payload)
                                                            } else {
                                                                val errMsg = if(errorStr.contains("Exceeds maximum limit", ignoreCase = true)) "Transport unavailable: Exceeds maximum limit of 40 km and 500 kg." else "Logistics calculation failed."
                                                                val payload = com.simats.growise.data.model.TransitionDealRequest(
                                                                    chatId = chatId,
                                                                    dealId = origId,
                                                                    type = "TEXT",
                                                                    senderId = "SYSTEM",
                                                                    receiverId = currentUserEmail,
                                                                    text = "⚠️ $errMsg",
                                                                    timestamp = System.currentTimeMillis()
                                                                )
                                                                dealViewModel.transitionDeal(payload)
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                            } else {
                                                // Buyer sets drop location first, waiting for farmer
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    try {
                                                        val payload = com.simats.growise.data.model.TransitionDealRequest(
                                                            chatId = chatId,
                                                            dealId = origId,
                                                            status = "LOCATION_UPDATED",
                                                            dropAddress = manualAddressInput,
                                                            dropLat = currentLat,
                                                            dropLon = currentLon,
                                                            userName = myName
                                                        )
                                                        val jsonRequest = com.google.gson.Gson().toJson(payload)
                                                        Log.d("ADDRESS_FLOW", "Buyer selected: $manualAddressInput")
                                                        Log.d("ADDRESS_FLOW", "Transition payload: $jsonRequest")
                                                        Log.d("ORDER_WORKFLOW", "Buyer Address Saved - Request: $jsonRequest")
                                                        val res = dealViewModel.transitionDeal(payload)
                                                        Log.d("ORDER_WORKFLOW", "Buyer Address Response: Status: ${res.code()}, Body: ${res.body()}, Error: ${res.errorBody()?.string()}")
                                                    } catch (e: Exception) {
                                                        Log.e("TRANSITION_ERROR", "Failed", e)
                                                        e.printStackTrace()
                                                    }
                                                }
                                            }
                                        }
                                        showAddressDialog = false
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Invalid coordinates",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                enabled = manualAddressInput.isNotBlank() && latInputStr.isNotBlank() && lonInputStr.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TerracottaPrimary,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    "Confirm Address",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showCounterDialog && targetMsgToCounter != null) {
            val msg = targetMsgToCounter!!
            Dialog(onDismissRequest = {
                showCounterDialog = false; counterPriceInput = ""; counterReasonInput = ""
            }) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                        Text(
                            "Send Counter-Offer",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = TerracottaPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .background(Color(0xFFF9EFE9), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Market Rate",
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    ); Text(
                                    "₹${String.format(Locale.US, "%.2f", msg.basePrice)}/kg",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Other Offer",
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    ); Text(
                                    "₹${String.format(Locale.US, "%.2f", msg.targetPrice)}/kg",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Red
                                )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = counterPriceInput,
                            onValueChange = { counterPriceInput = it },
                            label = { Text("Your Counter Price (₹/kg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = counterReasonInput,
                            onValueChange = { counterReasonInput = it },
                            label = { Text("Reason") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )

                        val newPrice = counterPriceInput.toDoubleOrNull() ?: 0.0
                        if (newPrice > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .background(Color(0xFFE8F5E9), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFF2E7D32), RoundedCornerShape(12.dp))
                                    .padding(12.dp), contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "New Total Value: ₹${
                                        String.format(
                                            Locale.US,
                                            "%.2f",
                                            msg.kg * newPrice
                                        )
                                    }",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF2E7D32),
                                    fontSize = 16.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { showCounterDialog = false }) {
                                Text(
                                    "Cancel",
                                    color = Color.Gray
                                )
                            }
                            Button(
                                onClick = {
                                    if (newPrice > 0) {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val origId = msg.dealId.takeIf { !it.isNullOrEmpty() } ?: msg.id
                                                val payload = com.simats.growise.data.model.TransitionDealRequest(
                                                    chatId = chatId,
                                                    dealId = origId,
                                                    type = "COUNTER_CARD",
                                                    targetPrice = newPrice,
                                                    totalPrice = (msg.kg * newPrice),
                                                    reason = counterReasonInput,
                                                    status = "PENDING",
                                                    senderId = currentUserEmail,
                                                    receiverId = if (msg.senderId == currentUserEmail) msg.receiverId else msg.senderId,
                                                    timestamp = System.currentTimeMillis()
                                                )
                                                val jsonReq = com.google.gson.Gson().toJson(payload)
                                                Log.d("ORDER_WORKFLOW", "Counter Clicked - Request: $jsonReq")
                                                val res = dealViewModel.transitionDeal(payload)
                                                Log.d("ORDER_WORKFLOW", "Counter Response: ${res.code()} - ${res.body()}")
                                            } catch (e: Exception) { e.printStackTrace() }
                                        }
                                        showCounterDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)
                            ) {
                                Text(
                                    "Send Counter",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Scaffold(
            containerColor = PeachBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (otherProfileUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = "$baseUrl/${otherProfileUrl.removePrefix("/")}",
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(CircleShape)
                                        .border(1.dp, GoldenYellow, CircleShape),
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(id = R.drawable.app_logo),
                                    placeholder = painterResource(id = R.drawable.app_logo)
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape)
                                        .background(Color.White)
                                        .border(1.dp, GoldenYellow, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.app_logo),
                                        contentDescription = "Profile Logo",
                                        modifier = Modifier.size(24.dp).clip(CircleShape)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                targetEmail.substringBefore("@")
                                    .replaceFirstChar { it.uppercase() },
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = TerracottaPrimary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TerracottaPrimary
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (otherPhone.isNotEmpty()) {
                                try {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_DIAL,
                                            Uri.parse("tel:$otherPhone")
                                        )
                                    )
                                } catch (e: Exception) {
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "Phone number not available",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }) {
                            Icon(
                                Icons.Filled.Call,
                                contentDescription = "Call",
                                tint = TerracottaPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PeachBackground)
                )
            }
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {

                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        .clickable { showAIBottomSheet = true },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, GoldenYellow),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.SmartToy,
                            contentDescription = "AI",
                            tint = Color(0xFF1565C0),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tap for Gemini AI Market Insight",
                            fontSize = 14.sp,
                            color = Color(0xFF1565C0),
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                if (showAIBottomSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showAIBottomSheet = false },
                        containerColor = Color.White,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.SmartToy,
                                    contentDescription = "AI Insight",
                                    tint = Color(0xFF1565C0),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Gemini Market Insight",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    color = TerracottaPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isFarmer) "Get AI advice on min & max rates to quote buyers based on product & location."
                                else "Get AI advice on min & max rates to offer farmers based on product & location.",
                                fontSize = 12.sp, color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = aiCropInput,
                                onValueChange = { aiCropInput = it },
                                label = { Text("Product Name (e.g. Tomato)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = aiLocationInput,
                                onValueChange = { aiLocationInput = it },
                                label = { Text("Market Location (e.g. Chennai)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (aiCropInput.isNotBlank() && aiLocationInput.isNotBlank()) {
                                        aiIsLoading = true
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val req = DealAnalysisRequest(
                                                    cropName = aiCropInput,
                                                    location = aiLocationInput,
                                                    role = role
                                                )
                                                val res =
                                                    RetrofitClient.apiService.analyzeMarketDeal(req)
                                                withContext(Dispatchers.Main) {
                                                    aiIsLoading = false
                                                    aiMinPrice = res.minPrice
                                                    aiMaxPrice = res.maxPrice
                                                    aiReason = res.reason
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    aiIsLoading = false
                                                    Toast.makeText(
                                                        context,
                                                        "Network error fetching insights",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Please enter product and location",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(
                                        0xFF1565C0
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !aiIsLoading
                            ) {
                                if (aiIsLoading) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Get AI Price Suggestion",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (aiMinPrice != null && aiMaxPrice != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFE3F2FD)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFF1565C0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = if (isFarmer) "Recommended Quote Range (Farmer)" else "Recommended Offer Range (Buyer)",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF1565C0)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(
                                                    "Min Price",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                                Text(
                                                    "₹${
                                                        String.format(
                                                            Locale.US,
                                                            "%.2f",
                                                            aiMinPrice
                                                        )
                                                    } / kg",
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 18.sp,
                                                    color = Color(0xFF2E7D32)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    "Max Price",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                                Text(
                                                    "₹${
                                                        String.format(
                                                            Locale.US,
                                                            "%.2f",
                                                            aiMaxPrice
                                                        )
                                                    } / kg",
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 18.sp,
                                                    color = TerracottaPrimary
                                                )
                                            }
                                        }
                                        if (!aiReason.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Divider(color = Color(0xFF90CAF9))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Reason:\n$aiReason",
                                                fontSize = 12.sp,
                                                color = Color.DarkGray,
                                                lineHeight = 18.sp
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(chatMessages) { msg ->
                            var isAccepting by remember { mutableStateOf(false) }
                            val isMe = msg.senderId == currentUserEmail
                            val timeStr = SimpleDateFormat(
                                "hh:mm a",
                                Locale.getDefault()
                            ).format(Date(msg.timestamp))
                            val isExpired =
                                msg.type == "INQUIRY_CARD" && TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - msg.timestamp) > 24

                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                when (msg.type) {
                                    "INQUIRY_CARD", "COUNTER_CARD" -> {
                                        val isCounter = msg.type == "COUNTER_CARD"
                                        Card(
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isMe && isCounter) Color(
                                                    0xFFFFF3E0
                                                ) else Color.White
                                            ),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isCounter) Color(0xFFEF6C00) else GoldenYellow
                                            ),
                                            modifier = Modifier.fillMaxWidth(0.85f)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    val isDonation = msg.dealType == "DONATION" || msg.type == "DONATION_REQUEST"
                                                    val headerText = if (isDonation) {
                                                        if (isMe) "Sent Donation Request" else "Received Donation Request"
                                                    } else if (isCounter) {
                                                        if (isMe) "Sent Counter-Offer" else "Received Counter-Offer"
                                                    } else {
                                                        if (isMe) "Sent Deal Request" else "Received Deal Request"
                                                    }
                                                    
                                                    Text(
                                                        headerText,
                                                        fontSize = 11.sp,
                                                        color = if (isCounter) Color(0xFFEF6C00) else TerracottaPrimary,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row {
                                                    AsyncImage(
                                                        model = "$baseUrl/${msg.imageUrl.removePrefix("/")}",
                                                        contentDescription = null,
                                                        modifier = Modifier.size(40.dp).clip(CircleShape).border(1.dp, GoldenYellow, CircleShape),
                                                        contentScale = ContentScale.Crop,
                                                        error = painterResource(id = R.drawable.app_logo),
                                                        placeholder = painterResource(id = R.drawable.app_logo)
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column {
                                                        val isDonation = msg.dealType == "DONATION" || msg.type == "DONATION_REQUEST"
                                                        Text(
                                                            msg.cropName,
                                                            fontWeight = FontWeight.Bold,
                                                            color = TextDark
                                                        )
                                                        Text(
                                                            "Required: ${msg.kg} kg",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = TextDark
                                                        )
                                                        if (!isDonation) {
                                                            Text(
                                                                "Market Rate: ₹${
                                                                    String.format(
                                                                        Locale.US,
                                                                        "%.2f",
                                                                        msg.basePrice
                                                                    )
                                                                }/kg",
                                                                fontSize = 11.sp,
                                                                color = TextDark
                                                            )
                                                            Text(
                                                                if (isCounter) "Counter Price: ₹${
                                                                    String.format(
                                                                        Locale.US,
                                                                        "%.2f",
                                                                        msg.targetPrice
                                                                    )
                                                                } / kg" else "Offer: ₹${
                                                                    String.format(
                                                                        Locale.US,
                                                                        "%.2f",
                                                                        msg.targetPrice
                                                                    )
                                                                }/kg",
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isCounter) Color(0xFFEF6C00) else TerracottaPrimary,
                                                                fontSize = 11.sp
                                                            )
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(
                                                                "Final Value: ₹${
                                                                    String.format(
                                                                        Locale.US,
                                                                        "%.2f",
                                                                        msg.totalPrice
                                                                    )
                                                                }",
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = Color(0xFF2E7D32),
                                                                fontSize = 13.sp
                                                            )
                                                        }
                                                    }
                                                }

                                                if (msg.status == "PENDING") {
                                                    if ((!isMe && isCounter) || (!isMe && !isCounter)) {
                                                        Spacer(modifier = Modifier.height(12.dp))
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(
                                                                    2.dp
                                                                )
                                                            ) {
                                                                TextButton(
                                                                    onClick = {
                                                                        coroutineScope.launch(Dispatchers.IO) {
                                                                            try {
                                                                                val payload = com.simats.growise.data.model.TransitionDealRequest(
                                                                                    chatId = chatId,
                                                                                    dealId = msg.id,
                                                                                    status = "DECLINED"
                                                                                )
                                                                                val jsonReq = com.google.gson.Gson().toJson(payload)
                                                                                Log.d("ORDER_WORKFLOW", "Decline Clicked - Request: $jsonReq")
                                                                                val res = dealViewModel.transitionDeal(payload)
                                                                                Log.d("ORDER_WORKFLOW", "Decline Response: ${res.code()} - ${res.body()}")
                                                                            } catch (e: Exception) { e.printStackTrace() }
                                                                        }
                                                                    },
                                                                    contentPadding = PaddingValues(
                                                                        horizontal = 4.dp
                                                                    )
                                                                ) {
                                                                    Text(
                                                                        "Decline",
                                                                        color = Color.Red,
                                                                        fontSize = 11.sp
                                                                    )
                                                                }
                                                                TextButton(
                                                                    onClick = {
                                                                        targetMsgToCounter =
                                                                            msg; counterPriceInput =
                                                                        msg.targetPrice.toString(); showCounterDialog =
                                                                        true
                                                                    },
                                                                    contentPadding = PaddingValues(
                                                                        horizontal = 4.dp
                                                                    )
                                                                ) {
                                                                    Text(
                                                                        "Edit Offer",
                                                                        color = Color(0xFF1976D2),
                                                                        fontSize = 11.sp
                                                                    )
                                                                }
                                                            }
                                                            Button(
                                                                onClick = {
                                                                    Log.d("ACCEPT_FLOW", "Accept clicked for deal: ${msg.id}")
                                                                    isAccepting = true
                                                                    coroutineScope.launch(Dispatchers.IO) {
                                                                        try {
                                                                            val payload = com.simats.growise.data.model.TransitionDealRequest(
                                                                                chatId = chatId,
                                                                                dealId = msg.id,
                                                                                status = "ACCEPTED",
                                                                                timestamp = System.currentTimeMillis()
                                                                            )
                                                                            val jsonReq = com.google.gson.Gson().toJson(payload)
                                                                            Log.d("ORDER_WORKFLOW", "Accept Clicked - Request: $jsonReq")
                                                                            val res = dealViewModel.transitionDeal(payload)
                                                                            withContext(Dispatchers.Main) {
                                                                                isAccepting = false
                                                                                if (res.isSuccessful) {
                                                                                    Log.d("ORDER_WORKFLOW", "Accept API Success: ${res.body()}")
                                                                                    Toast.makeText(context, "Deal Accepted!", Toast.LENGTH_SHORT).show()
                                                                                } else {
                                                                                    val errBody = res.errorBody()?.string() ?: "Unknown API error"
                                                                                    Log.e("ORDER_WORKFLOW", "Accept API Failed: $errBody")
                                                                                    Toast.makeText(context, "Failed to accept: $errBody", Toast.LENGTH_LONG).show()
                                                                                }
                                                                            }
                                                                        } catch (e: Exception) {
                                                                            Log.e("ACCEPT_FLOW", "Accept failed exceptionally", e)
                                                                            withContext(Dispatchers.Main) {
                                                                                isAccepting = false
                                                                                Toast.makeText(context, "Network Error: ${e.message}", Toast.LENGTH_LONG).show()
                                                                            }
                                                                        }
                                                                    }
                                                                },
                                                                enabled = !isAccepting,
                                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                                                contentPadding = PaddingValues(horizontal = 8.dp)
                                                            ) {
                                                                if (isAccepting) {
                                                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                                } else {
                                                                    Text("Accept", color = Color.White, fontSize = 11.sp)
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        Spacer(modifier = Modifier.height(12.dp))
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.Start
                                                        ) {
                                                            TextButton(
                                                                onClick = {
                                                                    coroutineScope.launch(Dispatchers.IO) {
                                                                        try {
                                                                            val payload = com.simats.growise.data.model.TransitionDealRequest(
                                                                                chatId = chatId,
                                                                                dealId = msg.id,
                                                                                status = "WITHDRAWN"
                                                                            )
                                                                            dealViewModel.transitionDeal(payload)
                                                                        } catch (e: Exception) { e.printStackTrace() }
                                                                    }
                                                                },
                                                                contentPadding = PaddingValues(
                                                                    horizontal = 4.dp
                                                                )
                                                            ) {
                                                                Text(
                                                                    "Withdraw",
                                                                    color = Color.Red,
                                                                    fontSize = 11.sp
                                                                )
                                                            }
                                                        }
                                                    }
                                                } else if (msg.status == "ACCEPTED" || msg.status == "LOCATION_UPDATED") {
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    if (isFarmer) {
                                                        if (msg.pickupLat == 0.0) {
                                                            Button(
                                                                onClick = {
                                                                    pendingAddressMsg =
                                                                        msg; showAddressDialog =
                                                                    true
                                                                },
                                                                modifier = Modifier.fillMaxWidth()
                                                                    .height(36.dp),
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = TerracottaPrimary
                                                                )
                                                            ) {
                                                                Text(
                                                                    "Set Pickup Location",
                                                                    fontSize = 11.sp,
                                                                    color = Color.White
                                                                )
                                                            }
                                                        } else {
                                                            val isDonation = msg.dealType == "DONATION" || msg.type == "DONATION_REQUEST"
                                                            if (isDonation) {
                                                                Text("Status: Pickup Address Submitted", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                                            } else {
                                                                Text(
                                                                    "Waiting for Buyer to set Drop Location...",
                                                                    fontSize = 10.sp,
                                                                    color = Color.Gray
                                                                )
                                                            }
                                                        }
                                                    } else {
                                                        val isDonation = msg.dealType == "DONATION" || msg.type == "DONATION_REQUEST"
                                                        if (msg.dropLat == 0.0 && !isDonation) {
                                                            Button(
                                                                onClick = {
                                                                    pendingAddressMsg =
                                                                        msg; showAddressDialog =
                                                                    true
                                                                },
                                                                modifier = Modifier.fillMaxWidth()
                                                                    .height(36.dp),
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = TerracottaPrimary
                                                                )
                                                            ) {
                                                                Text(
                                                                    "Set Drop Location",
                                                                    fontSize = 11.sp,
                                                                    color = Color.White
                                                                )
                                                            }
                                                        } else {
                                                            if (isDonation) {
                                                                Text("Status: Pickup Address Received", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                                            } else {
                                                                Text(
                                                                    "Waiting for Farmer to set Pickup Location...",
                                                                    fontSize = 10.sp,
                                                                    color = Color.Gray
                                                                )
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    val isDonation = msg.dealType == "DONATION" || msg.type == "DONATION_REQUEST"
                                                    var statusText = msg.status
                                                    if (isDonation) {
                                                        if (msg.status == "PENDING") {
                                                            statusText = "Pending"
                                                        } else if (msg.status == "SELECTED") {
                                                            val mode = if (msg.vehicleType.isNullOrBlank()) "Self Service" else msg.vehicleType
                                                            val isSelf = mode == "Self Pickup" || mode == "Self Service"
                                                            statusText = if (isMe) {
                                                                if (isSelf) "Self Service Selected" else "Delivery Partner Selected"
                                                            } else {
                                                                if (isSelf) "Mode Selected (Self Service)" else "Delivery Partner Selected"
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        "Status: $statusText",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (msg.status == "PAID" || msg.status == "INVOICED" || msg.status == "SELECTED") Color(
                                                            0xFF2E7D32
                                                        ) else Color.Gray
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    "INVOICE_CARD" -> {
                                        Card(
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = BorderStroke(2.dp, goldenBrush),
                                            modifier = Modifier.fillMaxWidth(0.85f)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    val isDonation = msg.dealType == "DONATION" || msg.type == "DONATION_REQUEST"
                                                    Text(
                                                        if (isDonation) "DONATION INVOICE" else "SMART INVOICE",
                                                        fontSize = 14.sp,
                                                        color = TerracottaPrimary,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        letterSpacing = 1.sp
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().background(
                                                        Color(0xFFFFF3E0),
                                                        RoundedCornerShape(8.dp)
                                                    ).padding(12.dp)
                                                ) {
                                                    Column {
                                                        Text(
                                                            "From: ${msg.farmerName}",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = TerracottaPrimary
                                                        )
                                                        Text(
                                                            msg.pickupAddress.takeIf { it.isNotBlank() } ?: "Pickup location unavailable",
                                                            fontSize = 11.sp,
                                                            color = Color.DarkGray
                                                        )
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Log.d("ADDRESS_FLOW", "Firestore dropAddress: ${msg.dropAddress}")
                                                        Log.d("ADDRESS_FLOW", "Invoice displaying: ${msg.dropAddress.takeIf { it.isNotBlank() } ?: "Drop location unavailable"}")
                                                        Text(
                                                            "To: ${msg.userName}",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF1976D2)
                                                        )
                                                        Text(
                                                            msg.dropAddress.takeIf { it.isNotBlank() } ?: "Drop location unavailable",
                                                            fontSize = 11.sp,
                                                            color = Color.DarkGray
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        "Crop Value",
                                                        fontSize = 12.sp
                                                    )
                                                    val isDonation = msg.dealType == "DONATION" || msg.type == "DONATION_REQUEST"
                                                    Text(
                                                        if (isDonation) "₹0.00 (Donated)" else "₹${
                                                            String.format(
                                                                Locale.US,
                                                                "%.2f",
                                                                msg.totalPrice - msg.transportCost
                                                            )
                                                        }", fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        "Transport (${msg.vehicleType})",
                                                        fontSize = 12.sp
                                                    )
                                                    val isDonation = msg.dealType == "DONATION" || msg.type == "DONATION_REQUEST"
                                                    Text(
                                                        if (isDonation && msg.transportCost == 0.0) "₹0.00 (Self Pickup)" else "+ ₹${
                                                            String.format(
                                                                Locale.US,
                                                                "%.2f",
                                                                msg.transportCost
                                                            )
                                                        }", fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Divider(modifier = Modifier.padding(vertical = 4.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        "Total",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    val isDonation = msg.dealType == "DONATION" || msg.type == "DONATION_REQUEST"
                                                    Text(
                                                        if (isDonation && msg.transportCost == 0.0) "₹0.00" else "₹${
                                                            String.format(
                                                                Locale.US,
                                                                "%.2f",
                                                                msg.totalPrice
                                                            )
                                                        }",
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = TerracottaPrimary
                                                    )
                                                }

                                                val isDonation = msg.dealType == "DONATION" || msg.type == "DONATION_REQUEST"
                                                if (msg.status == "PENDING" && !isFarmer && !(isDonation && msg.transportCost == 0.0)) {
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    var currentWalletBalance by remember {
                                                        mutableDoubleStateOf(
                                                            0.0
                                                        )
                                                    }
                                                    LaunchedEffect(currentUserEmail) {
                                                        db.collection(
                                                            "wallets"
                                                        ).document(currentUserEmail)
                                                            .addSnapshotListener { snap, _ ->
                                                                currentWalletBalance =
                                                                    snap?.getDouble("balance")
                                                                        ?: 0.0
                                                            }
                                                    }
                                                    Button(
                                                        onClick = {
                                                            if (currentWalletBalance >= msg.totalPrice) {
                                                                pendingInvoiceItem = msg
                                                                showFullScreenPayment = true
                                                            } else {
                                                                Toast.makeText(
                                                                    context,
                                                                    "Insufficient wallet balance. Please add money to your wallet.",
                                                                    Toast.LENGTH_LONG
                                                                ).show()
                                                            }
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = Color(0xFF2E7D32)
                                                        ),
                                                        shape = RoundedCornerShape(12.dp)
                                                    ) {
                                                        Text(
                                                            "Pay via GroWise Secure",
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                } else {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    val isDonation = msg.dealType == "DONATION" || msg.type == "DONATION_REQUEST"
                                                    Text(
                                                        if (isDonation && msg.transportCost == 0.0) "Status: PAID (Free)" else "Status: ${msg.status}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (msg.status == "PAID" || (isDonation && msg.transportCost == 0.0)) Color(
                                                            0xFF2E7D32
                                                        ) else Color.Gray
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    "RECEIPT_CARD" -> {
                                        val captureController = rememberCaptureController()
                                        Capturable(
                                            controller = captureController,
                                            onCaptured = { bitmap, error ->
                                                if (bitmap != null) {
                                                    saveBitmapToGallery(
                                                        context,
                                                        bitmap.asAndroidBitmap(),
                                                        msg.orderId
                                                    )
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Capture failed",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        ) {
                                            Card(
                                                shape = RoundedCornerShape(16.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                                border = BorderStroke(2.dp, goldenBrush),
                                                modifier = Modifier.fillMaxWidth(0.9f)
                                            ) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.Center
                                                    ) {
                                                        Text(
                                                            "OFFICIAL RECEIPT",
                                                            fontSize = 16.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = TerracottaPrimary
                                                        )
                                                    }
                                                    Text(
                                                        "Order ID: ${msg.orderId}",
                                                        fontSize = 12.sp,
                                                        color = Color.Gray
                                                    )
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Box(
                                                        modifier = Modifier.fillMaxWidth()
                                                            .background(
                                                                if (isFarmer) Color(0xFFFFF3E0) else Color(
                                                                    0xFFE8F5E9
                                                                ), RoundedCornerShape(8.dp)
                                                            ).padding(12.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Text(
                                                                if (isFarmer) "YOUR PICKUP OTP" else "YOUR DROP OTP",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isFarmer) Color(
                                                                    0xFFE65100
                                                                ) else Color(0xFF2E7D32)
                                                            )
                                                            Text(
                                                                if (isFarmer) msg.pickupOtp else msg.dropOtp,
                                                                fontSize = 28.sp,
                                                                fontWeight = FontWeight.Black,
                                                                color = if (isFarmer) Color(
                                                                    0xFFE65100
                                                                ) else Color(0xFF2E7D32),
                                                                letterSpacing = 4.sp
                                                            )
                                                            Text(
                                                                "Share with driver on arrival",
                                                                fontSize = 10.sp,
                                                                color = Color.Gray
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "RESCUE_CARD" -> {
                                        Card(
                                            shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = BorderStroke(1.dp, GoldenYellow), modifier = Modifier.fillMaxWidth(0.85f)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(if (isMe) "Sent Rescue Request" else "Received Rescue Request", fontSize = 11.sp, color = TerracottaPrimary, fontWeight = FontWeight.ExtraBold)
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row {
                                                    AsyncImage(model = baseUrl + msg.imageUrl, contentDescription = null, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column {
                                                        Text(msg.cropName, fontWeight = FontWeight.Bold, color = TextDark)
                                                        Text("Requested: ${msg.kg} kg", fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32), fontSize = 13.sp)
                                                    }
                                                }
                                                if (msg.status == "PENDING" && isFarmer) {
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        TextButton(onClick = {
                                                            coroutineScope.launch(Dispatchers.IO) {
                                                                try {
                                                                    val payload = com.simats.growise.data.model.TransitionDealRequest(chatId = chatId, dealId = msg.id, status = "REJECTED")
                                                                    dealViewModel.transitionDeal(payload)
                                                                } catch (e: Exception) { e.printStackTrace() }
                                                            }
                                                        }) { Text("Reject", color = Color.Red) }
                                                        Button(
                                                            onClick = {
                                                                Log.d("ACCEPT_FLOW", "Accept clicked for DONATION deal: ${msg.id}")
                                                                isAccepting = true
                                                                coroutineScope.launch(Dispatchers.IO) {
                                                                    try {
                                                                        val payload = com.simats.growise.data.model.TransitionDealRequest(chatId = chatId, dealId = msg.id, status = "ACCEPTED")
                                                                        Log.d("ACCEPT_FLOW", "Calling transitionDeal API for Donation...")
                                                                        val res = dealViewModel.transitionDeal(payload)
                                                                        withContext(Dispatchers.Main) {
                                                                            isAccepting = false
                                                                            if (res.isSuccessful) {
                                                                                Log.d("ACCEPT_FLOW", "Donation API Success!")
                                                                                Toast.makeText(context, "Donation Accepted!", Toast.LENGTH_SHORT).show()
                                                                                pendingAddressMsg = msg; showAddressDialog = true
                                                                            } else {
                                                                                val err = res.errorBody()?.string() ?: "Unknown error"
                                                                                Log.e("ACCEPT_FLOW", "Donation API Failed: $err")
                                                                                Toast.makeText(context, "API Error: $err", Toast.LENGTH_LONG).show()
                                                                            }
                                                                        }
                                                                    } catch (e: Exception) { 
                                                                        Log.e("ACCEPT_FLOW", "Donation Accept Failed", e)
                                                                        withContext(Dispatchers.Main) {
                                                                            isAccepting = false
                                                                            Toast.makeText(context, "Network Error: ${e.message}", Toast.LENGTH_LONG).show()
                                                                        }
                                                                    }
                                                                }
                                                            },
                                                            enabled = !isAccepting,
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                                        ) { 
                                                            if (isAccepting) {
                                                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                            } else {
                                                                Text("Accept", color = Color.White) 
                                                            }
                                                        }
                                                    }
                                                } else if (msg.status == "ACCEPTED" && isFarmer) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Button(onClick = { pendingAddressMsg = msg; showAddressDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)) { Text("Set Pickup Location", color = Color.White) }
                                                } else {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text("Status: ${msg.status}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (msg.status == "ACCEPTED" || msg.status == "LOGISTICS_PENDING") Color(0xFF2E7D32) else Color.Gray)
                                                }
                                            }
                                        }
                                    }
                                    "DONATION_ORDER_CARD" -> {
                                        DonationWorkflowCard(
                                            msg = msg,
                                            isMe = isMe,
                                            isFarmer = isFarmer,
                                            currentUserEmail = currentUserEmail,
                                            myProfile = myProfile,
                                            baseUrl = baseUrl,
                                            chatId = chatId,
                                            dealViewModel = dealViewModel,
                                            onSetPickupAddress = {
                                                pendingAddressMsg = it
                                                showAddressDialog = true
                                            }
                                        )
                                    }
                                    "SYSTEM_MESSAGE", "DONATION_EVENT" -> {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (msg.text.isNotEmpty()) msg.text else msg.event,
                                                color = Color.Gray,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                    "DONATION_DELIVERY" -> {
                                        Card(
                                            shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
                                            border = BorderStroke(1.dp, Color(0xFF9C27B0)), modifier = Modifier.fillMaxWidth(0.9f)
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Filled.VolunteerActivism, contentDescription = null, tint = Color(0xFF9C27B0), modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Donation Delivery", color = Color(0xFF9C27B0), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                                }
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text("Status: ${msg.status}", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text("Pickup Address:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextMuted)
                                                Text(msg.pickupAddress, fontSize = 12.sp, color = TextDark)
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text("Delivery Address:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextMuted)
                                                Text(msg.dropAddress.takeIf { it.isNotBlank() } ?: "Drop location unavailable", fontSize = 12.sp, color = TextDark)

                                                if (!isFarmer && msg.status == "PENDING") {
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Button(
                                                            onClick = {
                                                                val isDonation = msg.dealType == "DONATION" || msg.type == "DONATION_REQUEST"
                                                                val origId = msg.dealId.takeIf { !it.isNullOrEmpty() } ?: msg.id
                                                                
                                                                if (isDonation) {
                                                                    coroutineScope.launch(Dispatchers.IO) {
                                                                        try {
                                                                            val req = com.simats.growise.data.model.OrderRequest(
                                                                                orderId = "",
                                                                                farmerEmail = msg.senderId,
                                                                                userEmail = msg.receiverId,
                                                                                cropName = msg.cropName,
                                                                                weightKg = msg.kg,
                                                                                cropValue = 0.0,
                                                                                transportFare = 0.0,
                                                                                totalPaid = 0.0,
                                                                                isDonation = true,
                                                                                pickupAddress = msg.pickupAddress,
                                                                                dropAddress = "Self Pickup",
                                                                                pickupLat = msg.rawPickupLat.toString().toDoubleOrNull() ?: 0.0,
                                                                                pickupLon = msg.rawPickupLon.toString().toDoubleOrNull() ?: 0.0,
                                                                                dropLat = 0.0,
                                                                                dropLon = 0.0,
                                                                                distanceKm = 0.0,
                                                                                vehicleType = "Self Pickup",
                                                                                pickupOtp = "",
                                                                                dropOtp = "",
                                                                                dealId = origId,
                                                                                itemId = msg.itemId,
                                                                                chatId = chatId
                                                                            )
                                                                            RetrofitClient.apiService.createOrder(req)
                                                                        } catch (e: Exception) { e.printStackTrace() }
                                                                    }
                                                                } else {
                                                                    val payload = com.simats.growise.data.model.TransitionDealRequest(
                                                                        chatId = chatId,
                                                                        dealId = origId,
                                                                        type = "TEXT",
                                                                        senderId = currentUserEmail,
                                                                        receiverId = targetEmail,
                                                                        text = "I have placed the order for ${msg.cropName}.",
                                                                        timestamp = System.currentTimeMillis()
                                                                    )
                                                                    
                                                                    coroutineScope.launch(Dispatchers.IO) {
                                                                        try {
                                                                            dealViewModel.transitionDeal(payload)
                                                                        } catch (e: Exception) { e.printStackTrace() }
                                                                    }
                                                                }
                                                            },
                                                            modifier = Modifier.weight(1f).padding(end = 4.dp),
                                                            colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)
                                                        ) {
                                                            Text("Self Pickup", color = Color.White, fontSize = 11.sp)
                                                        }
                                                        Button(
                                                            onClick = {
                                                                val isDonation = msg.dealType == "DONATION" || msg.type == "DONATION_REQUEST"
                                                                val origId = msg.dealId.takeIf { !it.isNullOrEmpty() } ?: msg.id
                                                                if (isDonation) {
                                                                    coroutineScope.launch(Dispatchers.IO) {
                                                                        try {
                                                                            val payload = com.simats.growise.data.model.TransitionDealRequest(
                                                                                chatId = chatId,
                                                                                dealId = origId,
                                                                                type = "RECEIPT_CARD",
                                                                                status = "COMPLETED",
                                                                                logisticsType = "GRO-WISE",
                                                                                transportCost = msg.transportCost,
                                                                                totalPrice = msg.totalPrice,
                                                                                timestamp = System.currentTimeMillis()
                                                                            )
                                                                            dealViewModel.transitionDeal(payload)
                                                                        } catch (e: Exception) { e.printStackTrace() }
                                                                    }
                                                                }
                                                                pendingAddressMsg = msg
                                                                showAddressDialog = true
                                                            },
                                                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                                                        ) {
                                                            Text("Delivery Partner", color = Color.White, fontSize = 11.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "LOGISTICS_CHOICE" -> {
                                        Card(
                                            shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = BorderStroke(2.dp, GoldenYellow), modifier = Modifier.fillMaxWidth(0.9f)
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text("Logistics Selection", fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary, fontSize = 16.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text("Pickup Address: ${msg.pickupAddress}", fontSize = 11.sp, color = Color.Gray)
                                                if (msg.status != "SELECTED" && msg.status != "INVOICED" && msg.status != "SELF_SERVICE" && !isFarmer) {
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    OutlinedButton(
                                                        onClick = {
                                                            coroutineScope.launch(Dispatchers.IO) {
                                                                try {
                                                                    val origId = msg.dealId.takeIf { !it.isNullOrEmpty() } ?: msg.id
                                                                    val payload = com.simats.growise.data.model.TransitionDealRequest(
                                                                        chatId = chatId,
                                                                        dealId = origId,
                                                                        type = "INVOICE_CARD",
                                                                        status = "PENDING",
                                                                        logisticsType = "SELF",
                                                                        distanceKm = 0.0,
                                                                        vehicleType = "Self Pickup",
                                                                        transportCost = 0.0,
                                                                        dropAddress = "Self Pickup"
                                                                    )
                                                                    val jsonRequest = com.google.gson.Gson().toJson(payload)
                                                                    Log.d("TRANSITION_REQUEST", "Self Pickup: $jsonRequest")
                                                                    val resSelf = dealViewModel.transitionDeal(payload)
                                                                    Log.d("TRANSITION_RESPONSE", "Status: ${resSelf.code()}, Body: ${resSelf.body()}, Error: ${resSelf.errorBody()?.string()}")
                                                                } catch (e: Exception) {
                                                                    e.printStackTrace()
                                                                }
                                                            }
                                                        }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, TerracottaPrimary)
                                                    ) { Text("Self Service (Free)", color = TerracottaPrimary) }
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Button(
                                                        onClick = { pendingAddressMsg = msg; showAddressDialog = true },
                                                        modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)
                                                    ) { Text("GroWise Delivery (Paid Transport)", color = Color.White) }
                                                } else {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text("Status: ${msg.status}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                                }
                                            }
                                        }
                                    }
                                    else -> {
                                        Box(
                                            modifier = Modifier.clip(RoundedCornerShape(16.dp))
                                                .background(if (isMe) TerracottaPrimary else Color.White)
                                                .border(
                                                    1.dp,
                                                    if (isMe) Color.Transparent else PeachBackground,
                                                    RoundedCornerShape(16.dp)
                                                ).padding(16.dp)
                                        ) {
                                            Text(
                                                text = msg.text,
                                                color = if (isMe) Color.White else TextDark
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, PeachBackground),
                            modifier = Modifier.weight(1f).height(56.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
                            ) {
                                OutlinedTextField(
                                    value = messageText,
                                    onValueChange = { messageText = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("Type a message...", color = Color.Gray) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier.size(56.dp)
                                .background(TerracottaPrimary, CircleShape).clickable {
                                if (messageText.isNotEmpty()) {
                                    val msgData = hashMapOf(
                                        "senderId" to currentUserEmail,
                                        "receiverId" to targetEmail,
                                        "type" to "TEXT",
                                        "text" to messageText,
                                        "timestamp" to System.currentTimeMillis()
                                    )
                                    db.collection("chats").document(chatId).collection("messages")
                                        .add(msgData)
                                    db.collection("chats").document(chatId).set(
                                        hashMapOf(
                                            "lastMessage" to messageText,
                                            "timestamp" to System.currentTimeMillis(),
                                            (if (isFarmer) "unreadCountUser" else "unreadCountFarmer") to FieldValue.increment(
                                                1
                                            )
                                        ), SetOptions.merge()
                                    )
                                    messageText = ""
                                }
                            }, contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White
                            )
                        }
                    }
            }
        }
    }
}

// --- GLOBAL EXPORT HELPER FUNCTIONS ---
    fun exportReceiptAsPdf(context: Context, msg: ChatMessage) {
        try {
            val pdfDocument = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(400, 600, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val paint = android.graphics.Paint()
                .apply { textSize = 14f; color = android.graphics.Color.BLACK }

            val fullDateStr = SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
            ).format(Date(msg.timestamp))

            canvas.drawText("GROWISE OFFICIAL RECEIPT", 100f, 40f, paint)
            canvas.drawText("Order ID: ${msg.orderId}", 20f, 70f, paint)
            canvas.drawText("Date: $fullDateStr", 20f, 90f, paint)

            canvas.drawText("Crop: ${msg.cropName} (${msg.kg} kg)", 20f, 130f, paint)
            canvas.drawText("Crop Value: ₹${msg.totalPrice - msg.transportCost}", 20f, 160f, paint)
            canvas.drawText("Transport: ₹${msg.transportCost}", 20f, 190f, paint)
            canvas.drawText("Total Paid: ₹${msg.totalPrice}", 20f, 220f, paint)

            canvas.drawText("From: ${msg.farmerName}", 20f, 260f, paint)
            canvas.drawText("Address: ${msg.pickupAddress}", 20f, 280f, paint)
            canvas.drawText("GPS: ${msg.pickupLat}, ${msg.pickupLon}", 20f, 300f, paint)

            canvas.drawText("To: ${msg.userName}", 20f, 340f, paint)
            canvas.drawText("Address: ${msg.dropAddress.takeIf { it.isNotBlank() } ?: "Drop location unavailable"}", 20f, 360f, paint)
            canvas.drawText("GPS: ${msg.dropLat}, ${msg.dropLon}", 20f, 380f, paint)

            pdfDocument.finishPage(page)
            val fileName = "GroWise_Receipt_${msg.orderId}.pdf"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(
                        android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    )
                }
                val uri = resolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { pdfDocument.writeTo(it) }
                    Toast.makeText(context, "PDF Saved to Downloads Folder", Toast.LENGTH_LONG)
                        .show()
                }
            } else {
                val file = File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )
                pdfDocument.writeTo(java.io.FileOutputStream(file))
                Toast.makeText(context, "PDF Saved to Downloads Folder", Toast.LENGTH_LONG).show()
            }
            pdfDocument.close()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save PDF", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveBitmapToGallery(context: Context, bitmap: android.graphics.Bitmap, orderId: String) {
        try {
            val filename = "GroWise_Receipt_$orderId.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(
                        android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_PICTURES + "/GroWise"
                    )
                }
                val imageUri = resolver.insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                if (imageUri != null) {
                    resolver.openOutputStream(imageUri)?.use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    Toast.makeText(context, "Image Saved to Pictures/GroWise", Toast.LENGTH_SHORT)
                        .show()
                }
            } else {
                val dir = File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
                    "GroWise"
                )
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, filename)
                java.io.FileOutputStream(file).use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                Toast.makeText(context, "Image Saved to Pictures/GroWise", Toast.LENGTH_SHORT)
                    .show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save Image", Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    fun ScrollToBottomFab(show: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
        androidx.compose.animation.AnimatedVisibility(
            visible = show,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = modifier
        ) {
            FloatingActionButton(
                onClick = onClick,
                containerColor = TerracottaPrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Scroll to bottom")
            }
        }
    }

@Composable
fun DonationWorkflowCard(
    msg: com.simats.growise.data.model.ChatMessage,
    isMe: Boolean,
    isFarmer: Boolean,
    currentUserEmail: String,
    myProfile: com.simats.growise.data.model.ProfileDetailsResponse?,
    baseUrl: String,
    chatId: String,
    dealViewModel: com.simats.growise.ui.viewmodel.DealViewModel,
    onSetPickupAddress: (com.simats.growise.data.model.ChatMessage) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var finalImageUrl by remember(msg.imageUrl) { mutableStateOf(msg.imageUrl) }

    LaunchedEffect(msg.itemId, msg.imageUrl) {
        if (msg.imageUrl.isBlank() && msg.itemId.isNotBlank()) {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val invDoc = db.collection("inventory").document(msg.itemId).get().await()
                if (invDoc.exists()) {
                    val invImage = invDoc.getString("imageUrl") ?: invDoc.getString("image") ?: ""
                    if (invImage.isNotBlank()) {
                        finalImageUrl = invImage
                        // Optimistically cache it on the order doc so we don't fetch it next time
                        try {
                            db.collection("orders").document(msg.orderId).update("imageUrl", invImage)
                        } catch (e: Exception) {}
                    }
                }
            } catch (e: Exception) {}
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, GoldenYellow),
        modifier = Modifier.fillMaxWidth(0.85f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (isMe) "Sent Donation Request" else "Received Donation Request", fontSize = 11.sp, color = TerracottaPrimary, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            Row {
                AsyncImage(
                    model = baseUrl + finalImageUrl, contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape).border(1.dp, GoldenYellow, CircleShape),
                    contentScale = ContentScale.Crop, error = painterResource(id = R.drawable.app_logo), placeholder = painterResource(id = R.drawable.app_logo)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(msg.cropName, fontWeight = FontWeight.Bold, color = TextDark)
                    Text("Required: ${msg.kg} kg", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val statusText = when(msg.status) {
                        "PENDING_FARMER_APPROVAL" -> "Waiting for Farmer Approval"
                        "WAITING_FOR_TRANSPORT_SELECTION" -> "Choose Transport"
                        "PENDING_DRIVER" -> "Driver Assigned"
                        "READY_FOR_PICKUP" -> "Ready for Pickup"
                        "COMPLETED" -> "Donation Completed"
                        "REJECTED" -> "Donation Declined"
                        "WITHDRAWN" -> "Donation Withdrawn"
                        else -> msg.status
                    }
                    val statusColor = when (msg.status) {
                        "COMPLETED", "READY_FOR_PICKUP", "WAITING_FOR_TRANSPORT_SELECTION", "PENDING_DRIVER" -> Color(0xFF2E7D32)
                        "REJECTED", "WITHDRAWN" -> Color.Red
                        else -> Color.Gray
                    }
                    Text("Status: $statusText", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor)
                }
            }

            when (msg.status) {
                "PENDING_FARMER_APPROVAL" -> {
                    if (isFarmer) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val req = com.simats.growise.data.model.RejectDonationRequest(msg.orderId, currentUserEmail)
                                            RetrofitClient.apiService.rejectDonation(req)
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text("Decline", color = Color.Red, fontSize = 11.sp) }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val req = com.simats.growise.data.model.AcceptDonationRequest(msg.orderId, currentUserEmail)
                                            RetrofitClient.apiService.acceptDonation(req)
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("Accept", fontSize = 11.sp, color = Color.White) }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val payload = com.simats.growise.data.model.TransitionDealRequest(chatId = chatId, dealId = msg.orderId, status = "WITHDRAWN")
                                            dealViewModel.transitionDeal(payload)
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text("Withdraw Request", color = Color.Red, fontSize = 11.sp) }
                        }
                    }
                }
                "WAITING_FOR_TRANSPORT_SELECTION" -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (isFarmer) {
                        if (msg.pickupLat == 0.0 || msg.pickupAddress.isBlank()) {
                            Button(
                                onClick = { onSetPickupAddress(msg) },
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)
                            ) { Text("Set Pickup Location", fontSize = 11.sp, color = Color.White) }
                        } else {
                            Text("Pickup Address", fontSize = 10.sp, color = Color.Gray)
                            Text(msg.pickupAddress, fontSize = 11.sp, color = TextDark)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onSetPickupAddress(msg) },
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, TerracottaPrimary)
                            ) { Text("Edit Pickup Location", fontSize = 11.sp, color = TerracottaPrimary) }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Waiting for NGO to choose transport...", fontSize = 10.sp, color = Color.Gray)
                        }
                    } else {
                        if (msg.pickupLat == 0.0 || msg.pickupAddress.isBlank()) {
                            Text("Waiting for Farmer to set Pickup Location...", fontSize = 10.sp, color = Color.Gray)
                        } else {
                            Text("Pickup Address", fontSize = 10.sp, color = Color.Gray)
                            Text(msg.pickupAddress, fontSize = 11.sp, color = TextDark)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Choose Transport", fontSize = 10.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val req = com.simats.growise.data.model.ConfirmDonationLogisticsRequest(msg.orderId, currentUserEmail, "Self Pickup", 0.0, "Self Pickup", 0.0, 0.0, 0.0)
                                                RetrofitClient.apiService.confirmDonationLogistics(req)
                                            } catch (e: Exception) { e.printStackTrace() }
                                        }
                                    },
                                    modifier = Modifier.weight(1f).padding(end = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, TerracottaPrimary)
                                ) { Text("Self Pickup", fontSize = 11.sp, color = TerracottaPrimary) }
                                
                                Button(
                                    onClick = {
                                        onSetPickupAddress(msg)
                                    },
                                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)
                                ) { Text("Delivery", fontSize = 11.sp, color = Color.White) }
                            }
                        }
                    }
                }
                "READY_FOR_PICKUP" -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().background(if (isFarmer) Color(0xFFFFF3E0) else Color(0xFFE8F5E9), RoundedCornerShape(8.dp)).padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Order ID", fontSize = 10.sp, color = Color.Gray)
                            Text(msg.orderId.takeIf { it.isNotBlank() } ?: msg.dealId.takeIf { !it.isNullOrBlank() } ?: msg.id.removeSuffix("_invoice").removeSuffix("_receipt"), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            if (!isFarmer) {
                                Text("YOUR PICKUP OTP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                Text(msg.pickupOtp ?: "", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E7D32), letterSpacing = 4.sp)
                                Text("Share with Farmer on arrival", fontSize = 10.sp, color = Color.Gray)
                            } else {
                                Text("OTP GENERATED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                Text("Pickup OTP has been generated for the NGO.", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFFE65100), textAlign = TextAlign.Center)
                                Text("The NGO will share it with you.", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}
