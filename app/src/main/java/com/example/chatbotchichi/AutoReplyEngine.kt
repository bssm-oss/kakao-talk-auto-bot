package com.example.kakaotalkautobot

import android.content.Context

object AutoReplyEngine {
    fun onIncoming(
        context: Context,
        room: String,
        message: String,
        sender: String,
        isGroupChat: Boolean,
        replier: SessionReplier,
        config: AutoReplyConfig
    ) {
        if (!BotManager.findMatchingConfig(context, room, sender, message, isGroupChat)?.name.equals(config.name)) {
            return
        }
        if (!config.replyEnabled) return
        Thread {
            val history = RoomStore.recentMessages(context, room).let { messages ->
                if (messages.isNotEmpty()) {
                    val last = messages.last()
                    if (last.incoming && last.sender == sender && last.message == message) messages.dropLast(1) else messages
                } else {
                    messages
                }
            }
            val reply = when (config.replyMode.lowercase()) {
                "canned" -> cannedReply(config, room, sender, message)
                else -> AiProviderClient.generate(
                    config = config,
                    room = room,
                    sender = sender,
                    message = message,
                    history = history
                )
            }
            if (reply.isNullOrBlank()) {
                UiLogger.log(
                    context,
                    "OUT_FAIL",
                    "[$room] AI reply skipped or provider config missing",
                    roomName = room,
                    speaker = "AI",
                    serverMessage = "reply skipped"
                )
                return@Thread
            }
            replier.replyToRoom(room, reply)
        }.start()
    }

    private fun cannedReply(config: AutoReplyConfig, room: String, sender: String, message: String): String? {
        if (config.cannedReplies.isEmpty()) return null
        val raw = config.cannedReplies[(message.hashCode() and Int.MAX_VALUE) % config.cannedReplies.size]
        return raw
            .replace("{room}", room)
            .replace("{sender}", sender)
            .replace("{message}", message)
            .trim()
    }
}
