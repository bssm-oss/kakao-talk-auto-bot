package com.example.kakaotalkautobot

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

object AiProviderClient {
    private const val TAG = "AiProviderClient"
    private const val PRIMARY_HISTORY_LIMIT = 8
    private const val PRIMARY_PERSONA_LIMIT = 220
    private const val PRIMARY_ROOM_MEMORY_LIMIT = 320
    private const val PRIMARY_STYLE_GUIDE_LIMIT = 640
    private const val STYLE_HISTORY_LIMIT = 6
    private const val STYLE_PERSONA_LIMIT = 160
    private const val STYLE_ROOM_MEMORY_LIMIT = 220
    private const val STYLE_GUIDE_LIMIT = 520
    private const val HUMAN_STYLE_HISTORY_LIMIT = 8
    private const val HUMAN_STYLE_PERSONA_LIMIT = 140
    private const val HUMAN_STYLE_ROOM_MEMORY_LIMIT = 200
    private const val HUMAN_STYLE_GUIDE_LIMIT = 720
    private const val COMPACT_HISTORY_LIMIT = 4
    private const val COMPACT_PERSONA_LIMIT = 100
    private const val COMPACT_ROOM_MEMORY_LIMIT = 160
    private const val COMPACT_STYLE_GUIDE_LIMIT = 360
    private const val EMERGENCY_HISTORY_LIMIT = 2
    private const val EMERGENCY_PERSONA_LIMIT = 60
    private const val EMERGENCY_ROOM_MEMORY_LIMIT = 80
    private const val EMERGENCY_STYLE_GUIDE_LIMIT = 180

    data class GenerationResult(
        val reply: String? = null,
        val failureReason: String? = null,
        val skippedReason: String? = null
    )

    internal data class LlmCandidateSpec(
        val source: String,
        val prompt: String,
        val maxTokens: Int
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

        // Check trigger conditions first (non-AI logic still applies)
        val judgeMode = config.trigger.mode.equals("ai_judge", true) || config.trigger.mode.equals("smart", true)
        Log.d(TAG, "generate called: room=$room, sender=$sender, msg=$message, judgeMode=$judgeMode, triggerMode=${config.trigger.mode}")

        // Low signal quick check - skip for very short meaningless messages
        if (shouldSkipLowSignalBeforeModelLoad(config, normalizedMessage, history)) {
            return GenerationResult(skippedReason = "의미 없는 짧은 메시지입니다.")
        }

        val deterministicCandidates = buildDeterministicCandidates(config, normalizedMessage, history)
        val groundedDeterministic = ReplyQualityEvaluator.selectBest(deterministicCandidates)
            ?.takeIf { candidate ->
                candidate.score >= 90 && candidate.reasons.contains("grounded_fact")
            }
        if (groundedDeterministic != null) {
            Log.d(
                TAG,
                "Using grounded deterministic reply source=${groundedDeterministic.source}, score=${groundedDeterministic.score}"
            )
            return GenerationResult(reply = groundedDeterministic.reply)
        }

        // Ensure LLM is loaded only after deterministic skip paths are resolved.
        if (!ensureLlmLoaded(context)) {
            val fallbackCandidate = ReplyQualityEvaluator.selectBest(deterministicCandidates)
            if (fallbackCandidate != null) {
                Log.d(
                    TAG,
                    "Using deterministic fallback without loaded model source=${fallbackCandidate.source}, score=${fallbackCandidate.score}"
                )
                return GenerationResult(reply = fallbackCandidate.reply)
            }
            return GenerationResult(failureReason = "LLM 모델이 로드되지 않았습니다. 모델 다운로드를 기다려주세요.")
        }

        // Build the prompt and generate
        return try {
            val aiConfig = AppSettings.getAiConfig(context)
            val styleGuide = StyleProfileStore.buildPromptStyleGuide(context, aiConfig, config, room, history)
            val prompt = buildPrompt(config, room, sender, normalizedMessage, history, styleGuide)
            Log.d(TAG, "Prompt length: ${prompt.length} chars, judgeMode=$judgeMode")
            Log.d(TAG, "Config: persona=${config.persona.take(30)}, roomMemory=${config.roomMemory.take(30)}, replyMode=${config.replyMode}")

            val candidateStats = ReplyCandidateStatsStore.snapshot(context)
            val sourcePriors = candidateStats.selectionPriors()
            val candidates = deterministicCandidates + generateCandidateReplies(config, room, sender, normalizedMessage, history, prompt, styleGuide)
            val selectionCandidates = ReplyQualityEvaluator.dedupeCandidates(candidates)
            logCandidateSummary(selectionCandidates)
            val bestCandidate = ReplyQualityEvaluator.selectBest(selectionCandidates, sourcePriors)
            ReplyCandidateStatsStore.recordBatch(context, selectionCandidates, bestCandidate?.source)
            val rawResponse = bestCandidate?.raw.orEmpty()

            if (bestCandidate != null) {
                Log.d(
                    TAG,
                    "Selected LLM reply source=${bestCandidate.source}, score=${bestCandidate.score}, sourcePrior=${sourcePriors.getOrDefault(bestCandidate.source, 0)}, reasons=${bestCandidate.reasons}"
                )
                Log.d(TAG, "Raw LLM response preview: '${rawResponse.take(100)}'")
            } else {
                Log.w(TAG, "LLM candidates were empty or below quality threshold")
            }

            val reply = bestCandidate?.reply.orEmpty()
            Log.d(TAG, "Cleaned reply: '$reply'")

            if (reply.isNotBlank()) {
                GenerationResult(reply = reply)
            } else {
                GenerationResult(failureReason = "AI가 전송 가능한 품질의 응답을 만들지 못했습니다. (candidates=${candidates.size})")
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
        history: List<RoomHistoryMessage>,
        styleGuide: String = ""
    ): String {
        return buildString {
            append("너는 카카오톡 자동 응답 도우미다. 아래 규칙을 따른다:\n")
            append("1. 짧고 자연스럽게 카톡 답장처럼 답변해라 (한두 문장)\n")
            append("2. 최근 대화나 방 메모에 답이 있으면 그 사실을 그대로 짧게 답해라.\n")
            append("3. 모르는 것은 모른다고 말해라. 추측하지 마라.\n")
            append("4. 한국어로만 답변 영어를 섞지 마라.\n")
            append("5. 이모지, 읽음 표시, 확인 등의 과도한 표현은 자제해라.\n")
            append("6. 오직 답장 내용만 출력해라. 설명이나 부가 문구를 넣지 마라.\n")
            append("7. 말투는 사용자 직접 예시, 수동 방 스타일, 학습된 사용자/방 스타일을 우선 적용해라.\n")
            append("8. 사실은 현재 메시지보다 먼저 방 메모와 최근 대화에서 근거를 찾고, 모르면 추측하지 마라.\n")

            if (styleGuide.isNotBlank()) {
                append(styleGuide.take(PRIMARY_STYLE_GUIDE_LIMIT))
                append("\n")
            }

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
        history: List<RoomHistoryMessage>,
        styleGuide: String = ""
    ): String {
        return buildString {
            append("짧고 자연스럽게 한국어 카톡 답장만 출력해라. 답을 모르면 짧게 모른다고 말해라. 최근 대화와 메모에 근거가 있으면 그걸 우선 써라.\n")
            if (styleGuide.isNotBlank()) {
                append(styleGuide.take(COMPACT_STYLE_GUIDE_LIMIT))
                append("\n")
            }
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

    internal fun buildStyleRewritePrompt(
        config: AutoReplyConfig,
        room: String,
        sender: String,
        message: String,
        history: List<RoomHistoryMessage>,
        styleGuide: String = ""
    ): String {
        return buildString {
            append("아래 대화에서 '나'가 실제로 보낼 법한 카카오톡 답장만 써라.\n")
            append("사용자 직접 예시와 수동 방 말투가 있으면 그것을 최우선으로 흉내내라.\n")
            append("모르는 사실은 지어내지 말고 짧게 모른다고 하거나 확인 질문을 해라.\n")
            append("챗봇처럼 설명하지 말고 한 문장으로 끝내라.\n")
            if (styleGuide.isNotBlank()) {
                append(styleGuide.take(STYLE_GUIDE_LIMIT))
                append("\n")
            }
            if (config.persona.isNotBlank()) {
                append("페르소나: ${config.persona.take(STYLE_PERSONA_LIMIT)}\n")
            }
            if (config.roomStyle.isNotBlank()) {
                append("수동 방 말투: ${config.roomStyle.take(STYLE_GUIDE_LIMIT)}\n")
            }
            if (config.roomMemory.isNotBlank()) {
                append("방 메모: ${config.roomMemory.take(STYLE_ROOM_MEMORY_LIMIT)}\n")
            }
            val recentHistory = history.takeLast(STYLE_HISTORY_LIMIT)
            if (recentHistory.isNotEmpty()) {
                append("최근 대화:\n")
                recentHistory.forEach { msg ->
                    val role = if (msg.incoming) "상대" else "나"
                    append("[$role/${msg.sender}] ${msg.message}\n")
                }
            }
            append("방: $room\n")
            append("[$sender] $message\n")
            append("내가 보낼 답장:\n")
        }
    }

    internal fun buildHumanStylePrompt(
        config: AutoReplyConfig,
        room: String,
        sender: String,
        message: String,
        history: List<RoomHistoryMessage>,
        styleGuide: String = ""
    ): String {
        return buildString {
            append("너는 자동응답기가 아니라 이 방의 실제 사용자처럼 답장한다.\n")
            append("사용자 직접 예시가 있으면 단어 선택, 길이, 반말/존댓말, 느낌을 최우선으로 맞춘다.\n")
            append("수동 방 말투가 있으면 학습된 말투보다 우선한다.\n")
            append("방 메모나 최근 대화에 없는 사실은 만들지 말고, 모르면 짧게 모른다고 답한다.\n")
            append("카톡에서 보낼 한 문장만 출력하고 설명, 후보, 따옴표, AI 티를 내는 말은 쓰지 않는다.\n")
            if (styleGuide.isNotBlank()) {
                append(styleGuide.take(HUMAN_STYLE_GUIDE_LIMIT))
                append("\n")
            }
            if (config.persona.isNotBlank()) {
                append("페르소나: ${config.persona.take(HUMAN_STYLE_PERSONA_LIMIT)}\n")
            }
            if (config.roomStyle.isNotBlank()) {
                append("수동 방 말투: ${config.roomStyle.take(HUMAN_STYLE_GUIDE_LIMIT)}\n")
            }
            if (config.roomMemory.isNotBlank()) {
                append("방 메모: ${config.roomMemory.take(HUMAN_STYLE_ROOM_MEMORY_LIMIT)}\n")
            }
            val recentHistory = history.takeLast(HUMAN_STYLE_HISTORY_LIMIT)
            if (recentHistory.isNotEmpty()) {
                append("최근 대화:\n")
                recentHistory.forEach { msg ->
                    val role = if (msg.incoming) "상대" else "나"
                    append("[$role/${msg.sender}] ${msg.message}\n")
                }
            }
            append("방: $room\n")
            append("상대: $sender\n")
            append("받은 말: $message\n")
            append("내 답장:\n")
        }
    }

    internal fun buildEmergencyPrompt(
        config: AutoReplyConfig,
        room: String,
        sender: String,
        message: String,
        history: List<RoomHistoryMessage>,
        styleGuide: String = ""
    ): String {
        return buildString {
            append("한국어로 짧게 한 문장만 답해라. 설명하지 마라. 추측하지 말고 페르소나와 최근 맥락을 최대한 유지해라.\n")
            if (styleGuide.isNotBlank()) {
                append(styleGuide.take(EMERGENCY_STYLE_GUIDE_LIMIT))
                append("\n")
            }
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

    internal fun generateCandidateReplies(
        config: AutoReplyConfig,
        room: String,
        sender: String,
        message: String,
        history: List<RoomHistoryMessage>,
        prompt: String,
        styleGuide: String
    ): List<ReplyQualityEvaluator.Candidate> {
        val initialSpecs = buildInitialCandidateSpecs(config, room, sender, message, history, prompt, styleGuide)
        Log.d(TAG, "Generating ${initialSpecs.size} LLM candidate lanes for internal comparison")
        val candidates = generateCandidatesFromSpecs(initialSpecs, config, message, history).toMutableList()

        if (candidates.any { it.score >= 80 }) {
            return candidates
        }

        val emergencySpec = buildEmergencyCandidateSpec(config, room, sender, message, history, styleGuide)
        Log.d(TAG, "Generating emergency candidate because quality is still weak")
        candidates += generateCandidatesFromSpecs(listOf(emergencySpec), config, message, history)
        return candidates
    }

    internal fun buildInitialCandidateSpecs(
        config: AutoReplyConfig,
        room: String,
        sender: String,
        message: String,
        history: List<RoomHistoryMessage>,
        primaryPrompt: String,
        styleGuide: String
    ): List<LlmCandidateSpec> {
        return listOf(
            LlmCandidateSpec(
                source = "primary",
                prompt = primaryPrompt,
                maxTokens = 12
            ),
            LlmCandidateSpec(
                source = "style_rewrite",
                prompt = buildStyleRewritePrompt(config, room, sender, message, history, styleGuide),
                maxTokens = 18
            ),
            LlmCandidateSpec(
                source = "human_style",
                prompt = buildHumanStylePrompt(config, room, sender, message, history, styleGuide),
                maxTokens = 18
            ),
            LlmCandidateSpec(
                source = "compact",
                prompt = buildCompactPrompt(config, room, sender, message, history, styleGuide),
                maxTokens = 24
            )
        )
    }

    private fun logCandidateSummary(candidates: List<ReplyQualityEvaluator.Candidate>) {
        if (candidates.isEmpty()) {
            Log.w(TAG, "No reply candidates were available for quality selection")
            return
        }
        candidates.forEach { candidate ->
            Log.d(
                TAG,
                "Candidate source=${candidate.source}, score=${candidate.score}, reasons=${candidate.reasons}, replyLength=${candidate.reply.length}, latencyMs=${candidate.latencyMs}"
            )
        }
    }

    internal fun buildEmergencyCandidateSpec(
        config: AutoReplyConfig,
        room: String,
        sender: String,
        message: String,
        history: List<RoomHistoryMessage>,
        styleGuide: String
    ): LlmCandidateSpec {
        return LlmCandidateSpec(
            source = "emergency",
            prompt = buildEmergencyPrompt(config, room, sender, message, history, styleGuide),
            maxTokens = 24
        )
    }

    private fun generateCandidatesFromSpecs(
        specs: List<LlmCandidateSpec>,
        config: AutoReplyConfig,
        message: String,
        history: List<RoomHistoryMessage>
    ): List<ReplyQualityEvaluator.Candidate> {
        if (specs.isEmpty()) return emptyList()
        return runBlocking {
            specs.map { spec ->
                async(Dispatchers.Default) {
                    Log.d(TAG, "Generating ${spec.source} candidate prompt length=${spec.prompt.length} chars")
                    val startedAt = System.currentTimeMillis()
                    val rawResponse = LlmEngine.generate(spec.prompt, maxTokens = spec.maxTokens)
                    val latencyMs = System.currentTimeMillis() - startedAt
                    Log.d(TAG, "${spec.source} raw LLM response length: ${rawResponse.length}, latencyMs=$latencyMs")
                    evaluateCandidate(spec.source, rawResponse, config, message, history, latencyMs)
                }
            }.awaitAll()
        }
    }

    internal fun buildDeterministicCandidates(
        config: AutoReplyConfig,
        message: String,
        history: List<RoomHistoryMessage>
    ): List<ReplyQualityEvaluator.Candidate> {
        return listOfNotNull(
            findFastContextReply(message, history)?.let { reply ->
                evaluateDeterministicCandidate("context_fact", reply, config, message, history)
            },
            findFastDeadlineReply(message, config, history)?.let { reply ->
                evaluateDeterministicCandidate("deadline_fact", reply, config, message, history)
            },
            findAmbiguousClarificationReply(config, message, history)?.let { reply ->
                evaluateDeterministicCandidate("ambiguous_clarify", reply, config, message, history)
            },
            findUnknownFactGuardReply(config, message, history)?.let { reply ->
                evaluateDeterministicCandidate("unknown_guard", reply, config, message, history)
            }
        )
    }

    private fun evaluateDeterministicCandidate(
        source: String,
        reply: String,
        config: AutoReplyConfig,
        message: String,
        history: List<RoomHistoryMessage>
    ): ReplyQualityEvaluator.Candidate {
        return ReplyQualityEvaluator.evaluate(
            source = source,
            raw = reply,
            reply = reply,
            config = config,
            message = message,
            history = history
        )
    }

    private fun evaluateCandidate(
        source: String,
        raw: String,
        config: AutoReplyConfig,
        message: String,
        history: List<RoomHistoryMessage>,
        latencyMs: Long = 0L
    ): ReplyQualityEvaluator.Candidate {
        return ReplyQualityEvaluator.evaluate(
            source = source,
            raw = raw,
            reply = cleanResponse(raw),
            config = config,
            message = message,
            history = history,
            latencyMs = latencyMs
        )
    }

    internal fun cleanResponse(raw: String): String {
        var text = raw.trim()

        // Remove any markdown code blocks
        text = text.replace(Regex("```\\w*\\n?"), "")

        // Remove system-like prefixes if model outputs them
        text = text.replace(Regex("^<\\|im_start\\|>assistant\\n?"), "")
        text = text.replace(Regex("^<\\|im_end\\|>"), "")

        // Remove common AI filler phrases
        text = text.replace(Regex("^(답장:|답변:|메시지:|assistant:)\\s*", RegexOption.IGNORE_CASE), "")
        text = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

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

    internal fun shouldSkipLowSignalBeforeModelLoad(
        config: AutoReplyConfig,
        message: String,
        history: List<RoomHistoryMessage>
    ): Boolean {
        val judgeMode = config.trigger.mode.equals("ai_judge", true) || config.trigger.mode.equals("smart", true)
        return judgeMode && isLowSignalMessage(message) && !hasRecentContext(history, 3)
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
        return findFastDeadlineReply(message, AutoReplyJson.defaultConfig(""), history)
    }

    internal fun findFastDeadlineReply(
        message: String,
        config: AutoReplyConfig,
        history: List<RoomHistoryMessage>
    ): String? {
        val normalized = message.trim()
        if (!(normalized.contains("언제까지") || normalized.contains("마감") || normalized.contains("데드라인"))) {
            return null
        }

        val combinedFacts = buildString {
            if (config.roomMemory.isNotBlank()) appendLine(config.roomMemory)
            history.asReversed().forEach { appendLine(it.message) }
        }
        val explicitDeadline = Regex("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일(?:\\s*\\d{1,2}\\s*시)?").find(combinedFacts)
        if (explicitDeadline != null) {
            return if (!prefersCasualTone(config) && prefersFormalTone(config)) {
                "${explicitDeadline.value}까지입니다."
            } else {
                "${explicitDeadline.value}까지로 알고 있어."
            }
        }

        return if (!prefersCasualTone(config) && prefersFormalTone(config)) {
            "아직 일정이 확정된 내용은 못 찾았습니다."
        } else {
            "아직 일정이 확정된 건 못 찾았어. 정리되면 바로 공유할게."
        }
    }

    internal fun findUnknownFactGuardReply(
        config: AutoReplyConfig,
        message: String,
        history: List<RoomHistoryMessage>
    ): String? {
        val normalized = message.trim()
        if (!asksSpecificFact(normalized)) return null
        val hasGrounding = hasUsableRoomMemory(config.roomMemory) || history.any { it.message.length >= 10 }
        if (hasGrounding) return null
        return if (prefersFormalTone(config)) {
            "아직 확인된 내용은 못 찾았습니다."
        } else {
            "아직 확인된 건 못 찾았어."
        }
    }

    internal fun findAmbiguousClarificationReply(
        config: AutoReplyConfig,
        message: String,
        history: List<RoomHistoryMessage>
    ): String? {
        val normalized = message.trim()
        if (!isAmbiguousReference(normalized)) return null

        val topics = extractClarificationTopics(history)
        if (topics.size < 2) return null

        val first = topics[0]
        val second = topics[1]
        return if (!prefersCasualTone(config) && prefersFormalTone(config)) {
            "$first 말씀하시는 건가요, $second 말씀하시는 건가요?"
        } else {
            "$first 말하는 거야, $second 말하는 거야?"
        }
    }

    private fun isAmbiguousReference(message: String): Boolean {
        val normalized = message.trim()
        if (normalized.length > 30) return false
        return listOf("그거", "그건", "그건?", "이거", "그때", "그 일", "그것").any {
            normalized.contains(it)
        }
    }

    private fun extractClarificationTopics(history: List<RoomHistoryMessage>): List<String> {
        val topicCandidates = listOf(
            "문서 초안" to listOf("문서 초안", "문서", "초안"),
            "발표 자료" to listOf("발표 자료", "발표", "자료"),
            "회의 일정" to listOf("회의 일정", "회의", "일정"),
            "제출 마감" to listOf("제출 마감", "제출", "마감"),
            "장소" to listOf("장소", "어디", "회의실", "과학실"),
            "모델 다운로드" to listOf("모델 다운로드", "다운로드", "모델")
        )
        val recentText = history
            .takeLast(6)
            .joinToString("\n") { it.message }
        return topicCandidates
            .filter { (_, needles) -> needles.any { recentText.contains(it) } }
            .map { it.first }
            .distinct()
            .take(2)
    }

    private fun asksSpecificFact(message: String): Boolean {
        return listOf("언제", "몇 시", "몇시", "마감", "일정", "제출", "발표", "어디", "누구").any {
            message.contains(it)
        }
    }

    private fun hasUsableRoomMemory(roomMemory: String): Boolean {
        val normalized = roomMemory.trim()
        if (normalized.length < 8) return false
        return !normalized.contains("이 방의 맥락, 금지어, 말투를 간단히 적어두세요")
    }

    private fun prefersFormalTone(config: AutoReplyConfig): Boolean {
        val style = "${config.roomStyle}\n${config.persona}"
        return style.contains("존댓말") ||
            style.contains("학교") ||
            style.contains("팀") ||
            style.contains("습니다") ||
            style.contains("정중")
    }

    private fun prefersCasualTone(config: AutoReplyConfig): Boolean {
        val style = "${config.roomStyle}\n${config.persona}"
        return style.contains("반말") ||
            style.contains("친한") ||
            style.contains("가볍") ||
            style.contains("캐주얼")
    }
}
