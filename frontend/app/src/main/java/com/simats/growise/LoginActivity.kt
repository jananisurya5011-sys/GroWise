package com.simats.growise

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.growise.data.model.LoginRequest
import com.simats.growise.data.network.RetrofitClient
import com.simats.growise.ui.theme.GoldenYellow
import com.simats.growise.ui.theme.GroWiseTheme
import com.simats.growise.ui.theme.PeachBackground
import com.simats.growise.ui.theme.TerracottaPrimary
import com.simats.growise.ui.theme.TextMuted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GroWiseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LoginScreenContent(
                        onNavigateToSignup = {
                            startActivity(Intent(this@LoginActivity, SignupActivity::class.java))
                            @Suppress("DEPRECATION")
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        },
                        onLoginSuccess = { role, userEmail ->
                            val sharedPref = getSharedPreferences("GroWiseSession", android.content.Context.MODE_PRIVATE)
                            with(sharedPref.edit()) {
                                putString("ROLE", role)
                                putString("USER_EMAIL", userEmail)
                                apply()
                            }
                            val intent = Intent(this@LoginActivity, HomeActivity::class.java).apply { putExtra("USER_ROLE", role) }
                            startActivity(intent)
                            @Suppress("DEPRECATION")
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LoginScreenContent(
    onNavigateToSignup: () -> Unit,
    onLoginSuccess: (String, String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf("") }
    var isLoading by rememberSaveable { mutableStateOf(false) }

    // Status Screens
    var showPendingScreen by rememberSaveable { mutableStateOf(false) }

    var showRejectedScreen by rememberSaveable { mutableStateOf(false) }
    var rejectionReason by rememberSaveable { mutableStateOf("") }

    var showApprovedScreen by rememberSaveable { mutableStateOf(false) }
    var approvedDriverId by rememberSaveable { mutableStateOf("") }

    val shakeOffset = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(animation = tween(500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse)
    )

    LaunchedEffect(errorMessage) {
        if (errorMessage.isNotEmpty()) {
            shakeOffset.animateTo(15f, tween(50))
            shakeOffset.animateTo(-15f, tween(50))
            shakeOffset.animateTo(15f, tween(50))
            shakeOffset.animateTo(-15f, tween(50))
            shakeOffset.animateTo(0f, tween(50))
        }
    }

    val passwordRegex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{6,8}$".toRegex()
    val emailRegex = "^[a-zA-Z0-9._%+-]+@(gmail\\.com|mail\\.com)$".toRegex()
    val phoneRegex = "^\\d{10}$".toRegex()

    val boxedTextFieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = Color.White, focusedContainerColor = Color.White,
        unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        focusedBorderColor = MaterialTheme.colorScheme.primary, errorBorderColor = com.simats.growise.ui.theme.ErrorRed
    )

    if (showPendingScreen) {
        Column(
            modifier = Modifier.fillMaxSize().background(PeachBackground),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = "Logo", modifier = Modifier.size(100.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Icon(Icons.Filled.HourglassEmpty, contentDescription = "Pending", tint = TerracottaPrimary, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Verification Pending", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Your driver account is currently under\nreview by the Administrator. Please check back later.", textAlign = TextAlign.Center, color = TextMuted, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { showPendingScreen = false; identifier = ""; password = "" }, colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)) { Text("Back to Login", color = Color.White) }
        }
    } else if (showRejectedScreen) {
        Column(
            modifier = Modifier.fillMaxSize().background(PeachBackground).padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(100.dp).scale(pulseScale).background(Color(0xFFFFEBEE), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Cancel, contentDescription = "Rejected", tint = Color.Red, modifier = Modifier.size(60.dp))
            }
            Spacer(modifier = Modifier.height(32.dp))
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(2.dp, GoldenYellow), elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Application Rejected", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.Red)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Reason for Rejection:", fontWeight = FontWeight.Bold, color = Color.DarkGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(rejectionReason, color = Color.Black, fontSize = 16.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { showRejectedScreen = false; identifier = ""; password = "" }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)) { Text("Back to Login", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    } else if (showApprovedScreen) {
        Column(
            modifier = Modifier.fillMaxSize().background(PeachBackground).padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(100.dp).scale(pulseScale).background(Color(0xFFE8F5E9), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = "Approved", tint = Color(0xFF2E7D32), modifier = Modifier.size(60.dp))
            }
            Spacer(modifier = Modifier.height(32.dp))
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(2.dp, GoldenYellow), elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = "Logo", modifier = Modifier.size(50.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Account Approved!", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Welcome to the team. To securely enter the driver portal, you must log in using your official Driver ID.", color = Color.DarkGray, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))

                    Text("YOUR DRIVER ID", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.fillMaxWidth().background(PeachBackground, RoundedCornerShape(12.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(approvedDriverId, fontSize = 24.sp, fontWeight = FontWeight.Black, color = TerracottaPrimary)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { showApprovedScreen = false; identifier = ""; password = "" }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)) { Text("Back to Login", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = "GroWise Logo", modifier = Modifier.size(90.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "GroWise", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text(text = "Sign In", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(40.dp))

            val isError = errorMessage.isNotEmpty()

            OutlinedTextField(
                value = identifier, onValueChange = { identifier = it; errorMessage = "" },
                placeholder = { Text("Mail ID, Phone, or GW-D ID", color = Color(0xFFAAAAAA)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().offset(x = shakeOffset.value.dp), isError = isError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), shape = RoundedCornerShape(16.dp), colors = boxedTextFieldColors
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password, onValueChange = { password = it; errorMessage = "" },
                placeholder = { Text("Enter the password", color = Color(0xFFAAAAAA)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().offset(x = shakeOffset.value.dp), isError = isError,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = RoundedCornerShape(16.dp), colors = boxedTextFieldColors,
                trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = null, tint = Color(0xFFAAAAAA)) } }
            )

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = errorMessage, color = com.simats.growise.ui.theme.ErrorRed, fontSize = 13.sp, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(40.dp))

            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Button(
                    onClick = {
                        val isEmailFormat = identifier.contains("@")
                        val isDriverIdFormat = identifier.startsWith("GW-D")

                        if (identifier == "growise@gmail.com" && password == "Grow@123") {
                            // Valid Admin Format
                        } else if (isEmailFormat && !identifier.matches(emailRegex)) { errorMessage = "Invalid Domain: Must use @gmail.com or @mail.com" }
                        else if (!isEmailFormat && !isDriverIdFormat && !identifier.matches(phoneRegex)) { errorMessage = "Invalid Phone: Must be 10 digits" }
                        else if (password.length < 6 || password.length > 8) { errorMessage = "Length Error: Password must be 6 to 8 characters." }
                        else if (!password.matches(passwordRegex)) { errorMessage = "Complexity Error: Requires 1 uppercase, 1 lowercase, 1 digit, 1 special." }

                        if (errorMessage.isEmpty()) {
                            isLoading = true
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val response = RetrofitClient.apiService.login(LoginRequest(identifier, password))
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        if (response.isSuccessful && response.body() != null) {
                                            val verificationStatus = response.body()!!.verificationStatus
                                            val userRole = response.body()!!.role ?: "user"
                                            val returnedEmail = response.body()!!.email ?: identifier

                                            if (userRole == "driver" && verificationStatus == "PENDING") {
                                                showPendingScreen = true
                                            } else if (userRole == "driver" && verificationStatus == "REJECTED") {
                                                rejectionReason = response.body()!!.rejectionReason ?: "Failed background verification."
                                                showRejectedScreen = true
                                            } else if (userRole == "driver" && verificationStatus == "APPROVED_NEEDS_ID") {
                                                approvedDriverId = response.body()!!.driverId ?: "ID_FETCH_ERROR"
                                                showApprovedScreen = true
                                            } else {
                                                // Instantly pre-cache the Driver ID to prevent UI flickering on the Profile
                                                val sp = context.getSharedPreferences("GroWiseSession", android.content.Context.MODE_PRIVATE)
                                                if (isDriverIdFormat) {
                                                    sp.edit().putString("U_DRIVER_ID", identifier).apply()
                                                }
                                                // Fetch and save vehicle type if driver
                                                if (userRole == "driver") {
                                                    try {
                                                        val profileRes = RetrofitClient.apiService.retrieveProfileFields(returnedEmail)
                                                        if (profileRes.isSuccessful) {
                                                            sp.edit().putString("DRIVER_VEHICLE_TYPE", profileRes.body()?.vehicleType ?: "Auto").apply()
                                                        }
                                                    } catch (e: Exception) {}
                                                }
                                                // Authenticate with Firebase using Custom Token
                                                val customToken = response.body()!!.customToken
                                                if (customToken != null) {
                                                    com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCustomToken(customToken)
                                                        .addOnCompleteListener { task ->
                                                            if (task.isSuccessful) {
                                                                onLoginSuccess(userRole, returnedEmail)
                                                            } else {
                                                                errorMessage = "Firebase Authentication Failed: ${task.exception?.localizedMessage}"
                                                            }
                                                        }
                                                } else {
                                                    errorMessage = "Security Error: Missing Firebase Auth Token from Server."
                                                }
                                            }
                                        } else { errorMessage = "Invalid credentials or account does not exist." }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        errorMessage = "System Error: ${e.localizedMessage}"
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Login", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "New user ? ", color = Color(0xFF888888), fontSize = 14.sp)
                Text(text = "Create account", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable { onNavigateToSignup() })
            }
        }
    }
}