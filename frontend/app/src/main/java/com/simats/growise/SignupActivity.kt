package com.simats.growise

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.growise.data.model.SignupRequest
import com.simats.growise.data.network.RetrofitClient
import com.simats.growise.ui.theme.GroWiseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class SignupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GroWiseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SignupScreenDesign(
                        onNavigateToLogin = {
                            finish()
                            @Suppress("DEPRECATION")
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SignupScreenDesign(onNavigateToLogin: () -> Unit) {
    val context = LocalContext.current as Activity

    BackHandler {
        context.finish()
        @Suppress("DEPRECATION")
        context.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    var selectedRole by rememberSaveable { mutableIntStateOf(0) } // 0=Farmer, 1=User, 2=Driver
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPassVisible by rememberSaveable { mutableStateOf(false) }

    // Farmer / Driver Specific
    var aadhaarNumber by rememberSaveable { mutableStateOf("") }
    var isAadhaarVerified by rememberSaveable { mutableStateOf(false) }

    // User/NGO specific
    var isNgo by rememberSaveable { mutableStateOf(false) }
    var darpanId by rememberSaveable { mutableStateOf("") }
    var isDarpanVerified by rememberSaveable { mutableStateOf(false) }

    // Driver Specific
    var vehicleType by rememberSaveable { mutableStateOf("") }
    var licenseUri by remember { mutableStateOf<Uri?>(null) }
    var rcBookUri by remember { mutableStateOf<Uri?>(null) }

    val licenseLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> licenseUri = uri }
    val rcLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> rcBookUri = uri }

    var errorMessage by rememberSaveable { mutableStateOf("") }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(errorMessage) {
        if (errorMessage.isNotEmpty()) {
            shakeOffset.animateTo(15f, tween(50))
            shakeOffset.animateTo(-15f, tween(50))
            shakeOffset.animateTo(15f, tween(50))
            shakeOffset.animateTo(-15f, tween(50))
            shakeOffset.animateTo(0f, tween(50))
        }
    }

    val nameRegex = "^[a-zA-Z ]+$".toRegex()
    val emailRegex = "^[a-zA-Z0-9._%+-]+@(gmail\\.com|mail\\.com)$".toRegex()
    val phoneRegex = "^\\d{10}$".toRegex()
    val passwordRegex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{6,8}$".toRegex()
    val darpanRegex = "^[A-Z]{2}/\\d{4}/\\d{7}$".toRegex()

    val boxedTextFieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = Color.White,
        focusedContainerColor = Color.White,
        unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        errorBorderColor = com.simats.growise.ui.theme.ErrorRed
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = "Logo", modifier = Modifier.size(80.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("Create Account", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))

        // 3-Way Floating Glassmorphism Toggle
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.5f)).padding(4.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                listOf("Farmer", "User", "Driver").forEachIndexed { index, title ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selectedRole == index) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { selectedRole = index; errorMessage = "" }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(title, color = if (selectedRole == index) Color.White else Color(0xFF666666), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val isError = errorMessage.isNotEmpty()
        val fieldModifier = Modifier.fillMaxWidth().offset(x = shakeOffset.value.dp)

        OutlinedTextField(
            value = name, onValueChange = { name = it; errorMessage = "" },
            placeholder = { Text("Full Name", color = Color(0xFFAAAAAA)) }, modifier = fieldModifier,
            isError = isError, singleLine = true, shape = RoundedCornerShape(16.dp), colors = boxedTextFieldColors
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it; errorMessage = "" },
            placeholder = { Text("Email (@gmail.com)", color = Color(0xFFAAAAAA)) }, modifier = fieldModifier,
            isError = isError, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(16.dp), colors = boxedTextFieldColors,
            trailingIcon = { Text("@", color = Color(0xFFAAAAAA), fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp)) }
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = phone, onValueChange = { phone = it; errorMessage = "" },
            placeholder = { Text("Phone Number (10 Digits)", color = Color(0xFFAAAAAA)) }, modifier = fieldModifier,
            isError = isError, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(16.dp), colors = boxedTextFieldColors,
            leadingIcon = { Text("+91", color = Color(0xFFAAAAAA), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp)) }
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (selectedRole == 0 || selectedRole == 2) { // Farmer OR Driver
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = aadhaarNumber, onValueChange = { aadhaarNumber = it; isAadhaarVerified = false; errorMessage = "" },
                    placeholder = { Text("Aadhaar Number", color = Color(0xFFAAAAAA)) },
                    modifier = Modifier.weight(1f).offset(x = shakeOffset.value.dp),
                    isError = isError, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp), colors = boxedTextFieldColors
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (aadhaarNumber.length == 12 && aadhaarNumber.all { it.isDigit() }) { isAadhaarVerified = true; errorMessage = "" }
                        else { isAadhaarVerified = false; errorMessage = "Aadhaar must be exactly 12 digits." }
                    },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isAadhaarVerified) com.simats.growise.ui.theme.GoldenYellow else MaterialTheme.colorScheme.primary)
                ) { Text(if (isAadhaarVerified) "Verified" else "Verify", color = Color.White) }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (selectedRole == 1) { // User Only
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = isNgo, onCheckedChange = { isNgo = it; errorMessage = "" }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
                Text("Register as recognized NGO", color = Color(0xFF555555))
            }
            if (isNgo) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = darpanId, onValueChange = { darpanId = it; isDarpanVerified = false; errorMessage = "" },
                        placeholder = { Text("Enter your Darpan ID", color = Color(0xFFAAAAAA)) }, modifier = Modifier.weight(1f).offset(x = shakeOffset.value.dp),
                        isError = isError, singleLine = true, shape = RoundedCornerShape(16.dp), colors = boxedTextFieldColors
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (darpanId.matches(darpanRegex)) { isDarpanVerified = true; errorMessage = "" }
                            else { isDarpanVerified = false; errorMessage = "Invalid Darpan format." }
                        },
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isDarpanVerified) com.simats.growise.ui.theme.GoldenYellow else MaterialTheme.colorScheme.primary)
                    ) { Text(if (isDarpanVerified) "Verified" else "Verify", color = Color.White) }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        if (selectedRole == 2) { // Driver Only
            var expanded by remember { mutableStateOf(false) }
            val vehicleOptions = listOf("Bike", "Auto", "Mini-Truck", "Heavy Lorry")

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = vehicleType,
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("Select Vehicle Type", color = Color(0xFFAAAAAA)) },
                    modifier = fieldModifier,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = boxedTextFieldColors,
                    trailingIcon = {
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Dropdown Menu", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
                // Transparent overlay to catch clicks smoothly across the entire text field
                Box(modifier = Modifier.matchParentSize().clickable { expanded = true; errorMessage = "" })

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.85f).background(Color.White)
                ) {
                    vehicleOptions.forEach { option ->
                        val description = when(option) {
                            "Bike" -> " (Bikes <= 20kg)"
                            "Auto" -> " (Autos <= 500kg)"
                            "Mini-Truck" -> " (<= 1500kg)"
                            else -> " (> 1500kg)"
                        }
                        DropdownMenuItem(
                            text = {
                                Row {
                                    Text(option, color = Color.Black, fontWeight = FontWeight.Bold)
                                    Text(description, color = Color.Gray)
                                }
                            },
                            onClick = {
                                vehicleType = option
                                errorMessage = ""
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.weight(1f).height(100.dp).border(BorderStroke(2.dp, if(licenseUri != null) com.simats.growise.ui.theme.GoldenYellow else Color.Gray), RoundedCornerShape(16.dp)).clickable { licenseLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Badge, contentDescription = "License", tint = if(licenseUri != null) com.simats.growise.ui.theme.GoldenYellow else Color.Gray)
                        Text(if (licenseUri != null) "License Added" else "Upload License", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top=4.dp))
                    }
                }
                Box(
                    modifier = Modifier.weight(1f).height(100.dp).border(BorderStroke(2.dp, if(rcBookUri != null) com.simats.growise.ui.theme.GoldenYellow else Color.Gray), RoundedCornerShape(16.dp)).clickable { rcLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.DirectionsCar, contentDescription = "RC", tint = if(rcBookUri != null) com.simats.growise.ui.theme.GoldenYellow else Color.Gray)
                        Text(if (rcBookUri != null) "RC Book Added" else "Upload RC Book", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top=4.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = password, onValueChange = { password = it; errorMessage = "" },
            placeholder = { Text("Password", color = Color(0xFFAAAAAA)) },
            visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = fieldModifier, isError = isError, singleLine = true, shape = RoundedCornerShape(16.dp), colors = boxedTextFieldColors,
            trailingIcon = { IconButton(onClick = { passVisible = !passVisible }) { Icon(if (passVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = null, tint = Color(0xFFAAAAAA)) } }
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmPassword, onValueChange = { confirmPassword = it; errorMessage = "" },
            placeholder = { Text("Confirm Password", color = Color(0xFFAAAAAA)) },
            visualTransformation = if (confirmPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = fieldModifier, isError = isError, singleLine = true, shape = RoundedCornerShape(16.dp), colors = boxedTextFieldColors,
            trailingIcon = { IconButton(onClick = { confirmPassVisible = !confirmPassVisible }) { Icon(if (confirmPassVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = null, tint = Color(0xFFAAAAAA)) } }
        )

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = errorMessage, color = com.simats.growise.ui.theme.ErrorRed, fontSize = 13.sp, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            Button(
                onClick = {
                    if (!name.matches(nameRegex)) errorMessage = "Only letters allowed. No spaces or numbers."
                    else if (!email.matches(emailRegex)) errorMessage = "Must use @gmail.com or @mail.com."
                    else if (!phone.matches(phoneRegex)) errorMessage = "Phone number must be exactly 10 digits."
                    else if (!password.matches(passwordRegex)) errorMessage = "Requires 1 uppercase, 1 lowercase, 1 digit, 1 special."
                    else if (password != confirmPassword) errorMessage = "Passwords do not match."
                    else if ((selectedRole == 0 || selectedRole == 2) && !isAadhaarVerified) errorMessage = "You must verify your Aadhaar number."
                    else if (selectedRole == 1 && isNgo && !isDarpanVerified) errorMessage = "You must verify your Darpan ID."
                    else if (selectedRole == 2 && vehicleType.isEmpty()) errorMessage = "Vehicle Type is required."
                    else if (selectedRole == 2 && (licenseUri == null || rcBookUri == null)) errorMessage = "License and RC Book images are required."
                    else {
                        isLoading = true
                        errorMessage = ""

                        val roleStr = when(selectedRole) { 0 -> "farmer"; 1 -> "user"; else -> "driver" }
                        val finalAadhaar = if (selectedRole == 0 || selectedRole == 2) aadhaarNumber else null
                        val finalDarpan = if (selectedRole == 1 && isNgo) darpanId else null

                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                var finalLicenseUrl = ""
                                var finalRcUrl = ""

                                // Convert Local Images to Base64 to send to Flask Backend
                                if (selectedRole == 2) {
                                    finalLicenseUrl = licenseUri?.let { uri ->
                                        context.contentResolver.openInputStream(uri)?.use {
                                            android.util.Base64.encodeToString(it.readBytes(), android.util.Base64.NO_WRAP)
                                        }
                                    } ?: ""

                                    finalRcUrl = rcBookUri?.let { uri ->
                                        context.contentResolver.openInputStream(uri)?.use {
                                            android.util.Base64.encodeToString(it.readBytes(), android.util.Base64.NO_WRAP)
                                        }
                                    } ?: ""
                                }

                                val request = SignupRequest(
                                    name = name, email = email, phone = phone, password = password, role = roleStr,
                                    aadhaarNumber = finalAadhaar, isNgo = isNgo, darpanId = finalDarpan,
                                    vehicleType = if(selectedRole == 2) vehicleType else null,
                                    licenseUrl = if(selectedRole == 2) finalLicenseUrl else null,
                                    rcBookUrl = if(selectedRole == 2) finalRcUrl else null
                                )

                                val response = RetrofitClient.apiService.signup(request)
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    if (response.isSuccessful) {
                                        Toast.makeText(context, if(selectedRole == 2) "Driver Request Sent for Verification!" else "Registration Successful", Toast.LENGTH_LONG).show()
                                        onNavigateToLogin()
                                    } else { errorMessage = "Signup Failed: Account with this email/phone may already exist." }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    // Displays the exact Firebase/Server error on the screen instead of a generic message
                                    errorMessage = "Upload Error: ${e.localizedMessage}"
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Sign Up", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.clickable { onNavigateToLogin() }) {
            Text(text = "Already have an account? ", color = Color(0xFF888888), fontSize = 14.sp)
            Text(text = "Sign In", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}