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

        val selectedFromKeyword = when {
            q.contains("gelato") -> chunks.firstOrNull { it.containsInTitleOrText("gelato") }
            q.contains("pizza") -> chunks.firstOrNull {
                it.category.lowercase() == "restaurant" && it.containsInTitleOrText("pizza")
            }
            q.contains("duomo") -> chunks.firstOrNull {
                it.category.lowercase() == "attraction" && it.containsInTitleOrText("duomo")
            }
            q.contains("ticket") || q.contains("affordable public transport") -> {
                chunks.firstOrNull { it.category.lowercase() == "transit_guide" }
                    ?: chunks.firstOrNull {
                        it.category.lowercase() == "transit" &&
                            (it.containsInTitleOrText("ticket") || it.containsInTitleOrText("price"))
                    }
            }
            else -> null
        }

        val category = when {
            q.contains("gelato") || q.contains("eat") || q.contains("restaurant") || q.contains("food") -> "restaurant"
            q.contains("tram") || q.contains("bus") || q.contains("train") || q.contains("stop") || q.contains("transit") -> "transit"
            q.contains("duomo") || q.contains("see") || q.contains("visit") || q.contains("museum") || q.contains("attraction") -> "attraction"
            q.contains("medicine") || q.contains("pharmacy") || q.contains("doctor") || q.contains("help") || q.contains("emergency") -> "phrase"
            else -> "restaurant"
        }

        val selected = selectedFromKeyword
            ?: chunks.firstOrNull { it.category.lowercase() == category }
            ?: chunks.firstOrNull()

        Log.d(
            TAG,
            "FakeQueryEmbedder selected chunk title=${selected?.title}, category=${selected?.category}, source=${selected?.source}",
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

    private fun VectorChunk.containsInTitleOrText(needle: String): Boolean {
        val n = needle.lowercase()
        return title.lowercase().contains(n) || text.lowercase().contains(n)
    }

    companion object {
        private const val TAG = "BeyondMapsVectorRAG"
    }
}
