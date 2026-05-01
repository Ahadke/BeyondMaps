package com.beyondmaps.data.rag

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DestinationEntity::class,
        KnowledgeChunkEntity::class,
        EmbeddingEntity::class,
        PhraseEntity::class,
        PackImportStateEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class BeyondMapsDatabase : RoomDatabase() {
    abstract fun ragDao(): RagDao

    companion object {
        @Volatile
        private var INSTANCE: BeyondMapsDatabase? = null

        fun getInstance(context: Context): BeyondMapsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BeyondMapsDatabase::class.java,
                    "beyondmaps.db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}
