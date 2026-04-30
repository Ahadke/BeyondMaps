package com.beyondmaps.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow

class BeyondMapsChatbot(private val context: Context) {
    private val manager = LiteRTLMManager.getInstance(context)
    private val resolver = ModelResolver(context)

    suspend fun initialize(): Result<Boolean> {
        Log.i(TAG, "Model exists at: ${resolver.expectedPath()}")
        return manager.initialize(
            modelPath = resolver.getModelPath(),
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
