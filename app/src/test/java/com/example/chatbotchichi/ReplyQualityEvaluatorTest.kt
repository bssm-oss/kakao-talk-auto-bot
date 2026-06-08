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
    fun builtInScenarios_coverRequiredQualityCases() {
        val ids = ReplyQualityScenarios.builtIns().map { it.id }.toSet()

        assertTrue("friend scenario missing", "friend_light" in ids)
        assertTrue("team scenario missing", "team_formal" in ids)
        assertTrue("low signal scenario missing", "low_signal_skip" in ids)
        assertTrue("ambiguous scenario missing", "ambiguous_clarify" in ids)
        assertTrue("unknown fact scenario missing", "unknown_fact_guard" in ids)
        assertTrue("room memory scenario missing", "room_memory_fact" in ids)
    }

    @Test
    fun traitScore_rewardsExpectedRoomStyle() {
        val friend = ReplyQualityScenarios.builtIns().first { it.id == "friend_light" }
        val team = ReplyQualityScenarios.builtIns().first { it.id == "team_formal" }

        assertTrue(ReplyQualityScenarios.traitScore("아무것도 없긴해", friend) >= 40)
        assertTrue(ReplyQualityScenarios.traitScore("별일없습니다!", team) >= 40)
    }

    @Test
    fun builtInScenarioExamples_passQualityThresholds() {
        val replies = mapOf(
            "friend_light" to "아무것도 없긴해",
            "team_formal" to "별일없습니다!",
            "low_signal_skip" to "",
            "ambiguous_clarify" to "문서 말하는 거야, 발표 자료 말하는 거야?",
            "unknown_fact_guard" to "아직 확인된 내용은 못 찾았습니다.",
            "room_memory_fact" to "6월 12일 18시까지입니다."
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

        assertTrue(!ReplyQualityScenarios.evaluateReply("AI 모델로는 답변할 수 없습니다.", unknown).passed)
        assertTrue(!ReplyQualityScenarios.evaluateReply("됐어.", ambiguous).passed)
    }
}
