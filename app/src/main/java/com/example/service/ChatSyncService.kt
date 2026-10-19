package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.data.ChatRepository
import com.example.model.Conversation
import com.example.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class ChatSyncService : Service() {

    private lateinit var chatRepository: ChatRepository
    private var listenerRegistration: ListenerRegistration? = null
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            chatRepository = ChatRepository(this)
            val notification = NotificationHelper.getServiceNotification(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
                ServiceCompat.startForeground(
                    this,
                    NotificationHelper.SERVICE_NOTIFICATION_ID,
                    notification,
                    serviceType
                )
            } else {
                startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        val uid = currentUser?.uid ?: prefs.getString("last_logged_in_uid", null)
        if (uid.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        prefs.edit().putString("last_logged_in_uid", uid).apply()

        startListening(uid)
        return START_STICKY
    }

    private fun getLastSeenTimestamp(convId: String): Long {
        return prefs.getLong("seen_$convId", 0L)
    }

    private fun setLastSeenTimestamp(convId: String, timestamp: Long) {
        prefs.edit().putLong("seen_$convId", timestamp).apply()
    }

    private fun isInitialized(): Boolean {
        return prefs.getBoolean("is_initialized", false)
    }

    private fun setInitialized() {
        prefs.edit().putBoolean("is_initialized", true).apply()
    }

    private fun startListening(uid: String) {
        if (listenerRegistration != null) return

        try {
            listenerRegistration = chatRepository.db.collection("conversations")
                .whereArrayContains("memberUids", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Sync snapshot error: ${error.message}")
                        return@addSnapshotListener
                    }

                    val conversations = snapshot?.toObjects(Conversation::class.java) ?: return@addSnapshotListener

                    val initialized = isInitialized()

                    if (!initialized) {
                        // First-time baseline: store existing message timestamps without sending notifications
                        conversations.forEach { conv ->
                            setLastSeenTimestamp(conv.id, conv.lastMessageTimestamp)
                        }
                        setInitialized()
                        return@addSnapshotListener
                    }

                    conversations.forEach { conv ->
                        val prevTime = getLastSeenTimestamp(conv.id)

                        if (conv.lastMessageTimestamp > prevTime && conv.lastMessageText.isNotBlank()) {
                            val isPlaceholder = conv.lastMessageText == "Conversation started" ||
                                    conv.lastMessageText == "Group created"

                            val isMyMessage = if (conv.lastMessageSenderUid.isNotBlank()) {
                                conv.lastMessageSenderUid == uid
                            } else {
                                val myName = FirebaseAuth.getInstance().currentUser?.displayName ?: ""
                                myName.isNotBlank() && myName != "User" && conv.lastMessageSenderName == myName
                            }

                            val isUserLookingAtThisChat = isAppInForeground && activeConversationId == conv.id

                            if (!isPlaceholder && !isMyMessage && !isUserLookingAtThisChat) {
                                // If the app is closed, minimized or in background, send system notification
                                if (!isAppInForeground) {
                                    val cleanTitle = if (conv.isGroup) {
                                        conv.title.ifBlank { "Group Room" }
                                    } else {
                                        val partner = conv.memberNames.entries.firstOrNull { it.key != uid }?.value
                                        val name = partner ?: conv.title
                                        if (name.contains("@")) name.substringBefore("@") else name.ifBlank { "Direct Chat" }
                                    }
                                    val senderName = if (conv.lastMessageSenderName.contains("@")) {
                                        conv.lastMessageSenderName.substringBefore("@")
                                    } else conv.lastMessageSenderName.ifBlank { "Someone" }

                                    NotificationHelper.showMessageNotification(
                                        context = this@ChatSyncService,
                                        conversationId = conv.id,
                                        title = cleanTitle,
                                        message = "$senderName: ${conv.lastMessageText}",
                                        notificationId = Math.abs(conv.id.hashCode())
                                    )
                                }
                            }
                            setLastSeenTimestamp(conv.id, conv.lastMessageTimestamp)
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening in ChatSyncService: ${e.message}", e)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        isAppInForeground = false
        activeConversationId = null
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    companion object {
        private const val TAG = "ChatSyncService"
        private const val PREFS_NAME = "nexus_chat_sync_prefs"

        @Volatile
        var isAppInForeground: Boolean = false

        @Volatile
        var activeConversationId: String? = null

        fun start(context: Context) {
            try {
                val intent = Intent(context, ChatSyncService::class.java)
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ChatSyncService: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().remove("last_logged_in_uid").apply()
                val intent = Intent(context, ChatSyncService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop ChatSyncService: ${e.message}", e)
            }
        }
    }
}
