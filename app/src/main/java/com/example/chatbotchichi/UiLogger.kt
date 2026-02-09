package com.example.chatbotchichi

import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UiLogger {
    fun log(context: Context, label: String, message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "[$time][$label] $message"
        LogStore.append(context, line)
        val intent = Intent("com.example.chatbotchichi.LOG_UPDATE")
        intent.putExtra("log", line)
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
    }
}
