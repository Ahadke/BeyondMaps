package com.beyondmaps.rag.vector

import android.util.Log
import java.util.Locale

interface QueryEmbedder {
    val modelName: String
    val vectorSize: Int
    suspend fun embed(text: String): FloatArray
}

/**
 * Lightweight on-device query embedder:
 * builds a query vector as a weighted centroid of top lexical matches.
 *
 * This is a practical drop-in for demos when a full encoder runtime is not yet wired.
 */
class LightweightQueryEmbedder(private val chunks: List<VectorChunk>) : QueryEmbedder {
    override val modelName: String = "all-MiniLM-L6-v2"
    override val vectorSize: Int = 384

    init {
        Log.d(TAG, "Using LightweightQueryEmbedder (lexical-weighted centroid).")
    }

    override suspend fun embed(text: String): FloatArray {
        if (chunks.isEmpty()) return FloatArray(vectorSize)

        val queryTokens = tokenize(text)
        if (queryTokens.isEmpty()) {
            return globalCentroid()
        }

        data class Candidate(val chunk: VectorChunk, val score: Float)
        val top = chunks.asSequence()
            .filter { it.embedding.size == vectorSize }
            .mapNotNull { chunk ->
                val score = lexicalScore(queryTokens, chunk)
                if (score <= 0f) null else Candidate(chunk, score)
            }
            .sortedByDescending { it.score }
            .take(12)
            .toList()

        if (top.isEmpty()) {
            return globalCentroid()
        }

        val out = FloatArray(vectorSize)
        var weightSum = 0f
        for (candidate in top) {
            val weight = candidate.score
            weightSum += weight
            val emb = candidate.chunk.embedding
            for (i in 0 until vectorSize) {
                out[i] += emb[i] * weight
            }
        }
        if (weightSum <= 0f) return globalCentroid()
        for (i in 0 until vectorSize) out[i] /= weightSum

        Log.d(TAG, "LightweightQueryEmbedder query='${text.take(80)}' topMatches=${top.size}")
        return out
    }

    private fun lexicalScore(queryTokens: Set<String>, chunk: VectorChunk): Float {
        val hay = "${chunk.title} ${chunk.text} ${chunk.category}".lowercase(Locale.ROOT)
        val matched = queryTokens.count { hay.contains(it) }
        if (matched == 0) return 0f
        val base = matched.toFloat() / queryTokens.size.toFloat()
        val titleBoost = if (queryTokens.any { chunk.title.lowercase(Locale.ROOT).contains(it) }) 0.15f else 0f
        return base + titleBoost
    }

    private fun globalCentroid(): FloatArray {
        val valid = chunks.filter { it.embedding.size == vectorSize }
        if (valid.isEmpty()) return FloatArray(vectorSize)
        val out = FloatArray(vectorSize)
        for (chunk in valid) {
            for (i in 0 until vectorSize) {
                out[i] += chunk.embedding[i]
            }
        }
        val count = valid.size.toFloat()
        for (i in 0 until vectorSize) out[i] /= count
        return out
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .toSet()

    companion object {
        private const val TAG = "BeyondMapsVectorRAG"
    }
}
