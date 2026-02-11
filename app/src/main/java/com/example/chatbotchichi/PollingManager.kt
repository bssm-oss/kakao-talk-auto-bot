package com.example.chatbotchichi

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object PollingManager {
    private const val TAG = "BotEngine-Polling"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    fun start(botId: String, url: String, intervalMs: Long, replier: SessionReplier) {
        val key = botId.trim().ifEmpty { url.trim() }
        if (key.isEmpty()) {
            Log.e(TAG, "폴링 시작 실패: BOT_ID/URL 비어있음")
            return
        }
        if (jobs.containsKey(key)) {
            Log.d(TAG, "이미 폴링 중: $key")
            return
        }
        val baseInterval = if (intervalMs < 100L) 100L else intervalMs

        val job = scope.launch {
            while (isActive) {
                try {
                    pollOnce(url, replier)
                } catch (e: Exception) {
                    Log.e(TAG, "Polling Error: ${e.message}")
                }
                val delayMs = if (baseInterval <= 1000L) {
                    Random.nextLong(100L, baseInterval + 1)
                } else {
                    baseInterval
                }
                delay(delayMs)
            }
        }
        jobs[key] = job
        Log.d(TAG, "폴링 시작 (id=$key, ${baseInterval}ms)")
    }

    fun stop(botId: String, replier: SessionReplier) {
        val key = botId.trim()
        val job = jobs.remove(key)
        if (job != null) {
            job.cancel()
            Log.d(TAG, "폴링 중지 (id=$key)")
        } else {
            Log.d(TAG, "폴링 중지 실패: 실행 중 아님 (id=$key)")
        }
    }

    private fun pollOnce(url: String, replier: SessionReplier) {
        if (url.isBlank()) {
            Log.e(TAG, "Polling Error: URL 비어있음")
            return
        }
        Log.d(TAG, "요청 시작: $url")
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP ${response.code}: ${body ?: ""}")
                return
            }
            if (body.isNullOrBlank()) {
                Log.d(TAG, "빈 응답")
                return
            }
            emitPayloads(body, replier)
        }
    }

    private fun emitPayloads(body: String, replier: SessionReplier) {
        val trimmed = body.trim()
        try {
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i)
                    if (item != null) {
                        emitSingle(item, replier)
                    } else {
                        Log.d(TAG, "Invalid payload index $i")
                    }
                }
            } else {
                val obj = JSONObject(trimmed)
                emitSingle(obj, replier)
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON 파싱 실패: ${e.message}")
        }
    }

    private fun emitSingle(obj: JSONObject, replier: SessionReplier) {
        val room = obj.optString("room", "").trim()
        val message = obj.optString("message", "").trim()
        if (room.isEmpty() || message.isEmpty()) {
            Log.d(TAG, "Invalid payload: room/message 없음")
            return
        }
        val incomingLine = "[$room] 시스템: N8N응답 : $message"
        UiLogger.log(replier.context, "IN", incomingLine)
        val ok = replier.replyToRoom(room, message)
        if (!ok) {
            Log.d(TAG, "Reply 실패: 세션 없음 ($room)")
        }
    }
}
