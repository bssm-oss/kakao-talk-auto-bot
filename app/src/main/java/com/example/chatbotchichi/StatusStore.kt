package com.example.chatbotchichi

import android.content.Context

object StatusStore {
    private const val PREFS_NAME = "ListenerStatusPrefs"
    private const val KEY_TEXT = "status_text"
    private const val KEY_CONNECTED = "status_connected"
    private const val KEY_TS = "status_ts"

    data class Status(
        val text: String,
        val isConnected: Boolean,
        val timestamp: Long
    )

    fun save(context: Context, text: String, isConnected: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_TEXT, text)
            .putBoolean(KEY_CONNECTED, isConnected)
            .putLong(KEY_TS, System.currentTimeMillis())
            .apply()
    }

    fun get(context: Context): Status? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val text = prefs.getString(KEY_TEXT, null) ?: return null
        val connected = prefs.getBoolean(KEY_CONNECTED, false)
        val ts = prefs.getLong(KEY_TS, 0L)
        return Status(text, connected, ts)
    }
}
