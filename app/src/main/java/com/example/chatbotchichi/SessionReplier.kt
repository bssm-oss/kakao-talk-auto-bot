package com.example.chatbotchichi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SessionReplier(
    val context: Context,
    val room: String, // 세션 조회를 위한 키 (방 이름)
    val isDebug: Boolean = false // 디버그 모드 여부
) {
    private val TAG = "BotEngine-Replier"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun logOutgoing(targetRoom: String, message: String, success: Boolean, reason: String? = null) {
        val label = if (success) "OUT" else "OUT_FAIL"
        val base = "[$targetRoom] $message"
        val line = if (success) {
            base
        } else {
            val detail = if (reason.isNullOrBlank()) "" else " (reason=$reason)"
            "❌ $base$detail"
        }
        UiLogger.log(context, label, line)
    }

    fun reply(message: String): Boolean {
        return replyToRoom(this.room, message)
    }

    /**
     * 특정 방으로 메시지 전송 (스크립트에서 사용)
     */
    fun replyToRoom(targetRoom: String, message: String): Boolean {
        // 1. 세션 매니저에서 캐싱된 실전 Action 조회 (우선 순위 높음)
        val session = SessionManager.getSession(targetRoom)
        
        if (session != null) {
            try {
                val action = session.action
                val remoteInputs = action.remoteInputs
                val pi = action.actionIntent
                if (pi != null) {
                    val line = "pendingIntent=$pi creatorPackage=${pi.creatorPackage} uid=${pi.creatorUid} isActivity=${pi.isActivity} isService=${pi.isService} isBroadcast=${pi.isBroadcast}"
                    Log.d(TAG, line)
                } else {
                    Log.d(TAG, "pendingIntent=null")
                }
                
                if (!remoteInputs.isNullOrEmpty()) {
                    for (remoteInput in remoteInputs) {
                        if (remoteInput.resultKey != null) {
                            val intent = Intent()
                            val bundle = Bundle()
                            bundle.putCharSequence(remoteInput.resultKey, message)
                            RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
                            val clip = intent.clipData
                            if (clip != null) {
                                val clipLine = "clipData items=${clip.itemCount} label=${clip.description?.label}"
                                Log.d(TAG, clipLine)
                            } else {
                                Log.d(TAG, "clipData=null")
                            }
                            
                            val pendingIntent = action.actionIntent
                            if (pendingIntent == null) {
                                Log.e(TAG, "Reply failed: PendingIntent is null for $targetRoom")
                                logOutgoing(targetRoom, message, false, "pendingIntent null")
                                return false
                            }
                            pendingIntent.send(context, 0, intent)
                            Log.d(TAG, "Reply sent to $targetRoom: $message")
                            logOutgoing(targetRoom, message, true, null)
                            
                            // 디버그 모드에서 실전송 성공 시, 디버깅 룸에도 로그 남김
                            if (isDebug) {
                                val intentDebug = Intent("com.example.chatbotchichi.BOT_REPLY")
                                intentDebug.putExtra("msg", "✅ [실전송] $targetRoom: $message")
                                intentDebug.putExtra("room", room) // 현재 디버깅 중인 방에 표시
                                intentDebug.setPackage(context.packageName)
                                context.sendBroadcast(intentDebug)
                            }
                            return true
                        }
                    }
                    logOutgoing(targetRoom, message, false, "no remoteInput")
                    return false
                }
                logOutgoing(targetRoom, message, false, "no remoteInput")
                return false
            } catch (e: Exception) {
                Log.e(TAG, "Reply failed", e)
                logOutgoing(targetRoom, message, false, e.message ?: "exception")
            }
        }

        // 2. 실전 세션이 없고 디버그 모드인 경우 (가상 시뮬레이션)
        if (isDebug) {
            Log.d(TAG, "[DEBUG] Simulation Reply to $targetRoom: $message")
            val intent = Intent("com.example.chatbotchichi.BOT_REPLY")
            intent.putExtra("msg", "🛠 [가상] $targetRoom: $message")
            intent.putExtra("room", room)
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
            return true
        }

        Log.e(TAG, "Reply failed: No session found for room $targetRoom")
        logOutgoing(targetRoom, message, false, "no session")
        return false
    }

    fun executeWorkflow(actionType: String, data: Map<String, Any>): Boolean {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject()
                json.put("action", actionType)
                json.put("timestamp", System.currentTimeMillis())
                json.put("room", room)
                
                for ((key, value) in data) {
                    json.put(key, value)
                }

                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("https://internal-n8n.company.com/webhook/chatbot-hook") // 실제 주소로 변경 필요
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "N8N success: ${response.body?.string()}")
                    } else {
                        Log.e(TAG, "N8N fail: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Workflow error", e)
            }
        }
        return true
    }

    fun log(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val line = "[$time] $message"
        LogStore.append(context, line)
        val intent = Intent("com.example.chatbotchichi.LOG_UPDATE")
        intent.putExtra("log", line)
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    fun startPolling(botId: String, url: String, intervalMs: Long) {
        PollingManager.start(botId, url, intervalMs, this)
    }

    fun startPolling(botId: String, url: String, intervalMs: Double) {
        PollingManager.start(botId, url, intervalMs.toLong(), this)
    }

    fun stopPolling(botId: String) {
        PollingManager.stop(botId, this)
    }
}
