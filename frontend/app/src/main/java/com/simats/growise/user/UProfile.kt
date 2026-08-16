package com.simats.growise.user

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simats.growise.R
import com.simats.growise.data.model.AddressModel
import com.simats.growise.data.model.ProfileUpdateRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UProfile(
    userEmail: String,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPref = remember { context.getSharedPreferences("GroWiseSession", Context.MODE_PRIVATE) }
    val gson = Gson()

    var isSaving by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var refreshProfileDataTrigger by remember { mutableStateOf(0) }

    var showAddressDialog by remember { mutableStateOf(false) }
    var editingAddress by remember { mutableStateOf<AddressModel?>(null) }
    var capturedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showPhotoSelectorDialog by remember { mutableStateOf(false) }

    // Instant Loading using SharedPreferences
    val cachedName = sharedPref.getString("U_FULL_NAME", "User") ?: "User"
    val cachedMemberSince = sharedPref.getString("U_MEMBER_SINCE", "--") ?: "--"
    val cachedUsername = sharedPref.getString("U_USERNAME", "") ?: ""
    val cachedProfileImage = sharedPref.getString("U_PROFILE_IMAGE", "") ?: ""
    val cachedPhone = sharedPref.getString("U_PHONE", "") ?: ""
    val cachedRole = sharedPref.getString("U_ROLE", "user") ?: "user"
    val cachedIsNgo = sharedPref.getBoolean("U_IS_NGO", false)
    val cachedDarpan = sharedPref.getString("U_DARPAN", "XX/XXXX/XXXXXXXX") ?: "XX/XXXX/XXXXXXXX"

    val cachedAddressesJson = sharedPref.getString("U_ADDRESSES", "[]") ?: "[]"
    val addressListType = object : TypeToken<List<AddressModel>>() {}.type
    val cachedAddresses: List<AddressModel> = try { gson.fromJson(cachedAddressesJson, addressListType) } catch (e: Exception) { emptyList() }

    var fullName by remember { mutableStateOf(cachedName) }
    var memberSince by remember { mutableStateOf(cachedMemberSince) }
    var customUsername by remember { mutableStateOf(cachedUsername) }
    var profileImageUrl by remember { mutableStateOf(cachedProfileImage) }
    var phoneNumber by remember { mutableStateOf(cachedPhone) }

    var userRole by remember { mutableStateOf(cachedRole) }
    var isNgo by remember { mutableStateOf(cachedIsNgo) }
    var maskedDarpanId by remember { mutableStateOf(cachedDarpan) }

    var addresses by remember { mutableStateOf<List<AddressModel>>(cachedAddresses) }

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
        if (isGranted) cameraLauncher.launch(null)
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

    if (showAddressDialog) {
        DynamicAddressEntryDialog(
            initialAddress = editingAddress,
            onDismiss = { showAddressDialog = false; editingAddress = null },
            onSaved = { id, title, fullAddress, lat, lon, isDefault ->
                // Ensure default if it is the only address being saved
                val forceDefault = isDefault || addresses.isEmpty() || (addresses.size == 1 && id != null)

                val newAddress = AddressModel(
                    id = id ?: UUID.randomUUID().toString(),
                    title = title,
                    fullAddress = fullAddress,
                    lat = lat,
                    lon = lon,
                    isDefault = forceDefault
                )

                val updatedList = addresses.toMutableList()
                val existingIndex = updatedList.indexOfFirst { it.id == newAddress.id }

                if (existingIndex != -1) {
                    updatedList[existingIndex] = newAddress
                } else {
                    updatedList.add(newAddress)
                }

                addresses = if (forceDefault) {
                    updatedList.map { if (it.id == newAddress.id) it else it.copy(isDefault = false) }
                } else {
                    updatedList
                }

                sharedPref.edit().putString("U_ADDRESSES", gson.toJson(addresses)).apply()
                showAddressDialog = false
                editingAddress = null
            }
        )
    }

    LaunchedEffect(refreshProfileDataTrigger) {
        try {
            val response = com.simats.growise.data.network.RetrofitClient.apiService.retrieveProfileFields(userEmail)
            if (response.isSuccessful) {
                response.body()?.let { data ->
                    fullName = data.name?.takeIf { it.isNotEmpty() } ?: "User"
                    memberSince = data.member_since?.takeIf { it.isNotEmpty() } ?: "--"
                    customUsername = data.username ?: ""
                    profileImageUrl = data.profile_image_url ?: ""
                    phoneNumber = data.phone ?: ""
                    userRole = data.role ?: "user"
                    isNgo = data.isNgo ?: false

                    if (isNgo) {
                        val rawDarpan = data.darpan_masked ?: ""
                        maskedDarpanId = if (rawDarpan.length >= 4) "XX/XXXX/XXX${rawDarpan.takeLast(4)}" else "XX/XXXX/XXXXXXXX"
                    }

                    addresses = data.addresses ?: emptyList()
                    if(addresses.size == 1 && !addresses.first().isDefault) {
                        addresses = listOf(addresses.first().copy(isDefault = true))
                    }

                    sharedPref.edit().apply {
                        putString("U_FULL_NAME", fullName)
                        putString("U_MEMBER_SINCE", memberSince)
                        putString("U_USERNAME", customUsername)
                        putString("U_PROFILE_IMAGE", profileImageUrl)
                        putString("U_PHONE", phoneNumber)
                        putString("U_ROLE", userRole)
                        putBoolean("U_IS_NGO", isNgo)
                        putString("U_DARPAN", maskedDarpanId)
                        putString("U_ADDRESSES", gson.toJson(addresses))
                    }.apply()
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
                        modifier = Modifier.size(72.dp).rotate(rotation)
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
                    initialValue = 1f, targetValue = 1.08f,
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
                                Text(
                                    text = if (isNgo) "Verified NGO" else "GroWise Verified",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                            // D. Profile Container
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
                                Text(
                                    text = fullName ?: "User",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = com.simats.growise.ui.theme.TextDark,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (!customUsername.isNullOrEmpty()) {
                                    Text(text = "@$customUsername", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.TerracottaPrimary)
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
                                                Text("Role", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted)
                                                Text((userRole ?: "user").uppercase(), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.TextDark)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                if (isNgo) {
                                                    Text("Darpan ID", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted)
                                                    Text(maskedDarpanId ?: "XX/XXXX/XXXXXXXX", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.TextDark)
                                                } else {
                                                    Text("Email Address", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted)
                                                    Text(userEmail ?: "Unknown", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.TextDark)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))
                                        HorizontalDivider(color = Color(0xFFE8DFD8), thickness = 1.dp)
                                        Spacer(modifier = Modifier.height(16.dp))

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column {
                                                Text("Phone Number", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted)
                                                Text(if (phoneNumber.isNullOrEmpty()) "Not Set" else phoneNumber!!, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.TextDark)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Member Since", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted)
                                                Text(memberSince ?: "--", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.TextDark)
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Saved Addresses Preview
                    Text("Saved Addresses", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TerracottaPrimary)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (addresses.isEmpty()) {
                        Text("No addresses saved yet.", color = Color.Gray, fontSize = 14.sp)
                    } else {
                        addresses.forEach { address ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = if (address.isDefault) BorderStroke(2.dp, com.simats.growise.ui.theme.GoldenYellow) else null
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = com.simats.growise.ui.theme.TerracottaPrimary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(address.title ?: "Address", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = com.simats.growise.ui.theme.TextDark)
                                        if (address.isDefault) {
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text("Default", color = com.simats.growise.ui.theme.GoldenYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(address.fullAddress ?: "Location not updated", color = Color.DarkGray, fontSize = 14.sp)
                                }
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
                        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier.size(120.dp).clip(CircleShape).background(Color.White).padding(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize().clip(CircleShape).background(com.simats.growise.ui.theme.LogoBoxColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (capturedImageBitmap != null) {
                                        Image(bitmap = capturedImageBitmap!!.asImageBitmap(), contentDescription = "Captured", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    } else if (profileImageUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data("${com.simats.growise.data.network.RetrofitClient.BASE_URL}${profileImageUrl.removePrefix("/")}")
                                                .crossfade(true).build(),
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
                                    .size(36.dp).clip(CircleShape).background(com.simats.growise.ui.theme.TerracottaPrimary)
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
                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Email Address", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted)
                            OutlinedTextField(
                                value = userEmail,
                                onValueChange = {}, readOnly = true,
                                trailingIcon = { Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = Color.LightGray) },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(disabledContainerColor = Color(0xFFFFFBF8), disabledBorderColor = Color(0xFFF9EFE9), disabledTextColor = com.simats.growise.ui.theme.TextDark),
                                enabled = false
                            )

                            if (isNgo) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Darpan ID", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted)
                                OutlinedTextField(
                                    value = maskedDarpanId,
                                    onValueChange = {}, readOnly = true,
                                    trailingIcon = { Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = Color.LightGray) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(disabledContainerColor = Color(0xFFFFFBF8), disabledBorderColor = Color(0xFFF9EFE9), disabledTextColor = com.simats.growise.ui.theme.TextDark),
                                    enabled = false
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Dynamic Address Management Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Address Management", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TerracottaPrimary)
                            Spacer(modifier = Modifier.height(16.dp))

                            addresses.forEach { address ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF9EFE9)),
                                    border = if (address.isDefault) BorderStroke(1.dp, com.simats.growise.ui.theme.GoldenYellow) else null
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(address.title ?: "Address", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = com.simats.growise.ui.theme.TextDark)
                                                if (address.isDefault) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("(Default)", color = com.simats.growise.ui.theme.GoldenYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Text(address.fullAddress ?: "Location not updated", fontSize = 13.sp, color = Color.DarkGray)
                                        }
                                        Row {
                                            IconButton(onClick = { editingAddress = address; showAddressDialog = true }) {
                                                Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = com.simats.growise.ui.theme.TerracottaPrimary)
                                            }
                                            IconButton(onClick = { addresses = addresses.filter { it.id != address.id } }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = com.simats.growise.ui.theme.ErrorRed)
                                            }
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { editingAddress = null; showAddressDialog = true },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = com.simats.growise.ui.theme.GoldenYellow)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add New Address", color = Color.White, fontWeight = FontWeight.Bold)
                            }
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
                                        val addressesJsonStr = gson.toJson(addresses)

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

                                            val response = com.simats.growise.data.network.RetrofitClient.apiService.updateUserProfileWithImage(
                                                email = createPart(userEmail),
                                                username = createPart(customUsername),
                                                phone = createPart(phoneNumber),
                                                addressesJson = createPart(addressesJsonStr),
                                                profile_image = body
                                            )

                                            if (response.isSuccessful) {
                                                Toast.makeText(context, "Profile & Photo updated", Toast.LENGTH_SHORT).show()
                                                capturedImageBitmap = null
                                                isEditMode = false
                                                refreshProfileDataTrigger++
                                            }
                                        } else {
                                            val updatePayload = ProfileUpdateRequest(
                                                email = userEmail,
                                                username = customUsername,
                                                phone = phoneNumber,
                                                addresses = addresses
                                            )
                                            val response = com.simats.growise.data.network.RetrofitClient.apiService.saveUserProfileFields(updatePayload)
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
// DYNAMIC DIALOG COMPONENT FOR INFINITE ADDRESS ENTRY (CHIP UI UPDATE)
// ======================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicAddressEntryDialog(
    initialAddress: AddressModel? = null,
    onDismiss: () -> Unit,
    onSaved: (id: String?, title: String, address: String, lat: Double, lon: Double, isDefault: Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val haptic = LocalHapticFeedback.current

    var isManualTab by remember { mutableStateOf(initialAddress != null) }
    var isGpsFetching by remember { mutableStateOf(false) }

    // Address Label Chip States
    val initialChip = if (initialAddress?.title in listOf("Home", "Work")) initialAddress!!.title else if (initialAddress != null) "Custom" else "Home"
    var selectedChip by remember { mutableStateOf(initialChip) }
    var customLabel by remember { mutableStateOf(if (initialChip == "Custom") initialAddress?.title ?: "" else "") }

    var setAsDefault by remember { mutableStateOf(initialAddress?.isDefault ?: false) }

    var stateInput by remember { mutableStateOf("") }
    var areaInput by remember { mutableStateOf(initialAddress?.fullAddress ?: "") }
    var pincodeInput by remember { mutableStateOf("") }
    var latInput by remember { mutableStateOf(initialAddress?.lat?.toString() ?: "") }
    var lonInput by remember { mutableStateOf(initialAddress?.lon?.toString() ?: "") }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            isGpsFetching = true
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    coroutineScope.launch {
                        delay(1200)
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

                                    val finalTitle = if (selectedChip == "Custom") {
                                        customLabel.ifEmpty { "My Location" }
                                    } else {
                                        selectedChip
                                    }
                                    onSaved(initialAddress?.id, finalTitle, resolvedText, location.latitude, location.longitude, setAsDefault)
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
            modifier = Modifier.fillMaxWidth().padding(24.dp).wrapContentHeight()
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = if(initialAddress != null) "Edit Address" else "Add New Address", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = com.simats.growise.ui.theme.TerracottaPrimary)
                Spacer(modifier = Modifier.height(20.dp))

                // Premium Chip Selection for Address Label
                Text("Select Address Label", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Home", "Work", "Custom").forEach { chip ->
                        val isSelected = selectedChip == chip
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) com.simats.growise.ui.theme.TerracottaPrimary else Color(0xFFF9EFE9))
                                .clickable { selectedChip = chip }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(chip, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isSelected) Color.White else com.simats.growise.ui.theme.TextDark)
                        }
                    }
                }

                if (selectedChip == "Custom") {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customLabel,
                        onValueChange = { customLabel = it },
                        label = { Text("Enter Custom Label (e.g. Farm, Shop)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = com.simats.growise.ui.theme.TerracottaPrimary)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Checkbox(
                        checked = setAsDefault,
                        onCheckedChange = { setAsDefault = it },
                        colors = CheckboxDefaults.colors(checkedColor = com.simats.growise.ui.theme.TerracottaPrimary)
                    )
                    Text("Set as Default Address", fontSize = 14.sp, color = com.simats.growise.ui.theme.TextDark, fontWeight = FontWeight.Medium)
                }

                Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFF9EFE9), RoundedCornerShape(12.dp)).padding(4.dp)) {
                    Box(
                        modifier = Modifier.weight(1f)
                            .background(if (!isManualTab) com.simats.growise.ui.theme.TerracottaPrimary else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { isManualTab = false }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Auto GPS", fontWeight = FontWeight.Bold, color = if (!isManualTab) Color.White else com.simats.growise.ui.theme.TerracottaPrimary)
                    }
                    Box(
                        modifier = Modifier.weight(1f)
                            .background(if (isManualTab) com.simats.growise.ui.theme.TerracottaPrimary else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { isManualTab = true }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Manual Entry", fontWeight = FontWeight.Bold, color = if (isManualTab) Color.White else com.simats.growise.ui.theme.TerracottaPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                if (!isManualTab) {
                    val infiniteTransition = rememberInfiniteTransition()
                    val pulse by infiniteTransition.animateFloat(
                        initialValue = 1f, targetValue = 1.3f,
                        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse)
                    )

                    Text("Tap the pin to fetch your precise coordinates securely.", textAlign = TextAlign.Center, color = Color.Gray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(32.dp))

                    Box(
                        modifier = Modifier.size(100.dp).background(com.simats.growise.ui.theme.GoldenYellow.copy(alpha = 0.15f), CircleShape)
                            .clickable(enabled = !isGpsFetching) {
                                if (selectedChip == "Custom" && customLabel.isEmpty()) {
                                    Toast.makeText(context, "Please enter a custom label first", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }
                                val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                                if (!isGpsEnabled) {
                                    Toast.makeText(context, "Please turn on your device GPS/Location first", Toast.LENGTH_LONG).show()
                                } else {
                                    locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_location_pin), contentDescription = "Pin Drop", tint = com.simats.growise.ui.theme.TerracottaPrimary, modifier = Modifier.size(48.dp).scale(if (isGpsFetching) pulse else 1f))
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    if (isGpsFetching) Text("Locking Satellites...", fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.GoldenYellow)
                } else {
                    OutlinedTextField(value = areaInput, onValueChange = { areaInput = it }, label = { Text(if (initialAddress != null) "Full Address" else "Area / Village") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

                    if (initialAddress == null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(value = stateInput, onValueChange = { stateInput = it }, label = { Text("State") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(value = pincodeInput, onValueChange = { input -> if (input.all { it.isDigit() }) pincodeInput = input }, label = { Text("Pincode") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Coordinate Details (Required)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = com.simats.growise.ui.theme.TextMuted, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = latInput, onValueChange = { input -> if (input.all { it.isDigit() || it == '.' || it == '-' }) latInput = input }, label = { Text("Latitude") }, leadingIcon = { Icon(Icons.Filled.Map, contentDescription = null, tint = com.simats.growise.ui.theme.GoldenYellow, modifier = Modifier.size(18.dp)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                        OutlinedTextField(value = lonInput, onValueChange = { input -> if (input.all { it.isDigit() || it == '.' || it == '-' }) lonInput = input }, label = { Text("Longitude") }, leadingIcon = { Icon(Icons.Filled.Map, contentDescription = null, tint = com.simats.growise.ui.theme.GoldenYellow, modifier = Modifier.size(18.dp)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    val isManualValid = (selectedChip != "Custom" || customLabel.isNotBlank()) && areaInput.isNotBlank() && latInput.isNotBlank() && lonInput.isNotBlank()

                    Button(
                        onClick = {
                            val finalTitle = if (selectedChip == "Custom") customLabel else selectedChip
                            val fullAddress = if (stateInput.isNotBlank() || pincodeInput.isNotBlank()) "$areaInput, $stateInput - $pincodeInput" else areaInput
                            val safeLat = latInput.toDoubleOrNull() ?: 0.0
                            val safeLon = lonInput.toDoubleOrNull() ?: 0.0
                            onSaved(initialAddress?.id, finalTitle, fullAddress, safeLat, safeLon, setAsDefault)
                        },
                        enabled = isManualValid,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = com.simats.growise.ui.theme.TerracottaPrimary, disabledContainerColor = Color.LightGray)
                    ) {
                        Text(if (initialAddress != null) "Update Address" else "Save Manual Address", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}