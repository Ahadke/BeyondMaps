package com.beyondmaps.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beyondmaps.ai.BeyondMapsChatbot
import com.beyondmaps.ai.OnDeviceOcr
import com.beyondmaps.rag.vector.FakeQueryEmbedder
import com.beyondmaps.rag.vector.QueryEmbedder
import com.beyondmaps.rag.vector.VectorPackLoader
import com.beyondmaps.rag.vector.VectorPromptBuilder
import com.beyondmaps.rag.vector.VectorRetriever
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed class ChatMessage {
    data class Ai(
        val text: String,
        val sources: List<String>? = null,
        val isOcr: Boolean = false,
    ) : ChatMessage()

    data class User(
        val text: String,
        val hadImageAttachment: Boolean = false,
        val imageUri: Uri? = null,
    ) : ChatMessage()
}

enum class ImageUseCase {
    TRANSLATE_TEXT,
    IDENTIFY,
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val chatbot = BeyondMapsChatbot(application.applicationContext)
    private val onDeviceOcr = OnDeviceOcr(application.applicationContext)
    private val vectorPackLoader = VectorPackLoader(application.applicationContext)
    private val startupMutex = Mutex()
    private var startupTravelReady = false
    private var queryEmbedder: QueryEmbedder? = null
    private var vectorRetriever: VectorRetriever? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(ChatMessage.Ai("Preparing your offline guide..."))
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    val inputText = MutableStateFlow("")

    private val _pendingImageUri = MutableStateFlow<Uri?>(null)
    val pendingImageUri: StateFlow<Uri?> = _pendingImageUri.asStateFlow()
    private val _pendingImageUseCase = MutableStateFlow(ImageUseCase.TRANSLATE_TEXT)
    val pendingImageUseCase: StateFlow<ImageUseCase> = _pendingImageUseCase.asStateFlow()

    private var inferenceJob: Job? = null

    init {
        viewModelScope.launch {
            _isThinking.value = true
            val ready = ensureTravelAtStartup()
            _messages.value = if (ready) {
                listOf(ChatMessage.Ai("Your offline guide is ready. Ask me anything."))
            } else {
                listOf(
                    ChatMessage.Ai(
                        "I couldn\u2019t start the offline guide right now. Please verify the local model is installed and try again."
                    )
                )
            }
            _isThinking.value = false
        }
    }

    fun setPendingImage(uri: Uri?, useCase: ImageUseCase = _pendingImageUseCase.value) {
        _pendingImageUri.value = uri
        _pendingImageUseCase.value = useCase
    }

    fun setPendingImageUseCase(useCase: ImageUseCase) {
        _pendingImageUseCase.value = useCase
    }

    fun clearPendingImage() {
        _pendingImageUri.value = null
    }

    fun sendMessage(userText: String) {
        Log.d("BeyondMapsRAG", "USER RAW INPUT = $userText")
        Log.d("BeyondMapsRAG", "USER RAW INPUT length = ${userText.length}")
        val prompt = userText.trim()
        val imageUri = _pendingImageUri.value
        val imageUseCase = _pendingImageUseCase.value
        if (prompt.isBlank() && imageUri == null) return
        if (_isThinking.value) return

        val userDisplay = prompt.ifBlank { "(Image attached)" }
        val hadImage = imageUri != null
        _pendingImageUri.value = null

        _messages.value = _messages.value + ChatMessage.User(
            text = userDisplay,
            hadImageAttachment = hadImage,
            imageUri = imageUri,
        )
        inputText.value = ""
        _isThinking.value = true

        inferenceJob?.cancel()
        inferenceJob = viewModelScope.launch {
            try {
                val useImageFlow = hadImage
                val engineOk = withContext(Dispatchers.IO) { chatbot.ensureTravelModel() }
                if (engineOk.isFailure) {
                    val err = engineOk.exceptionOrNull()?.message
                        ?: "The offline model is not ready."
                    _messages.value = _messages.value + ChatMessage.Ai(err, isOcr = useImageFlow)
                    return@launch
                }

                val ragPrompt = if (!useImageFlow) {
                    ensureVectorRagLoaded()

                    val queryVector = queryEmbedder!!.embed(prompt)

                    val results = vectorRetriever!!.retrieve(
                        query = prompt,
                        queryVector = queryVector,
                        userLat = 43.7731,
                        userLon = 11.2550,
                        topK = 6
                    )

                    val builtPrompt = VectorPromptBuilder().buildPrompt(
                        query = prompt,
                        results = results
                    )

                    Log.d("BeyondMapsVectorRAG", "VECTOR RESULTS count=${results.size}")
                    results.forEachIndexed { index, result ->
                        Log.d(
                            "BeyondMapsVectorRAG",
                            "VECTOR RESULT[$index] title=${result.chunk.title}, category=${result.chunk.category}, source=${result.chunk.source}, finalScore=${result.finalScore}"
                        )
                    }

                    Log.d("BeyondMapsVectorRAG", "RAG PROMPT length=${builtPrompt.length}")
                    Log.d("BeyondMapsVectorRAG", "RAG PROMPT START =====")
                    Log.d("BeyondMapsVectorRAG", builtPrompt)
                    Log.d("BeyondMapsVectorRAG", "RAG PROMPT END =====")

                    if (results.isNotEmpty() && !builtPrompt.contains(results.first().chunk.title)) {
                        Log.e("BeyondMapsVectorRAG", "BUG: RAG prompt does not contain first retrieved result title")
                    }

                    builtPrompt
                } else {
                    null
                }

                val modelPrompt = if (useImageFlow) {
                    when (imageUseCase) {
                        ImageUseCase.TRANSLATE_TEXT -> {
                            val extracted = withContext(Dispatchers.IO) {
                                onDeviceOcr.extractText(imageUri!!)
                            }
                            if (extracted.isBlank()) {
                                _messages.value = _messages.value + ChatMessage.Ai(
                                    "I couldn't detect readable text in that image. Try a clearer photo or crop tighter."
                                )
                                return@launch
                            }
                            val prepared = prepareOcrForLlm(extracted)
                            if (prepared.isBlank()) {
                                _messages.value = _messages.value + ChatMessage.Ai(
                                    "I detected text, but it was too noisy to translate reliably. Try a closer photo with less glare."
                                )
                                return@launch
                            }
                            buildGemmaPromptFromOcr(userPrompt = prompt, extractedText = prepared)
                        }
                        ImageUseCase.IDENTIFY -> {
                            val imagePath = withContext(Dispatchers.IO) { copyUriToImageFile(imageUri!!) }
                            if (imagePath == null) {
                                _messages.value = _messages.value + ChatMessage.Ai(
                                    "I couldn't process that image. Please try another one."
                                )
                                return@launch
                            }
                            val fastVlmReady = withContext(Dispatchers.IO) { chatbot.ensureFastVlmModel() }
                            if (fastVlmReady.isFailure) {
                                _messages.value = _messages.value + ChatMessage.Ai(
                                    fastVlmReady.exceptionOrNull()?.message
                                        ?: "FastVLM is not ready on this device."
                                )
                                return@launch
                            }
                            var response = ""
                            var assistantMessageAdded = false
                            withContext(Dispatchers.IO) {
                                chatbot.sendVisionSummary(prompt, imagePath).collect { chunk ->
                                    if (chunk.isEmpty()) return@collect
                                    response += chunk
                                    withContext(Dispatchers.Main) {
                                        if (!assistantMessageAdded) {
                                            _messages.value = _messages.value + ChatMessage.Ai(response, isOcr = true)
                                            assistantMessageAdded = true
                                        } else {
                                            replaceLastAssistantMessage(response, true)
                                        }
                                    }
                                }
                            }
                            _isThinking.value = false
                            return@launch
                        }
                    }
                } else ragPrompt!!

                var response = ""
                var assistantMessageAdded = false
                withContext(Dispatchers.IO) {
                    val promptBeingSent = if (!useImageFlow) ragPrompt!! else modelPrompt

                    if (!useImageFlow) {
                        Log.d("BeyondMapsVectorRAG", "PROMPT BEING SENT TO LLM length=${promptBeingSent.length}")
                        Log.d("BeyondMapsVectorRAG", "PROMPT BEING SENT contains Local context = ${promptBeingSent.contains("Local context:")}")
                        Log.d("BeyondMapsVectorRAG", "PROMPT BEING SENT contains user raw only = ${promptBeingSent == userText}")

                        if (promptBeingSent == userText) {
                            Log.e("BeyondMapsVectorRAG", "BUG: raw userText is being sent to LLM instead of RAG prompt")
                        }

                        if (!promptBeingSent.contains("Local context:")) {
                            Log.e("BeyondMapsVectorRAG", "BUG: prompt sent to LLM does not contain Local context")
                        }
                    } else {
                        Log.w("BeyondMapsRAG", "OLD RAW LLM PATH USED")
                    }

                    chatbot.sendMessage(promptBeingSent).collect { token ->
                        Log.d("BeyondMapsChatbot", "VECTOR_RAG_MODEL_CHUNK: $token")
                        val chunk = token
                        if (chunk.isEmpty()) return@collect
                        response += chunk
                        withContext(Dispatchers.Main) {
                            if (!assistantMessageAdded) {
                                _messages.value = _messages.value + ChatMessage.Ai(response, isOcr = useImageFlow)
                                assistantMessageAdded = true
                            } else {
                                replaceLastAssistantMessage(response, useImageFlow)
                            }
                        }
                    }
                }

                if (response.isBlank()) {
                    _messages.value = _messages.value + ChatMessage.Ai("No response generated.", isOcr = useImageFlow)
                }
            } catch (_: CancellationException) {
                // Expected when the user taps stop.
            } catch (e: Throwable) {
                Log.e(TAG, "Chat generation failed: ${e.message}", e)
                _messages.value = _messages.value + ChatMessage.Ai(
                    "I ran into a problem while generating that response. Please try again in a moment."
                )
            } finally {
                _isThinking.value = false
            }
        }
    }

    fun onStop() {
        inferenceJob?.cancel()
        inferenceJob = null
        _isThinking.value = false
    }

    private suspend fun ensureTravelAtStartup(): Boolean {
        if (startupTravelReady) return true
        return startupMutex.withLock {
            if (startupTravelReady) return@withLock true
            val result = withContext(Dispatchers.IO) {
                chatbot.ensureTravelModel()
            }
            if (result.isSuccess) {
                startupTravelReady = true
                true
            } else {
                val error = result.exceptionOrNull()
                Log.e(TAG, "Chatbot startup failed: ${error?.message}", error)
                false
            }
        }
    }

    private suspend fun ensureVectorRagLoaded() {
        if (queryEmbedder != null && vectorRetriever != null) return
        withContext(Dispatchers.IO) {
            if (queryEmbedder != null && vectorRetriever != null) return@withContext
            val pack = vectorPackLoader.loadVectorPack()
            queryEmbedder = FakeQueryEmbedder(pack.chunks)
            vectorRetriever = VectorRetriever(pack)
            Log.d("BeyondMapsVectorRAG", "Vector RAG loaded chunks=${pack.chunks.size}, modelName=${pack.modelName}, vectorSize=${pack.vectorSize}")
        }
    }

    private fun replaceLastAssistantMessage(text: String, isOcr: Boolean) {
        val currentMessages = _messages.value.toMutableList()
        val lastAssistantIndex = currentMessages.indexOfLast { it is ChatMessage.Ai }
        if (lastAssistantIndex >= 0) {
            val old = currentMessages[lastAssistantIndex] as ChatMessage.Ai
            currentMessages[lastAssistantIndex] = ChatMessage.Ai(
                text = text,
                sources = old.sources,
                isOcr = isOcr,
            )
            _messages.value = currentMessages
        }
    }

    private fun buildGemmaPromptFromOcr(userPrompt: String, extractedText: String): String {
        val request = userPrompt.ifBlank {
            "Translate this text to clear English for a traveler."
        }
        return buildString {
            appendLine("Use only OCR_TEXT. Do not invent missing text.")
            appendLine("Keep response clear, practical, and easy to read on mobile.")
            appendLine("Do not repeat duplicate multilingual lines; keep only the clearest source line.")
            appendLine("If a line is uncertain, keep it and mark [OCR uncertain].")
            appendLine("Prefer this structure when helpful:")
            appendLine("- Translation lines as: Original text -> English meaning")
            appendLine("- Then brief practical notes")
            appendLine("Do not use awkward labels before arrows (for example: 'Practical ->').")
            appendLine("Tone should be natural and conversational, not robotic.")
            appendLine("A slightly longer response is okay when it improves clarity.")
            appendLine("Keep practical notes to around 2-4 bullets.")
            appendLine()
            appendLine("User request: $request")
            appendLine()
            appendLine("OCR_TEXT:")
            appendLine(extractedText)
        }
    }

    private fun prepareOcrForLlm(raw: String): String {
        val normalized = raw.lineSequence()
            .map { it.replace('\u00A0', ' ') }
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .map { it.replace('•', '-').replace('·', '-') }
            .filter { it.isNotBlank() }
            .toList()

        if (normalized.isEmpty()) return ""

        data class ScoredLine(val index: Int, val text: String, val score: Double)

        val deduped = mutableListOf<ScoredLine>()
        val seen = mutableSetOf<String>()
        normalized.forEachIndexed { idx, line ->
            val canonical = canonicalLine(line)
            if (canonical.isBlank() || canonical in seen) return@forEachIndexed
            seen += canonical
            val score = lineQualityScore(line)
            if (score < 0.2) return@forEachIndexed
            val tagged = if (score < 0.55) "$line [OCR uncertain]" else line
            deduped += ScoredLine(idx, tagged, score)
        }

        if (deduped.isEmpty()) return ""

        val selected = deduped
            .sortedByDescending { it.score }
            .take(8)
            .sortedBy { it.index }
            .map { it.text }

        return selected.joinToString("\n")
    }

    private fun canonicalLine(line: String): String {
        // Keep prices in visible output, but ignore them for dedupe matching.
        val noPrice = line.replace(Regex("[$€£¥]?\\s?\\d+[\\d.,]*"), " ")
        return noPrice.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun lineQualityScore(line: String): Double {
        val compact = line.filterNot { it.isWhitespace() }
        if (compact.isBlank()) return 0.0
        val alnum = compact.count { it.isLetterOrDigit() }.toDouble()
        val ratio = alnum / compact.length.toDouble()
        val lengthFactor = (compact.length.coerceAtMost(40) / 40.0)
        return (ratio * 0.8) + (lengthFactor * 0.2)
    }

    private suspend fun copyUriToImageFile(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val app = getApplication<Application>()
            val dest = File(app.cacheDir, "vision_${System.currentTimeMillis()}.jpg")
            app.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: return@withContext null
            dest.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "copyUriToImageFile failed", e)
            null
        }
    }

    override fun onCleared() {
        inferenceJob?.cancel()
        chatbot.close()
        super.onCleared()
    }

    private companion object {
        const val TAG = "ChatViewModel"
    }
}
