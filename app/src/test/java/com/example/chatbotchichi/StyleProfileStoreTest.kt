package com.example.kakaotalkautobot

import org.junit.Assert.assertTrue
import org.junit.Test

class StyleProfileStoreTest {
    @Test
    fun composePromptStyleGuide_preserves_manual_priority_order() {
        val guide = StyleProfileStore.composePromptStyleGuide(
            StyleProfileStore.StyleGuideParts(
                personaExamples = "아무것도 없긴해",
                manualRoomStyle = "친구방에서는 가볍게 반말",
                learnedUserStyle = "내 발화 기준: 반말과 캐주얼한 표현을 자주 씀",
                learnedRoomStyle = "방 전체 말투 기준: 친한 친구방처럼 가벼움",
                learnedUserConfidenceLabel = "낮음",
                learnedRoomConfidenceLabel = "높음",
                learnedUserConfidence = 24,
                learnedRoomConfidence = 92,
                learnedUserSampleCount = 2,
                learnedRoomSampleCount = 12
            )
        )

        assertTrue(guide.contains("우선순위"))
        assertTrue(guide.contains("신뢰도가 낮으면"))
        assertTrue(guide.contains("보조 힌트로 표시된 학습 말투"))
        assertTrue(guide.contains("충돌하면 반드시 버린다"))
        assertTrue(guide.contains("학습된 사용자 말투(신뢰도 낮음, 24점, 샘플 2개, 보조 힌트, 충돌 시 무시)"))
        assertTrue(guide.contains("학습된 방 말투(신뢰도 높음, 92점, 샘플 12개)"))
        assertTrue(guide.indexOf("사용자 직접 예시") < guide.indexOf("수동 방 스타일"))
        assertTrue(guide.indexOf("수동 방 스타일") < guide.indexOf("학습된 사용자 말투"))
        assertTrue(guide.indexOf("학습된 사용자 말투") < guide.indexOf("학습된 방 말투"))
    }

    @Test
    fun composePromptStyleGuide_marksLowConfidenceAsSecondaryHint() {
        val guide = StyleProfileStore.composePromptStyleGuide(
            StyleProfileStore.StyleGuideParts(
                personaExamples = "아무것도 없긴해",
                manualRoomStyle = "친한 친구방은 반말 우선",
                learnedRoomStyle = "방 전체 말투 기준: 학교/팀방처럼 존댓말과 격식이 중심",
                learnedRoomConfidenceLabel = "낮음",
                learnedRoomConfidence = 24,
                learnedRoomSampleCount = 1
            )
        )

        assertTrue(guide.contains("학습 말투 신뢰도가 낮으면 확정 규칙처럼 따르지 말고"))
        assertTrue(guide.contains("학습된 방 말투(신뢰도 낮음, 24점, 샘플 1개, 보조 힌트, 충돌 시 무시)"))
        assertTrue(guide.indexOf("수동 방 스타일") < guide.indexOf("학습된 방 말투"))
    }

    @Test
    fun composePromptStyleGuide_doesNotMarkManualOverrideAsDiscardableHint() {
        val guide = StyleProfileStore.composePromptStyleGuide(
            StyleProfileStore.StyleGuideParts(
                personaExamples = "별일없습니다!",
                manualRoomStyle = "팀방에서는 짧게 존댓말",
                learnedUserStyle = "팀방에서는 별일없습니다!처럼 짧게",
                learnedUserConfidenceLabel = "수동 수정",
                learnedUserConfidence = 24,
                learnedUserSampleCount = 1
            )
        )

        assertTrue(guide.contains("학습된 사용자 말투(신뢰도 수동 수정, 24점, 샘플 1개)"))
        assertTrue(!guide.contains("학습된 사용자 말투(신뢰도 수동 수정, 24점, 샘플 1개, 보조 힌트"))
    }

    @Test
    fun buildUserStyleFromMessages_uses_imported_own_messages() {
        val style = StyleProfileStore.buildUserStyleFromMessages(
            displayName = "동건",
            history = emptyList(),
            importedText = """
                민수: 오늘 뭐 있어?
                동건: 아무것도 없긴해
                지우: 오키
                동건: 이따 봐
            """.trimIndent()
        )

        assertTrue(style.contains("내 발화 기준"))
        assertTrue(style.contains("아무것도 없긴해"))
        assertTrue(style.contains("이따 봐"))
    }

    @Test
    fun buildRoomStyleFromMessages_distinguishes_friend_and_team_tones() {
        val friendStyle = StyleProfileStore.buildRoomStyleFromMessages(
            listOf(
                RoomHistoryMessage("민수", "ㅋㅋ 오늘 뭐함", true, 1L),
                RoomHistoryMessage("지우", "아무것도 없긴해", true, 2L),
                RoomHistoryMessage("민수", "ㅇㅋ 이따 봐", true, 3L)
            )
        )
        val teamStyle = StyleProfileStore.buildRoomStyleFromMessages(
            listOf(
                RoomHistoryMessage("팀장", "오늘 공유할 내용 있나요?", true, 1L),
                RoomHistoryMessage("나", "별일없습니다!", false, 2L),
                RoomHistoryMessage("팀장", "확인했습니다", true, 3L)
            )
        )

        assertTrue(friendStyle.contains("친한 친구방"))
        assertTrue(teamStyle.contains("학교/팀방"))
    }

    @Test
    fun styleProfiles_includeConfidenceFromSampleCount() {
        val low = StyleProfileStore.buildRoomStyleProfile(
            listOf(RoomHistoryMessage("민수", "ㅇㅋ", true, 1L))
        )
        val high = StyleProfileStore.buildRoomStyleProfile(
            (1..12).map { index ->
                RoomHistoryMessage("팀원$index", "확인했습니다", true, index.toLong())
            }
        )

        assertTrue(low.confidence < high.confidence)
        assertTrue(high.confidence >= 80)
        assertTrue(high.sampleCount == 12)
    }

    @Test
    fun learnedStyleState_confidenceSummary_marksLowConfidenceAsSecondaryHint() {
        val state = StyleProfileStore.LearnedStyleState(
            enabled = true,
            override = "",
            generated = "방 전체 말투 기준: 친한 친구방처럼 가볍고 짧은 반응이 중심",
            confidence = 24,
            sampleCount = 1
        )

        assertTrue(state.confidenceSummary.contains("낮음"))
        assertTrue(state.confidenceSummary.contains("24점"))
        assertTrue(state.confidenceSummary.contains("샘플 1개"))
        assertTrue(state.confidenceSummary.contains("보조 힌트"))
    }

    @Test
    fun learnedStyleState_confidenceSummary_marksManualOverrideAsManual() {
        val state = StyleProfileStore.LearnedStyleState(
            enabled = true,
            override = "친구방에서는 아무것도 없긴해처럼 짧게 반말",
            generated = "방 전체 말투 기준: 학교/팀방처럼 존댓말과 격식이 중심",
            confidence = 24,
            sampleCount = 1
        )

        assertTrue(state.confidenceSummary.contains("수동 수정"))
        assertTrue(!state.confidenceSummary.contains("보조 힌트"))
    }

    @Test
    fun learnedStyleState_resetOverrideActionReflectsManualOverrideOnly() {
        val automaticOnly = StyleProfileStore.LearnedStyleState(
            enabled = true,
            override = "",
            generated = "방 전체 말투 기준: 친한 친구방처럼 가볍고 짧은 반응이 중심",
            confidence = 62,
            sampleCount = 5
        )
        val manualOverride = automaticOnly.copy(
            override = "친구방에서는 아무것도 없긴해처럼 짧게 반말"
        )

        assertTrue(!automaticOnly.hasManualOverride)
        assertTrue(automaticOnly.resetOverrideButtonLabel == "수정 없음")
        assertTrue(manualOverride.hasManualOverride)
        assertTrue(manualOverride.resetOverrideButtonLabel == "수정 초기화")
    }

    @Test
    fun learnedStyleState_resetGuidance_explainsLowConfidenceRelearningPath() {
        val state = StyleProfileStore.LearnedStyleState(
            enabled = true,
            override = "",
            generated = "방 전체 말투 기준: 친한 친구방처럼 가볍고 짧은 반응이 중심",
            confidence = 24,
            sampleCount = 1
        )

        assertTrue(state.resetGuidance.contains("신뢰도가 낮습니다"))
        assertTrue(state.resetGuidance.contains("학습 원본 삭제"))
        assertTrue(state.resetGuidance.contains("새 대화"))
    }

    @Test
    fun learnedStyleState_resetGuidance_distinguishesManualOverrideAndDisabledState() {
        val manual = StyleProfileStore.LearnedStyleState(
            enabled = true,
            override = "팀방에서는 별일없습니다!처럼 짧게",
            generated = "방 전체 말투 기준: 친한 친구방처럼 가벼움",
            confidence = 92,
            sampleCount = 12
        )
        val disabled = StyleProfileStore.LearnedStyleState(
            enabled = false,
            override = "",
            generated = "방 전체 말투 기준: 학교/팀방처럼 존댓말과 격식이 중심",
            confidence = 78,
            sampleCount = 8
        )

        assertTrue(manual.resetGuidance.contains("수동 수정값이 우선"))
        assertTrue(manual.resetGuidance.contains("수정 초기화"))
        assertTrue(disabled.resetGuidance.contains("꺼져 있어"))
        assertTrue(disabled.resetGuidance.contains("반영되지 않습니다"))
    }

    @Test
    fun learnedStylePreviewText_includesResetGuidanceForUserStyle() {
        val state = StyleProfileStore.LearnedStyleState(
            enabled = true,
            override = "",
            generated = "내 발화 기준: 반말과 캐주얼한 표현을 자주 씀",
            confidence = 24,
            sampleCount = 1
        )

        val preview = StyleProfileStore.learnedStylePreviewText(
            subject = "내 말투",
            state = state,
            emptyMessage = "자동 추출된 내 말투가 아직 없습니다."
        )

        assertTrue(preview.contains("자동 추출"))
        assertTrue(preview.contains("낮음"))
        assertTrue(preview.contains("24점"))
        assertTrue(preview.contains("샘플 1개"))
        assertTrue(preview.contains("보조 힌트"))
        assertTrue(preview.contains("학습 원본 삭제"))
        assertTrue(preview.contains("새 대화"))
    }

    @Test
    fun learnedStylePreviewText_showsManualOverrideEvenWithoutGeneratedStyle() {
        val state = StyleProfileStore.LearnedStyleState(
            enabled = true,
            override = "친구방에서는 아무것도 없긴해처럼 짧게 반말",
            generated = "",
            confidence = 0,
            sampleCount = 0
        )

        val preview = StyleProfileStore.learnedStylePreviewText(
            subject = "내 말투",
            state = state,
            emptyMessage = "자동 추출된 내 말투가 아직 없습니다."
        )

        assertTrue(preview.contains("수동 수정 적용 중"))
        assertTrue(preview.contains("친구방에서는 아무것도 없긴해처럼 짧게 반말"))
        assertTrue(preview.contains("수동 수정값이 우선"))
        assertTrue(!preview.contains("자동 추출된 내 말투가 아직 없습니다."))
    }

    @Test
    fun learnedStylePreviewText_prefersManualOverrideOverGeneratedStyle() {
        val state = StyleProfileStore.LearnedStyleState(
            enabled = true,
            override = "팀방에서는 별일없습니다!처럼 짧게",
            generated = "방 전체 말투 기준: 친한 친구방처럼 가벼움",
            confidence = 92,
            sampleCount = 12
        )

        val preview = StyleProfileStore.learnedStylePreviewText(
            subject = "방 말투",
            state = state,
            emptyMessage = "자동 추출된 방 말투가 아직 없습니다."
        )

        assertTrue(preview.contains("수동 수정 적용 중"))
        assertTrue(preview.contains("팀방에서는 별일없습니다!처럼 짧게"))
        assertTrue(!preview.contains("방 전체 말투 기준: 친한 친구방처럼 가벼움"))
    }

    @Test
    fun learnedStylePreviewText_explainsDisabledStateWhenGeneratedStyleExists() {
        val state = StyleProfileStore.LearnedStyleState(
            enabled = false,
            override = "",
            generated = "방 전체 말투 기준: 학교/팀방처럼 존댓말과 격식이 중심",
            confidence = 78,
            sampleCount = 8
        )

        val preview = StyleProfileStore.learnedStylePreviewText(
            subject = "방 말투",
            state = state,
            emptyMessage = "자동 추출된 방 말투가 아직 없습니다."
        )

        assertTrue(preview.contains("꺼짐"))
        assertTrue(preview.contains("학습된 방 말투가 꺼져 있어"))
        assertTrue(preview.contains("반영되지 않습니다"))
    }

    @Test
    fun learnedStylePreviewText_warnsWhenManualRoomStyleConflictsWithGeneratedStyle() {
        val state = StyleProfileStore.LearnedStyleState(
            enabled = true,
            override = "",
            generated = "방 전체 말투 기준: 학교/팀방처럼 존댓말과 격식이 중심",
            confidence = 78,
            sampleCount = 8
        )

        val preview = StyleProfileStore.learnedStylePreviewText(
            subject = "방 말투",
            state = state,
            emptyMessage = "자동 추출된 방 말투가 아직 없습니다.",
            manualStyle = "친한 친구방. 가볍게 반말. 예시: 아무것도 없긴해"
        )

        assertTrue(preview.contains("수동 방 스타일과 자동 학습값이 충돌합니다"))
        assertTrue(preview.contains("수동 방 스타일이 우선"))
        assertTrue(preview.contains("학습 원본 삭제"))
    }

    @Test
    fun learnedStyleConflictWarning_doesNotWarnWhenManualOverrideOrDisabledStateApplies() {
        val automaticConflict = StyleProfileStore.LearnedStyleState(
            enabled = true,
            override = "",
            generated = "방 전체 말투 기준: 학교/팀방처럼 존댓말과 격식이 중심",
            confidence = 78,
            sampleCount = 8
        )
        val manualOverride = automaticConflict.copy(override = "친구방에서는 아무것도 없긴해처럼 짧게 반말")
        val disabled = automaticConflict.copy(enabled = false)

        assertTrue(
            StyleProfileStore.learnedStyleConflictWarning(
                manualStyle = "친한 친구방. 가볍게 반말",
                state = automaticConflict
            ).isNotBlank()
        )
        assertTrue(
            StyleProfileStore.learnedStyleConflictWarning(
                manualStyle = "친한 친구방. 가볍게 반말",
                state = manualOverride
            ).isBlank()
        )
        assertTrue(
            StyleProfileStore.learnedStyleConflictWarning(
                manualStyle = "친한 친구방. 가볍게 반말",
                state = disabled
            ).isBlank()
        )
    }
}
