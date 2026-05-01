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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.beyondmaps.ai.BeyondMapsChatbot
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun TranslatorScreen() {
    var inputText by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf("") }
    var isEnglishToItalian by remember { mutableStateOf(true) }
    var detectedMode by remember { mutableStateOf(isEnglishToItalian) }
    var isModelReady by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var skipClearForAutoMode by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val chatbot = remember { BeyondMapsChatbot(context) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
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

    LaunchedEffect(isEnglishToItalian) {
        if (skipClearForAutoMode) {
            skipClearForAutoMode = false
            return@LaunchedEffect
        }
        inputText = ""
        outputText = ""
    }

    DisposableEffect(Unit) {
        val textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // default language
            }
        }
        tts = textToSpeech

        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
            chatbot.close()
        }
    }

    fun translateAndRender(
        sourceText: String,
        autoMode: Boolean,
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

        val prompt = buildPrompt(sourceText, autoMode)
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
                    val textToSpeak = extractSpeakText(outputText, autoMode)
                    tts?.language = if (autoMode) Locale.ITALIAN else Locale.ENGLISH
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
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                if (isEnglishToItalian) "en-US" else "it-IT",
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

                val autoMode = detectEnglishToItalian(spokenText)
                detectedMode = autoMode
                skipClearForAutoMode = true
                isEnglishToItalian = autoMode
                translateAndRender(
                    sourceText = spokenText,
                    autoMode = autoMode,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Voice Translator",
            style = MaterialTheme.typography.headlineMedium,
        )

        Text(
            text = if (isEnglishToItalian) {
                "🎤 Use keyboard mic and speak in English"
            } else {
                "🎤 Usa il microfono della tastiera e parla in italiano"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
        )

        Card {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Input", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Tap mic on keyboard or type") },
                    placeholder = {
                        Text(
                            if (isEnglishToItalian) {
                                "Speak in English..."
                            } else {
                                "Parla in italiano..."
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }
        }

        Button(
            onClick = { startVoiceInput() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isListening) "Listening..." else "🎤 Speak")
        }

        if (isListening) {
            Text(
                text = "🎙️ Listening...",
                style = MaterialTheme.typography.bodyLarge,
            )
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = {
                val autoMode = detectEnglishToItalian(inputText)
                detectedMode = autoMode
                skipClearForAutoMode = true
                isEnglishToItalian = autoMode
                translateAndRender(
                    sourceText = inputText,
                    autoMode = autoMode,
                    autoSpeak = false,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isTranslating,
        ) {
            Text(if (isTranslating) "Translating..." else "Translate")
        }

        Card {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Translation", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = outputText,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Button(
            onClick = {
                val textToSpeak = extractSpeakText(outputText, detectedMode)
                tts?.language = if (detectedMode) {
                    Locale.ITALIAN
                } else {
                    Locale.ENGLISH
                }
                tts?.speak(
                    textToSpeak,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "translation_output",
                )
            },
            enabled = outputText.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("🔊 Speak")
        }
    }
}

fun buildPrompt(input: String, isEnglishToItalian: Boolean): String {
    return if (isEnglishToItalian) {
        """
        You are a translation assistant.

        Translate the following English sentence into natural Italian.

        Return ONLY:

        Italian: <translation>
        Pronunciation: <phonetic>
        Meaning: <english meaning>

        Sentence: $input
        """.trimIndent()
    } else {
        """
        You are a translation assistant.

        Translate the following Italian sentence into natural English.

        Return ONLY:

        English: <translation>

        Sentence: $input
        """.trimIndent()
    }
}

fun extractSpeakText(output: String, isEnglishToItalian: Boolean): String {
    return if (isEnglishToItalian) {
        output.substringAfter("Italian:", output)
            .substringBefore("Pronunciation:")
            .trim()
    } else {
        output.substringAfter("English:", output)
            .trim()
    }
}

fun detectEnglishToItalian(input: String): Boolean {
    val italianHints = listOf(
        "ciao", "grazie", "prego", "scusa", "questo", "questa",
        "contiene", "maiale", "carne", "latte", "formaggio",
        "dove", "quanto", "vorrei", "posso", "senza",
    )
    val lower = input.lowercase()
    return italianHints.none { lower.contains(it) }
}
