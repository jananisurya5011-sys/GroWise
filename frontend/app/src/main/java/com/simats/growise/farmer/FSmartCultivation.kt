package com.simats.growise.farmer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.growise.R
import com.simats.growise.data.model.CultivationRequest
import com.simats.growise.data.model.CultivationTask
import com.simats.growise.data.model.TaskCompletionRequest
import com.simats.growise.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class ActiveCropState(
    val cropName: String,
    val tasks: List<CultivationTask>,
    var currentActiveDay: Int = 1,
    var consecutiveMisses: Int = 0,
    val processStatus: String = "Active",
    val startDate: String = ""
)

// Helper: Calculate actual calendar days passed since creation
fun getDaysPassed(startDateStr: String): Int {
    if (startDateStr.isEmpty()) return 0
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val start = sdf.parse(startDateStr.substringBefore(".")) ?: Date()
        val now = Date()
        ((now.time - start.time) / (1000 * 60 * 60 * 24)).toInt()
    } catch (e: Exception) { 0 }
}

// Helper: Format future target dates for locked tasks
fun getTargetDate(startDateStr: String, targetDay: Int): String {
    if (startDateStr.isEmpty()) return "Future Date"
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val start = sdf.parse(startDateStr.substringBefore(".")) ?: Date()
        val calendar = Calendar.getInstance()
        calendar.time = start
        calendar.add(Calendar.DAY_OF_YEAR, targetDay - 1)
        val displaySdf = SimpleDateFormat("MMM dd", Locale.getDefault())
        displaySdf.format(calendar.time)
    } catch (e: Exception) { "Future Date" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FSmartCultivation(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val sharedPref = remember { context.getSharedPreferences("GroWiseSession", android.content.Context.MODE_PRIVATE) }
    val userEmail = remember { sharedPref.getString("USER_EMAIL", "farmer@growise.com") ?: "farmer@growise.com" }

    var cropSearchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isInitialLoad by remember { mutableStateOf(true) } // FIX: Initial Skeleton UI State
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var activeCrops by remember { mutableStateOf<List<ActiveCropState>>(emptyList()) }
    var expandedCropIndex by remember { mutableStateOf<Int?>(0) }

    val displayCrops = activeCrops.filter { it.processStatus == "Active" }

    // Strict calendar calculation for tasks due today
    val tasksDueToday = displayCrops.count { crop ->
        val daysPassed = getDaysPassed(crop.startDate)
        crop.tasks.any { task -> task.day == crop.currentActiveDay && task.day <= (daysPassed + 1) }
    }

    LaunchedEffect(Unit) {
        try {
            val response = RetrofitClient.apiService.fetchActiveCrops(com.simats.growise.data.model.EmailRequest(email = userEmail))
            if (response.success && response.active_crops != null) {
                activeCrops = response.active_crops.map { data ->
                    ActiveCropState(
                        cropName = data.cropName,
                        tasks = data.roadmap,
                        currentActiveDay = data.currentDay,
                        consecutiveMisses = data.consecutiveMisses,
                        processStatus = data.processStatus,
                        startDate = data.startDate
                    )
                }
            }
        } catch (e: Exception) {
            errorMessage = "Heavy traffic occurs try again after some times"
        } finally {
            isInitialLoad = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.simats.growise.ui.theme.PeachBackground)
    ) {
        // --- 1. TOP NAV BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = com.simats.growise.ui.theme.TextDark)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "AI Scheduler",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = com.simats.growise.ui.theme.TerracottaPrimary
            )
        }

        if (isInitialLoad) {
            // FIX: Premium Loading State instead of blank screen flash
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val infiniteTransition = rememberInfiniteTransition()
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = 360f,
                    animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing))
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_agri_loading),
                        contentDescription = "Loading",
                        tint = com.simats.growise.ui.theme.TerracottaPrimary,
                        modifier = Modifier.size(64.dp).rotate(rotation)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Fetching Farm Data...", color = com.simats.growise.ui.theme.TerracottaPrimary, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // --- 2. NOTIFICATION DASHBOARD ---
            if (displayCrops.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (tasksDueToday > 0) com.simats.growise.ui.theme.ErrorRed.copy(alpha = 0.1f)
                        else com.simats.growise.ui.theme.GoldenYellow.copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(1.dp, if (tasksDueToday > 0) com.simats.growise.ui.theme.ErrorRed.copy(alpha = 0.3f)
                    else com.simats.growise.ui.theme.GoldenYellow.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (tasksDueToday > 0) Icons.Filled.NotificationsActive else Icons.Filled.CheckCircle,
                            contentDescription = "Alerts",
                            tint = if (tasksDueToday > 0) com.simats.growise.ui.theme.ErrorRed else com.simats.growise.ui.theme.GoldenYellow,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = if (tasksDueToday > 0) "$tasksDueToday Tasks Due Today" else "All Caught Up!",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = if (tasksDueToday > 0) com.simats.growise.ui.theme.ErrorRed else com.simats.growise.ui.theme.TextDark
                            )
                            Text(
                                text = if (tasksDueToday > 0) "Complete them to prevent timeline delays." else "Your active crops are on schedule.",
                                fontSize = 12.sp,
                                color = com.simats.growise.ui.theme.TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // --- 3. SEARCH BAR & GENERATOR TRIGGER ---
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                OutlinedTextField(
                    value = cropSearchQuery,
                    onValueChange = { cropSearchQuery = it },
                    placeholder = { Text("Track a new crop (e.g., Tomatoes)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = com.simats.growise.ui.theme.TerracottaPrimary,
                        unfocusedBorderColor = Color.White,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    leadingIcon = {
                        Icon(Icons.Filled.Eco, contentDescription = null, tint = com.simats.growise.ui.theme.GoldenYellow)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (cropSearchQuery.isNotBlank()) {
                            isLoading = true
                            errorMessage = null
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val request = CultivationRequest(email = userEmail, crop_name = cropSearchQuery)
                                    val response = RetrofitClient.apiService.generateRoadmap(request)
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        if (response.success && response.roadmap != null) {
                                            // Reset screen fetch entirely to pull DB anchored start date
                                            isInitialLoad = true
                                            val freshResponse = RetrofitClient.apiService.fetchActiveCrops(com.simats.growise.data.model.EmailRequest(email = userEmail))
                                            if (freshResponse.success && freshResponse.active_crops != null) {
                                                activeCrops = freshResponse.active_crops.map { data ->
                                                    ActiveCropState(
                                                        cropName = data.cropName,
                                                        tasks = data.roadmap,
                                                        currentActiveDay = data.currentDay,
                                                        consecutiveMisses = data.consecutiveMisses,
                                                        processStatus = data.processStatus,
                                                        startDate = data.startDate
                                                    )
                                                }
                                                cropSearchQuery = ""
                                                expandedCropIndex = 0
                                            }
                                            isInitialLoad = false
                                        } else {
                                            errorMessage = response.error ?: "Heavy traffic occurs try again after some times"
                                        }
                                    }
                                } catch (e: retrofit2.HttpException) {
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        val errorBody = e.response()?.errorBody()?.string() ?: ""
                                        if (errorBody.contains("quota", ignoreCase = true)) {
                                            errorMessage = "AI quota is over for today comeback tommorrow"
                                        } else {
                                            errorMessage = "Heavy traffic occurs try again after some times"
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        errorMessage = "Heavy traffic occurs try again after some times"
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = com.simats.growise.ui.theme.TerracottaPrimary),
                    enabled = !isLoading && cropSearchQuery.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Generate AI Roadmap", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- 4. MULTI-CROP TIMELINE VIEW ---
            Box(modifier = Modifier.fillMaxSize()) {
                if (errorMessage != null) {
                    Card(
                        modifier = Modifier.padding(24.dp).align(Alignment.Center),
                        colors = CardDefaults.cardColors(containerColor = com.simats.growise.ui.theme.ErrorRed.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, com.simats.growise.ui.theme.ErrorRed)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Error, contentDescription = null, tint = com.simats.growise.ui.theme.ErrorRed)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = errorMessage!!, color = com.simats.growise.ui.theme.ErrorRed, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (displayCrops.isEmpty() && !isLoading) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.Science, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Enter a crop to generate a smart schedule", color = Color.Gray, fontWeight = FontWeight.Medium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        itemsIndexed(displayCrops) { cropIndex, cropState ->
                            val isCropExpanded = expandedCropIndex == cropIndex
                            val arrowRotation by animateFloatAsState(if (isCropExpanded) 180f else 0f)
                            val daysPassed = getDaysPassed(cropState.startDate)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .clickable { expandedCropIndex = if (isCropExpanded) null else cropIndex },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE5D5C5)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(com.simats.growise.ui.theme.PeachBackground),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                cropState.cropName.take(1).uppercase(),
                                                fontWeight = FontWeight.Black,
                                                color = com.simats.growise.ui.theme.TerracottaPrimary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = cropState.cropName.uppercase(),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 16.sp,
                                                color = com.simats.growise.ui.theme.TextDark
                                            )
                                            Text(
                                                text = "Day ${cropState.currentActiveDay} of Cycle",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = com.simats.growise.ui.theme.TextMuted
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Expand",
                                        tint = com.simats.growise.ui.theme.TextDark,
                                        modifier = Modifier.rotate(arrowRotation)
                                    )
                                }
                            }

                            Column(modifier = Modifier.animateContentSize()) {
                                AnimatedVisibility(visible = isCropExpanded) {
                                    Column(modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)) {
                                        cropState.tasks.forEachIndexed { taskIndex, task ->
                                            val isLast = taskIndex == cropState.tasks.lastIndex
                                            val isCompleted = task.status == 1 || task.day < cropState.currentActiveDay

                                            // Real-time lock logic
                                            val isCurrentTask = task.day == cropState.currentActiveDay
                                            val isCalendarUnlocked = task.day <= (daysPassed + 1)

                                            val isActive = isCurrentTask && isCalendarUnlocked
                                            val isLocked = isCurrentTask && !isCalendarUnlocked

                                            TimelineNode(
                                                task = task,
                                                isLast = isLast,
                                                isCompleted = isCompleted,
                                                isActive = isActive,
                                                isLocked = isLocked,
                                                cropStartDate = cropState.startDate,
                                                onCompleteClick = {
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        try {
                                                            val nextTask = cropState.tasks.firstOrNull { it.day > task.day }
                                                            val nextDay = nextTask?.day ?: (task.day + 1)
                                                            val req = TaskCompletionRequest(userEmail, cropState.cropName, nextDay)
                                                            val res = RetrofitClient.apiService.markTaskDone(req)

                                                            if (res.success) {
                                                                withContext(Dispatchers.Main) {
                                                                    val updatedTasks = cropState.tasks.map { if (it.day == task.day) it.copy(status = 1) else it }
                                                                    val realIndex = activeCrops.indexOfFirst { it.cropName == cropState.cropName }

                                                                    if (realIndex != -1) {
                                                                        val updatedCrops = activeCrops.toMutableList()
                                                                        updatedCrops[realIndex] = cropState.copy(
                                                                            currentActiveDay = nextDay,
                                                                            consecutiveMisses = 0,
                                                                            tasks = updatedTasks,
                                                                            processStatus = if (nextTask == null) "Completed" else "Active"
                                                                        )
                                                                        activeCrops = updatedCrops
                                                                    }
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            withContext(Dispatchers.Main) {
                                                                errorMessage = "Heavy traffic occurs try again after some times"
                                                            }
                                                        }
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
            }
        }
    }
}

// Custom Premium Component
@Composable
fun SlideToComplete(onComplete: () -> Unit) {
    var swipeOffset by remember { mutableStateOf(0f) }
    var isCompleted by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFFE5D5C5))
    ) {
        val maxPx = with(density) { (maxWidth - 56.dp).toPx() }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Slide to Complete", color = com.simats.growise.ui.theme.TextDark.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(swipeOffset.roundToInt(), 0) }
                .size(56.dp)
                .clip(CircleShape)
                .background(com.simats.growise.ui.theme.TerracottaPrimary)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (swipeOffset > maxPx * 0.75f) {
                                swipeOffset = maxPx
                                if (!isCompleted) {
                                    isCompleted = true
                                    onComplete()
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

@Composable
fun TimelineNode(
    task: CultivationTask,
    isLast: Boolean,
    isCompleted: Boolean,
    isActive: Boolean,
    isLocked: Boolean,
    cropStartDate: String,
    onCompleteClick: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(isActive || isLocked) }

    val dotColor = when {
        isCompleted -> Color(0xFF2E7D32)
        isActive -> com.simats.growise.ui.theme.GoldenYellow
        task.status == -1 -> com.simats.growise.ui.theme.ErrorRed
        else -> Color(0xFFE0D5CC)
    }

    val cardBgColor = when {
        isActive -> com.simats.growise.ui.theme.GoldenYellow
        else -> Color.White
    }

    val arrowRotation by animateFloatAsState(if (isExpanded) 180f else 0f)

    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        // --- TIMELINE DRAWING (LEFT SIDE) ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(dotColor))
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(if (isExpanded) 190.dp else 90.dp)
                        .background(if (isCompleted) Color(0xFF2E7D32).copy(alpha = 0.5f) else Color(0xFFE0D5CC))
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // --- TASK CARD (RIGHT SIDE) ---
        Column(modifier = Modifier.weight(1f).padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "DAY ${task.day}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = com.simats.growise.ui.theme.TextMuted,
                    letterSpacing = 1.sp
                )

                if (task.status == 1) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Row(
                        modifier = Modifier
                            .background(Color(0xFF2E7D32).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF2E7D32).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Completed", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                } else if (isActive) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Row(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                            .border(1.dp, com.simats.growise.ui.theme.TextMuted.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Cloud, contentDescription = null, tint = com.simats.growise.ui.theme.TerracottaPrimary, modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Weather Checked", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextDark)
                    }
                } else if (isLocked) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Row(
                        modifier = Modifier
                            .background(Color(0xFFE5D5C5).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFE5D5C5), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = com.simats.growise.ui.theme.TextMuted, modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Upcoming: ${getTargetDate(cropStartDate, task.day)}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow))
                    .clickable { isExpanded = !isExpanded },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.dp, if (isActive) Color.Transparent else Color(0xFFE5D5C5)),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 4.dp else 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = task.title,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = com.simats.growise.ui.theme.TextDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Expand",
                            tint = com.simats.growise.ui.theme.TextDark,
                            modifier = Modifier.rotate(arrowRotation)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = task.description,
                        fontSize = 13.sp,
                        color = if (isActive) Color(0xFF4A3811) else com.simats.growise.ui.theme.TextMuted,
                        lineHeight = 18.sp,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    AnimatedVisibility(visible = isExpanded) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            // FIX: Only render SlideToComplete if the task is actively unlocked today
                            if (isActive) {
                                Divider(color = Color.Black.copy(alpha = 0.05f))
                                Spacer(modifier = Modifier.height(16.dp))
                                SlideToComplete(onComplete = onCompleteClick)
                            }
                        }
                    }
                }
            }
        }
    }
}