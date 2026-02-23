package com.example.chatbotchichi

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

object PollingManager {
    private const val TAG = "BotEngine-Polling"
    private const val MIN_INTERVAL_MS = 1000L
    private const val MAX_JITTER_MS = 1000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val client = SharedHttpClient.instance

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
        val baseInterval = if (intervalMs < MIN_INTERVAL_MS) MIN_INTERVAL_MS else intervalMs

        val job = scope.launch {
            while (isActive) {
                try {
                    pollOnce(url, replier)
                } catch (e: Exception) {
                    Log.e(TAG, "Polling Error: ${e.message}")
                }
                val delayMs = baseInterval + Random.nextLong(0L, MAX_JITTER_MS)
                delay(delayMs)
            }
        }
        jobs[key] = job
        Log.d(TAG, "폴링 시작 (id=$key, base=${baseInterval}ms, jitter<${MAX_JITTER_MS}ms)")
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
        val resolvedUrl = resolveInboundUrl(url, replier)
        val usedDeviceName = resolvedUrl.toHttpUrlOrNull()?.queryParameter("device_name")?.trim().orEmpty()
        Log.d(TAG, "폴링 device_name=${if (usedDeviceName.isBlank()) "<empty>" else usedDeviceName}")
        val request = Request.Builder()
            .url(resolvedUrl)
            .get()
            .header("Accept", "application/json")
            .build()
        val requestBytes = estimateRequestSizeBytes(request)
        Log.d(TAG, "요청 시작: $resolvedUrl (tx~${requestBytes}B)")
        val usageBefore = NetworkUsageMeter.snapshot()

        try {
            client.newCall(request).execute().use { response ->
                val bodyBytes = response.body?.bytes()
                val responseBytes = bodyBytes?.size ?: 0
                val charset = response.body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                val body = bodyBytes?.toString(charset)
                Log.d(TAG, "응답 수신: code=${response.code}, rx=${responseBytes}B")
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
        } finally {
            NetworkUsageMeter.logDelta(TAG, "폴링 실측", usageBefore, NetworkUsageMeter.snapshot())
        }
    }

    private fun emitPayloads(body: String, replier: SessionReplier) {
        val trimmed = body.trim()
        if (trimmed == "[]" || trimmed == "{}") {
            Log.d(TAG, "처리할 메시지 없음 (body=$trimmed)")
            return
        }
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
            Log.d(TAG, "Invalid payload: room/message 없음 keys=${obj.keys().asSequence().toList()}")
            return
        }
        val incomingLine = "[$room] 시스템: N8N응답 : $message"
        UiLogger.log(
            replier.context,
            "IN",
            incomingLine,
            roomName = room,
            speaker = "시스템",
            serverMessage = "N8N응답 : $message"
        )
        val ok = replier.replyToRoom(room, message)
        if (!ok) {
            Log.d(TAG, "Reply 실패: 세션 없음 ($room)")
        }
    }

    private fun resolveInboundUrl(url: String, replier: SessionReplier): String {
        if (!url.contains("/messages/inbound")) return url
        val parsed = url.toHttpUrlOrNull() ?: return url
        val savedDeviceName = DeviceSettings.getDeviceName(replier.context)?.trim().orEmpty()
        val existingDeviceName = parsed.queryParameter("device_name")?.trim().orEmpty()
        val deviceName = when {
            savedDeviceName.isNotBlank() -> savedDeviceName
            existingDeviceName.isNotBlank() -> existingDeviceName
            else -> ""
        }
        if (deviceName.isBlank()) {
            Log.w(TAG, "device_name 없음: 저장값/URL 파라미터 모두 비어있음")
            return url
        }
        val resolved = parsed.newBuilder()
            .removeAllQueryParameters("device_name")
            .addQueryParameter("device_name", deviceName)
            .build()
            .toString()
        if (existingDeviceName != deviceName || resolved != url) {
            Log.d(TAG, "device_name 적용: $deviceName")
        }
        return resolved
    }

    private fun estimateRequestSizeBytes(request: Request): Long {
        var total = 0L
        val encodedPath = request.url.encodedPath
        val encodedQuery = request.url.encodedQuery
        val target = if (encodedQuery.isNullOrBlank()) encodedPath else "$encodedPath?$encodedQuery"
        total += "${request.method} $target HTTP/1.1\r\n".toByteArray(Charsets.UTF_8).size
        for (i in 0 until request.headers.size) {
            val name = request.headers.name(i)
            val value = request.headers.value(i)
            total += "$name: $value\r\n".toByteArray(Charsets.UTF_8).size
        }
        total += 2 // header 종료 CRLF
        val bodyLength = request.body?.contentLength()?.takeIf { it > 0L } ?: 0L
        return total + bodyLength
    }
}
