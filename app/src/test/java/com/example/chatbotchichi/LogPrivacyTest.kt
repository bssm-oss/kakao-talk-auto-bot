package com.example.kakaotalkautobot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogPrivacyTest {
    @Test
    fun redact_hidesIncomingMessageBody() {
        val raw = "[12:00:00][IN] [친구방] 민수: 오늘 뭐해?"

        val redacted = LogPrivacy.redact(raw)

        assertFalse(redacted.contains("친구방"))
        assertFalse(redacted.contains("민수"))
        assertFalse(redacted.contains("오늘 뭐해"))
        assertTrue(redacted.contains("<방 숨김>"))
        assertTrue(redacted.contains("<발화자 숨김>"))
        assertTrue(redacted.contains("<메시지 숨김>"))
    }

    @Test
    fun redact_keepsFailureReasonVisible() {
        val raw = "[12:00:00][OUT_FAIL] ❌ [친구방] 답장 실패 (reason=no session)"

        val redacted = LogPrivacy.redact(raw)

        assertFalse(redacted.contains("친구방"))
        assertTrue(redacted.contains("<방 숨김>"))
        assertTrue(redacted.contains("reason=no session"))
    }

    @Test
    fun redact_hidesOutgoingMessageBody() {
        val raw = "[12:00:00][OUT] [팀방] 내일 발표 자료 보낼게"

        val redacted = LogPrivacy.redact(raw)

        assertFalse(redacted.contains("팀방"))
        assertFalse(redacted.contains("내일 발표 자료"))
        assertTrue(redacted.contains("<방 숨김>"))
        assertTrue(redacted.contains("<내용 숨김>"))
    }
}
