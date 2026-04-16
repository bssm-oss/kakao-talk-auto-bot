package com.example.kakaotalkautobot

import android.content.Context
import android.util.Log

object AiProviderClient {
    private const val TAG = "AiProviderClient"
    private const val PRIMARY_HISTORY_LIMIT = 8
    private const val PRIMARY_PERSONA_LIMIT = 220
    private const val PRIMARY_ROOM_MEMORY_LIMIT = 320
    private const val COMPACT_HISTORY_LIMIT = 4
    private const val COMPACT_PERSONA_LIMIT = 100
    private const val COMPACT_ROOM_MEMORY_LIMIT = 160
    private const val EMERGENCY_HISTORY_LIMIT = 2
    private const val EMERGENCY_PERSONA_LIMIT = 60
    private const val EMERGENCY_ROOM_MEMORY_LIMIT = 80

    data class GenerationResult(
        val reply: String? = null,
        val failureReason: String? = null,
        val skippedReason: String? = null
    )

    fun generate(
        context: Context,
        config: AutoReplyConfig,
        room: String,
        sender: String,
        message: String,
        history: List<RoomHistoryMessage>
    ): GenerationResult {
        val normalizedMessage = message.trim()
        if (normalizedMessage.isBlank()) {
            return GenerationResult(skippedReason = "빈 메시지에는 답장하지 않습니다.")
        }

        findFastContextReply(normalizedMessage, history)?.let { fastReply ->
            Log.d(TAG, "Using fast context reply shortcut: '$fastReply'")
            return GenerationResult(reply = fastReply)
        }

        findFastDeadlineReply(normalizedMessage, history)?.let { fastReply ->
            Log.d(TAG, "Using fast deadline reply shortcut: '$fastReply'")
            return GenerationResult(reply = fastReply)
        }

        // Ensure LLM is loaded
        if (!ensureLlmLoaded(context)) {
            return GenerationResult(failureReason = "LLM 모델이 로드되지 않았습니다. 모델 다운로드를 기다려주세요.")
        }

        // Check trigger conditions first (non-AI logic still applies)
        val judgeMode = config.trigger.mode.equals("ai_judge", true) || config.trigger.mode.equals("smart", true)
        Log.d(TAG, "generate called: room=$room, sender=$sender, msg=$message, judgeMode=$judgeMode, triggerMode=${config.trigger.mode}")

        // Low signal quick check - skip for very short meaningless messages
        if (isLowSignalMessage(normalizedMessage) && !hasRecentContext(history, 3)) {
            if (judgeMode) {
                return GenerationResult(skippedReason = "의미 없는 짧은 메시지입니다.")
            }
        }

        // Build the prompt and generate
        return try {
            val prompt = buildPrompt(config, room, sender, normalizedMessage, history)
            Log.d(TAG, "Prompt length: ${prompt.length} chars, judgeMode=$judgeMode")
            Log.d(TAG, "Config: persona=${config.persona.take(30)}, roomMemory=${config.roomMemory.take(30)}, replyMode=${config.replyMode}")

            val rawResponse = generateWithFallbackPrompts(config, room, sender, normalizedMessage, history, prompt)

            if (rawResponse.isNotEmpty()) {
                Log.d(TAG, "Raw LLM response preview: '${rawResponse.take(100)}'")
            } else {
                Log.w(TAG, "LLM returned empty response after retry - model may not be generating properly")
            }

            val reply = cleanResponse(rawResponse)
            Log.d(TAG, "Cleaned reply: '$reply'")

            if (reply.isNotBlank()) {
                GenerationResult(reply = reply)
            } else {
                GenerationResult(failureReason = "AI가 빈 응답을 생성했습니다. (raw=${rawResponse.length}chars)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM generation failed", e)
            GenerationResult(failureReason = "AI 응답 생성 중 오류: ${e.message}")
        }
    }

    private fun ensureLlmLoaded(context: Context): Boolean {
        if (LlmEngine.isLoaded) return true

        val modelInfo = LlmModelManager.getModelInfo(context, LlmModelManager.DEFAULT_MODEL)
        if (!modelInfo.exists) {
            Log.w(TAG, "LLM model file does not exist at: ${LlmModelManager.getModelFile(context, LlmModelManager.DEFAULT_MODEL).absolutePath}")
            return false
        }
        if (!modelInfo.matchesExpectedSource) {
            Log.w(TAG, "LLM model file does not match production default: ${modelInfo.sizeMb}MB")
            return false
        }

        // Try to load with retry (max 3 attempts)
        for (attempt in 1..3) {
            val loaded = LlmEngine.loadModel(context, source = LlmModelManager.DEFAULT_MODEL)
            if (loaded) {
                Log.i(TAG, "LLM loaded successfully on attempt $attempt")
                return true
            }
            val lastError = LlmEngine.getLastError().orEmpty()
            if (lastError.contains("not supported on Android emulator", ignoreCase = true)) {
                Log.e(TAG, lastError)
                return false
            }
            Log.w(TAG, "LLM load attempt $attempt failed, retrying in 500ms...")
            Thread.sleep(500)
            LlmEngine.free()
        }
        Log.e(TAG, "Failed to load LLM after 3 attempts - model file may be corrupted or incompatible")
        return false
    }

    internal fun buildPrompt(
        config: AutoReplyConfig,
        room: String,
        sender: String,
        message: String,
        history: List<RoomHistoryMessage>
    ): String {
        return buildString {
            append("너는 카카오톡 자동 응답 도우미다. 아래 규칙을 따른다:\n")
            append("1. 짧고 자연스럽게 카톡 답장처럼 답변해라 (한두 문장)\n")
            append("2. 최근 대화나 방 메모에 답이 있으면 그 사실을 그대로 짧게 답해라.\n")
            append("3. 모르는 것은 모른다고 말해라. 추측하지 마라.\n")
            append("4. 한국어로만 답변 영어를 섞지 마라.\n")
            append("5. 이모지, 읽음 표시, 확인 등의 과도한 표현은 자제해라.\n")
            append("6. 오직 답장 내용만 출력해라. 설명이나 부가 문구를 넣지 마라.\n")
            append("7. 현재 메시지보다 먼저 페르소나, 방 메모, 최근 대화에서 근거를 찾고 그 말투와 사실을 유지해라.\n")

            // Persona from config
            if (config.persona.isNotBlank()) {
                append("페르소나:\n")
                append(config.persona.take(PRIMARY_PERSONA_LIMIT))
                append("\n")
            }

            // Room memory
            if (config.roomMemory.isNotBlank()) {
                append("방 메모/기억:\n")
                append(config.roomMemory.take(PRIMARY_ROOM_MEMORY_LIMIT))
                append("\n")
            }

            // Conversation history (recent messages)
            val recentHistory = history.takeLast(PRIMARY_HISTORY_LIMIT)
            if (recentHistory.isNotEmpty()) {
                append("이전 대화 맥락:\n")
                recentHistory.forEach { msg ->
                    val role = if (msg.incoming) "상대방" else "나"
                    append("- [$role/${msg.sender}] ${msg.message}\n")
                }
            }

            append("현재 방: $room\n")
            // Actual incoming message
            append("[$sender]님이 보낸 메시지: $message\n")
            append("위 메시지에 대해 최근 대화/방 메모/페르소나를 우선 참고해서 자연스러운 카톡 답장을 한두 문장으로 작성해라.\n")
            append("답장:\n")
        }
    }

    internal fun buildCompactPrompt(
        config: AutoReplyConfig,
        room: String,
        sender: String,
        message: String,
        history: List<RoomHistoryMessage>
    ): String {
        return buildString {
            append("짧고 자연스럽게 한국어 카톡 답장만 출력해라. 답을 모르면 짧게 모른다고 말해라. 최근 대화와 메모에 근거가 있으면 그걸 우선 써라.\n")
            if (config.persona.isNotBlank()) {
                append("페르소나: ${config.persona.take(COMPACT_PERSONA_LIMIT)}\n")
            }
            if (config.roomMemory.isNotBlank()) {
                append("방 메모: ${config.roomMemory.take(COMPACT_ROOM_MEMORY_LIMIT)}\n")
            }

            val recentHistory = history.takeLast(COMPACT_HISTORY_LIMIT)
            if (recentHistory.isNotEmpty()) {
                append("최근 대화:\n")
                recentHistory.forEach { msg ->
                    val role = if (msg.incoming) "상대" else "나"
                    append("[$role/${msg.sender}] ${msg.message}\n")
                }
            }

            append("방: $room\n")
            append("[$sender] $message\n")
            append("답장:\n")
        }
    }

    internal fun buildEmergencyPrompt(
        config: AutoReplyConfig,
        room: String,
        sender: String,
        message: String,
        history: List<RoomHistoryMessage>
    ): String {
        return buildString {
            append("한국어로 짧게 한 문장만 답해라. 설명하지 마라. 추측하지 말고 페르소나와 최근 맥락을 최대한 유지해라.\n")
            if (config.persona.isNotBlank()) {
                append("페르소나: ${config.persona.take(EMERGENCY_PERSONA_LIMIT)}\n")
            }
            if (config.roomMemory.isNotBlank()) {
                append("방 메모: ${config.roomMemory.take(EMERGENCY_ROOM_MEMORY_LIMIT)}\n")
            }
            val recentHistory = history.takeLast(EMERGENCY_HISTORY_LIMIT)
            if (recentHistory.isNotEmpty()) {
                append("최근:\n")
                recentHistory.forEach { msg ->
                    val role = if (msg.incoming) "상대" else "나"
                    append("[$role/${msg.sender}] ${msg.message}\n")
                }
            }
            append("방: $room\n")
            append("[$sender] $message\n")
            append("답장:\n")
        }
    }

    private fun generateWithFallbackPrompts(
        config: AutoReplyConfig,
        room: String,
        sender: String,
        message: String,
        history: List<RoomHistoryMessage>,
        prompt: String
    ): String {
        val primaryRawResponse = LlmEngine.generate(prompt, maxTokens = 12)
        Log.d(TAG, "Primary raw LLM response length: ${primaryRawResponse.length}")
        if (primaryRawResponse.isNotEmpty()) return primaryRawResponse

        Log.w(TAG, "LLM returned empty response on primary prompt, retrying with compact prompt")
        val compactPrompt = buildCompactPrompt(config, room, sender, message, history)
        Log.d(TAG, "Compact prompt length: ${compactPrompt.length} chars")
        val compactRawResponse = LlmEngine.generate(compactPrompt, maxTokens = 24)
        Log.d(TAG, "Retry raw LLM response length: ${compactRawResponse.length}")
        if (compactRawResponse.isNotEmpty()) return compactRawResponse

        Log.w(TAG, "LLM returned empty response after compact retry, retrying with emergency prompt")
        val emergencyPrompt = buildEmergencyPrompt(config, room, sender, message, history)
        Log.d(TAG, "Emergency prompt length: ${emergencyPrompt.length} chars")
        val emergencyRawResponse = LlmEngine.generate(emergencyPrompt, maxTokens = 24)
        Log.d(TAG, "Emergency raw LLM response length: ${emergencyRawResponse.length}")
        return emergencyRawResponse
    }

    internal fun cleanResponse(raw: String): String {
        var text = raw.trim()

        // Remove any markdown code blocks
        text = text.replace(Regex("```\\w*\\n?"), "")

        // Remove system-like prefixes if model outputs them
        text = text.replace(Regex("^<\\|im_start\\|>assistant\\n?"), "")
        text = text.replace(Regex("^<\\|im_end\\|>"), "")

        // Remove common AI filler phrases
        text = text.replace(Regex("^(답장:|답변:|메시지:)\\s*"), "")

        // Take only first line/sentence if too long
        if (text.length > 200) {
            val sentenceBreak = text.indexOfAny(charArrayOf('.', '!', '?'), 100)
            if (sentenceBreak > 0) {
                text = text.substring(0, sentenceBreak + 1)
            } else {
                text = text.take(150)
            }
        }

        return text.trim()
    }

    internal fun isLowSignalMessage(message: String): Boolean {
        val lowered = message.lowercase()
        val lowSignalSet = setOf("ㅇㅋ", "ok", "넵", "네", "응", "ㅇㅇ", "ㅋㅋ", "ㅎㅎ", "ㄱㄱ")
        if (lowered in lowSignalSet) return true
        if (Regex("^[ㅋㅎㅠㅜ!?~. ]+$").matches(lowered)) return true
        return false
    }

    internal fun hasRecentContext(history: List<RoomHistoryMessage>, minCount: Int): Boolean {
        val recent = history.takeLast(minCount)
        return recent.any { it.message.length > 10 }
    }

    internal fun findFastContextReply(
        message: String,
        history: List<RoomHistoryMessage>
    ): String? {
        val trimmed = message.trim()
        if (trimmed.isBlank()) return null

        val asksTime = trimmed.contains("몇 시") || trimmed.contains("언제")
        if (!asksTime) return null

        val keywords = extractContextKeywords(trimmed)
        if (keywords.isEmpty()) return null

        val timeRegex = Regex("(\\d{1,2})\\s*시(?:\\s*(?:반|[0-5]?\\d분))?")
        for (item in history.asReversed()) {
            val candidate = item.message.trim()
            if (candidate.isBlank()) continue
            if (!keywords.any { candidate.contains(it) }) continue

            val timeMatch = timeRegex.find(candidate) ?: continue
            return when {
                trimmed.contains("시작") -> "${timeMatch.value}에 시작해."
                trimmed.contains("끝") -> "${timeMatch.value}에 끝나."
                else -> candidate.replace(Regex("[!?]+$"), ".").take(60)
            }
        }

        return null
    }

    internal fun extractContextKeywords(message: String): List<String> {
        val stopwords = setOf(
            "오늘", "지금", "회의", "몇", "시", "언제", "이야", "야", "은", "는", "이", "가",
            "을", "를", "에", "의", "좀", "좀더", "혹시", "혹은", "그거", "그건", "이거", "그거야"
        )

        return Regex("[가-힣A-Za-z0-9]+").findAll(message)
            .map { it.value }
            .filter { token -> token.length >= 2 && token !in stopwords }
            .distinct()
            .toList()
    }

    internal fun findFastDeadlineReply(
        message: String,
        history: List<RoomHistoryMessage>
    ): String? {
        val normalized = message.trim()
        if (!(normalized.contains("언제까지") || normalized.contains("마감") || normalized.contains("데드라인"))) {
            return null
        }

        val combinedHistory = history.asReversed().joinToString("\n") { it.message }
        val explicitDeadline = Regex("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일").find(combinedHistory)
        if (explicitDeadline != null) {
            return "${explicitDeadline.value}까지로 알고 있어."
        }

        return "아직 일정이 확정된 건 못 찾았어. 정리되면 바로 공유할게."
    }
}
