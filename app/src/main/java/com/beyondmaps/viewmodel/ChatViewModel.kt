package com.beyondmaps.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beyondmaps.ai.BeyondMapsChatbot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
                val useOcr = hadImage
                val imagePath = if (useOcr) {
                    withContext(Dispatchers.IO) { copyUriToImageFile(imageUri!!) }
                } else {
                    null
                }

                if (useOcr && imagePath == null) {
                    _messages.value = _messages.value + ChatMessage.Ai(
                        "I couldn\u2019t read that image. Try another photo or pick from Gallery again."
                    )
                    return@launch
                }

                val engineOk = withContext(Dispatchers.IO) {
                    if (useOcr) {
                        chatbot.ensureFastVlmModel()
                    } else {
                        chatbot.ensureTravelModel()
                    }
                }
                if (engineOk.isFailure) {
                    val err = engineOk.exceptionOrNull()?.message
                        ?: "The offline model is not ready."
                    _messages.value = _messages.value + ChatMessage.Ai(err, isOcr = useOcr)
                    return@launch
                }

                var response = ""
                var assistantMessageAdded = false
                withContext(Dispatchers.IO) {
                    val stream = if (useOcr) {
                        chatbot.sendOcrMessage(prompt, imagePath!!)
                    } else {
                        chatbot.sendMessage(prompt)
                    }
                    stream.collect { chunk ->
                        if (chunk.isEmpty()) return@collect
                        response += chunk
                        withContext(Dispatchers.Main) {
                            if (!assistantMessageAdded) {
                                _messages.value = _messages.value + ChatMessage.Ai(response, isOcr = useOcr)
                                assistantMessageAdded = true
                            } else {
                                replaceLastAssistantMessage(response, useOcr)
                            }
                        }
                    }
                }

                if (response.isBlank()) {
                    _messages.value = _messages.value + ChatMessage.Ai("No response generated.", isOcr = useOcr)
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

    private suspend fun copyUriToImageFile(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val app = getApplication<Application>()
            val dest = File(app.cacheDir, "ocr_${System.currentTimeMillis()}.jpg")
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
