package com.example.chatbotchichi

import android.content.Context
import android.util.Log

object PollingBotController {
    private const val TAG = "PollingBotController"

    fun applyToggle(context: Context, botName: String, enabled: Boolean) {
        val config = BotManager.getPollingBotConfig(context, botName) ?: return
        val replier = SessionReplier(context.applicationContext, "__SYSTEM__", false)
        if (enabled) {
            PollingManager.start(config.botId, config.url, config.intervalMs, replier)
            Log.d(TAG, "토글 ON -> 폴링 시작 (${config.botId}, ${config.intervalMs}ms)")
        } else {
            PollingManager.stop(config.botId, replier)
            Log.d(TAG, "토글 OFF -> 폴링 중지 (${config.botId})")
        }
    }

    fun syncAll(context: Context, runEnabledBots: Boolean) {
        val appContext = context.applicationContext
        val replier = SessionReplier(appContext, "__SYSTEM__", false)
        val bots = BotManager.getBots(appContext)
        for (bot in bots) {
            val config = BotManager.getPollingBotConfig(appContext, bot.name) ?: continue
            val shouldRun = runEnabledBots && bot.isEnabled
            if (shouldRun) {
                PollingManager.start(config.botId, config.url, config.intervalMs, replier)
            } else {
                PollingManager.stop(config.botId, replier)
            }
        }
    }
}
