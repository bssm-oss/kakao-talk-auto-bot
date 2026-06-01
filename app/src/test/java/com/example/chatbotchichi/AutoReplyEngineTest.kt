package com.example.kakaotalkautobot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoReplyEngineTest {
    @Test
    fun shouldAcceptReplyWork_dedupesSameRoomSenderMessageWithinTtl() {
        AutoReplyEngine.clearRecentReplyWorkForTest()

        assertTrue(
            AutoReplyEngine.shouldAcceptReplyWork(
                room = "친구방",
                sender = "민수",
                message = "뭐해",
                nowMs = 1_000L
            )
        )
        assertFalse(
            AutoReplyEngine.shouldAcceptReplyWork(
                room = "친구방",
                sender = "민수",
                message = "뭐해",
                nowMs = 2_000L
            )
        )
        assertTrue(
            AutoReplyEngine.shouldAcceptReplyWork(
                room = "친구방",
                sender = "민수",
                message = "뭐해",
                nowMs = 14_000L
            )
        )
    }
}
