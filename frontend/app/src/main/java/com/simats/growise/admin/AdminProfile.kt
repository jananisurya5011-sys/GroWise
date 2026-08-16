package com.simats.growise.admin

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.simats.growise.R
import com.simats.growise.data.model.ProfileUpdateRequest
import com.simats.growise.data.network.RetrofitClient
import com.simats.growise.ui.theme.GoldenYellow
import com.simats.growise.ui.theme.LogoBoxColor
import com.simats.growise.ui.theme.PeachBackground
import com.simats.growise.ui.theme.TerracottaPrimary
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import java.io.FileOutputStream

@Composable
fun AdminProfile(userEmail: String) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPref = remember { context.getSharedPreferences("GroWiseSession", Context.MODE_PRIVATE) }

    var isEditMode by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var refreshProfileDataTrigger by remember { mutableStateOf(0) }
    var showPhotoSelectorDialog by remember { mutableStateOf(false) }

    var adminName by remember { mutableStateOf(sharedPref.getString("U_FULL_NAME", "Admin") ?: "Admin") }
    var phoneNumber by remember { mutableStateOf(sharedPref.getString("U_PHONE", "") ?: "") }
    var profileImageUrl by remember { mutableStateOf(sharedPref.getString("U_PROFILE_IMAGE", "") ?: "") }

    var capturedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) { capturedImageBitmap = bitmap; Toast.makeText(context, "Photo captured", Toast.LENGTH_SHORT).show() }
        showPhotoSelectorDialog = false
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) { cameraLauncher.launch(null) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
                capturedImageBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                Toast.makeText(context, "Photo selected", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
        showPhotoSelectorDialog = false
    }

    LaunchedEffect(refreshProfileDataTrigger) {
        try {
            val response = RetrofitClient.apiService.retrieveProfileFields(userEmail)
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                adminName = data.name.ifEmpty { "Admin" }
                phoneNumber = data.phone ?: ""
                profileImageUrl = data.profile_image_url ?: ""

                sharedPref.edit().apply {
                    putString("U_FULL_NAME", adminName)
                    putString("U_PHONE", phoneNumber)
                    putString("U_PROFILE_IMAGE", profileImageUrl)
                }.apply()
            }
        } catch (e: Exception) {}
    }

    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Restart)
    )

    if (showPhotoSelectorDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSelectorDialog = false },
            title = { Text("Update Profile Picture", fontWeight = FontWeight.Bold, color = TerracottaPrimary) },
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

    if (isSaving) {
        Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(PeachBackground.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painter = painterResource(id = R.drawable.ic_agri_loading), contentDescription = "Loading", tint = TerracottaPrimary, modifier = Modifier.size(72.dp).rotate(rotation))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Saving Secure Data into the Vault...", color = TerracottaPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }

    // MAIN VIEW MODE
    Column(modifier = Modifier.fillMaxSize().background(PeachBackground).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp)) {
        Text("Admin Identity", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary)
        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth().shadow(16.dp, shape = RoundedCornerShape(32.dp)), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp).background(Brush.verticalGradient(colors = listOf(TerracottaPrimary, GoldenYellow))))
                    Row(modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Verified", tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Super Admin", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
                    }
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(90.dp))

                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 1f, targetValue = 1.08f,
                            animationSpec = infiniteRepeatable(animation = tween(1000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse)
                        )
                        Box(contentAlignment = Alignment.Center) {
                            Box(modifier = Modifier.size(130.dp).scale(pulseScale).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
                            Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(Color.White).padding(6.dp)) {
                                Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(LogoBoxColor), contentAlignment = Alignment.Center) {
                                    if (profileImageUrl.isNotEmpty()) {
                                        AsyncImage(model = "${RetrofitClient.BASE_URL}${profileImageUrl.removePrefix("/")}", contentDescription = "Profile Photo", placeholder = painterResource(id = R.drawable.app_logo), error = painterResource(id = R.drawable.app_logo), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    } else {
                                        Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = "Default", modifier = Modifier.size(64.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(adminName, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.Black)
                        Spacer(modifier = Modifier.height(24.dp))

                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF7F2))) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column { Text("Role", fontSize = 11.sp, color = Color.Gray); Text("ADMIN", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
                                    Column(horizontalAlignment = Alignment.End) { Text("Email", fontSize = 11.sp, color = Color.Gray); Text(userEmail, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Divider(color = Color.LightGray)
                                Spacer(modifier = Modifier.height(16.dp))
                                Column { Text("Phone Number", fontSize = 11.sp, color = Color.Gray); Text(phoneNumber.ifEmpty { "Not Set" }, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { isEditMode = true }, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Edit Contact Info", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    // FULL SCREEN DIALOG EDIT MODE
    if (isEditMode) {
        Dialog(onDismissRequest = { isEditMode = false; capturedImageBitmap = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(modifier = Modifier.fillMaxSize().background(PeachBackground).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp)) {
                Text("Update Configuration", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary)
                Spacer(modifier = Modifier.height(24.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.BottomEnd) {
                        Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(Color.White).padding(6.dp)) {
                            Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(LogoBoxColor), contentAlignment = Alignment.Center) {
                                if (capturedImageBitmap != null) {
                                    Image(bitmap = capturedImageBitmap!!.asImageBitmap(), contentDescription = "Captured", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else if (profileImageUrl.isNotEmpty()) {
                                    AsyncImage(model = "${RetrofitClient.BASE_URL}${profileImageUrl.removePrefix("/")}", contentDescription = "Profile Photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = "Default", modifier = Modifier.size(64.dp))
                                }
                            }
                        }
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(TerracottaPrimary).clickable { showPhotoSelectorDialog = true }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = "Edit Photo", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Account Security (Locked)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        OutlinedTextField(value = adminName, onValueChange = {}, readOnly = true, trailingIcon = { Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = Color.LightGray) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(disabledContainerColor = Color.White, disabledBorderColor = Color(0xFFF9EFE9)), enabled = false)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(value = userEmail, onValueChange = {}, readOnly = true, trailingIcon = { Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = Color.LightGray) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(disabledContainerColor = Color.White, disabledBorderColor = Color(0xFFF9EFE9)), enabled = false)

                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Operational Phone", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TerracottaPrimary)
                        OutlinedTextField(value = phoneNumber, onValueChange = { phoneNumber = it }, placeholder = { Text("Enter admin phone") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth().padding(top = 4.dp), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TerracottaPrimary, unfocusedBorderColor = Color(0xFFF9EFE9)))
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { isEditMode = false; capturedImageBitmap = null }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White), border = BorderStroke(1.dp, TerracottaPrimary)) { Text("Cancel", color = TerracottaPrimary, fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = {
                            isSaving = true
                            coroutineScope.launch {
                                try {
                                    if (capturedImageBitmap != null) {
                                        val file = File(context.cacheDir, "admin_profile.jpg")
                                        val fos = FileOutputStream(file)
                                        capturedImageBitmap!!.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                                        fos.flush(); fos.close()

                                        val reqFile = RequestBody.create("image/jpeg".toMediaTypeOrNull(), file)
                                        val body = MultipartBody.Part.createFormData("profile_image", file.name, reqFile)
                                        fun createPart(text: String) = RequestBody.create("text/plain".toMediaTypeOrNull(), text)

                                        RetrofitClient.apiService.updateUserProfileWithImage(
                                            email = createPart(userEmail), username = createPart(adminName), phone = createPart(phoneNumber), addressesJson = createPart("[]"), profile_image = body
                                        )
                                    } else {
                                        RetrofitClient.apiService.saveUserProfileFields(ProfileUpdateRequest(email = userEmail, username = adminName, phone = phoneNumber))
                                    }
                                    refreshProfileDataTrigger++
                                    isEditMode = false
                                } catch (e: Exception) {}
                                isSaving = false
                            }
                        },
                        modifier = Modifier.weight(1.5f).height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)
                    ) { Text("Save Secure Vault", color = Color.White, fontWeight = FontWeight.Bold) }
                }
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}