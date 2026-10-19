package com.example.data

import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryTest {

    @Test
    fun userProfile_defaultValues_areValid() {
        val user = UserProfile(
            uid = "user_123",
            email = "user@example.com",
            displayName = "Alex Mercer",
            username = "alex",
            usernameNormalized = "alex"
        )
        assertEquals("user_123", user.uid)
        assertEquals("Alex Mercer", user.displayName)
        assertEquals("alex", user.usernameNormalized)
        assertTrue(user.createdAt > 0)
    }

    @Test
    fun conversation_memberListAndTitle_behaveCorrectly() {
        val conversation = Conversation(
            id = "conv_123",
            title = "Project Alpha",
            isGroup = true,
            memberUids = listOf("u1", "u2", "u3"),
            lastMessageText = "Welcome to the group"
        )
        assertEquals(3, conversation.memberUids.size)
        assertTrue(conversation.isGroup)
        assertEquals("Project Alpha", conversation.title)
    }

    @Test
    fun chatMessage_reactionAndText_updated() {
        val message = ChatMessage(
            id = "msg_1",
            conversationId = "conv_123",
            senderUid = "u1",
            senderName = "Alex",
            text = "Hello world!",
            reaction = "❤️"
        )
        assertEquals("Hello world!", message.text)
        assertEquals("❤️", message.reaction)
        assertFalse(message.isEncrypted)
    }
}
