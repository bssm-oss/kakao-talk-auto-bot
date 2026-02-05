package com.example.chatbotchichi

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.mozilla.javascript.Context as RhinoContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * 카카오톡 알림 감지 + 가상 알림(디버깅) 처리 + 멀티 봇 실행 (파일 기반)
 */
class NotificationListener : NotificationListenerService() {
    private val TAG = "BotEngine-Listener"
    private val processedNotifications = mutableSetOf<String>()

    // 디버깅 메시지 수신용 리시버
    private val debugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.chatbotchichi.DEBUG_MSG") {
                val room = intent.getStringExtra("room") ?: "디버그방"
                val msg = intent.getStringExtra("msg") ?: ""
                val sender = intent.getStringExtra("sender") ?: "테스터"
                val pkgName = intent.getStringExtra("packageName") ?: packageName
                
                Log.d(TAG, "가상 메시지 수신: $msg")
                
                // 디버깅용 Replier 생성
                val debugReplier = SessionReplier(null, this@NotificationListener, true, room)
                processMessage(room, msg, sender, true, debugReplier, null, pkgName)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "NotificationListener connected")
        updateStatus("연결됨 (작동 중)")
        
        val filter = IntentFilter("com.example.chatbotchichi.DEBUG_MSG")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(debugReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(debugReceiver, filter)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "NotificationListener disconnected")
        updateStatus("연결 끊김")
        try {
            unregisterReceiver(debugReceiver)
        } catch (e: Exception) {}
    }

    private fun updateStatus(msg: String) {
        val intent = Intent("com.example.chatbotchichi.STATUS_UPDATE")
        intent.putExtra("status", msg)
        sendBroadcast(intent)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        
        if (sbn.packageName != "com.kakao.talk" && 
            sbn.packageName != packageName && 
            sbn.packageName != "com.android.shell") return

        if (processedNotifications.contains(sbn.key)) return
        processedNotifications.add(sbn.key)
        if (processedNotifications.size > 200) processedNotifications.clear()

        try {
            val extras = sbn.notification.extras ?: return
            
            var msg: String? = extras.getCharSequence("android.text")?.toString()
            if (msg == null) msg = extras.getCharSequence("android.bigText")?.toString()
            
            val sender = extras.getString("android.title") ?: "알수없음"
            var room = extras.getString("android.subText") ?: extras.getString("android.summaryText")
            val isGroupChat = room != null
            if (room == null) room = sender

            if (msg == null) return

            // 실제 알림 Replier (isDebug=false)
            val replier = SessionReplier(sbn, this, false)
            val imageDB = ImageDB(null, null)

            processMessage(room, msg, sender, isGroupChat, replier, imageDB, sbn.packageName)

        } catch (e: Exception) {
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
        
        val intent = Intent("com.example.chatbotchichi.LOG_UPDATE")
        intent.putExtra("log", logMsg)
        sendBroadcast(intent)

        // 파일에서 봇 목록 로드 (BotManager 사용)
        val allBots = BotManager.getBots(this) // Context 전달
        val enabledBots = allBots.filter { it.isEnabled }

        if (enabledBots.isEmpty()) {
            // 봇이 하나도 없거나 다 꺼져있으면 로그만 남김
            return
        }

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

            // 파일에서 스크립트 읽기
            val file = File(bot.fileName) // fileName은 절대 경로
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
            e.printStackTrace() // 스택 트레이스 전체 출력
            val errIntent = Intent("com.example.chatbotchichi.LOG_UPDATE")
            errIntent.putExtra("log", "❌ ${bot.name} 에러: ${e.javaClass.simpleName} - ${e.message}")
            sendBroadcast(errIntent)
        } finally {
            RhinoContext.exit()
        }
    }
}