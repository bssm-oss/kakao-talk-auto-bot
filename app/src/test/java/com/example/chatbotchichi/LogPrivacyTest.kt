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

    @Test
    fun redact_hidesSensitiveTokensInUnstructuredLines() {
        val raw = "[12:00:00][DEBUG] 연락처 010-1234-5678, 메일 test@example.com, 링크 https://example.com/a"

        val redacted = LogPrivacy.redact(raw)

        assertFalse(redacted.contains("010-1234-5678"))
        assertFalse(redacted.contains("test@example.com"))
        assertFalse(redacted.contains("https://example.com/a"))
        assertTrue(redacted.contains("<전화번호 숨김>"))
        assertTrue(redacted.contains("<이메일 숨김>"))
        assertTrue(redacted.contains("<URL 숨김>"))
    }

    @Test
    fun redact_hidesSensitiveTokensInsideFailureReason() {
        val raw = "[12:00:00][OUT_FAIL] ❌ [친구방] 답장 실패 (reason=exception:test@example.com)"

        val redacted = LogPrivacy.redact(raw)

        assertFalse(redacted.contains("친구방"))
        assertFalse(redacted.contains("test@example.com"))
        assertTrue(redacted.contains("<방 숨김>"))
        assertTrue(redacted.contains("reason=exception:<이메일 숨김>"))
    }
}
