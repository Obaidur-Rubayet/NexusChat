package com.example.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.AuthManager
import com.example.data.ChatRepository
import com.example.model.UserProfile
import com.example.ui.theme.NexusBlue
import com.example.ui.theme.NexusCyan
import com.example.ui.theme.StatusOnline
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProfileDialog(
    userProfile: UserProfile?,
    currentUserId: String,
    authManager: AuthManager,
    chatRepository: ChatRepository,
    onDismiss: () -> Unit,
    onSignedOut: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var displayName by remember { mutableStateOf(userProfile?.displayName.orEmpty()) }
    var username by remember { mutableStateOf(userProfile?.username.orEmpty()) }
    var bio by remember { mutableStateOf(userProfile?.bio ?: "Available") }
    var selectedAvatarId by remember { mutableStateOf(userProfile?.avatarId ?: 0) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Username uniqueness checking state
    var isCheckingUsername by remember { mutableStateOf(false) }
    var isUsernameAvailable by remember { mutableStateOf<Boolean?>(null) }

    val cleanInputUsername = username.trim().removePrefix("@").filter { it.isLetterOrDigit() || it == '_' }.lowercase()
    val initialCleanUsername = userProfile?.username.orEmpty().lowercase()

    LaunchedEffect(cleanInputUsername) {
        if (cleanInputUsername.isBlank()) {
            isUsernameAvailable = null
            isCheckingUsername = false
        } else if (cleanInputUsername == initialCleanUsername) {
            isUsernameAvailable = true
            isCheckingUsername = false
        } else if (cleanInputUsername.length < 3) {
            isUsernameAvailable = false
            isCheckingUsername = false
        } else {
            isCheckingUsername = true
            delay(350) // Debounce checking
            val available = chatRepository.isUsernameAvailable(cleanInputUsername, currentUserId)
            isUsernameAvailable = available
            isCheckingUsername = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Account & Profile",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .imePadding()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar Picker
                Text("Choose your avatar", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(5),
                    modifier = Modifier.height(240.dp)
                ) {
                    items(20) { id ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(if (id == selectedAvatarId) NexusCyan else Color.Transparent)
                                .border(2.dp, if (id == selectedAvatarId) NexusBlue else Color.LightGray, CircleShape)
                                .clickable { selectedAvatarId = id }
                        ) {
                            com.example.ui.components.AvatarDisplay(
                                avatarId = id,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (userProfile?.username?.isNotBlank() == true) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(NexusCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "@${userProfile.username}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = NexusCyan
                            )
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        errorMessage = null
                    },
                    label = { Text("Display Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NexusCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_display_name_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { input ->
                        username = input.filter { it.isLetterOrDigit() || it == '@' || it == '_' }.lowercase()
                        errorMessage = null
                    },
                    label = { Text("Unique Username (@handle)") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AlternateEmail,
                            contentDescription = null,
                            tint = NexusCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (isCheckingUsername) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = NexusCyan
                            )
                        } else if (cleanInputUsername.length >= 3 && cleanInputUsername != initialCleanUsername) {
                            if (isUsernameAvailable == true) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Available",
                                    tint = StatusOnline,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else if (isUsernameAvailable == false) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Taken",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    supportingText = {
                        if (cleanInputUsername.isNotBlank() && cleanInputUsername.length < 3) {
                            Text("Minimum 3 characters", color = MaterialTheme.colorScheme.error)
                        } else if (isCheckingUsername) {
                            Text("Checking availability...")
                        } else if (cleanInputUsername == initialCleanUsername) {
                            Text("Your current unique username", color = NexusCyan)
                        } else if (isUsernameAvailable == true) {
                            Text("✓ @$cleanInputUsername is available!", color = StatusOnline, fontWeight = FontWeight.SemiBold)
                        } else if (isUsernameAvailable == false) {
                            Text("✗ @$cleanInputUsername is already taken by another user", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("Each user must have a unique username")
                        }
                    },
                    isError = isUsernameAvailable == false && cleanInputUsername != initialCleanUsername,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isUsernameAvailable == true) StatusOnline else NexusCyan,
                        errorBorderColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_username_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Status / Bio") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NexusCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_bio_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Sign Out Button
                OutlinedButton(
                    onClick = {
                        authManager.signOut {
                            onDismiss()
                            onSignedOut()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sign_out_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
        },
        confirmButton = {
            val isSaveEnabled = !isSaving && displayName.isNotBlank() &&
                    !isCheckingUsername &&
                    (isUsernameAvailable != false || cleanInputUsername == initialCleanUsername)

            TextButton(
                onClick = {
                    scope.launch {
                        isSaving = true
                        errorMessage = null
                        val finalUsername = cleanInputUsername
                        val finalAvatarId = selectedAvatarId
                        val (success, err) = chatRepository.updateProfile(displayName, finalUsername, bio, finalAvatarId)
                        isSaving = false
                        if (success) {
                            onDismiss()
                        } else {
                            errorMessage = err ?: "Could not update profile. Please try again."
                        }
                    }
                },
                enabled = isSaveEnabled,
                modifier = Modifier.testTag("save_profile_button")
            ) {
                Text(
                    text = if (isSaving) "Saving..." else "Save",
                    color = if (isSaveEnabled) NexusCyan else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
