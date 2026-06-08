package com.example.kakaotalkautobot

import java.io.File
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
        assertTrue("baseline failures: ${results.filterNot { it.passed }}", results.all { it.passed })
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
    }
}
