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

    data class EngineBaselineResult(
        val scenarioId: String,
        val source: String,
        val reply: String,
        val score: Int,
        val passed: Boolean,
        val coveredWithoutLlm: Boolean,
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
                id = "friend_no_echo",
                room = "친구방",
                sender = "민수",
                message = "오늘 뭐함",
                config = AutoReplyJson.defaultConfig("친구방").copy(
                    roomStyle = "친한 친구방. 가볍게 반말. 상대 말을 따라 쓰지 말고 예시처럼 답장. 예시: 아무것도 없긴해",
                    trigger = TriggerConfig("ai_judge", "")
                ),
                history = listOf(
                    RoomHistoryMessage("민수", "오늘 뭐함", true, 1L),
                    RoomHistoryMessage("나", "아무것도 없긴해", false, 2L)
                ),
                expectedTraits = setOf("casual", "short", "no_echo", "manual_example"),
                minimumScore = 60
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
                id = "team_known_fact_no_generic_ack",
                room = "팀단톡",
                sender = "팀장",
                message = "오늘 회의 몇 시인가요?",
                config = AutoReplyJson.defaultConfig("팀단톡").copy(
                    roomMemory = "오늘 회의는 15시.",
                    roomStyle = "팀 단톡. 존댓말. 메모에 답이 있으면 확인했습니다로 넘기지 말고 바로 답장",
                    trigger = TriggerConfig("ai_judge", "")
                ),
                history = listOf(
                    RoomHistoryMessage("팀장", "오늘 회의 몇 시인가요?", true, 1L),
                    RoomHistoryMessage("나", "15시입니다.", false, 2L)
                ),
                expectedTraits = setOf("formal", "short", "grounded_fact", "no_generic_ack"),
                minimumScore = 60
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
                id = "school_concise_no_overexplained",
                room = "학교방",
                sender = "선생님",
                message = "오늘 전달할 내용 있나요?",
                config = AutoReplyJson.defaultConfig("학교방").copy(
                    roomStyle = "학교 단톡. 존댓말. 별일 없으면 짧고 단정하게 답장하고 부연 설명을 붙이지 않음",
                    trigger = TriggerConfig("ai_judge", "")
                ),
                history = listOf(
                    RoomHistoryMessage("선생님", "오늘 전달할 내용 있나요?", true, 1L),
                    RoomHistoryMessage("나", "별일 없습니다.", false, 2L)
                ),
                expectedTraits = setOf("formal", "short", "concise_no_overexplained"),
                minimumScore = 60
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
                id = "low_signal_with_context_ack",
                room = "친구방",
                sender = "민수",
                message = "ㅇㅋ",
                config = AutoReplyJson.defaultConfig("친구방").copy(
                    roomStyle = "친한 친구방. 가볍게 반말",
                    trigger = TriggerConfig("ai_judge", "")
                ),
                history = listOf(
                    RoomHistoryMessage("민수", "그럼 오늘 7시에 학교 앞에서 보자", true, 1L),
                    RoomHistoryMessage("나", "좋아 그때 갈게", false, 2L),
                    RoomHistoryMessage("민수", "늦으면 바로 말해줘", true, 3L)
                ),
                expectedTraits = setOf("ack", "casual", "short", "low_signal_brief_ack"),
                minimumScore = 45
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
                expectedTraits = setOf("clarify", "short", "no_overclaim"),
                minimumScore = 55
            ),
            Scenario(
                id = "school_ambiguous_formal",
                room = "학교방",
                sender = "선생님",
                message = "그거 준비됐나요?",
                config = AutoReplyJson.defaultConfig("학교방").copy(
                    roomStyle = "학교 단톡. 존댓말. 모호한 요청은 단정하지 말고 확인 질문",
                    trigger = TriggerConfig("ai_judge", "")
                ),
                history = listOf(
                    RoomHistoryMessage("선생님", "문서 초안과 발표 자료를 둘 다 확인해 주세요.", true, 1L),
                    RoomHistoryMessage("나", "먼저 문서 초안부터 정리하겠습니다.", false, 2L)
                ),
                expectedTraits = setOf("clarify", "formal", "short", "no_overclaim"),
                minimumScore = 55
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
                id = "friend_unknown_fact_guard",
                room = "친구방",
                sender = "민수",
                message = "내일 발표 몇 시야?",
                config = AutoReplyJson.defaultConfig("친구방").copy(
                    roomStyle = "친한 친구방. 모르는 일정은 추측하지 말고 짧게 반말",
                    trigger = TriggerConfig("ai_judge", "")
                ),
                history = emptyList(),
                expectedTraits = setOf("unknown_guard", "casual", "short"),
                minimumScore = 55
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
            ),
            Scenario(
                id = "manual_example_override",
                room = "친구방",
                sender = "민수",
                message = "오늘 별일 있어?",
                config = AutoReplyJson.defaultConfig("친구방").copy(
                    roomStyle = "친한 친구방. 수동 방 말투가 최우선. 예시: 아무것도 없긴해",
                    trigger = TriggerConfig("ai_judge", "")
                ),
                history = listOf(
                    RoomHistoryMessage("선생님", "전달 사항 있나요?", true, 1L),
                    RoomHistoryMessage("나", "별일 없습니다.", false, 2L),
                    RoomHistoryMessage("팀장", "공유할 이슈 있나요?", true, 3L),
                    RoomHistoryMessage("나", "별일없습니다!", false, 4L)
                ),
                expectedTraits = setOf("casual", "short", "manual_example"),
                minimumScore = 55
            ),
            Scenario(
                id = "friend_no_business_ack",
                room = "친구방",
                sender = "민수",
                message = "오늘 별일 있어?",
                config = AutoReplyJson.defaultConfig("친구방").copy(
                    roomStyle = "친한 친구방. 가볍게 반말. 업무용 확인 답장 금지. 예시: 아무것도 없긴해",
                    trigger = TriggerConfig("ai_judge", "")
                ),
                history = listOf(
                    RoomHistoryMessage("민수", "오늘 별일 있어?", true, 1L),
                    RoomHistoryMessage("나", "아무것도 없긴해", false, 2L)
                ),
                expectedTraits = setOf("casual", "short", "manual_example", "no_business_ack"),
                minimumScore = 65
            ),
            Scenario(
                id = "friend_no_service_apology",
                room = "친구방",
                sender = "민수",
                message = "지금 가능?",
                config = AutoReplyJson.defaultConfig("친구방").copy(
                    roomStyle = "친한 친구방. 서비스 상담원처럼 사과하거나 안내하지 말고 짧게 반말. 예시: 지금은 좀 애매해",
                    trigger = TriggerConfig("ai_judge", "")
                ),
                history = listOf(
                    RoomHistoryMessage("민수", "지금 가능?", true, 1L),
                    RoomHistoryMessage("나", "지금은 좀 애매해", false, 2L)
                ),
                expectedTraits = setOf("casual", "short", "manual_example", "no_service_apology"),
                minimumScore = 65
            ),
            Scenario(
                id = "friend_no_helper_followup",
                room = "친구방",
                sender = "민수",
                message = "지금 가능?",
                config = AutoReplyJson.defaultConfig("친구방").copy(
                    roomStyle = "친한 친구방. 자동응답기처럼 도움말이나 응원 꼬리를 붙이지 말고 짧게 반말. 예시: 지금은 좀 애매해",
                    trigger = TriggerConfig("ai_judge", "")
                ),
                history = listOf(
                    RoomHistoryMessage("민수", "지금 가능?", true, 1L),
                    RoomHistoryMessage("나", "지금은 좀 애매해", false, 2L)
                ),
                expectedTraits = setOf("casual", "short", "manual_example", "no_helper_followup"),
                minimumScore = 65
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
            ScenarioExample("friend_no_echo", "아무것도 없긴해"),
            ScenarioExample("team_formal", "별일없습니다!"),
            ScenarioExample("team_known_fact_no_generic_ack", "15시입니다."),
            ScenarioExample("school_formal_notice", "별일 없습니다."),
            ScenarioExample("school_concise_no_overexplained", "별일 없습니다."),
            ScenarioExample("low_signal_skip", ""),
            ScenarioExample("low_signal_with_context_ack", "응 알겠어"),
            ScenarioExample("ambiguous_clarify", "문서 말하는 거야, 발표 자료 말하는 거야?"),
            ScenarioExample("school_ambiguous_formal", "문서 초안 말씀하시는 건가요, 발표 자료 말씀하시는 건가요?"),
            ScenarioExample("unknown_fact_guard", "아직 확인된 내용은 못 찾았습니다."),
            ScenarioExample("friend_unknown_fact_guard", "아직 확인된 건 못 찾았어."),
            ScenarioExample("room_memory_fact", "6월 12일 18시까지입니다."),
            ScenarioExample("manual_example_override", "아무것도 없긴해"),
            ScenarioExample("friend_no_business_ack", "아무것도 없긴해"),
            ScenarioExample("friend_no_service_apology", "지금은 좀 애매해"),
            ScenarioExample("friend_no_helper_followup", "지금은 좀 애매해")
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
        val engineResults = evaluateEngineBaselines(scenarios)
        val coveredEngineResults = engineResults.filter { it.coveredWithoutLlm }
        val passedCount = results.count { it.passed }
        val enginePassedCount = coveredEngineResults.count { it.passed }
        return buildString {
            appendLine("# Reply Quality Baseline")
            appendLine()
            appendLine("- scenarios: ${results.size}")
            appendLine("- passed: $passedCount")
            appendLine("- failed: ${results.size - passedCount}")
            appendLine("- engine_covered_without_llm: ${coveredEngineResults.size}")
            appendLine("- engine_passed_without_llm: $enginePassedCount")
            appendLine()
            appendLine("## Expected Reply Examples")
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
            appendLine()
            appendLine("## Engine Baseline Without LLM")
            appendLine()
            appendLine("| scenario | source | covered | traits | score | min | passed | reply |")
            appendLine("| --- | --- | --- | --- | ---: | ---: | --- | --- |")
            engineResults.forEach { result ->
                val scenario = requireNotNull(scenarioById[result.scenarioId])
                appendLine(
                    "| ${result.scenarioId} | ${result.source} | ${if (result.coveredWithoutLlm) "yes" else "no"} | " +
                        "${result.expectedTraits.sorted().joinToString(", ")} | ${result.score} | ${scenario.minimumScore} | " +
                        "${if (result.coveredWithoutLlm && result.passed) "yes" else "n/a"} | ${escapeMarkdownTable(result.reply)} |"
                )
            }
        }
    }

    fun evaluateEngineBaselines(scenarios: List<Scenario> = builtIns()): List<EngineBaselineResult> {
        return scenarios.map { scenario ->
            if (
                AiProviderClient.shouldSkipLowSignalBeforeModelLoad(
                    config = scenario.config,
                    message = scenario.message,
                    history = scenario.history
                )
            ) {
                val result = evaluateReply("", scenario)
                return@map EngineBaselineResult(
                    scenarioId = scenario.id,
                    source = "pre_model_skip",
                    reply = "",
                    score = result.score,
                    passed = result.passed,
                    coveredWithoutLlm = true,
                    expectedTraits = scenario.expectedTraits
                )
            }

            val bestCandidate = ReplyQualityEvaluator.selectBest(
                AiProviderClient.buildDeterministicCandidates(
                    config = scenario.config,
                    message = scenario.message,
                    history = scenario.history
                )
            )
            if (bestCandidate == null) {
                EngineBaselineResult(
                    scenarioId = scenario.id,
                    source = "requires_llm",
                    reply = "",
                    score = 0,
                    passed = false,
                    coveredWithoutLlm = false,
                    expectedTraits = scenario.expectedTraits
                )
            } else {
                val result = evaluateReply(bestCandidate.reply, scenario)
                EngineBaselineResult(
                    scenarioId = scenario.id,
                    source = bestCandidate.source,
                    reply = bestCandidate.reply,
                    score = result.score,
                    passed = result.passed,
                    coveredWithoutLlm = true,
                    expectedTraits = scenario.expectedTraits
                )
            }
        }
    }

    fun traitScore(reply: String, scenario: Scenario): Int {
        var score = 0
        val normalized = reply.trim()
        if ("skip" in scenario.expectedTraits && normalized.isBlank()) score += 50
        if ("ack" in scenario.expectedTraits && listOf("응", "알겠", "오케이", "ㅇㅋ", "갈게", "할게").any { normalized.contains(it) }) score += 25
        if ("short" in scenario.expectedTraits && normalized.length in 1..80) score += 20
        if ("casual" in scenario.expectedTraits && !normalized.contains("습니다") && !normalized.endsWith("요")) score += 20
        if ("formal" in scenario.expectedTraits && (normalized.contains("습니다") || normalized.contains("요") || normalized.contains("입니다"))) score += 20
        if ("clarify" in scenario.expectedTraits && listOf("뭐", "어떤", "문서", "발표", "그거").any { normalized.contains(it) }) score += 25
        if ("unknown_guard" in scenario.expectedTraits && listOf("몰라", "확인", "못 찾", "모르").any { normalized.contains(it) }) score += 25
        val candidate = ReplyQualityEvaluator.evaluate(
            source = "quality_scenario",
            raw = normalized,
            reply = normalized,
            config = scenario.config,
            message = scenario.message,
            history = scenario.history
        )
        if ("grounded_fact" in scenario.expectedTraits && "grounded_fact" in candidate.reasons) score += 25
        if ("manual_example" in scenario.expectedTraits) {
            if ("manual_example_match" in candidate.reasons) score += 25
            if (candidate.reasons.any { it.startsWith("manual_room_style_mismatch") }) score -= 20
            if ("self_referential_business_tone" in candidate.reasons) score -= 35
        }
        if ("no_echo" in scenario.expectedTraits) {
            if ("short_prompt_echo" in candidate.reasons || "prompt_echo" in candidate.reasons) score -= 40 else score += 20
        }
        if ("no_generic_ack" in scenario.expectedTraits) {
            if ("generic_ack_instead_of_known_fact" in candidate.reasons) score -= 40 else score += 20
        }
        if ("concise_no_overexplained" in scenario.expectedTraits) {
            if ("over_explained" in candidate.reasons || "assistant_boilerplate" in candidate.reasons) score -= 40 else score += 20
        }
        if ("low_signal_brief_ack" in scenario.expectedTraits) {
            if ("low_signal_overreply" in candidate.reasons || "over_explained" in candidate.reasons) score -= 40 else score += 20
        }
        if ("no_overclaim" in scenario.expectedTraits) {
            if ("ambiguous_overclaim" in candidate.reasons) score -= 45 else score += 20
        }
        if ("no_business_ack" in scenario.expectedTraits) {
            if ("generic_business_ack_in_casual_room" in candidate.reasons) score -= 40 else score += 20
        }
        if ("no_service_apology" in scenario.expectedTraits) {
            if ("service_apology_boilerplate" in candidate.reasons || "assistant_boilerplate" in candidate.reasons) {
                score -= 45
            } else {
                score += 20
            }
        }
        if ("no_helper_followup" in scenario.expectedTraits) {
            if ("helper_followup_boilerplate" in candidate.reasons || "assistant_boilerplate" in candidate.reasons) {
                score -= 45
            } else {
                score += 20
            }
        }
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
