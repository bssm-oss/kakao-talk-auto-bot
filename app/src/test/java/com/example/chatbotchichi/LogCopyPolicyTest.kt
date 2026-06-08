package com.example.kakaotalkautobot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogCopyPolicyTest {
    @Test
    fun dialogText_usesPlainCopyForRedactedLogs() {
        val text = LogCopyPolicy.dialogText(redacted = true)

        assertTrue(text.title.contains("마스킹"))
        assertTrue(text.message.contains("가린 로그"))
        assertTrue(text.positiveButton == "복사")
        assertFalse(text.message.contains("그대로 포함"))
    }

    @Test
    fun dialogText_requiresExplicitRiskAcknowledgementForRawLogs() {
        val text = LogCopyPolicy.dialogText(redacted = false)

        assertTrue(text.title.contains("원본"))
        assertTrue(text.message.contains("방 이름"))
        assertTrue(text.message.contains("발화자"))
        assertTrue(text.message.contains("메시지 내용"))
        assertTrue(text.message.contains("그대로 포함"))
        assertTrue(text.message.contains("다시 확인"))
        assertTrue(text.positiveButton.contains("위험"))
        assertTrue(text.positiveButton.contains("원본 복사"))
    }
}
