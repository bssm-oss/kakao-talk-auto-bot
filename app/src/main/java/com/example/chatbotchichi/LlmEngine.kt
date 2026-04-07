package com.example.kakaotalkautobot

import android.content.Context
import android.util.Log
import java.io.File

object LlmEngine {
    private const val TAG = "LlmEngine"

    private var _isLoaded = false
    val isLoaded: Boolean get() = _isLoaded

    data class LlmConfig(
        val maxTokens: Int = 256,
        val temperature: Float = 0.7f,
        val topP: Float = 0.9f,
        val topK: Int = 40,
        val contextSize: Int = 2048
    )

    fun loadModel(context: Context, config: LlmConfig = LlmConfig()): Boolean {
        if (_isLoaded) {
            Log.i(TAG, "Model already loaded")
            return true
        }

        val modelFile = LlmModelManager.getModelFile(context)
        if (!modelFile.exists()) {
            Log.w(TAG, "Model file not found: ${modelFile.absolutePath}")
            return false
        }

        Log.i(TAG, "Loading model: ${modelFile.name} (${modelFile.length() / 1024 / 1024}MB)")
        val result = nativeInit(
            modelPath = modelFile.absolutePath,
            maxTokens = config.maxTokens,
            temperature = config.temperature,
            topP = config.topP,
            topK = config.topK,
            nCtx = config.contextSize
        )

        _isLoaded = result
        if (result) {
            Log.i(TAG, "Model loaded successfully")
        } else {
            Log.e(TAG, "Failed to load model")
        }
        return result
    }

    fun generate(prompt: String, maxTokens: Int = 256): String {
        if (!_isLoaded) {
            Log.w(TAG, "generate called but model not loaded")
            return ""
        }
        return nativeGenerate(prompt, maxTokens)
    }

    fun free() {
        if (_isLoaded) {
            nativeFree()
            _isLoaded = false
            Log.i(TAG, "Model freed")
        }
    }

    // Native methods
    private external fun nativeInit(
        modelPath: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        nCtx: Int
    ): Boolean

    private external fun nativeGenerate(prompt: String, maxTokens: Int): String

    private external fun nativeFree()
}
