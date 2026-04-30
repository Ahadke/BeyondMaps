package com.beyondmaps.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.beyondmaps.ui.components.AiBubble
import com.beyondmaps.ui.components.AtmosphereBackground
import com.beyondmaps.ui.components.ChatInputBar
import com.beyondmaps.ui.components.TypingIndicator
import com.beyondmaps.ui.components.UserBubble
import com.beyondmaps.ui.theme.BorderSubtle
import com.beyondmaps.ui.theme.TextPrimary
import com.beyondmaps.viewmodel.ChatMessage
import com.beyondmaps.viewmodel.ChatViewModel

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ChatScreen(
    navController: NavHostController,
    viewModel: ChatViewModel = viewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val input by viewModel.inputText.collectAsState()
    val imeVisible = WindowInsets.isImeVisible

    Box(modifier = Modifier.fillMaxSize()) {
        AtmosphereBackground()
        Scaffold(
            modifier = Modifier.fillMaxSize().zIndex(1f),
            containerColor = Color.Transparent,
            contentColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = { ChatTopBar(onBack = { navController.navigateUp() }) },
            bottomBar = {
                ChatInputBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (imeVisible) {
                                Modifier.imePadding()
                            } else {
                                Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                            }
                        ),
                    value = input,
                    onValueChange = { viewModel.inputText.value = it },
                    isThinking = isThinking,
                    onSend = { viewModel.sendMessage(input) },
                    onStop = { viewModel.onStop() },
                    placeholder = "Ask anything about Tokyo...",
                    destination = "Tokyo, Japan",
                    entryCount = "2,400 entries",
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = {
                    itemsIndexed(messages) { _, msg ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(280)) + slideInVertically(
                                tween(280, easing = FastOutSlowInEasing)
                            ) { it / 4 }
                        ) {
                            when (msg) {
                                is ChatMessage.Ai -> AiBubble(msg.text, msg.sources)
                                is ChatMessage.User -> UserBubble(msg.text)
                            }
                        }
                    }
                    if (isThinking) {
                        item {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(280)) + slideInVertically(
                                    tween(280, easing = FastOutSlowInEasing)
                                ) { it / 4 }
                            ) { TypingIndicator() }
                        }
                    }
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            )
        }
    }
}

@Composable
private fun ChatTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, BorderSubtle)
            .padding(top = 16.dp, start = 18.dp, end = 18.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(Color.Transparent, CircleShape)
                .border(0.5.dp, Color(0xFF2A3C5A), CircleShape)
                .padding(7.dp)
                .background(Color(0xFF2A3C5A), CircleShape)
                .noRippleClickable(onBack),
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(10.dp))
        Column {
            Text(text = "Travel Guide", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                text = "Tokyo, Japan",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF5D7398),
            )
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(Color(0xFF1A3360), CircleShape),
        )
    }
}

private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    return this.then(
        clickable(
            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
            indication = null,
            onClick = onClick,
        )
    )
}
