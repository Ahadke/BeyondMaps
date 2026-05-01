package com.beyondmaps.rag.vector

import android.content.Context
import android.util.Log
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.File

class VectorPackLoader(private val context: Context) {
    fun loadVectorPack(): LoadedVectorPack {
        val baseDir = context.getExternalFilesDir(null)
        val packFile = File(baseDir, PACK_FILE_NAME)
        val embeddingsFile = File(baseDir, EMBEDDINGS_FILE_NAME)
        val legacyPackFile = File(baseDir, LEGACY_PACK_FILE_NAME)
        val useLegacySingleFile = !packFile.exists() && !embeddingsFile.exists() && legacyPackFile.exists()

        Log.d(TAG, "Pack file path: ${packFile.absolutePath}")
        Log.d(TAG, "Pack file exists: ${packFile.exists()} size=${packFile.length()}")
        Log.d(TAG, "Embeddings file path: ${embeddingsFile.absolutePath}")
        Log.d(TAG, "Embeddings file exists: ${embeddingsFile.exists()} size=${embeddingsFile.length()}")
        if (useLegacySingleFile) {
            Log.w(TAG, "Using legacy single-file vector pack: ${legacyPackFile.absolutePath}")
        }

        if (!useLegacySingleFile && (!packFile.exists() || !packFile.canRead() || !embeddingsFile.exists() || !embeddingsFile.canRead())) {
            Log.w(TAG, "Split RAG files are unavailable/read-protected")
            return LoadedVectorPack(chunks = emptyList(), vectorSize = EXPECTED_VECTOR_SIZE, modelName = "")
        }

        try {
            val chunkSeedsById = linkedMapOf<String, ChunkSeed>()
            var vectorSizeFromMeta: Int? = null
            var modelNameFromMeta: String? = null
            var knowledgeChunksCount = 0
            var embeddingsCount = 0
            var phrasebookCount = 0
            var skippedMissingChunk = 0
            var skippedBadVector = 0
            var chunksWithEmbeddings = 0
            var modelNameFromEmbeddings: String? = null

            val chunkSourceFile = if (useLegacySingleFile) legacyPackFile else packFile
            chunkSourceFile.reader().use { fileReader ->
                JsonReader(fileReader).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "meta" -> {
                                val meta = parseMeta(reader)
                                vectorSizeFromMeta = meta.first
                                modelNameFromMeta = meta.second
                            }
                            "knowledgeChunks" -> {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    val seed = parseKnowledgeChunkSeed(reader)
                                    knowledgeChunksCount++
                                    chunkSeedsById[seed.id] = seed
                                }
                                reader.endArray()
                            }
                            "phrasebook" -> {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    phrasebookCount++
                                    reader.skipValue()
                                }
                                reader.endArray()
                            }
                            "vectorSize" -> vectorSizeFromMeta = nextIntOrNull(reader)
                            "modelName" -> modelNameFromMeta = nextStringOrNull(reader)
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            }

            val embeddingSourceFile = if (useLegacySingleFile) legacyPackFile else embeddingsFile
            embeddingSourceFile.reader().use { fileReader ->
                JsonReader(fileReader).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "modelName" -> {
                                modelNameFromEmbeddings = nextStringOrNull(reader) ?: modelNameFromEmbeddings
                            }
                            "vectorSize" -> {
                                vectorSizeFromMeta = nextIntOrNull(reader) ?: vectorSizeFromMeta
                            }
                            "embeddings" -> {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    embeddingsCount++
                                    val embedding = parseEmbedding(reader)
                                    if (modelNameFromEmbeddings.isNullOrBlank() && !embedding.modelName.isNullOrBlank()) {
                                        modelNameFromEmbeddings = embedding.modelName
                                    }

                                    if (embeddingsCount <= 3) {
                                        Log.d(
                                            TAG,
                                            "embedding[${embeddingsCount - 1}] chunkId=${embedding.chunkId}, model=${embedding.modelName}, vectorSize=${embedding.vector.size}",
                                        )
                                    }

                                    val chunkId = embedding.chunkId
                                    if (chunkId.isBlank()) {
                                        skippedMissingChunk++
                                        continue
                                    }

                                    val seed = chunkSeedsById[chunkId]
                                    if (seed == null) {
                                        skippedMissingChunk++
                                        continue
                                    }

                                    val parsed = embedding.vector
                                    if (parsed.size != EXPECTED_VECTOR_SIZE) {
                                        skippedBadVector++
                                        continue
                                    }

                                    if (seed.embedding == null) {
                                        chunksWithEmbeddings++
                                    }
                                    seed.embedding = parsed
                                }
                                reader.endArray()
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            }

            val loadedChunks = chunkSeedsById.values
                .asSequence()
                .filter { it.embedding?.size == EXPECTED_VECTOR_SIZE }
                .map { seed ->
                    VectorChunk(
                        id = seed.id,
                        title = seed.title,
                        text = seed.text,
                        category = seed.category,
                        source = seed.source,
                        lat = seed.lat,
                        lon = seed.lon,
                        embedding = seed.embedding ?: FloatArray(0),
                    )
                }
                .toList()

            Log.d(TAG, "knowledgeChunks seen=$knowledgeChunksCount")
            Log.d(TAG, "embeddings seen=$embeddingsCount")
            Log.d(TAG, "phrasebook count=$phrasebookCount")
            Log.d(TAG, "chunks with embeddings=$chunksWithEmbeddings")
            Log.d(TAG, "skipped missing chunk=$skippedMissingChunk")
            Log.d(TAG, "skipped bad vector=$skippedBadVector")
            loadedChunks.take(3).forEachIndexed { index, chunk ->
                Log.d(TAG, "loaded[$index] title=${chunk.title}, embeddingSize=${chunk.embedding.size}")
            }

            val vectorSize = vectorSizeFromMeta ?: EXPECTED_VECTOR_SIZE
            val modelName = modelNameFromMeta ?: modelNameFromEmbeddings ?: ""
            return LoadedVectorPack(
                chunks = loadedChunks,
                vectorSize = vectorSize,
                modelName = modelName,
            )
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM while loading vector pack", oom)
            throw oom
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load vector pack", e)
            return LoadedVectorPack(chunks = emptyList(), vectorSize = EXPECTED_VECTOR_SIZE, modelName = "")
        }
    }

    private fun parseMeta(reader: JsonReader): Pair<Int?, String?> {
        var vectorSize: Int? = null
        var modelName: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "vectorSize" -> {
                    vectorSize = when (reader.peek()) {
                        JsonToken.NULL -> {
                            reader.nextNull()
                            null
                        }
                        JsonToken.NUMBER -> reader.nextInt()
                        else -> {
                            reader.skipValue()
                            null
                        }
                    }
                }
                "modelName" -> modelName = nextStringOrNull(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return vectorSize to modelName
    }

    private fun parseKnowledgeChunkSeed(reader: JsonReader): ChunkSeed {
        var id: String? = null
        var chunkId: String? = null
        var title = ""
        var text = ""
        var category = ""
        var source = ""
        var lat: Double? = null
        var lon: Double? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = nextStringOrNull(reader)
                "chunkId" -> chunkId = nextStringOrNull(reader)
                "title" -> title = nextStringOrNull(reader).orEmpty()
                "text" -> text = nextStringOrNull(reader).orEmpty()
                "category" -> category = nextStringOrNull(reader).orEmpty()
                "source" -> source = nextStringOrNull(reader).orEmpty()
                "lat" -> lat = nextDoubleOrNull(reader)
                "lon" -> lon = nextDoubleOrNull(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val finalId = (chunkId ?: id).orEmpty().trim()
        return ChunkSeed(
            id = if (finalId.isBlank()) "chunk_${System.nanoTime()}" else finalId,
            title = title,
            text = text,
            category = category,
            source = source,
            lat = lat,
            lon = lon,
        )
    }

    private fun parseEmbedding(reader: JsonReader): EmbeddingSeed {
        var chunkId: String? = null
        var modelName: String? = null
        var vector = FloatArray(0)

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "chunkId" -> chunkId = nextStringOrNull(reader)
                "modelName" -> modelName = nextStringOrNull(reader)
                "vector" -> vector = parseVector(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return EmbeddingSeed(
            chunkId = chunkId.orEmpty().trim(),
            modelName = modelName,
            vector = vector,
        )
    }

    private fun nextStringOrNull(reader: JsonReader): String? =
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            null
        } else {
            reader.nextString()
        }

    private fun nextDoubleOrNull(reader: JsonReader): Double? =
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            null
        } else {
            reader.nextDouble()
        }

    private fun nextIntOrNull(reader: JsonReader): Int? =
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            null
        } else {
            reader.nextInt()
        }

    private fun parseVector(reader: JsonReader): FloatArray {
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                FloatArray(0)
            }
            JsonToken.BEGIN_ARRAY -> {
                val values = ArrayList<Float>(EXPECTED_VECTOR_SIZE)
                reader.beginArray()
                while (reader.hasNext()) {
                    values += when (reader.peek()) {
                        JsonToken.NUMBER -> reader.nextDouble().toFloat()
                        JsonToken.STRING -> reader.nextString().toFloatOrNull() ?: 0f
                        else -> {
                            reader.skipValue()
                            0f
                        }
                    }
                }
                reader.endArray()
                values.toFloatArray()
            }
            JsonToken.STRING -> parseVectorString(reader.nextString())
            else -> {
                reader.skipValue()
                FloatArray(0)
            }
        }
    }

    private fun parseVectorString(vectorStr: String): FloatArray {
        if (vectorStr.isBlank()) return FloatArray(0)
        return vectorStr
            .trim()
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .mapNotNull { it.trim().toFloatOrNull() }
            .toFloatArray()
    }

    companion object {
        private const val TAG = "BeyondMapsVectorRAG"
        private const val EXPECTED_VECTOR_SIZE = 384
        private const val PACK_FILE_NAME = "florence_pack_clean.json"
        private const val EMBEDDINGS_FILE_NAME = "florence_embeddings_all-MiniLM-L6-v2.json"
        private const val LEGACY_PACK_FILE_NAME = "florence_pack.json"
    }

    fun loadPack(): List<VectorChunk> = loadVectorPack().chunks

    private data class ChunkSeed(
        val id: String,
        val title: String,
        val text: String,
        val category: String,
        val source: String,
        val lat: Double?,
        val lon: Double?,
        var embedding: FloatArray? = null,
    )

    private data class EmbeddingSeed(
        val chunkId: String,
        val modelName: String?,
        val vector: FloatArray,
    )
}
