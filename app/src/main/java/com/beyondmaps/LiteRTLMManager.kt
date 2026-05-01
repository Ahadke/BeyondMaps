package com.beyondmaps

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Singleton manager for LiteRT-LM Engine.
 * Optimised for Qualcomm Snapdragon 8 Elite (SM8750).
 */
class LiteRTLMManager private constructor(private val context: Context) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    
    @Volatile
    var initialized: Boolean = false
        private set

    @Volatile
    var lastInitError: String = ""
        private set

    companion object {
        private const val TAG = "LiteRTLMManager"

        @Volatile
        private var INSTANCE: LiteRTLMManager? = null

        fun getInstance(context: Context): LiteRTLMManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LiteRTLMManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun extractText(message: Message): String {
            return message.contents.contents.filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
        }
    }

    /**
     * Entry point for initialization.
     */
    suspend fun initialize(modelPath: String, supportsImage: Boolean, supportsAudio: Boolean): Boolean = 
        withContext(Dispatchers.IO) {
            Log.i(TAG, "== STARTING INITIALIZATION ==")
            if (initialized) cleanup()
            lastInitError = ""

            val backends = resolveBackendOrder(modelPath)
            val failures = mutableListOf<String>()

            for (name in backends) {
                try {
                    Log.i(TAG, "Trying backend: $name")
                    val result = safeNativeInit(modelPath, name, supportsImage, supportsAudio)
                    if (result) {
                        initialized = true
                        Log.i(TAG, "Init SUCCESS: $name")
                        return@withContext true
                    }
                    if (lastInitError.isNotBlank()) failures += "[$name] $lastInitError"
                } catch (e: Throwable) {
                    val detail = describeInitFailure(modelPath, name, e)
                    failures += detail
                    Log.e(TAG, "Backend $name threw exception", e)
                }
            }

            lastInitError = failures.joinToString("\n")
            Log.e(TAG, "All backends failed initialization.\n$lastInitError")
            false
        }

    /**
     * Paths like *.qualcomm.* / *sm8750* are compiled for Qualcomm NPU graphs; the CPU executor
     * typically fails with "Input tensor not found". Try NPU first for those.
     */
    private fun modelLooksQualcommNpu(modelPath: String): Boolean {
        val p = modelPath.lowercase()
        return p.contains("qualcomm") || p.contains("sm8750") || p.contains("qnn")
    }

    private fun resolveBackendOrder(modelPath: String): List<String> {
        return when {
            modelLooksQualcommNpu(modelPath) -> {
                Log.i(TAG, "Model path looks Qualcomm-targeted; backend order: NPU, then CPU (CPU may not load this artifact).")
                listOf("NPU", "CPU")
            }
            isSm8750() -> {
                Log.i(TAG, "SM8750 + generic model path; trying CPU before NPU.")
                listOf("CPU", "NPU")
            }
            else -> listOf("CPU")
        }
    }

    private fun describeInitFailure(modelPath: String, backend: String, e: Throwable): String {
        val msg = e.message ?: e.javaClass.simpleName
        val hint = buildString {
            if (backend == "CPU" && msg.contains("Input tensor not found", ignoreCase = true)) {
                append(" Hint: This .litertlm is likely a Qualcomm/NPU build; use an NPU-capable device and matching QNN stack, or a CPU/generic model artifact.")
            }
            if (backend == "NPU" && (msg.contains("INTERNAL", ignoreCase = true) || msg.contains("litert_compiled_model", ignoreCase = true))) {
                append(" Hint: Align litertlm-android, qnn-litert-delegate, and jniLibs QNN .so files with the QAIRT/SDK version used to compile the model; see LiteRT-LM + Qualcomm release notes.")
            }
        }
        return "[$backend] $msg$hint"
    }

    /** Runs native Engine.initialize(); JNI SIGSEGV is not catchable here (process dies). */
    private fun safeNativeInit(path: String, backendName: String, img: Boolean, audio: Boolean): Boolean {
        val modelFile = File(path)
        if (!modelFile.exists()) {
            lastInitError = "Model file missing"
            return false
        }

        val libDir = context.applicationInfo.nativeLibraryDir
        Log.d(TAG, "App Lib Dir: $libDir")

        // 1. Environment Setup (Only for NPU)
        if (backendName == "NPU") {
            val hasDispatch =
                File(libDir, "libLiteRtDispatch.so").exists() ||
                    File(libDir, "libLiteRtDispatch_Qualcomm.so").exists()
            if (!hasDispatch) {
                Log.e(TAG, "No LiteRT dispatch .so in $libDir (expected libLiteRtDispatch.so or libLiteRtDispatch_Qualcomm.so)")
                lastInitError = "Native dispatch library missing"
                return false
            }
            
            try {
                android.system.Os.setenv("LD_LIBRARY_PATH", libDir, true)
                android.system.Os.setenv("ADSP_LIBRARY_PATH", libDir, true)
                android.system.Os.setenv("CDSP_LIBRARY_PATH", libDir, true)
                Log.d(TAG, "Env variables set for NPU (LD_LIBRARY_PATH, ADSP/CDSP_LIBRARY_PATH)")
            } catch (e: Exception) {
                Log.w(TAG, "Env set failed (non-fatal): ${e.message}")
            }
        }

        // 2. Create Config
        Log.d(TAG, "Creating Backend objects...")
        val backend = if (backendName == "NPU") Backend.NPU(libDir) else Backend.CPU()
        val vision = if (img) (if (backendName == "NPU") Backend.NPU(libDir) else Backend.CPU()) else null
        
        val config = EngineConfig(
            modelPath = path,
            backend = backend,
            visionBackend = vision,
            audioBackend = if (audio) Backend.CPU() else null,
            maxNumTokens = 1024,
            maxNumImages = if (img) 1 else 0,
            cacheDir = context.cacheDir.path
        )

        // 3. Initialize Engine
        Log.i(TAG, "Calling native Engine.initialize()...")
        val candidate = Engine(config)
        candidate.initialize() 
        
        Log.d(TAG, "Verifying engine with conversation...")
        val test = candidate.createConversation(ConversationConfig(null))
        test.close()

        this.engine = candidate
        return true
    }

    private fun isSm8750(): Boolean {
        val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else ""
        val combined = "$soc ${Build.BOARD} ${Build.HARDWARE}".lowercase()
        val match = combined.contains("sm8750")
        Log.d(TAG, "Hardware check: '$combined' -> SM8750 (NPU candidate): $match")
        return match
    }

    fun startConversation() {
        check(initialized) { "Engine not initialized" }
        conversation = engine?.createConversation(ConversationConfig(null))
    }

    fun sendMultimodalMessage(text: String, imagePath: String? = null): Flow<String> {
        val conv = conversation ?: run {
            startConversation()
            conversation!!
        }

        val parts = mutableListOf<Content>()
        imagePath?.takeIf { it.isNotEmpty() }?.let {
            parts.add(Content.ImageFile(it))
        }
        parts.add(Content.Text(text))

        return conv.sendMessageAsync(Contents.of(*parts.toTypedArray())).map { extractText(it) }
    }

    fun cleanup() {
        Log.i(TAG, "Cleaning up...")
        try {
            conversation?.close()
            engine?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup error", e)
        }
        conversation = null
        engine = null
        initialized = false
    }
}
