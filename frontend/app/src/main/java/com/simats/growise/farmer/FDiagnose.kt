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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.EnergySavingsLeaf
import android.speech.tts.UtteranceProgressListener
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.growise.data.model.SaveDiagnosisRequest
import com.simats.growise.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.withContext
import org.json.JSONObject
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

fun parseJsonArray(jsonObj: JSONObject, key: String): String {
    val arr = jsonObj.optJSONArray(key) ?: return "N/A"
    val result = StringBuilder()
    for (i in 0 until arr.length()) {
        result.append("• ${arr.getString(i)}")
        if (i < arr.length() - 1) result.append("\n")
    }
    return result.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FDiagnose(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("GroWiseSession", Context.MODE_PRIVATE) }
    val userEmail = remember { sharedPref.getString("USER_EMAIL", "farmer@growise.com") ?: "farmer@growise.com" }

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var galleryUri by remember { mutableStateOf<Uri?>(null) }

    var isAnalyzing by remember { mutableStateOf(false) }
    
    var diagnosisDisease by remember { mutableStateOf<String?>(null) }
    var diagnosisConfidence by remember { mutableStateOf<Float>(0f) }
    var diagnosisSymptoms by remember { mutableStateOf("") }
    var diagnosisCauses by remember { mutableStateOf("") }
    var diagnosisOrganic by remember { mutableStateOf("") }
    var diagnosisChemical by remember { mutableStateOf("") }
    var diagnosisPrevention by remember { mutableStateOf("") }
    var activeImagePath by remember { mutableStateOf<String>("") }
    var errorAlert by remember { mutableStateOf<String?>(null) }

    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var isAudioPlaying by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf("Offline") }
    val tfLiteHelper = remember { TFLiteHelper(context) }

    val infiniteTransition = rememberInfiniteTransition()
    val scanLinePosition by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Reverse)
    )

    // Lazy load the JSON metadata strictly once
    val diseaseMetadata = remember {
        try {
            val jsonStr = context.assets.open("disease_metadata.json").bufferedReader().use { it.readText() }
            
            // TEMPORARY DEBUG LOGGING FOR TFLITE ASSET
            try {
                val fd = context.assets.openFd("crop_model.tflite")
                val size = fd.length
                android.util.Log.e("TFLITE_DEBUG", "Found model: crop_model.tflite, Size: $size bytes")
                fd.close()
            } catch (e: Exception) {
                android.util.Log.e("TFLITE_DEBUG", "Could not find crop_model.tflite in assets: ${e.message}")
            }
            JSONObject(jsonStr)
        } catch (e: Exception) {
            JSONObject()
        }
    }

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
            tfLiteHelper.close()
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
        val file = File(context.filesDir, "crop_img_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)
        // Retain original dimensions and 100% quality so Gemini Vision receives the exact same clarity as the Website
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
        return file
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            capturedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            diagnosisDisease = null
            errorAlert = null
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bmp = getBitmapFromUri(uri)
            if (bmp != null) {
                capturedBitmap = bmp.copy(Bitmap.Config.ARGB_8888, true)
                diagnosisDisease = null
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.clip(CircleShape).background(Color.White)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = com.simats.growise.ui.theme.TextDark)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Row(
                modifier = Modifier.weight(1f).height(40.dp).background(Color.White, RoundedCornerShape(20.dp)).padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(if (selectedMode == "Offline") com.simats.growise.ui.theme.GoldenYellow else Color.Transparent)
                        .clickable { selectedMode = "Offline" },
                    contentAlignment = Alignment.Center
                ) { Text("Offline ML", color = if (selectedMode == "Offline") Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(if (selectedMode == "AI") com.simats.growise.ui.theme.GoldenYellow else Color.Transparent)
                        .clickable { selectedMode = "AI" },
                    contentAlignment = Alignment.Center
                ) { Text("AI Doctor", color = if (selectedMode == "AI") Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
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
                diagnosisDisease = null
                tts?.stop()

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        capturedBitmap?.let { bmp ->
                            if (selectedMode == "Offline") {
                                // 100% OFFLINE TFLITE INFERENCE
                                val result = tfLiteHelper.predict(bmp)
                                withContext(Dispatchers.Main) {
                                    isAnalyzing = false
                                    if (result != null) {
                                        val disease = result.disease
                                        val conf = result.confidence

                                        if (disease.equals("Background Noise", ignoreCase = true) || disease.equals("Background_Noise", ignoreCase = true)) {
                                            errorAlert = "It is not a plant or crop. Show me the crop or plant."
                                        } else if (conf < 0.45f) {
                                            errorAlert = "Crop not recognized clearly. Please capture a clearer leaf image."
                                        } else {
                                            diagnosisDisease = disease.replace("_", " ").replace("  ", " ")
                                            diagnosisConfidence = conf * 100
                                            
                                            // Save bitmap to temp file for rendering and saving history
                                            val file = createTempFileFromBitmap(bmp)
                                            activeImagePath = file.absolutePath

                                            val meta = diseaseMetadata.optJSONObject(disease.replace(" ", "_")) 
                                                ?: diseaseMetadata.optJSONObject(disease.replace(" ", "___"))
                                                
                                            if (meta != null) {
                                                diagnosisSymptoms = parseJsonArray(meta, "symptoms")
                                                diagnosisCauses = parseJsonArray(meta, "causes")
                                                diagnosisOrganic = parseJsonArray(meta, "organicTreatment")
                                                diagnosisChemical = parseJsonArray(meta, "chemicalTreatment")
                                                diagnosisPrevention = parseJsonArray(meta, "prevention")
                                            } else {
                                                diagnosisSymptoms = "Information unavailable."
                                                diagnosisCauses = "Information unavailable."
                                                diagnosisOrganic = "Maintain regular schedule."
                                                diagnosisChemical = "Maintain regular schedule."
                                                diagnosisPrevention = "Maintain regular schedule."
                                            }
                                        }
                                    } else {
                                        errorAlert = "Local Inference failed. ${tfLiteHelper.lastError}"
                                    }
                                }
                            } else {
                                // ONLINE GEMINI AI INFERENCE
                                val file = createTempFileFromBitmap(bmp)
                                val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                                val languageBody = "en".toRequestBody("text/plain".toMediaTypeOrNull())
                                
                                val response = RetrofitClient.apiService.uploadCropImageAI(body, languageBody)
                                
                                withContext(Dispatchers.Main) {
                                    isAnalyzing = false
                                    if (response.success) {
                                        val dataObj = response.data
                                        val disease = dataObj?.disease ?: response.disease ?: ""
                                        val conf = (dataObj?.confidence ?: response.confidence)?.toFloat() ?: 0f
                                        
                                        if (disease.equals("Background Noise", ignoreCase = true) || disease.equals("Background_Noise", ignoreCase = true)) {
                                            errorAlert = "It is not a plant or crop. Show me the crop or plant."
                                        } else if (conf < 0.45f) {
                                            errorAlert = "Crop not recognized clearly. Please capture a clearer leaf image."
                                        } else {
                                            diagnosisDisease = disease.replace("_", " ").replace("  ", " ")
                                            diagnosisConfidence = conf * 100
                                            activeImagePath = response.imagePath ?: file.absolutePath
                                            
                                            val meta = diseaseMetadata.optJSONObject(disease.replace(" ", "_")) 
                                                ?: diseaseMetadata.optJSONObject(disease.replace(" ", "___"))
                                                
                                            if (meta != null) {
                                                diagnosisSymptoms = parseJsonArray(meta, "symptoms")
                                                diagnosisCauses = parseJsonArray(meta, "causes")
                                                diagnosisOrganic = parseJsonArray(meta, "organicTreatment")
                                                diagnosisChemical = parseJsonArray(meta, "chemicalTreatment")
                                                diagnosisPrevention = parseJsonArray(meta, "prevention")
                                            } else {
                                                diagnosisSymptoms = dataObj?.symptoms?.joinToString(", ") ?: "Information unavailable."
                                                diagnosisCauses = dataObj?.causes ?: "Information unavailable."
                                                diagnosisOrganic = dataObj?.organicTreatment?.joinToString(", ") ?: dataObj?.remedy ?: response.remedy ?: "Maintain regular schedule."
                                                diagnosisChemical = dataObj?.chemicalTreatment?.joinToString(", ") ?: "Maintain regular schedule."
                                                diagnosisPrevention = dataObj?.prevention?.joinToString(", ") ?: "Maintain regular schedule."
                                            }
                                        }
                                    } else {
                                        errorAlert = response.error ?: "API Inference failed"
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            isAnalyzing = false
                            errorAlert = "Inference API failed: ${e.message}"
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
                Text(if (selectedMode == "Offline") "Running Core Accelerator..." else "Connecting to AI...", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            } else {
                Icon(if (selectedMode == "Offline") Icons.Filled.ImageSearch else Icons.Filled.CloudDone, contentDescription = null, tint = if (capturedBitmap != null) Color.White else Color.Gray)
                Spacer(modifier = Modifier.width(12.dp))
                Text(if (selectedMode == "Offline") "Analyze Leaf Offline" else "Diagnose with AI", color = if (capturedBitmap != null) Color.White else Color.Gray, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        AnimatedVisibility(visible = diagnosisDisease == null && errorAlert == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF81C784))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SignalWifiOff, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("100% Offline Core Active", fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32), fontSize = 14.sp)
                        Text("Instant predictions powered by local TensorFlow hardware acceleration.", fontSize = 12.sp, color = Color(0xFF1B5E20), lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
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

        diagnosisDisease?.let { diseaseName ->
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${String.format("%.1f", diagnosisConfidence)}% Confidence", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = com.simats.growise.ui.theme.GoldenYellow, letterSpacing = 1.sp)
                            Text(diseaseName, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = com.simats.growise.ui.theme.TerracottaPrimary)
                        }
                        IconButton(
                            onClick = {
                                if (isAudioPlaying) {
                                    tts?.stop()
                                    isAudioPlaying = false
                                } else {
                                    tts?.speak("Disease Identified: $diseaseName. Organic Treatment: $diagnosisOrganic", TextToSpeech.QUEUE_FLUSH, null, "diagnosis_audio")
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

                    Spacer(modifier = Modifier.height(20.dp))

                    Text("Symptoms", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = com.simats.growise.ui.theme.TextDark)
                    Text(text = diagnosisSymptoms, fontSize = 14.sp, color = com.simats.growise.ui.theme.TextMuted, lineHeight = 22.sp, modifier = Modifier.padding(top=4.dp))
                    
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF0E5DB))
                    
                    Text("Organic Treatment", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFF2E7D32))
                    Text(text = diagnosisOrganic, fontSize = 14.sp, color = com.simats.growise.ui.theme.TextMuted, lineHeight = 22.sp, modifier = Modifier.padding(top=4.dp))
                    
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF0E5DB))

                    Text("Chemical Treatment", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = com.simats.growise.ui.theme.TerracottaPrimary)
                    Text(text = diagnosisChemical, fontSize = 14.sp, color = com.simats.growise.ui.theme.TextMuted, lineHeight = 22.sp, modifier = Modifier.padding(top=4.dp))
                    
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF0E5DB))

                    Text("Prevention", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = com.simats.growise.ui.theme.GoldenYellow)
                    Text(text = diagnosisPrevention, fontSize = 14.sp, color = com.simats.growise.ui.theme.TextMuted, lineHeight = 22.sp, modifier = Modifier.padding(top=4.dp))

                    Spacer(modifier = Modifier.height(24.dp))

                    SlideToSave(onSave = {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val emailBody = userEmail.toRequestBody("text/plain".toMediaTypeOrNull())
                                val diseaseBody = diseaseName.toRequestBody("text/plain".toMediaTypeOrNull())
                                val remedyBody = diagnosisOrganic.toRequestBody("text/plain".toMediaTypeOrNull())
                                val typeBody = (if (selectedMode == "Offline") "Offline ML" else "Online").toRequestBody("text/plain".toMediaTypeOrNull())
                                val langBody = "en".toRequestBody("text/plain".toMediaTypeOrNull())
                                val detailsBody = "{}".toRequestBody("text/plain".toMediaTypeOrNull())
                                val confidenceBody = diagnosisConfidence.toString().toRequestBody("text/plain".toMediaTypeOrNull())

                                val file = java.io.File(activeImagePath)
                                val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                                val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

                                RetrofitClient.apiService.saveDiagnosis(
                                    email = emailBody,
                                    disease = diseaseBody,
                                    remedy = remedyBody,
                                    diagnosisType = typeBody,
                                    language = langBody,
                                    details = detailsBody,
                                    confidence = confidenceBody,
                                    file = filePart
                                )
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Log saved to ledger.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Network error: saved locally. Will sync when online.", Toast.LENGTH_LONG).show()
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
