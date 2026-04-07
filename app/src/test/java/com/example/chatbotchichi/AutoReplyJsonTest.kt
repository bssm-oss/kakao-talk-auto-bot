package com.example.kakaotalkautobot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoReplyJsonTest {
    @Test
    fun defaultConfig_uses_llm_provider() {
        val config = AutoReplyJson.defaultConfig("테스트방")

        assertEquals("테스트방", config.name)
        assertEquals("ai_judge", config.trigger.mode)
        assertEquals("provider", config.replyMode)
        assertEquals("llm", config.provider.type)
        assertEquals("local-gguf", config.provider.model)
    }

    @Test
    fun parseAndSerialize_preservesRoomSpecificSettings() {
        val original = AutoReplyConfig(
            name = "회의방",
            roomPattern = "프로젝트 회의방",
            replyMode = "canned",
            roomMemory = "나를 팀장처럼 말하게 해라",
            allowedSenders = listOf("민수", "지우"),
            blockedSenders = listOf("광고봇"),
            cannedReplies = listOf("확인했습니다.", "잠시 후 답할게요."),
            trigger = TriggerConfig(mode = "keyword", value = "@봇, 도와줘"),
            provider = ProviderConfig(type = "llm", model = "local-gguf")
        )

        val json = AutoReplyJson.toJson(original, includeImportHistory = true)
        val reparsed = AutoReplyJson.parse(json, "fallback")

        assertEquals(original.name, reparsed.name)
        assertEquals(original.roomPattern, reparsed.roomPattern)
        assertEquals(original.replyMode, reparsed.replyMode)
        assertEquals(original.allowedSenders, reparsed.allowedSenders)
        assertEquals(original.blockedSenders, reparsed.blockedSenders)
        assertEquals(original.cannedReplies, reparsed.cannedReplies)
        assertEquals(original.trigger.mode, reparsed.trigger.mode)
        assertEquals(original.trigger.value, reparsed.trigger.value)
        assertEquals(original.provider.type, reparsed.provider.type)
        assertEquals(original.provider.model, reparsed.provider.model)
    }
}
