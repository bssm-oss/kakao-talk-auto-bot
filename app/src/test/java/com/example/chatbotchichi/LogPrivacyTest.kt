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

    @Test
    fun redact_hidesLooseBracketedChatLines() {
        val raw = "카톡 알림 [비밀친구방] 민수: 오늘 7시에 보자"

        val redacted = LogPrivacy.redact(raw)

        assertFalse(redacted.contains("비밀친구방"))
        assertFalse(redacted.contains("민수"))
        assertFalse(redacted.contains("오늘 7시에 보자"))
        assertTrue(redacted.contains("<카톡 로그 숨김>"))
    }

    @Test
    fun redact_hidesChatKeyValueLines() {
        val raw = "debug room=비밀팀방, sender=민수, msg=010-1234-5678로 전화줘"

        val redacted = LogPrivacy.redact(raw)

        assertFalse(redacted.contains("비밀팀방"))
        assertFalse(redacted.contains("민수"))
        assertFalse(redacted.contains("010-1234-5678"))
        assertTrue(redacted.contains("room=<방 숨김>"))
        assertTrue(redacted.contains("sender=<발화자 숨김>"))
        assertTrue(redacted.contains("msg=<메시지 숨김>"))
    }

    @Test
    fun redact_hidesKoreanChatKeyValueLines() {
        val raw = "디버그 방=비밀방, 발화자=지우, 메시지=내일 자료 보내줘"

        val redacted = LogPrivacy.redact(raw)

        assertFalse(redacted.contains("비밀방"))
        assertFalse(redacted.contains("지우"))
        assertFalse(redacted.contains("내일 자료 보내줘"))
        assertTrue(redacted.contains("방=<방 숨김>"))
        assertTrue(redacted.contains("발화자=<발화자 숨김>"))
        assertTrue(redacted.contains("메시지=<메시지 숨김>"))
    }
}
