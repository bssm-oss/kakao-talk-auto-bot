package com.example.kakaotalkautobot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LlmModelManagerTest {
    @Test
    fun validateModelFile_rejectsWrongShaEvenWhenSizeMatches() {
        val file = File.createTempFile("model", ".litertlm")
        try {
            file.writeText("abc")
            val source = LlmModelManager.ModelSource(
                name = "test",
                downloadUrl = "https://example.invalid/model",
                expectedSizeBytes = 3L,
                sha256 = "0000000000000000000000000000000000000000000000000000000000000000"
            )

            val validation = LlmModelManager.validateModelFile(file, source, verifyChecksum = true)

            assertFalse(validation.isUsable)
            assertTrue(validation.sizeMatchesExpected)
            assertTrue(validation.checksumVerified)
            assertFalse(validation.checksumMatchesExpected)
        } finally {
            file.delete()
            File(file.parentFile, "${file.name}.sha256").delete()
        }
    }

    @Test
    fun validateModelFile_acceptsMatchingShaAndWritesSidecar() {
        val file = File.createTempFile("model", ".litertlm")
        try {
            file.writeText("abc")
            val source = LlmModelManager.ModelSource(
                name = "test",
                downloadUrl = "https://example.invalid/model",
                expectedSizeBytes = 3L,
                sha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
            )

            val validation = LlmModelManager.validateModelFile(file, source, verifyChecksum = true)

            assertTrue(validation.isUsable)
            assertTrue(validation.checksumVerified)
            assertTrue(File(file.parentFile, "${file.name}.sha256").exists())
        } finally {
            file.delete()
            File(file.parentFile, "${file.name}.sha256").delete()
        }
    }
}
