package com.simats.growise.common

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.simats.growise.data.model.Transaction
import com.simats.growise.ui.theme.GoldenYellow
import com.simats.growise.ui.theme.PeachBackground
import com.simats.growise.ui.theme.TerracottaPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(userEmail: String, role: String, navController: NavController) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    var walletBalance by remember { mutableDoubleStateOf(0.0) }
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var showAddMoneyDialog by remember { mutableStateOf(false) }
    var showSuccessPopup by remember { mutableStateOf(false) }
    var popupMessage by remember { mutableStateOf("Payment Successful!") }
    var amountInput by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var driverOrderHistory by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var driverStandardOrders by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var driverPoolOrders by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var selectedOrder by remember { mutableStateOf<Map<String, Any>?>(null) }

    // Date Filter State

    // Date Filter State
    var selectedDate by remember { mutableStateOf<Date?>(null) }
    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.set(year, month, dayOfMonth)
            selectedDate = cal.time
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    // Driver specific Tab State
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    fun fetchData() {
        db.collection("wallets").document(userEmail).get().addOnSuccessListener { snap ->
            walletBalance = snap?.getDouble("balance") ?: 0.0
        }
        db.collection("transactions").whereEqualTo("email", userEmail)
            .get().addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { doc ->
                    Transaction(
                        title = doc.getString("title") ?: "",
                        amount = doc.getDouble("amount") ?: 0.0,
                        isCredit = doc.getBoolean("isCredit") ?: false,
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        type = doc.getString("type") ?: "UNKNOWN"
                    )
                }
                transactions = list.sortedByDescending { it.timestamp }
            }

        if (role == "driver") {
            db.collection("orders").whereEqualTo("driverEmail", userEmail).whereEqualTo("status", "DELIVERED")
                .get().addOnSuccessListener { snap ->
                    driverStandardOrders = snap.documents.mapNotNull { it.data }
                    driverOrderHistory = (driverStandardOrders + driverPoolOrders).sortedByDescending { it["timestamp"] as? Long ?: 0L }
                }
            db.collection("crop_pools_master").whereEqualTo("driverEmail", userEmail).whereEqualTo("status", "DELIVERED")
                .get().addOnSuccessListener { snap ->
                    driverPoolOrders = snap.documents.mapNotNull { it.data }
                    driverOrderHistory = (driverStandardOrders + driverPoolOrders).sortedByDescending { it["timestamp"] as? Long ?: 0L }
                }
        }
    }

    LaunchedEffect(userEmail) {
        db.collection("wallets").document(userEmail)
            .addSnapshotListener { snap, _ -> walletBalance = snap?.getDouble("balance") ?: 0.0 }
        db.collection("transactions").whereEqualTo("email", userEmail)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val list = snap.documents.mapNotNull { doc ->
                        Transaction(
                            title = doc.getString("title") ?: "",
                            amount = doc.getDouble("amount") ?: 0.0,
                            isCredit = doc.getBoolean("isCredit") ?: false,
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            type = doc.getString("type") ?: "UNKNOWN"
                        )
                    }
                    transactions = list.sortedByDescending { it.timestamp }
                }
            }
        if (role == "driver") {
            db.collection("orders").whereEqualTo("driverEmail", userEmail).whereEqualTo("status", "DELIVERED")
                .addSnapshotListener { snap, _ ->
                    if (snap != null) {
                        driverStandardOrders = snap.documents.mapNotNull { it.data }
                        driverOrderHistory = (driverStandardOrders + driverPoolOrders).sortedByDescending { it["timestamp"] as? Long ?: 0L }
                    }
                }
            db.collection("crop_pools_master").whereEqualTo("driverEmail", userEmail).whereEqualTo("status", "DELIVERED")
                .addSnapshotListener { snap, _ ->
                    if (snap != null) {
                        driverPoolOrders = snap.documents.mapNotNull { it.data }
                        driverOrderHistory = (driverStandardOrders + driverPoolOrders).sortedByDescending { it["timestamp"] as? Long ?: 0L }
                    }
                }
        }
    }

    val filteredTransactions = if (selectedDate != null) {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val selectedDateStr = dateFormat.format(selectedDate!!)
        transactions.filter { dateFormat.format(Date(it.timestamp)) == selectedDateStr }
    } else transactions

    val filteredDriverOrders = if (selectedDate != null) {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val selectedDateStr = dateFormat.format(selectedDate!!)
        driverOrderHistory.filter {
            dateFormat.format(
                Date(
                    it["timestamp"] as? Long ?: 0L
                )
            ) == selectedDateStr
        }
    } else driverOrderHistory

    Scaffold(
        containerColor = PeachBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (role == "driver") "Driver Hub" else "GroWise Wallet",
                        fontWeight = FontWeight.ExtraBold,
                        color = TerracottaPrimary
                    )
                },
                navigationIcon = {
                    if (role != "driver") {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TerracottaPrimary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            isRefreshing = true; fetchData(); delay(500); isRefreshing = false
                            Toast.makeText(context, "Wallet Synced", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = TerracottaPrimary
                        )
                    }
                    if (selectedDate != null) {
                        TextButton(
                            onClick = { selectedDate = null },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Clear", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = { datePickerDialog.show() }) {
                        Icon(
                            Icons.Filled.DateRange,
                            contentDescription = "Filter Date",
                            tint = TerracottaPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PeachBackground)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
            ) {

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = TerracottaPrimary),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Available Balance",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "₹${String.format(Locale.US, "%.2f", walletBalance)}",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 42.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (role == "user" || role == "farmer") {
                                Button(
                                    onClick = { showAddMoneyDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldenYellow),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "Add",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Add",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            Button(
                                onClick = { showWithdrawDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    Icons.Filled.ArrowUpward,
                                    contentDescription = "Withdraw",
                                    tint = TerracottaPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Withdraw",
                                    color = TerracottaPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (role == "driver") {
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = PeachBackground,
                        contentColor = TerracottaPrimary,
                        indicator = { tabPositions ->
                            TabRowDefaults.Indicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                height = 3.dp,
                                color = TerracottaPrimary
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("Wallet Logs", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("Trip History", fontWeight = FontWeight.Bold) }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Transaction History",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                        if (selectedDate != null) {
                            Text(
                                SimpleDateFormat("dd MMM yy", Locale.getDefault()).format(
                                    selectedDate!!
                                ),
                                fontSize = 12.sp,
                                color = TerracottaPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (role != "driver" || selectedTabIndex == 0) {
                    if (filteredTransactions.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.AccountBalanceWallet,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = TerracottaPrimary.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No Transactions Yet",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color.DarkGray
                                )
                                Text(
                                    if (selectedDate == null) "Your wallet is fresh and clean." else "No activity found for this date.",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredTransactions) { txn ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, GoldenYellow.copy(alpha = 0.5f)),
                                    elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // STRICT LOGIC: isCredit TRUE = Income (+ Green), FALSE = Outgo (- Red)
                                        val isEscrow = txn.type == "ESCROW_LOCK"
                                        val isIncome = txn.isCredit == true

                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isEscrow) GoldenYellow.copy(alpha = 0.2f)
                                                    else if (isIncome) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isEscrow) Icons.Filled.HourglassEmpty
                                                else if (isIncome) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                                                contentDescription = null,
                                                tint = if (isEscrow) GoldenYellow
                                                else if (isIncome) Color(0xFF2E7D32) else Color.Red
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                if (isEscrow) "Escrow Payment - Pending" else txn.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = if (isEscrow) GoldenYellow else Color.DarkGray
                                            )
                                            Text(
                                                SimpleDateFormat(
                                                    "MMM dd, yyyy • hh:mm a",
                                                    Locale.getDefault()
                                                ).format(Date(txn.timestamp)),
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        val amtStr = String.format(Locale.US, "%.0f", txn.amount)
                                        Text(
                                            text = if (isEscrow) "- ₹$amtStr"
                                            else "${if (isIncome) "+" else "-"} ₹$amtStr",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            color = if (isEscrow) GoldenYellow
                                            else if (isIncome) Color(0xFF2E7D32) else Color.Red
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (role == "driver" && selectedTabIndex == 1) {
                    if (filteredDriverOrders.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.LocalShipping,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = TerracottaPrimary.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No Completed Trips",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color.DarkGray
                                )
                                Text(
                                    if (selectedDate == null) "Your trip history will appear here." else "No trips found for this date.",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(
                                filteredDriverOrders,
                                key = { it["orderId"].toString() }) { order ->
                                val isPool = order.containsKey("hostEmail")
                                val orderIdStr = order["orderId"]?.toString() ?: ""
                                val isNgoRescue = order["orderType"] == "NGO_RESCUE" || order["isDonation"] == true || orderIdStr.startsWith("GW-DON-")
                                val crop = if (isNgoRescue) "Donation Rescue" else if (isPool) "Crop Pool" else "Standard Order"
                                val total = if (!isPool) {
                                    (order["transportFare"] as? Number)?.toDouble() ?: 0.0
                                } else {
                                    val tPay = (order["totalPayment"] as? Number)?.toDouble() ?: 0.0
                                    val cPay = (order["coLoaderPayment"] as? Number)?.toDouble() ?: 0.0
                                    tPay + cPay
                                }
                                val date = SimpleDateFormat(
                                    "dd MMM yyyy",
                                    Locale.getDefault()
                                ).format(
                                    Date(
                                        order["timestamp"] as? Long ?: System.currentTimeMillis()
                                    )
                                )
                                val orderId = order["orderId"] as? String ?: ""

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                        .clickable { selectedOrder = order },
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, GoldenYellow.copy(alpha = 0.5f)),
                                    elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(50.dp)
                                                .background(PeachBackground, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Filled.LocalShipping,
                                                contentDescription = null,
                                                tint = TerracottaPrimary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                crop,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 16.sp,
                                                color = Color.Black
                                            )
                                            Text(
                                                "Delivered on $date",
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        Text(
                                            "₹${String.format(Locale.US, "%.0f", total)}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = Color(0xFF2E7D32)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            Icons.Filled.ChevronRight,
                                            contentDescription = "View Details",
                                            tint = Color.LightGray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // WITHDRAW FUNDS DIALOG
            if (showWithdrawDialog) {
                Dialog(onDismissRequest = { showWithdrawDialog = false; amountInput = "" }) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Withdraw to Bank",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TerracottaPrimary
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            OutlinedTextField(
                                value = amountInput,
                                onValueChange = { amountInput = it },
                                label = { Text("Enter Amount (₹)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.CurrencyRupee,
                                        contentDescription = null,
                                        tint = TerracottaPrimary
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = TerracottaPrimary,
                                    focusedLabelColor = TerracottaPrimary
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    val amount = amountInput.toDoubleOrNull() ?: 0.0
                                    if (amount > 0 && amount <= walletBalance) {
                                        val newBal = walletBalance - amount
                                        db.collection("wallets").document(userEmail)
                                            .set(mapOf("balance" to newBal), SetOptions.merge())

                                        val txMap = hashMapOf(
                                            "email" to userEmail,
                                            "title" to "Withdrawn to Bank",
                                            "amount" to amount,
                                            "isCredit" to false,
                                            "timestamp" to System.currentTimeMillis()
                                        )
                                        db.collection("transactions").add(txMap)

                                        showWithdrawDialog = false
                                        amountInput = ""
                                        popupMessage = "Withdrawal Processed!"
                                        showSuccessPopup = true
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Invalid Amount or Insufficient Balance",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)
                            ) {
                                Text(
                                    "Withdraw Funds",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // ADD MONEY DIALOG
            if (showAddMoneyDialog) {
                Dialog(onDismissRequest = { showAddMoneyDialog = false; amountInput = "" }) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Top Up Wallet",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TerracottaPrimary
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            OutlinedTextField(
                                value = amountInput,
                                onValueChange = { amountInput = it },
                                label = { Text("Enter Amount (₹)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.CurrencyRupee,
                                        contentDescription = null,
                                        tint = TerracottaPrimary
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = TerracottaPrimary,
                                    focusedLabelColor = TerracottaPrimary
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    val amount = amountInput.toDoubleOrNull() ?: 0.0
                                    if (amount > 0) {
                                        val newBal = walletBalance + amount
                                        db.collection("wallets").document(userEmail)
                                            .set(mapOf("balance" to newBal), SetOptions.merge())

                                        val txMap = hashMapOf(
                                            "email" to userEmail,
                                            "title" to "Added via Bank",
                                            "amount" to amount,
                                            "isCredit" to true,
                                            "timestamp" to System.currentTimeMillis()
                                        )
                                        db.collection("transactions").add(txMap)

                                        showAddMoneyDialog = false
                                        amountInput = ""
                                        popupMessage = "Money Added Successfully!"
                                        showSuccessPopup = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)
                            ) {
                                Text(
                                    "Securely Add Money",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showSuccessPopup,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Dialog(onDismissRequest = { showSuccessPopup = false }) {
                    Card(
                        modifier = Modifier.size(240.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(Color(0xFF4CAF50), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Success",
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                popupMessage,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = Color(0xFF2E7D32),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                LaunchedEffect(showSuccessPopup) {
                    if (showSuccessPopup) {
                        delay(2000); showSuccessPopup = false
                    }
                }
            }
        }

        // Driver Trip Detail Dialog (Premium Tax Invoice ported from OrderHistoryScreen)
        if (selectedOrder != null && role == "driver") {
            Dialog(onDismissRequest = { selectedOrder = null }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(16.dp)
                ) {
                    val isNgoRescue = selectedOrder!!["orderType"] == "NGO_RESCUE" || selectedOrder!!["isDonation"] == true
                    Column(modifier = Modifier
                        .padding(24.dp)
                        .fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (isNgoRescue) "DONATION RECORD" else "TAX INVOICE",
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp,
                                color = TerracottaPrimary,
                                letterSpacing = 2.sp
                            )
                            IconButton(onClick = {
                                selectedOrder = null
                            }) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                        }

                        Divider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color(0xFFF0F0F0)
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF9F9F9), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        "Delivery Partner",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.LocalShipping,
                                            contentDescription = null,
                                            tint = TerracottaPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))

                                        var realDriverName by remember { mutableStateOf("Fetching...") }
                                        var fetchedDriverId by remember { mutableStateOf("GW-D...") }
                                        LaunchedEffect(selectedOrder!!["driverEmail"]) {
                                            db.collection("users")
                                                .document(selectedOrder!!["driverEmail"].toString())
                                                .get().addOnSuccessListener { dSnap ->
                                                realDriverName = dSnap.getString("name")
                                                    ?: selectedOrder!!["driverName"].toString()
                                                fetchedDriverId =
                                                    dSnap.getString("driverId") ?: "GW-D0000"
                                            }
                                        }
                                        Text(
                                            "$realDriverName (ID: $fetchedDriverId)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.DarkGray
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Delivered: ${
                                            SimpleDateFormat(
                                                "dd MMM yyyy, hh:mm a",
                                                Locale.getDefault()
                                            ).format(Date(selectedOrder!!["timestamp"] as? Long ?: System.currentTimeMillis()))
                                        }", fontSize = 12.sp, color = Color.Gray
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            val isPool = selectedOrder!!.containsKey("hostEmail")
                            val orderId = selectedOrder!!["orderId"] as? String ?: ""
                            Text("Order ID: $orderId", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TerracottaPrimary)
                            Spacer(modifier = Modifier.height(8.dp))

                            if (!isPool) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Filled.Circle, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(12.dp).padding(top = 4.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Pickup (Farmer: ${selectedOrder!!["farmerEmail"]})", fontSize = 11.sp, color = Color.Gray)
                                        Text("${selectedOrder!!["pickupAddress"]}", fontSize = 13.sp, color = Color.Black)
                                    }
                                }
                                Box(modifier = Modifier.padding(start = 5.dp, top = 4.dp, bottom = 4.dp).width(2.dp).height(20.dp).background(Color.LightGray))
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = TerracottaPrimary, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text("Drop-off (Buyer: ${selectedOrder!!["userEmail"]})", fontSize = 11.sp, color = Color.Gray)
                                        Text("${selectedOrder!!["dropAddress"]}", fontSize = 13.sp, color = Color.Black)
                                    }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Filled.Circle, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(12.dp).padding(top = 4.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Host Pickup (${selectedOrder!!["hostEmail"]})", fontSize = 11.sp, color = Color.Gray)
                                        Text("${selectedOrder!!["pickupAddress"]}", fontSize = 13.sp, color = Color.Black)
                                    }
                                }
                                if (selectedOrder!!.containsKey("coLoaderEmail") && selectedOrder!!["coLoaderEmail"] != null) {
                                    Box(modifier = Modifier.padding(start = 5.dp, top = 4.dp, bottom = 4.dp).width(2.dp).height(20.dp).background(Color.LightGray))
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(Icons.Filled.Circle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(12.dp).padding(top = 4.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Co-Loader Pickup (${selectedOrder!!["coLoaderEmail"]})", fontSize = 11.sp, color = Color(0xFF2E7D32))
                                            Text("${selectedOrder!!["coLoaderPickupAddress"]}", fontSize = 13.sp, color = Color.Black)
                                        }
                                    }
                                }
                                Box(modifier = Modifier.padding(start = 5.dp, top = 4.dp, bottom = 4.dp).width(2.dp).height(20.dp).background(Color.LightGray))
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = TerracottaPrimary, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text("Host Drop", fontSize = 11.sp, color = Color.Gray)
                                        Text("${selectedOrder!!["dropAddress"]}", fontSize = 13.sp, color = Color.Black)
                                    }
                                }
                                if (selectedOrder!!.containsKey("coLoaderEmail") && selectedOrder!!["coLoaderEmail"] != null) {
                                    Box(modifier = Modifier.padding(start = 5.dp, top = 4.dp, bottom = 4.dp).width(2.dp).height(20.dp).background(Color.LightGray))
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp).padding(top = 2.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text("Co-Loader Drop", fontSize = 11.sp, color = Color(0xFF2E7D32))
                                            Text("${selectedOrder!!["coLoaderDropAddress"]}", fontSize = 13.sp, color = Color.Black)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(if (isNgoRescue) "DONATION SUMMARY" else "BILL SUMMARY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))

                            if (!isPool) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${selectedOrder!!["cropName"]} (${selectedOrder!!["weightKg"]} kg)", fontSize = 14.sp, color = Color.DarkGray)
                                    if (isNgoRescue) {
                                        Text("Donated", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                    } else {
                                        Text("₹${selectedOrder!!["cropValue"]}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Transport Fare (${selectedOrder!!["distanceKm"]} km)", fontSize = 14.sp, color = Color.DarkGray)
                                    Text("₹${selectedOrder!!["transportFare"]}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                                Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF0F0F0))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total Earned", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                                    Text("₹${selectedOrder!!["transportFare"]}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
                                }
                            } else {
                                val tPayment = (selectedOrder!!["totalPayment"] as? Number)?.toDouble() ?: 0.0
                                val cPayment = (selectedOrder!!["coLoaderPayment"] as? Number)?.toDouble() ?: 0.0

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Host Fare Contribution", fontSize = 14.sp, color = Color.DarkGray)
                                    Text("₹${String.format("%.2f", tPayment)}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                                if (selectedOrder!!.containsKey("coLoaderEmail") && selectedOrder!!["coLoaderEmail"] != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Co-Loader Fare Contribution", fontSize = 14.sp, color = Color.DarkGray)
                                        Text("₹${String.format("%.2f", cPayment)}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                                Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF0F0F0))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total Earned", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary)
                                    Text("₹${String.format("%.2f", tPayment + cPayment)}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TerracottaPrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                generateAndSavePdf(context, selectedOrder!!)
                                selectedOrder = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)
                        ) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = "Download",
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Download PDF Invoice",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}