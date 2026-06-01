package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * 知识库数据来源
 */
@Serializable
data class KnowledgeSource(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val type: KnowledgeSourceType = KnowledgeSourceType.FILE,
    val assistantId: Uuid? = null, // null = 全局，对所有人可见
    val filePath: String? = null,
    val fileSize: Long = 0,
    val chunkCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
enum class KnowledgeSourceType {
    FILE,      // 文件导入（PDF/DOCX/EPUB/PPTX/TXT）
    CHAT,      // 聊天记录导入
    TEXT,      // 手动输入的笔记/文字
}

/**
 * 知识分块
 */
data class KnowledgeChunk(
    val id: String = "",
    val sourceId: Uuid,
    val chunkIndex: Int = 0,
    val text: String = "",
    val sentenceStart: Int = 0,  // 在原始文本中的起始句子索引
    val sentenceEnd: Int = 0,    // 在原始文本中的结束句子索引
    val embedding: List<Float>? = null, // 向量，null = 尚未embedding
)

/**
 * 检索结果
 */
data class KnowledgeSearchResult(
    val chunk: KnowledgeChunk,
    val score: Float,       // 相似度分数
    val source: KnowledgeSource,
    val matchType: MatchType,
)

enum class MatchType {
    EMBEDDING,  // 语义匹配
    FTS,        // 关键字精确匹配
    HYBRID,     // 两通道都命中
}
