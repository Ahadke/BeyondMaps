package com.beyondmaps.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BeyondMapsChatbot(private val context: Context) {
    private val manager = LiteRTLMManager.getInstance(context)
    private val resolver = ModelResolver(context)
    private val engineMutex = Mutex()

    /** Path of the model file last successfully loaded into [manager]. */
    private var activeModelPath: String? = null

    suspend fun initialize(): Result<Boolean> = ensureTravelModel()

    /**
     * Ensures the travel/text model (e.g. Gemma as `model.litertlm`) is loaded.
     */
    suspend fun ensureTravelModel(): Result<Boolean> = engineMutex.withLock {
        val modelPath = runCatching { resolver.getModelPath() }.getOrElse { error ->
            Log.e(TAG, "Travel model validation failed: ${error.message}", error)
            return@withLock Result.failure(error)
        }
        if (modelPath == activeModelPath) {
            return@withLock Result.success(true)
        }
        Log.i(TAG, "Loading travel model: $modelPath")
        val result = manager.initialize(
            modelPath = modelPath,
            systemPrompt = SYSTEM_PROMPT,
            preferredBackend = "NPU",
            supportsImage = true,
            supportsAudio = true,
        )
        if (result.isSuccess) activeModelPath = modelPath
        result
    }

    /**
     * Ensures the FastVLM vision model is loaded for OCR / image+text turns.
     */
    suspend fun ensureFastVlmModel(): Result<Boolean> = engineMutex.withLock {
        if (!resolver.fastVlmModelExists()) {
            val msg =
                "FastVLM model is missing. Copy a FastVLM .litertlm file to: ${resolver.expectedFastVlmPathHint()}"
            Log.e(TAG, msg)
            return@withLock Result.failure(IllegalStateException(msg))
        }
        val modelPath = runCatching { resolver.getFastVlmModelPath() }.getOrElse { error ->
            Log.e(TAG, "FastVLM path resolution failed: ${error.message}", error)
            return@withLock Result.failure(error)
        }
        if (modelPath == activeModelPath) {
            return@withLock Result.success(true)
        }
        Log.i(TAG, "Loading FastVLM model: $modelPath")
        val result = manager.initialize(
            modelPath = modelPath,
            systemPrompt = OCR_SYSTEM_PROMPT,
            preferredBackend = "NPU",
            supportsImage = true,
            supportsAudio = false,
        )
        if (result.isSuccess) activeModelPath = modelPath
        result
    }

    fun sendMessage(text: String): Flow<String> {
        return manager.sendMessage(text)
    }

    /**
     * Runs OCR-style extraction: image + user query, using FastVLM (caller must call [ensureFastVlmModel] first).
     */
    fun sendOcrMessage(userQuery: String, imagePath: String): Flow<String> {
        // Start a fresh conversation for each OCR request.
        // EngineConfig uses maxNumImages=1, so reusing one conversation across multiple image turns
        // can fail after the first image.
        manager.startConversation(systemPrompt = OCR_SYSTEM_PROMPT)
        val prompt = buildOcrPrompt(userQuery)
        return manager.sendMultimodalMessage(text = prompt, imagePath = imagePath, audioPath = null)
    }

    fun close() {
        manager.cleanup()
        activeModelPath = null
    }

    private fun buildOcrPrompt(userQuery: String): String {
        val q = userQuery.trim().ifBlank {
            "Extract all visible text from the image. Preserve line breaks where possible."
        }
        return buildString {
            appendLine(
                "You are an OCR assistant. Follow these rules strictly:",
            )
            appendLine("- Output only the text relevant to the user's request (or full transcript if asked).")
            appendLine("- Preserve reading order and line breaks when possible.")
            appendLine("- If a word is unclear, write [unclear] for that fragment.")
            appendLine("- Do not add commentary before or after the extracted content unless the user explicitly asks for explanation.")
            appendLine()
            appendLine("User request: $q")
        }
    }

    companion object {
        private const val TAG = "BeyondMapsChatbot"
        private const val SYSTEM_PROMPT =
            "You are BeyondMaps, an offline travel assistant. Reply clearly and briefly."

        private const val OCR_SYSTEM_PROMPT =
            "You extract text from images accurately and concisely. Output only what the user asked for unless they request formatting help."
    }
}
