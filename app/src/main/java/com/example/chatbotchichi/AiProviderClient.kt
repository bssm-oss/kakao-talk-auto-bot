package com.example.kakaotalkautobot

import org.json.JSONObject
import java.util.Locale

object AiProviderClient {
    data class GenerationResult(
        val reply: String? = null,
        val failureReason: String? = null,
        val skippedReason: String? = null
    )

    private val tokenRegex = Regex("""[A-Za-z0-9가-힣._@-]{2,}""")
    private val stopwords = setOf(
        "그리고", "그러면", "그냥", "이거", "저거", "오늘", "내일", "지금", "방금",
        "일단", "혹시", "정도", "관련", "문의", "확인", "부탁", "좀", "the", "and"
    )
    private val lowSignalMessages = setOf("ㅇㅋ", "ok", "넵", "네", "응", "ㅇㅇ", "ㅋㅋ", "ㅎㅎ", "ㄱㄱ")
    private val trimSuffixes = listOf(
        "입니다", "이에요", "예요", "이네요", "하네요", "해요", "했어요", "합니다",
        "이야", "야", "요", "은", "는", "이", "가", "을", "를", "에", "의",
        "와", "과", "도", "만", "랑", "으로", "로", "해", "줘"
    )

    fun generate(
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

        val judgeMode = config.trigger.mode.equals("ai_judge", true) || config.trigger.mode.equals("smart", true)
        val tone = selectTone(config.persona)
        val signal = analyzeSignal(config, sender, normalizedMessage)
        val candidate = bestCandidate(config, sender, normalizedMessage, history)

        if (judgeMode && shouldAbstain(signal, candidate)) {
            return GenerationResult(skippedReason = "로컬 판단 결과 이번 메시지는 개입하지 않았습니다.")
        }

        signal.fixedReply(tone)?.let { return GenerationResult(reply = it) }

        if (candidate != null && candidate.score >= 1.6) {
            val grounded = groundedReply(signal, candidate, tone)
            if (grounded != null) {
                return GenerationResult(reply = grounded)
            }
        }

        if (judgeMode) {
            return GenerationResult(skippedReason = "로컬 판단 결과 확신이 낮아 답장을 보류했습니다.")
        }

        return GenerationResult(reply = tone.uncertainReply)
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

    private fun bestCandidate(
        config: AutoReplyConfig,
        sender: String,
        message: String,
        history: List<RoomHistoryMessage>
    ): ScoredCandidate? {
        val messageTokens = tokenize(message)
        val memoryCandidates = config.roomMemory.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapIndexed { index, line ->
                val parsed = parseMemoryLine(line)
                ContextCandidate(
                    text = parsed,
                    source = if (line.startsWith("[CSV 가져오기:")) "csv_header" else "memory",
                    order = index
                )
            }
            .filter { it.text.isNotBlank() }

        val historyCandidates = history.mapIndexed { index, item ->
            ContextCandidate(
                text = item.message.trim(),
                source = if (item.incoming) "history_in" else "history_out",
                sender = item.sender.trim(),
                order = index
            )
        }.filter { it.text.isNotBlank() }

        return (memoryCandidates + historyCandidates)
            .mapNotNull { candidate ->
                scoreCandidate(message, messageTokens, sender, candidate)
            }
            .sortedWith(compareByDescending<ScoredCandidate> { it.score }.thenByDescending { it.order })
            .firstOrNull()
    }

    private fun scoreCandidate(
        message: String,
        messageTokens: Set<String>,
        sender: String,
        candidate: ContextCandidate
    ): ScoredCandidate? {
        if (candidate.source == "csv_header") return null
        if (candidate.text.equals(message, ignoreCase = true)) return null

        val candidateTokens = tokenize(candidate.text)
        if (candidateTokens.isEmpty() && !containsDigits(candidate.text)) return null

        var score = overlapScore(messageTokens, candidateTokens)
        if (containsDigits(message) && containsDigits(candidate.text)) {
            score += 0.9
        }
        if (candidate.sender.equals(sender, ignoreCase = true)) {
            score -= 0.2
        }
        if (candidate.source == "memory") {
            score += 0.25
        }
        if (candidate.source == "history_out") {
            score += 0.1
        }
        if (candidate.text.length <= 60) {
            score += 0.15
        }

        return if (score >= 0.8) ScoredCandidate(candidate.text, candidate.source, candidate.order, score) else null
    }

    private fun analyzeSignal(config: AutoReplyConfig, sender: String, message: String): MessageSignal {
        val lowered = message.lowercase(Locale.ROOT)
        val mentionsDisplayName = config.persona.lineSequence()
            .firstOrNull { it.startsWith("사용자 이름:") }
            ?.substringAfter(':')
            ?.trim()
            ?.let { displayName ->
                displayName.isNotBlank() && (
                    message.contains(displayName, ignoreCase = true) ||
                        message.contains("@$displayName", ignoreCase = true) ||
                        message.contains("${displayName}아", ignoreCase = true) ||
                        message.contains("${displayName}야", ignoreCase = true)
                    )
            } == true
        val isQuestion = message.endsWith("?") || message.endsWith("？") || listOf("왜", "뭐", "어디", "언제", "누가", "몇 시", "얼마", "가능", "맞아", "맞나요", "알려", "있어")
            .any { lowered.contains(it) }
        val isRequest = listOf("해줘", "봐줘", "부탁", "확인", "정리", "보내", "알려줘", "가능해", "될까").any { lowered.contains(it) }
        val isGreeting = listOf("안녕", "ㅎㅇ", "좋은 아침", "굿모닝").any { lowered.contains(it) }
        val isThanks = listOf("고마워", "감사", "thanks").any { lowered.contains(it) }
        val isLowSignal = lowered in lowSignalMessages || Regex("^[ㅋㅎㅠㅜ!?~. ]+$").matches(lowered)
        return MessageSignal(
            mentionsDisplayName = mentionsDisplayName,
            isQuestion = isQuestion,
            isRequest = isRequest,
            isGreeting = isGreeting,
            isThanks = isThanks,
            isLowSignal = isLowSignal,
            sender = sender
        )
    }

    private fun shouldAbstain(signal: MessageSignal, candidate: ScoredCandidate?): Boolean {
        if (signal.isLowSignal && candidate == null) return true
        if (signal.isGreeting || signal.isThanks) return false
        if (signal.mentionsDisplayName && (signal.isQuestion || signal.isRequest)) return false
        if ((signal.isQuestion || signal.isRequest) && candidate != null && candidate.score >= 1.6) return false
        return !(signal.isQuestion || signal.isRequest || signal.mentionsDisplayName) || candidate == null || candidate.score < 1.6
    }

    private fun groundedReply(signal: MessageSignal, candidate: ScoredCandidate, tone: ReplyTone): String? {
        val snippet = candidate.text
            .substringBefore('\n')
            .trim()
            .removePrefix("-")
            .trim()
            .take(46)
            .trimEnd()
            .ifBlank { return null }

        return when {
            signal.isQuestion -> "기록상 ${snippet}${tone.contextEnding}"
            signal.isRequest -> "${snippet}${tone.confirmEnding}"
            else -> "${snippet}${tone.shortEnding}"
        }
    }

    private fun MessageSignal.fixedReply(tone: ReplyTone): String? {
        return when {
            isThanks -> tone.thanksReply
            isGreeting -> tone.greetingReply
            isLowSignal -> tone.shortAck
            else -> null
        }
    }

    private fun selectTone(persona: String): ReplyTone {
        val casual = persona.contains("반말") || persona.contains("캐주얼")
        return if (casual) {
            ReplyTone(
                greetingReply = "안녕. 필요한 내용만 짧게 도와줄게.",
                thanksReply = "응, 괜찮아.",
                shortAck = "응, 확인했어.",
                uncertainReply = "지금은 확실하지 않아. 확인 후 답할게.",
                contextEnding = "야.",
                confirmEnding = "로 보여.",
                shortEnding = " 같아."
            )
        } else {
            ReplyTone(
                greetingReply = "안녕하세요. 필요한 내용만 짧게 도와드릴게요.",
                thanksReply = "네, 괜찮아요.",
                shortAck = "네, 확인했어요.",
                uncertainReply = "지금은 확실하지 않아요. 확인 후 답할게요.",
                contextEnding = "예요.",
                confirmEnding = "로 보여요.",
                shortEnding = " 같아요."
            )
        }
    }

    private fun parseMemoryLine(line: String): String {
        val trimmed = line.trim()
        if (trimmed.startsWith("[CSV 가져오기:")) return ""
        if (trimmed.startsWith("자동 메모리 요약")) return ""
        return if (trimmed.startsWith("-")) trimmed.removePrefix("-").trim() else trimmed
    }

    private fun tokenize(text: String): Set<String> {
        return tokenRegex.findAll(text)
            .map { normalizeToken(it.value) }
            .filter { it.isNotBlank() }
            .filterNot { it in stopwords }
            .toSet()
    }

    private fun normalizeToken(token: String): String {
        var normalized = token.lowercase(Locale.ROOT)
        trimSuffixes.forEach { suffix ->
            if (normalized.length > suffix.length + 1 && normalized.endsWith(suffix)) {
                normalized = normalized.removeSuffix(suffix)
                return@forEach
            }
        }
        return normalized
    }

    private fun overlapScore(messageTokens: Set<String>, candidateTokens: Set<String>): Double {
        if (messageTokens.isEmpty() || candidateTokens.isEmpty()) return 0.0
        val overlap = messageTokens.intersect(candidateTokens).size.toDouble()
        if (overlap == 0.0) return 0.0
        return overlap + (overlap / candidateTokens.size.coerceAtLeast(1))
    }

    private fun containsDigits(text: String): Boolean = text.any(Char::isDigit)

    private data class ContextCandidate(
        val text: String,
        val source: String,
        val sender: String = "",
        val order: Int
    )

    private data class ScoredCandidate(
        val text: String,
        val source: String,
        val order: Int,
        val score: Double
    )

    private data class MessageSignal(
        val mentionsDisplayName: Boolean,
        val isQuestion: Boolean,
        val isRequest: Boolean,
        val isGreeting: Boolean,
        val isThanks: Boolean,
        val isLowSignal: Boolean,
        val sender: String
    )

    private data class ReplyTone(
        val greetingReply: String,
        val thanksReply: String,
        val shortAck: String,
        val uncertainReply: String,
        val contextEnding: String,
        val confirmEnding: String,
        val shortEnding: String
    )
}
