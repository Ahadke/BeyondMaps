package com.beyondmaps.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beyondmaps.ai.BeyondMapsChatbot
import com.beyondmaps.ai.OnDeviceOcr
import com.beyondmaps.data.rag.PackImportRepository
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
    private val packImportRepository = PackImportRepository(application.applicationContext)
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
    private val _pendingImageUseCase = MutableStateFlow(ImageUseCase.TRANSLATE_TEXT)
    val pendingImageUseCase: StateFlow<ImageUseCase> = _pendingImageUseCase.asStateFlow()

    private var inferenceJob: Job? = null

    init {
        viewModelScope.launch {
            _isThinking.value = true
            ensureLocalPackImported()
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

    private suspend fun ensureLocalPackImported() {
        val result = withContext(Dispatchers.IO) { packImportRepository.ensureImported() }
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            Log.w(TAG, "Local pack import skipped/failed: ${error?.message}", error)
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

    fun sendMessage(text: String) {
        val prompt = text.trim()
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
                            buildGemmaPromptFromOcr(userPrompt = prompt, extractedText = extracted)
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

    private fun buildGemmaPromptFromOcr(userPrompt: String, extractedText: String): String {
        val request = userPrompt.ifBlank {
            "Translate the extracted text to English and explain what it means."
        }
        return buildString {
            appendLine("The following text was extracted locally using OCR from an image.")
            appendLine("Use only this extracted text. If information is missing, say so.")
            appendLine("If the text is not English, translate it to English first.")
            appendLine("Then explain the translated meaning clearly and briefly.")
            appendLine("If the user asks a specific question, answer it using the translated text.")
            appendLine()
            appendLine("User request: $request")
            appendLine()
            appendLine("OCR_TEXT:")
            appendLine(extractedText)
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
