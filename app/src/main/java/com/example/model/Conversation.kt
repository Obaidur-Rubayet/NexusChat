package com.example.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val title: String = "",
    val isGroup: Boolean = false,
    val memberUids: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val groupAdminUids: List<String> = emptyList(),
    val lastMessageText: String = "",
    val lastMessageSenderName: String = "",
    val lastMessageSenderUid: String = "",
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val photoUrl: String = "",
    val category: String = "Chats"
)
