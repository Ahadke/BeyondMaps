package com.beyondmaps.ai

import android.content.Context
import java.io.File

class ModelResolver(private val context: Context) {

    fun modelExists(): Boolean {
        val modelFile = getModelFile()
        return modelFile.exists() && modelFile.canRead() && modelFile.length() > 0
    }

    fun getModelPath(): String {
        val modelFile = getModelFile()
        if (!modelExists()) {
            throw IllegalStateException(
                "LiteRT-LM model is missing. Expected model.litertlm at ${modelFile.absolutePath}"
            )
        }
        return modelFile.absolutePath
    }

    fun expectedPath(): String = getModelFile().absolutePath

    private fun getModelFile(): File {
        val modelDir = context.getExternalFilesDir(null)
            ?: File("/sdcard/Android/data/${context.packageName}/files")
        return File(modelDir, MODEL_FILENAME)
    }

    private companion object {
        const val MODEL_FILENAME = "model.litertlm"
    }
}
