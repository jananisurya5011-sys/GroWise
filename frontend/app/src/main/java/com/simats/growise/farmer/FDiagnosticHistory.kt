package com.simats.growise.farmer

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import android.speech.tts.UtteranceProgressListener
import androidx.compose.material3.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import com.simats.growise.data.model.DiagnosticRecord
import com.simats.growise.data.network.RetrofitClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.Locale
import coil.compose.AsyncImage // NEW
import coil.request.ImageRequest // NEW

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FDiagnosticHistory(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("GroWiseSession", Context.MODE_PRIVATE) }
    val userEmail = remember { sharedPref.getString("USER_EMAIL", "farmer@growise.com") ?: "farmer@growise.com" }

    var historyList by remember { mutableStateOf<List<DiagnosticRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var playingRecordId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("en", "IN")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { playingRecordId = utteranceId }
                    override fun onDone(utteranceId: String?) { if (playingRecordId == utteranceId) playingRecordId = null }
                    override fun onError(utteranceId: String?) { if (playingRecordId == utteranceId) playingRecordId = null }
                })
            }
        }
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val response = RetrofitClient.apiService.fetchDiagnosisHistory(com.simats.growise.data.model.EmailRequest(email = userEmail))
                if (response.success && response.history != null) {
                    // Filter out locally deleted items so they do not reappear when fetching from the server
                    val deletedKeys = sharedPref.getStringSet("DELETED_LOGS", setOf()) ?: setOf()
                    historyList = response.history.filter { !deletedKeys.contains("${it.date}_${it.disease}") }
                } else {
                    errorMsg = response.error ?: "No historical data found."
                }
            } catch (e: Exception) {
                errorMsg = "Unable to fetch history. Check connection."
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.simats.growise.ui.theme.PeachBackground)
    ) {
        // Custom Premium Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.8f))
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = com.simats.growise.ui.theme.TextDark)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Diagnostic Ledger",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = com.simats.growise.ui.theme.TerracottaPrimary
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val infiniteTransition = rememberInfiniteTransition()

                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f, targetValue = 1.4f,
                        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Restart)
                    )
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 1f, targetValue = 0f,
                        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Restart)
                    )
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f, targetValue = 360f,
                        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing))
                    )

                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .border(2.dp, com.simats.growise.ui.theme.GoldenYellow.copy(alpha = pulseAlpha * 0.8f), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(com.simats.growise.ui.theme.TerracottaPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = com.simats.growise.R.drawable.ic_agri_loading),
                                contentDescription = "Loading",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp).rotate(rotation)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Fetching Farm Data...",
                        color = com.simats.growise.ui.theme.TerracottaPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        } else if (historyList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(errorMsg ?: "No saved diagnostics yet.", color = Color.Gray, fontSize = 16.sp)
            }
        } else {
            // Updated list implementation with Custom Bulletproof Swipe-to-Delete to avoid Material 3 version conflicts
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    items = historyList,
                    key = { "${it.date}_${it.disease}" }
                ) { record ->
                    var offsetX by remember { mutableStateOf(0f) }
                    val animatedOffset by animateFloatAsState(targetValue = offsetX, label = "swipe_anim")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        // Background Delete Reveal
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFFFEBEE))
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                "Delete Log",
                                color = Color(0xFFC62828),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        
                        // Swipe action handling
                        if (offsetX < -100f) {
                            var showConfirm by remember { mutableStateOf(false) }
                            if (!showConfirm) {
                                showConfirm = true
                            }
                            
                            if (showConfirm) {
                                AlertDialog(
                                    onDismissRequest = { 
                                        showConfirm = false
                                        offsetX = 0f
                                    },
                                    title = { Text("Delete Log?") },
                                    text = { Text("Remove this diagnostic record from ledger?") },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showConfirm = false
                                            coroutineScope.launch(Dispatchers.IO) {
                                                try {
                                                    record.id?.let {
                                                        RetrofitClient.apiService.deleteDiagnosisHistory(it, userEmail)
                                                        withContext(Dispatchers.Main) {
                                                            historyList = historyList.filter { item -> item.id != it }
                                                        }
                                                    }
                                                } catch (e: Exception) {}
                                            }
                                        }) { Text("Delete", color = Color.Red) }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { 
                                            showConfirm = false
                                            offsetX = 0f
                                        }) { Text("Cancel") }
                                    }
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            if (offsetX < -250f) {
                                                offsetX = -1500f
                                                historyList = historyList.filter { it != record }

                                                // Save to local deleted cache so it does not reappear on reload
                                                val deletedKeys = sharedPref.getStringSet("DELETED_LOGS", setOf())?.toMutableSet() ?: mutableSetOf()
                                                deletedKeys.add("${record.date}_${record.disease}")
                                                sharedPref.edit().putStringSet("DELETED_LOGS", deletedKeys).apply()
                                            } else {
                                                offsetX = 0f // Snap back if not swiped far enough
                                            }
                                        }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        // Only allow swiping left (negative offset)
                                        if (dragAmount < 0 || offsetX < 0) {
                                            offsetX = (offsetX + dragAmount).coerceAtMost(0f)
                                        }
                                    }
                                }
                        ) {
                            HistoryCard(
                                record = record,
                                tts = tts,
                                isPlaying = playingRecordId == "${record.date}_${record.disease}",
                                onTogglePlay = {
                                    val id = record.id ?: "${record.date}_${record.disease}"
                                    if (playingRecordId == id) {
                                        tts?.stop()
                                        playingRecordId = null
                                    } else {
                                        tts?.speak("${record.disease ?: "Unknown"}. Solution: ${record.remedy ?: ""}", TextToSpeech.QUEUE_FLUSH, null, id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryCard(record: DiagnosticRecord, tts: TextToSpeech?, isPlaying: Boolean, onTogglePlay: () -> Unit) {
    val isOnline = record.mode?.equals("Online", ignoreCase = true) ?: false

    // Formatting the date
    val displayDate = try {
        val dateStr = record.date ?: ""
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val formatter = SimpleDateFormat("MMM dd, yyyy \u2022 hh:mm a", Locale.getDefault())
        val dateObj = parser.parse(dateStr.substringBefore("."))
        dateObj?.let { formatter.format(it) } ?: dateStr
    } catch (e: Exception) {
        record.date?.take(10) ?: "Unknown Date"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Updated image string resolution rules ensuring absolute physical storage file parsing
            val filename = record.imagePath?.substringAfterLast("/")?.substringAfterLast("\\") ?: ""
            val cleanBase = com.simats.growise.data.network.RetrofitClient.BASE_URL.removeSuffix("/")
            val imageUrl = "$cleanBase/api/crop-doctor/serve-image/$filename"

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(com.simats.growise.ui.theme.PeachBackground),
                contentAlignment = Alignment.Center
            ) {
                // Underlying Loading Placeholder Animation
                val infiniteTransition = rememberInfiniteTransition()
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            1000,
                            easing = LinearEasing
                        )
                    )
                )
                Icon(
                    painter = painterResource(id = com.simats.growise.R.drawable.ic_agri_loading),
                    contentDescription = "Loading Image...",
                    tint = com.simats.growise.ui.theme.TerracottaPrimary.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp).rotate(rotation)
                )

                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Diagnostic Crop Image",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isOnline) Icons.Filled.CloudDone else Icons.Filled.SignalWifiOff,
                        contentDescription = null,
                        tint = if (isOnline) com.simats.growise.ui.theme.GoldenYellow else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = record.mode ?: "Unknown",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.simats.growise.ui.theme.TextMuted
                    )
                }
                Text(
                    text = displayDate,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = record.disease ?: "Unknown Disease",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = com.simats.growise.ui.theme.TerracottaPrimary,
                lineHeight = 22.sp
            )

            // Updated rule parameters to conditionally handle offline mode content isolation limits
            if (isOnline) {
                if (!record.remedy.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = record.remedy ?: "",
                        fontSize = 13.sp,
                        color = com.simats.growise.ui.theme.TextDark,
                        lineHeight = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onTogglePlay,
                    colors = ButtonDefaults.buttonColors(containerColor = com.simats.growise.ui.theme.PeachBackground),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.VolumeUp,
                        contentDescription = if (isPlaying) "Pause Audio" else "Play Audio",
                        tint = com.simats.growise.ui.theme.TerracottaPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPlaying) "Pause Audio" else "Listen to Remedy",
                        color = com.simats.growise.ui.theme.TerracottaPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
