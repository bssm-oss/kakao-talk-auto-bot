package com.example.kakaotalkautobot

import android.content.Context
import android.util.Log

object AiProviderClient {
    private const val TAG = "AiProviderClient"

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

        // Ensure LLM is loaded
        if (!ensureLlmLoaded(context)) {
            return GenerationResult(failureReason = "LLM 모델이 로드되지 않았습니다. 모델 다운로드를 기다려주세요.")
        }

        // Check trigger conditions first (non-AI logic still applies)
        val judgeMode = config.trigger.mode.equals("ai_judge", true) || config.trigger.mode.equals("smart", true)

        // Low signal quick check - skip for very short meaningless messages
        if (isLowSignalMessage(normalizedMessage) && !hasRecentContext(history, 3)) {
            if (judgeMode) {
                return GenerationResult(skippedReason = "의미 없는 짧은 메시지입니다.")
            }
        }

        // Build the prompt and generate
        return try {
            val prompt = buildPrompt(config, room, sender, normalizedMessage, history)
            Log.d(TAG, "Prompt length: ${prompt.length} chars")

            val rawResponse = LlmEngine.generate(prompt, maxTokens = 256)
            val reply = cleanResponse(rawResponse)

            if (reply.isNotBlank()) {
                GenerationResult(reply = reply)
            } else {
                GenerationResult(failureReason = "AI가 빈 응답을 생성했습니다.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM generation failed", e)
            GenerationResult(failureReason = "AI 응답 생성 중 오류: ${e.message}")
        }
    }

    private fun ensureLlmLoaded(context: Context): Boolean {
        if (LlmEngine.isLoaded) return true

        if (!LlmModelManager.hasModel(context)) {
            return false
        }

        return LlmEngine.loadModel(context)
    }

    private fun buildPrompt(
        config: AutoReplyConfig,
        room: String,
        sender: String,
        message: String,
        history: List<RoomHistoryMessage>
    ): String {
        return buildString {
            // System instruction
            append("<|im_start|>system\n")
            append("너는 카카오톡 자동 응답 도우미다. 아래 규칙을 따른다:\n")
            append("1. 짧고 자연스럽게 카톡 답장처럼 답변해라 (한두 문장)\n")
            append("2. 모르는 것은 모른다고 말해라. 추측하지 마라.\n")
            append("3. 한국어로만 답변 영어를 섞지 마라.\n")
            append("4. 이모지, 읽음 표시, 확인 등의 과도한 표현은 자제해라.\n")
            append("5. 오직 답장 내용만 출력해라. 설명이나 부가 문구를 넣지 마라.\n")

            // Persona from config
            if (config.persona.isNotBlank()) {
                append("6. 다음 페르소나를 참고해라: ")
                append(config.persona.take(200))
                append("\n")
            }

            // Room memory
            if (config.roomMemory.isNotBlank()) {
                append("7. 다음 방 맥락을 참고해라: ")
                append(config.roomMemory.take(300))
                append("\n")
            }

            append("<|im_end|>\n")

            // Conversation history (recent messages)
            val recentHistory = history.takeLast(20)
            if (recentHistory.isNotEmpty()) {
                append("<|im_start|>user\n")
                append("이전 대화 맥락:\n")
                recentHistory.forEach { msg ->
                    val role = if (msg.incoming) "상대방" else "나"
                    append("- [$role/${msg.sender}] ${msg.message}\n")
                }
                append("<|im_end|>\n")
            }

            // Actual incoming message
            append("<|im_start|>user\n")
            append("[$sender]님이 보낸 메시지: $message\n")
            append("위 메시지에 대한 자연스러운 카톡 답장을 한두 문장으로 작성해라.\n")
            append("<|im_end|>\n")

            // Assistant response
            append("<|im_start|>assistant\n")
        }
    }

    private fun cleanResponse(raw: String): String {
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

    private fun isLowSignalMessage(message: String): Boolean {
        val lowered = message.lowercase()
        val lowSignalSet = setOf("ㅇㅋ", "ok", "넵", "네", "응", "ㅇㅇ", "ㅋㅋ", "ㅎㅎ", "ㄱㄱ")
        if (lowered in lowSignalSet) return true
        if (Regex("^[ㅋㅎㅠㅜ!?~. ]+$").matches(lowered)) return true
        return false
    }

    private fun hasRecentContext(history: List<RoomHistoryMessage>, minCount: Int): Boolean {
        val recent = history.takeLast(minCount)
        return recent.any { it.message.length > 10 }
    }
}
