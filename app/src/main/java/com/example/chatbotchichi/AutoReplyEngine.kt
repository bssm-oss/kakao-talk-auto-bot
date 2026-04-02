package com.example.kakaotalkautobot

import android.content.Context

object AutoReplyEngine {
    data class ReplyResolution(
        val reply: String? = null,
        val failureReason: String? = null,
        val skippedReason: String? = null
    )

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
            val memoryAugmentedConfig = withAutoMemory(context, room, config)
            val resolution = when (memoryAugmentedConfig.replyMode.lowercase()) {
                "canned" -> cannedReply(memoryAugmentedConfig, room, sender, message)
                else -> AiProviderClient.generate(
                    config = memoryAugmentedConfig,
                    room = room,
                    sender = sender,
                    message = message,
                    history = history
                ).toResolution()
            }
            when {
                !resolution.reply.isNullOrBlank() -> replier.replyToRoom(room, resolution.reply)
                !resolution.skippedReason.isNullOrBlank() -> UiLogger.log(
                    context,
                    "OUT_SKIP",
                    "[$room] ${resolution.skippedReason}",
                    roomName = room,
                    speaker = "AI",
                    serverMessage = resolution.skippedReason
                )
                else -> {
                    val reason = resolution.failureReason ?: "응답 조건을 충족하지 못했습니다."
                    UiLogger.log(
                        context,
                        "OUT_FAIL",
                        "[$room] $reason",
                        roomName = room,
                        speaker = "AI",
                        serverMessage = reason
                    )
                }
            }
        }.start()
    }

    private fun withAutoMemory(context: Context, room: String, config: AutoReplyConfig): AutoReplyConfig {
        val autoMemory = AutoMemoryStore.getSummary(context, room)
        if (autoMemory.isBlank()) return config
        val combinedMemory = buildString {
            if (config.roomMemory.isNotBlank()) {
                append(config.roomMemory.trim())
                append("\n\n")
            }
            append(autoMemory)
        }
        return config.copy(roomMemory = combinedMemory.trim())
    }

    private fun cannedReply(
        config: AutoReplyConfig,
        room: String,
        sender: String,
        message: String
    ): ReplyResolution {
        if (config.cannedReplies.isEmpty()) {
            return ReplyResolution(failureReason = "고정 답장 목록이 비어 있습니다.")
        }
        val raw = config.cannedReplies[(message.hashCode() and Int.MAX_VALUE) % config.cannedReplies.size]
        val reply = raw
            .replace("{room}", room)
            .replace("{sender}", sender)
            .replace("{message}", message)
            .trim()
        if (reply.isBlank()) {
            return ReplyResolution(failureReason = "고정 답장 템플릿이 비어 있습니다.")
        }
        return ReplyResolution(reply = reply)
    }

    private fun AiProviderClient.GenerationResult.toResolution(): ReplyResolution {
        return ReplyResolution(
            reply = reply,
            failureReason = failureReason,
            skippedReason = skippedReason
        )
    }
}
