package com.example.kakaotalkautobot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmEngineTest {
    @Test
    fun trimToMaxOutputTokens_capsLongResponsesWithTokenBudget() {
        val raw = "가".repeat(120)

        val trimmed = ReplyOutputBudget.trimToMaxOutputTokens(raw, maxTokens = 8)

        assertEquals(48, trimmed.length)
    }

    @Test
    fun trimToMaxOutputTokens_keepsShortResponses() {
        val raw = "알겠어 조금 있다가 볼게"

        val trimmed = ReplyOutputBudget.trimToMaxOutputTokens(raw, maxTokens = 12)

        assertEquals(raw, trimmed)
        assertTrue(trimmed.length < 72)
    }
}
