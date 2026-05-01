package com.beyondmaps.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import com.beyondmaps.viewmodel.ImageUseCase
import java.io.File

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ChatScreen(
    navController: NavHostController,
    viewModel: ChatViewModel = viewModel(),
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val input by viewModel.inputText.collectAsState()
    val pendingImageUri by viewModel.pendingImageUri.collectAsState()
    val selectedUseCase by viewModel.pendingImageUseCase.collectAsState()
    val imeVisible = WindowInsets.isImeVisible

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImageUseCase by remember { mutableStateOf<ImageUseCase?>(null) }

    val pickMediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let { viewModel.setPendingImage(it, selectedUseCase) }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success && pendingCameraUri != null) {
            viewModel.setPendingImage(pendingCameraUri!!, selectedUseCase)
        }
        pendingCameraUri = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val photoFile = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile,
            )
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> {
                val photoFile = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    photoFile,
                )
                pendingCameraUri = uri
                takePictureLauncher.launch(uri)
            }

            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchGallery() {
        pickMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    fun chooseSource(useCase: ImageUseCase) {
        pendingImageUseCase = useCase
        viewModel.setPendingImageUseCase(useCase)
    }

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
                    pendingImageUri = pendingImageUri,
                    onClearImage = { viewModel.clearPendingImage() },
                    onChooseTranslateText = { chooseSource(ImageUseCase.TRANSLATE_TEXT) },
                    onChooseIdentify = { chooseSource(ImageUseCase.IDENTIFY) },
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
                                is ChatMessage.Ai -> AiBubble(msg.text, msg.sources, msg.isOcr)
                                is ChatMessage.User -> UserBubble(msg.text, msg.hadImageAttachment)
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
    if (pendingImageUseCase != null) {
        AlertDialog(
            onDismissRequest = { pendingImageUseCase = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color(0xFF0E1625),
            title = { Text("Choose image source", color = Color(0xFFD6E6FF)) },
            text = {
                Text(
                    if (pendingImageUseCase == ImageUseCase.TRANSLATE_TEXT) {
                        "Translate text mode selected"
                    } else {
                        "Identify mode selected"
                    },
                    color = Color(0xFF8CA5C9),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                    launchGallery()
                    pendingImageUseCase = null
                    }
                ) { Text("Upload picture", color = Color(0xFF8FC0FF)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                    launchCamera()
                    pendingImageUseCase = null
                    }
                ) { Text("Take picture", color = Color(0xFF8FC0FF)) }
            },
        )
    }
}

@Composable
private fun ChatTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .border(0.5.dp, BorderSubtle)
            .padding(top = 16.dp, start = 18.dp, end = 18.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedAssistantAvatar(modifier = Modifier.noRippleClickable(onBack))
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(10.dp))
        Column {
            Text(text = "BeyondMaps", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                text = "Tokyo, Japan",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF5D7398),
            )
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
        LiveStatusDot()
    }
}

@Composable
private fun AnimatedAssistantAvatar(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "assistantAvatar")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200)),
        label = "avatarPulse",
    )
    Box(
        modifier = modifier
            .size(30.dp)
            .background(
                brush = Brush.radialGradient(
                    listOf(Color(0x442C79EE), Color(0x223267B3), Color(0x0F1E3668))
                ),
                shape = CircleShape,
            )
            .border(0.75.dp, Color(0x3F6EAFFF), CircleShape),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(8.dp * pulse)
                .background(Color(0xFF77B6FF), CircleShape),
        )
    }
}

@Composable
private fun LiveStatusDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "statusDot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800)),
        label = "dotAlpha",
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .background(Color(0xFF56DBC0).copy(alpha = dotAlpha), CircleShape),
    )
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
