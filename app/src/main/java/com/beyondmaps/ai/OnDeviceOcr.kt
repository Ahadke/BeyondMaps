package com.beyondmaps.ai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class OnDeviceOcr(private val context: Context) {
    suspend fun extractText(uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val japaneseRecognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        return try {
            val latinText = latinRecognizer.process(image).await().text.trim()
            val japaneseText = japaneseRecognizer.process(image).await().text.trim()
            if (japaneseText.length > latinText.length) japaneseText else latinText
        } finally {
            latinRecognizer.close()
            japaneseRecognizer.close()
        }
    }
}
