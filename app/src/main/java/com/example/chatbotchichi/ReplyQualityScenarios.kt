package com.example.kakaotalkautobot

object ReplyQualityScenarios {
    data class Scenario(
        val id: String,
        val room: String,
        val sender: String,
        val message: String,
        val config: AutoReplyConfig,
        val history: List<RoomHistoryMessage>,
        val expectedTraits: Set<String>,
        val minimumScore: Int = 40
    )

    data class Result(
        val scenarioId: String,
        val reply: String,
        val score: Int,
        val passed: Boolean,
        val expectedTraits: Set<String>
    )

    fun builtIns(): List<Scenario> {
        return listOf(
            Scenario(
                id = "friend_light",
                room = "친구방",
                sender = "민수",
                message = "오늘 뭐함",
                config = AutoReplyJson.defaultConfig("친구방").copy(
                    roomStyle = "친한 친구방. 가볍게 반말. 예시: 아무것도 없긴해",
                    trigger = TriggerConfig("ai_judge", "")
                ),
                history = listOf(
                    RoomHistoryMessage("민수", "ㅋㅋ 오늘 뭐함", true, 1L),
                    RoomHistoryMessage("나", "아무것도 없긴해", false, 2L)
                ),
                expectedTraits = setOf("casual", "short")
            ),
            Scenario(
                id = "team_formal",
                room = "팀단톡",
                sender = "팀장",
                message = "오늘 공유할 특이사항 있나요?",
                config = AutoReplyJson.defaultConfig("팀단톡").copy(
                    roomStyle = "팀 단톡. 존댓말. 별일 없으면 '별일없습니다!'처럼 단정하게 답장",
                    trigger = TriggerConfig("ai_judge", "")
                ),
                history = listOf(
                    RoomHistoryMessage("팀장", "오늘 공유할 특이사항 있나요?", true, 1L),
                    RoomHistoryMessage("나", "별일없습니다!", false, 2L)
                ),
                expectedTraits = setOf("formal", "short")
            ),
            Scenario(
                id = "low_signal_skip",
                room = "친구방",
                sender = "민수",
                message = "ㅋㅋ",
                config = AutoReplyJson.defaultConfig("친구방").copy(
                    trigger = TriggerConfig("ai_judge", "")
                ),
                history = emptyList(),
                expectedTraits = setOf("skip"),
                minimumScore = 50
            ),
            Scenario(
                id = "ambiguous_clarify",
                room = "프로젝트방",
                sender = "민수",
                message = "그거 됐어?",
                config = AutoReplyJson.defaultConfig("프로젝트방").copy(
                    roomStyle = "프로젝트방. 모호한 요청은 아는 척하지 말고 짧게 확인 질문",
                    trigger = TriggerConfig("ai_judge", "")
                ),
                history = listOf(
                    RoomHistoryMessage("지우", "문서 초안이랑 발표 자료 둘 다 남았어", true, 1L),
                    RoomHistoryMessage("나", "일단 문서부터 볼게", false, 2L)
                ),
                expectedTraits = setOf("clarify", "short"),
                minimumScore = 45
            ),
            Scenario(
                id = "unknown_fact_guard",
                room = "학교방",
                sender = "지우",
                message = "내일 발표 몇 시야?",
                config = AutoReplyJson.defaultConfig("학교방").copy(
                    roomStyle = "학교 단톡. 모르는 일정은 추측하지 않음",
                    trigger = TriggerConfig("ai_judge", "")
                ),
                history = emptyList(),
                expectedTraits = setOf("unknown_guard", "formal")
            ),
            Scenario(
                id = "room_memory_fact",
                room = "프로젝트방",
                sender = "PM",
                message = "최종 제출 언제까지야?",
                config = AutoReplyJson.defaultConfig("프로젝트방").copy(
                    roomMemory = "최종 제출 마감은 6월 12일 18시.",
                    roomStyle = "프로젝트방. 짧고 정확하게 존댓말",
                    trigger = TriggerConfig("ai_judge", "")
                ),
                history = emptyList(),
                expectedTraits = setOf("grounded_fact", "formal")
            )
        )
    }

    fun evaluateReply(reply: String, scenario: Scenario): Result {
        val score = traitScore(reply, scenario)
        return Result(
            scenarioId = scenario.id,
            reply = reply.trim(),
            score = score,
            passed = score >= scenario.minimumScore,
            expectedTraits = scenario.expectedTraits
        )
    }

    fun traitScore(reply: String, scenario: Scenario): Int {
        var score = 0
        val normalized = reply.trim()
        if ("skip" in scenario.expectedTraits && normalized.isBlank()) score += 50
        if ("short" in scenario.expectedTraits && normalized.length in 1..80) score += 20
        if ("casual" in scenario.expectedTraits && !normalized.contains("습니다") && !normalized.endsWith("요")) score += 20
        if ("formal" in scenario.expectedTraits && (normalized.contains("습니다") || normalized.endsWith("요") || normalized.contains("입니다"))) score += 20
        if ("clarify" in scenario.expectedTraits && listOf("뭐", "어떤", "문서", "발표", "그거").any { normalized.contains(it) }) score += 25
        if ("unknown_guard" in scenario.expectedTraits && listOf("몰라", "확인", "못 찾", "모르").any { normalized.contains(it) }) score += 25
        if ("grounded_fact" in scenario.expectedTraits && listOf("6월 12일", "18시", "12일").any { normalized.contains(it) }) score += 25
        if (ReplyQualityEvaluator.containsAiMetaText(normalized)) score -= 30
        if (normalized.length > 120) score -= 20
        return score.coerceAtLeast(0)
    }
}
