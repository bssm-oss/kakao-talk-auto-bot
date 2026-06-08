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
            "unknown_fact_guard",
            "room_memory_fact"
        )

        requiredCovered.forEach { scenarioId ->
            val result = requireNotNull(byId[scenarioId])
            assertTrue("$scenarioId should be covered without LLM", result.coveredWithoutLlm)
            assertTrue("$scenarioId should pass without LLM: $result", result.passed)
        }
        assertEquals("pre_model_skip", byId.getValue("low_signal_skip").source)
        assertEquals("ambiguous_clarify", byId.getValue("ambiguous_clarify").source)
        assertEquals("unknown_guard", byId.getValue("unknown_fact_guard").source)
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
        assertTrue(report.contains("| room_memory_fact |"))
        assertTrue(report.contains("| manual_example_override |"))
        assertTrue(report.contains("| manual_example_override | 친구방 | casual, manual_example, short |"))
        assertTrue(report.contains("| manual_example_override | requires_llm | no |"))
        assertTrue(report.contains("| room_memory_fact | deadline_fact | yes |"))
    }
}
