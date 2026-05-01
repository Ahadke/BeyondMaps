package com.beyondmaps.rag.vector

import android.util.Log

interface QueryEmbedder {
    val modelName: String
    val vectorSize: Int
    suspend fun embed(text: String): FloatArray
}

class FakeQueryEmbedder(private val chunks: List<VectorChunk>) : QueryEmbedder {
    override val modelName: String = "all-MiniLM-L6-v2"
    override val vectorSize: Int = 384

    init {
        Log.d(TAG, "Using FakeQueryEmbedder. Replace with real embedding model later.")
    }

    override suspend fun embed(text: String): FloatArray {
        val q = text.lowercase()
        val category = when {
            q.contains("gelato") || q.contains("eat") || q.contains("restaurant") || q.contains("food") -> "restaurant"
            q.contains("tram") || q.contains("bus") || q.contains("train") || q.contains("stop") || q.contains("transit") -> "transit"
            q.contains("duomo") || q.contains("see") || q.contains("visit") || q.contains("museum") || q.contains("attraction") -> "attraction"
            q.contains("medicine") || q.contains("pharmacy") || q.contains("doctor") || q.contains("help") || q.contains("emergency") -> "phrase"
            else -> "restaurant"
        }

        val selected = chunks.firstOrNull { it.category.lowercase() == category }
            ?: chunks.firstOrNull()

        Log.d(
            TAG,
            "FakeQueryEmbedder selected category=$category chunk=${selected?.title} chunkCategory=${selected?.category}",
        )

        if (selected == null) {
            Log.e(TAG, "BUG: FakeQueryEmbedder could not select any chunk")
            return FloatArray(vectorSize)
        }
        if (selected.embedding.isEmpty()) {
            Log.e(TAG, "BUG: FakeQueryEmbedder selected empty embedding for chunk=${selected.title}")
            return FloatArray(vectorSize)
        }
        return selected.embedding
    }

    companion object {
        private const val TAG = "BeyondMapsVectorRAG"
    }
}
