package com.example.kakaotalkautobot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyQualityReportTest {
    @Test
    fun baselineReport_containsEveryScenarioAndPasses() {
        val scenarios = ReplyQualityScenarios.builtIns()
        val report = ReplyQualityScenarios.baselineReportMarkdown(scenarios)
        val results = ReplyQualityScenarios.evaluateBaselineReplies(scenarios)

        scenarios.forEach { scenario ->
            assertTrue("report missing scenario ${scenario.id}", report.contains("| ${scenario.id} |"))
        }
        assertTrue(report.contains("## Engine Baseline Without LLM"))
        assertTrue("baseline failures: ${results.filterNot { it.passed }}", results.all { it.passed })
    }

    @Test
    fun engineBaseline_coversDeterministicAndSkipScenarios() {
        val results = ReplyQualityScenarios.evaluateEngineBaselines()
        val byId = results.associateBy { it.scenarioId }
        val requiredCovered = setOf(
            "low_signal_skip",
            "ambiguous_clarify",
            "school_ambiguous_formal",
            "unknown_fact_guard",
            "friend_unknown_fact_guard",
            "room_memory_fact"
        )

        requiredCovered.forEach { scenarioId ->
            val result = requireNotNull(byId[scenarioId])
            assertTrue("$scenarioId should be covered without LLM", result.coveredWithoutLlm)
            assertTrue("$scenarioId should pass without LLM: $result", result.passed)
        }
        assertEquals("pre_model_skip", byId.getValue("low_signal_skip").source)
        assertEquals("requires_llm", byId.getValue("low_signal_with_context_ack").source)
        assertTrue("low signal with context should not be pre-model skipped", !byId.getValue("low_signal_with_context_ack").coveredWithoutLlm)
        assertEquals("ambiguous_clarify", byId.getValue("ambiguous_clarify").source)
        assertEquals("ambiguous_clarify", byId.getValue("school_ambiguous_formal").source)
        assertEquals("unknown_guard", byId.getValue("unknown_fact_guard").source)
        assertEquals("unknown_guard", byId.getValue("friend_unknown_fact_guard").source)
        assertEquals("deadline_fact", byId.getValue("room_memory_fact").source)
    }

    @Test
    fun writeBaselineReportForManualReview() {
        val report = ReplyQualityScenarios.baselineReportMarkdown()
        val output = File("build/reports/reply-quality/report.md")

        output.parentFile?.mkdirs()
        output.writeText(report)

        assertTrue(output.exists())
        assertTrue(report.contains("# Reply Quality Baseline"))
        assertTrue(report.contains("| friend_light |"))
        assertTrue(report.contains("| friend_no_echo |"))
        assertTrue(report.contains("| team_known_fact_no_generic_ack |"))
        assertTrue(report.contains("| school_concise_no_overexplained |"))
        assertTrue(report.contains("| low_signal_with_context_ack |"))
        assertTrue(report.contains("| school_ambiguous_formal |"))
        assertTrue(report.contains("| friend_unknown_fact_guard |"))
        assertTrue(report.contains("| room_memory_fact |"))
        assertTrue(report.contains("| manual_example_override |"))
        assertTrue(report.contains("| friend_no_service_apology |"))
        assertTrue(report.contains("| friend_no_helper_followup |"))
        assertTrue(report.contains("| friend_no_generic_encouragement |"))
        assertTrue(report.contains("| friend_no_generic_casual_ack |"))
        assertTrue(report.contains("| friend_no_false_delay_apology |"))
        assertTrue(report.contains("| friend_no_therapy_empathy |"))
        assertTrue(report.contains("| friend_no_reaction_spam |"))
        assertTrue(report.contains("| friend_no_formal_gratitude |"))
        assertTrue(report.contains("| friend_no_echo | 친구방 | casual, manual_example, no_echo, short |"))
        assertTrue(report.contains("| team_known_fact_no_generic_ack | 팀단톡 | formal, grounded_fact, no_generic_ack, short |"))
        assertTrue(report.contains("| school_concise_no_overexplained | 학교방 | concise_no_overexplained, formal, short |"))
        assertTrue(report.contains("| low_signal_with_context_ack | 친구방 | ack, casual, low_signal_brief_ack, short |"))
        assertTrue(report.contains("| ambiguous_clarify | 프로젝트방 | clarify, no_overclaim, short |"))
        assertTrue(report.contains("| school_ambiguous_formal | 학교방 | clarify, formal, no_overclaim, short |"))
        assertTrue(report.contains("| manual_example_override | 친구방 | casual, manual_example, short |"))
        assertTrue(report.contains("| friend_no_service_apology | 친구방 | casual, manual_example, no_service_apology, short |"))
        assertTrue(report.contains("| friend_no_helper_followup | 친구방 | casual, manual_example, no_helper_followup, short |"))
        assertTrue(report.contains("| friend_no_generic_encouragement | 친구방 | casual, manual_example, no_generic_encouragement, short |"))
        assertTrue(report.contains("| friend_no_generic_casual_ack | 친구방 | casual, manual_example, no_generic_casual_ack, short |"))
        assertTrue(report.contains("| friend_no_false_delay_apology | 친구방 | casual, manual_example, no_false_delay_apology, short |"))
        assertTrue(report.contains("| friend_no_therapy_empathy | 친구방 | casual, manual_example, no_therapy_empathy, short |"))
        assertTrue(report.contains("| friend_no_reaction_spam | 친구방 | casual, manual_example, no_reaction_spam, short |"))
        assertTrue(report.contains("| friend_no_formal_gratitude | 친구방 | casual, manual_example, no_formal_gratitude, short |"))
        assertTrue(report.contains("| manual_example_override | requires_llm | no |"))
        assertTrue(report.contains("| room_memory_fact | deadline_fact | yes |"))
    }
}
