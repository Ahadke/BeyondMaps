package com.beyondmaps.data.rag

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "destinations")
data class DestinationEntity(
    @PrimaryKey val id: String,
    val city: String,
    val country: String,
    val language: String,
    val version: String,
    val minLat: Double?,
    val maxLat: Double?,
    val minLon: Double?,
    val maxLon: Double?,
    val downloadedAt: Long?,
)
