package com.example.kakaotalkautobot

import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPersonaHintTest {
    @Test
    fun buildPersonaHint_describesRecentToneFromOwnMessages() {
        val hint = AutoMemoryStore.buildPersonaHint(
            displayName = "허동운",
            history = listOf(
                RoomHistoryMessage("허동운", "네, 확인해볼게요", true, 1L),
                RoomHistoryMessage("허동운", "이 부분은 제가 조금 더 자세히 정리해볼게요", true, 2L),
                RoomHistoryMessage("황준혁", "햇냐", true, 3L)
            )
        )

        assertTrue(hint!!.contains("허동운"))
        assertTrue(hint.contains("존댓말"))
        assertTrue(hint.contains("응답 밀도는"))
    }
}
