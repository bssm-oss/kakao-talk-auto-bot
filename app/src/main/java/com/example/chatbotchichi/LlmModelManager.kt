package com.example.kakaotalkautobot

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest

object LlmModelManager {
    private const val TAG = "LlmModelManager"
    private const val MODEL_DIR_NAME = "llm_models"
    private const val DEFAULT_MODEL_FILE_NAME = "model.litertlm"
    private const val TEST_MODEL_FILE_NAME = "model-test.litertlm"

    data class ModelSource(
        val name: String,
        val downloadUrl: String,
        val expectedSizeBytes: Long,
        val sha256: String = ""
    )

    val DEFAULT_MODEL = ModelSource(
        name = "Gemma-4-E2B-it-LiteRT-LM",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        expectedSizeBytes = 2_588_147_712L,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
    )

    val TEST_MODEL = ModelSource(
        name = "Qwen3-0.6B-LiteRT",
        downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
        expectedSizeBytes = 614_236_160L,
        sha256 = "555579ff2f4fd13379abe69c1c3ab5200f7338bc92471557f1d6614a6e5ab0b4"
    )

    fun getModelDir(context: Context): File {
        val dir = File(context.filesDir, MODEL_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getModelFile(context: Context, source: ModelSource = DEFAULT_MODEL): File {
        val fileName = if (source == TEST_MODEL) TEST_MODEL_FILE_NAME else DEFAULT_MODEL_FILE_NAME
        return File(getModelDir(context), fileName)
    }

    fun hasModel(context: Context, source: ModelSource = DEFAULT_MODEL): Boolean {
        return getModelInfo(context, source).matchesExpectedSource
    }

    @Deprecated("Use hasModel(context, source) to check a specific model source")
    fun hasModel(context: Context): Boolean {
        return hasAnyModel(context)
    }

    fun hasAnyModel(context: Context): Boolean {
        return listOf(DEFAULT_MODEL, TEST_MODEL).any { source ->
            getModelInfo(context, source).matchesExpectedSource
        }
    }

    fun getModelInfo(
        context: Context,
        source: ModelSource = DEFAULT_MODEL,
        verifyChecksum: Boolean = false
    ): ModelInfo {
        val file = getModelFile(context, source)
        val sizeBytes = if (file.exists()) file.length() else 0L
        val validation = validateModelFile(file, source, verifyChecksum)
        return ModelInfo(
            exists = file.exists(),
            path = file.absolutePath,
            sizeBytes = sizeBytes,
            sizeMb = if (file.exists()) sizeBytes / 1024 / 1024 else 0L,
            matchesExpectedSource = validation.isUsable,
            sizeMatchesExpected = validation.sizeMatchesExpected,
            checksumVerified = validation.checksumVerified,
            checksumMatchesExpected = validation.checksumMatchesExpected,
            validationMessage = validation.message
        )
    }

    suspend fun downloadModel(
        context: Context,
        source: ModelSource = DEFAULT_MODEL,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val outputFile = getModelFile(context, source)
        val partialFile = File(outputFile.parentFile, "${outputFile.name}.part")

        if (validateModelFile(outputFile, source, verifyChecksum = true).isUsable) {
            Log.i(TAG, "Model already exists and is complete: ${outputFile.absolutePath}")
            return@withContext Result.success(outputFile)
        }

        try {
            partialFile.delete()
            Log.i(TAG, "Downloading model from: ${source.downloadUrl}")
            val url = URL(source.downloadUrl)
            val connection = url.openConnection()
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            val hfToken = BuildConfig.HF_TOKEN.trim()
            if (hfToken.isNotEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer $hfToken")
            }
            connection.connect()

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L

            connection.getInputStream().use { input ->
                FileOutputStream(partialFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            val progress = (downloadedBytes.toDouble() / totalBytes * 100).toInt()
                            onProgress(progress.coerceIn(0, 100))
                        }
                    }
                }
            }

            val validation = validateModelFile(partialFile, source, verifyChecksum = true)
            if (validation.isUsable) {
                outputFile.delete()
                if (!partialFile.renameTo(outputFile)) {
                    partialFile.copyTo(outputFile, overwrite = true)
                    partialFile.delete()
                }
                writeChecksumSidecar(outputFile, source.sha256)
                Log.i(TAG, "Download complete: ${outputFile.length() / 1024 / 1024}MB")
                Result.success(outputFile)
            } else {
                partialFile.delete()
                Result.failure(Exception("Downloaded model failed validation: ${validation.message}"))
            }
        } catch (e: Exception) {
            partialFile.delete()
            Log.e(TAG, "Download failed", e)
            Result.failure(e)
        }
    }

    fun deleteModel(context: Context): Boolean {
        var deletedAny = false
        listOf(DEFAULT_MODEL, TEST_MODEL).forEach { source ->
            val file = getModelFile(context, source)
            if (file.exists() && file.delete()) {
                deletedAny = true
            }
            checksumSidecar(file).delete()
            File(file.parentFile, "${file.name}.part").delete()
        }
        if (deletedAny) Log.i(TAG, "Model deleted")
        return deletedAny
    }

    internal fun validateModelFile(
        file: File,
        source: ModelSource,
        verifyChecksum: Boolean = false
    ): ModelValidation {
        if (!file.exists()) {
            return ModelValidation(
                isUsable = false,
                sizeMatchesExpected = false,
                checksumVerified = false,
                checksumMatchesExpected = false,
                message = "model file missing"
            )
        }

        val sizeMatches = file.length() == source.expectedSizeBytes
        if (!sizeMatches) {
            return ModelValidation(
                isUsable = false,
                sizeMatchesExpected = false,
                checksumVerified = false,
                checksumMatchesExpected = false,
                message = "size ${file.length()} does not match expected ${source.expectedSizeBytes}"
            )
        }

        val expectedSha = source.sha256.trim().lowercase()
        if (expectedSha.isBlank()) {
            return ModelValidation(
                isUsable = true,
                sizeMatchesExpected = true,
                checksumVerified = false,
                checksumMatchesExpected = false,
                message = "size verified; checksum unavailable"
            )
        }

        val storedSha = checksumSidecar(file).takeIf { it.exists() }
            ?.readText()
            ?.trim()
            ?.lowercase()
        if (storedSha == expectedSha) {
            return ModelValidation(
                isUsable = true,
                sizeMatchesExpected = true,
                checksumVerified = true,
                checksumMatchesExpected = true,
                message = "checksum verified"
            )
        }

        if (storedSha != null && !verifyChecksum) {
            return ModelValidation(
                isUsable = false,
                sizeMatchesExpected = true,
                checksumVerified = true,
                checksumMatchesExpected = false,
                message = "checksum sidecar mismatch"
            )
        }

        if (!verifyChecksum) {
            return ModelValidation(
                isUsable = true,
                sizeMatchesExpected = true,
                checksumVerified = false,
                checksumMatchesExpected = false,
                message = "size verified; checksum pending"
            )
        }

        val actualSha = sha256(file)
        val matches = actualSha == expectedSha
        if (matches) {
            writeChecksumSidecar(file, actualSha)
        }
        return ModelValidation(
            isUsable = matches,
            sizeMatchesExpected = true,
            checksumVerified = true,
            checksumMatchesExpected = matches,
            message = if (matches) "checksum verified" else "checksum mismatch"
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun checksumSidecar(file: File): File {
        return File(file.parentFile, "${file.name}.sha256")
    }

    private fun writeChecksumSidecar(file: File, sha256: String) {
        val normalized = sha256.trim().lowercase()
        if (normalized.isBlank()) return
        checksumSidecar(file).writeText(normalized)
    }

    data class ModelValidation(
        val isUsable: Boolean,
        val sizeMatchesExpected: Boolean,
        val checksumVerified: Boolean,
        val checksumMatchesExpected: Boolean,
        val message: String
    )

    data class ModelInfo(
        val exists: Boolean,
        val path: String,
        val sizeBytes: Long,
        val sizeMb: Long,
        val matchesExpectedSource: Boolean,
        val sizeMatchesExpected: Boolean = false,
        val checksumVerified: Boolean = false,
        val checksumMatchesExpected: Boolean = false,
        val validationMessage: String = ""
    )
}
