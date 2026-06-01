package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.KnowledgeChunkEntity
import me.rerere.rikkahub.data.db.entity.KnowledgeSourceAssistantEntity
import me.rerere.rikkahub.data.db.entity.KnowledgeSourceEntity

@Dao
interface KnowledgeBaseDao {
    // ---- 知识源 ----

    @Query("SELECT * FROM knowledge_sources ORDER BY created_at DESC")
    fun getAllSourcesFlow(): Flow<List<KnowledgeSourceEntity>>

    @Query("SELECT * FROM knowledge_sources ORDER BY created_at DESC")
    suspend fun getAllSources(): List<KnowledgeSourceEntity>

    /**
     * 获取通过关联表绑定到指定助理的知识源
     */
    @Query("""
        SELECT * FROM knowledge_sources
        WHERE id IN (SELECT source_id FROM knowledge_source_assistants WHERE assistant_id = :assistantId)
        ORDER BY created_at DESC
    """)
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

    // ---- 关联表（知识源 ↔ 助理 多对多）----

    @Query("SELECT assistant_id FROM knowledge_source_assistants WHERE source_id = :sourceId")
    suspend fun getAssistantIdsForSource(sourceId: String): List<String>

    @Query("""
        SELECT source_id FROM knowledge_source_assistants
        WHERE assistant_id = :assistantId
    """)
    suspend fun getSourceIdsForAssistant(assistantId: String): List<String>

    @Query("DELETE FROM knowledge_source_assistants WHERE source_id = :sourceId")
    suspend fun clearSourceAssistants(sourceId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSourceAssistants(rows: List<KnowledgeSourceAssistantEntity>)

    /**
     * 替换某个知识源绑定的助理列表（先清后插）
     */
    suspend fun replaceSourceAssistants(sourceId: String, assistantIds: List<String>) {
        clearSourceAssistants(sourceId)
        if (assistantIds.isNotEmpty()) {
            addSourceAssistants(assistantIds.map { KnowledgeSourceAssistantEntity(sourceId, it) })
        }
    }

    // ---- FTS5 全文检索（通过 Service 层直接操作 WritableDatabase）----

    /** 获取通过关联表绑定到指定助理的所有已向量化chunks */
    @Query("""
        SELECT kc.* FROM knowledge_chunks kc
        INNER JOIN knowledge_sources ks ON ks.id = kc.source_id
        WHERE kc.embedding IS NOT NULL AND kc.embedding_dim > 0
        AND kc.source_id IN (
            SELECT source_id FROM knowledge_source_assistants WHERE assistant_id = :assistantId
        )
    """)
    suspend fun getEmbeddedChunksForAssistant(assistantId: String): List<KnowledgeChunkEntity>

    @Query("SELECT * FROM knowledge_chunks WHERE embedding IS NOT NULL AND embedding_dim > 0")
    suspend fun getAllEmbeddedChunks(): List<KnowledgeChunkEntity>
}
