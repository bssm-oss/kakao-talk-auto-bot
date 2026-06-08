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

    data class CoverageSummary(
        val scenarioCount: Int,
        val scenarioIds: Set<String>,
        val traitIds: Set<String>
    ) {
        fun hasRequiredScenarios(requiredIds: Set<String>): Boolean {
            return scenarioIds.containsAll(requiredIds)
        }
    }

    data class ScenarioExample(
        val scenarioId: String,
        val reply: String
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
                id = "school_formal_notice",
                room = "학교방",
                sender = "선생님",
                message = "오늘 전달할 내용 있나요?",
                config = AutoReplyJson.defaultConfig("학교방").copy(
                    roomStyle = "학교 단톡. 존댓말. 별일 없으면 '별일 없습니다.'처럼 단정하게 답장",
                    trigger = TriggerConfig("ai_judge", "")
                ),
                history = listOf(
                    RoomHistoryMessage("선생님", "오늘 전달할 내용 있나요?", true, 1L),
                    RoomHistoryMessage("나", "별일 없습니다.", false, 2L)
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

    fun coverageSummary(scenarios: List<Scenario> = builtIns()): CoverageSummary {
        return CoverageSummary(
            scenarioCount = scenarios.size,
            scenarioIds = scenarios.map { it.id }.toSet(),
            traitIds = scenarios.flatMap { it.expectedTraits }.toSet()
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

    fun baselineReplies(): List<ScenarioExample> {
        return listOf(
            ScenarioExample("friend_light", "아무것도 없긴해"),
            ScenarioExample("team_formal", "별일없습니다!"),
            ScenarioExample("school_formal_notice", "별일 없습니다."),
            ScenarioExample("low_signal_skip", ""),
            ScenarioExample("ambiguous_clarify", "문서 말하는 거야, 발표 자료 말하는 거야?"),
            ScenarioExample("unknown_fact_guard", "아직 확인된 내용은 못 찾았습니다."),
            ScenarioExample("room_memory_fact", "6월 12일 18시까지입니다.")
        )
    }

    fun evaluateBaselineReplies(
        scenarios: List<Scenario> = builtIns(),
        examples: List<ScenarioExample> = baselineReplies()
    ): List<Result> {
        val scenarioById = scenarios.associateBy { it.id }
        return examples.map { example ->
            val scenario = requireNotNull(scenarioById[example.scenarioId]) {
                "Unknown reply quality scenario: ${example.scenarioId}"
            }
            evaluateReply(example.reply, scenario)
        }
    }

    fun baselineReportMarkdown(
        scenarios: List<Scenario> = builtIns(),
        examples: List<ScenarioExample> = baselineReplies()
    ): String {
        val scenarioById = scenarios.associateBy { it.id }
        val results = evaluateBaselineReplies(scenarios, examples)
        val passedCount = results.count { it.passed }
        return buildString {
            appendLine("# Reply Quality Baseline")
            appendLine()
            appendLine("- scenarios: ${results.size}")
            appendLine("- passed: $passedCount")
            appendLine("- failed: ${results.size - passedCount}")
            appendLine()
            appendLine("| scenario | room | traits | score | min | passed | reply |")
            appendLine("| --- | --- | --- | ---: | ---: | --- | --- |")
            results.forEach { result ->
                val scenario = requireNotNull(scenarioById[result.scenarioId])
                appendLine(
                    "| ${result.scenarioId} | ${scenario.room} | ${result.expectedTraits.sorted().joinToString(", ")} | " +
                        "${result.score} | ${scenario.minimumScore} | ${if (result.passed) "yes" else "no"} | ${escapeMarkdownTable(result.reply)} |"
                )
            }
        }
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

    private fun escapeMarkdownTable(value: String): String {
        return value
            .ifBlank { "(skip)" }
            .replace("|", "\\|")
            .replace("\n", " ")
    }
}
