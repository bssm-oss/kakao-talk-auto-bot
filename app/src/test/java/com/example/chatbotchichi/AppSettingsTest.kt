package com.example.kakaotalkautobot

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {
    @Test
    fun normalizeProviderType_maps_legacy_openai_to_local_llm() {
        assertEquals("llm", AppSettings.normalizeProviderType("openai"))
    }

    @Test
    fun normalizeProviderType_keeps_local_variants_on_llm() {
        assertEquals("llm", AppSettings.normalizeProviderType("local"))
        assertEquals("llm", AppSettings.normalizeProviderType("local-litertlm"))
        assertEquals("llm", AppSettings.normalizeProviderType("llm"))
    }
}
