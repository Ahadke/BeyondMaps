package com.beyondmaps.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow

class BeyondMapsChatbot(private val context: Context) {
    private val manager = LiteRTLMManager.getInstance(context)
    private val resolver = ModelResolver(context)

    suspend fun initialize(): Result<Boolean> {
        val expectedPath = resolver.expectedPath()
        Log.i(TAG, "Expected model path: $expectedPath")

        val modelPath = runCatching { resolver.getModelPath() }.getOrElse { error ->
            Log.e(TAG, "Model validation failed: ${error.message}", error)
            return Result.failure(error)
        }

        return manager.initialize(
            modelPath = modelPath,
            systemPrompt = SYSTEM_PROMPT,
            preferredBackend = "NPU",
            supportsImage = true,
            supportsAudio = true
        )
    }

    fun sendMessage(text: String): Flow<String> {
        return manager.sendMessage(text)
    }

    fun close() {
        manager.cleanup()
    }

    companion object {
        private const val TAG = "BeyondMapsChatbot"
        private const val SYSTEM_PROMPT =
            "You are BeyondMaps, an offline travel assistant. Reply clearly and briefly."
    }
}
