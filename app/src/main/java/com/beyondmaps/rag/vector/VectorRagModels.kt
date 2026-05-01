package com.beyondmaps.rag.vector

import com.google.gson.annotations.SerializedName

data class FlorencePack(
    val destination: DestinationRaw? = null,
    val knowledgeChunks: List<KnowledgeChunkRaw> = emptyList(),
    val embeddings: List<EmbeddingRaw> = emptyList(),
    val phrasebook: List<PhraseRaw> = emptyList(),
    val meta: PackMetaRaw? = null,
)

data class DestinationRaw(
    val city: String? = null,
    val country: String? = null,
    val language: String? = null,
)

data class KnowledgeChunkRaw(
    val id: String? = null,
    @SerializedName("chunkId")
    val chunkId: String? = null,
    val title: String? = null,
    val text: String? = null,
    val category: String? = null,
    val source: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val lon: Double? = null,
)

data class EmbeddingRaw(
    @SerializedName("chunkId")
    val chunkId: String? = null,
    val modelName: String? = null,
    val vector: String? = null,
)

data class PhraseRaw(
    val english: String? = null,
    val italian: String? = null,
    val phonetic: String? = null,
    val category: String? = null,
)

data class PackMetaRaw(
    val cityId: String? = null,
    val version: String? = null,
    val createdAt: String? = null,
    val totalChunks: Int? = null,
    val totalEmbeddings: Int? = null,
    val modelName: String? = null,
    val vectorSize: Int? = null,
)

data class VectorChunk(
    val id: String,
    val title: String,
    val text: String,
    val category: String,
    val source: String,
    val lat: Double?,
    val lon: Double?,
    val embedding: FloatArray,
) {
    fun hasCoordinates(): Boolean = lat != null && lon != null
}

data class LoadedVectorPack(
    val chunks: List<VectorChunk>,
    val vectorSize: Int,
    val modelName: String,
)
