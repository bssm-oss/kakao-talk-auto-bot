package com.example.kakaotalkautobot

object ReplyQualityEvaluator {
    data class Candidate(
        val source: String,
        val raw: String,
        val reply: String,
        val score: Int,
        val reasons: List<String>,
        val latencyMs: Long = 0L
    )

    fun evaluate(
        source: String,
        raw: String,
        reply: String,
        config: AutoReplyConfig,
        message: String,
        history: List<RoomHistoryMessage>,
        latencyMs: Long = 0L
    ): Candidate {
        val normalized = reply.trim()
        val reasons = mutableListOf<String>()
        var score = 100

        if (normalized.isBlank()) {
            return Candidate(source, raw, normalized, 0, listOf("blank"), latencyMs)
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
        if (looksLikeAssistantBoilerplate(normalized)) {
            score -= 18
            reasons.add("assistant_boilerplate")
        }
        if (looksSelfReferentialBusinessTone(normalized)) {
            score -= 18
            reasons.add("self_referential_business_tone")
        }
        if (looksGenericBusinessAckInCasualRoom(normalized, config.roomStyle)) {
            score -= 30
            reasons.add("generic_business_ack_in_casual_room")
        }
        if (looksOverExplained(normalized)) {
            score -= 20
            reasons.add("over_explained")
        }
        if (overRepliesToLowSignal(normalized, message)) {
            score -= 26
            reasons.add("low_signal_overreply")
        }
        if (echoesPrompt(normalized, message)) {
            score -= 22
            reasons.add("prompt_echo")
        }
        if (echoesShortPrompt(normalized, message)) {
            score -= 34
            reasons.add("short_prompt_echo")
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
        if (evadesKnownFactQuestion(normalized, config, message, history)) {
            score -= 36
            reasons.add("generic_ack_instead_of_known_fact")
        }
        val styleMismatch = manualStyleMismatchReason(normalized, config.roomStyle)
        if (styleMismatch != null) {
            score -= 24
            reasons.add(styleMismatch)
        }
        if (matchesManualStyleHint(normalized, config.roomStyle)) {
            score += 8
            reasons.add("manual_room_style_match")
        }
        if (matchesManualExample(normalized, config.roomStyle)) {
            score += 14
            reasons.add("manual_example_match")
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
            reasons = reasons,
            latencyMs = latencyMs
        )
    }

    fun selectBest(
        candidates: List<Candidate>,
        sourcePriors: Map<String, Int> = emptyMap()
    ): Candidate? {
        return dedupeCandidates(candidates)
            .filter { it.reply.isNotBlank() && it.score >= 45 }
            .maxWithOrNull(
                compareBy<Candidate> { it.score + sourcePriors.getOrDefault(it.source, 0) }
                    .thenBy { it.score }
                    .thenBy { -it.reply.length }
            )
    }

    fun dedupeCandidates(candidates: List<Candidate>): List<Candidate> {
        if (candidates.size <= 1) return candidates
        val bestByReply = candidates
            .filter { it.reply.isNotBlank() }
            .groupBy { normalizeForExampleMatch(it.reply) }
            .filterKeys { it.isNotBlank() }
            .mapValues { (_, duplicates) ->
                duplicates.maxWithOrNull(compareBy<Candidate> { it.score }.thenBy { -it.reply.length })
            }
        return candidates.map { candidate ->
            val key = normalizeForExampleMatch(candidate.reply)
            val winner = bestByReply[key]
            if (key.isBlank() || winner === candidate || winner == null) {
                candidate
            } else {
                candidate.copy(
                    score = 0,
                    reasons = (candidate.reasons + "duplicate_reply").distinct()
                )
            }
        }
    }

    internal fun containsAiMetaText(reply: String): Boolean {
        val lowered = reply.lowercase()
        return listOf("ai", "인공지능", "모델", "프롬프트", "답변:", "답장:", "assistant").any {
            lowered.contains(it.lowercase())
        }
    }

    internal fun looksLikeAssistantBoilerplate(reply: String): Boolean {
        return listOf(
            "도움이 필요",
            "도와드릴",
            "추가로 궁금",
            "무엇을 도와",
            "알려드릴게요",
            "확인해보겠습니다",
            "좋은 질문"
        ).any { reply.contains(it) }
    }

    internal fun looksSelfReferentialBusinessTone(reply: String): Boolean {
        val selfReference = listOf("제가", "저는", "저희", "제가요").any { reply.contains(it) }
        if (!selfReference) return false
        return listOf(
            "확인해보겠습니다",
            "확인해 보겠습니다",
            "진행하겠습니다",
            "처리하겠습니다",
            "공유드리겠습니다",
            "답변드리겠습니다",
            "도와드리겠습니다",
            "말씀드리겠습니다"
        ).any { reply.contains(it) }
    }

    internal fun looksGenericBusinessAckInCasualRoom(reply: String, roomStyle: String): Boolean {
        if (!prefersCasualStyle(roomStyle)) return false
        val normalized = normalizeForExampleMatch(reply)
        val genericAck = listOf(
            "확인했습니다",
            "확인하겠습니다",
            "알겠습니다",
            "알겠어요",
            "네확인했습니다",
            "넵확인했습니다",
            "처리하겠습니다",
            "진행하겠습니다",
            "전달하겠습니다",
            "공유드리겠습니다"
        )
        return genericAck.any { phrase ->
            val normalizedPhrase = normalizeForExampleMatch(phrase)
            normalized == normalizedPhrase || normalized.contains(normalizedPhrase)
        }
    }

    internal fun looksOverExplained(reply: String): Boolean {
        val explanatoryPhrases = listOf(
            "추가로",
            "필요하시면",
            "필요하면",
            "말씀해 주세요",
            "알려주세요",
            "공유드리겠습니다",
            "확인 후",
            "진행하겠습니다",
            "참고해주세요",
            "문의 주세요"
        )
        val phraseHits = explanatoryPhrases.count { reply.contains(it) }
        if (phraseHits == 0) return false
        val sentenceLikeBreaks = reply.count { it == '.' || it == '!' || it == '?' || it == '。' || it == '？' || it == '！' }
        return sentenceLikeBreaks >= 2 || reply.length >= 50 || (reply.length >= 30 && reply.contains("추가로"))
    }

    internal fun overRepliesToLowSignal(reply: String, message: String): Boolean {
        if (!AiProviderClient.isLowSignalMessage(message)) return false
        if (reply.length <= 14 && reply.lines().size == 1) return false
        val replyLooksLikeBriefAck = listOf("응", "ㅇㅋ", "오케이", "넵", "네", "알겠", "좋아").any { reply.contains(it) }
        val sentenceLikeBreaks = reply.count { it == '.' || it == '!' || it == '?' || it == '。' || it == '？' || it == '！' }
        return reply.length > 22 || sentenceLikeBreaks >= 2 || (replyLooksLikeBriefAck && reply.length > 14)
    }

    private fun echoesPrompt(reply: String, message: String): Boolean {
        val replyTokens = tokens(reply)
        val messageTokens = tokens(message)
        if (replyTokens.isEmpty() || messageTokens.isEmpty()) return false
        val overlap = replyTokens.count { it in messageTokens }
        return overlap >= 4 && overlap * 2 >= replyTokens.size
    }

    private fun echoesShortPrompt(reply: String, message: String): Boolean {
        val normalizedReply = normalizeForExampleMatch(reply)
        val normalizedMessage = normalizeForExampleMatch(message)
        if (normalizedReply.isBlank() || normalizedMessage.isBlank()) return false
        if (normalizedMessage.length > 18 || normalizedReply.length > 28) return false
        return normalizedReply == normalizedMessage ||
            normalizedReply.contains(normalizedMessage) ||
            normalizedMessage.contains(normalizedReply)
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

    private fun evadesKnownFactQuestion(
        reply: String,
        config: AutoReplyConfig,
        message: String,
        history: List<RoomHistoryMessage>
    ): Boolean {
        return asksForConcreteFact(message) &&
            hasAvailableFact(config, history) &&
            !hasRelevantKnownFact(reply, config.roomMemory, history) &&
            looksLikeGenericAck(reply)
    }

    private fun asksForConcreteFact(message: String): Boolean {
        return listOf(
            "몇 시",
            "몇시",
            "언제",
            "어디",
            "누구",
            "마감",
            "제출",
            "일정",
            "날짜",
            "시간"
        ).any { message.contains(it) }
    }

    private fun hasAvailableFact(config: AutoReplyConfig, history: List<RoomHistoryMessage>): Boolean {
        return tokens(config.roomMemory).any { it.any(Char::isDigit) || it.length >= 3 } ||
            history.any { message ->
                tokens(message.message).any { it.any(Char::isDigit) || it.length >= 3 }
            }
    }

    private fun looksLikeGenericAck(reply: String): Boolean {
        val normalized = normalizeForExampleMatch(reply)
        val genericAckPhrases = listOf(
            "네",
            "확인했습니다",
            "확인했어요",
            "알겠습니다",
            "알겠어요",
            "확인해볼게요",
            "확인해보겠습니다",
            "넵",
            "응알겠어",
            "오케이"
        )
        return genericAckPhrases.any { phrase -> normalized.contains(normalizeForExampleMatch(phrase)) }
    }

    private fun lacksGrounding(config: AutoReplyConfig, history: List<RoomHistoryMessage>): Boolean {
        return config.roomMemory.isBlank() && history.none { it.message.length >= 10 }
    }

    private fun matchesManualStyleHint(reply: String, roomStyle: String): Boolean {
        val casual = prefersCasualStyle(roomStyle)
        val formal = !casual && prefersFormalStyle(roomStyle)
        return (casual && !reply.endsWith("요") && !reply.contains("습니다")) ||
            (formal && (reply.endsWith("요") || reply.contains("습니다") || reply.contains("입니다")))
    }

    private fun matchesManualExample(reply: String, roomStyle: String): Boolean {
        val examples = extractManualExamples(roomStyle)
        if (examples.isEmpty()) return false
        val normalizedReply = normalizeForExampleMatch(reply)
        return examples.any { example ->
            val normalizedExample = normalizeForExampleMatch(example)
            normalizedExample.isNotBlank() &&
                (normalizedReply == normalizedExample ||
                    normalizedReply.contains(normalizedExample) ||
                    normalizedExample.contains(normalizedReply))
        }
    }

    private fun extractManualExamples(roomStyle: String): List<String> {
        val markers = listOf("예시:", "예시：", "예:", "example:")
        return markers.flatMap { marker ->
            roomStyle.split(marker, ignoreCase = true, limit = 2)
                .getOrNull(1)
                ?.lineSequence()
                ?.flatMap { line -> line.split(",", "/", "|").asSequence() }
                ?.map { it.trim().trim('"', '\'', '`') }
                ?.filter { it.length in 2..40 }
                ?.toList()
                .orEmpty()
        }.distinct()
    }

    private fun manualStyleMismatchReason(reply: String, roomStyle: String): String? {
        val casual = prefersCasualStyle(roomStyle)
        val formal = !casual && prefersFormalStyle(roomStyle)
        val replyLooksFormal = reply.endsWith("요") || reply.contains("습니다") || reply.contains("입니다")
        val replyLooksCasual = listOf("야", "해", "어", "됐어", "할게", "같아", "몰라").any { reply.endsWith(it) } ||
            listOf("말하는 거야", "아니야", "없긴해").any { reply.contains(it) }

        return when {
            casual && replyLooksFormal -> "manual_room_style_mismatch_formal"
            formal && replyLooksCasual -> "manual_room_style_mismatch_casual"
            else -> null
        }
    }

    private fun prefersCasualStyle(roomStyle: String): Boolean {
        return roomStyle.contains("반말") ||
            roomStyle.contains("친한") ||
            roomStyle.contains("가볍") ||
            roomStyle.contains("캐주얼")
    }

    private fun prefersFormalStyle(roomStyle: String): Boolean {
        return roomStyle.contains("존댓말") ||
            roomStyle.contains("팀") ||
            roomStyle.contains("학교") ||
            roomStyle.contains("정중")
    }

    private fun hasRelevantKnownFact(
        reply: String,
        roomMemory: String,
        history: List<RoomHistoryMessage>
    ): Boolean {
        val facts = tokens(roomMemory) + history.flatMap { tokens(it.message) }
        val factTokens = facts.filter { it.length >= 2 }
        return tokens(reply).any { token ->
            token.length >= 2 && factTokens.any { fact ->
                token == fact ||
                    token.contains(fact) ||
                    (fact.any(Char::isDigit) && fact.contains(token))
            }
        }
    }

    private fun tokens(text: String): Set<String> {
        return Regex("[가-힣A-Za-z0-9]+").findAll(text)
            .map { it.value.lowercase() }
            .filter { it.length >= 2 }
            .toSet()
    }

    private fun normalizeForExampleMatch(text: String): String {
        return Regex("[가-힣A-Za-z0-9]+").findAll(text)
            .joinToString("") { it.value.lowercase() }
    }
}
