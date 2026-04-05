package com.example.kakaotalkautobot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderClientTest {
    @Test
    fun generate_usesRoomMemory_withoutApiKeyDependency() {
        val config = AutoReplyJson.defaultConfig("테스트방").copy(
            roomMemory = "회의 장소: 2층 회의실",
            provider = ProviderConfig(type = "openai", apiKey = "", authMode = "external"),
            trigger = TriggerConfig(mode = "always")
        )

        val reply = AiProviderClient.generate(
            config = config,
            room = "테스트방",
            sender = "민수",
            message = "회의 장소 어디야?",
            history = emptyList()
        )

        assertTrue(reply.reply.orEmpty().contains("2층 회의실"))
        assertNull(reply.failureReason)
    }

    @Test
    fun generate_usesRecentHistory_forGroundedReply() {
        val config = AutoReplyJson.defaultConfig("테스트방").copy(
            trigger = TriggerConfig(mode = "always")
        )

        val reply = AiProviderClient.generate(
            config = config,
            room = "테스트방",
            sender = "민수",
            message = "회의 몇 시 시작이야?",
            history = listOf(
                RoomHistoryMessage("지수", "회의는 오늘 3시에 시작해요", true, 1L),
                RoomHistoryMessage("AI", "네, 일정 확인할게요.", false, 2L)
            )
        )

        assertTrue(reply.reply.orEmpty().contains("3시"))
    }

    @Test
    fun generate_usesImportedCsvContext_afterSkippingHeaderLine() {
        val config = AutoReplyJson.defaultConfig("테스트방").copy(
            roomMemory = """
                [CSV 가져오기: history.csv]
                회의 장소는 강당입니다
            """.trimIndent(),
            trigger = TriggerConfig(mode = "always")
        )

        val reply = AiProviderClient.generate(
            config = config,
            room = "테스트방",
            sender = "민수",
            message = "회의 장소가 어디였지?",
            history = emptyList()
        )

        assertTrue(reply.reply.orEmpty().contains("강당"))
    }

    @Test
    fun generate_abstainsInAiJudge_whenConfidenceIsLow() {
        val config = AutoReplyJson.defaultConfig("테스트방").copy(
            trigger = TriggerConfig(mode = "ai_judge")
        )

        val reply = AiProviderClient.generate(
            config = config,
            room = "테스트방",
            sender = "민수",
            message = "ㅋㅋ",
            history = emptyList()
        )

        assertNull(reply.reply)
        assertEquals("로컬 판단 결과 이번 메시지는 개입하지 않았습니다.", reply.skippedReason)
    }

    @Test
    fun generate_returnsConservativeFallback_whenAlwaysModeHasNoContext() {
        val config = AutoReplyJson.defaultConfig("테스트방").copy(
            trigger = TriggerConfig(mode = "always")
        )

        val reply = AiProviderClient.generate(
            config = config,
            room = "테스트방",
            sender = "민수",
            message = "이건 어떻게 봐야 해",
            history = emptyList()
        )

        assertEquals("지금은 확실하지 않아요. 확인 후 답할게요.", reply.reply)
    }
}
