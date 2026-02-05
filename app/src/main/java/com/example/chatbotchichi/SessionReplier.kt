package com.example.chatbotchichi

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
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

/**
 * 알림 답장 및 외부 API (N8N) 연동 처리 클래스
 */
class SessionReplier(
    val sbn: StatusBarNotification?, // 디버그 모드일 땐 null 가능
    val context: Context,
    val isDebug: Boolean = false, // 디버그 모드 여부
    val debugRoom: String? = null // 디버그 답장 보낼 방 이름
) {
    private val TAG = "BotEngine-Replier"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * 카카오톡 알림에 답장을 보냅니다.
     * 디버그 모드일 경우 Broadcast로 가상 답장을 전송합니다.
     */
    fun reply(message: String): Boolean {
        if (isDebug) {
            Log.d(TAG, "[DEBUG] Reply intercepted: $message")
            val intent = Intent("com.example.chatbotchichi.BOT_REPLY")
            intent.putExtra("msg", message)
            intent.putExtra("room", debugRoom)
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
            return true
        }

        try {
            if (sbn == null) return false
            val wearableExtender = androidx.core.app.NotificationCompat.WearableExtender(sbn.notification)
            for (action in wearableExtender.actions) {
                val remoteInputs = action.remoteInputs ?: continue
                for (remoteInput in remoteInputs) {
                    if (remoteInput.resultKey != null) {
                        val intent = Intent()
                        val bundle = Bundle()
                        bundle.putCharSequence(remoteInput.resultKey, message)
                        RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
                        action.actionIntent.send(context, 0, intent)
                        Log.d(TAG, "Reply sent: $message")
                        return true
                    }
                }
            }
            Log.e(TAG, "No remote input found for reply")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Reply failed", e)
            return false
        }
    }

    /**
     * N8N 웹훅을 호출합니다.
     * @param actionType 액션 타입 (예: "buy", "log")
     * @param data 추가 데이터 Map
     */
    fun executeWorkflow(actionType: String, data: Map<String, Any>): Boolean {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject()
                json.put("action", actionType)
                json.put("timestamp", System.currentTimeMillis())
                // 채팅방 정보가 있다면 추가
                json.put("room", if (isDebug) debugRoom else sbn?.notification?.extras?.getString("android.subText") ?: "unknown")
                
                for ((key, value) in data) {
                    json.put(key, value)
                }

                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                
                // 실제 N8N 주소로 변경 필요 (환경변수나 설정으로 빼는 것 권장)
                val request = Request.Builder()
                    .url("https://YOUR_N8N_DOMAIN/webhook/chatbot-hook") 
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "N8N workflow executed successfully: ${response.body?.string()}")
                    } else {
                        Log.e(TAG, "N8N workflow failed: ${response.code} ${response.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing workflow", e)
            }
        }
        return true
    }

    /**
     * 로컬 스크립트 함수 실행 (구현 예시)
     */
    fun executeLocalScript(functionName: String, vararg args: Any?): Any? {
        Log.d(TAG, "executeLocalScript called: $functionName")
        return null
    }
}