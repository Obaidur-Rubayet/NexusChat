package com.example.data

import android.content.Context
import com.example.R
import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.model.UserProfile
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChatRepository(
    val db: FirebaseFirestore,
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context) ?: FirebaseApp.getInstance()
            } else {
                FirebaseApp.getInstance()
            },
            context.getString(R.string.firestore_database_id)
        ),
        FirebaseAuth.getInstance()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be authenticated before performing Firestore operations.")
    }

    // --- User Profile & Status ---

    suspend fun isUsernameAvailable(username: String, currentUserId: String): Boolean {
        val clean = username.trim().removePrefix("@").filter { it.isLetterOrDigit() || it == '_' }.lowercase()
        if (clean.length < 3) return false

        return try {
            val query = db.collection("users")
                .whereEqualTo("usernameNormalized", clean)
                .limit(1)
                .get()
                .await()

            if (query.isEmpty) {
                true
            } else {
                val doc = query.documents.first()
                doc.id == currentUserId // Available if it belongs to current user
            }
        } catch (e: Exception) {
            true
        }
    }

    suspend fun generateUniqueDefaultUsername(base: String, currentUserId: String): String {
        val cleanBase = base.filter { it.isLetterOrDigit() || it == '_' }.lowercase().ifBlank { "user" }
        if (isUsernameAvailable(cleanBase, currentUserId)) {
            return cleanBase
        }
        for (i in 1..5) {
            val candidate = "${cleanBase}_${(100..999).random()}"
            if (isUsernameAvailable(candidate, currentUserId)) {
                return candidate
            }
        }
        return "${cleanBase}_${currentUserId.take(4).lowercase()}"
    }

    suspend fun syncUserProfile(firebaseUser: FirebaseUser) {
        val uid = firebaseUser.uid
        val docRef = db.collection("users").document(uid)
        try {
            val existing = docRef.get().await()
            val now = System.currentTimeMillis()
            val baseUsername = firebaseUser.email?.substringBefore("@")
                ?.filter { it.isLetterOrDigit() || it == '_' }
                ?.lowercase()
                ?.ifBlank { "user" } ?: "user"

            val profile = if (existing.exists()) {
                val data = existing.data ?: emptyMap()
                val currentDisplayName = data["displayName"] as? String ?: firebaseUser.displayName ?: "User"
                val currentBio = data["bio"] as? String ?: "Available"
                val currentAvatarId = (data["avatarId"] as? Number)?.toInt() ?: 0
                val existingUsername = (data["username"] as? String)?.takeIf { it.isNotBlank() }
                    ?: generateUniqueDefaultUsername(baseUsername, uid)
                UserProfile(
                    id = uid,
                    uid = uid,
                    email = firebaseUser.email ?: "",
                    displayName = currentDisplayName,
                    username = existingUsername,
                    usernameNormalized = existingUsername.lowercase(),
                    avatarId = currentAvatarId,
                    bio = currentBio,
                    phoneNumber = firebaseUser.phoneNumber ?: "",
                    isOnline = true,
                    lastSeen = now,
                    createdAt = (data["createdAt"] as? Number)?.toLong() ?: now,
                    updatedAt = now
                )
            } else {
                val defaultName = firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@") ?: "User"
                val uniqueDefaultUsername = generateUniqueDefaultUsername(baseUsername, uid)
                UserProfile(
                    id = uid,
                    uid = uid,
                    email = firebaseUser.email ?: "",
                    displayName = defaultName,
                    username = uniqueDefaultUsername,
                    usernameNormalized = uniqueDefaultUsername.lowercase(),
                    photoUrl = firebaseUser.photoUrl?.toString() ?: "",
                    bio = "Available",
                    phoneNumber = firebaseUser.phoneNumber ?: "",
                    isOnline = true,
                    lastSeen = now,
                    createdAt = now,
                    updatedAt = now
                )
            }
            docRef.set(profile, SetOptions.merge()).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
        }
    }

    suspend fun updateProfile(displayName: String, username: String, bio: String, avatarId: Int = 0): Pair<Boolean, String?> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val cleanUsername = username.trim().removePrefix("@").filter { it.isLetterOrDigit() || it == '_' }.lowercase()

        if (cleanUsername.isNotBlank()) {
            if (cleanUsername.length < 3) {
                return Pair(false, "Username must be at least 3 characters")
            }
            val isAvailable = isUsernameAvailable(cleanUsername, uid)
            if (!isAvailable) {
                return Pair(false, "Username @$cleanUsername is already taken. Please choose another one.")
            }
        }

        try {
            val updates = mutableMapOf<String, Any>(
                "displayName" to displayName.trim(),
                "bio" to bio.trim(),
                "avatarId" to avatarId,
                "updatedAt" to System.currentTimeMillis()
            )
            if (cleanUsername.isNotBlank()) {
                updates["username"] = cleanUsername
                updates["usernameNormalized"] = cleanUsername
            }
            docRef.update(updates).await()
            return Pair(true, null)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            return Pair(false, "Failed to update profile: ${e.message}")
        }
    }

    suspend fun updateProfile(displayName: String, bio: String) {
        updateProfile(displayName = displayName, username = "", bio = bio)
    }

    suspend fun searchUserByUsername(query: String): List<UserProfile> {
        val cleanQuery = query.trim().removePrefix("@").lowercase()
        if (cleanQuery.isBlank()) return emptyList()
        val currentUid = auth.currentUser?.uid ?: ""

        return try {
            val snapshot = db.collection("users")
                .whereGreaterThanOrEqualTo("usernameNormalized", cleanQuery)
                .whereLessThanOrEqualTo("usernameNormalized", cleanQuery + "\uf8ff")
                .limit(20)
                .get()
                .await()

            val results = snapshot.toObjects(UserProfile::class.java).filter { it.uid != currentUid }
            if (results.isNotEmpty()) {
                results
            } else {
                val fallbackSnapshot = db.collection("users").limit(50).get().await()
                fallbackSnapshot.toObjects(UserProfile::class.java).filter { user ->
                    user.uid != currentUid && (
                        user.usernameNormalized.contains(cleanQuery) ||
                        user.username.contains(cleanQuery, ignoreCase = true) ||
                        user.displayName.contains(cleanQuery, ignoreCase = true)
                    )
                }
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users")
            emptyList()
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        if (uid.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val path = "users/$uid"
        val listener = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.GET, path)
                    trySend(null)
                    return@addSnapshotListener
                }
                try {
                    if (snapshot != null && snapshot.exists()) {
                        trySend(snapshot.toObject(UserProfile::class.java))
                    } else {
                        trySend(null)
                    }
                } catch (e: Exception) {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun updatePresence(isOnline: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val path = "users/$uid/status/presence"
        try {
            db.collection("users").document(uid)
                .collection("status").document("presence")
                .set(
                    mapOf(
                        "isOnline" to isOnline,
                        "lastSeen" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                ).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    fun observePresence(uid: String): Flow<Boolean> = callbackFlow {
        if (uid.isBlank()) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val path = "users/$uid/status/presence"
        val listener = db.collection("users").document(uid)
            .collection("status").document("presence")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.GET, path)
                    trySend(false)
                    return@addSnapshotListener
                }
                val isOnline = snapshot?.getBoolean("isOnline") ?: false
                trySend(isOnline)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getAllUsers(): List<UserProfile> {
        val path = "users"
        return try {
            val snapshot = db.collection(path).limit(50).get().await()
            snapshot.toObjects(UserProfile::class.java)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            emptyList()
        }
    }

    // --- Conversations ---

    fun observeConversations(userId: String): Flow<List<Conversation>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val path = "conversations"
        val listener = db.collection(path)
            .whereArrayContains("memberUids", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                try {
                    val list = snapshot?.toObjects(Conversation::class.java) ?: emptyList()
                    val sorted = list.sortedByDescending { it.lastMessageTimestamp }
                    trySend(sorted)
                } catch (e: Exception) {
                    trySend(emptyList())
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun createDirectConversation(contactName: String): Conversation {
        val currentUid = requireUserId()
        val currentName = auth.currentUser?.displayName ?: "User"
        val cleanName = contactName.trim().ifBlank { "Contact" }
        val partnerUid = "usr_" + kotlin.math.abs(cleanName.lowercase().hashCode())

        // Symmetrical conversation ID for direct chats
        val convId = if (currentUid < partnerUid) "dm_${currentUid}_${partnerUid}" else "dm_${partnerUid}_${currentUid}"
        val docRef = db.collection("conversations").document(convId)

        val existing = try {
            docRef.get().await()
        } catch (e: Exception) {
            null
        }

        if (existing != null && existing.exists()) {
            val existingConv = existing.toObject(Conversation::class.java)
            if (existingConv != null) return existingConv
        }

        val conv = Conversation(
            id = convId,
            title = cleanName,
            isGroup = false,
            memberUids = listOf(currentUid, partnerUid),
            memberNames = mapOf(
                currentUid to currentName,
                partnerUid to cleanName
            ),
            lastMessageText = "Conversation started",
            lastMessageSenderName = currentName,
            lastMessageSenderUid = currentUid,
            lastMessageTimestamp = System.currentTimeMillis(),
            category = "Direct"
        )
        try {
            docRef.set(conv).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
        }
        return conv
    }

    suspend fun createDirectConversation(partnerUser: UserProfile): Conversation {
        val currentUid = requireUserId()
        val currentName = auth.currentUser?.displayName ?: "User"
        val partnerUid = partnerUser.uid

        // Symmetrical conversation ID for direct chats
        val convId = if (currentUid < partnerUid) "dm_${currentUid}_${partnerUid}" else "dm_${partnerUid}_${currentUid}"
        val docRef = db.collection("conversations").document(convId)

        val existing = try {
            docRef.get().await()
        } catch (e: Exception) {
            null
        }

        if (existing != null && existing.exists()) {
            return existing.toObject(Conversation::class.java) ?: Conversation(id = convId)
        }

        val convTitle = partnerUser.displayName.ifBlank {
            partnerUser.email.substringBefore("@").ifBlank { "Contact" }
        }

        val conv = Conversation(
            id = convId,
            title = convTitle,
            isGroup = false,
            memberUids = listOf(currentUid, partnerUid),
            memberNames = mapOf(
                currentUid to currentName,
                partnerUid to convTitle
            ),
            lastMessageText = "Conversation started",
            lastMessageSenderName = currentName,
            lastMessageSenderUid = currentUid,
            lastMessageTimestamp = System.currentTimeMillis(),
            category = "Direct"
        )
        try {
            docRef.set(conv).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
        }
        return conv
    }

    suspend fun createGroupConversation(title: String, memberUids: List<String>, memberNames: Map<String, String>): Conversation {
        val currentUid = requireUserId()
        val currentName = auth.currentUser?.displayName ?: "User"
        val allMembers = (memberUids + currentUid).distinct()
        val allNames = memberNames + (currentUid to currentName)
        val convId = "group_${UUID.randomUUID()}"
        val docRef = db.collection("conversations").document(convId)

        val group = Conversation(
            id = convId,
            title = title,
            isGroup = true,
            memberUids = allMembers,
            memberNames = allNames,
            groupAdminUids = listOf(currentUid),
            lastMessageText = "Group created",
            lastMessageSenderName = currentName,
            lastMessageSenderUid = currentUid,
            lastMessageTimestamp = System.currentTimeMillis(),
            category = "Groups"
        )
        try {
            docRef.set(group).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
        }
        return group
    }

    // --- Messages ---

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = callbackFlow {
        if (conversationId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val path = "conversations/$conversationId/messages"
        val listener = db.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                try {
                    val messages = snapshot?.toObjects(ChatMessage::class.java) ?: emptyList()
                    trySend(messages)
                } catch (e: Exception) {
                    trySend(emptyList())
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendMessage(conversationId: String, text: String): ChatMessage {
        val currentUid = requireUserId()
        val currentName = auth.currentUser?.displayName ?: "User"
        val messageId = "msg_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()

        val chatMessage = ChatMessage(
            id = messageId,
            conversationId = conversationId,
            senderUid = currentUid,
            senderName = currentName,
            text = text.trim(),
            timestamp = now,
            isEncrypted = false
        )

        val messageRef = db.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .document(messageId)

        val convRef = db.collection("conversations").document(conversationId)

        try {
            messageRef.set(chatMessage).await()
            try {
                convRef.set(
                    mapOf(
                        "lastMessageText" to text.trim(),
                        "lastMessageSenderName" to currentName,
                        "lastMessageSenderUid" to currentUid,
                        "lastMessageTimestamp" to now
                    ),
                    SetOptions.merge()
                ).await()
            } catch (convEx: Exception) {
                // Non-fatal if last message sync fails
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, messageRef.path)
        }
        return chatMessage
    }

    suspend fun addReaction(conversationId: String, messageId: String, emoji: String) {
        val path = "conversations/$conversationId/messages/$messageId"
        try {
            db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(messageId)
                .update("reaction", emoji)
                .await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    // --- Realtime Typing Indicators ---

    fun observeTypingStatus(conversationId: String): Flow<Map<String, Boolean>> = callbackFlow {
        if (conversationId.isBlank()) {
            trySend(emptyMap())
            close()
            return@callbackFlow
        }
        val path = "conversations/$conversationId/typing"
        val listener = db.collection("conversations")
            .document(conversationId)
            .collection("typing")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                    trySend(emptyMap())
                    return@addSnapshotListener
                }
                try {
                    val map = mutableMapOf<String, Boolean>()
                    snapshot?.documents?.forEach { doc ->
                        val isTyping = doc.getBoolean("isTyping") ?: false
                        map[doc.id] = isTyping
                    }
                    trySend(map)
                } catch (e: Exception) {
                    trySend(emptyMap())
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun setTyping(conversationId: String, isTyping: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val path = "conversations/$conversationId/typing/$uid"
        try {
            db.collection("conversations")
                .document(conversationId)
                .collection("typing")
                .document(uid)
                .set(
                    mapOf(
                        "isTyping" to isTyping,
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                ).await()
        } catch (e: Exception) {
            // Typing status is best-effort transient indicator; do not report or log error
        }
    }

    suspend fun deleteMessage(conversationId: String, messageId: String) {
        val path = "conversations/$conversationId/messages/$messageId"
        try {
            db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(messageId)
                .delete()
                .await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
        }
    }
}
