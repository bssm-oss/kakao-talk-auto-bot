package com.example.kakaotalkautobot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun summary_surfacesSpecificRemoteInputFailureReason() {
        val summary = ReplyStatsStore.Snapshot(
            incoming = 5,
            sent = 1,
            skipped = 0,
            failed = 3,
            failureReasons = mapOf(
                SessionReplier.REASON_NO_SESSION to 1,
                SessionReplier.REASON_PENDING_INTENT_SEND_FAILED to 2
            ),
            skipReasons = emptyMap()
        ).summary()

        assertTrue(summary.contains("최다 실패: pendingIntent send failed 2회"))
        assertTrue(summary.contains("전송성공 25%"))
    }

    @Test
    fun normalizeReason_collapsesKnownSendFailureVariants() {
        assertEquals(
            SessionReplier.REASON_NO_SESSION,
            ReplyStatsStore.normalizeReason("AI 답장은 생성됐지만 카카오톡 전송에 실패했습니다. (reason=no session)")
        )
        assertEquals(
            SessionReplier.REASON_PENDING_INTENT_SEND_FAILED,
            ReplyStatsStore.normalizeReason("send_failed_after_generation:pendingIntent send failed")
        )
        assertEquals(
            SessionReplier.REASON_NO_REMOTE_INPUT,
            ReplyStatsStore.normalizeReason("no remoteInput")
        )
    }

    @Test
    fun normalizeReason_collapsesSkipAndModelReasons() {
        assertEquals("reply off", ReplyStatsStore.normalizeReason("AI 답장 OFF · 메시지 수집 중"))
        assertEquals("low signal", ReplyStatsStore.normalizeReason("의미 없는 짧은 메시지입니다."))
        assertEquals("model not loaded", ReplyStatsStore.normalizeReason("LLM 모델이 로드되지 않았습니다. 모델 다운로드를 기다려주세요."))
    }

    @Test
    fun normalizeReason_collapsesAiGenerationAndQualityFailures() {
        assertEquals(
            "ai quality rejected",
            ReplyStatsStore.normalizeReason("AI가 전송 가능한 품질의 응답을 만들지 못했습니다. (candidates=5)")
        )
        assertEquals(
            "ai generation exception",
            ReplyStatsStore.normalizeReason("AI 응답 생성 중 오류: LiteRtLmJniException")
        )
        assertEquals(
            "canned reply empty",
            ReplyStatsStore.normalizeReason("고정 답장 템플릿이 비어 있습니다.")
        )
    }

    @Test
    fun normalizeReason_redactsSensitiveTokensInUnknownReasons() {
        val reason = ReplyStatsStore.normalizeReason(
            "vendor_error room=비밀방 email=test@example.com phone=010-1234-5678 url=https://example.com/fail"
        )

        assertFalse(reason.contains("test@example.com"))
        assertFalse(reason.contains("010-1234-5678"))
        assertFalse(reason.contains("https://example.com/fail"))
        assertTrue(reason.contains("<이메일 숨김>"))
        assertTrue(reason.contains("<전화번호 숨김>"))
        assertTrue(reason.contains("<URL 숨김>"))
    }

    @Test
    fun detailSummary_listsTopFailureAndSkipReasons() {
        val detail = ReplyStatsStore.Snapshot(
            incoming = 9,
            sent = 2,
            skipped = 3,
            failed = 4,
            failureReasons = mapOf(
                SessionReplier.REASON_NO_SESSION to 1,
                SessionReplier.REASON_PENDING_INTENT_SEND_FAILED to 3
            ),
            skipReasons = mapOf(
                "reply off" to 2,
                "low signal" to 1
            )
        ).detailSummary()

        assertTrue(detail.contains("실패: pendingIntent send failed 3회"))
        assertTrue(detail.contains("no session 1회"))
        assertTrue(detail.contains("스킵: reply off 2회"))
        assertTrue(detail.contains("low signal 1회"))
    }

    @Test
    fun detailSummary_surfacesRecentFailureAndSkipReasons() {
        val detail = ReplyStatsStore.Snapshot(
            incoming = 6,
            sent = 2,
            skipped = 1,
            failed = 1,
            failureReasons = mapOf(SessionReplier.REASON_NO_REMOTE_INPUT to 1),
            skipReasons = mapOf("model not loaded" to 1),
            lastSentAtMillis = 3000L,
            lastFailureReason = SessionReplier.REASON_NO_REMOTE_INPUT,
            lastFailureAtMillis = 1000L,
            lastSkipReason = "model not loaded",
            lastSkipAtMillis = 2000L
        ).detailSummary()

        assertTrue(detail.contains("최근 실패: no remoteInput"))
        assertTrue(detail.contains("최근 스킵: model not loaded"))
        assertTrue(detail.contains("최근 이벤트:"))
        assertTrue(detail.contains("전송"))
        assertTrue(detail.contains("실패"))
        assertTrue(detail.contains("스킵"))
    }

    @Test
    fun formatAge_usesHumanReadableBuckets() {
        val now = 200_000_000L

        assertEquals("5초 전", ReplyStatsStore.formatAge(now - 5_000L, now))
        assertEquals("2분 전", ReplyStatsStore.formatAge(now - 120_000L, now))
        assertEquals("3시간 전", ReplyStatsStore.formatAge(now - 10_800_000L, now))
        assertEquals("2일 전", ReplyStatsStore.formatAge(now - 172_800_000L, now))
        assertEquals("없음", ReplyStatsStore.formatAge(0L, now))
    }

    @Test
    fun summaryShowsNoDeliveryAttemptsWhenOnlyIncomingExists() {
        val summary = ReplyStatsStore.Snapshot(
            incoming = 2,
            sent = 0,
            skipped = 0,
            failed = 0,
            failureReasons = emptyMap(),
            skipReasons = emptyMap()
        ).summary()

        assertTrue(summary.contains("전송시도 없음"))
    }
}
