package com.example.kakaotalkautobot

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object AiProviderClient {
    private val client = SharedHttpClient.instance
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    data class GenerationResult(
        val reply: String? = null,
        val failureReason: String? = null,
        val skippedReason: String? = null
    )

    fun generate(
        config: AutoReplyConfig,
        room: String,
        sender: String,
        message: String,
        history: List<RoomHistoryMessage>
    ): GenerationResult {
        val provider = config.provider
        if (!provider.authMode.equals("api_key", true)) {
            return GenerationResult(failureReason = "API Key 보관 방식이 '외부에서 관리'로 되어 있어 자동 응답을 생성할 수 없습니다.")
        }
        if (provider.apiKey.isBlank()) {
            return GenerationResult(failureReason = "${provider.type.ifBlank { "AI" }} API Key가 비어 있습니다.")
        }
        return when (provider.type.lowercase()) {
            "openai" -> callOpenAi(config, room, sender, message, history)
            "claude", "anthropic" -> callClaude(config, room, sender, message, history)
            "gemini", "google" -> callGemini(config, room, sender, message, history)
            else -> GenerationResult(failureReason = "지원하지 않는 공급자 유형입니다: ${provider.type}")
        }
    }

    private fun callOpenAi(
        config: AutoReplyConfig,
        room: String,
        sender: String,
        message: String,
        history: List<RoomHistoryMessage>
    ): GenerationResult {
        val payload = JSONObject()
            .put("model", config.provider.model.ifBlank { "gpt-4o-mini" })
            .put("messages", buildOpenAiMessages(config, room, sender, message, history))
        val request = Request.Builder()
            .url(config.provider.endpoint.ifBlank { "https://api.openai.com/v1/chat/completions" })
            .header("Authorization", "Bearer ${config.provider.apiKey}")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return GenerationResult(failureReason = "OpenAI 응답 실패 (${response.code})")
            }
            val body = response.body?.string().orEmpty()
            return normalizeGeneratedReply(
                config,
                JSONObject(body)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content", "")
                    ?.trim()
                    ?.ifBlank { null }
            )
        }
    }

    private fun callClaude(
        config: AutoReplyConfig,
        room: String,
        sender: String,
        message: String,
        history: List<RoomHistoryMessage>
    ): GenerationResult {
        val payload = JSONObject()
            .put("model", config.provider.model.ifBlank { "claude-3-5-haiku-latest" })
            .put("max_tokens", 300)
            .put("system", buildSystemPrompt(config, room))
            .put("messages", buildClaudeMessages(sender, message, history))
        val request = Request.Builder()
            .url(config.provider.endpoint.ifBlank { "https://api.anthropic.com/v1/messages" })
            .header("x-api-key", config.provider.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return GenerationResult(failureReason = "Claude 응답 실패 (${response.code})")
            }
            val body = response.body?.string().orEmpty()
            val content = JSONObject(body).optJSONArray("content")
                ?: return GenerationResult(failureReason = "Claude 응답 본문이 비어 있습니다.")
            return normalizeGeneratedReply(config, content.optJSONObject(0)?.optString("text", "")?.trim()?.ifBlank { null })
        }
    }

    private fun callGemini(
        config: AutoReplyConfig,
        room: String,
        sender: String,
        message: String,
        history: List<RoomHistoryMessage>
    ): GenerationResult {
        val model = config.provider.model.ifBlank { "gemini-1.5-flash" }
        val endpoint = config.provider.endpoint.ifBlank {
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${config.provider.apiKey}"
        }
        val payload = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", buildSystemPrompt(config, room)))))
            .put("contents", buildGeminiContents(sender, message, history))
        val request = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return GenerationResult(failureReason = "Gemini 응답 실패 (${response.code})")
            }
            val body = response.body?.string().orEmpty()
            return normalizeGeneratedReply(
                config,
                JSONObject(body)
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text", "")
                    ?.trim()
                    ?.ifBlank { null }
            )
        }
    }

    private fun buildSystemPrompt(config: AutoReplyConfig, room: String): String {
        return buildString {
            append("너는 카카오톡 방 '")
            append(room)
            append("' 에서 동작하는 자동응답 도우미다. 답변은 짧고 자연스럽게 작성한다.\n")
            if (config.persona.isNotBlank()) {
                append("페르소나: ")
                append(config.persona)
                append("\n")
            }
            if (config.roomMemory.isNotBlank()) {
                append("방 메모리: ")
                append(config.roomMemory)
                append("\n")
            }
            append("불확실하면 사실대로 말하고, 과장하거나 허위 사실을 만들지 마라.\n")
            if (config.trigger.mode.equals("ai_judge", true) || config.trigger.mode.equals("smart", true)) {
                append("지금 메시지에 끼어들 가치가 있는 경우에만 답장하라. 반드시 JSON 한 줄만 출력해라. 형식: {\"shouldReply\":true|false,\"reply\":\"답장 내용\"}. shouldReply가 false면 reply는 빈 문자열이어야 한다.")
            } else {
                append("답장 본문만 평문으로 출력해라.")
            }
        }
    }

    internal fun normalizeGeneratedReply(config: AutoReplyConfig, raw: String?): GenerationResult {
        val text = raw?.trim()?.removeSurrounding("```")?.trim()?.ifBlank { null }
            ?: return GenerationResult(failureReason = "AI 응답 본문이 비어 있습니다.")
        if (!config.trigger.mode.equals("ai_judge", true) && !config.trigger.mode.equals("smart", true)) {
            return GenerationResult(reply = text)
        }
        val jsonText = text
            .removePrefix("json")
            .trim()
            .let { candidate ->
                val start = candidate.indexOf('{')
                val end = candidate.lastIndexOf('}')
                if (start >= 0 && end > start) candidate.substring(start, end + 1) else candidate
            }
        return runCatching {
            val json = JSONObject(jsonText)
            val shouldReply = json.optBoolean("shouldReply", false)
            val reply = json.optString("reply", "").trim()
            when {
                shouldReply && reply.isNotBlank() -> GenerationResult(reply = reply)
                shouldReply -> GenerationResult(failureReason = "AI가 답장을 생성했지만 내용이 비어 있습니다.")
                else -> GenerationResult(skippedReason = "AI 판단에 따라 이번 메시지는 답장하지 않았습니다.")
            }
        }.getOrElse {
            GenerationResult(failureReason = "AI 판단 JSON을 해석하지 못했습니다.")
        }
    }

    private fun buildOpenAiMessages(config: AutoReplyConfig, room: String, sender: String, message: String, history: List<RoomHistoryMessage>): JSONArray {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", buildSystemPrompt(config, room)))
        history.forEach { item ->
            messages.put(
                JSONObject()
                    .put("role", if (item.incoming) "user" else "assistant")
                    .put("content", if (item.incoming) "${item.sender}: ${item.message}" else item.message)
            )
        }
        messages.put(JSONObject().put("role", "user").put("content", "$sender: $message"))
        return messages
    }

    private fun buildClaudeMessages(sender: String, message: String, history: List<RoomHistoryMessage>): JSONArray {
        val messages = JSONArray()
        history.forEach { item ->
            messages.put(
                JSONObject()
                    .put("role", if (item.incoming) "user" else "assistant")
                    .put("content", if (item.incoming) "${item.sender}: ${item.message}" else item.message)
            )
        }
        messages.put(JSONObject().put("role", "user").put("content", "$sender: $message"))
        return messages
    }

    private fun buildGeminiContents(sender: String, message: String, history: List<RoomHistoryMessage>): JSONArray {
        val contents = JSONArray()
        history.forEach { item ->
            contents.put(
                JSONObject()
                    .put("role", if (item.incoming) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", if (item.incoming) "${item.sender}: ${item.message}" else item.message)))
            )
        }
        contents.put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", "$sender: $message")))
        )
        return contents
    }
}
