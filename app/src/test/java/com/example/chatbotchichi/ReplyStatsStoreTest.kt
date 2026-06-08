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
        assertEquals("global reply off", ReplyStatsStore.normalizeReason("AI 답장 OFF · 메시지 수집 중"))
        assertEquals("room reply off", ReplyStatsStore.normalizeReason("방별 답장이 OFF 상태입니다."))
        assertEquals("no room config", ReplyStatsStore.normalizeReason("응답 설정이 없는 방입니다."))
        assertEquals("low signal", ReplyStatsStore.normalizeReason("의미 없는 짧은 메시지입니다."))
        assertEquals("model not loaded", ReplyStatsStore.normalizeReason("LLM 모델이 로드되지 않았습니다. 모델 다운로드를 기다려주세요."))
    }

    @Test
    fun normalizeReason_preservesAlreadyStandardSkipReasons() {
        assertEquals("global reply off", ReplyStatsStore.normalizeReason("global reply off"))
        assertEquals("room reply off", ReplyStatsStore.normalizeReason("room reply off"))
        assertEquals("condition not met", ReplyStatsStore.normalizeReason("condition not met"))
        assertEquals("blank message", ReplyStatsStore.normalizeReason("blank message"))
        assertEquals(AutoReplyEngine.REASON_DUPLICATE_NOTIFICATION, ReplyStatsStore.normalizeReason("duplicate notification"))
        assertEquals(AutoReplyEngine.REASON_DUPLICATE_NOTIFICATION, ReplyStatsStore.normalizeReason("중복 알림이라 답장을 건너뜁니다."))
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
                "global reply off" to 2,
                "low signal" to 1
            )
        ).detailSummary()

        assertTrue(detail.contains("실패: pendingIntent send failed 3회"))
        assertTrue(detail.contains("no session 1회"))
        assertTrue(detail.contains("스킵: global reply off 2회"))
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
    fun detailSummary_addsActionHintForRecentRemoteInputFailure() {
        val detail = ReplyStatsStore.Snapshot(
            incoming = 6,
            sent = 2,
            skipped = 0,
            failed = 2,
            failureReasons = mapOf(
                SessionReplier.REASON_NO_SESSION to 1,
                SessionReplier.REASON_NO_REMOTE_INPUT to 1
            ),
            skipReasons = emptyMap(),
            lastFailureReason = SessionReplier.REASON_NO_REMOTE_INPUT,
            lastFailureAtMillis = 1000L
        ).detailSummary()

        assertTrue(detail.contains("최근 실패: no remoteInput"))
        assertTrue(detail.contains("no remoteInput: 카카오톡 알림 액션에 답장 RemoteInput 포함 여부"))
    }

    @Test
    fun detailSummary_includesActionHintsForBothFailuresAndSkips() {
        val detail = ReplyStatsStore.Snapshot(
            incoming = 10,
            sent = 1,
            skipped = 4,
            failed = 2,
            failureReasons = mapOf(SessionReplier.REASON_PENDING_INTENT_SEND_FAILED to 2),
            skipReasons = mapOf("global reply off" to 3, "low signal" to 1),
            lastFailureReason = SessionReplier.REASON_PENDING_INTENT_SEND_FAILED,
            lastSkipReason = "global reply off"
        ).detailSummary(limit = 3)

        assertTrue(detail.contains("pendingIntent send failed: PendingIntent 전송 예외와 카카오톡 알림 권한/상태"))
        assertTrue(detail.contains("global reply off: 상단 전체 AI 답장 스위치 상태"))
    }

    @Test
    fun detailSummary_usesTopFailureActionHintWhenRecentFailureIsMissing() {
        val detail = ReplyStatsStore.Snapshot(
            incoming = 5,
            sent = 1,
            skipped = 0,
            failed = 4,
            failureReasons = mapOf(
                SessionReplier.REASON_NO_SESSION to 3,
                SessionReplier.REASON_PENDING_INTENT_SEND_FAILED to 1
            ),
            skipReasons = emptyMap()
        ).detailSummary()

        assertTrue(detail.contains("실패: no session 3회"))
        assertTrue(detail.contains("no session: 카카오톡 알림 수신 후 세션 캐시 생성 여부"))
    }

    @Test
    fun detailSummary_addsActionHintForRecentSkipWhenThereIsNoFailure() {
        val detail = ReplyStatsStore.Snapshot(
            incoming = 5,
            sent = 0,
            skipped = 4,
            failed = 0,
            failureReasons = emptyMap(),
            skipReasons = mapOf(
                "global reply off" to 1,
                "low signal" to 3
            ),
            lastSkipReason = "low signal",
            lastSkipAtMillis = 1000L
        ).detailSummary()

        assertTrue(detail.contains("최근 스킵: low signal"))
        assertTrue(detail.contains("low signal: AI 판단 모드의 낮은 신호 스킵 기준과 최근 대화 맥락"))
    }

    @Test
    fun detailSummary_addsActionHintForDuplicateNotificationSkip() {
        val detail = ReplyStatsStore.Snapshot(
            incoming = 5,
            sent = 1,
            skipped = 2,
            failed = 0,
            failureReasons = emptyMap(),
            skipReasons = mapOf(AutoReplyEngine.REASON_DUPLICATE_NOTIFICATION to 2),
            lastSkipReason = AutoReplyEngine.REASON_DUPLICATE_NOTIFICATION,
            lastSkipAtMillis = 1000L
        ).detailSummary()

        assertTrue(detail.contains("최근 스킵: duplicate notification"))
        assertTrue(detail.contains("duplicate notification: 같은 방/발화자/메시지의 짧은 시간 내 알림 재게시 여부"))
    }

    @Test
    fun detailSummary_usesTopSkipActionHintWhenRecentSkipIsMissing() {
        val detail = ReplyStatsStore.Snapshot(
            incoming = 4,
            sent = 0,
            skipped = 4,
            failed = 0,
            failureReasons = emptyMap(),
            skipReasons = mapOf(
                "room reply off" to 3,
                "condition not met" to 1
            )
        ).detailSummary()

        assertTrue(detail.contains("스킵: room reply off 3회"))
        assertTrue(detail.contains("room reply off: 대상 방별 답장 활성화 스위치"))
    }

    @Test
    fun failureActionHint_coversModelAndAiFailureReasons() {
        assertEquals(
            "Gemma 모델 다운로드와 해시 검증 상태",
            ReplyStatsStore.failureActionHint("LLM 모델이 로드되지 않았습니다.")
        )
        assertEquals(
            "후보 lane 점수와 품질 게이트 실패 이유",
            ReplyStatsStore.failureActionHint("AI가 전송 가능한 품질의 응답을 만들지 못했습니다.")
        )
        assertEquals(
            "LiteRT-LM 생성 예외와 모델 런타임 상태",
            ReplyStatsStore.failureActionHint("AI 응답 생성 중 오류: LiteRtLmJniException")
        )
    }

    @Test
    fun actionHint_coversSkipReasons() {
        assertEquals(
            "상단 전체 AI 답장 스위치 상태",
            ReplyStatsStore.actionHint("AI 답장 OFF · 메시지 수집 중")
        )
        assertEquals(
            "방 트리거와 발화자 조건",
            ReplyStatsStore.actionHint("응답 조건을 충족하지 않았습니다.")
        )
        assertEquals(
            "알림 텍스트 파싱 결과",
            ReplyStatsStore.actionHint("빈 메시지에는 답장하지 않습니다.")
        )
        assertEquals(
            "같은 방/발화자/메시지의 짧은 시간 내 알림 재게시 여부",
            ReplyStatsStore.actionHint(AutoReplyEngine.REASON_DUPLICATE_NOTIFICATION)
        )
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
