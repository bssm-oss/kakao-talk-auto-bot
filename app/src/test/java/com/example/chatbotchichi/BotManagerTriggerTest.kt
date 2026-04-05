package com.example.kakaotalkautobot

import org.junit.Assert.assertFalse
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
}
