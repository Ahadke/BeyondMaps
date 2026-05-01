package com.beyondmaps.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.beyondmaps.ai.BeyondMapsChatbot
import com.beyondmaps.ui.components.AtmosphereBackground
import com.beyondmaps.ui.theme.AccentBlue
import com.beyondmaps.ui.theme.BorderSubtle
import com.beyondmaps.ui.theme.TextDim
import com.beyondmaps.ui.theme.TextPrimary
import com.beyondmaps.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.util.Locale

data class TranslatorLanguage(
    val locale: Locale,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorScreen() {
    var inputText by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf("") }
    var isModelReady by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var isLanguageMenuExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val chatbot = remember { BeyondMapsChatbot(context) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    val defaultLanguages = remember {
        listOf(
            TranslatorLanguage(Locale.ENGLISH, "English"),
            TranslatorLanguage(Locale.ITALIAN, "Italian"),
            TranslatorLanguage(Locale.JAPANESE, "Japanese"),
        )
    }
    var availableLanguages by remember { mutableStateOf(defaultLanguages) }
    var selectedTargetLanguage by remember { mutableStateOf(defaultLanguages[1]) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            outputText = "Microphone permission is required for voice input."
        }
    }

    LaunchedEffect(Unit) {
        isModelReady = chatbot.initialize().isSuccess
        if (!isModelReady) {
            outputText = "Model not ready. Verify model.litertlm is installed."
        }
    }

    DisposableEffect(Unit) {
        var createdTts: TextToSpeech? = null
        val textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val supported = createdTts?.availableLanguages
                    ?.map { locale ->
                        TranslatorLanguage(
                            locale = locale,
                            label = locale.getDisplayLanguage(Locale.ENGLISH).replaceFirstChar { ch ->
                                if (ch.isLowerCase()) ch.titlecase() else ch.toString()
                            },
                        )
                    }
                    ?.distinctBy { it.locale.language }
                    ?.sortedBy { it.label }
                    ?.filter { it.locale.language.isNotBlank() }
                    .orEmpty()
                if (supported.isNotEmpty()) {
                    availableLanguages = supported
                    selectedTargetLanguage = supported.firstOrNull { it.locale.language == Locale.ITALIAN.language }
                        ?: supported.first()
                }
            }
        }
        createdTts = textToSpeech
        tts = textToSpeech

        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
            chatbot.close()
        }
    }

    fun translateAndRender(
        sourceText: String,
        autoSpeak: Boolean,
    ) {
        if (sourceText.isBlank()) {
            outputText = "Please enter text to translate."
            return
        }
        if (!isModelReady) {
            outputText = "Model not ready. Verify model.litertlm is installed."
            return
        }

        val prompt = buildPrompt(sourceText, selectedTargetLanguage)
        coroutineScope.launch {
            try {
                isTranslating = true
                outputText = ""
                chatbot.sendMessage(prompt).collect { chunk ->
                    if (chunk.isNotEmpty()) {
                        outputText += chunk
                    }
                }
                if (outputText.isBlank()) {
                    outputText = "No translation generated."
                } else if (autoSpeak) {
                    val textToSpeak = extractSpeakText(outputText)
                    tts?.language = selectedTargetLanguage.locale
                    tts?.speak(
                        textToSpeak,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "translation_output",
                    )
                }
            } catch (e: Exception) {
                Log.e("TranslatorScreen", "Translation failed: ${e.message}", e)
                outputText = "Translation error: ${e.message}"
            } finally {
                isTranslating = false
            }
        }
    }

    fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            outputText = "Speech recognition unavailable on this device."
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
            }

            override fun onBeginningOfSpeech() {
                isListening = true
            }

            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                outputText = "Voice input error. Try again or use keyboard mic."
                speechRecognizer.destroy()
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION,
                )
                val spokenText = matches?.firstOrNull().orEmpty()
                inputText = spokenText

                translateAndRender(
                    sourceText = spokenText,
                    autoSpeak = true,
                )

                speechRecognizer.destroy()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION,
                )?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    inputText = partial
                }
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        speechRecognizer.startListening(intent)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AtmosphereBackground()
        Column(
            modifier = Modifier
                .zIndex(1f)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Voice Translator",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
            )

            Text(
                text = "1) Speak/type input  2) Choose target language  3) Translate",
                style = MaterialTheme.typography.bodyMedium,
                color = TextDim,
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0x0CFFFFFF),
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.border(0.6.dp, BorderSubtle, RoundedCornerShape(18.dp)),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Input", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { startVoiceInput() }) {
                            Text("🎤", color = AccentBlue)
                        }
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text("Tap mic on keyboard or type") },
                        placeholder = { Text("Speak or type any language...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                }
            }

            ExposedDropdownMenuBox(
                expanded = isLanguageMenuExpanded,
                onExpandedChange = { isLanguageMenuExpanded = !isLanguageMenuExpanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = selectedTargetLanguage.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Translate to") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isLanguageMenuExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                DropdownMenu(
                    expanded = isLanguageMenuExpanded,
                    onDismissRequest = { isLanguageMenuExpanded = false },
                ) {
                    availableLanguages.forEach { language ->
                        DropdownMenuItem(
                            text = { Text(language.label) },
                            onClick = {
                                selectedTargetLanguage = language
                                isLanguageMenuExpanded = false
                            },
                        )
                    }
                }
            }

            if (isListening) {
                Text(
                    text = "Listening...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = AccentBlue,
                    trackColor = Color(0x1FFFFFFF),
                )
            }

            Button(
                onClick = {
                    translateAndRender(
                        sourceText = inputText,
                        autoSpeak = false,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTranslating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0x1A72A4FF),
                    disabledContainerColor = Color(0x0F72A4FF),
                ),
            ) {
                Text(if (isTranslating) "Translating..." else "Translate", color = TextPrimary)
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0x0CFFFFFF),
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .border(0.6.dp, BorderSubtle, RoundedCornerShape(18.dp)),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Translation", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                val textToSpeak = extractSpeakText(outputText)
                                tts?.language = selectedTargetLanguage.locale
                                tts?.speak(
                                    textToSpeak,
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "translation_output",
                                )
                            },
                            enabled = outputText.isNotBlank(),
                        ) {
                            Text(
                                text = "🔊",
                                color = if (outputText.isNotBlank()) AccentBlue else TextDim,
                            )
                        }
                    }
                    Text(
                        text = if (outputText.isBlank()) "Translation output appears here." else outputText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                    )
                }
            }
        }
    }
}

fun buildPrompt(input: String, targetLanguage: TranslatorLanguage): String {
    val target = targetLanguage.label
    return """
        You are a translation assistant.

        Translate the following sentence into natural $target.
        Detect the source language automatically.

        Return ONLY:
        Translation: <translated sentence in $target>
        Pronunciation: <phonetic pronunciation in Latin script if useful, else same as translation>

        Sentence: $input
    """.trimIndent()
}

fun extractSpeakText(output: String): String {
    return output.substringAfter("Translation:", output)
        .substringBefore("Pronunciation:")
        .trim()
}
