package com.example.chatbotchichi

import android.content.Context

object LogStore {
    private const val PREFS_NAME = "LogStorePrefs"
    private const val KEY_LOGS = "logs"
    private const val MAX_CHARS = 5000

    @Synchronized
    fun append(context: Context, line: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_LOGS, "") ?: ""
        val next = if (existing.isBlank()) line else "$existing\n$line"
        val trimmed = if (next.length > MAX_CHARS) next.takeLast(MAX_CHARS) else next
        prefs.edit().putString(KEY_LOGS, trimmed).apply()
    }

    fun appendWithTimestamp(context: Context, message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        append(context, "[$time] $message")
    }

    fun getAll(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LOGS, "") ?: ""
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_LOGS).apply()
    }
}
