package com.example.kakaotalkautobot

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

object AutoReplyEngine {
    private const val RECENT_REPLY_DEDUPE_TTL_MS = 12_000L
    private const val RECENT_REPLY_DEDUPE_MAX = 300

    /**
     * Reply generation stays off the main thread so notification capture remains responsive
     * even when local context scoring needs to inspect room history and memory.
     */
    private val replyScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val roomMutexes = ConcurrentHashMap<String, Mutex>()
    private val recentReplyWork = LinkedHashMap<String, Long>()

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
        if (!shouldAcceptReplyWork(room, sender, message)) return

        replyScope.launch {
            mutexForRoom(room).withLock {
                val history = RoomStore.recentMessages(context, room, limit = 40).let { messages ->
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
                        context = context,
                        config = memoryAugmentedConfig,
                        room = room,
                        sender = sender,
                        message = message,
                        history = history
                    ).toResolution()
                }
                when {
                    !resolution.reply.isNullOrBlank() -> {
                        val sent = replier.replyToRoom(room, resolution.reply)
                        if (!sent) {
                            val reason = "AI 답장은 생성됐지만 카카오톡 전송에 실패했습니다."
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
            }
        }
    }

    private fun mutexForRoom(room: String): Mutex {
        return roomMutexes.getOrPut(room.trim().ifBlank { "unknown" }) { Mutex() }
    }

    @Synchronized
    internal fun shouldAcceptReplyWork(
        room: String,
        sender: String,
        message: String,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        pruneRecentReplyWork(nowMs)
        val key = listOf(room.trim(), sender.trim(), message.trim()).joinToString("\u001F")
        val lastSeen = recentReplyWork[key]
        if (lastSeen != null && nowMs - lastSeen < RECENT_REPLY_DEDUPE_TTL_MS) {
            return false
        }
        recentReplyWork[key] = nowMs
        return true
    }

    @Synchronized
    internal fun clearRecentReplyWorkForTest() {
        recentReplyWork.clear()
    }

    private fun pruneRecentReplyWork(nowMs: Long) {
        val iterator = recentReplyWork.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMs - entry.value >= RECENT_REPLY_DEDUPE_TTL_MS) {
                iterator.remove()
            }
        }
        while (recentReplyWork.size > RECENT_REPLY_DEDUPE_MAX) {
            val iteratorForSize = recentReplyWork.entries.iterator()
            if (!iteratorForSize.hasNext()) break
            iteratorForSize.next()
            iteratorForSize.remove()
        }
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
