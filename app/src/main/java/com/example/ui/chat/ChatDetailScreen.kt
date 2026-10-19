package com.example.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChatRepository
import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.ui.theme.BubbleIncomingDark
import com.example.ui.theme.BubbleOutgoing
import com.example.ui.theme.NexusBlue
import com.example.ui.theme.NexusCyan
import com.example.ui.theme.NexusPurple
import com.example.ui.theme.StatusOnline
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatDetailScreen(
    conversation: Conversation,
    currentUserId: String,
    chatRepository: ChatRepository,
    onBackClick: () -> Unit,
    onMessageSent: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val messages by chatRepository.observeMessages(conversation.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val typingMap by chatRepository.observeTypingStatus(conversation.id)
        .collectAsStateWithLifecycle(initialValue = emptyMap())

    val partnerUid = remember(conversation.memberUids, currentUserId) {
        conversation.memberUids.firstOrNull { it != currentUserId } ?: ""
    }
    val partnerProfile by chatRepository.observeUserProfile(partnerUid)
        .collectAsStateWithLifecycle(initialValue = null)
    val isPartnerOnline by chatRepository.observePresence(partnerUid)
        .collectAsStateWithLifecycle(initialValue = false)
        
    LaunchedEffect(partnerUid, partnerProfile) {
        android.util.Log.d("ChatDetailScreen", "DEBUG: partnerUid: $partnerUid, partnerProfile: $partnerProfile, conversationId: ${conversation.id}, memberUids: ${conversation.memberUids}, currentUserId: $currentUserId")
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var showEmojiBar by remember { mutableStateOf(false) }
    var selectedMessageForReaction by remember { mutableStateOf<String?>(null) }
    var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }

    // Other users currently typing
    val activeTypingUsers = typingMap.filter { it.key != currentUserId && it.value }.keys

    var isCurrentlyTyping by remember { mutableStateOf(false) }

    LaunchedEffect(inputText) {
        val typing = inputText.isNotBlank()
        if (typing != isCurrentlyTyping) {
            isCurrentlyTyping = typing
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    chatRepository.setTyping(conversation.id, typing)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    // Reset typing status when leaving screen
    DisposableEffect(conversation.id) {
        onDispose {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    chatRepository.setTyping(conversation.id, false)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    // Auto scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .testTag("chat_detail_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Bottom Input Section (Emoji bar + Input field)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
            ) {
                // Emoji Quick Bar
                AnimatedVisibility(visible = showEmojiBar) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        listOf("😊", "🚀", "⚡", "🙌", "✨", "💯", "👋", "💬").forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 24.sp,
                                modifier = Modifier
                                    .clickable {
                                        inputText += emoji
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                }

                // Bottom Input Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Input Container
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showEmojiBar = !showEmojiBar },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEmotions,
                                    contentDescription = "Emoji",
                                    tint = if (showEmojiBar) NexusCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Box(modifier = Modifier.weight(1f)) {
                                if (inputText.isEmpty()) {
                                    Text(
                                        text = "Type a message...",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                BasicTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp
                                    ),
                                    maxLines = 4,
                                    cursorBrush = SolidColor(NexusCyan),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = {
                                        if (inputText.isNotBlank()) {
                                            val textToSend = inputText
                                            inputText = ""
                                            isCurrentlyTyping = false
                                            onMessageSent?.invoke(textToSend)
                                            scope.launch {
                                                try {
                                                    chatRepository.setTyping(conversation.id, false)
                                                    chatRepository.sendMessage(conversation.id, textToSend)
                                                } catch (e: Exception) {
                                                    // Prevent crash on sending
                                                }
                                            }
                                        }
                                    }),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("chat_message_input")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Send Button
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank()) NexusBlue else NexusBlue.copy(alpha = 0.5f))
                            .clickable(enabled = inputText.isNotBlank()) {
                                val textToSend = inputText
                                inputText = ""
                                isCurrentlyTyping = false
                                onMessageSent?.invoke(textToSend)
                                scope.launch {
                                    try {
                                        chatRepository.setTyping(conversation.id, false)
                                        chatRepository.sendMessage(conversation.id, textToSend)
                                    } catch (e: Exception) {
                                        // Prevent crash on sending
                                    }
                                }
                            }
                            .testTag("send_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag("chat_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Avatar
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (conversation.isGroup) NexusPurple else NexusBlue),
                    contentAlignment = Alignment.Center
                ) {
                    if (conversation.isGroup) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        if (partnerProfile != null) {
                            com.example.ui.components.AvatarDisplay(
                                avatarId = partnerProfile!!.avatarId,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val cleanTitle = if (conversation.title.contains("@")) {
                                conversation.title.substringBefore("@").replaceFirstChar { it.uppercase() }
                            } else {
                                conversation.title
                            }
                            Text(
                                text = cleanTitle.take(2).uppercase().ifBlank { "NC" },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val cleanTitle = if (conversation.title.contains("@")) {
                        conversation.title.substringBefore("@").replaceFirstChar { it.uppercase() }
                    } else {
                        conversation.title
                    }
                    Text(
                        text = cleanTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    val statusText = when {
                        activeTypingUsers.isNotEmpty() -> "typing..."
                        conversation.isGroup -> "${conversation.memberUids.size} members"
                        isPartnerOnline -> "Online"
                        else -> "Offline"
                    }
                    val statusColor = when {
                        activeTypingUsers.isNotEmpty() -> NexusCyan
                        conversation.isGroup -> MaterialTheme.colorScheme.onSurfaceVariant
                        isPartnerOnline -> StatusOnline
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!conversation.isGroup && activeTypingUsers.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            color = statusColor
                        )
                    }
                }
            }

            // Messages List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(10.dp)) }

                    items(messages, key = { it.id }) { message ->
                        val isOutgoing = message.senderUid == currentUserId
                        MessageBubble(
                            message = message,
                            isOutgoing = isOutgoing,
                            isGroup = conversation.isGroup,
                            onMessageLongClick = { selectedMessageForReaction = message.id }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                // Outside tap dismissal barrier + Reaction Picker & Delete Overlay
                selectedMessageForReaction?.let { msgId ->
                    val targetMessage = messages.firstOrNull { it.id == msgId }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                selectedMessageForReaction = null
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .clickable(enabled = false) {} // Consume clicks inside
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Message Options",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf("❤️", "👍", "🔥", "😂", "🎉", "👏").forEach { emoji ->
                                        val isSelected = targetMessage?.reaction == emoji
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) NexusCyan.copy(alpha = 0.25f) else Color.Transparent)
                                                .border(
                                                    width = if (isSelected) 1.5.dp else 0.dp,
                                                    color = if (isSelected) NexusCyan else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    scope.launch {
                                                        val newReaction = if (isSelected) "" else emoji
                                                        chatRepository.addReaction(conversation.id, msgId, newReaction)
                                                        selectedMessageForReaction = null
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = emoji, fontSize = 22.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Delete Message Button
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            messageToDelete = targetMessage
                                            selectedMessageForReaction = null
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Delete Message",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Delete Confirmation Dialog
            messageToDelete?.let { msg ->
                AlertDialog(
                    onDismissRequest = { messageToDelete = null },
                    title = { Text("Delete Message?") },
                    text = { Text("Are you sure you want to delete this message? This action cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val idToDelete = msg.id
                                messageToDelete = null
                                scope.launch {
                                    try {
                                        chatRepository.deleteMessage(conversation.id, idToDelete)
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            }
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { messageToDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isOutgoing: Boolean,
    isGroup: Boolean,
    onMessageLongClick: () -> Unit
) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(message.timestamp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isOutgoing) 16.dp else 4.dp,
                        bottomEnd = if (isOutgoing) 4.dp else 16.dp
                    )
                )
                .background(if (isOutgoing) BubbleOutgoing else BubbleIncomingDark)
                .pointerInput(message.id) {
                    detectTapGestures(
                        onLongPress = { onMessageLongClick() }
                    )
                }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                if (!isOutgoing && isGroup && message.senderName.isNotBlank()) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = NexusCyan
                        ),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        color = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    )
                    if (isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Delivered",
                            tint = NexusCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Reaction display
        if (message.reaction.isNotBlank()) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp, start = if (!isOutgoing) 8.dp else 0.dp, end = if (isOutgoing) 8.dp else 0.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = message.reaction, fontSize = 12.sp)
            }
        }
    }
}
