package com.beyondmaps.data.rag

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "embeddings",
    indices = [
        Index(value = ["chunkId"], unique = true),
        Index(value = ["modelName"]),
    ],
)
data class EmbeddingEntity(
    @PrimaryKey val id: String,
    val chunkId: String,
    val modelName: String,
    val vectorSize: Int,
    val vectorBlob: ByteArray,
)
