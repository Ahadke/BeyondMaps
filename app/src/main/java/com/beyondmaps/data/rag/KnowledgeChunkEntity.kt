package com.beyondmaps.data.rag

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "knowledge_chunks",
    indices = [
        Index(value = ["destinationId"]),
        Index(value = ["category"]),
        Index(value = ["source"]),
    ],
)
data class KnowledgeChunkEntity(
    @PrimaryKey val id: String,
    val destinationId: String,
    val category: String,
    val title: String,
    val text: String,
    val lat: Double?,
    val lng: Double?,
    val source: String,
)
