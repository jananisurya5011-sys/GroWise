package com.simats.growise.farmer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.EnergySavingsLeaf
import android.speech.tts.UtteranceProgressListener
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.growise.data.model.SaveDiagnosisRequest
import com.simats.growise.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt

// Custom Premium Slide to Save Component
@Composable
fun SlideToSave(onSave: () -> Unit) {
    var swipeOffset by remember { mutableStateOf(0f) }
    var isCompleted by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(com.simats.growise.ui.theme.PeachBackground)
            .border(1.dp, com.simats.growise.ui.theme.TerracottaPrimary.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
    ) {
        val maxPx = with(density) { (maxWidth - 56.dp).toPx() }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (isCompleted) "Saved to Ledger" else "Slide to Save Log",
                color = com.simats.growise.ui.theme.TerracottaPrimary.copy(alpha = 0.8f),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                letterSpacing = 0.5.sp
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(swipeOffset.roundToInt(), 0) }
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(com.simats.growise.ui.theme.GoldenYellow, com.simats.growise.ui.theme.TerracottaPrimary)
                    )
                )
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (swipeOffset > maxPx * 0.75f) {
                                swipeOffset = maxPx
                                if (!isCompleted) {
                                    isCompleted = true
                                    onSave()
                                }
                            } else {
                                swipeOffset = 0f
                            }
                        }
                    ) { _, dragAmount ->
                        if (!isCompleted) {
                            swipeOffset = (swipeOffset + dragAmount).coerceIn(0f, maxPx)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FDiagnose(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("GroWiseSession", Context.MODE_PRIVATE) }
    val userEmail = remember { sharedPref.getString("USER_EMAIL", "farmer@growise.com") ?: "farmer@growise.com" }

    var isOnlineMode by rememberSaveable { mutableStateOf(true) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var galleryUri by remember { mutableStateOf<Uri?>(null) }

    var isAnalyzing by remember { mutableStateOf(false) }
    var diagnosisResult by remember { mutableStateOf<String?>(null) }
    var activeRemedy by remember { mutableStateOf<String>("") }
    var activeImagePath by remember { mutableStateOf<String>("") }
    var errorAlert by remember { mutableStateOf<String?>(null) }

    val languages = mapOf("English" to "en", "Tamil" to "ta", "Hindi" to "hi", "Telugu" to "te", "Malayalam" to "ml")
    var expandedLanguageMenu by remember { mutableStateOf(false) }
    var selectedLanguageName by remember { mutableStateOf("English") }
    var selectedLanguageCode by remember { mutableStateOf("en") }

    val classifier = remember { CropDiseaseClassifier(context) }
    var tts: TextToSpeech? by remember { mutableStateOf(null) }

    val infiniteTransition = rememberInfiniteTransition()
    val scanLinePosition by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Reverse)
    )

    var isAudioPlaying by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("en", "IN")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { isAudioPlaying = true }
                    override fun onDone(utteranceId: String?) { isAudioPlaying = false }
                    override fun onError(utteranceId: String?) { isAudioPlaying = false }
                })
            }
        }
        onDispose {
            tts?.stop()
            tts?.shutdown()
            classifier.close()
        }
    }

    fun updateTTSLanguage(code: String) {
        val loc = Locale(code)
        if (tts?.isLanguageAvailable(loc) == TextToSpeech.LANG_AVAILABLE || tts?.isLanguageAvailable(loc) == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
            tts?.language = loc
        }
    }

    fun getBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT < 28) {
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            } else {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            }
        } catch (e: Exception) { null }
    }

    fun createTempFileFromBitmap(bitmap: Bitmap): File {
        // Updated to use permanent filesDir to prevent image loss when clearing cache
        val file = File(context.filesDir, "crop_img_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)

        // FIX: Client-Side Image Compression (Resize & Compress)
        val maxDimension = 800
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val width = if (ratio > 1) maxDimension else (maxDimension * ratio).toInt()
        val height = if (ratio < 1) maxDimension else (maxDimension / ratio).toInt()

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
        // Compress scaled image at 70% quality for optimal network upload speed
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)

        outputStream.flush()
        outputStream.close()
        return file
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            capturedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            diagnosisResult = null
            errorAlert = null
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bmp = getBitmapFromUri(uri)
            if (bmp != null) {
                capturedBitmap = bmp.copy(Bitmap.Config.ARGB_8888, true)
                diagnosisResult = null
                errorAlert = null
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch()
        } else {
            Toast.makeText(context, "Camera permission is required.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(com.simats.growise.ui.theme.PeachBackground, Color(0xFFFAF2EB))
                )
            )
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // --- PREMIUM HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.clip(CircleShape).background(Color.White)
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = com.simats.growise.ui.theme.TextDark)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Crop Doctor",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = com.simats.growise.ui.theme.TerracottaPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            Box {
                IconButton(
                    onClick = { expandedLanguageMenu = true },
                    modifier = Modifier.clip(CircleShape).background(Color.White)
                ) {
                    Icon(Icons.Filled.Language, contentDescription = "Language", tint = com.simats.growise.ui.theme.GoldenYellow)
                }
                DropdownMenu(
                    expanded = expandedLanguageMenu,
                    onDismissRequest = { expandedLanguageMenu = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    languages.forEach { (name, code) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = name,
                                    fontWeight = if (code == selectedLanguageCode) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (code == selectedLanguageCode) com.simats.growise.ui.theme.TerracottaPrimary else com.simats.growise.ui.theme.TextDark
                                )
                            },
                            onClick = {
                                selectedLanguageName = name
                                selectedLanguageCode = code
                                updateTTSLanguage(code)
                                expandedLanguageMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- MODERN PILL TOGGLE ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(30.dp))
                .border(1.dp, Color(0xFFF0E5DB), RoundedCornerShape(30.dp))
                .padding(6.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isOnlineMode) com.simats.growise.ui.theme.TerracottaPrimary else Color.Transparent)
                        .clickable { isOnlineMode = true; errorAlert = null; diagnosisResult = null }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CloudQueue, contentDescription = null, tint = if (isOnlineMode) Color.White else Color.Gray, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Online AI", color = if (isOnlineMode) Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (!isOnlineMode) com.simats.growise.ui.theme.GoldenYellow else Color.Transparent)
                        .clickable { isOnlineMode = false; errorAlert = null; diagnosisResult = null }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SignalWifiOff, contentDescription = null, tint = if (!isOnlineMode) Color.White else Color.Gray, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Offline ML", color = if (!isOnlineMode) Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- ENHANCED SCANNER VIEWPORT ---
        Card(
            modifier = Modifier.fillMaxWidth().aspectRatio(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .drawWithContent {
                        drawContent()
                        if (capturedBitmap == null) {
                            val stroke = Stroke(
                                width = 4f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
                            )
                            drawRoundRect(color = Color.LightGray, style = stroke, cornerRadius = CornerRadius(20.dp.toPx()))
                        } else if (isAnalyzing) {
                            val y = size.height * scanLinePosition
                            val gradientBrush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, com.simats.growise.ui.theme.GoldenYellow.copy(alpha = 0.6f), Color.Transparent),
                                startY = y - 80f, endY = y + 80f
                            )
                            drawRect(brush = gradientBrush)
                            drawLine(color = com.simats.growise.ui.theme.GoldenYellow, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 8f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (capturedBitmap != null) {
                    Image(bitmap = capturedBitmap!!.asImageBitmap(), contentDescription = "Captured", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(72.dp).background(com.simats.growise.ui.theme.PeachBackground, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.EnergySavingsLeaf, contentDescription = null, tint = com.simats.growise.ui.theme.TerracottaPrimary, modifier = Modifier.size(36.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No crop selected", color = com.simats.growise.ui.theme.TextDark, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Position leaf clearly in frame", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- ACTION ROW ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE5D5C5)),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = "Gallery", tint = com.simats.growise.ui.theme.TerracottaPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gallery", fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.TerracottaPrimary)
            }

            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        cameraLauncher.launch()
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = com.simats.growise.ui.theme.TerracottaPrimary),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Camera", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Camera", color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val coroutineScope = rememberCoroutineScope()
        Button(
            onClick = {
                isAnalyzing = true
                errorAlert = null
                diagnosisResult = null
                tts?.stop()

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        if (isOnlineMode) {
                            capturedBitmap?.let { bmp ->
                                val file = createTempFileFromBitmap(bmp)
                                val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                                val langBody = selectedLanguageCode.toRequestBody("text/plain".toMediaTypeOrNull())

                                val response = RetrofitClient.apiService.uploadCropImage(body, langBody)
                                withContext(Dispatchers.Main) {
                                    isAnalyzing = false
                                    if (response.success) {
                                        val confidencePct = response.confidence * 100
                                        diagnosisResult = "Disease Identified: ${response.disease}\nConfidence: ${String.format("%.1f", confidencePct)}%"
                                        activeRemedy = response.remedy
                                        activeImagePath = response.imagePath ?: "Local"
                                    } else {
                                        errorAlert = "Heavy traffic occurs try again after some times"
                                    }
                                }
                            }
                        } else {
                            capturedBitmap?.let { bmp ->
                                val result = classifier.analyze(bmp)

                                // NEW: Save offline image to local cache so Coil can render it in history
                                val file = createTempFileFromBitmap(bmp)
                                val localFilePath = file.absolutePath

                                withContext(Dispatchers.Main) {
                                    isAnalyzing = false
                                    if (result.startsWith("Error")) {
                                        errorAlert = result.replace("Error: ", "")
                                    } else if (result.contains("Not a plant", ignoreCase = true) || result.contains("Negative", ignoreCase = true)) {
                                        errorAlert = "It is not a plant or crop. Show me the crop or plant."
                                    } else {
                                        diagnosisResult = result
                                        activeRemedy = "Switch to Online Mode for verified solutions."
                                        activeImagePath = localFilePath
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            isAnalyzing = false
                            if (e is HttpException && (e.code() == 429)) {
                                errorAlert = "AI quota is over for today comeback tommorrow"
                            } else if (e is HttpException && e.code() == 400) {
                                errorAlert = "It is not a plant or crop. Show me the crop or plant."
                            } else {
                                errorAlert = "Heavy traffic occurs try again after some times"
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            enabled = capturedBitmap != null && !isAnalyzing,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = com.simats.growise.ui.theme.GoldenYellow,
                disabledContainerColor = Color(0xFFE0E0E0)
            )
        ) {
            if (isAnalyzing) {
                Text("Scanning Image Viewport...", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            } else {
                Icon(Icons.Filled.ImageSearch, contentDescription = null, tint = if (capturedBitmap != null) Color.White else Color.Gray)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Run Health Diagnosis", color = if (capturedBitmap != null) Color.White else Color.Gray, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        AnimatedVisibility(visible = !isOnlineMode && diagnosisResult == null && errorAlert == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFFFD54F))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SignalWifiOff, contentDescription = null, tint = Color(0xFFF57F17), modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Offline Core Active", fontWeight = FontWeight.ExtraBold, color = Color(0xFFF57F17), fontSize = 14.sp)
                        Text("TFLite Model tracks limited crops without audio support.", fontSize = 12.sp, color = Color(0xFF5D4037), lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        errorAlert?.let { err ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFEF9A9A))
            ) {
                Text(text = err, color = Color(0xFFC62828), modifier = Modifier.padding(16.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        diagnosisResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Lab Results", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = com.simats.growise.ui.theme.GoldenYellow, letterSpacing = 1.sp)
                            Text("Diagnosis Complete", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = com.simats.growise.ui.theme.TerracottaPrimary)
                        }
                        if (isOnlineMode) {
                            IconButton(
                                onClick = {
                                    if (isAudioPlaying) {
                                        tts?.stop()
                                        isAudioPlaying = false
                                    } else {
                                        tts?.speak("${result.replace("\n", ". ")}. Solution: $activeRemedy", TextToSpeech.QUEUE_FLUSH, null, "diagnosis_audio")
                                    }
                                },
                                modifier = Modifier.background(com.simats.growise.ui.theme.PeachBackground, CircleShape).size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAudioPlaying) Icons.Filled.Pause else Icons.Filled.VolumeUp,
                                    contentDescription = if (isAudioPlaying) "Pause Audio Guide" else "Play Audio Guide",
                                    tint = com.simats.growise.ui.theme.TerracottaPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(text = result, fontSize = 15.sp, color = com.simats.growise.ui.theme.TextDark, lineHeight = 24.sp, fontWeight = FontWeight.Bold)

                    Divider(modifier = Modifier.padding(vertical = 16.dp), color = Color(0xFFF0E5DB))

                    Text("Recommended Solution", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = com.simats.growise.ui.theme.TerracottaPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = activeRemedy, fontSize = 14.sp, color = com.simats.growise.ui.theme.TextMuted, lineHeight = 22.sp, fontWeight = FontWeight.Medium)

                    Spacer(modifier = Modifier.height(24.dp))

                    SlideToSave(onSave = {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val req = SaveDiagnosisRequest(
                                    email = userEmail,
                                    disease = result.substringBefore("\n"),
                                    remedy = activeRemedy,
                                    imagePath = activeImagePath,
                                    mode = if (isOnlineMode) "Online" else "Offline",
                                    language = selectedLanguageName
                                )
                                RetrofitClient.apiService.saveDiagnosis(req)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Log saved to ledger.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Heavy traffic occurs try again after some times", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    })
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }

}

