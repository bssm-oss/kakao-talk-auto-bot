package com.example.kakaotalkautobot

import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyStatsStoreTest {
    @Test
    fun summary_prefersFailureReasonWhenFailureExists() {
        val summary = ReplyStatsStore.Snapshot(
            incoming = 4,
            sent = 2,
            skipped = 1,
            failed = 1,
            failureReasons = mapOf("no session" to 1),
            skipReasons = mapOf("의미 없는 짧은 메시지입니다." to 1)
        ).summary()

        assertTrue(summary.contains("최다 실패: no session 1회"))
    }

    @Test
    fun summary_showsSkipReasonWhenThereIsNoFailure() {
        val summary = ReplyStatsStore.Snapshot(
            incoming = 3,
            sent = 1,
            skipped = 2,
            failed = 0,
            failureReasons = emptyMap(),
            skipReasons = mapOf("의미 없는 짧은 메시지입니다." to 2)
        ).summary()

        assertTrue(summary.contains("최다 스킵: 의미 없는 짧은 메시지입니다. 2회"))
    }
}
