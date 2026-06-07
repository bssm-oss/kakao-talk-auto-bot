package com.example.kakaotalkautobot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogPrivacyTest {
    @Test
    fun redact_hidesIncomingMessageBody() {
        val raw = "[12:00:00][IN] [친구방] 민수: 오늘 뭐해?"

        val redacted = LogPrivacy.redact(raw)

        assertFalse(redacted.contains("오늘 뭐해"))
        assertTrue(redacted.contains("<메시지 숨김>"))
    }

    @Test
    fun redact_keepsFailureReasonVisible() {
        val raw = "[12:00:00][OUT_FAIL] ❌ [친구방] 답장 실패 (reason=no session)"

        val redacted = LogPrivacy.redact(raw)

        assertTrue(redacted.contains("reason=no session"))
    }
}
