package com.example.chatbotchichi

object PollingBotTemplate {
    fun generate(botId: String, url: String, room: String, intervalMs: Long): String {
        val safeUrl = url.replace("\\", "\\\\").replace("\"", "\\\"")
        val safeRoom = room.replace("\\", "\\\\").replace("\"", "\\\"")
        val safeBotId = botId.replace("\\", "\\\\").replace("\"", "\\\"")
        val safeInterval = if (intervalMs >= 1000L) intervalMs else 1000L

        return """
// === Chatbotchichi Polling Bot Template ===
const BOT_ID = "$safeBotId";
const WEBHOOK_URL = "$safeUrl";
const TRIGGER_ROOM = "$safeRoom"; // 비워두면 모든 방에서 제어
const START_CMD = "폴링 시작";
const STOP_CMD = "폴링 중지";
const GLOBAL_START_CMD = "__GLOBAL_START__";
const GLOBAL_STOP_CMD = "__GLOBAL_STOP__";
const AUTO_START = false; // 자동 시작 끔
const INTERVAL_MS = $safeInterval;
const REQUIRE_ROOM = TRIGGER_ROOM && String(TRIGGER_ROOM).trim().length > 0;

var __replier = null;

function isTriggerRoom(room) {
    if (!REQUIRE_ROOM) return true;
    return room == TRIGGER_ROOM;
}

function responseFix(room, msg, sender, isGroupChat, replier, imageDB, packageName) {
    __replier = replier;

    if (packageName == "com.example.chatbotchichi" && msg == GLOBAL_START_CMD) {
        replier.startPolling(BOT_ID, WEBHOOK_URL, INTERVAL_MS);
        return;
    }
    if (packageName == "com.example.chatbotchichi" && msg == GLOBAL_STOP_CMD) {
        replier.stopPolling(BOT_ID);
        return;
    }

    if (AUTO_START && isTriggerRoom(room) && msg != STOP_CMD) {
        replier.startPolling(BOT_ID, WEBHOOK_URL, INTERVAL_MS);
        return;
    }

    if (!isTriggerRoom(room)) return;

    if (msg == START_CMD) {
        replier.startPolling(BOT_ID, WEBHOOK_URL, INTERVAL_MS);
    }
    if (msg == STOP_CMD) {
        replier.stopPolling(BOT_ID);
    }
}
""".trimIndent()
    }
}
