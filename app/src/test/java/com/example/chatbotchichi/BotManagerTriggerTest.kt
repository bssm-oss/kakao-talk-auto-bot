package com.example.kakaotalkautobot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BotManagerTriggerTest {
    @Test
    fun mentionTrigger_matchesWhenValueContainsAtPrefix() {
        val config = AutoReplyJson.defaultConfig("테스트방").copy(
            trigger = TriggerConfig(mode = "mention", value = "@허동운")
        )

        assertTrue(BotManager.triggerMatches(config, "@허동운 이거 확인해줘", isGroupChat = true))
        assertTrue(BotManager.triggerMatches(config, "허동운아 이거 확인해줘", isGroupChat = true))
        assertFalse(BotManager.triggerMatches(config, "다른 사람 태그", isGroupChat = true))
    }

    @Test
    fun keywordTrigger_matchesCommaSeparatedValues() {
        val config = AutoReplyJson.defaultConfig("테스트방").copy(
            trigger = TriggerConfig(mode = "keyword", value = "secrets,@허동운")
        )

        assertTrue(BotManager.triggerMatches(config, "진짜 secrets만 넣으면 돼", isGroupChat = true))
        assertTrue(BotManager.triggerMatches(config, "@허동운 답해봐", isGroupChat = true))
        assertFalse(BotManager.triggerMatches(config, "아무 관련 없는 메시지", isGroupChat = true))
    }

    @Test
    fun questionTrigger_matches_question_and_request_phrases() {
        val config = AutoReplyJson.defaultConfig("테스트방").copy(
            trigger = TriggerConfig(mode = "question", value = "")
        )

        assertTrue(BotManager.triggerMatches(config, "이거 언제 끝나?", isGroupChat = true))
        assertTrue(BotManager.triggerMatches(config, "자료 좀 알려줘", isGroupChat = true))
        assertFalse(BotManager.triggerMatches(config, "오늘 점심 먹었어", isGroupChat = true))
    }

    @Test
    fun directTrigger_matches_only_in_direct_chat() {
        val config = AutoReplyJson.defaultConfig("테스트방").copy(
            trigger = TriggerConfig(mode = "direct", value = "")
        )

        assertTrue(BotManager.triggerMatches(config, "바로 답해줘", isGroupChat = false))
        assertFalse(BotManager.triggerMatches(config, "바로 답해줘", isGroupChat = true))
    }

    @Test
    fun roomMatches_escapesRegexCharactersAroundWildcard() {
        assertTrue(BotManager.roomMatches("팀방+[A]*", "팀방+[A]공지"))
        assertFalse(BotManager.roomMatches("팀방+[A]*", "팀방X공지"))
    }

    @Test
    fun mergeRoomMemory_prefersRealRoomMemoryAndDropsDefaultPlaceholder() {
        val merged = BotManager.mergeRoomMemory(
            baseMemory = AutoReplyJson.defaultConfig("기본 자동응답").roomMemory,
            roomMemory = "친구방에서는 가볍게 반말로 답장"
        )

        assertEquals("친구방에서는 가볍게 반말로 답장", merged)
    }

    @Test
    fun mergeRoomMemory_keepsManualBaseAndRoomMemory() {
        val merged = BotManager.mergeRoomMemory(
            baseMemory = "기본 금지어: 욕설 금지",
            roomMemory = "팀방에서는 존댓말"
        )

        assertEquals("기본 금지어: 욕설 금지\n\n팀방에서는 존댓말", merged)
    }
}
