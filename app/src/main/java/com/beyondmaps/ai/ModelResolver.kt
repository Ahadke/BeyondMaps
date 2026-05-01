package com.beyondmaps.ai

import android.content.Context
import java.io.File

/**
 * Resolves LiteRT-LM model files from app external storage (same pattern for all models).
 * Place files under Android/data/<package>/files/ so they persist across APK rebuilds.
 */
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

    /**
     * FastVLM for on-device vision/OCR. Tries common filenames in order.
     */
    fun fastVlmModelExists(): Boolean = resolveFastVlmFile() != null

    fun getFastVlmModelPath(): String {
        val file = resolveFastVlmFile()
            ?: throw IllegalStateException(
                "FastVLM model is missing. Place one of ${FASTVLM_CANDIDATES.joinToString()} under ${resolveAppSpecificDir().absolutePath}"
            )
        return file.absolutePath
    }

    fun expectedFastVlmPathHint(): String =
        "${resolveAppSpecificDir().absolutePath}/ (${FASTVLM_CANDIDATES.joinToString(" or ")})"

    private fun resolveFastVlmFile(): File? {
        val dir = resolveAppSpecificDir()
        for (name in FASTVLM_CANDIDATES) {
            val f = File(dir, name)
            if (f.exists() && f.canRead() && f.length() > 0) return f
        }
        return null
    }

    private fun resolveAppSpecificDir(): File {
        return context.getExternalFilesDir(null)
            ?: File("/sdcard/Android/data/${context.packageName}/files")
    }

    private companion object {
        const val MODEL_FILENAME = "model.litertlm"
        val FASTVLM_CANDIDATES = listOf(
            // Hugging Face Qualcomm NPU build (e.g. S25-class): prefer this name on disk
            "FastVLM-0.5B.qualcomm.sm8750.litertlm",
            "FastVLM-0.5B.sm8850.litertlm",
            "FastVLM-0.5B.litertlm",
            "fastvlm.litertlm",
        )
        const val LEGACY_MODEL_PATH =
            "/sdcard/Android/data/com.example.qnn_litertlm_gemma/files/model.litertlm"
    }
}
