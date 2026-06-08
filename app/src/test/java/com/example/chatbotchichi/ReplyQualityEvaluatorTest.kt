package com.example.kakaotalkautobot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyQualityEvaluatorTest {
    @Test
    fun selectBest_prefersGroundedNaturalReplyOverMetaReply() {
        val scenario = ReplyQualityScenarios.builtIns().first { it.id == "room_memory_fact" }
        val candidates = listOf(
            ReplyQualityEvaluator.evaluate(
                source = "primary",
                raw = "답변: AI 모델로는 정확히 알 수 없습니다.",
                reply = AiProviderClient.cleanResponse("답변: AI 모델로는 정확히 알 수 없습니다."),
                config = scenario.config,
                message = scenario.message,
                history = scenario.history
            ),
            ReplyQualityEvaluator.evaluate(
                source = "compact",
                raw = "6월 12일 18시까지입니다.",
                reply = AiProviderClient.cleanResponse("6월 12일 18시까지입니다."),
                config = scenario.config,
                message = scenario.message,
                history = scenario.history
            )
        )

        val best = ReplyQualityEvaluator.selectBest(candidates)

        assertEquals("compact", best?.source)
        assertEquals("6월 12일 18시까지입니다.", best?.reply)
    }

    @Test
    fun selectBest_penalizesFormalToneInCasualFriendRoom() {
        val config = AutoReplyJson.defaultConfig("친구방").copy(
            roomStyle = "친한 친구방. 가볍게 반말로 답장. 예시: 아무것도 없긴해"
        )
        val candidates = listOf(
            ReplyQualityEvaluator.evaluate(
                source = "primary",
                raw = "별일 없습니다.",
                reply = "별일 없습니다.",
                config = config,
                message = "뭐 있어?",
                history = emptyList()
            ),
            ReplyQualityEvaluator.evaluate(
                source = "compact",
                raw = "아무것도 없긴해",
                reply = "아무것도 없긴해",
                config = config,
                message = "뭐 있어?",
                history = emptyList()
            )
        )

        val best = ReplyQualityEvaluator.selectBest(candidates)

        assertEquals("compact", best?.source)
        assertTrue(candidates.first().reasons.contains("manual_room_style_mismatch_formal"))
        assertTrue(candidates.last().reasons.contains("manual_room_style_match"))
        assertTrue(candidates.last().reasons.contains("manual_example_match"))
    }

    @Test
    fun selectBest_penalizesAssistantBoilerplateAgainstHumanLikeReply() {
        val config = AutoReplyJson.defaultConfig("친구방").copy(
            roomStyle = "친한 친구방. 가볍게 반말. 예시: 아무것도 없긴해"
        )
        val candidates = listOf(
            ReplyQualityEvaluator.evaluate(
                source = "primary",
                raw = "좋은 질문입니다. 추가로 궁금한 점이 있으면 도와드릴게요.",
                reply = "좋은 질문입니다. 추가로 궁금한 점이 있으면 도와드릴게요.",
                config = config,
                message = "오늘 뭐함",
                history = emptyList()
            ),
            ReplyQualityEvaluator.evaluate(
                source = "style_rewrite",
                raw = "아무것도 없긴해",
                reply = "아무것도 없긴해",
                config = config,
                message = "오늘 뭐함",
                history = emptyList()
            )
        )

        val best = ReplyQualityEvaluator.selectBest(candidates)

        assertEquals("style_rewrite", best?.source)
        assertTrue(candidates.first().reasons.contains("assistant_boilerplate"))
        assertTrue(candidates.last().reasons.contains("manual_example_match"))
    }

    @Test
    fun selectBest_penalizesSelfReferentialBusinessToneAgainstManualExample() {
        val config = AutoReplyJson.defaultConfig("친구방").copy(
            roomStyle = "친한 친구방. 가볍게 반말. 예시: 아무것도 없긴해"
        )
        val candidates = listOf(
            ReplyQualityEvaluator.evaluate(
                source = "primary",
                raw = "제가 확인해보겠습니다.",
                reply = "제가 확인해보겠습니다.",
                config = config,
                message = "오늘 별일 있어?",
                history = emptyList()
            ),
            ReplyQualityEvaluator.evaluate(
                source = "human_style",
                raw = "아무것도 없긴해",
                reply = "아무것도 없긴해",
                config = config,
                message = "오늘 별일 있어?",
                history = emptyList()
            )
        )

        val best = ReplyQualityEvaluator.selectBest(candidates)

        assertEquals("human_style", best?.source)
        assertTrue(candidates.first().reasons.contains("self_referential_business_tone"))
        assertTrue(candidates.last().reasons.contains("manual_example_match"))
    }

    @Test
    fun selectBest_penalizesGenericBusinessAckInCasualFriendRoom() {
        val config = AutoReplyJson.defaultConfig("친구방").copy(
            roomStyle = "친한 친구방. 가볍게 반말. 업무용 확인 답장 금지. 예시: 아무것도 없긴해"
        )
        val candidates = listOf(
            ReplyQualityEvaluator.evaluate(
                source = "primary",
                raw = "확인했습니다!",
                reply = "확인했습니다!",
                config = config,
                message = "오늘 별일 있어?",
                history = emptyList()
            ),
            ReplyQualityEvaluator.evaluate(
                source = "human_style",
                raw = "아무것도 없긴해",
                reply = "아무것도 없긴해",
                config = config,
                message = "오늘 별일 있어?",
                history = emptyList()
            )
        )

        val best = ReplyQualityEvaluator.selectBest(candidates)

        assertEquals("human_style", best?.source)
        assertTrue(candidates.first().reasons.contains("generic_business_ack_in_casual_room"))
        assertTrue(candidates.last().reasons.contains("manual_example_match"))
    }

    @Test
    fun selectBest_penalizesShortPromptEchoAgainstHumanLikeReply() {
        val config = AutoReplyJson.defaultConfig("친구방").copy(
            roomStyle = "친한 친구방. 가볍게 반말. 상대 말을 따라 쓰지 말고 예시처럼 답장. 예시: 아무것도 없긴해"
        )
        val candidates = listOf(
            ReplyQualityEvaluator.evaluate(
                source = "primary",
                raw = "오늘 뭐함",
                reply = "오늘 뭐함",
                config = config,
                message = "오늘 뭐함",
                history = emptyList()
            ),
            ReplyQualityEvaluator.evaluate(
                source = "human_style",
                raw = "아무것도 없긴해",
                reply = "아무것도 없긴해",
                config = config,
                message = "오늘 뭐함",
                history = emptyList()
            )
        )

        val best = ReplyQualityEvaluator.selectBest(candidates)

        assertEquals("human_style", best?.source)
        assertTrue(candidates.first().reasons.contains("short_prompt_echo"))
        assertTrue(candidates.last().reasons.contains("manual_example_match"))
    }

    @Test
    fun selectBest_penalizesGenericAckWhenKnownFactAnswersQuestion() {
        val config = AutoReplyJson.defaultConfig("팀단톡").copy(
            roomMemory = "오늘 회의는 15시.",
            roomStyle = "팀 단톡. 존댓말. 메모에 답이 있으면 바로 답장"
        )
        val candidates = listOf(
            ReplyQualityEvaluator.evaluate(
                source = "primary",
                raw = "네 확인했습니다.",
                reply = "네 확인했습니다.",
                config = config,
                message = "오늘 회의 몇 시인가요?",
                history = emptyList()
            ),
            ReplyQualityEvaluator.evaluate(
                source = "compact",
                raw = "15시입니다.",
                reply = "15시입니다.",
                config = config,
                message = "오늘 회의 몇 시인가요?",
                history = emptyList()
            )
        )

        val best = ReplyQualityEvaluator.selectBest(candidates)

        assertEquals("compact", best?.source)
        assertTrue(candidates.first().reasons.contains("generic_ack_instead_of_known_fact"))
        assertTrue(candidates.last().reasons.contains("grounded_fact"))
    }

    @Test
    fun selectBest_penalizesOverExplainedSchoolNoticeReply() {
        val config = AutoReplyJson.defaultConfig("학교방").copy(
            roomStyle = "학교 단톡. 존댓말. 별일 없으면 짧고 단정하게 답장"
        )
        val candidates = listOf(
            ReplyQualityEvaluator.evaluate(
                source = "primary",
                raw = "별일 없습니다. 추가로 필요한 내용이 있으면 말씀해 주세요.",
                reply = "별일 없습니다. 추가로 필요한 내용이 있으면 말씀해 주세요.",
                config = config,
                message = "오늘 전달할 내용 있나요?",
                history = emptyList()
            ),
            ReplyQualityEvaluator.evaluate(
                source = "compact",
                raw = "별일 없습니다.",
                reply = "별일 없습니다.",
                config = config,
                message = "오늘 전달할 내용 있나요?",
                history = emptyList()
            )
        )

        val best = ReplyQualityEvaluator.selectBest(candidates)

        assertEquals("compact", best?.source)
        assertTrue(candidates.first().reasons.contains("over_explained"))
        assertTrue(candidates.last().reasons.contains("manual_room_style_match"))
    }

    @Test
    fun selectBest_penalizesLowSignalOverReplyAgainstBriefAck() {
        val config = AutoReplyJson.defaultConfig("친구방").copy(
            roomStyle = "친한 친구방. 가볍게 반말"
        )
        val history = listOf(
            RoomHistoryMessage("민수", "그럼 오늘 7시에 학교 앞에서 보자", true, 1L),
            RoomHistoryMessage("나", "좋아 그때 갈게", false, 2L),
            RoomHistoryMessage("민수", "늦으면 바로 말해줘", true, 3L)
        )
        val candidates = listOf(
            ReplyQualityEvaluator.evaluate(
                source = "primary",
                raw = "응 알겠어. 필요하면 추가로 알려줘.",
                reply = "응 알겠어. 필요하면 추가로 알려줘.",
                config = config,
                message = "ㅇㅋ",
                history = history
            ),
            ReplyQualityEvaluator.evaluate(
                source = "human_style",
                raw = "응 알겠어",
                reply = "응 알겠어",
                config = config,
                message = "ㅇㅋ",
                history = history
            )
        )

        val best = ReplyQualityEvaluator.selectBest(candidates)

        assertEquals("human_style", best?.source)
        assertTrue(candidates.first().reasons.contains("low_signal_overreply"))
        assertTrue(!candidates.last().reasons.contains("low_signal_overreply"))
    }

    @Test
    fun selectBest_usesSourcePriorOnlyAsTieBreakerForCloseCandidates() {
        val config = AutoReplyJson.defaultConfig("친구방").copy(
            roomStyle = "친한 친구방. 가볍게 반말."
        )
        val candidates = listOf(
            ReplyQualityEvaluator.evaluate(
                source = "primary",
                raw = "응 알겠어",
                reply = "응 알겠어",
                config = config,
                message = "오늘 가능?",
                history = emptyList()
            ),
            ReplyQualityEvaluator.evaluate(
                source = "human_style",
                raw = "가능해",
                reply = "가능해",
                config = config,
                message = "오늘 가능?",
                history = emptyList()
            )
        )

        val best = ReplyQualityEvaluator.selectBest(
            candidates = candidates,
            sourcePriors = mapOf("human_style" to 6)
        )

        assertEquals("human_style", best?.source)
    }

    @Test
    fun selectBest_doesNotLetSourcePriorOverrideClearlyBetterReply() {
        val config = AutoReplyJson.defaultConfig("친구방").copy(
            roomStyle = "친한 친구방. 가볍게 반말. 예시: 아무것도 없긴해"
        )
        val candidates = listOf(
            ReplyQualityEvaluator.evaluate(
                source = "primary",
                raw = "아무것도 없긴해",
                reply = "아무것도 없긴해",
                config = config,
                message = "오늘 뭐 있어?",
                history = emptyList()
            ),
            ReplyQualityEvaluator.evaluate(
                source = "compact",
                raw = "좋은 질문입니다. 무엇을 도와드릴까요?",
                reply = "좋은 질문입니다. 무엇을 도와드릴까요?",
                config = config,
                message = "오늘 뭐 있어?",
                history = emptyList()
            )
        )

        val best = ReplyQualityEvaluator.selectBest(
            candidates = candidates,
            sourcePriors = mapOf("compact" to 6)
        )

        assertEquals("primary", best?.source)
    }

    @Test
    fun dedupeCandidates_marksRepeatedRepliesAsDuplicate() {
        val config = AutoReplyJson.defaultConfig("친구방").copy(
            roomStyle = "친한 친구방. 가볍게 반말"
        )
        val candidates = listOf(
            ReplyQualityEvaluator.evaluate(
                source = "primary",
                raw = "응 알겠어",
                reply = "응 알겠어",
                config = config,
                message = "오늘 가능?",
                history = emptyList()
            ),
            ReplyQualityEvaluator.evaluate(
                source = "compact",
                raw = "응, 알겠어.",
                reply = "응, 알겠어.",
                config = config,
                message = "오늘 가능?",
                history = emptyList()
            )
        )

        val deduped = ReplyQualityEvaluator.dedupeCandidates(candidates)

        assertEquals(2, deduped.size)
        assertTrue(deduped.any { it.reasons.contains("duplicate_reply") })
        assertTrue(deduped.any { it.score > 0 && !it.reasons.contains("duplicate_reply") })
    }

    @Test
    fun selectBest_ignoresDuplicateReplyEvenWithSourcePrior() {
        val config = AutoReplyJson.defaultConfig("친구방").copy(
            roomStyle = "친한 친구방. 가볍게 반말"
        )
        val candidates = listOf(
            ReplyQualityEvaluator.evaluate(
                source = "primary",
                raw = "가능해",
                reply = "가능해",
                config = config,
                message = "오늘 가능?",
                history = emptyList()
            ),
            ReplyQualityEvaluator.evaluate(
                source = "human_style",
                raw = "가능해",
                reply = "가능해",
                config = config,
                message = "오늘 가능?",
                history = emptyList()
            )
        )

        val best = ReplyQualityEvaluator.selectBest(
            candidates = candidates,
            sourcePriors = mapOf("human_style" to 6)
        )

        assertEquals("primary", best?.source)
    }

    @Test
    fun selectBest_penalizesCasualToneInSchoolRoom() {
        val config = AutoReplyJson.defaultConfig("학교방").copy(
            roomStyle = "학교 단톡. 존댓말로 단정하게 답장"
        )
        val candidates = listOf(
            ReplyQualityEvaluator.evaluate(
                source = "primary",
                raw = "별일 없어",
                reply = "별일 없어",
                config = config,
                message = "별일 있나요?",
                history = emptyList()
            ),
            ReplyQualityEvaluator.evaluate(
                source = "compact",
                raw = "별일없습니다!",
                reply = "별일없습니다!",
                config = config,
                message = "별일 있나요?",
                history = emptyList()
            )
        )

        val best = ReplyQualityEvaluator.selectBest(candidates)

        assertEquals("compact", best?.source)
        assertTrue(candidates.first().reasons.contains("manual_room_style_mismatch_casual"))
        assertTrue(candidates.last().reasons.contains("manual_room_style_match"))
    }

    @Test
    fun builtInScenarios_coverRequiredQualityCases() {
        val coverage = ReplyQualityScenarios.coverageSummary()
        val requiredIds = setOf(
            "friend_light",
            "friend_no_echo",
            "team_formal",
            "team_known_fact_no_generic_ack",
            "school_formal_notice",
            "school_concise_no_overexplained",
            "low_signal_skip",
            "low_signal_with_context_ack",
            "ambiguous_clarify",
            "school_ambiguous_formal",
            "unknown_fact_guard",
            "friend_unknown_fact_guard",
            "room_memory_fact",
            "manual_example_override",
            "friend_no_business_ack"
        )

        assertTrue("required quality matrix missing: ${coverage.scenarioIds}", coverage.hasRequiredScenarios(requiredIds))
        assertTrue("formal trait missing", "formal" in coverage.traitIds)
        assertTrue("casual trait missing", "casual" in coverage.traitIds)
        assertTrue("clarify trait missing", "clarify" in coverage.traitIds)
        assertTrue("unknown guard trait missing", "unknown_guard" in coverage.traitIds)
        assertTrue("grounded fact trait missing", "grounded_fact" in coverage.traitIds)
        assertTrue("manual example trait missing", "manual_example" in coverage.traitIds)
        assertTrue("no echo trait missing", "no_echo" in coverage.traitIds)
        assertTrue("no generic ack trait missing", "no_generic_ack" in coverage.traitIds)
        assertTrue("concise trait missing", "concise_no_overexplained" in coverage.traitIds)
        assertTrue("low signal brief ack trait missing", "low_signal_brief_ack" in coverage.traitIds)
        assertTrue("skip trait missing", "skip" in coverage.traitIds)
        assertTrue("ack trait missing", "ack" in coverage.traitIds)
        assertTrue("no business ack trait missing", "no_business_ack" in coverage.traitIds)
    }

    @Test
    fun traitScore_rewardsExpectedRoomStyle() {
        val friend = ReplyQualityScenarios.builtIns().first { it.id == "friend_light" }
        val team = ReplyQualityScenarios.builtIns().first { it.id == "team_formal" }
        val school = ReplyQualityScenarios.builtIns().first { it.id == "school_formal_notice" }

        assertTrue(ReplyQualityScenarios.traitScore("아무것도 없긴해", friend) >= 40)
        assertTrue(ReplyQualityScenarios.traitScore("별일없습니다!", team) >= 40)
        assertTrue(ReplyQualityScenarios.traitScore("별일 없습니다.", school) >= 40)
    }

    @Test
    fun builtInScenarioExamples_passQualityThresholds() {
        val replies = mapOf(
            "friend_light" to "아무것도 없긴해",
            "friend_no_echo" to "아무것도 없긴해",
            "team_formal" to "별일없습니다!",
            "team_known_fact_no_generic_ack" to "15시입니다.",
            "school_formal_notice" to "별일 없습니다.",
            "school_concise_no_overexplained" to "별일 없습니다.",
            "low_signal_skip" to "",
            "low_signal_with_context_ack" to "응 알겠어",
            "ambiguous_clarify" to "문서 말하는 거야, 발표 자료 말하는 거야?",
            "school_ambiguous_formal" to "문서 초안 말씀하시는 건가요, 발표 자료 말씀하시는 건가요?",
            "unknown_fact_guard" to "아직 확인된 내용은 못 찾았습니다.",
            "friend_unknown_fact_guard" to "아직 확인된 건 못 찾았어.",
            "room_memory_fact" to "6월 12일 18시까지입니다.",
            "manual_example_override" to "아무것도 없긴해",
            "friend_no_business_ack" to "아무것도 없긴해"
        )

        ReplyQualityScenarios.builtIns().forEach { scenario ->
            val reply = replies.getValue(scenario.id)
            val result = ReplyQualityScenarios.evaluateReply(reply, scenario)

            assertTrue("${scenario.id} should pass, score=${result.score}", result.passed)
        }
    }

    @Test
    fun builtInScenarioExamples_rejectMetaAndOverconfidentReplies() {
        val unknown = ReplyQualityScenarios.builtIns().first { it.id == "unknown_fact_guard" }
        val ambiguous = ReplyQualityScenarios.builtIns().first { it.id == "ambiguous_clarify" }
        val manualOverride = ReplyQualityScenarios.builtIns().first { it.id == "manual_example_override" }
        val noEcho = ReplyQualityScenarios.builtIns().first { it.id == "friend_no_echo" }
        val noGenericAck = ReplyQualityScenarios.builtIns().first { it.id == "team_known_fact_no_generic_ack" }
        val concise = ReplyQualityScenarios.builtIns().first { it.id == "school_concise_no_overexplained" }
        val lowSignalAck = ReplyQualityScenarios.builtIns().first { it.id == "low_signal_with_context_ack" }
        val friendNoBusinessAck = ReplyQualityScenarios.builtIns().first { it.id == "friend_no_business_ack" }

        assertTrue(!ReplyQualityScenarios.evaluateReply("AI 모델로는 답변할 수 없습니다.", unknown).passed)
        assertTrue(!ReplyQualityScenarios.evaluateReply("됐어.", ambiguous).passed)
        assertTrue(!ReplyQualityScenarios.evaluateReply("별일 없습니다.", manualOverride).passed)
        assertTrue(!ReplyQualityScenarios.evaluateReply("오늘 뭐함", noEcho).passed)
        assertTrue(!ReplyQualityScenarios.evaluateReply("네 확인했습니다.", noGenericAck).passed)
        assertTrue(!ReplyQualityScenarios.evaluateReply("별일 없습니다. 추가로 필요한 내용이 있으면 말씀해 주세요.", concise).passed)
        assertTrue(!ReplyQualityScenarios.evaluateReply("응 알겠어. 필요하면 추가로 알려줘.", lowSignalAck).passed)
        assertTrue(!ReplyQualityScenarios.evaluateReply("제가 확인해보겠습니다.", manualOverride).passed)
        assertTrue(!ReplyQualityScenarios.evaluateReply("확인했습니다!", friendNoBusinessAck).passed)
    }
}
