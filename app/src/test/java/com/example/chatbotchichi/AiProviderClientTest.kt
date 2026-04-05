package com.example.kakaotalkautobot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiProviderClientTest {
    @Test
    fun generate_returnsNull_whenAuthModeIsNotApiKey() {
        val config = AutoReplyJson.defaultConfig("테스트방").copy(
            provider = ProviderConfig(type = "openai", apiKey = "secret", authMode = "external")
        )

        val reply = AiProviderClient.generate(
            config = config,
            room = "테스트방",
            sender = "민수",
            message = "안녕?",
            history = emptyList()
        )

        assertNull(reply.reply)
        assertEquals("API Key 보관 방식이 '외부에서 관리'로 되어 있어 자동 응답을 생성할 수 없습니다.", reply.failureReason)
    }

    @Test
    fun generate_returnsNull_whenApiKeyIsBlank() {
        val config = AutoReplyJson.defaultConfig("테스트방").copy(
            provider = ProviderConfig(type = "openai", apiKey = "", authMode = "api_key")
        )

        val reply = AiProviderClient.generate(
            config = config,
            room = "테스트방",
            sender = "민수",
            message = "안녕?",
            history = emptyList()
        )

        assertNull(reply.reply)
        assertEquals("openai API Key가 비어 있습니다.", reply.failureReason)
    }

    @Test
    fun normalizeGeneratedReply_stripsJsonFence_whenAiJudgeMode() {
        val config = AutoReplyJson.defaultConfig("테스트방").copy(
            trigger = TriggerConfig(mode = "ai_judge")
        )

        val reply = AiProviderClient.normalizeGeneratedReply(
            config,
            """
            ```json
            {"shouldReply":true,"reply":"지금 바로 확인할게요."}
            ```
            """.trimIndent()
        )

        assertEquals("지금 바로 확인할게요.", reply.reply)
    }

    @Test
    fun normalizeGeneratedReply_returnsPlainText_whenNotJudgeMode() {
        val config = AutoReplyJson.defaultConfig("테스트방").copy(
            trigger = TriggerConfig(mode = "always")
        )

        val reply = AiProviderClient.normalizeGeneratedReply(
            config,
            "네, 조금 뒤에 답장할게요."
        )

        assertEquals("네, 조금 뒤에 답장할게요.", reply.reply)
    }
}
