package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "knowledge_sources")
data class KnowledgeSourceEntity(
    @PrimaryKey
    @ColumnInfo("id")
    val id: String,
    @ColumnInfo("name")
    val name: String,
    @ColumnInfo("type")
    val type: String, // FILE / CHAT / TEXT
    @ColumnInfo("assistant_id")
    val assistantId: String?, // null = 全局
    @ColumnInfo("file_path")
    val filePath: String?,
    @ColumnInfo("file_size")
    val fileSize: Long = 0,
    @ColumnInfo("chunk_count")
    val chunkCount: Int = 0,
    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "knowledge_chunks")
data class KnowledgeChunkEntity(
    @PrimaryKey
    @ColumnInfo("id")
    val id: String,
    @ColumnInfo("source_id")
    val sourceId: String,
    @ColumnInfo("chunk_index")
    val chunkIndex: Int = 0,
    @ColumnInfo("text")
    val text: String = "",
    @ColumnInfo("sentence_start")
    val sentenceStart: Int = 0,
    @ColumnInfo("sentence_end")
    val sentenceEnd: Int = 0,
    @ColumnInfo("embedding", typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray? = null, // FloatArray serialized
    @ColumnInfo("embedding_dim")
    val embeddingDim: Int = 0, // 向量维度，0=未embedding
) {
    companion object {
        fun floatsToBytes(floats: List<Float>): ByteArray {
            val buffer = java.nio.ByteBuffer.allocate(floats.size * 4)
            floats.forEach { buffer.putFloat(it) }
            return buffer.array()
        }

        fun bytesToFloats(bytes: ByteArray): List<Float> {
            val buffer = java.nio.ByteBuffer.wrap(bytes)
            val result = mutableListOf<Float>()
            while (buffer.hasRemaining()) {
                result.add(buffer.getFloat())
            }
            return result
        }
    }
}
