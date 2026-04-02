package com.example.kakaotalkautobot

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat

class NotificationListener : NotificationListenerService() {
    private val processedNotifications = mutableMapOf<String, Long>()

    private val debugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != "com.example.kakaotalkautobot.DEBUG_MSG") return
            val room = intent.getStringExtra("room") ?: "디버그방"
            val msg = intent.getStringExtra("msg") ?: return
            val sender = intent.getStringExtra("sender") ?: "테스터"
            processIncoming(room, msg, sender, room != sender, SessionReplier(this@NotificationListener, room, true))
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        updateStatus("알림 리스너 연결됨", true)
        val filter = IntentFilter("com.example.kakaotalkautobot.DEBUG_MSG")
        ContextCompat.registerReceiver(this, debugReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        updateStatus("연결 끊김", false)
        try {
            unregisterReceiver(debugReceiver)
        } catch (_: Exception) {
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        if (sbn.packageName != "com.kakao.talk" && sbn.packageName != packageName && sbn.packageName != "com.android.shell") return

        val lastTime = processedNotifications[sbn.key] ?: 0L
        if (sbn.postTime <= lastTime) return
        processedNotifications[sbn.key] = sbn.postTime
        if (processedNotifications.size > 200) {
            val iterator = processedNotifications.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }

        try {
            val extras = sbn.notification.extras ?: return
            var msg = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            if (msg.isNullOrBlank()) {
                msg = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            }
            val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            var room = conversationTitle?.trim()
            var sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "알수없음"

            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (!messages.isNullOrEmpty()) {
                val last = messages.last()
                if (last is Bundle) {
                    val text = last.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    val senderName = last.getCharSequence("sender")?.toString()
                    if (!text.isNullOrBlank()) msg = text
                    if (!senderName.isNullOrBlank()) sender = senderName
                }
            }
            if (room.isNullOrBlank()) room = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
            if (room.isNullOrBlank()) room = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.trim()
            if (room.isNullOrBlank()) room = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            if (!msg.isNullOrBlank() && room == sender) {
                val idx = msg.indexOf(": ")
                if (idx in 1..40) {
                    val possibleSender = msg.substring(0, idx).trim()
                    val possibleMsg = msg.substring(idx + 2).trim()
                    if (possibleSender.isNotBlank() && possibleMsg.isNotBlank()) {
                        sender = possibleSender
                        msg = possibleMsg
                    }
                }
            }

            if (msg.isNullOrBlank()) return
            if (room.isNullOrBlank()) room = sender
            SessionManager.bindSession(this, room, sbn.notification, sbn.packageName)
            processIncoming(room, msg, sender, room != sender, SessionReplier(this, room, false))
        } catch (error: Throwable) {
            Log.e("AutoReply-Listener", "알림 처리 실패", error)
        }
    }

    private fun processIncoming(room: String, msg: String, sender: String, isGroupChat: Boolean, replier: SessionReplier) {
        val roomConfig = BotManager.findRoomConfig(this, room, sender)
        if (roomConfig?.captureEnabled == true) {
            RoomStore.recordIncoming(this, room, sender, msg)
        }
        UiLogger.log(
            this,
            "IN",
            "[$room] $sender: $msg",
            roomName = room,
            speaker = sender,
            serverMessage = msg
        )
        if (!AppSettings.isAiReplyEnabled(this)) {
            return
        }
        if (roomConfig == null) return
        AutoReplyEngine.onIncoming(this, room, msg, sender, isGroupChat, replier, roomConfig)
    }

    private fun updateStatus(msg: String, isConnected: Boolean) {
        StatusStore.save(this, msg, isConnected)
        val intent = Intent("com.example.kakaotalkautobot.STATUS_UPDATE")
        intent.putExtra("status", msg)
        intent.putExtra("connected", isConnected)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }
}
