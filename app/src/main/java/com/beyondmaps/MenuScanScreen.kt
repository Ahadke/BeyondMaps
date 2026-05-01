package com.beyondmaps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MenuScanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var modelOutput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    selectedBitmap = bitmap
                    modelOutput = ""
                    errorText = null
                    scope.launch {
                        runFastVlmScan(
                            context = context,
                            bitmap = bitmap,
                            onStart = { isLoading = true },
                            onResult = { output ->
                                modelOutput = output
                                errorText = null
                                isLoading = false
                            },
                            onError = { message ->
                                errorText = message
                                isLoading = false
                            },
                        )
                    }
                } else {
                    errorText = "Unable to decode selected image."
                }
            }
        }
    }

    ScreenContainer {
        OutlinedButton(onClick = onBack) { Text("Back") }
        Spacer(modifier = Modifier.height(20.dp))
        Text("Menu Scan", color = Color(0xFF111827), fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        Text("Upload a menu photo and run FastVLM.", color = Color(0xFF64748B), fontSize = 16.sp)
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Upload Image") }
        Spacer(modifier = Modifier.height(20.dp))
        selectedBitmap?.let { bitmap ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Selected Menu", fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
                    Spacer(modifier = Modifier.height(8.dp))
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Selected menu image",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Scanning with FastVLM...")
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
        errorText?.let {
            Text(text = it, color = Color(0xFFB91C1C), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (modelOutput.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("FastVLM Output", color = Color(0xFF111827), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(modelOutput, color = Color(0xFF334155), lineHeight = 22.sp)
                }
            }
        }
    }
}

private suspend fun runFastVlmScan(
    context: Context,
    bitmap: Bitmap,
    onStart: () -> Unit,
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
) {
    onStart()
    val externalDir = context.getExternalFilesDir(null)
    val candidateModelFiles = listOfNotNull(
        externalDir?.let { File(it, "FastVLM-0.5B.qualcomm.sm8750.litertlm") },
        externalDir?.let { File(it, "model.litertlm") },
    )
    val modelFile = candidateModelFiles.firstOrNull { it.exists() && it.isFile }
    if (modelFile == null) {
        val targetPath = externalDir?.absolutePath ?: "/sdcard/Android/data/com.beyondmaps/files"
        onError("Model not found. Please adb push FastVLM-0.5B.qualcomm.sm8750.litertlm to $targetPath")
        return
    }

    val imageFile = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.cacheDir, "menu_input_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            file
        }.getOrNull()
    }
    if (imageFile == null) {
        onError("Failed to prepare image for inference.")
        return
    }

    val prompt = "This is a photo of a restaurant menu. List every dish you can see with its name, price if visible, and any ingredients or description shown."
    try {
        val manager = LiteRTLMManager.getInstance(context)
        val ok = manager.initialize(modelFile.absolutePath, supportsImage = true, supportsAudio = false)
        if (!ok) {
            val reason = manager.lastInitError.ifBlank { "unknown error" }
            onError("FastVLM initialization failed: $reason")
            return
        }
        manager.startConversation()
        val output = StringBuilder()
        manager.sendMultimodalMessage(prompt, imageFile.absolutePath).collect { chunk ->
            output.append(chunk)
        }
        manager.cleanup()
        val text = output.toString().trim()
        if (text.isBlank()) onError("FastVLM returned blank output.") else onResult(text)
    } catch (t: Throwable) {
        Log.e("MenuScanScreen", "Inference exception", t)
        onError("Inference failed: ${t.message ?: "unknown error"}")
    }
}
