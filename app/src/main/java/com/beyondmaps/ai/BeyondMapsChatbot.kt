package com.beyondmaps.ai

import android.content.Context
import android.util.Log
import com.beyondmaps.rag.vector.FakeQueryEmbedder
import com.beyondmaps.rag.vector.VectorPackLoader
import com.beyondmaps.rag.vector.VectorPromptBuilder
import com.beyondmaps.rag.vector.VectorRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BeyondMapsChatbot(private val context: Context) {
    private val manager = LiteRTLMManager.getInstance(context)
    private val resolver = ModelResolver(context)
    private val engineMutex = Mutex()
    private val ragMutex = Mutex()
    private val vectorPackLoader = VectorPackLoader(context)
    private val vectorPromptBuilder = VectorPromptBuilder()

    /** Path of the model file last successfully loaded into [manager]. */
    private var activeModelPath: String? = null
    private var ragRetriever: VectorRetriever? = null
    private var ragEmbedder: FakeQueryEmbedder? = null
    private var isRagReady: Boolean = false

    suspend fun initialize(): Result<Boolean> {
        val modelReady = ensureTravelModel()
        if (modelReady.isSuccess) {
            warmupRag()
        }
        return modelReady
    }

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

    suspend fun sendMessageWithRag(userMessage: String): Flow<String> {
        val ragPrompt = tryBuildRagPrompt(userMessage)
        if (ragPrompt != null) {
            Log.d(TAG, "Using RAG-augmented prompt for user request")
            return manager.sendMessage(ragPrompt)
        }
        Log.w(TAG, "Falling back to plain prompt (RAG unavailable)")
        return manager.sendMessage(userMessage)
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

    /**
     * Runs a visual-scene summary (non-OCR) for fusion with OCR text.
     */
    fun sendVisionSummary(userQuery: String, imagePath: String): Flow<String> {
        manager.startConversation(systemPrompt = VISION_SYSTEM_PROMPT)
        val prompt = buildVisionPrompt(userQuery)
        return manager.sendMultimodalMessage(text = prompt, imagePath = imagePath, audioPath = null)
    }

    fun close() {
        manager.cleanup()
        activeModelPath = null
        isRagReady = false
        ragRetriever = null
        ragEmbedder = null
    }

    private suspend fun warmupRag() {
        if (isRagReady) return
        ragMutex.withLock {
            if (isRagReady) return@withLock
            withContext(Dispatchers.IO) {
                runCatching {
                    val pack = vectorPackLoader.loadVectorPack()
                    if (pack.chunks.isEmpty()) {
                        Log.w(TAG, "RAG warmup loaded 0 chunks")
                        return@runCatching
                    }
                    ragRetriever = VectorRetriever(pack)
                    ragEmbedder = FakeQueryEmbedder(pack.chunks)
                    isRagReady = true
                    Log.i(
                        TAG,
                        "RAG warmup complete. chunks=${pack.chunks.size}, vectorSize=${pack.vectorSize}, model=${pack.modelName}",
                    )
                }.onFailure { error ->
                    isRagReady = false
                    ragRetriever = null
                    ragEmbedder = null
                    Log.e(TAG, "RAG warmup failed: ${error.message}", error)
                }
            }
        }
    }

    private suspend fun tryBuildRagPrompt(userMessage: String): String? {
        warmupRag()
        if (!isRagReady) return null
        val retriever = ragRetriever ?: return null
        val embedder = ragEmbedder ?: return null

        return runCatching {
            val queryVector = embedder.embed(userMessage)
            if (queryVector.size != embedder.vectorSize) {
                Log.w(TAG, "RAG query embedding has invalid size=${queryVector.size}")
                return null
            }
            val results = retriever.retrieve(query = userMessage, queryVector = queryVector, topK = RAG_TOP_K)
            Log.d(TAG, "RAG retrieved=${results.size} chunks")
            vectorPromptBuilder.buildPrompt(query = userMessage, results = results)
        }.getOrElse { error ->
            Log.e(TAG, "RAG prompt build failed: ${error.message}", error)
            null
        }
    }

    private fun buildOcrPrompt(userQuery: String): String {
        val q = userQuery.trim().ifBlank {
            "Transcribe all visible text from the image exactly."
        }
        return buildString {
            appendLine("Task: OCR transcription from image.")
            appendLine("Rules (strict):")
            appendLine("1) Output only transcribed text. No explanation, no summary.")
            appendLine("2) Preserve reading order and line breaks.")
            appendLine("3) Keep original casing, punctuation, and numbers exactly.")
            appendLine("4) Never invent or complete missing words.")
            appendLine("5) If a token is not readable, write [unclear].")
            appendLine("6) If no text is visible, output exactly: [unreadable].")
            appendLine()
            appendLine("User request: $q")
        }
    }

    private fun buildVisionPrompt(userQuery: String): String {
        val q = userQuery.trim().ifBlank {
            "Describe the scene and relevant visual context."
        }
        return buildString {
            appendLine("Task: describe non-text visual context from the image.")
            appendLine("Do not transcribe text unless it is essential context.")
            appendLine("Focus on objects, layout, symbols, and likely situation.")
            appendLine("Keep it concise and factual.")
            appendLine()
            appendLine("User request: $q")
        }
    }

    companion object {
        private const val TAG = "BeyondMapsChatbot"
        private const val RAG_TOP_K = 5
        private const val SYSTEM_PROMPT =
            "You are BeyondMaps, an offline travel assistant. Reply clearly and briefly."

        private const val OCR_SYSTEM_PROMPT =
            "You are a strict OCR transcription engine. Return only faithful text from the image. Do not infer, paraphrase, translate, or explain unless explicitly requested."
        private const val VISION_SYSTEM_PROMPT =
            "You are a vision assistant. Describe visual context accurately and briefly without hallucinating details."
    }
}
