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
                learnedRoomStyle = "방 전체 말투 기준: 친한 친구방처럼 가벼움"
            )
        )

        assertTrue(guide.contains("우선순위"))
        assertTrue(guide.indexOf("사용자 직접 예시") < guide.indexOf("수동 방 스타일"))
        assertTrue(guide.indexOf("수동 방 스타일") < guide.indexOf("학습된 사용자 말투"))
        assertTrue(guide.indexOf("학습된 사용자 말투") < guide.indexOf("학습된 방 말투"))
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
}
