package com.beyondmaps.rag.vector

import android.content.Context
import android.util.Log
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.File

class VectorPackLoader(private val context: Context) {
    fun loadVectorPack(): LoadedVectorPack {
        val file = File(context.getExternalFilesDir(null), "florence_pack.json")
        Log.d(TAG, "Vector pack path: ${file.absolutePath}")
        Log.d(TAG, "Vector pack exists: ${file.exists()}")
        Log.d(TAG, "Vector pack canRead: ${file.canRead()}")
        Log.d(TAG, "Vector pack file size: ${file.length()}")

        if (!file.exists() || !file.canRead()) {
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

            file.reader().use { fileReader ->
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
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            }

            file.reader().use { fileReader ->
                JsonReader(fileReader).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        if (reader.nextName() != "embeddings") {
                            reader.skipValue()
                            continue
                        }
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
                                    "embedding[${embeddingsCount - 1}] chunkId=${embedding.chunkId}, model=${embedding.modelName}, vectorStringLength=${embedding.vectorStringLength}",
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

                            val parsed = parseVectorString(embedding.vectorString ?: "")
                            if (embeddingsCount <= 3) {
                                Log.d(TAG, "embedding[${embeddingsCount - 1}] parsedSize=${parsed.size}")
                            }
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
                        lon = seed.lon ?: seed.lng,
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
            val chunksWithCoordinates = loadedChunks.count { it.hasCoordinates() }
            val restaurantWithCoordinates = loadedChunks.count {
                it.hasCoordinates() && normalizeCategory(it.category) == "restaurant"
            }
            val attractionWithCoordinates = loadedChunks.count {
                it.hasCoordinates() && normalizeCategory(it.category) == "attraction"
            }
            val transitWithCoordinates = loadedChunks.count {
                it.hasCoordinates() && normalizeCategory(it.category) == "transit"
            }
            Log.d(TAG, "coordinate stats total chunks=${loadedChunks.size}")
            Log.d(TAG, "coordinate stats chunks with coordinates=$chunksWithCoordinates")
            Log.d(TAG, "coordinate stats restaurant with coordinates=$restaurantWithCoordinates")
            Log.d(TAG, "coordinate stats attraction with coordinates=$attractionWithCoordinates")
            Log.d(TAG, "coordinate stats transit with coordinates=$transitWithCoordinates")
            loadedChunks
                .asSequence()
                .filter { it.hasCoordinates() }
                .take(20)
                .forEachIndexed { index, chunk ->
                    Log.d(
                        TAG,
                        "coords[$index] title=${chunk.title}, category=${chunk.category}, source=${chunk.source}, lat=${chunk.lat}, lon=${chunk.lon}",
                    )
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
        var lng: Double? = null

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
                "lng" -> lng = nextDoubleOrNull(reader)
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
            lng = lng,
        )
    }

    private fun parseEmbedding(reader: JsonReader): EmbeddingSeed {
        var chunkId: String? = null
        var modelName: String? = null
        var vectorRaw: String? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "chunkId" -> chunkId = nextStringOrNull(reader)
                "modelName" -> modelName = nextStringOrNull(reader)
                "vector" -> vectorRaw = nextStringOrNull(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return EmbeddingSeed(
            chunkId = chunkId.orEmpty().trim(),
            modelName = modelName,
            vectorString = vectorRaw,
            vectorStringLength = vectorRaw?.length ?: 0,
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

    private fun normalizeCategory(raw: String): String {
        val c = raw.lowercase()
        return when {
            c.contains("restaurant") || c.contains("cafe") || c.contains("bar") || c.contains("food") -> "restaurant"
            c.contains("transit") || c.contains("bus") || c.contains("train") || c.contains("tram") || c.contains("station") -> "transit"
            c.contains("attraction") || c.contains("museum") || c.contains("church") || c.contains("monument") || c.contains("historic") -> "attraction"
            else -> c
        }
    }

    companion object {
        private const val TAG = "BeyondMapsVectorRAG"
        private const val EXPECTED_VECTOR_SIZE = 384
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
        val lng: Double?,
        var embedding: FloatArray? = null,
    )

    private data class EmbeddingSeed(
        val chunkId: String,
        val modelName: String?,
        val vectorString: String?,
        val vectorStringLength: Int,
    )
}
