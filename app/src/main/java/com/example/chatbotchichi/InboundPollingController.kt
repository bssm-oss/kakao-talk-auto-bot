package com.example.chatbotchichi

import android.content.Context

object InboundPollingController {
    private const val BOT_ID = "__SYSTEM_INBOUND__"
    private const val DEFAULT_INTERVAL_MS = 1000L

    fun startIfPossible(context: Context) {
        val deviceName = DeviceSettings.getDeviceName(context)?.trim().orEmpty()
        if (deviceName.isBlank()) return
        val url = ApiClient.buildInboundUrl(deviceName)
        val replier = SessionReplier(context.applicationContext, "__SYSTEM__", false)
        PollingManager.start(BOT_ID, url, DEFAULT_INTERVAL_MS, replier)
    }

    fun stop(context: Context) {
        val replier = SessionReplier(context.applicationContext, "__SYSTEM__", false)
        PollingManager.stop(BOT_ID, replier)
    }
}
