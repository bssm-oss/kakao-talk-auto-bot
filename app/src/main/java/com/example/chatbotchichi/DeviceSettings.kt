package com.example.chatbotchichi

import android.content.Context

object DeviceSettings {
    private const val PREFS_NAME = "DevicePrefs"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_DEVICE_ID = "device_id"

    fun getDeviceName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_NAME, null)
    }

    fun getDeviceId(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_DEVICE_ID)) prefs.getLong(KEY_DEVICE_ID, 0L) else null
    }

    fun saveDevice(context: Context, name: String, id: Long?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val edit = prefs.edit().putString(KEY_DEVICE_NAME, name)
        if (id != null) {
            edit.putLong(KEY_DEVICE_ID, id)
        } else {
            edit.remove(KEY_DEVICE_ID)
        }
        edit.apply()
    }
}
