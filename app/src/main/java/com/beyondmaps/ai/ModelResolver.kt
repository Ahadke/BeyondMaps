package com.beyondmaps.ai

import android.content.Context
import java.io.File

/**
 * Resolves LiteRT-LM model files from app external storage (same pattern for all models).
 * Place files under Android/data/<package>/files/ so they persist across APK rebuilds.
 */
class ModelResolver(private val context: Context) {

    private fun modelDir(): File {
        return context.getExternalFilesDir(null)
            ?: File("/sdcard/Android/data/${context.packageName}/files")
    }

    fun modelExists(): Boolean {
        val modelFile = getModelFile()
        return modelFile.exists() && modelFile.canRead() && modelFile.length() > 0
    }

    fun getModelPath(): String {
        val modelFile = getModelFile()
        if (!modelExists()) {
            throw IllegalStateException(
                "LiteRT-LM model is missing. Expected $MODEL_FILENAME at ${modelFile.absolutePath}"
            )
        }
        return modelFile.absolutePath
    }

    fun expectedPath(): String = getModelFile().absolutePath

    /**
     * Primary travel/chat model (e.g. Gemma packaged as model.litertlm).
     */
    private fun getModelFile(): File = File(modelDir(), MODEL_FILENAME)

    /**
     * FastVLM for on-device vision/OCR. Tries common filenames in order.
     */
    fun fastVlmModelExists(): Boolean = resolveFastVlmFile() != null

    fun getFastVlmModelPath(): String {
        val file = resolveFastVlmFile()
            ?: throw IllegalStateException(
                "FastVLM model is missing. Place one of ${FASTVLM_CANDIDATES.joinToString()} under ${modelDir().absolutePath}"
            )
        return file.absolutePath
    }

    fun expectedFastVlmPathHint(): String =
        "${modelDir().absolutePath}/ (${FASTVLM_CANDIDATES.joinToString(" or ")})"

    private fun resolveFastVlmFile(): File? {
        val dir = modelDir()
        for (name in FASTVLM_CANDIDATES) {
            val f = File(dir, name)
            if (f.exists() && f.canRead() && f.length() > 0) return f
        }
        return null
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
    }
}
