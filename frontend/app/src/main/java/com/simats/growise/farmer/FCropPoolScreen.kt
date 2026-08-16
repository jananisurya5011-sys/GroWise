package com.simats.growise.farmer

import android.Manifest
import android.app.TimePickerDialog
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.location.Geocoder
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.FirebaseFirestore
import com.simats.growise.R
import com.simats.growise.data.model.*
import com.simats.growise.data.network.RetrofitClient
import com.simats.growise.ui.theme.PeachBackground
import com.simats.growise.ui.theme.TerracottaPrimary
import com.simats.growise.ui.theme.GoldenYellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.*

private fun poolCalculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FCropPoolScreen(navController: NavController, userEmail: String) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(PeachBackground)) {
                TopAppBar(
                    title = { Text("Crop Pool Hub", fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary, fontSize = 24.sp) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TerracottaPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PeachBackground)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .background(Color.White, RoundedCornerShape(24.dp))
                        .border(1.dp, Color.LightGray, RoundedCornerShape(24.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(if (selectedTabIndex == 0) TerracottaPrimary else Color.Transparent, RoundedCornerShape(24.dp))
                            .clickable { selectedTabIndex = 0 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Create Pool", color = if (selectedTabIndex == 0) Color.White else Color.Gray, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(if (selectedTabIndex == 1) TerracottaPrimary else Color.Transparent, RoundedCornerShape(24.dp))
                            .clickable { selectedTabIndex = 1 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Review", color = if (selectedTabIndex == 1) Color.White else Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().background(PeachBackground).padding(innerPadding)) {
            if (selectedTabIndex == 0) {
                CreatePoolTab(userEmail, onSwitchToReview = { selectedTabIndex = 1 })
            } else {
                ReviewPoolsTab(userEmail)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePoolTab(userEmail: String, onSwitchToReview: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var cropName by remember { mutableStateOf("") }
    var weightToPool by remember { mutableStateOf("") }
    var pickupAddress by remember { mutableStateOf("") }
    var pickupLat by remember { mutableStateOf("") }
    var pickupLon by remember { mutableStateOf("") }
    var dropAddress by remember { mutableStateOf("") }
    var dropLat by remember { mutableStateOf("") }
    var dropLon by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    var selectedDateText by remember { mutableStateOf("Select Date") }
    var selectedTimeText by remember { mutableStateOf("Select Time") }

    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var hasReadPrivacyPolicy by remember { mutableStateOf(false) }
    var termsAccepted by remember { mutableStateOf(false) }

    var showDisclaimerDialog by remember { mutableStateOf(false) }
    var showPaymentScreen by remember { mutableStateOf(false) }
    var isQuoting by remember { mutableStateOf(false) }
    var finalDistanceKm by remember { mutableDoubleStateOf(0.0) }
    var finalVehicleType by remember { mutableStateOf("Unknown") }
    var perKmRate by remember { mutableDoubleStateOf(0.0) }
    var baseFare by remember { mutableDoubleStateOf(0.0) }
    var totalAmount by remember { mutableDoubleStateOf(0.0) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
                    if (loc != null) {
                        pickupLat = loc.latitude.toString(); pickupLon = loc.longitude.toString()
                        try {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                            if (!addresses.isNullOrEmpty()) pickupAddress = addresses[0].getAddressLine(0)
                        } catch (e: Exception) {}
                    }
                }
            } catch (e: SecurityException) {}
        } else Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
    }

    val isFormComplete = cropName.isNotBlank() && weightToPool.isNotBlank() && pickupAddress.isNotBlank() && dropAddress.isNotBlank() && selectedDateText != "Select Date" && selectedTimeText != "Select Time" && termsAccepted

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val cal = Calendar.getInstance().apply { timeInMillis = it }
                        selectedDateText = "${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH)+1}/${cal.get(Calendar.YEAR)}"
                    }
                    showDatePicker = false
                }) { Text("OK", color = TerracottaPrimary) }
            }
        ) { DatePicker(state = datePickerState) }
    }

    val timePickerDialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            val amPm = if (hourOfDay >= 12) "PM" else "AM"
            val displayHour = if (hourOfDay % 12 == 0) 12 else hourOfDay % 12
            selectedTimeText = String.format("%02d:%02d %s", displayHour, minute, amPm)
        },
        10, 0, false
    )

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(16.dp))

        // 1. Cargo Details Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, Color(0xFFF9EFE9))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Inventory, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Cargo Details", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = TerracottaPrimary)
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = cropName, onValueChange = { cropName = it },
                    placeholder = { Text("Vegetable / Crop Name", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = PeachBackground, unfocusedContainerColor = Color(0xFFFDFDFD), focusedBorderColor = GoldenYellow, unfocusedBorderColor = Color(0xFFEEEEEE))
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = weightToPool, onValueChange = { weightToPool = it },
                    placeholder = { Text("Weight (kg)", color = Color.Gray) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = PeachBackground, unfocusedContainerColor = Color(0xFFFDFDFD), focusedBorderColor = GoldenYellow, unfocusedBorderColor = Color(0xFFEEEEEE))
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Logistics Details Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, Color(0xFFF9EFE9))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Pickup Location", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = TerracottaPrimary)
                    }
                    Button(
                        onClick = {
                            when (PackageManager.PERMISSION_GRANTED) {
                                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) -> {
                                    try {
                                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
                                            if (loc != null) {
                                                pickupLat = loc.latitude.toString(); pickupLon = loc.longitude.toString()
                                                try {
                                                    val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
                                                    if (!addresses.isNullOrEmpty()) pickupAddress = addresses[0].getAddressLine(0)
                                                } catch (e: Exception) {}
                                            }
                                        }
                                    } catch (e: SecurityException) {}
                                }
                                else -> locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PeachBackground),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) { Text("Auto", color = TerracottaPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = pickupAddress, onValueChange = { pickupAddress = it },
                    placeholder = { Text("Exact Pickup Address", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = PeachBackground, unfocusedContainerColor = Color(0xFFFDFDFD), focusedBorderColor = GoldenYellow, unfocusedBorderColor = Color(0xFFEEEEEE))
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = pickupLat, onValueChange = { pickupLat = it }, placeholder = { Text("Lat", color = Color.Gray) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = PeachBackground, unfocusedContainerColor = Color(0xFFFDFDFD), focusedBorderColor = GoldenYellow, unfocusedBorderColor = Color(0xFFEEEEEE)))
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(value = pickupLon, onValueChange = { pickupLon = it }, placeholder = { Text("Lon", color = Color.Gray) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = PeachBackground, unfocusedContainerColor = Color(0xFFFDFDFD), focusedBorderColor = GoldenYellow, unfocusedBorderColor = Color(0xFFEEEEEE)))
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Flag, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Drop Location", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = TerracottaPrimary)
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = dropAddress, onValueChange = { dropAddress = it },
                    placeholder = { Text("Exact Drop Destination", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = PeachBackground, unfocusedContainerColor = Color(0xFFFDFDFD), focusedBorderColor = GoldenYellow, unfocusedBorderColor = Color(0xFFEEEEEE))
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = dropLat, onValueChange = { dropLat = it }, placeholder = { Text("Lat", color = Color.Gray) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = PeachBackground, unfocusedContainerColor = Color(0xFFFDFDFD), focusedBorderColor = GoldenYellow, unfocusedBorderColor = Color(0xFFEEEEEE)))
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(value = dropLon, onValueChange = { dropLon = it }, placeholder = { Text("Lon", color = Color.Gray) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = PeachBackground, unfocusedContainerColor = Color(0xFFFDFDFD), focusedBorderColor = GoldenYellow, unfocusedBorderColor = Color(0xFFEEEEEE)))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Dispatch & Agreement Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, Color(0xFFF9EFE9))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Dispatch Timing", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = TerracottaPrimary)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = PeachBackground, contentColor = TerracottaPrimary),
                        border = BorderStroke(1.dp, Color(0xFFEEEEEE))
                    ) {
                        Icon(Icons.Filled.CalendarToday, null, tint = TerracottaPrimary, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp))
                        Text(selectedDateText, color = TerracottaPrimary)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = { timePickerDialog.show() },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = PeachBackground, contentColor = TerracottaPrimary),
                        border = BorderStroke(1.dp, Color(0xFFEEEEEE))
                    ) {
                        Icon(Icons.Filled.AccessTime, null, tint = TerracottaPrimary, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp))
                        Text(selectedTimeText, color = TerracottaPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = termsAccepted, onCheckedChange = { termsAccepted = it }, enabled = hasReadPrivacyPolicy, colors = CheckboxDefaults.colors(checkedColor = TerracottaPrimary))
                    Text("I accept the ", fontSize = 13.sp)
                    Text("Privacy Policy", fontSize = 13.sp, color = TerracottaPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { showPrivacyPolicy = true })
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                isQuoting = true
                coroutineScope.launch {
                    try {
                        val pLatDouble = pickupLat.toDoubleOrNull() ?: 0.0
                        val pLonDouble = pickupLon.toDoubleOrNull() ?: 0.0
                        val dLatDouble = dropLat.toDoubleOrNull() ?: 0.0
                        val dLonDouble = dropLon.toDoubleOrNull() ?: 0.0
                        val wKg = weightToPool.toDoubleOrNull() ?: 0.0

                        val req = LogisticsFareRequest(wKg, pLatDouble, pLonDouble, dLatDouble, dLonDouble)
                        val res = RetrofitClient.apiService.quotePool(req)
                        if (res.isSuccessful && res.body()?.success == true) {
                            val quote = res.body()!!
                            finalDistanceKm = quote.distanceKm
                            finalVehicleType = quote.vehicleType
                            baseFare = quote.baseFare
                            perKmRate = quote.perKmRate
                            totalAmount = quote.totalAmount
                            showDisclaimerDialog = true
                        } else {
                            Toast.makeText(context, "Failed to calculate secure fare.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Network Error", Toast.LENGTH_SHORT).show()
                    } finally {
                        isQuoting = false
                    }
                }
            },
            enabled = isFormComplete && !isQuoting,
            colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary, disabledContainerColor = Color.Gray),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            if (isQuoting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text("Review & Escrow Lock", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(40.dp))
    }

    if (showPrivacyPolicy) {
        ModalBottomSheet(onDismissRequest = { showPrivacyPolicy = false; hasReadPrivacyPolicy = true }) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Strict No-Cancellation Policy", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TerracottaPrimary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("By proceeding, your funds are securely locked in an Escrow account. Because Heavy Freight requires guaranteed fare for drivers, you CANNOT cancel this order once the Escrow is engaged.", color = Color.DarkGray)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { showPrivacyPolicy = false; hasReadPrivacyPolicy = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)) { Text("I Understand", color = Color.White) }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showDisclaimerDialog) {
        var typedConsent by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showDisclaimerDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x44FF0000), Color.Transparent))), contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp).shadow(24.dp, RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Warning, null, tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("100% Liability Warning", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.Red)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Total Distance: ${finalDistanceKm} km\nBase Fare: ₹${baseFare} + (₹${perKmRate}/km)\n\nYou are responsible for 100% of the transport fare (₹${String.format("%.2f", totalAmount)}) unless another farmer joins your pool. Funds will be locked in Escrow instantly.", textAlign = TextAlign.Center, color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedTextField(
                            value = typedConsent, onValueChange = { typedConsent = it.uppercase() },
                            placeholder = { Text("Type 'ACCEPT' to confirm", color = Color.Gray) }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedBorderColor = if (typedConsent == "ACCEPT") Color(0xFF4CAF50) else GoldenYellow, unfocusedBorderColor = Color.LightGray)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showDisclaimerDialog = false; showPaymentScreen = true },
                            enabled = typedConsent == "ACCEPT", colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(24.dp)
                        ) { Text("Proceed to Payment", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    AnimatedVisibility(visible = showPaymentScreen, enter = slideInVertically(initialOffsetY = { it }), exit = slideOutVertically(targetOffsetY = { it })) {
        var isProcessing by remember { mutableStateOf(false) }
        var isSuccess by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf("") }

        Box(modifier = Modifier.fillMaxSize().background(PeachBackground).padding(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                if (isSuccess) {
                    Spacer(modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.size(120.dp).background(Color(0xFF4CAF50), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(80.dp))
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Payment Successful!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    Text("Escrow Locked.", color = Color.Gray)
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(onClick = {
                        val invText = "GroWise Crop Pool Invoice\n\nVegetable: $cropName\nWeight: $weightToPool kg\nVehicle: $finalVehicleType\nDistance: ${String.format("%.2f", finalDistanceKm)} km\nRate: Rs. $perKmRate/km\n\nTotal Paid: Rs. ${String.format("%.2f", totalAmount)}\nDate: ${Date()}"
                        generatePdfAndShare(context, invText)
                    }, colors = ButtonDefaults.buttonColors(containerColor = GoldenYellow), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(24.dp)) {
                        Icon(Icons.Filled.Download, null, tint = Color.White); Spacer(modifier = Modifier.width(8.dp))
                        Text("Download PDF Invoice", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    LaunchedEffect(Unit) { delay(3000); showPaymentScreen = false; onSwitchToReview() }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        IconButton(onClick = { showPaymentScreen = false }) { Icon(Icons.Filled.Close, null) }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("GroWise Crop Pool Invoice", fontSize = 28.sp, fontWeight = FontWeight.Black, color = TerracottaPrimary)
                    Spacer(modifier = Modifier.height(32.dp))
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(2.dp, GoldenYellow)) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Crop Name", color = Color.Gray); Text(cropName, fontWeight = FontWeight.Bold) }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Weight", color = Color.Gray); Text("$weightToPool kg", fontWeight = FontWeight.Bold) }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Assigned Vehicle", color = Color.Gray); Text(finalVehicleType, fontWeight = FontWeight.Bold, color = TerracottaPrimary) }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Distance Calculated", color = Color.Gray); Text("${String.format("%.2f", finalDistanceKm)} km", fontWeight = FontWeight.Bold) }
                        }
                    }
                    if (errorMessage.isNotEmpty()) { Spacer(modifier = Modifier.height(16.dp)); Text(errorMessage, color = Color.Red, fontSize = 14.sp) }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            isProcessing = true; errorMessage = ""
                            coroutineScope.launch {
                                try {
                                    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy hh:mm a", java.util.Locale.getDefault())
                                    val dispatchTimeMillis = try {
                                        sdf.parse("$selectedDateText $selectedTimeText")?.time ?: (System.currentTimeMillis() + 86400000)
                                    } catch (e: Exception) { System.currentTimeMillis() + 86400000 }

                                    val req = CreatePoolRequest(farmerEmail = userEmail, cropName = cropName, weightKg = weightToPool.toDoubleOrNull() ?: 100.0, pickupAddress = pickupAddress, dropAddress = dropAddress, pickupLat = pickupLat.toDoubleOrNull() ?: 13.1, pickupLon = pickupLon.toDoubleOrNull() ?: 80.1, dropLat = dropLat.toDoubleOrNull() ?: 13.2, dropLon = dropLon.toDoubleOrNull() ?: 80.2, dispatchTime = dispatchTimeMillis, vehicleType = finalVehicleType, distanceKm = finalDistanceKm, totalAmount = totalAmount)
                                    val res = RetrofitClient.apiService.createPool(req)
                                    if (res.isSuccessful && res.body()?.success == true) isSuccess = true else errorMessage = res.body()?.error ?: "Insufficient wallet balance."
                                } catch (e: Exception) { errorMessage = "Network Error" } finally { isProcessing = false }
                            }
                        },
                        enabled = !isProcessing, colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(24.dp)
                    ) {
                        if(isProcessing) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp)) else {
                            Icon(Icons.Filled.AccountBalanceWallet, null, tint = Color.White); Spacer(modifier = Modifier.width(8.dp))
                            Text("Pay through Wallet ₹${String.format("%.2f", totalAmount)}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun ReviewPoolsTab(userEmail: String) {
    val context = LocalContext.current // FIX: Added context for Toast messages
    var availablePools by remember { mutableStateOf<List<PoolItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    var selectedPoolToJoin by remember { mutableStateOf<PoolItem?>(null) }
    var selectedHostPool by remember { mutableStateOf<PoolItem?>(null) }
    var selectedCoLoaderPool by remember { mutableStateOf<PoolItem?>(null) }

    val fetchPools = {
        isLoading = true
        coroutineScope.launch {
            try {
                // 1. Fetch user's registered home coordinates from their profile
                val profileRes = RetrofitClient.apiService.retrieveProfileFields(userEmail)
                var userLat = 0.0
                var userLon = 0.0

                if (profileRes.isSuccessful) {
                    val profile = profileRes.body()
                    userLat = profile?.homeLat ?: 0.0
                    userLon = profile?.homeLon ?: 0.0
                }

                // 2. Query available pools natively mapping to the new unified orders logic
                val res = RetrofitClient.apiService.getAvailablePools(userLat, userLon, userEmail)
                if (res.isSuccessful && res.body()?.success == true) availablePools = res.body()?.pools ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }
    LaunchedEffect(Unit) { fetchPools() }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        android.widget.ImageView(ctx).apply {
                            setImageResource(R.drawable.ic_agri_loading)
                            val d = drawable
                            if (d is android.graphics.drawable.Animatable) d.start()
                        }
                    },
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Syncing Pools...", color = TerracottaPrimary, fontWeight = FontWeight.Bold)
            }
        } else if (availablePools.isEmpty()) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Inbox, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No active pools.", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                items(availablePools) { pool ->
                    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
                    var isDeleting by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        while(true) {
                            kotlinx.coroutines.delay(1000L)
                            currentTime = System.currentTimeMillis()
                        }
                    }

                    val diff = pool.dispatchTimestamp - currentTime
                    val hours = maxOf(0L, diff / 3600000)
                    val minutes = maxOf(0L, (diff % 3600000) / 60000)
                    val seconds = maxOf(0L, (diff % 60000) / 1000)
                    val timeRemainingText = if (diff > 0) String.format(Locale.getDefault(), "Time Remaining: %02dh %02dm %02ds", hours, minutes, seconds) else "EXPIRED"

                    val isCancellable = pool.hostEmail == userEmail && pool.coLoaderEmail == null && pool.driverEmail == null && pool.status == "PENDING_CO_LOADER"

                    val isIncomplete = pool.status in listOf("PENDING_CO_LOADER", "PENDING_DRIVER", "EN_ROUTE_TO_PICKUP_A", "WAITING_AT_PICKUP_A")
                    val isExpired = pool.status == "EXPIRED" || (diff <= 0 && isIncomplete)
                    val statusBgColor: Color
                    val statusTextColor: Color
                    val statusText: String
                    when {
                        isExpired || pool.status.contains("CANCELLED") -> { statusBgColor = Color(0xFFFFEBEE); statusTextColor = Color.Red; statusText = "Failed - No Drivers" }
                        pool.status == "DELIVERED" -> { statusBgColor = Color(0xFFE8F5E9); statusTextColor = Color(0xFF2E7D32); statusText = "Completed" }
                        pool.driverEmail != null || pool.status.contains("IN_TRANSIT") || pool.status.contains("EN_ROUTE") -> { statusBgColor = Color(0xFFE3F2FD); statusTextColor = Color(0xFF1565C0); statusText = "Ongoing" }
                        pool.coLoaderEmail != null && pool.driverEmail == null -> { statusBgColor = Color(0xFFE8F5E9); statusTextColor = Color(0xFF2E7D32); statusText = "Co-loader Joined" }
                        else -> { statusBgColor = Color(0xFFFFF3E0); statusTextColor = GoldenYellow; statusText = "Waiting for Co-loader" }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .shadow(4.dp, RoundedCornerShape(16.dp))
                            .alpha(if (isExpired || pool.status.contains("CANCELLED")) 0.6f else 1f)
                            .clickable {
                                if (isExpired) {
                                    Toast.makeText(context, "Failed to deliver. Driver not found in time.", Toast.LENGTH_LONG).show()
                                } else if (pool.hostEmail == userEmail) {
                                    selectedHostPool = pool
                                } else if (pool.coLoaderEmail == userEmail) {
                                    selectedCoLoaderPool = pool
                                } else {
                                    selectedPoolToJoin = pool
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, GoldenYellow)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Host: ${pool.hostEmail.take(12)}...", fontWeight = FontWeight.Bold, color = TerracottaPrimary)
                                Box(modifier = Modifier.background(statusBgColor, RoundedCornerShape(8.dp)).padding(horizontal=8.dp, vertical=4.dp)) {
                                    Text(statusText, color = statusTextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            val postedDate = java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(pool.dispatchTimestamp))
                            Text("Posted: $postedDate", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("From: ${pool.pickupAddress}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("(Lat: ${String.format(Locale.US, "%.4f", pool.pickupLat)}, Lon: ${String.format(Locale.US, "%.4f", pool.pickupLon)})", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("To: ${pool.dropAddress}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("(Lat: ${String.format(Locale.US, "%.4f", pool.dropLat)}, Lon: ${String.format(Locale.US, "%.4f", pool.dropLon)})", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Cargo: ${pool.cropName} - ${pool.weightKg} kg", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.DarkGray)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Vehicle: ${pool.vehicleType}", color = Color.DarkGray, fontSize = 14.sp)
                                Text("Avail: ${pool.remainingCapacity} kg", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (pool.status != "DELIVERED" && !isExpired) {
                                Text("⏱ $timeRemainingText", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else if (isExpired) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("❌ EXPIRED", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                    if (isDeleting) {
                                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Red, strokeWidth = 2.dp)
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                isDeleting = true
                                                coroutineScope.launch {
                                                    try {
                                                        val res = RetrofitClient.apiService.deletePool(com.simats.growise.data.model.PoolIdRequest(poolId = pool.orderId))
                                                        if (res.isSuccessful && res.body()?.success == true) {
                                                            Toast.makeText(context, "Pool removed from your list.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Network Error", Toast.LENGTH_SHORT).show()
                                                    } finally {
                                                        isDeleting = false
                                                        fetchPools()
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                            border = BorderStroke(1.dp, Color.Red),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.height(36.dp)
                                        ) { Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }

                            if (isCancellable) {
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                val req = com.simats.growise.data.model.OrderIdRequest(orderId = pool.orderId)
                                                // Calling the correct dedicated cancel endpoint
                                                val res = RetrofitClient.apiService.cancelOrder(req)

                                                if (res.isSuccessful && res.body()?.success == true) {
                                                    Toast.makeText(context, "Order Cancelled. Escrow Refunded.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Failed to cancel. Funds might be locked.", Toast.LENGTH_LONG).show()
                                                }
                                            } catch (e: retrofit2.HttpException) {
                                                // Catch 400 Errors thrown by strict Escrow rules
                                                Toast.makeText(context, "Cannot Cancel: Funds locked in Escrow.", Toast.LENGTH_LONG).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Network Error", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                fetchPools() // Always refresh the list
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                    border = BorderStroke(1.dp, Color.Red),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Cancel Order & Refund Escrow", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            } else if (pool.coLoaderEmail != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(modifier = Modifier.background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp)).padding(8.dp).fillMaxWidth()) {
                                    Text("Co-loader Joined! Escrow Split Active.", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = selectedPoolToJoin != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        if (selectedPoolToJoin != null) {
            JoinPoolFullScreen(
                pool = selectedPoolToJoin!!,
                userEmail = userEmail,
                onBack = { selectedPoolToJoin = null },
                onSuccess = { selectedPoolToJoin = null; fetchPools() }
            )
        }
    }

    AnimatedVisibility(
        visible = selectedHostPool != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        if (selectedHostPool != null) {
            HostPoolTrackingFullScreen(
                pool = selectedHostPool!!,
                onBack = { selectedHostPool = null }
            )
        }
    }

    AnimatedVisibility(
        visible = selectedCoLoaderPool != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        if (selectedCoLoaderPool != null) {
            CoLoaderTrackingFullScreen(
                pool = selectedCoLoaderPool!!,
                onBack = { selectedCoLoaderPool = null }
            )
        }
    }
}

@Composable
fun RouteTimeline(pool: PoolItem, isHostView: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        // Timeline Item A
        Row(verticalAlignment = Alignment.Top) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Circle, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(16.dp))
                Box(modifier = Modifier.width(2.dp).height(30.dp).background(Color.LightGray))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Host Pickup", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Text(pool.pickupAddress, fontSize = 14.sp, color = Color.Black, fontWeight = FontWeight.SemiBold)
                Text("(Lat: ${String.format(Locale.US, "%.4f", pool.pickupLat)}, Lon: ${String.format(Locale.US, "%.4f", pool.pickupLon)})", fontSize = 11.sp, color = Color.Gray)
            }
        }

        // Timeline Item B (If CoLoader joined)
        if (pool.coLoaderEmail != null) {
            Row(verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Circle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                    Box(modifier = Modifier.width(2.dp).height(30.dp).background(Color.LightGray))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Co-Loader Pickup (${pool.coLoaderEmail?.take(12)}...)", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    Text(pool.coLoaderPickupAddress ?: "Co-Loader Pickup Location", fontSize = 14.sp, color = Color.Black, fontWeight = FontWeight.SemiBold)
                    Text("(Lat: ${String.format(Locale.US, "%.4f", pool.coLoaderPickupLat ?: 0.0)}, Lon: ${String.format(Locale.US, "%.4f", pool.coLoaderPickupLon ?: 0.0)})", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }

        // Timeline Item C
        Row(verticalAlignment = Alignment.Top) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = TerracottaPrimary, modifier = Modifier.size(16.dp))
                if (pool.coLoaderEmail != null) {
                    Box(modifier = Modifier.width(2.dp).height(30.dp).background(Color.LightGray))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Host Drop", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Text(pool.dropAddress, fontSize = 14.sp, color = Color.Black, fontWeight = FontWeight.SemiBold)
                Text("(Lat: ${String.format(Locale.US, "%.4f", pool.dropLat)}, Lon: ${String.format(Locale.US, "%.4f", pool.dropLon)})", fontSize = 11.sp, color = Color.Gray)
            }
        }

        // Timeline Item D (If CoLoader joined)
        if (pool.coLoaderEmail != null) {
            Row(verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Co-Loader Drop", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    Text(pool.coLoaderDropAddress ?: "Co-Loader Drop Destination", fontSize = 14.sp, color = Color.Black, fontWeight = FontWeight.SemiBold)
                    Text("(Lat: ${String.format(Locale.US, "%.4f", pool.coLoaderDropLat ?: 0.0)}, Lon: ${String.format(Locale.US, "%.4f", pool.coLoaderDropLon ?: 0.0)})", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun EscrowTimelineVisual(pool: PoolItem) {
    val step = when {
        pool.status == "DELIVERED" -> 4
        pool.driverEmail != null -> 3
        pool.coLoaderEmail != null -> 2
        else -> 1
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Lock, null, tint = TerracottaPrimary, modifier = Modifier.size(20.dp))
            Text("Locked", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.height(2.dp).weight(1f).background(if (step >= 2) Color(0xFF4CAF50) else Color.LightGray))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.CallSplit, null, tint = if (step >= 2) Color(0xFF4CAF50) else Color.LightGray, modifier = Modifier.size(20.dp))
            Text("Split", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.height(2.dp).weight(1f).background(if (step >= 3) Color(0xFF4CAF50) else Color.LightGray))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.LocalShipping, null, tint = if (step >= 3) Color(0xFF4CAF50) else Color.LightGray, modifier = Modifier.size(20.dp))
            Text("Driver", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.height(2.dp).weight(1f).background(if (step >= 4) Color(0xFF4CAF50) else Color.LightGray))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.CheckCircle, null, tint = if (step >= 4) Color(0xFF4CAF50) else Color.LightGray, modifier = Modifier.size(20.dp))
            Text("Paid", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HostPoolTrackingFullScreen(pool: PoolItem, onBack: () -> Unit) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize().background(PeachBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 8.dp, end = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TerracottaPrimary) }
                Text("Live Pool Tracking", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary)
            }

            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {

                // NEW: Escrow Timeline
                Card(modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Interactive Escrow Progress", fontWeight = FontWeight.Bold, color = TerracottaPrimary)
                        EscrowTimelineVisual(pool)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Card(modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Map, contentDescription = null, tint = TerracottaPrimary, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Logistics Route", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        RouteTimeline(pool, isHostView = true)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                val db = FirebaseFirestore.getInstance()
                Text("Co-Loader Status & Fare", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TerracottaPrimary)
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, if (pool.coLoaderEmail == null) GoldenYellow else Color(0xFF4CAF50))
                ) {
                    Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (pool.coLoaderEmail != null) {
                                var coName by remember { mutableStateOf("Co-Loader") }
                                var coPhone by remember { mutableStateOf("") }
                                var coImg by remember { mutableStateOf("") }
                                LaunchedEffect(pool.coLoaderEmail) {
                                    db.collection("users").document(pool.coLoaderEmail!!).get().addOnSuccessListener { d ->
                                        coName = d.getString("name") ?: "Co-Loader"
                                        coPhone = d.getString("phone") ?: ""
                                        coImg = d.getString("profileImageUrl") ?: ""
                                    }
                                }
                                if (coImg.isNotEmpty()) {
                                    val safeUrl = if (coImg.startsWith("http")) coImg else "${RetrofitClient.BASE_URL}$coImg"
                                    AsyncImage(model = safeUrl, contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                } else {
                                    Image(painter = androidx.compose.ui.res.painterResource(R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(coName, color = Color(0xFF2E7D32), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                    Text(pool.coLoaderEmail!!, fontSize = 14.sp, color = Color.DarkGray)
                                }
                                IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$coPhone"))) }, modifier = Modifier.background(Color(0xFFE8F5E9), CircleShape)) {
                                    Icon(Icons.Filled.Phone, null, tint = Color(0xFF2E7D32))
                                }
                            } else {
                                Box(modifier = Modifier.size(56.dp).background(GoldenYellow.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.HourglassEmpty, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(28.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Waiting for Co-loader", color = GoldenYellow, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                    Text("Available: ${pool.remainingCapacity} kg", fontSize = 14.sp, color = Color.DarkGray)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.LightGray)
                        Spacer(modifier = Modifier.height(12.dp))
                        val originalPay = pool.totalPayment + (pool.coLoaderPayment ?: 0.0)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Original Pay:", color = Color.Gray, fontSize = 12.sp); Text("₹${String.format("%.2f", originalPay)}", fontSize = 12.sp)
                        }
                        if (pool.coLoaderEmail != null) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Co-Loader Escrow Refund:", color = Color.Red, fontSize = 12.sp); Text("- ₹${String.format("%.2f", pool.coLoaderPayment ?: 0.0)}", color = Color.Red, fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Your Final Escrow Pay:", color = Color.DarkGray, fontWeight = FontWeight.Bold)
                            Text("₹${String.format("%.2f", pool.totalPayment)}", color = TerracottaPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("Driver Profile", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TerracottaPrimary)
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, if (pool.driverEmail == null) GoldenYellow else Color(0xFF4CAF50))
                ) {
                    Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (pool.driverEmail != null) {
                                var drName by remember { mutableStateOf("Driver") }
                                var drPhone by remember { mutableStateOf("") }
                                var drImg by remember { mutableStateOf("") }
                                var drId by remember { mutableStateOf("GW-D...") }
                                LaunchedEffect(pool.driverEmail) {
                                    db.collection("users").document(pool.driverEmail!!).get().addOnSuccessListener { d ->
                                        drName = d.getString("name") ?: "Driver"
                                        drPhone = d.getString("phone") ?: ""
                                        drImg = d.getString("profileImageUrl") ?: ""
                                        drId = d.getString("driverId") ?: "GW-D0000"
                                    }
                                }
                                if (drImg.isNotEmpty()) {
                                    val safeUrl = if (drImg.startsWith("http")) drImg else "${RetrofitClient.BASE_URL}$drImg"
                                    AsyncImage(model = safeUrl, contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                } else {
                                    Image(painter = androidx.compose.ui.res.painterResource(R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("$drName ($drId)", color = Color(0xFF2E7D32), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                    Text(pool.driverEmail!!, fontSize = 12.sp, color = Color.DarkGray)
                                }
                                IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$drPhone"))) }, modifier = Modifier.background(Color(0xFFE3F2FD), CircleShape)) {
                                    Icon(Icons.Filled.Phone, null, tint = Color(0xFF1565C0))
                                }
                            } else {
                                Box(modifier = Modifier.size(56.dp).background(GoldenYellow.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Search, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(28.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Searching for Driver", color = GoldenYellow, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                    Text("Matching nearby trucks...", fontSize = 14.sp, color = Color.DarkGray)
                                }
                            }
                        }

                        if (pool.driverEmail != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = Color.LightGray)
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Host Pickup OTP", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(pool.pickupOtp_A, fontSize = 24.sp, fontWeight = FontWeight.Black, color = TerracottaPrimary, letterSpacing = 4.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Host Drop OTP", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(pool.dropOtp_A, fontSize = 24.sp, fontWeight = FontWeight.Black, color = TerracottaPrimary, letterSpacing = 4.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun CoLoaderTrackingFullScreen(pool: PoolItem, onBack: () -> Unit) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize().background(PeachBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 8.dp, end = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TerracottaPrimary) }
                Text("Co-Loader Tracking", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary)
            }

            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {

                Card(modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Interactive Escrow Progress", fontWeight = FontWeight.Bold, color = TerracottaPrimary)
                        EscrowTimelineVisual(pool)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Co-Loader Live Tracking Map Wrapper
                Card(modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        if (pool.driverEmail != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Map, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Logistics Route", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            RouteTimeline(pool, isHostView = false)
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                                Icon(Icons.Filled.LocationOff, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Tracking Map locked until Driver assigned.", color = Color.Gray, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                val db = FirebaseFirestore.getInstance()

                Text("Host Status & Fare Details", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TerracottaPrimary)
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFF4CAF50))
                ) {
                    Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var hostName by remember { mutableStateOf("Host Farmer") }
                            var hostPhone by remember { mutableStateOf("") }
                            var hostImg by remember { mutableStateOf("") }
                            LaunchedEffect(pool.hostEmail) {
                                db.collection("users").document(pool.hostEmail).get().addOnSuccessListener { d ->
                                    hostName = d.getString("name") ?: "Host Farmer"
                                    hostPhone = d.getString("phone") ?: ""
                                    hostImg = d.getString("profileImageUrl") ?: ""
                                }
                            }
                            if (hostImg.isNotEmpty()) {
                                val safeUrl = if (hostImg.startsWith("http")) hostImg else "${RetrofitClient.BASE_URL}$hostImg"
                                AsyncImage(model = safeUrl, contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            } else {
                                Image(painter = androidx.compose.ui.res.painterResource(R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(hostName, color = Color(0xFF2E7D32), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                Text(pool.hostEmail, fontSize = 14.sp, color = Color.DarkGray)
                            }
                            IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$hostPhone"))) }, modifier = Modifier.background(Color(0xFFE8F5E9), CircleShape)) {
                                Icon(Icons.Filled.Phone, null, tint = Color(0xFF2E7D32))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.LightGray)
                        Spacer(modifier = Modifier.height(12.dp))
                        val originalHostPay = pool.totalPayment + (pool.coLoaderPayment ?: 0.0)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Host Original Lock:", color = Color.Gray, fontSize = 12.sp); Text("₹${String.format("%.2f", originalHostPay)}", fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Detour Applied (+):", color = Color(0xFF2E7D32), fontSize = 12.sp); Text("Included", color = Color(0xFF2E7D32), fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Your Total Escrow Paid:", color = Color.DarkGray, fontWeight = FontWeight.Bold)
                            Text("₹${String.format("%.2f", pool.coLoaderPayment ?: 0.0)}", color = Color(0xFF2E7D32), fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("Driver Profile", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TerracottaPrimary)
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, if (pool.driverEmail == null) GoldenYellow else Color(0xFF4CAF50))
                ) {
                    Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (pool.driverEmail != null) {
                                var drName by remember { mutableStateOf("Driver") }
                                var drPhone by remember { mutableStateOf("") }
                                var drImg by remember { mutableStateOf("") }
                                var drId by remember { mutableStateOf("GW-D...") }
                                LaunchedEffect(pool.driverEmail) {
                                    db.collection("users").document(pool.driverEmail!!).get().addOnSuccessListener { d ->
                                        drName = d.getString("name") ?: "Driver"
                                        drPhone = d.getString("phone") ?: ""
                                        drImg = d.getString("profileImageUrl") ?: ""
                                        drId = d.getString("driverId") ?: "GW-D0000"
                                    }
                                }
                                if (drImg.isNotEmpty()) {
                                    AsyncImage(model = drImg, contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape))
                                } else {
                                    Image(painter = androidx.compose.ui.res.painterResource(R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("$drName ($drId)", color = Color(0xFF2E7D32), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                    Text(pool.driverEmail!!, fontSize = 12.sp, color = Color.DarkGray)
                                }
                                IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$drPhone"))) }, modifier = Modifier.background(Color(0xFFE3F2FD), CircleShape)) {
                                    Icon(Icons.Filled.Phone, null, tint = Color(0xFF1565C0))
                                }
                            } else {
                                Box(modifier = Modifier.size(56.dp).background(GoldenYellow.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Search, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(28.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Searching for Driver", color = GoldenYellow, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                    Text("Matching nearby trucks...", fontSize = 14.sp, color = Color.DarkGray)
                                }
                            }
                        }

                        if (pool.driverEmail != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = Color.LightGray)
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Co-Loader Pickup OTP", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(pool.pickupOtp_B ?: "----", fontSize = 24.sp, fontWeight = FontWeight.Black, color = TerracottaPrimary, letterSpacing = 4.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Co-Loader Drop OTP", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(pool.dropOtp_B ?: "----", fontSize = 24.sp, fontWeight = FontWeight.Black, color = TerracottaPrimary, letterSpacing = 4.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinPoolFullScreen(pool: PoolItem, userEmail: String, onBack: () -> Unit, onSuccess: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var cropName by remember { mutableStateOf("") }
    var weightToPool by remember { mutableStateOf("") }
    var pickupAddress by remember { mutableStateOf("") }
    var pickupLat by remember { mutableStateOf("") }
    var pickupLon by remember { mutableStateOf("") }
    var dropAddress by remember { mutableStateOf("") }
    var dropLat by remember { mutableStateOf("") }
    var dropLon by remember { mutableStateOf("") }

    var showInvoice by remember { mutableStateOf(false) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
                    if (loc != null) {
                        pickupLat = loc.latitude.toString(); pickupLon = loc.longitude.toString()
                        try {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                            if (!addresses.isNullOrEmpty()) pickupAddress = addresses[0].getAddressLine(0)
                        } catch (e: Exception) {}
                    }
                }
            } catch (e: SecurityException) {}
        } else Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
    }

    val isFormComplete = cropName.isNotBlank() && weightToPool.isNotBlank() && pickupAddress.isNotBlank() && dropAddress.isNotBlank()
    val availableSpace = pool.remainingCapacity

    Box(modifier = Modifier.fillMaxSize().background(PeachBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 8.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TerracottaPrimary) }
                Text("Join Co-Load Pool", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary)
            }

            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, GoldenYellow)) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Available Capacity:", fontWeight = FontWeight.Bold, color = Color.Gray)
                        Text("$availableSpace kg", fontWeight = FontWeight.Black, color = Color(0xFF2E7D32), fontSize = 18.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Cargo Details", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TerracottaPrimary, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = cropName, onValueChange = { cropName = it },
                    placeholder = { Text("Vegetable / Crop Name", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedBorderColor = GoldenYellow, unfocusedBorderColor = Color.LightGray)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = weightToPool, onValueChange = { weightToPool = it },
                    placeholder = { Text("Weight (kg)", color = Color.Gray) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedBorderColor = GoldenYellow, unfocusedBorderColor = Color.LightGray)
                )
                if ((weightToPool.toDoubleOrNull() ?: 0.0) > availableSpace) {
                    Text("Exceeds available capacity!", color = Color.Red, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start).padding(start = 8.dp, top = 4.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Pickup Location", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TerracottaPrimary)
                    Button(
                        onClick = {
                            when (PackageManager.PERMISSION_GRANTED) {
                                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) -> {
                                    try {
                                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
                                            if (loc != null) {
                                                pickupLat = loc.latitude.toString(); pickupLon = loc.longitude.toString()
                                                try {
                                                    val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
                                                    if (!addresses.isNullOrEmpty()) pickupAddress = addresses[0].getAddressLine(0)
                                                } catch (e: Exception) {}
                                            }
                                        }
                                    } catch (e: SecurityException) {}
                                }
                                else -> locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) { Text("Auto-Fetch", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pickupAddress, onValueChange = { pickupAddress = it },
                    placeholder = { Text("Exact Pickup Address", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedBorderColor = GoldenYellow, unfocusedBorderColor = Color.LightGray)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = pickupLat, onValueChange = { pickupLat = it }, placeholder = { Text("Lat", color = Color.Gray) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedBorderColor = GoldenYellow, unfocusedBorderColor = Color.LightGray))
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(value = pickupLon, onValueChange = { pickupLon = it }, placeholder = { Text("Lon", color = Color.Gray) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedBorderColor = GoldenYellow, unfocusedBorderColor = Color.LightGray))
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Drop Location", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TerracottaPrimary, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = dropAddress, onValueChange = { dropAddress = it },
                    placeholder = { Text("Exact Drop Destination", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedBorderColor = GoldenYellow, unfocusedBorderColor = Color.LightGray)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = dropLat, onValueChange = { dropLat = it }, placeholder = { Text("Lat", color = Color.Gray) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedBorderColor = GoldenYellow, unfocusedBorderColor = Color.LightGray))
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(value = dropLon, onValueChange = { dropLon = it }, placeholder = { Text("Lon", color = Color.Gray) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedBorderColor = GoldenYellow, unfocusedBorderColor = Color.LightGray))
                }

                Spacer(modifier = Modifier.height(32.dp))
                var isQuotingShare by remember { mutableStateOf(false) }
                var propShare by remember { mutableDoubleStateOf(0.0) }
                var detourKm by remember { mutableDoubleStateOf(0.0) }
                var detourCost by remember { mutableDoubleStateOf(0.0) }
                var totalShareB by remember { mutableDoubleStateOf(0.0) }

                Button(
                    onClick = {
                        isQuotingShare = true
                        coroutineScope.launch {
                            try {
                                val wKg = weightToPool.toDoubleOrNull() ?: 0.0
                                val pLatDouble = pickupLat.toDoubleOrNull() ?: 0.0
                                val pLonDouble = pickupLon.toDoubleOrNull() ?: 0.0
                                val dLatDouble = dropLat.toDoubleOrNull() ?: 0.0
                                val dLonDouble = dropLon.toDoubleOrNull() ?: 0.0

                                val req = JoinPoolQuoteRequest(
                                    poolId = pool.orderId, farmerEmail = userEmail,
                                    weightKg = wKg, pickupLat = pLatDouble, pickupLon = pLonDouble,
                                    dropLat = dLatDouble, dropLon = dLonDouble
                                )
                                val res = RetrofitClient.apiService.quoteJoinPool(req)
                                if (res.isSuccessful && res.body()?.success == true) {
                                    val q = res.body()!!
                                    propShare = q.proportionalShare
                                    detourKm = q.detourKm
                                    detourCost = q.detourCost
                                    totalShareB = q.totalShareB
                                    showInvoice = true
                                } else {
                                    Toast.makeText(context, "Failed to calculate dynamic detour.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Network Error", Toast.LENGTH_SHORT).show()
                            } finally {
                                isQuotingShare = false
                            }
                        }
                    },
                    enabled = isFormComplete && !isQuotingShare && (weightToPool.toDoubleOrNull() ?: 0.0) <= availableSpace,
                    colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary, disabledContainerColor = Color.Gray),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    if (isQuotingShare) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    else Text("Calculate Share & Review", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    if (showInvoice) {
        val wKg = weightToPool.toDoubleOrNull() ?: 0.0
        val pLatDouble = pickupLat.toDoubleOrNull() ?: 0.0
        val pLonDouble = pickupLon.toDoubleOrNull() ?: 0.0
        val dLatDouble = dropLat.toDoubleOrNull() ?: 0.0
        val dLonDouble = dropLon.toDoubleOrNull() ?: 0.0

        val distPToP = poolCalculateDistance(pool.pickupLat, pool.pickupLon, pLatDouble, pLonDouble)
        val distDToD = poolCalculateDistance(pool.dropLat, pool.dropLon, dLatDouble, dLonDouble)
        val detourCost = (distPToP + distDToD) * 15.0

        val totalWeight = pool.weightKg + wKg
        val baseCost = pool.totalPayment
        val shareB = Math.round(((baseCost * (wKg / totalWeight)) + detourCost) * 100.0) / 100.0

        var isProcessing by remember { mutableStateOf(false) }
        var paymentSuccess by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf("") }
        var typedConsent by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { if(!isProcessing && !paymentSuccess) showInvoice = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x44FF0000), Color.Transparent))), contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp).shadow(24.dp, RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedVisibility(visible = !paymentSuccess, enter = fadeIn(), exit = fadeOut()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Split Invoice", fontSize = 22.sp, fontWeight = FontWeight.Black, color = TerracottaPrimary)
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Proportional Share", color = Color.Gray); Text("₹${String.format("%.2f", baseCost * (wKg / totalWeight))}", fontWeight = FontWeight.Bold) }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Detour Cost", color = Color.Gray); Text("₹${String.format("%.2f", detourCost)}", fontWeight = FontWeight.Bold) }
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Total to Escrow", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("₹${String.format("%.2f", shareB)}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = TerracottaPrimary) }

                                Spacer(modifier = Modifier.height(24.dp))
                                OutlinedTextField(
                                    value = typedConsent, onValueChange = { typedConsent = it.uppercase() },
                                    placeholder = { Text("Type 'ACCEPT' to confirm", color = Color.Gray) }, modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedBorderColor = if(typedConsent == "ACCEPT") Color(0xFF4CAF50) else TerracottaPrimary, unfocusedBorderColor = Color.LightGray)
                                )
                                if (errorMessage.isNotEmpty()) { Spacer(modifier = Modifier.height(8.dp)); Text(errorMessage, color = Color.Red, fontSize = 12.sp, textAlign = TextAlign.Center) }
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = {
                                        isProcessing = true; errorMessage = ""
                                        coroutineScope.launch {
                                            try {
                                                val req = JoinPoolRequest(poolId = pool.orderId, farmerEmail = userEmail, cropName = cropName, weightKg = wKg, pickupAddress = pickupAddress, dropAddress = dropAddress, pickupLat = pLatDouble, pickupLon = pLonDouble, dropLat = dLatDouble, dropLon = dLonDouble)
                                                val res = RetrofitClient.apiService.joinPool(req)
                                                if (res.isSuccessful && res.body()?.success == true) {
                                                    paymentSuccess = true
                                                    val invoiceText = "GroWise Crop Pool Invoice\n\nOrder ID: ${pool.orderId}\nCo-Loader: $userEmail\nCrop: $cropName\nWeight: $weightToPool kg\nDetour Applied: ${String.format("%.2f", distPToP + distDToD)} km\n\nTotal Escrow Paid: Rs. ${String.format("%.2f", shareB)}\nDate: ${Date()}"
                                                    generatePdfAndShare(context, invoiceText)
                                                } else { errorMessage = res.body()?.error ?: "Insufficient wallet balance." }
                                            } catch (e: Exception) { errorMessage = "Network Error" } finally { isProcessing = false }
                                        }
                                    },
                                    enabled = !isProcessing && typedConsent == "ACCEPT", colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(24.dp)
                                ) {
                                    if(isProcessing) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp)) else Text("Pay & Join Pool", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        AnimatedVisibility(visible = paymentSuccess, enter = scaleIn() + fadeIn()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(modifier = Modifier.size(80.dp).background(Color(0xFF4CAF50).copy(alpha=0.2f), CircleShape).border(4.dp, Color(0xFF4CAF50), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(50.dp)) }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Joined Pool Successfully!", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(onClick = { showInvoice = false; onSuccess() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(24.dp)) { Text("Done", color = Color.White) }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun generatePdfAndShare(context: Context, invoiceDetails: String) {
    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(400, 600, 1).create()
    val page = document.startPage(pageInfo)
    val paint = Paint().apply { textSize = 14f }
    var y = 40f
    invoiceDetails.split("\n").forEach { line -> page.canvas.drawText(line, 20f, y, paint); y += 20f }
    document.finishPage(page)
    try {
        val fileName = "GroWise_Invoice_${System.currentTimeMillis()}.pdf"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out -> document.writeTo(out) }
            Toast.makeText(context, "Invoice Saved to Downloads", Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) { e.printStackTrace() } finally { document.close() }
}