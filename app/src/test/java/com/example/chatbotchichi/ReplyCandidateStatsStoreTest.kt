package com.example.kakaotalkautobot

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyCandidateStatsStoreTest {
    @Test
    fun snapshotFromJson_readsSourceStats() {
        val root = JSONObject().apply {
            put(
                "human_style",
                JSONObject()
                    .put("generated", 3)
                    .put("selected", 2)
                    .put("blank", 1)
                    .put("lowQuality", 1)
                    .put("totalLatencyMs", 900L)
                    .put("maxLatencyMs", 500L)
            )
        }

        val stats = ReplyCandidateStatsStore.snapshotFromJson(root).sources.getValue("human_style")

        assertEquals(3, stats.generated)
        assertEquals(2, stats.selected)
        assertEquals(1, stats.blank)
        assertEquals(1, stats.lowQuality)
        assertEquals(300L, stats.averageLatencyMs)
        assertEquals(500L, stats.maxLatencyMs)
    }

    @Test
    fun summary_ordersBySelectedThenGeneratedCount() {
        val snapshot = ReplyCandidateStatsStore.Snapshot(
            sources = mapOf(
                "primary" to ReplyCandidateStatsStore.SourceStats(
                    generated = 5,
                    selected = 1,
                    blank = 0,
                    lowQuality = 1,
                    totalLatencyMs = 1000L,
                    maxLatencyMs = 300L
                ),
                "human_style" to ReplyCandidateStatsStore.SourceStats(
                    generated = 4,
                    selected = 3,
                    blank = 1,
                    lowQuality = 1,
                    totalLatencyMs = 2000L,
                    maxLatencyMs = 800L
                )
            )
        )

        val summary = snapshot.summary()

        assertTrue(summary.startsWith("human_style 선택 3/4"))
        assertTrue(summary.contains("빈 1"))
        assertTrue(summary.contains("저품질 1"))
        assertTrue(summary.contains("평균 500ms"))
    }

    @Test
    fun emptySummaryExplainsNoCandidateStats() {
        assertEquals("후보 통계 없음", ReplyCandidateStatsStore.Snapshot(emptyMap()).summary())
    }
}
