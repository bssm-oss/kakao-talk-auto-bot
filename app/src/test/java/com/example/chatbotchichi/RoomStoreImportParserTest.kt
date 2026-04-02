package com.example.kakaotalkautobot

import org.junit.Assert.assertEquals
import org.junit.Test

class RoomStoreImportParserTest {
    @Test
    fun parseImportedLines_supportsPipeAndColonFormats() {
        val parsed = RoomStore.parseImportedLines(
            raw = """
                2026-04-02 12:00 | 민수 | 첫 번째 메시지
                지우: 두 번째 메시지
                잘못된 줄
            """.trimIndent(),
            timestamp = 1234L
        )

        assertEquals(2, parsed.size)
        assertEquals("민수", parsed[0].sender)
        assertEquals("첫 번째 메시지", parsed[0].message)
        assertEquals(1234L, parsed[0].timestamp)
        assertEquals("지우", parsed[1].sender)
        assertEquals("두 번째 메시지", parsed[1].message)
    }
}
