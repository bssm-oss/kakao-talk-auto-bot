package com.example.kakaotalkautobot

import org.json.JSONArray
import org.json.JSONObject

data class ProviderConfig(
    val type: String = "llm",
    val apiKey: String = "",
    val model: String = "gemma-4-e2b-it-litertlm",
    val endpoint: String = "",
    val authMode: String = "local"
)

data class TriggerConfig(
    val mode: String = "ai_judge",
    val value: String = ""
)

data class AutoReplyConfig(
    val name: String,
    val roomPattern: String = name,
    val enabled: Boolean = true,
    val captureEnabled: Boolean = true,
    val replyEnabled: Boolean = true,
    val replyMode: String = "provider",
    val persona: String = "친절하고 간결한 카카오톡 자동응답 도우미",
    val roomMemory: String = "",
    val allowedSenders: List<String> = emptyList(),
    val blockedSenders: List<String> = emptyList(),
    val cannedReplies: List<String> = emptyList(),
    val trigger: TriggerConfig = TriggerConfig(),
    val provider: ProviderConfig = ProviderConfig(),
    val importHistory: String = ""
)

object AutoReplyJson {
    private const val LOCAL_PROVIDER_TYPE = "llm"
    private const val LOCAL_PROVIDER_MODEL = "gemma-4-e2b-it-litertlm"
    private val LEGACY_LOCAL_PROVIDER_TYPES = setOf("", "local", "local-gguf", "local-litertlm", LOCAL_PROVIDER_TYPE)
    private val LEGACY_LOCAL_PROVIDER_MODELS = setOf("", "local", "local-gguf", "local-litertlm", "gemma4", "gemma-4", LOCAL_PROVIDER_MODEL)

    fun defaultConfig(name: String = "새 자동응답"): AutoReplyConfig {
        return AutoReplyConfig(
            name = name,
            roomPattern = name,
            persona = "너는 카카오톡 자동응답 도우미다. 짧고 자연스럽게 답하고, 모르면 추측하지 말고 솔직히 말해라.",
            roomMemory = "이 방의 맥락, 금지어, 말투를 간단히 적어두세요.",
            cannedReplies = listOf("확인했어요.", "조금 뒤에 답할게요."),
            trigger = TriggerConfig(mode = "ai_judge", value = ""),
            provider = ProviderConfig(type = LOCAL_PROVIDER_TYPE, model = LOCAL_PROVIDER_MODEL)
        )
    }

    fun parse(text: String, fallbackName: String = "새 자동응답"): AutoReplyConfig {
        val json = JSONObject(text)
        val name = json.optString("name", fallbackName).ifBlank { fallbackName }
        val triggerJson = json.optJSONObject("trigger") ?: JSONObject()
        val providerJson = json.optJSONObject("provider") ?: JSONObject()
        return AutoReplyConfig(
            name = name,
            roomPattern = json.optString("roomPattern", name),
            enabled = json.optBoolean("enabled", true),
            captureEnabled = json.optBoolean("captureEnabled", true),
            replyEnabled = json.optBoolean("replyEnabled", true),
            replyMode = json.optString("replyMode", "provider"),
            persona = json.optString("persona", defaultConfig(name).persona),
            roomMemory = json.optString("roomMemory", ""),
            allowedSenders = json.optJSONArray("allowedSenders").toStringList(),
            blockedSenders = json.optJSONArray("blockedSenders").toStringList(),
            cannedReplies = json.optJSONArray("cannedReplies").toStringList(),
            trigger = TriggerConfig(
                mode = triggerJson.optString("mode", "always"),
                value = triggerJson.optString("value", "")
            ),
            provider = normalizeProvider(
                ProviderConfig(
                    type = providerJson.optString("type", LOCAL_PROVIDER_TYPE),
                apiKey = providerJson.optString("apiKey", ""),
                model = providerJson.optString("model", ""),
                endpoint = providerJson.optString("endpoint", ""),
                authMode = providerJson.optString("authMode", "local")
                )
            ),
            importHistory = json.optString("importHistory", "")
        )
    }

    fun toJson(config: AutoReplyConfig, includeImportHistory: Boolean = false): String {
        val normalizedProvider = normalizeProvider(config.provider)
        val json = JSONObject()
            .put("name", config.name)
            .put("roomPattern", config.roomPattern)
            .put("enabled", config.enabled)
            .put("captureEnabled", config.captureEnabled)
            .put("replyEnabled", config.replyEnabled)
            .put("replyMode", config.replyMode)
            .put("persona", config.persona)
            .put("roomMemory", config.roomMemory)
            .put("allowedSenders", JSONArray(config.allowedSenders))
            .put("blockedSenders", JSONArray(config.blockedSenders))
            .put("cannedReplies", JSONArray(config.cannedReplies))
            .put(
                "trigger",
                JSONObject()
                    .put("mode", config.trigger.mode)
                    .put("value", config.trigger.value)
            )
            .put(
                "provider",
                JSONObject()
                    .put("type", normalizedProvider.type)
                    .put("apiKey", normalizedProvider.apiKey)
                    .put("model", normalizedProvider.model)
                    .put("endpoint", normalizedProvider.endpoint)
                    .put("authMode", normalizedProvider.authMode)
            )
        if (includeImportHistory) {
            json.put("importHistory", config.importHistory)
        }
        return json.toString(2)
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val items = mutableListOf<String>()
        for (index in 0 until length()) {
            val value = optString(index).trim()
            if (value.isNotBlank()) items.add(value)
        }
        return items
    }

    private fun normalizeProvider(provider: ProviderConfig): ProviderConfig {
        val normalizedType = provider.type.trim().lowercase()
        val normalizedModel = provider.model.trim().lowercase()
        val shouldUseLocalProvider = normalizedType in LEGACY_LOCAL_PROVIDER_TYPES || normalizedType == "openai"

        return if (shouldUseLocalProvider) {
            provider.copy(
                type = LOCAL_PROVIDER_TYPE,
                apiKey = "",
                model = if (normalizedModel in LEGACY_LOCAL_PROVIDER_MODELS || normalizedType == "openai") {
                    LOCAL_PROVIDER_MODEL
                } else {
                    provider.model.trim()
                },
                endpoint = "",
                authMode = "local"
            )
        } else {
            provider.copy(
                type = LOCAL_PROVIDER_TYPE,
                apiKey = "",
                model = LOCAL_PROVIDER_MODEL,
                endpoint = "",
                authMode = "local"
            )
        }
    }
}
