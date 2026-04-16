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
        expectedSizeBytes = 2_583_085_056L
    )

    val TEST_MODEL = ModelSource(
        name = "Qwen3-0.6B-LiteRT",
        downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
        expectedSizeBytes = 614_000_000L
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
        val file = getModelFile(context, source)
        return file.exists() && file.length() > source.expectedSizeBytes * 0.9
    }

    @Deprecated("Use hasModel(context, source) to check a specific model source")
    fun hasModel(context: Context): Boolean {
        return hasAnyModel(context)
    }

    fun hasAnyModel(context: Context): Boolean {
        return listOf(DEFAULT_MODEL, TEST_MODEL).any { source ->
            val file = getModelFile(context, source)
            file.exists() && file.length() > 100_000_000L
        }
    }

    fun getModelInfo(context: Context, source: ModelSource = DEFAULT_MODEL): ModelInfo {
        val file = getModelFile(context, source)
        val sizeBytes = if (file.exists()) file.length() else 0L
        return ModelInfo(
            exists = file.exists(),
            path = file.absolutePath,
            sizeBytes = sizeBytes,
            sizeMb = if (file.exists()) sizeBytes / 1024 / 1024 else 0L,
            matchesExpectedSource = file.exists() && sizeBytes > source.expectedSizeBytes * 0.9
        )
    }

    suspend fun downloadModel(
        context: Context,
        source: ModelSource = DEFAULT_MODEL,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val outputFile = getModelFile(context, source)

        if (outputFile.exists() && outputFile.length() > source.expectedSizeBytes * 0.9) {
            Log.i(TAG, "Model already exists and is complete: ${outputFile.absolutePath}")
            return@withContext Result.success(outputFile)
        }

        try {
            Log.i(TAG, "Downloading model from: ${source.downloadUrl}")
            val url = URL(source.downloadUrl)
            val connection = url.openConnection()
            val hfToken = BuildConfig.HF_TOKEN.trim()
            if (hfToken.isNotEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer $hfToken")
            }
            connection.connect()

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L

            connection.getInputStream().use { input ->
                FileOutputStream(outputFile).use { output ->
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

            if (outputFile.length() > source.expectedSizeBytes * 0.9) {
                Log.i(TAG, "Download complete: ${outputFile.length() / 1024 / 1024}MB")
                Result.success(outputFile)
            } else {
                outputFile.delete()
                Result.failure(Exception("Downloaded file too small, possibly incomplete"))
            }
        } catch (e: Exception) {
            outputFile.delete()
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
        }
        if (deletedAny) Log.i(TAG, "Model deleted")
        return deletedAny
    }

    data class ModelInfo(
        val exists: Boolean,
        val path: String,
        val sizeBytes: Long,
        val sizeMb: Long,
        val matchesExpectedSource: Boolean
    )
}
