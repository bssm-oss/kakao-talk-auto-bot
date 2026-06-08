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
            "team_formal",
            "school_formal_notice",
            "low_signal_skip",
            "ambiguous_clarify",
            "unknown_fact_guard",
            "room_memory_fact",
            "manual_example_override"
        )

        assertTrue("required quality matrix missing: ${coverage.scenarioIds}", coverage.hasRequiredScenarios(requiredIds))
        assertTrue("formal trait missing", "formal" in coverage.traitIds)
        assertTrue("casual trait missing", "casual" in coverage.traitIds)
        assertTrue("clarify trait missing", "clarify" in coverage.traitIds)
        assertTrue("unknown guard trait missing", "unknown_guard" in coverage.traitIds)
        assertTrue("grounded fact trait missing", "grounded_fact" in coverage.traitIds)
        assertTrue("manual example trait missing", "manual_example" in coverage.traitIds)
        assertTrue("skip trait missing", "skip" in coverage.traitIds)
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
            "team_formal" to "별일없습니다!",
            "school_formal_notice" to "별일 없습니다.",
            "low_signal_skip" to "",
            "ambiguous_clarify" to "문서 말하는 거야, 발표 자료 말하는 거야?",
            "unknown_fact_guard" to "아직 확인된 내용은 못 찾았습니다.",
            "room_memory_fact" to "6월 12일 18시까지입니다.",
            "manual_example_override" to "아무것도 없긴해"
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

        assertTrue(!ReplyQualityScenarios.evaluateReply("AI 모델로는 답변할 수 없습니다.", unknown).passed)
        assertTrue(!ReplyQualityScenarios.evaluateReply("됐어.", ambiguous).passed)
        assertTrue(!ReplyQualityScenarios.evaluateReply("별일 없습니다.", manualOverride).passed)
    }
}
