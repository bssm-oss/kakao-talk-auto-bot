package com.example.kakaotalkautobot

import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UiLogger {
    private val allowedLabels = setOf("IN", "OUT", "OUT_FAIL")

    fun log(
        context: Context,
        label: String,
        message: String,
        roomName: String? = null,
        speaker: String? = null,
        serverMessage: String? = null
    ) {
        if (!allowedLabels.contains(label)) return
        val normalizedMessage = if (label == "OUT_FAIL" && !message.trimStart().startsWith("❌")) {
            "❌ $message"
        } else {
            message
        }
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "[$time][$label] $normalizedMessage"
        LogStore.append(context, line)
        val intent = Intent("com.example.kakaotalkautobot.LOG_UPDATE")
        intent.putExtra("log", line)
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
    }
}
