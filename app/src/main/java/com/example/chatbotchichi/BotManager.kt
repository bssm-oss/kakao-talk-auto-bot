package com.example.kakaotalkautobot

import android.content.Context
import java.io.File

object BotManager {
    private const val DIR_NAME = "auto_reply_configs"

    fun getBots(context: Context): List<BotInfo> {
        ensureSampleConfig(context)
        return configFiles(context)
            .mapNotNull { file ->
                runCatching {
                    val config = AutoReplyJson.parse(file.readText(), file.nameWithoutExtension)
                    BotInfo(
                        name = config.name,
                        fileName = file.absolutePath,
                        isEnabled = config.enabled,
                        roomPattern = config.roomPattern,
                        captureEnabled = config.captureEnabled,
                        replyEnabled = config.replyEnabled,
                        replyMode = config.replyMode
                    )
                }.getOrNull()
            }
            .sortedBy { it.name.lowercase() }
    }

    fun getConfigs(context: Context): List<AutoReplyConfig> {
        ensureSampleConfig(context)
        return configFiles(context)
            .mapNotNull { file -> runCatching { AutoReplyJson.parse(file.readText(), file.nameWithoutExtension) }.getOrNull() }
            .sortedByDescending { it.roomPattern.length }
    }

    fun getConfig(context: Context, name: String): AutoReplyConfig? {
        ensureSampleConfig(context)
        val file = configFile(context, name)
        if (!file.exists()) return null
        return runCatching { AutoReplyJson.parse(file.readText(), name) }.getOrNull()
    }

    fun getConfigByRoomPattern(context: Context, roomPattern: String): AutoReplyConfig? {
        return getConfigs(context).firstOrNull { it.roomPattern.equals(roomPattern, true) }
    }

    fun findRoomConfig(context: Context, room: String, sender: String): AutoReplyConfig? {
        val roomConfig = getConfigs(context)
            .asSequence()
            .filter { it.enabled }
            .filter { roomMatches(it.roomPattern, room) }
            .filter { senderAllowed(it, sender) }
            .firstOrNull()
            ?: return null
        return applyGlobalAiSettings(context, roomConfig)
    }

    fun findMatchingConfig(context: Context, room: String, sender: String, message: String, isGroupChat: Boolean): AutoReplyConfig? {
        val roomConfig = findRoomConfig(context, room, sender) ?: return null
        return roomConfig.takeIf { triggerMatches(it, message, isGroupChat) }
    }

    fun saveBot(context: Context, name: String, code: String) {
        val parsed = AutoReplyJson.parse(code, name)
        val normalized = parsed.copy(
            name = name,
            roomPattern = parsed.roomPattern.ifBlank { name }
        )
        val importTargetRoom = if (normalized.roomPattern.contains("*")) normalized.name else normalized.roomPattern
        val trimmedImport = normalized.importHistory.trim()
        if (trimmedImport.isNotBlank()) {
            RoomStore.importHistory(context, importTargetRoom, trimmedImport)
        }
        val stored = normalized.copy(importHistory = "")
        configFile(context, stored.name).writeText(AutoReplyJson.toJson(stored, includeImportHistory = true))
    }

    fun saveConfig(context: Context, config: AutoReplyConfig) {
        val normalized = config.copy(
            name = config.name.trim().ifBlank { config.roomPattern.ifBlank { "자동응답" } },
            roomPattern = config.roomPattern.trim().ifBlank { config.name.trim() }
        )
        configFile(context, normalized.name).writeText(AutoReplyJson.toJson(normalized, includeImportHistory = true))
    }

    fun getBotCode(context: Context, name: String): String {
        val file = configFile(context, name)
        return if (file.exists()) {
            val config = AutoReplyJson.parse(file.readText(), name).copy(importHistory = "")
            AutoReplyJson.toJson(config, includeImportHistory = true)
        } else {
            AutoReplyJson.toJson(AutoReplyJson.defaultConfig(name), includeImportHistory = true)
        }
    }

    fun deleteBot(context: Context, name: String) {
        val file = configFile(context, name)
        if (file.exists()) file.delete()
    }

    fun setBotEnabled(context: Context, name: String, isEnabled: Boolean) {
        val file = configFile(context, name)
        if (!file.exists()) return
        val updated = AutoReplyJson.parse(file.readText(), name).copy(enabled = isEnabled)
        file.writeText(AutoReplyJson.toJson(updated, includeImportHistory = true))
    }

    private fun ensureSampleConfig(context: Context) {
        val file = configFile(context, "기본 자동응답")
        if (!file.exists()) {
            file.writeText(AutoReplyJson.toJson(AutoReplyJson.defaultConfig("기본 자동응답"), includeImportHistory = true))
        }
    }

    private fun roomMatches(pattern: String, room: String): Boolean {
        val normalizedPattern = pattern.trim()
        if (normalizedPattern.isBlank()) return true
        if (normalizedPattern.contains("*")) {
            val regex = normalizedPattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .toRegex(setOf(RegexOption.IGNORE_CASE))
            return regex.matches(room)
        }
        return normalizedPattern.equals(room, true)
    }

    private fun senderAllowed(config: AutoReplyConfig, sender: String): Boolean {
        if (config.blockedSenders.any { it.equals(sender, true) }) {
            return false
        }
        if (config.allowedSenders.isEmpty()) {
            return true
        }
        return config.allowedSenders.any { it.equals(sender, true) }
    }

    internal fun triggerMatches(config: AutoReplyConfig, message: String, isGroupChat: Boolean): Boolean {
        val value = config.trigger.value.trim()
        return when (config.trigger.mode.lowercase()) {
            "always" -> true
            "prefix" -> value.isNotBlank() && message.startsWith(value, true)
            "keyword", "contains" -> value.split(',', '\n').map { it.trim() }.any { it.isNotBlank() && message.contains(it, true) }
            "mention" -> {
                val normalizedValue = value.removePrefix("@").trim()
                val candidates = buildList {
                    if (normalizedValue.isNotBlank()) add(normalizedValue)
                    if (normalizedValue.isNotBlank()) add("@$normalizedValue")
                    if (normalizedValue.isNotBlank()) add("${normalizedValue}아")
                    if (normalizedValue.isNotBlank()) add("${normalizedValue}야")
                }
                candidates.any { it.isNotBlank() && message.contains(it, true) }
            }
            "question" -> {
                val trimmed = message.trim()
                trimmed.endsWith("?") || trimmed.endsWith("？") || trimmed.contains("알려") || trimmed.contains("해줘") || trimmed.contains("부탁")
            }
            "ai_judge", "smart" -> true
            "direct" -> !isGroupChat
            else -> value.isBlank() || message.contains(value, true)
        }
    }

    private fun applyGlobalAiSettings(context: Context, config: AutoReplyConfig): AutoReplyConfig {
        val ai = AppSettings.getAiConfig(context)
        val autoPersonaHint = AutoMemoryStore.getPersonaHint(context, config.roomPattern, ai.displayName.ifBlank { "나" })
        val providerType = when (ai.provider.lowercase()) {
            "openai" -> "openai"
            "openrouter" -> "openai"
            "anthropic", "claude" -> "anthropic"
            "gemini" -> "gemini"
            else -> config.provider.type
        }
        val providerEndpoint = when (ai.provider.lowercase()) {
            "openrouter" -> "https://openrouter.ai/api/v1/chat/completions"
            else -> config.provider.endpoint
        }
        val trigger = resolveTrigger(ai, config)
        val toneGuide = when (ai.replyMode) {
            "간결하게" -> "한두 문장으로 짧고 자연스럽게 답해라."
            "조금 더 자세히" -> "필요한 맥락을 조금 더 붙여 설명하되 장황하지 않게 답해라."
            else -> "짧고 균형 있게 답해라."
        }
        val persona = buildString {
            append("사용자 이름: ")
            append(ai.displayName.ifBlank { "나" })
            append("\n")
            append("기본 페르소나: ")
            append(ai.persona.ifBlank { config.persona })
            append("\n")
            autoPersonaHint?.let {
                append("자동 페르소나 힌트: ")
                append(it)
                append("\n")
            }
            append("말투 지침: ")
            append(toneGuide)
        }
        return config.copy(
            persona = persona,
            trigger = trigger,
            provider = config.provider.copy(
                type = providerType,
                apiKey = ai.apiKey,
                endpoint = providerEndpoint,
                authMode = if (ai.apiKeyMode == "외부에서 관리") "external" else "api_key"
            )
        )
    }

    private fun resolveTrigger(ai: AppSettings.AiConfig, config: AutoReplyConfig): TriggerConfig {
        val normalizedMode = config.trigger.mode.trim().lowercase()
        val hasRoomSpecificTrigger = normalizedMode !in setOf("", "ai_judge", "smart") || config.trigger.value.isNotBlank()
        if (hasRoomSpecificTrigger) {
            return config.trigger
        }
        return when (ai.triggerMode) {
            "호출어/멘션만" -> TriggerConfig(mode = "mention", value = ai.displayName.trim())
            "질문/명령만" -> TriggerConfig(mode = "question", value = "")
            "모든 메시지" -> TriggerConfig(mode = "always", value = "")
            else -> TriggerConfig(mode = "ai_judge", value = "")
        }
    }

    private fun configFiles(context: Context): List<File> {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir.listFiles { _, fileName -> fileName.endsWith(".json") }?.toList() ?: emptyList()
    }

    private fun configFile(context: Context, name: String): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        val safe = name.trim().replace(Regex("[^a-zA-Z0-9가-힣._-]+"), "_").ifBlank { "config" }
        return File(dir, "$safe.json")
    }
}
