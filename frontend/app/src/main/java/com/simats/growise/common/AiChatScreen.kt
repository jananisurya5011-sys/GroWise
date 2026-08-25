package com.simats.growise.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.simats.growise.data.model.AiChatRequest
import com.simats.growise.data.model.AiHistoryMessage
import com.simats.growise.data.network.RetrofitClient
import com.simats.growise.ui.theme.GoldenYellow
import com.simats.growise.ui.theme.PeachBackground
import com.simats.growise.ui.theme.TerracottaPrimary
import com.simats.growise.ui.theme.TextDark
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(navController: NavController, userEmail: String, role: String) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messageText by remember { mutableStateOf("") }
    var chatHistory by remember { mutableStateOf(listOf<AiHistoryMessage>()) }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "GroWise AI",
                        fontWeight = FontWeight.ExtraBold,
                        color = TerracottaPrimary,
                        fontSize = 22.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TerracottaPrimary)
                    }
                },
                actions = {
                    // Language dropdown removed as part of local model migration
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PeachBackground)
            )
        },
        containerColor = PeachBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // Initial Greeting Bubble
                if (chatHistory.isEmpty()) {
                    item {
                        ChatBubble(
                            message = AiHistoryMessage(
                                role = "model",
                                parts = "Hello! I am your GroWise Agricultural Assistant. How can I help you today?"
                            )
                        )
                    }
                }

                items(chatHistory) { msg ->
                    ChatBubble(message = msg)
                }
                if (isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = TerracottaPrimary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Input Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color.White, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask about agriculture...", color = Color.Gray, fontSize = 14.sp) },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = PeachBackground,
                        unfocusedContainerColor = Color(0xFFFDFDFD),
                        focusedBorderColor = GoldenYellow,
                        unfocusedBorderColor = Color(0xFFEEEEEE),
                        focusedTextColor = TextDark,
                        unfocusedTextColor = TextDark
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                FloatingActionButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            val userMsg = messageText
                            messageText = ""

                            // Add user message to UI immediately
                            val newUserMsg = AiHistoryMessage(role = "user", parts = userMsg)
                            chatHistory = chatHistory + newUserMsg

                            isLoading = true

                            coroutineScope.launch {
                                listState.animateScrollToItem(if (chatHistory.isEmpty()) 0 else chatHistory.size - 1)
                                try {
                                    // Send current history context EXCLUDING the very last message we just typed
                                    val historyToPass = chatHistory.dropLast(1)

                                    val request = AiChatRequest(
                                        message = userMsg,
                                        history = historyToPass,
                                        role = role
                                    )
                                    val response = RetrofitClient.apiService.sendAiChatMessage(request)

                                    if (response.success && response.reply != null) {
                                        chatHistory = chatHistory + AiHistoryMessage(role = "model", parts = response.reply)
                                    } else {
                                        chatHistory = chatHistory + AiHistoryMessage(role = "model", parts = "Sorry, I couldn't process that.")
                                    }
                                } catch (e: Exception) {
                                    chatHistory = chatHistory + AiHistoryMessage(role = "model", parts = "Network error. Please try again.")
                                } finally {
                                    isLoading = false
                                    listState.animateScrollToItem(chatHistory.size - 1)
                                }
                            }
                        }
                    },
                    containerColor = GoldenYellow,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: AiHistoryMessage) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        val bubbleShape = RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = if (isUser) 20.dp else 4.dp,
            bottomEnd = if (isUser) 4.dp else 20.dp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .then(
                    if (isUser) {
                        Modifier
                            .shadow(2.dp, bubbleShape)
                            .background(TerracottaPrimary, bubbleShape)
                    } else {
                        Modifier
                            .shadow(1.dp, bubbleShape)
                            .background(Color.White, bubbleShape)
                            .border(BorderStroke(1.dp, GoldenYellow.copy(alpha = 0.5f)), bubbleShape)
                    }
                )
                .padding(16.dp)
        ) {
            Text(
                text = message.parts,
                color = if (isUser) Color.White else TextDark,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }
}