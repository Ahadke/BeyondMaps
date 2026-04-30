package com.beyondmaps

import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.beyondmaps.ai.BeyondMapsChatbot
import com.beyondmaps.ui.navigation.NavGraph
import com.beyondmaps.ui.theme.BeyondMapsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val testPrompt = "hii"
    private var chatbot: BeyondMapsChatbot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = TRANSPARENT
        window.navigationBarColor = TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            BeyondMapsTheme {
                NavGraph()
            }
        }
        runBackendOnlyChatbotTest()
    }

    override fun onDestroy() {
        chatbot?.close()
        chatbot = null
        super.onDestroy()
    }

    private fun runBackendOnlyChatbotTest() {
        Log.i(TAG, "Starting backend-only LiteRT-LM test")
        Log.i(TAG, "Test prompt: $testPrompt")

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val chatbotInstance = BeyondMapsChatbot(applicationContext)
                    chatbot = chatbotInstance

                    Log.i(TAG, "Initializing chatbot...")
                    val result = chatbotInstance.initialize()
                    if (result.isFailure) {
                        val error = result.exceptionOrNull()
                        Log.e(TAG, "Chatbot initialization failed: ${error?.message}", error)
                        return@withContext
                    }

                    Log.i(TAG, "Chatbot initialized successfully")
                    chatbotInstance.sendMessage(testPrompt).collect { chunk ->
                        if (chunk.isNotEmpty()) {
                            Log.i(TAG, "MODEL_CHUNK: $chunk")
                        }
                    }
                    Log.i(TAG, "MODEL_DONE")
                } catch (e: Throwable) {
                    Log.e(TAG, "Backend-only LiteRT-LM test failed: ${e.message}", e)
                }
            }
        }
    }

    private companion object {
        const val TAG = "BeyondMapsTest"
    }
}
