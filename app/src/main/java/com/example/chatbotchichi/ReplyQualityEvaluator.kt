package com.example.kakaotalkautobot

object ReplyQualityEvaluator {
    data class Candidate(
        val source: String,
        val raw: String,
        val reply: String,
        val score: Int,
        val reasons: List<String>
    )

    fun evaluate(
        source: String,
        raw: String,
        reply: String,
        config: AutoReplyConfig,
        message: String,
        history: List<RoomHistoryMessage>
    ): Candidate {
        val normalized = reply.trim()
        val reasons = mutableListOf<String>()
        var score = 100

        if (normalized.isBlank()) {
            return Candidate(source, raw, normalized, 0, listOf("blank"))
        }
        if (normalized.length > 90) {
            score -= 18
            reasons.add("too_long")
        }
        if (normalized.lines().size > 2) {
            score -= 10
            reasons.add("too_many_lines")
        }
        if (containsAiMetaText(normalized)) {
            score -= 28
            reasons.add("ai_meta_text")
        }
        if (echoesPrompt(normalized, message)) {
            score -= 22
            reasons.add("prompt_echo")
        }
        if (hasUnsupportedEnglish(normalized)) {
            score -= 12
            reasons.add("english_noise")
        }
        if (normalized.count { it == '!' } > 2 || normalized.count { it == '?' } > 2) {
            score -= 8
            reasons.add("over_punctuated")
        }
        if (looksLikeGuess(normalized) && lacksGrounding(config, history)) {
            score -= 18
            reasons.add("unguarded_guess")
        }
        if (matchesManualStyleHint(normalized, config.roomStyle)) {
            score += 8
            reasons.add("manual_room_style_match")
        }
        if (hasRelevantKnownFact(normalized, config.roomMemory, history)) {
            score += 10
            reasons.add("grounded_fact")
        }

        return Candidate(
            source = source,
            raw = raw,
            reply = normalized,
            score = score.coerceIn(0, 120),
            reasons = reasons
        )
    }

    fun selectBest(candidates: List<Candidate>): Candidate? {
        return candidates
            .filter { it.reply.isNotBlank() && it.score >= 45 }
            .maxWithOrNull(compareBy<Candidate> { it.score }.thenBy { -it.reply.length })
    }

    internal fun containsAiMetaText(reply: String): Boolean {
        val lowered = reply.lowercase()
        return listOf("ai", "인공지능", "모델", "프롬프트", "답변:", "답장:", "assistant").any {
            lowered.contains(it.lowercase())
        }
    }

    private fun echoesPrompt(reply: String, message: String): Boolean {
        val replyTokens = tokens(reply)
        val messageTokens = tokens(message)
        if (replyTokens.isEmpty() || messageTokens.isEmpty()) return false
        val overlap = replyTokens.count { it in messageTokens }
        return overlap >= 4 && overlap * 2 >= replyTokens.size
    }

    private fun hasUnsupportedEnglish(reply: String): Boolean {
        val englishWords = Regex("[A-Za-z]{4,}").findAll(reply).map { it.value.lowercase() }.toList()
        if (englishWords.isEmpty()) return false
        val allowed = setOf("ok", "todo", "zoom", "meet", "github", "figma")
        return englishWords.any { it !in allowed }
    }

    private fun looksLikeGuess(reply: String): Boolean {
        return listOf("아마", "대충", "같아", "것 같", "듯", "추측").any { reply.contains(it) }
    }

    private fun lacksGrounding(config: AutoReplyConfig, history: List<RoomHistoryMessage>): Boolean {
        return config.roomMemory.isBlank() && history.none { it.message.length >= 10 }
    }

    private fun matchesManualStyleHint(reply: String, roomStyle: String): Boolean {
        if (roomStyle.isBlank()) return false
        val casual = roomStyle.contains("반말") || roomStyle.contains("가볍")
        val formal = roomStyle.contains("존댓말") || roomStyle.contains("팀") || roomStyle.contains("학교")
        return (casual && !reply.endsWith("요") && !reply.contains("습니다")) ||
            (formal && (reply.endsWith("요") || reply.contains("습니다") || reply.contains("입니다")))
    }

    private fun hasRelevantKnownFact(
        reply: String,
        roomMemory: String,
        history: List<RoomHistoryMessage>
    ): Boolean {
        val facts = tokens(roomMemory) + history.flatMap { tokens(it.message) }
        return tokens(reply).any { token -> token.length >= 2 && token in facts }
    }

    private fun tokens(text: String): Set<String> {
        return Regex("[가-힣A-Za-z0-9]+").findAll(text)
            .map { it.value.lowercase() }
            .filter { it.length >= 2 }
            .toSet()
    }
}
