package com.example.kakaotalkautobot

import org.junit.Assert.assertEquals
import org.junit.Test

class RoomStoreParserEdgeCaseTest {
    @Test
    fun parseImportedLines_ignoresInvalidSenderOrEmptyMessage() {
        val parsed = RoomStore.parseImportedLines(
            raw = """
                : 메시지 없음
                민수:
                정상인: 첫 번째
                
                | 잘못된 줄
            """.trimIndent(),
            timestamp = 555L
        )

        assertEquals(1, parsed.size)
        assertEquals("정상인", parsed.first().sender)
        assertEquals("첫 번째", parsed.first().message)
        assertEquals(555L, parsed.first().timestamp)
    }

    @Test
    fun parseImportedLines_pipeFormat_usesLastTwoColumnsForSenderAndMessage() {
        val parsed = RoomStore.parseImportedLines(
            raw = """
                2026-04-02 | 일반 | 민수 | 회의는 3시에 시작
                2026-04-02 | 시스템 | 지우 | 자료를 올렸어요
            """.trimIndent(),
            timestamp = 999L
        )

        assertEquals(2, parsed.size)
        assertEquals("민수", parsed[0].sender)
        assertEquals("회의는 3시에 시작", parsed[0].message)
        assertEquals("지우", parsed[1].sender)
        assertEquals("자료를 올렸어요", parsed[1].message)
    }
}
