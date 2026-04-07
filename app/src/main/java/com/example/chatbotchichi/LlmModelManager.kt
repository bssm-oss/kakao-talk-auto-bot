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
    const val MODEL_FILE_NAME = "model.gguf"

    // Default model: Qwen2.5-1.5B-Instruct GGUF Q4_K_M
    // Using HuggingFace direct download URL
    data class ModelSource(
        val name: String,
        val downloadUrl: String,
        val expectedSizeBytes: Long,
        val sha256: String = ""
    )

    val DEFAULT_MODEL = ModelSource(
        name = "Qwen2.5-1.5B-Instruct-Q4_K_M",
        downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
        expectedSizeBytes = 1_100_000_000L // ~1.1GB, approximate
    )

    fun getModelDir(context: Context): File {
        val dir = File(context.filesDir, MODEL_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getModelFile(context: Context): File {
        return File(getModelDir(context), MODEL_FILE_NAME)
    }

    fun hasModel(context: Context): Boolean {
        val file = getModelFile(context)
        return file.exists() && file.length() > 100_000_000L // At least 100MB
    }

    fun getModelInfo(context: Context): ModelInfo {
        val file = getModelFile(context)
        return ModelInfo(
            exists = file.exists(),
            path = file.absolutePath,
            sizeBytes = if (file.exists()) file.length() else 0L,
            sizeMb = if (file.exists()) file.length() / 1024 / 1024 else 0L
        )
    }

    suspend fun downloadModel(
        context: Context,
        source: ModelSource = DEFAULT_MODEL,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val outputFile = getModelFile(context)

        if (outputFile.exists() && outputFile.length() > source.expectedSizeBytes * 0.9) {
            Log.i(TAG, "Model already exists and is complete: ${outputFile.absolutePath}")
            return@withContext Result.success(outputFile)
        }

        try {
            Log.i(TAG, "Downloading model from: ${source.downloadUrl}")
            val url = URL(source.downloadUrl)
            val connection = url.openConnection()
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

            if (outputFile.length() > 100_000_000L) {
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
        val file = getModelFile(context)
        if (file.exists()) {
            val deleted = file.delete()
            if (deleted) Log.i(TAG, "Model deleted")
            return deleted
        }
        return false
    }

    data class ModelInfo(
        val exists: Boolean,
        val path: String,
        val sizeBytes: Long,
        val sizeMb: Long
    )
}
