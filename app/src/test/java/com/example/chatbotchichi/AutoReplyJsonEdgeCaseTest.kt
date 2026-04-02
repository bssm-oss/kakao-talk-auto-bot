package com.example.kakaotalkautobot

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoReplyJsonEdgeCaseTest {
    @Test
    fun parse_usesFallbacks_whenNestedSectionsAreMissing() {
        val parsed = AutoReplyJson.parse(
            """
            {
              "name": "프로젝트방"
            }
            """.trimIndent(),
            "fallback"
        )

        assertEquals("프로젝트방", parsed.name)
        assertEquals("프로젝트방", parsed.roomPattern)
        assertEquals("provider", parsed.replyMode)
        assertEquals("always", parsed.trigger.mode)
        assertEquals("openai", parsed.provider.type)
        assertEquals("api_key", parsed.provider.authMode)
    }

    @Test
    fun toJson_excludesImportHistory_whenNotRequested() {
        val config = AutoReplyJson.defaultConfig("프로젝트방").copy(importHistory = "이전 CSV")

        val json = JSONObject(AutoReplyJson.toJson(config, includeImportHistory = false))

        assertFalse(json.has("importHistory"))
    }

    @Test
    fun toJson_includesImportHistory_whenRequested() {
        val config = AutoReplyJson.defaultConfig("프로젝트방").copy(importHistory = "이전 CSV")

        val json = JSONObject(AutoReplyJson.toJson(config, includeImportHistory = true))

        assertTrue(json.has("importHistory"))
        assertEquals("이전 CSV", json.getString("importHistory"))
    }
}
