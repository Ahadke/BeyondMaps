package com.beyondmaps.data.rag

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "phrases",
    indices = [Index(value = ["category"])],
)
data class PhraseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val english: String,
    val italian: String,
    val phonetic: String,
    val category: String,
)
