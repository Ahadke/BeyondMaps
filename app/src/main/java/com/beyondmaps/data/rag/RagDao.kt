package com.beyondmaps.data.rag

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface RagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDestination(destination: DestinationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKnowledgeChunks(chunks: List<KnowledgeChunkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmbeddings(embeddings: List<EmbeddingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhrases(phrases: List<PhraseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPackImportState(state: PackImportStateEntity)

    @Query("SELECT * FROM pack_import_state WHERE destinationId = :destinationId LIMIT 1")
    suspend fun getPackImportState(destinationId: String): PackImportStateEntity?

    @Query("DELETE FROM embeddings")
    suspend fun clearEmbeddings()

    @Query("DELETE FROM knowledge_chunks")
    suspend fun clearKnowledgeChunks()

    @Query("DELETE FROM phrases")
    suspend fun clearPhrases()

    @Query("DELETE FROM destinations")
    suspend fun clearDestinations()

    @Query("DELETE FROM pack_import_state")
    suspend fun clearPackImportState()

    @Transaction
    suspend fun replaceAllData(
        destination: DestinationEntity,
        chunks: List<KnowledgeChunkEntity>,
        embeddings: List<EmbeddingEntity>,
        phrases: List<PhraseEntity>,
        state: PackImportStateEntity,
    ) {
        clearEmbeddings()
        clearKnowledgeChunks()
        clearPhrases()
        clearDestinations()
        clearPackImportState()

        upsertDestination(destination)
        upsertKnowledgeChunks(chunks)
        upsertEmbeddings(embeddings)
        upsertPhrases(phrases)
        upsertPackImportState(state)
    }
}
