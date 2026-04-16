package com.example.kakaotalkautobot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationListenerTest {
    private val listener = NotificationListener()

    @Test
    fun incomingHandlingPlan_keepsCaptureWhenGlobalRepliesAreOff() {
        val plan = listener.incomingHandlingPlan(
            roomConfig = AutoReplyJson.defaultConfig("테스트방").copy(captureEnabled = true, replyEnabled = true),
            aiReplyEnabled = false
        )

        assertTrue(plan.shouldCapture)
        assertFalse(plan.shouldAttemptReply)
    }

    @Test
    fun incomingHandlingPlan_keepsCaptureWhenRoomRepliesAreOff() {
        val plan = listener.incomingHandlingPlan(
            roomConfig = AutoReplyJson.defaultConfig("테스트방").copy(captureEnabled = true, replyEnabled = false),
            aiReplyEnabled = true
        )

        assertTrue(plan.shouldCapture)
        assertFalse(plan.shouldAttemptReply)
    }

    @Test
    fun incomingHandlingPlan_stopsEverythingWhenRoomIsNotConfigured() {
        val plan = listener.incomingHandlingPlan(
            roomConfig = null,
            aiReplyEnabled = true
        )

        assertTrue(plan.shouldCapture)
        assertFalse(plan.shouldAttemptReply)
    }

    @Test
    fun incomingHandlingPlan_canSkipCapture_and_still_attempt_reply() {
        val plan = listener.incomingHandlingPlan(
            roomConfig = AutoReplyJson.defaultConfig("테스트방").copy(captureEnabled = false, replyEnabled = true),
            aiReplyEnabled = true
        )

        assertFalse(plan.shouldCapture)
        assertTrue(plan.shouldAttemptReply)
    }
}
