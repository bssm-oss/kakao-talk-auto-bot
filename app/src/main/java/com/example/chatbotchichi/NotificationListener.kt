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
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput as CoreRemoteInput
import org.mozilla.javascript.Context as RhinoContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

class NotificationListener : NotificationListenerService() {
    private val TAG = "BotEngine-Listener"
    private val VERBOSE_LOG = false
    private val VERBOSE_UI_LOG = false
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
            InboundPollingController.startIfPossible(this)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "NotificationListener disconnected")
        updateStatus("연결 끊김", false)
        InboundPollingController.stop(this)
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
        UiLogger.log(this, "IN", message)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        
        if (!AppSettings.isGlobalEnabled(this)) return

        if (sbn.packageName != "com.kakao.talk" && 
            sbn.packageName != packageName && 
            sbn.packageName != "com.android.shell") return

        if (VERBOSE_LOG || VERBOSE_UI_LOG) {
            logNotificationDetails(sbn)
        }

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
        if (room == "__SYSTEM__" && sender == "SYSTEM") {
            when (msg) {
                "__GLOBAL_START__" -> InboundPollingController.startIfPossible(this)
                "__GLOBAL_STOP__" -> InboundPollingController.stop(this)
            }
        }
        val logMsg = "[$room] $sender: $msg"
        Log.i(TAG, "처리 중: $logMsg")
        val isSystem = room == "__SYSTEM__" || sender == "SYSTEM" && msg.startsWith("__GLOBAL_")
        if (!isSystem) {
            sendLog(logMsg)
        }

        val allBots = BotManager.getBots(this)
        val enabledBots = allBots.filter { it.isEnabled }

        for (bot in enabledBots) {
            runJsScript(bot, room, msg, sender, isGroupChat, replier, imageDB, packageName)
        }
    }

    private fun logNotificationDetails(sbn: StatusBarNotification) {
        try {
            val n = sbn.notification
            val header = "SBN pkg=${sbn.packageName} id=${sbn.id} tag=${sbn.tag} key=${sbn.key} groupKey=${sbn.groupKey}"
            Log.d(TAG, header)
            if (VERBOSE_UI_LOG) UiLogger.log(this, "NLS", header)

            val extras = n.extras
            if (extras == null) {
                Log.d(TAG, "extras: null")
                if (VERBOSE_UI_LOG) UiLogger.log(this, "EXTRAS", "null")
            } else {
                val keys = extras.keySet().toList().sorted()
                Log.d(TAG, "extras keys (${keys.size}): $keys")
                if (VERBOSE_UI_LOG) UiLogger.log(this, "EXTRAS", "keys(${keys.size})=$keys")
                for (key in keys) {
                    val line = "extras[$key]=${summarizeValue(extras.get(key))}"
                    Log.d(TAG, line)
                    if (VERBOSE_UI_LOG) UiLogger.log(this, "EXTRAS", line)
                }

                // android.messages (MessagingStyle) 상세
                val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                if (!messages.isNullOrEmpty()) {
                    Log.d(TAG, "extras[android.messages] size=${messages.size}")
                    if (VERBOSE_UI_LOG) UiLogger.log(this, "EXTRAS", "android.messages size=${messages.size}")
                    messages.forEachIndexed { idx, item ->
                        if (item is Bundle) {
                            Log.d(TAG, "messages[$idx] keys=${item.keySet()}")
                            if (VERBOSE_UI_LOG) UiLogger.log(this, "EXTRAS", "messages[$idx] keys=${item.keySet()}")
                            for (k in item.keySet()) {
                                val line = "messages[$idx].$k=${summarizeValue(item.get(k))}"
                                Log.d(TAG, line)
                                if (VERBOSE_UI_LOG) UiLogger.log(this, "EXTRAS", line)
                            }
                        } else {
                            val line = "messages[$idx] type=${item?.javaClass?.name}"
                            Log.d(TAG, line)
                            if (VERBOSE_UI_LOG) UiLogger.log(this, "EXTRAS", line)
                        }
                    }
                }
            }

            // Native actions (Notification.Action)
            val nativeActions = n.actions
            if (nativeActions != null) {
                Log.d(TAG, "native actions count=${nativeActions.size}")
                if (VERBOSE_UI_LOG) UiLogger.log(this, "ACTION", "native count=${nativeActions.size}")
                nativeActions.forEachIndexed { idx, action ->
                    val headerAction = "nativeAction[$idx] title=${action.title} icon=${action.icon}"
                    Log.d(TAG, headerAction)
                    if (VERBOSE_UI_LOG) UiLogger.log(this, "ACTION", headerAction)
                    val pi = action.actionIntent
                    if (pi != null) {
                        val line1 = "nativeAction[$idx] pendingIntent=$pi"
                        val line2 = "nativeAction[$idx] creatorPackage=${pi.creatorPackage} uid=${pi.creatorUid} isActivity=${pi.isActivity} isService=${pi.isService} isBroadcast=${pi.isBroadcast}"
                        Log.d(TAG, line1)
                        Log.d(TAG, line2)
                        if (VERBOSE_UI_LOG) UiLogger.log(this, "PENDING", line1)
                        if (VERBOSE_UI_LOG) UiLogger.log(this, "PENDING", line2)
                    } else {
                        Log.d(TAG, "nativeAction[$idx] pendingIntent=null")
                        if (VERBOSE_UI_LOG) UiLogger.log(this, "PENDING", "nativeAction[$idx] pendingIntent=null")
                    }
                    val ris = action.remoteInputs
                    if (!ris.isNullOrEmpty()) {
                        Log.d(TAG, "nativeAction[$idx] remoteInputs count=${ris.size}")
                        if (VERBOSE_UI_LOG) UiLogger.log(this, "REMOTEINPUT", "nativeAction[$idx] count=${ris.size}")
                        ris.forEachIndexed { rIdx, ri ->
                            logRemoteInput("nativeAction[$idx].remoteInputs[$rIdx]", ri)
                        }
                    }
                }
            } else {
                Log.d(TAG, "native actions: null")
                if (VERBOSE_UI_LOG) UiLogger.log(this, "ACTION", "native actions: null")
            }

            // Compat actions
            val count = NotificationCompat.getActionCount(n)
            Log.d(TAG, "compat actions count=$count")
            if (VERBOSE_UI_LOG) UiLogger.log(this, "ACTION", "compat count=$count")
            for (i in 0 until count) {
                val action = NotificationCompat.getAction(n, i)
                if (action != null) {
                    val headerAction = "compatAction[$i] title=${action.title} icon=${action.icon}"
                    Log.d(TAG, headerAction)
                    if (VERBOSE_UI_LOG) UiLogger.log(this, "ACTION", headerAction)
                    val pi = action.actionIntent
                    if (pi != null) {
                        val line1 = "compatAction[$i] pendingIntent=$pi"
                        val line2 = "compatAction[$i] creatorPackage=${pi.creatorPackage} uid=${pi.creatorUid} isActivity=${pi.isActivity} isService=${pi.isService} isBroadcast=${pi.isBroadcast}"
                        Log.d(TAG, line1)
                        Log.d(TAG, line2)
                        if (VERBOSE_UI_LOG) UiLogger.log(this, "PENDING", line1)
                        if (VERBOSE_UI_LOG) UiLogger.log(this, "PENDING", line2)
                    } else {
                        Log.d(TAG, "compatAction[$i] pendingIntent=null")
                        if (VERBOSE_UI_LOG) UiLogger.log(this, "PENDING", "compatAction[$i] pendingIntent=null")
                    }
                    val ris = action.remoteInputs
                    if (!ris.isNullOrEmpty()) {
                        Log.d(TAG, "compatAction[$i] remoteInputs count=${ris.size}")
                        if (VERBOSE_UI_LOG) UiLogger.log(this, "REMOTEINPUT", "compatAction[$i] count=${ris.size}")
                        ris.forEachIndexed { rIdx, ri ->
                            logRemoteInput("compatAction[$i].remoteInputs[$rIdx]", ri)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "logNotificationDetails error", e)
            if (VERBOSE_UI_LOG) UiLogger.log(this, "ERROR", "logNotificationDetails error: ${e.message}")
        }
    }

    private fun logRemoteInput(prefix: String, ri: android.app.RemoteInput) {
        val lines = listOf(
            "$prefix.resultKey=${ri.resultKey}",
            "$prefix.label=${ri.label}",
            "$prefix.allowFreeFormInput=${ri.allowFreeFormInput}",
            "$prefix.choices=${ri.choices?.size ?: 0}",
            "$prefix.allowedDataTypes=${ri.allowedDataTypes}",
            "$prefix.extrasKeys=${ri.extras.keySet()}"
        )
        for (line in lines) {
            Log.d(TAG, line)
            if (VERBOSE_UI_LOG) UiLogger.log(this, "REMOTEINPUT", line)
        }
    }

    private fun logRemoteInput(prefix: String, ri: CoreRemoteInput) {
        val lines = listOf(
            "$prefix.resultKey=${ri.resultKey}",
            "$prefix.label=${ri.label}",
            "$prefix.allowFreeFormInput=${ri.allowFreeFormInput}",
            "$prefix.choices=${ri.choices?.size ?: 0}",
            "$prefix.allowedDataTypes=${ri.allowedDataTypes}",
            "$prefix.extrasKeys=${ri.extras.keySet()}"
        )
        for (line in lines) {
            Log.d(TAG, line)
            if (VERBOSE_UI_LOG) UiLogger.log(this, "REMOTEINPUT", line)
        }
    }

    private fun summarizeValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is CharSequence -> "\"${value}\""
            is String -> "\"${value}\""
            is Int, is Long, is Boolean, is Float, is Double -> value.toString()
            is Bundle -> "Bundle{keys=${value.keySet()}}"
            is android.graphics.Bitmap -> "Bitmap(${value.width}x${value.height})"
            is android.graphics.drawable.Icon -> "Icon(type=${value.type})"
            is Array<*> -> "Array(size=${value.size}, type=${value.javaClass.componentType})"
            is IntArray -> "IntArray(size=${value.size})"
            is LongArray -> "LongArray(size=${value.size})"
            is BooleanArray -> "BooleanArray(size=${value.size})"
            is CharArray -> "CharArray(size=${value.size})"
            is FloatArray -> "FloatArray(size=${value.size})"
            is DoubleArray -> "DoubleArray(size=${value.size})"
            else -> value.javaClass.name
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
