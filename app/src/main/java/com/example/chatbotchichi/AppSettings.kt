package com.example.chatbotchichi

import android.content.Context

object AppSettings {
    private const val PREFS_NAME = "AppSettingsPrefs"
    private const val KEY_GLOBAL_ENABLED = "global_enabled"

    fun isGlobalEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_GLOBAL_ENABLED, true)
    }

    fun setGlobalEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_GLOBAL_ENABLED, enabled).apply()
    }
}
