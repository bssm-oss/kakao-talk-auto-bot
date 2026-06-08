package com.example.kakaotalkautobot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReplierTest {
    @Test
    fun sendResult_keepsStructuredFailureReason() {
        val result = SessionReplier.SendResult(
            sent = false,
            reason = SessionReplier.REASON_NO_REMOTE_INPUT
        )

        assertFalse(result.sent)
        assertEquals("no remoteInput", result.reason)
    }

    @Test
    fun knownFailureReasons_coverRealDeviceTroubleshootingCases() {
        val reasons = setOf(
            SessionReplier.REASON_NO_SESSION,
            SessionReplier.REASON_NO_REMOTE_INPUT,
            SessionReplier.REASON_PENDING_INTENT_NULL,
            SessionReplier.REASON_PENDING_INTENT_SEND_FAILED,
            SessionReplier.REASON_EXCEPTION
        )

        assertTrue("no session" in reasons)
        assertTrue("no remoteInput" in reasons)
        assertTrue("pendingIntent null" in reasons)
        assertTrue("pendingIntent send failed" in reasons)
    }
}
