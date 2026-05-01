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
                "LiteRT-LM model is missing. Expected model.litertlm at ${modelFile.absolutePath}. " +
                    "If your source file is in another app folder, copy it into this app's folder first."
            )
        }
        return modelFile.absolutePath
    }

    fun expectedPath(): String = getModelFile().absolutePath

    private fun getModelFile(): File {
        val appSpecificModel = File(resolveAppSpecificDir(), MODEL_FILENAME)
        if (appSpecificModel.exists() && appSpecificModel.canRead() && appSpecificModel.length() > 0) {
            return appSpecificModel
        }

        val legacyModel = File(LEGACY_MODEL_PATH)
        if (legacyModel.exists() && legacyModel.canRead() && legacyModel.length() > 0) {
            return legacyModel
        }

        return appSpecificModel
    }

    private fun resolveAppSpecificDir(): File {
        return context.getExternalFilesDir(null)
            ?: File("/sdcard/Android/data/${context.packageName}/files")
    }

    private companion object {
        const val MODEL_FILENAME = "model.litertlm"
        const val LEGACY_MODEL_PATH =
            "/sdcard/Android/data/com.example.qnn_litertlm_gemma/files/model.litertlm"
    }
}
