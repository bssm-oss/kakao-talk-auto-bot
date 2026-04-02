package com.example.kakaotalkautobot

import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMemoryStoreTest {
    @Test
    fun buildSummary_includesParticipantsKeywordsAndDomains() {
        val summary = AutoMemoryStore.buildSummary(
            listOf(
                RoomHistoryMessage("황준혁", "https://www.figma.com/design/abc 디자인 봐줘", true, 1L),
                RoomHistoryMessage("김현우", "https://api.jagalchi.dev/ 접근은된다이제", true, 2L),
                RoomHistoryMessage("황준혁", "햇냐? 햇냐?", true, 3L)
            )
        )

        assertTrue(summary.contains("황준혁"))
        assertTrue(summary.contains("김현우"))
        assertTrue(summary.contains("figma.com"))
        assertTrue(summary.contains("api.jagalchi.dev"))
    }
}
