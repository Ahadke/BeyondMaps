package com.beyondmaps.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ChatMessage {
    data class Ai(val text: String, val sources: List<String>? = null) : ChatMessage()
    data class User(val text: String) : ChatMessage()
}

class ChatViewModel : ViewModel() {
    private val replies = listOf(
        "**Fuunji** - 3 min from the west exit, open until 3 am. Thick tsukemen broth, cash only.",
        "Generally considered impolite - locals eat at standing counters, not while walking.",
        "Suica card is the easiest way to ride any train or subway in Tokyo. Top up at any station kiosk.",
        "A standard bow of about 15 degrees is appropriate for most casual interactions.",
    )
    private var replyIndex = 0

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(ChatMessage.Ai("Ask anything, and I will answer from local context soon."))
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    val inputText = MutableStateFlow("")

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        _messages.value = _messages.value + ChatMessage.User(text.trim())
        inputText.value = ""
        _isThinking.value = true

        viewModelScope.launch {
            delay(1500)
            val response = replies[replyIndex % replies.size]
            replyIndex++
            _messages.value = _messages.value + ChatMessage.Ai(
                text = response,
                sources = listOf("Tokyo Guide", "Transit Pack")
            )
            _isThinking.value = false
        }
    }
}
