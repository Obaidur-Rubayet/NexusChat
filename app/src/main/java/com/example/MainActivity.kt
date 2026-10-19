package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AuthManager
import com.example.data.ChatRepository
import com.example.model.Conversation
import com.example.service.ChatSyncService
import com.example.ui.auth.SignInScreen
import com.example.ui.chat.ChatDetailScreen
import com.example.ui.chat.ConversationListScreen
import com.example.ui.components.NotificationData
import com.example.ui.components.TopNotificationBanner
import com.example.ui.profile.ProfileDialog
import com.example.ui.theme.NexusChatTheme
import com.example.util.NotificationHelper
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope

class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var chatRepository: ChatRepository
    private val pendingConversationId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationHelper.createNotificationChannel(this)

        pendingConversationId.value = intent.getStringExtra("conversation_id")

        authManager = AuthManager(this)
        chatRepository = ChatRepository(this)

        setContent {
            NexusChatTheme {
                NexusChatRoot(
                    authManager = authManager,
                    chatRepository = chatRepository,
                    pendingConversationId = pendingConversationId.value,
                    onClearPendingConversation = { pendingConversationId.value = null },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ChatSyncService.isAppInForeground = true
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) {
            ChatSyncService.start(this)
        }
    }

    override fun onPause() {
        super.onPause()
        ChatSyncService.isAppInForeground = false
        ChatSyncService.activeConversationId = null
    }

    override fun onStop() {
        super.onStop()
        ChatSyncService.isAppInForeground = false
        ChatSyncService.activeConversationId = null
    }

    override fun onDestroy() {
        super.onDestroy()
        ChatSyncService.isAppInForeground = false
        ChatSyncService.activeConversationId = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val convId = intent.getStringExtra("conversation_id")
        if (!convId.isNullOrBlank()) {
            pendingConversationId.value = convId
        }
    }
}

@Composable
fun NexusChatRoot(
    authManager: AuthManager,
    chatRepository: ChatRepository,
    pendingConversationId: String?,
    onClearPendingConversation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentUser by authManager.currentUser.collectAsStateWithLifecycle()

    if (currentUser == null) {
        SignInScreen(
            authManager = authManager,
            onAuthSuccess = { /* authStateListener automatically triggers recomposition */ },
            modifier = modifier
        )
    } else {
        NexusChatAuthenticated(
            currentUser = currentUser!!,
            authManager = authManager,
            chatRepository = chatRepository,
            pendingConversationId = pendingConversationId,
            onClearPendingConversation = onClearPendingConversation,
            modifier = modifier
        )
    }
}

@Composable
fun NexusChatAuthenticated(
    currentUser: FirebaseUser,
    authManager: AuthManager,
    chatRepository: ChatRepository,
    pendingConversationId: String?,
    onClearPendingConversation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedConversation by remember { mutableStateOf<Conversation?>(null) }
    var showProfileDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var topNotification by remember { mutableStateOf<NotificationData?>(null) }

    // Request Notification permission for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Permission granted or denied */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Start background message sync service
        ChatSyncService.start(context)
    }

    LaunchedEffect(selectedConversation?.id) {
        ChatSyncService.activeConversationId = selectedConversation?.id
    }

    val userProfile by chatRepository.observeUserProfile(currentUser.uid)
        .collectAsStateWithLifecycle(initialValue = null)

    val conversationsState by chatRepository.observeConversations(currentUser.uid)
        .collectAsStateWithLifecycle(initialValue = null)
    val conversations = conversationsState ?: emptyList()

    val appStartTime = remember { System.currentTimeMillis() }
    var isInitialConversationsLoaded by remember { mutableStateOf(false) }
    var lastSeenTimestamps by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

    // If a notification was clicked to open a specific conversation
    LaunchedEffect(pendingConversationId, conversations) {
        if (!pendingConversationId.isNullOrBlank() && conversations.isNotEmpty()) {
            val target = conversations.firstOrNull { it.id == pendingConversationId }
            if (target != null) {
                selectedConversation = target
                onClearPendingConversation()
            }
        }
    }

    LaunchedEffect(conversationsState) {
        val currentConversations = conversationsState ?: return@LaunchedEffect

        if (!isInitialConversationsLoaded) {
            // First time loading from Firestore: record all existing conversation timestamps so we don't notify on app start
            lastSeenTimestamps = currentConversations.associate { it.id to it.lastMessageTimestamp }
            isInitialConversationsLoaded = true
            return@LaunchedEffect
        }

        currentConversations.forEach { conv ->
            val prevTime = lastSeenTimestamps[conv.id]

            // A new message has arrived if timestamp advanced or brand-new conversation appeared
            val isNewMessage = if (prevTime == null) {
                conv.lastMessageTimestamp >= (appStartTime - 10000L) && conv.lastMessageText.isNotBlank()
            } else {
                conv.lastMessageTimestamp > prevTime && conv.lastMessageText.isNotBlank()
            }

            if (isNewMessage) {
                val isPlaceholder = conv.lastMessageText == "Conversation started" ||
                        conv.lastMessageText == "Group created"

                val isMyMessage = if (conv.lastMessageSenderUid.isNotBlank()) {
                    conv.lastMessageSenderUid == currentUser.uid
                } else {
                    val myName = userProfile?.displayName ?: currentUser.displayName ?: ""
                    myName.isNotBlank() && myName != "User" && conv.lastMessageSenderName == myName
                }

                val isCurrentChatOpen = selectedConversation?.id == conv.id

                if (!isPlaceholder && !isMyMessage && !isCurrentChatOpen) {
                    val cleanTitle = if (conv.isGroup) {
                        conv.title.ifBlank { "Group Room" }
                    } else {
                        val partner = conv.memberNames.entries.firstOrNull { it.key != currentUser.uid }?.value
                        val name = partner ?: conv.title
                        if (name.contains("@")) name.substringBefore("@") else name.ifBlank { "Direct Chat" }
                    }
                    val senderName = if (conv.lastMessageSenderName.contains("@")) {
                        conv.lastMessageSenderName.substringBefore("@")
                    } else conv.lastMessageSenderName.ifBlank { "Someone" }

                    val notif = NotificationData(
                        id = "${conv.id}_${conv.lastMessageTimestamp}",
                        title = cleanTitle,
                        message = "$senderName: ${conv.lastMessageText}",
                        conversationId = conv.id,
                        isSent = false
                    )
                    topNotification = notif
                    NotificationHelper.showMessageNotification(
                        context = context,
                        conversationId = conv.id,
                        title = cleanTitle,
                        message = "$senderName: ${conv.lastMessageText}",
                        notificationId = conv.id.hashCode()
                    )
                }
            }
        }
        lastSeenTimestamps = currentConversations.associate { it.id to it.lastMessageTimestamp }
        val prefs = context.getSharedPreferences("nexus_chat_sync_prefs", Context.MODE_PRIVATE)
        currentConversations.forEach { conv ->
            prefs.edit().putLong("seen_${conv.id}", conv.lastMessageTimestamp).apply()
        }
        prefs.edit().putBoolean("is_initialized", true).apply()
    }

    // Sync profile & online presence upon authenticated session start
    LaunchedEffect(currentUser.uid) {
        chatRepository.syncUserProfile(currentUser)
        chatRepository.updatePresence(true)
    }

    DisposableEffect(currentUser.uid) {
        onDispose {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    chatRepository.updatePresence(false)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (selectedConversation != null) {
            BackHandler {
                selectedConversation = null
            }
            ChatDetailScreen(
                conversation = selectedConversation!!,
                currentUserId = currentUser.uid,
                chatRepository = chatRepository,
                onBackClick = { selectedConversation = null },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            ConversationListScreen(
                currentUserId = currentUser.uid,
                chatRepository = chatRepository,
                onConversationClick = { conv -> selectedConversation = conv },
                onProfileClick = { showProfileDialog = true },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top Heads-Up Notification Banner
        TopNotificationBanner(
            notification = topNotification,
            onDismiss = { topNotification = null },
            onClick = { convId ->
                val targetConv = conversations.firstOrNull { it.id == convId }
                if (targetConv != null) {
                    selectedConversation = targetConv
                }
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    if (showProfileDialog) {
        ProfileDialog(
            userProfile = userProfile,
            currentUserId = currentUser.uid,
            authManager = authManager,
            chatRepository = chatRepository,
            onDismiss = { showProfileDialog = false },
            onSignedOut = {
                ChatSyncService.stop(context)
                showProfileDialog = false
                selectedConversation = null
            }
        )
    }
}
