package com.example.chatbotchichi

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

object PollingManager {
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
            replier.log("폴링 시작 실패: BOT_ID/URL 비어있음")
            return
        }
        if (jobs.containsKey(key)) {
            replier.log("이미 폴링 중: $key")
            return
        }
        val safeInterval = if (intervalMs < 1000L) 1000L else intervalMs

        val job = scope.launch {
            while (isActive) {
                try {
                    pollOnce(url, replier)
                } catch (e: Exception) {
                    replier.log("Polling Error: ${e.message}")
                }
                delay(safeInterval)
            }
        }
        jobs[key] = job
        replier.log("폴링 시작 (id=$key, ${safeInterval / 1000}s)")
    }

    fun stop(botId: String, replier: SessionReplier) {
        val key = botId.trim()
        val job = jobs.remove(key)
        if (job != null) {
            job.cancel()
            replier.log("폴링 중지 (id=$key)")
        } else {
            replier.log("폴링 중지 실패: 실행 중 아님 (id=$key)")
        }
    }

    private fun pollOnce(url: String, replier: SessionReplier) {
        if (url.isBlank()) {
            replier.log("Polling Error: URL 비어있음")
            return
        }
        replier.log("요청 시작: $url")
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                replier.log("HTTP ${response.code}: ${body ?: ""}")
                return
            }
            if (body.isNullOrBlank()) {
                replier.log("빈 응답")
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
                        replier.log("Invalid payload index $i")
                    }
                }
            } else {
                val obj = JSONObject(trimmed)
                emitSingle(obj, replier)
            }
        } catch (e: Exception) {
            replier.log("JSON 파싱 실패: ${e.message}")
        }
    }

    private fun emitSingle(obj: JSONObject, replier: SessionReplier) {
        val room = obj.optString("room", "").trim()
        val message = obj.optString("message", "").trim()
        if (room.isEmpty() || message.isEmpty()) {
            replier.log("Invalid payload: room/message 없음")
            return
        }
        val ok = replier.replyToRoom(room, message)
        if (!ok) {
            replier.log("Reply 실패: 세션 없음 ($room)")
        }
    }
}
