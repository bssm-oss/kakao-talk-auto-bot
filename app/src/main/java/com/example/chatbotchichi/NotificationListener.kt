package com.example.chatbotchichi

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.mozilla.javascript.Context as RhinoContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationListener : NotificationListenerService() {
    private val TAG = "BotEngine-Listener"
    // 알림 키 -> 마지막 처리 시간 (중복 방지용)
    private val processedNotifications = mutableMapOf<String, Long>()

    private val debugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.chatbotchichi.DEBUG_MSG") {
                val room = intent.getStringExtra("room") ?: "디버그방"
                val msg = intent.getStringExtra("msg") ?: ""
                val sender = intent.getStringExtra("sender") ?: "테스터"
                val pkgName = intent.getStringExtra("packageName") ?: packageName
                
                Log.d(TAG, "가상 메시지 수신: $msg")
                
                // 디버깅용 Replier (isDebug=true)
                val debugReplier = SessionReplier(this@NotificationListener, room, true)
                processMessage(room, msg, sender, true, debugReplier, null, pkgName)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "NotificationListener connected")
        updateStatus("연결됨 (작동 중)", true)
        
        val filter = IntentFilter("com.example.chatbotchichi.DEBUG_MSG")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(debugReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(debugReceiver, filter)
        }

        if (AppSettings.isGlobalEnabled(this)) {
            val replier = SessionReplier(this, "__SYSTEM__", false)
            processMessage("__SYSTEM__", "__GLOBAL_START__", "SYSTEM", false, replier, null, packageName)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "NotificationListener disconnected")
        updateStatus("연결 끊김", false)
        try {
            unregisterReceiver(debugReceiver)
        } catch (e: Exception) {}
    }

    private fun updateStatus(msg: String, isConnected: Boolean) {
        StatusStore.save(this, msg, isConnected)
        val intent = Intent("com.example.chatbotchichi.STATUS_UPDATE")
        intent.putExtra("status", msg)
        intent.putExtra("connected", isConnected)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun sendLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "[$time] $message"
        LogStore.append(this, line)
        val intent = Intent("com.example.chatbotchichi.LOG_UPDATE")
        intent.putExtra("log", line)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        
        if (!AppSettings.isGlobalEnabled(this)) return

        if (sbn.packageName != "com.kakao.talk" && 
            sbn.packageName != packageName && 
            sbn.packageName != "com.android.shell") return

        // 중복 방지 로직 개선: 시간 비교
        val lastTime = processedNotifications[sbn.key] ?: 0L
        if (sbn.postTime <= lastTime) {
            // 이미 처리한 알림이거나 과거 알림이면 무시
            return 
        }
        processedNotifications[sbn.key] = sbn.postTime
        
        // 맵 사이즈 관리 (오래된 키 제거)
        if (processedNotifications.size > 200) {
            val iterator = processedNotifications.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }

        try {
            val extras = sbn.notification.extras ?: return

            var msg: String? = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            if (msg.isNullOrBlank()) {
                msg = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            }

            val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            var room: String? = conversationTitle?.trim()
            var sender: String = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "알수없음"

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

            if (room.isNullOrBlank()) {
                room = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
            }
            if (room.isNullOrBlank()) {
                room = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.trim()
            }
            if (room.isNullOrBlank()) {
                room = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            }

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
            val isGroupChat = room != sender

            // *** 중요: 답장 세션 캐싱 (SessionManager) ***
            SessionManager.bindSession(this, room, sbn.notification, sbn.packageName)

            // Replier 생성 (이제 room 이름만 알면 됨)
            val replier = SessionReplier(this, room, false)
            val imageDB = ImageDB(null, null)

            processMessage(room, msg, sender, isGroupChat, replier, imageDB, sbn.packageName)

        } catch (e: Throwable) {
            Log.e(TAG, "알림 처리 중 에러", e)
        }
    }

    private fun processMessage(
        room: String, 
        msg: String, 
        sender: String, 
        isGroupChat: Boolean,
        replier: SessionReplier?, 
        imageDB: ImageDB?, 
        packageName: String
    ) {
        val logMsg = "[$room] $sender: $msg"
        Log.i(TAG, "처리 중: $logMsg")
        sendLog(logMsg)

        val allBots = BotManager.getBots(this)
        val enabledBots = allBots.filter { it.isEnabled }

        for (bot in enabledBots) {
            runJsScript(bot, room, msg, sender, isGroupChat, replier, imageDB, packageName)
        }
    }

    private fun runJsScript(
        bot: BotInfo,
        room: String,
        msg: String,
        sender: String,
        isGroupChat: Boolean,
        replier: SessionReplier?,
        imageDB: ImageDB?,
        packageName: String
    ) {
        val rhino = RhinoContext.enter()
        rhino.optimizationLevel = -1
        try {
            val scope = rhino.initStandardObjects()

            val file = File(bot.fileName)
            if (!file.exists()) {
                Log.e(TAG, "${bot.name}: 파일 없음")
                return
            }

            val reader = InputStreamReader(FileInputStream(file))
            rhino.evaluateReader(scope, reader, bot.name, 1, null)

            val function = scope.get("responseFix", scope)
            if (function is org.mozilla.javascript.Function) {
                val args = arrayOf(
                    room, msg, sender, isGroupChat, replier, imageDB, packageName, 0
                )
                function.call(rhino, scope, scope, args)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "${bot.name} 실행 중 치명적 에러", e)
            e.printStackTrace()
            sendLog("❌ ${bot.name} 에러: ${e.message}")
        } finally {
            RhinoContext.exit()
        }
    }
}
