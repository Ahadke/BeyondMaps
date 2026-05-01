package com.beyondmaps.data.rag

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

class PackImportRepository(private val context: Context) {
    private val dao = BeyondMapsDatabase.getInstance(context).ragDao()

    suspend fun ensureImported(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val contentFile = File(resolveAppDataDir(), CONTENT_FILENAME)
            val embeddingsFile = File(resolveAppDataDir(), EMBEDDINGS_FILENAME)

            require(contentFile.exists()) {
                "Missing $CONTENT_FILENAME at ${contentFile.absolutePath}"
            }
            require(embeddingsFile.exists()) {
                "Missing $EMBEDDINGS_FILENAME at ${embeddingsFile.absolutePath}"
            }

            val contentBytes = contentFile.readBytes()
            val embeddingsBytes = embeddingsFile.readBytes()
            val contentHash = sha256(contentBytes)
            val embeddingsHash = sha256(embeddingsBytes)

            val contentJson = JSONObject(contentBytes.decodeToString())
            val embeddingsJson = JSONObject(embeddingsBytes.decodeToString())

            val destination = contentJson.getJSONObject("destination")
            val destinationId = destination.getString("id")
            val currentState = dao.getPackImportState(destinationId)
            if (currentState != null &&
                currentState.contentHash == contentHash &&
                currentState.embeddingsHash == embeddingsHash
            ) {
                Log.i(TAG, "Pack import skipped; hashes unchanged for $destinationId")
                return@runCatching true
            }

            val contentVersion = destination.optString("version", "unknown")
            val embeddingModel = embeddingsJson.optString("modelName", "unknown")
            val vectorSize = embeddingsJson.optInt("vectorSize", DEFAULT_VECTOR_SIZE)

            val destinationEntity = DestinationEntity(
                id = destinationId,
                city = destination.getString("city"),
                country = destination.getString("country"),
                language = destination.optString("language", "unknown"),
                version = contentVersion,
                minLat = destination.optJSONObject("bbox")?.optDouble("min_lat"),
                maxLat = destination.optJSONObject("bbox")?.optDouble("max_lat"),
                minLon = destination.optJSONObject("bbox")?.optDouble("min_lon"),
                maxLon = destination.optJSONObject("bbox")?.optDouble("max_lon"),
                downloadedAt = destination.optLong("downloadedAt"),
            )

            val chunks = parseKnowledgeChunks(contentJson.getJSONArray("knowledgeChunks"))
            val phrases = parsePhrases(contentJson.getJSONArray("phrasebook"))
            val embeddings = parseEmbeddings(
                embeddingsJson.getJSONArray("embeddings"),
                modelName = embeddingModel,
                vectorSize = vectorSize,
            )

            require(chunks.isNotEmpty()) { "knowledgeChunks is empty." }
            require(embeddings.isNotEmpty()) { "embeddings is empty." }
            require(chunks.size == embeddings.size) {
                "Chunk/embedding size mismatch: ${chunks.size} != ${embeddings.size}"
            }

            val chunkIds = chunks.mapTo(hashSetOf()) { it.id }
            embeddings.forEach { embedding ->
                require(chunkIds.contains(embedding.chunkId)) {
                    "Embedding chunkId not found in knowledge chunks: ${embedding.chunkId}"
                }
                require(embedding.vectorSize == vectorSize) {
                    "Embedding vector size mismatch for ${embedding.chunkId}"
                }
            }

            val state = PackImportStateEntity(
                destinationId = destinationId,
                contentHash = contentHash,
                embeddingsHash = embeddingsHash,
                contentVersion = contentVersion,
                embeddingModelName = embeddingModel,
                vectorSize = vectorSize,
                importedAtMs = System.currentTimeMillis(),
            )

            dao.replaceAllData(
                destination = destinationEntity,
                chunks = chunks,
                embeddings = embeddings,
                phrases = phrases,
                state = state,
            )

            Log.i(TAG, "Pack import completed. chunks=${chunks.size}, embeddings=${embeddings.size}")
            true
        }
    }

    private fun parseKnowledgeChunks(array: JSONArray): List<KnowledgeChunkEntity> {
        val out = ArrayList<KnowledgeChunkEntity>(array.length())
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            out += KnowledgeChunkEntity(
                id = item.getString("id"),
                destinationId = item.getString("destinationId"),
                category = item.optString("category", "general"),
                title = item.optString("title", ""),
                text = item.optString("text", ""),
                lat = item.optDoubleOrNull("lat"),
                lng = item.optDoubleOrNull("lng"),
                source = item.optString("source", "unknown"),
            )
        }
        return out
    }

    private fun parsePhrases(array: JSONArray): List<PhraseEntity> {
        val out = ArrayList<PhraseEntity>(array.length())
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            out += PhraseEntity(
                english = item.optString("english", ""),
                italian = item.optString("italian", ""),
                phonetic = item.optString("phonetic", ""),
                category = item.optString("category", "general"),
            )
        }
        return out
    }

    private fun parseEmbeddings(
        array: JSONArray,
        modelName: String,
        vectorSize: Int,
    ): List<EmbeddingEntity> {
        val out = ArrayList<EmbeddingEntity>(array.length())
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val vector = parseVector(item.get("vector"), vectorSize)
            out += EmbeddingEntity(
                id = item.optString("id", "emb_$i"),
                chunkId = item.getString("chunkId"),
                modelName = item.optString("modelName", modelName),
                vectorSize = vector.size,
                vectorBlob = vector.toByteArray(),
            )
        }
        return out
    }

    private fun parseVector(rawVector: Any, expectedSize: Int): FloatArray {
        val vector = when (rawVector) {
            is JSONArray -> {
                FloatArray(rawVector.length()) { idx -> rawVector.getDouble(idx).toFloat() }
            }
            is String -> {
                val trimmed = rawVector.trim().removePrefix("[").removeSuffix("]")
                if (trimmed.isBlank()) FloatArray(0)
                else trimmed.split(",").map { it.trim().toFloat() }.toFloatArray()
            }
            else -> throw IllegalArgumentException("Unsupported vector type: ${rawVector::class.java}")
        }
        require(vector.size == expectedSize) {
            "Vector size mismatch. expected=$expectedSize actual=${vector.size}"
        }
        return vector
    }

    private fun FloatArray.toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (value in this) buffer.putFloat(value)
        return buffer.array()
    }

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun resolveAppDataDir(): File {
        return context.getExternalFilesDir(null)
            ?: File("/sdcard/Android/data/${context.packageName}/files")
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return optDouble(name)
    }

    companion object {
        private const val TAG = "PackImportRepository"
        const val CONTENT_FILENAME = "florence_pack_clean.json"
        const val EMBEDDINGS_FILENAME = "florence_embeddings_all-MiniLM-L6-v2.json"
        private const val DEFAULT_VECTOR_SIZE = 384
    }
}
