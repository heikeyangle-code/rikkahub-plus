package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.KnowledgeChunkEntity
import me.rerere.rikkahub.data.db.entity.KnowledgeSourceEntity

@Dao
interface KnowledgeBaseDao {
    // ---- 知识源 ----

    @Query("SELECT * FROM knowledge_sources ORDER BY created_at DESC")
    fun getAllSourcesFlow(): Flow<List<KnowledgeSourceEntity>>

    @Query("SELECT * FROM knowledge_sources ORDER BY created_at DESC")
    suspend fun getAllSources(): List<KnowledgeSourceEntity>

    @Query("SELECT * FROM knowledge_sources WHERE assistant_id = :assistantId OR assistant_id IS NULL ORDER BY created_at DESC")
    fun getSourcesForAssistantFlow(assistantId: String): Flow<List<KnowledgeSourceEntity>>

    @Query("SELECT * FROM knowledge_sources WHERE id = :id")
    suspend fun getSourceById(id: String): KnowledgeSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: KnowledgeSourceEntity)

    @Query("DELETE FROM knowledge_sources WHERE id = :id")
    suspend fun deleteSource(id: String)

    // ---- 知识分块 ----

    @Query("SELECT * FROM knowledge_chunks WHERE source_id = :sourceId ORDER BY chunk_index")
    suspend fun getChunksBySource(sourceId: String): List<KnowledgeChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<KnowledgeChunkEntity>)

    @Query("DELETE FROM knowledge_chunks WHERE source_id = :sourceId")
    suspend fun deleteChunksBySource(sourceId: String)

    @Query("SELECT * FROM knowledge_chunks WHERE id = :id")
    suspend fun getChunkById(id: String): KnowledgeChunkEntity?

    @Query("SELECT count(*) FROM knowledge_chunks WHERE source_id = :sourceId")
    suspend fun getChunkCount(sourceId: String): Int

    // ---- FTS5 全文检索 ----

    @Query("""
        SELECT kc.* FROM knowledge_fts kf
        INNER JOIN knowledge_chunks kc ON kc.id = kf.chunk_id
        WHERE kf.text MATCH :query
        ORDER BY rank
        LIMIT :limit
    """)
    suspend fun searchFts(query: String, limit: Int = 10): List<KnowledgeChunkEntity>

    // ---- 嵌入向量检索（全表加载后内存计算） ----

    @Query("SELECT * FROM knowledge_chunks WHERE embedding IS NOT NULL AND embedding_dim > 0")
    suspend fun getAllEmbeddedChunks(): List<KnowledgeChunkEntity>

    @Query("""
        SELECT * FROM knowledge_chunks 
        WHERE embedding IS NOT NULL AND embedding_dim > 0
        AND source_id IN (SELECT id FROM knowledge_sources WHERE assistant_id = :assistantId OR assistant_id IS NULL)
    """)
    suspend fun getEmbeddedChunksForAssistant(assistantId: String): List<KnowledgeChunkEntity>
}
