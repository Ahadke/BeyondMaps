package com.beyondmaps.viewmodel

import android.app.Application
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

sealed class ChatMessage {
    data class Ai(val text: String, val sources: List<String>? = null) : ChatMessage()
    data class User(val text: String) : ChatMessage()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val chatbot = BeyondMapsChatbot(application.applicationContext)
    private val initializationMutex = Mutex()
    private var isInitialized = false

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(ChatMessage.Ai("Loading offline travel guide model..."))
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    val inputText = MutableStateFlow("")
    private var inferenceJob: Job? = null

    init {
        viewModelScope.launch {
            _isThinking.value = true
            val ready = ensureInitialized()
            _messages.value = if (ready) {
                listOf(ChatMessage.Ai("Offline travel guide is ready. Ask me anything."))
            } else {
                listOf(
                    ChatMessage.Ai(
                        "I could not start the offline model. Check Logcat and confirm model.litertlm is installed."
                    )
                )
            }
            _isThinking.value = false
        }
    }

    fun sendMessage(text: String) {
        val prompt = text.trim()
        if (prompt.isBlank() || _isThinking.value) return

        _messages.value = _messages.value + ChatMessage.User(prompt)
        inputText.value = ""
        _isThinking.value = true

        inferenceJob?.cancel()
        inferenceJob = viewModelScope.launch {
            try {
                if (!ensureInitialized()) {
                    _messages.value = _messages.value + ChatMessage.Ai(
                        "The offline model is not ready. Confirm model.litertlm is at the app files path."
                    )
                    return@launch
                }

                var response = ""
                var assistantMessageAdded = false
                withContext(Dispatchers.IO) {
                    chatbot.sendMessage(prompt).collect { chunk ->
                        if (chunk.isEmpty()) return@collect
                        response += chunk
                        withContext(Dispatchers.Main) {
                            if (!assistantMessageAdded) {
                                _messages.value = _messages.value + ChatMessage.Ai(response)
                                assistantMessageAdded = true
                            } else {
                                replaceLastAssistantMessage(response)
                            }
                        }
                    }
                }

                if (response.isBlank()) {
                    _messages.value = _messages.value + ChatMessage.Ai("No response generated.")
                }
            } catch (_: CancellationException) {
                // Expected when the user taps stop.
            } catch (e: Throwable) {
                Log.e(TAG, "Chat generation failed: ${e.message}", e)
                _messages.value = _messages.value + ChatMessage.Ai(
                    "The offline model failed while generating a response. Check Logcat for details."
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

    private suspend fun ensureInitialized(): Boolean {
        if (isInitialized) return true

        return initializationMutex.withLock {
            if (isInitialized) return@withLock true

            val result = withContext(Dispatchers.IO) {
                chatbot.initialize()
            }
            if (result.isSuccess) {
                isInitialized = true
                true
            } else {
                val error = result.exceptionOrNull()
                Log.e(TAG, "Chatbot initialization failed: ${error?.message}", error)
                false
            }
        }
    }

    private fun replaceLastAssistantMessage(text: String) {
        val currentMessages = _messages.value.toMutableList()
        val lastAssistantIndex = currentMessages.indexOfLast { it is ChatMessage.Ai }
        if (lastAssistantIndex >= 0) {
            currentMessages[lastAssistantIndex] = ChatMessage.Ai(text)
            _messages.value = currentMessages
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
