package com.example.chatbotchichi

import android.app.Notification
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

    fun reply(message: String): Boolean {
        // 1. 디버그 모드 처리
        if (isDebug) {
            Log.d(TAG, "[DEBUG] Reply: $message")
            val intent = Intent("com.example.chatbotchichi.BOT_REPLY")
            intent.putExtra("msg", message)
            intent.putExtra("room", room)
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
            return true
        }

        // 2. 세션 매니저에서 캐싱된 Action 조회
        val session = SessionManager.getSession(room)
        if (session == null) {
            Log.e(TAG, "Reply failed: No session found for room $room. (알림을 한 번도 받지 않았거나 세션 만료)")
            return false
        }

        try {
            val action = session.action
            val remoteInputs = action.remoteInputs
            
            if (remoteInputs.isNullOrEmpty()) {
                Log.e(TAG, "Reply failed: RemoteInput is null/empty")
                return false
            }

            for (remoteInput in remoteInputs) {
                if (remoteInput.resultKey != null) {
                    val intent = Intent()
                    val bundle = Bundle()
                    bundle.putCharSequence(remoteInput.resultKey, message)
                    RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
                    
                    action.actionIntent.send(context, 0, intent)
                    Log.d(TAG, "Reply sent to $room: $message")
                    return true
                }
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Reply failed", e)
            return false
        }
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
                    .url("https://YOUR_N8N_DOMAIN/webhook/chatbot-hook") // 실제 주소로 변경 필요
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
}
