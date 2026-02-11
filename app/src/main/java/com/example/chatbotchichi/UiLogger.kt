package com.example.chatbotchichi

import android.content.Context
import android.content.Intent
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UiLogger {
    private val allowedLabels = setOf("IN", "OUT", "OUT_FAIL")
    private const val LOG_WEBHOOK_BASE = "https://n8n.onthelook.ai/webhook/f5dae0c0-ea7c-48f4-92e6-37b14ba41172"
    private val client = OkHttpClient()

    fun log(context: Context, label: String, message: String) {
        if (!allowedLabels.contains(label)) return
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "[$time][$label] $message"
        LogStore.append(context, line)
        val intent = Intent("com.example.chatbotchichi.LOG_UPDATE")
        intent.putExtra("log", line)
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
        sendWebhook(line, label)
    }

    private fun sendWebhook(message: String, label: String) {
        val status = when (label) {
            "IN" -> "in"
            "OUT" -> "out"
            "OUT_FAIL" -> "fail"
            else -> "fail"
        }
        val (roomName, cleanMessage) = extractRoomAndMessage(message)
        try {
            val url = LOG_WEBHOOK_BASE
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("message", cleanMessage)
                .addQueryParameter("status", status)
                .addQueryParameter("room_name", roomName ?: "")
                .build()
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    // ignore
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.close()
                }
            })
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun extractRoomAndMessage(raw: String): Pair<String?, String> {
        var text = raw.trim()
        if (text.startsWith("❌")) {
            text = text.removePrefix("❌").trim()
        }
        if (text.startsWith("[")) {
            val end = text.indexOf(']')
            if (end > 1) {
                val room = text.substring(1, end).trim()
                var rest = text.substring(end + 1).trim()
                if (rest.startsWith(":")) {
                    rest = rest.removePrefix(":").trim()
                }
                return room to rest
            }
        }
        return null to text
    }
}
