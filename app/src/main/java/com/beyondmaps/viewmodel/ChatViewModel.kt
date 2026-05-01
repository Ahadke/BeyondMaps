package com.beyondmaps.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beyondmaps.ai.BeyondMapsChatbot
import com.beyondmaps.ai.OnDeviceOcr
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
    ) : ChatMessage()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val chatbot = BeyondMapsChatbot(application.applicationContext)
    private val onDeviceOcr = OnDeviceOcr(application.applicationContext)
    private val startupMutex = Mutex()
    private var startupTravelReady = false

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(ChatMessage.Ai("Preparing your offline guide..."))
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    val inputText = MutableStateFlow("")

    private val _pendingImageUri = MutableStateFlow<Uri?>(null)
    val pendingImageUri: StateFlow<Uri?> = _pendingImageUri.asStateFlow()

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

    fun setPendingImage(uri: Uri?) {
        _pendingImageUri.value = uri
    }

    fun clearPendingImage() {
        _pendingImageUri.value = null
    }

    fun sendMessage(text: String) {
        val prompt = text.trim()
        val imageUri = _pendingImageUri.value
        if (prompt.isBlank() && imageUri == null) return
        if (_isThinking.value) return

        val userDisplay = prompt.ifBlank { "(Image attached)" }
        val hadImage = imageUri != null
        _pendingImageUri.value = null

        _messages.value = _messages.value + ChatMessage.User(userDisplay, hadImageAttachment = hadImage)
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

                val modelPrompt = if (useImageFlow) {
                    val extracted = withContext(Dispatchers.IO) {
                        onDeviceOcr.extractText(imageUri!!)
                    }
                    val imagePath = withContext(Dispatchers.IO) { copyUriToImageFile(imageUri!!) }
                    val visionSummary = if (imagePath != null) {
                        withContext(Dispatchers.IO) { getFastVlmVisionSummary(prompt, imagePath) }
                    } else {
                        ""
                    }
                    if (extracted.isBlank() && visionSummary.isBlank()) {
                        _messages.value = _messages.value + ChatMessage.Ai(
                            "I couldn't extract text or visual context from that image. Try a clearer photo."
                        )
                        return@launch
                    }
                    // Switch back to Gemma for final reasoning after optional FastVLM pass.
                    withContext(Dispatchers.IO) { chatbot.ensureTravelModel() }
                    buildGemmaFusionPrompt(
                        userPrompt = prompt,
                        extractedText = extracted,
                        visionSummary = visionSummary,
                    )
                } else {
                    prompt
                }

                var response = ""
                var assistantMessageAdded = false
                withContext(Dispatchers.IO) {
                    val stream = chatbot.sendMessage(modelPrompt)
                    stream.collect { chunk ->
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

    private suspend fun getFastVlmVisionSummary(userPrompt: String, imagePath: String): String {
        val fastVlmReady = chatbot.ensureFastVlmModel()
        if (fastVlmReady.isFailure) {
            Log.w(TAG, "FastVLM unavailable for fusion: ${fastVlmReady.exceptionOrNull()?.message}")
            return ""
        }
        return runCatching {
            val sb = StringBuilder()
            chatbot.sendVisionSummary(userPrompt, imagePath).collect { chunk ->
                if (chunk.isNotEmpty()) sb.append(chunk)
            }
            sb.toString().trim()
        }.getOrElse { e ->
            Log.w(TAG, "FastVLM vision summary failed", e)
            ""
        }
    }

    private fun buildGemmaFusionPrompt(
        userPrompt: String,
        extractedText: String,
        visionSummary: String,
    ): String {
        val request = userPrompt.ifBlank {
            "Translate the extracted text to English and explain what it means."
        }
        return buildString {
            appendLine("You are given OCR text and a visual summary from the same image.")
            appendLine("Use both sources for the best answer.")
            appendLine("Prioritize OCR for literal text content.")
            appendLine("Use visual summary for scene context and non-text cues.")
            appendLine("If OCR text is non-English, translate it to English before explaining.")
            appendLine("If sources conflict, prefer OCR for exact wording and mention uncertainty.")
            appendLine()
            appendLine("User request: $request")
            appendLine()
            appendLine("OCR_TEXT:")
            appendLine(if (extractedText.isBlank()) "[none]" else extractedText)
            appendLine()
            appendLine("VISION_SUMMARY:")
            appendLine(if (visionSummary.isBlank()) "[none]" else visionSummary)
        }
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
