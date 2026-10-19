package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.ChatRepository
import com.example.model.Conversation
import com.example.model.UserProfile
import com.example.ui.theme.NexusBlue
import com.example.ui.theme.NexusCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NewConversationDialog(
    currentUserId: String,
    chatRepository: ChatRepository,
    onDismiss: () -> Unit,
    onConversationCreated: (Conversation) -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Direct Chat, 1: Create Group

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    var groupTitle by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    // Search users by username debounce
    LaunchedEffect(searchQuery) {
        val trimmed = searchQuery.trim()
        if (trimmed.length >= 2) {
            delay(250) // Debounce
            isSearching = true
            try {
                searchResults = chatRepository.searchUserByUsername(trimmed)
            } catch (e: Exception) {
                searchResults = emptyList()
            }
            isSearching = false
        } else {
            searchResults = emptyList()
            isSearching = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = {
            Text(
                text = if (selectedTab == 0) "Find User & Start Chat" else "Create Group Room",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = NexusCyan
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Direct Chat") },
                        icon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("New Group") },
                        icon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    // Direct Chat Tab
                    Text(
                        text = "Search by username (@handle) or name:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("e.g. @proobaidur or Alex") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = NexusCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = NexusCyan,
                                    strokeWidth = 2.dp
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NexusCyan
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("direct_chat_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search Results List
                    if (searchResults.isNotEmpty()) {
                        Text(
                            text = "Found users (${searchResults.size}):",
                            style = MaterialTheme.typography.labelSmall,
                            color = NexusCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyColumn(modifier = Modifier.height(150.dp)) {
                            items(searchResults, key = { it.uid }) { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .clickable(enabled = !isCreating) {
                                            isCreating = true
                                            scope.launch {
                                                try {
                                                    val conv = chatRepository.createDirectConversation(user)
                                                    isCreating = false
                                                    onConversationCreated(conv)
                                                } catch (e: Exception) {
                                                    isCreating = false
                                                    val conv = chatRepository.createDirectConversation(user.displayName)
                                                    onConversationCreated(conv)
                                                }
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                        .testTag("user_result_${user.username}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (user.photoUrl.isBlank()) NexusBlue else Color.Transparent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (user.photoUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = user.photoUrl,
                                                contentDescription = "Avatar",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                text = user.displayName.take(1).uppercase().ifBlank { "U" },
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = user.displayName.ifBlank { user.username },
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (user.username.isNotBlank()) {
                                            Text(
                                                text = "@${user.username}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = NexusCyan
                                                )
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Chat",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = NexusCyan
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    } else if (searchQuery.trim().length >= 2 && !isSearching) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "No user found with username \"$searchQuery\". You can still start a direct chat with this name.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "Tip: Users can be found instantly by typing their @username.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    // Create Group Tab
                    Text(
                        text = "Enter a name for your new group room:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = groupTitle,
                        onValueChange = { groupTitle = it },
                        label = { Text("Group Name") },
                        placeholder = { Text("e.g. Nexus Core Team, Friends Lounge") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NexusCyan
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("group_title_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Multi-user rooms sync messages instantly with Firestore.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                if (isCreating) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = NexusCyan,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                TextButton(
                    onClick = {
                        val cleanName = searchQuery.trim().removePrefix("@")
                        if (cleanName.isNotBlank() && !isCreating) {
                            isCreating = true
                            scope.launch {
                                try {
                                    val conv = chatRepository.createDirectConversation(cleanName)
                                    isCreating = false
                                    onConversationCreated(conv)
                                } catch (e: Exception) {
                                    isCreating = false
                                    val safeHash = kotlin.math.abs(cleanName.hashCode())
                                    val fallbackConv = Conversation(
                                        id = "dm_${currentUserId}_${safeHash}",
                                        title = cleanName,
                                        isGroup = false,
                                        memberUids = listOf(currentUserId, "usr_$safeHash"),
                                        memberNames = mapOf(
                                            currentUserId to (chatRepository.auth.currentUser?.displayName ?: "User"),
                                            "usr_$safeHash" to cleanName
                                        ),
                                        lastMessageText = "Conversation started",
                                        lastMessageTimestamp = System.currentTimeMillis()
                                    )
                                    onConversationCreated(fallbackConv)
                                }
                            }
                        }
                    },
                    enabled = searchQuery.isNotBlank() && !isCreating,
                    modifier = Modifier.testTag("create_direct_chat_button")
                ) {
                    Text("Start Chat", color = NexusCyan, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(
                    onClick = {
                        val cleanTitle = groupTitle.trim()
                        if (cleanTitle.isNotBlank() && !isCreating) {
                            isCreating = true
                            scope.launch {
                                try {
                                    val group = chatRepository.createGroupConversation(
                                        title = cleanTitle,
                                        memberUids = emptyList(),
                                        memberNames = emptyMap()
                                    )
                                    isCreating = false
                                    onConversationCreated(group)
                                } catch (e: Exception) {
                                    isCreating = false
                                    val fallbackGroup = Conversation(
                                        id = "group_${System.currentTimeMillis()}",
                                        title = cleanTitle,
                                        isGroup = true,
                                        memberUids = listOf(currentUserId),
                                        memberNames = mapOf(currentUserId to (chatRepository.auth.currentUser?.displayName ?: "User")),
                                        lastMessageText = "Group created",
                                        lastMessageTimestamp = System.currentTimeMillis()
                                    )
                                    onConversationCreated(fallbackGroup)
                                }
                            }
                        }
                    },
                    enabled = groupTitle.isNotBlank() && !isCreating,
                    modifier = Modifier.testTag("create_group_button")
                ) {
                    Text("Create Group", color = NexusCyan, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCreating
            ) {
                Text("Cancel")
            }
        }
    )
}
