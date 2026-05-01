package com.beyondmaps

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import kotlin.math.sqrt

data class RagChunk(
    val id: String,
    val title: String,
    val text: String,
    val category: String,
    val source: String
)

class RagEngine(private val context: Context) {

    private val chunks = mutableListOf<RagChunk>()
    private val vectors = mutableListOf<FloatArray>()
    private var isLoaded = false

    fun loadPack() {
        if (isLoaded) return

        try {
            // Read florence_pack.json from phone storage
            val file = java.io.File(
                "/sdcard/Android/data/com.beyondmaps/files/florence_pack.json"
            )

            if (!file.exists()) {
                android.util.Log.e("RagEngine", "florence_pack.json not found")
                return
            }

            android.util.Log.d("RagEngine", "Loading florence_pack.json...")
            val jsonText = file.readText()
            val pack = JSONObject(jsonText)

            // Load knowledge chunks
            val chunksArray = pack.getJSONArray("knowledgeChunks")
            val embeddingsArray = pack.getJSONArray("embeddings")

            android.util.Log.d("RagEngine", "Chunks: ${chunksArray.length()}")

            for (i in 0 until chunksArray.length()) {
                val chunk = chunksArray.getJSONObject(i)
                chunks.add(
                    RagChunk(
                        id       = chunk.optString("id"),
                        title    = chunk.optString("title"),
                        text     = chunk.optString("text"),
                        category = chunk.optString("category"),
                        source   = chunk.optString("source")
                    )
                )
            }

            // Load embeddings
            for (i in 0 until embeddingsArray.length()) {
                val emb = embeddingsArray.getJSONObject(i)
                val vectorStr = emb.getString("vector")
                val vectorArray = JSONArray(vectorStr)
                val vector = FloatArray(vectorArray.length()) {
                    vectorArray.getDouble(it).toFloat()
                }
                vectors.add(vector)
            }

            isLoaded = true
            android.util.Log.d("RagEngine", "Loaded ${chunks.size} chunks and ${vectors.size} vectors")

        } catch (e: Exception) {
            android.util.Log.e("RagEngine", "Error loading pack: ${e.message}")
        }
    }

    // Cosine similarity between two vectors
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return if (normA == 0f || normB == 0f) 0f
        else dot / (sqrt(normA) * sqrt(normB))
    }

    // Search for top K relevant chunks
    fun search(queryVector: FloatArray, topK: Int = 5): List<RagChunk> {
        if (!isLoaded || chunks.isEmpty()) {
            return emptyList()
        }

        // Score every chunk
        val scores = vectors.mapIndexed { index, vector ->
            index to cosineSimilarity(queryVector, vector)
        }

        // Return top K
        return scores
            .sortedByDescending { it.second }
            .take(topK)
            .map { chunks[it.first] }
    }

    // Simple keyword search fallback
    fun keywordSearch(query: String, topK: Int = 5): List<RagChunk> {
        val words = query.lowercase().split(" ")
        return chunks
            .map { chunk ->
                val score = words.count { word ->
                    chunk.text.lowercase().contains(word) ||
                    chunk.title.lowercase().contains(word)
                }
                chunk to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }

    fun isReady() = isLoaded
    fun getChunkCount() = chunks.size
}