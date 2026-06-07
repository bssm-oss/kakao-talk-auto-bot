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
}
