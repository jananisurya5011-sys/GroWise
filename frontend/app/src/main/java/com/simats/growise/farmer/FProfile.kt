package com.simats.growise.farmer

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.location.LocationServices
import com.simats.growise.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FProfile(
    userEmail: String = "farmer@growise.com",
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPref = remember { context.getSharedPreferences("GroWiseSession", android.content.Context.MODE_PRIVATE) }

    var isSaving by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var refreshProfileDataTrigger by remember { mutableStateOf(0) }

    var activeAddressType by remember { mutableStateOf("") }

    var capturedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showPhotoSelectorDialog by remember { mutableStateOf(false) }

    var fullName by remember { mutableStateOf(sharedPref.getString("FARMER_NAME", "Fetching Profile...") ?: "Fetching Profile...") }
    var memberSince by remember { mutableStateOf(sharedPref.getString("MEMBER_SINCE", "--") ?: "--") }
    var maskedAadhaar by remember { mutableStateOf(sharedPref.getString("AADHAAR_MASKED", "XXXX-XXXX-XXXX") ?: "XXXX-XXXX-XXXX") }
    var customUsername by remember { mutableStateOf(sharedPref.getString("USERNAME", "") ?: "") }
    var profileImageUrl by remember { mutableStateOf(sharedPref.getString("PROFILE_IMAGE_URL", "") ?: "") }
    var userRole by remember { mutableStateOf("farmer") }

    var primaryCrop by remember { mutableStateOf("") }
    var farmAddress by remember { mutableStateOf("") }
    var homeAddress by remember { mutableStateOf("") }
    var farmLat by remember { mutableStateOf(0.0) }
    var farmLon by remember { mutableStateOf(0.0) }
    var homeLat by remember { mutableStateOf(0.0) }
    var homeLon by remember { mutableStateOf(0.0) }
    var isSameAddress by remember { mutableStateOf(false) }
    var totalAcreage by remember { mutableStateOf("") }
    var selectedSoilType by remember { mutableStateOf("Select Soil Type") }
    var phoneNumber by remember { mutableStateOf("") }

    var isSoilDropdownExpanded by remember { mutableStateOf(false) }
    val soilOptions = listOf("Alluvial Soil", "Black Soil", "Red Soil", "Laterite Soil", "Desert Soil")

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            capturedImageBitmap = bitmap
            Toast.makeText(context, "Photo captured locally", Toast.LENGTH_SHORT).show()
        }
        showPhotoSelectorDialog = false
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
                capturedImageBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                Toast.makeText(context, "Photo selected from gallery", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
        showPhotoSelectorDialog = false
    }

    if (activeAddressType.isNotEmpty()) {
        AddressEntryDialog(
            addressType = activeAddressType,
            onDismiss = { activeAddressType = "" },
            onSaved = { address, lat, lon ->
                if (activeAddressType == "FARM" || activeAddressType == "BOTH") {
                    farmAddress = address
                    farmLat = lat
                    farmLon = lon
                }
                if (activeAddressType == "HOME" || activeAddressType == "BOTH") {
                    homeAddress = address
                    homeLat = lat
                    homeLon = lon
                }
                activeAddressType = ""
            }
        )
    }

    LaunchedEffect(refreshProfileDataTrigger) {
        try {
            val response = com.simats.growise.data.network.RetrofitClient.apiService.retrieveProfileFields(userEmail)
            if (response.isSuccessful) {
                response.body()?.let { data ->
                    fullName = data.name.ifEmpty { "Farmer" }
                    memberSince = data.member_since.ifEmpty { "--" }
                    customUsername = data.username ?: ""
                    profileImageUrl = data.profile_image_url ?: ""

                    val rawAadhaar = data.aadhaar_masked.ifEmpty { "" }
                    maskedAadhaar = if (rawAadhaar.length == 4) {
                        "XXXX-XXXX-$rawAadhaar"
                    } else {
                        "XXXX-XXXX-XXXX"
                    }

                    sharedPref.edit().apply {
                        putString("FARMER_NAME", fullName)
                        putString("MEMBER_SINCE", memberSince)
                        putString("AADHAAR_MASKED", maskedAadhaar)
                        putString("USERNAME", customUsername)
                        putString("PROFILE_IMAGE_URL", profileImageUrl)
                    }.apply()

                    userRole = data.role.ifEmpty { "farmer" }
                    primaryCrop = data.primary_crop
                    farmAddress = data.farm_address
                    homeAddress = data.home_address ?: ""
                    isSameAddress = data.is_same_address
                    selectedSoilType = data.soil_type.ifEmpty { "Select Soil Type" }
                    totalAcreage = data.total_acreage
                    phoneNumber = data.phone ?: ""
                    farmLat = data.farmLat ?: 0.0
                    farmLon = data.farmLon ?: 0.0
                    homeLat = data.homeLat ?: 0.0
                    homeLon = data.homeLon ?: 0.0
                }
            }
        } catch (e: Exception) {}
    }

    if (isSaving) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val infiniteTransition = rememberInfiniteTransition()
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Restart)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(com.simats.growise.ui.theme.PeachBackground.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_agri_loading),
                        contentDescription = "Loading Overlay",
                        tint = com.simats.growise.ui.theme.TerracottaPrimary,
                        modifier = Modifier
                            .size(72.dp)
                            .rotate(rotation)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Updating Secure Profile...",
                        color = com.simats.growise.ui.theme.TerracottaPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }

    if (showPhotoSelectorDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSelectorDialog = false },
            title = { Text("Update Profile Picture", fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TerracottaPrimary) },
            text = { Text("Choose the source for your new profile image.") },
            confirmButton = {
                TextButton(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Camera")
                }
            },
            dismissButton = {
                TextButton(onClick = { galleryLauncher.launch("image/*") }) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gallery")
                }
            },
            containerColor = Color.White
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.simats.growise.ui.theme.PeachBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Text(
            text = if (isEditMode) "Edit Information" else "GroWise Identity",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = com.simats.growise.ui.theme.TerracottaPrimary
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!isEditMode) {
            // ====================================================
            // 1. PREMIUM DIGITAL ID CARD (VIEW MODE)
                // ====================================================
                val infiniteTransition = rememberInfiniteTransition()
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(animation = tween(1000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse)
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(16.dp, shape = RoundedCornerShape(32.dp)),
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // A. Deep Gradient Header
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                com.simats.growise.ui.theme.TerracottaPrimary,
                                                com.simats.growise.ui.theme.GoldenYellow
                                            )
                                        )
                                    )
                            )
                            // B. Faded Watermark inside the card body
                            Icon(
                                painter = painterResource(id = R.drawable.app_logo),
                                contentDescription = "Watermark",
                                tint = Color.LightGray.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .size(220.dp)
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 40.dp, y = 40.dp)
                            )
                            // C. Verification Badge
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(20.dp)
                                    .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Verified", tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("GroWise Verified", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
                            }
                            // D. Profile Container (Centered & Overlapping)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(modifier = Modifier.height(90.dp))

                                // Animated Glowing Ring + Image
                                Box(contentAlignment = Alignment.Center) {
                                    Box(
                                        modifier = Modifier
                                            .size(130.dp)
                                            .scale(pulseScale)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.3f))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(120.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .padding(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .background(com.simats.growise.ui.theme.LogoBoxColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (capturedImageBitmap != null) {
                                                Image(bitmap = capturedImageBitmap!!.asImageBitmap(), contentDescription = "Photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            } else if (profileImageUrl.isNotEmpty()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data("${com.simats.growise.data.network.RetrofitClient.BASE_URL}${profileImageUrl.removePrefix("/")}")
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = "Profile Photo",
                                                    placeholder = painterResource(id = R.drawable.app_logo),
                                                    error = painterResource(id = R.drawable.app_logo),
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = "Default", modifier = Modifier.size(64.dp))
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                // Name & Tag
                                Text(
                                    text = fullName,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = com.simats.growise.ui.theme.TextDark,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (customUsername.isNotEmpty()) {
                                    Text(text = "@$customUsername", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.TerracottaPrimary)
                                } else {
                                    Text(text = "@Setup_Username", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.ErrorRed)
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // E. Data Island Card
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF7F2)),
                                    border = BorderStroke(1.dp, Color.White)
                                ) {
                                    Column(modifier = Modifier.padding(20.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column {
                                                Text("Aadhaar Number", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted)
                                                Text(maskedAadhaar, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.TextDark)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Primary Crop", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted)
                                                Text(if (primaryCrop.isEmpty()) "Not Set" else primaryCrop, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.TextDark)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        HorizontalDivider(color = Color(0xFFE8DFD8), thickness = 1.dp)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column {
                                                Text("Phone Number", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted)
                                                Text(if (phoneNumber.isEmpty()) "Not Set" else phoneNumber, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.TextDark)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Member Since", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted)
                                                Text(memberSince, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.TextDark)
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { isEditMode = true },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = com.simats.growise.ui.theme.TerracottaPrimary)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Edit Profile Details", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
        } // CLOSES THE MAIN BACKGROUND COLUMN

        // ====================================================
        // 2. SECURE EDITING FORM (EDIT MODE) - FULL SCREEN DIALOG
        // ====================================================
        if (isEditMode) {
            Dialog(onDismissRequest = { isEditMode = false; capturedImageBitmap = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Column(modifier = Modifier.fillMaxSize().background(com.simats.growise.ui.theme.PeachBackground).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp)) {
                    Text("Edit Information", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.TerracottaPrimary)
                    Spacer(modifier = Modifier.height(24.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier.size(120.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .padding(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(com.simats.growise.ui.theme.LogoBoxColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (capturedImageBitmap != null) {
                                        Image(bitmap = capturedImageBitmap!!.asImageBitmap(), contentDescription = "Captured", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    } else if (profileImageUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data("${com.simats.growise.data.network.RetrofitClient.BASE_URL}${profileImageUrl.removePrefix("/")}")
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Profile Photo",
                                            placeholder = painterResource(id = R.drawable.app_logo),
                                            error = painterResource(id = R.drawable.app_logo),
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = "Default", modifier = Modifier.size(64.dp))
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(com.simats.growise.ui.theme.TerracottaPrimary)
                                    .clickable { showPhotoSelectorDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = "Edit Photo", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Account Configuration", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TerracottaPrimary)
                            Spacer(modifier = Modifier.height(16.dp))

                            Text("Unique Username (No Spaces)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted)
                            OutlinedTextField(
                                value = customUsername,
                                onValueChange = { input -> customUsername = input.filter { !it.isWhitespace() } },
                                placeholder = { Text("e.g. Ranjith_77", color = Color.LightGray) },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = com.simats.growise.ui.theme.TerracottaPrimary, unfocusedBorderColor = Color(0xFFF9EFE9))
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Email Address", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted)
                            OutlinedTextField(
                                value = userEmail,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = Color.LightGray) },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledContainerColor = Color(0xFFFFFBF8),
                                    disabledBorderColor = Color(0xFFF9EFE9),
                                    disabledTextColor = com.simats.growise.ui.theme.TextDark
                                ),
                                enabled = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Address Management Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Address Management", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TerracottaPrimary)

                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Checkbox(
                                    checked = isSameAddress,
                                    onCheckedChange = {
                                        isSameAddress = it
                                        // Erase data dynamically to force user to re-enter
                                        farmAddress = ""
                                        homeAddress = ""
                                        farmLat = 0.0; farmLon = 0.0
                                        homeLat = 0.0; homeLon = 0.0
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = com.simats.growise.ui.theme.TerracottaPrimary)
                                )
                                Text("Farm and Home are same address", fontSize = 14.sp, color = com.simats.growise.ui.theme.TextDark, fontWeight = FontWeight.Medium)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (isSameAddress) {
                                Button(
                                    onClick = { activeAddressType = "BOTH" },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = com.simats.growise.ui.theme.GoldenYellow)
                                ) {
                                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Set Farm & Home Address", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                if (farmAddress.isNotEmpty()) {
                                    Text(farmAddress, fontSize = 13.sp, color = com.simats.growise.ui.theme.TextDark, modifier = Modifier.padding(top = 8.dp))
                                    Text("Lat: $farmLat, Lon: $farmLon", fontSize = 11.sp, color = Color.Gray)
                                }
                            } else {
                                Button(
                                    onClick = { activeAddressType = "FARM" },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = com.simats.growise.ui.theme.GoldenYellow)
                                ) {
                                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Set Farm Address", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                if (farmAddress.isNotEmpty()) {
                                    Text(farmAddress, fontSize = 13.sp, color = com.simats.growise.ui.theme.TextDark, modifier = Modifier.padding(top = 8.dp))
                                    Text("Lat: $farmLat, Lon: $farmLon", fontSize = 11.sp, color = Color.Gray)
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { activeAddressType = "HOME" },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = com.simats.growise.ui.theme.GoldenYellow)
                                ) {
                                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Set Home Address", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                if (homeAddress.isNotEmpty()) {
                                    Text(homeAddress, fontSize = 13.sp, color = com.simats.growise.ui.theme.TextDark, modifier = Modifier.padding(top = 8.dp))
                                    Text("Lat: $homeLat, Lon: $homeLon", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Land Details", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TerracottaPrimary)
                            Spacer(modifier = Modifier.height(16.dp))

                            Text("Soil Type", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted)
                            ExposedDropdownMenuBox(
                                expanded = isSoilDropdownExpanded,
                                onExpandedChange = { isSoilDropdownExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = selectedSoilType,
                                    onValueChange = {},
                                    readOnly = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = Color.White,
                                        focusedBorderColor = com.simats.growise.ui.theme.TerracottaPrimary,
                                        unfocusedBorderColor = Color(0xFFF9EFE9)
                                    ),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isSoilDropdownExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth().padding(top = 4.dp),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = isSoilDropdownExpanded,
                                    onDismissRequest = { isSoilDropdownExpanded = false },
                                    modifier = Modifier.background(Color.White)
                                ) {
                                    soilOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option, fontWeight = FontWeight.Medium) },
                                            onClick = {
                                                selectedSoilType = option
                                                isSoilDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Total Acreage", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted)
                            OutlinedTextField(
                                value = totalAcreage,
                                onValueChange = { input -> if (input.all { it.isDigit() || it == '.' }) totalAcreage = input },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                placeholder = { Text("Enter field acreage size", color = Color.LightGray) },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = com.simats.growise.ui.theme.TerracottaPrimary, unfocusedBorderColor = Color(0xFFF9EFE9))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Other Details", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TerracottaPrimary)
                            Spacer(modifier = Modifier.height(16.dp))

                            Text("Primary Crop Focus", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted)
                            OutlinedTextField(
                                value = primaryCrop,
                                onValueChange = { primaryCrop = it },
                                placeholder = { Text("e.g. Organic Wheat, Rice", color = Color.LightGray) },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = com.simats.growise.ui.theme.TerracottaPrimary, unfocusedBorderColor = Color(0xFFF9EFE9))
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Phone Number", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted)
                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { phoneNumber = it },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                placeholder = { Text("Enter current operational number", color = Color.LightGray) },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = com.simats.growise.ui.theme.TerracottaPrimary, unfocusedBorderColor = Color(0xFFF9EFE9))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = { isEditMode = false },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, com.simats.growise.ui.theme.TerracottaPrimary)
                        ) {
                            Text("Cancel", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TerracottaPrimary)
                        }

                        Button(
                            onClick = {
                                if (customUsername.isEmpty()) {
                                    Toast.makeText(context, "Username cannot be empty", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isSaving = true
                                coroutineScope.launch {
                                    try {
                                        if (capturedImageBitmap != null) {
                                            val file = File(context.cacheDir, "profile_image.jpg")
                                            val fos = FileOutputStream(file)
                                            capturedImageBitmap!!.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                                            fos.flush()
                                            fos.close()

                                            val mediaTypeImage = "image/jpeg".toMediaTypeOrNull()
                                            val requestFile = RequestBody.create(mediaTypeImage, file)
                                            val body = MultipartBody.Part.createFormData("profile_image", file.name, requestFile)

                                            val mediaTypeText ="text/plain".toMediaTypeOrNull()
                                            fun createPart(text: String) = RequestBody.create(mediaTypeText, text)

                                            val response = com.simats.growise.data.network.RetrofitClient.apiService.updateProfileWithImage(
                                                email = createPart(userEmail),
                                                primaryCrop = createPart(primaryCrop),
                                                farmAddress = createPart(farmAddress),
                                                homeAddress = createPart(homeAddress),
                                                isSameAddress = createPart(isSameAddress.toString()),
                                                soilType = createPart(selectedSoilType),
                                                totalAcreage = createPart(totalAcreage),
                                                username = createPart(customUsername),
                                                phone = createPart(phoneNumber),
                                                farmLat = createPart(farmLat.toString()),
                                                farmLon = createPart(farmLon.toString()),
                                                homeLat = createPart(homeLat.toString()),
                                                homeLon = createPart(homeLon.toString()),
                                                profile_image = body
                                            )

                                            if (response.isSuccessful) {
                                                Toast.makeText(context, "Profile & Photo updated", Toast.LENGTH_SHORT).show()
                                                capturedImageBitmap = null
                                                isEditMode = false
                                                refreshProfileDataTrigger++
                                            }
                                        } else {
                                            val updatePayload = com.simats.growise.data.model.ProfileUpdateRequest(
                                                email = userEmail,
                                                primaryCrop = primaryCrop,
                                                farmAddress = farmAddress,
                                                homeAddress = homeAddress,
                                                isSameAddress = isSameAddress,
                                                soilType = selectedSoilType,
                                                totalAcreage = totalAcreage,
                                                username = customUsername,
                                                phone = phoneNumber,
                                                farmLat = farmLat,
                                                farmLon = farmLon,
                                                homeLat = homeLat,
                                                homeLon = homeLon
                                            )
                                            val response = com.simats.growise.data.network.RetrofitClient.apiService.saveProfileFields(updatePayload)
                                            if (response.isSuccessful) {
                                                Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                                                isEditMode = false
                                                refreshProfileDataTrigger++
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Network handshake failure", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1.5f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = com.simats.growise.ui.theme.TerracottaPrimary)
                        ) {
                            Text("Save Profile", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(100.dp))
            } // Closes Dialog Column
        } // Closes Dialog
    } // Closes UProfile/FProfile Function
}

// ======================================================================
// PREMIUM DIALOG COMPONENT FOR ADDRESS ENTRY
// ======================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressEntryDialog(
    addressType: String,
    onDismiss: () -> Unit,
    onSaved: (address: String, lat: Double, lon: Double) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val haptic = LocalHapticFeedback.current

    var isManualTab by remember { mutableStateOf(false) }
    var isGpsFetching by remember { mutableStateOf(false) }

    // Manual Form States
    var stateInput by remember { mutableStateOf("") }
    var areaInput by remember { mutableStateOf("") }
    var pincodeInput by remember { mutableStateOf("") }
    var latInput by remember { mutableStateOf("") }
    var lonInput by remember { mutableStateOf("") }

    val displayTitle = when (addressType) {
        "FARM" -> "Farm Address"
        "HOME" -> "Home Address"
        else -> "Farm & Home Address"
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {

            isGpsFetching = true
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    coroutineScope.launch {
                        delay(1200) // Show heartbeat animation briefly
                        isGpsFetching = false
                        if (location != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            latInput = location.latitude.toString()
                            lonInput = location.longitude.toString()
                            val geocoder = Geocoder(context, Locale.getDefault())
                            try {
                                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                                if (!addresses.isNullOrEmpty()) {
                                    val resolvedText = addresses[0].getAddressLine(0) ?: ""
                                    onSaved(resolvedText, location.latitude, location.longitude)
                                } else {
                                    Toast.makeText(context, "Address string not found. Please type Area.", Toast.LENGTH_LONG).show()
                                    isManualTab = true
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Network issue. Please type manually.", Toast.LENGTH_LONG).show()
                                isManualTab = true
                            }
                        } else {
                            Toast.makeText(context, "Failed to get GPS. Try manual entry.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.addOnFailureListener {
                    isGpsFetching = false
                    Toast.makeText(context, "Location unavailable", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                isGpsFetching = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = displayTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = com.simats.growise.ui.theme.TerracottaPrimary
                )
                Spacer(modifier = Modifier.height(20.dp))

                // Custom Tab Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF9EFE9), RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (!isManualTab) com.simats.growise.ui.theme.TerracottaPrimary else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { isManualTab = false }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Auto GPS", fontWeight = FontWeight.Bold, color = if (!isManualTab) Color.White else com.simats.growise.ui.theme.TerracottaPrimary)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isManualTab) com.simats.growise.ui.theme.TerracottaPrimary else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { isManualTab = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Manual Entry", fontWeight = FontWeight.Bold, color = if (isManualTab) Color.White else com.simats.growise.ui.theme.TerracottaPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                if (!isManualTab) {
                    // GPS Tab
                    val infiniteTransition = rememberInfiniteTransition()
                    val pulse by infiniteTransition.animateFloat(
                        initialValue = 1f, targetValue = 1.3f,
                        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse)
                    )

                    Text("Tap the pin to fetch your precise coordinates securely.", textAlign = TextAlign.Center, color = Color.Gray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(32.dp))

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(com.simats.growise.ui.theme.GoldenYellow.copy(alpha = 0.15f), CircleShape)
                            .clickable(enabled = !isGpsFetching) {
                                val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                                if (!isGpsEnabled) {
                                    Toast.makeText(context, "Please turn on your device GPS/Location first", Toast.LENGTH_LONG).show()
                                } else {
                                    locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_location_pin),
                            contentDescription = "Pin Drop",
                            tint = com.simats.growise.ui.theme.TerracottaPrimary,
                            modifier = Modifier
                                .size(48.dp)
                                .scale(if (isGpsFetching) pulse else 1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    if (isGpsFetching) {
                        Text("Locking Satellites...", fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.GoldenYellow)
                    }
                } else {
                    // Manual Tab with Premium Coordinate Fields
                    OutlinedTextField(
                        value = stateInput,
                        onValueChange = { stateInput = it },
                        label = { Text("State") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = com.simats.growise.ui.theme.TerracottaPrimary, unfocusedBorderColor = Color(0xFFF9EFE9))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = areaInput,
                        onValueChange = { areaInput = it },
                        label = { Text("Area / Village") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = com.simats.growise.ui.theme.TerracottaPrimary, unfocusedBorderColor = Color(0xFFF9EFE9))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pincodeInput,
                        onValueChange = { input -> if (input.all { it.isDigit() }) pincodeInput = input },
                        label = { Text("Pincode") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = com.simats.growise.ui.theme.TerracottaPrimary, unfocusedBorderColor = Color(0xFFF9EFE9))
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Coordinate Details (Required)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = latInput,
                            onValueChange = { input -> if (input.all { it.isDigit() || it == '.' || it == '-' }) latInput = input },
                            label = { Text("Latitude") },
                            leadingIcon = { Icon(Icons.Filled.Map, contentDescription = null, tint = com.simats.growise.ui.theme.GoldenYellow, modifier = Modifier.size(18.dp)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = com.simats.growise.ui.theme.TerracottaPrimary, unfocusedBorderColor = Color(0xFFF9EFE9))
                        )
                        OutlinedTextField(
                            value = lonInput,
                            onValueChange = { input -> if (input.all { it.isDigit() || it == '.' || it == '-' }) lonInput = input },
                            label = { Text("Longitude") },
                            leadingIcon = { Icon(Icons.Filled.Map, contentDescription = null, tint = com.simats.growise.ui.theme.GoldenYellow, modifier = Modifier.size(18.dp)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = com.simats.growise.ui.theme.TerracottaPrimary, unfocusedBorderColor = Color(0xFFF9EFE9))
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    val isManualValid = stateInput.isNotBlank() && areaInput.isNotBlank() && pincodeInput.isNotBlank() && latInput.isNotBlank() && lonInput.isNotBlank()

                    Button(
                        onClick = {
                            val fullAddress = "$areaInput, $stateInput - $pincodeInput"
                            val safeLat = latInput.toDoubleOrNull() ?: 0.0
                            val safeLon = lonInput.toDoubleOrNull() ?: 0.0
                            onSaved(fullAddress, safeLat, safeLon)
                        },
                        enabled = isManualValid,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = com.simats.growise.ui.theme.TerracottaPrimary,
                            disabledContainerColor = Color.LightGray
                        )
                    ) {
                        Text("Save Manual Address", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}