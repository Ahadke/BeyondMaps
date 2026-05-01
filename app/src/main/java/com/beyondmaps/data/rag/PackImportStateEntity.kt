package com.beyondmaps.data.rag

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pack_import_state")
data class PackImportStateEntity(
    @PrimaryKey val destinationId: String,
    val contentHash: String,
    val embeddingsHash: String,
    val contentVersion: String,
    val embeddingModelName: String,
    val vectorSize: Int,
    val importedAtMs: Long,
)
