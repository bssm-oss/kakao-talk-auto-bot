package com.example.kakaotalkautobot

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect

object LlmEngine {
    private const val TAG = "LlmEngine"
    private val lock = Any()

    private var _isLoaded = false
    val isLoaded: Boolean get() = _isLoaded
    private var engine: Engine? = null
    private var lastError: String? = null
    fun getLastError(): String? = lastError

    init {
        try {
            System.loadLibrary("litertlm_jni")
            Log.i(TAG, "LiteRT-LM native library loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load LiteRT-LM JNI library", e)
        }
    }

    data class LlmConfig(
        val maxTokens: Int = 16,
        val temperature: Float = 0.2f,
        val topP: Float = 0.9f,
        val topK: Int = 20,
        val contextSize: Int = 2048
    )

    fun loadModel(
        context: Context,
        config: LlmConfig = LlmConfig(),
        source: LlmModelManager.ModelSource = LlmModelManager.DEFAULT_MODEL
    ): Boolean {
        synchronized(lock) {
            if (_isLoaded) {
                Log.i(TAG, "Model already loaded")
                return true
            }

            if (isUnsupportedEmulator()) {
                lastError = "Gemma/LiteRT-LM local runtime is not supported on Android emulator. Use a real ARM64 device."
                Log.e(TAG, lastError!!)
                return false
            }

            val modelFile = LlmModelManager.getModelFile(context, source)
            if (!modelFile.exists()) {
                lastError = "Model file not found: ${modelFile.absolutePath}"
                Log.w(TAG, "Model file not found: ${modelFile.absolutePath}")
                return false
            }
            val modelInfo = LlmModelManager.getModelInfo(context, source)
            if (!modelInfo.matchesExpectedSource) {
                lastError = "Model file does not match expected source '${source.name}': ${modelInfo.sizeMb}MB"
                Log.w(TAG, "Model file does not match expected source '${source.name}': ${modelInfo.sizeMb}MB")
                return false
            }

            Log.i(TAG, "Loading model: ${modelFile.name} (${modelInfo.sizeMb}MB)")
            try {
                Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
                val engineConfig = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.path,
                    maxNumTokens = config.contextSize
                )
                engine = Engine(engineConfig)
                engine?.initialize()
                val result = engine != null
                _isLoaded = result
                if (result) {
                    lastError = null
                    Log.i(TAG, "Model loaded successfully")
                } else {
                    lastError = "LiteRT-LM engine initialization returned null"
                    Log.e(TAG, "LiteRT-LM engine initialization returned null")
                }
                return result
            } catch (e: Exception) {
                lastError = "LiteRT-LM initialization failed: ${e.message}"
                Log.e(TAG, "Exception during LiteRT-LM initialization", e)
                engine?.close()
                engine = null
                return false
            }
        }
    }

    fun generate(prompt: String, maxTokens: Int = 256): String {
        synchronized(lock) {
            val currentEngine = engine
            if (!_isLoaded || currentEngine == null) {
                Log.w(TAG, "generate called but model not loaded")
                return ""
            }
            return try {
                var output = ""
                val conversationConfig = ConversationConfig(
                    systemInstruction = Contents.of("너는 짧고 자연스럽게 한국어로 답하는 도우미다. 답장 내용만 출력해라.")
                )
                currentEngine.createConversation(conversationConfig).use { conversation ->
                    runBlocking {
                        conversation.sendMessageAsync(prompt).collect { chunk ->
                            output += chunk.toString()
                        }
                    }
                }
                output
            } catch (e: Exception) {
                lastError = "LiteRT-LM generation failed: ${e.message}"
                Log.e(TAG, "LiteRT-LM generation failed", e)
                ""
            }
        }
    }

    fun free() {
        synchronized(lock) {
            if (_isLoaded) {
                try {
                    engine?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to close LiteRT-LM engine cleanly", e)
                }
                engine = null
                _isLoaded = false
                lastError = null
                Log.i(TAG, "Model freed")
            }
        }
    }

    private fun isUnsupportedEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val product = Build.PRODUCT.lowercase()
        return fingerprint.contains("generic") ||
            model.contains("emulator") ||
            model.contains("sdk_gphone") ||
            hardware.contains("ranchu") ||
            product.contains("sdk_gphone")
    }
}
