package me.rerere.rikkahub.data.knowledge

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.ProviderManager
import me.rerere.document.DocxParser
import me.rerere.document.EpubParser
import me.rerere.document.PdfParser
import me.rerere.document.PptxParser
import me.rerere.rikkahub.data.db.dao.KnowledgeBaseDao
import me.rerere.rikkahub.data.db.entity.KnowledgeChunkEntity
import me.rerere.rikkahub.data.db.entity.KnowledgeSourceEntity
import me.rerere.rikkahub.data.model.KnowledgeSearchResult
import me.rerere.rikkahub.data.model.KnowledgeSource
import me.rerere.rikkahub.data.model.KnowledgeSourceType
import me.rerere.rikkahub.data.model.MatchType
import me.rerere.rikkahub.data.model.KnowledgeChunk
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.Conversation
import java.io.File
import kotlin.math.sqrt
import kotlin.uuid.Uuid

private const val TAG = "KnowledgeBaseService"

class KnowledgeBaseService(
    private val context: Context,
    private val dao: KnowledgeBaseDao,
    private val chunker: DocumentChunker,
    private val providerManager: ProviderManager,
) {
    // ---- 数据源管理 ----

    fun getAllSourcesFlow(): Flow<List<KnowledgeSourceEntity>> = dao.getAllSourcesFlow()

    fun getSourcesForAssistantFlow(assistantId: String): Flow<List<KnowledgeSourceEntity>> =
        dao.getSourcesForAssistantFlow(assistantId)

    suspend fun getSourcesForAssistantOnce(assistantId: String): List<KnowledgeSourceEntity> =
        dao.getAllSources().filter { it.assistantId == null || it.assistantId == assistantId }

    suspend fun deleteSource(sourceId: String) = withContext(Dispatchers.IO) {
        dao.deleteChunksBySource(sourceId)
        dao.deleteSource(sourceId)
        // 清理FTS
        val db = (dao as? androidx.room.RoomDatabase)?.openHelper?.writableDatabase
        if (db != null) {
            db.execSQL("DELETE FROM knowledge_fts WHERE source_id = ?", arrayOf(sourceId))
        }
    }

    // ---- 文件导入 ----

    suspend fun importFile(
        uri: Uri,
        fileName: String,
        assistantId: String? = null,
        chunkSize: Int = 10,
        overlap: Int = 2,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val text = readDocument(uri) ?: run {
                Log.e(TAG, "Failed to read document: $fileName")
                return@withContext null
            }
            if (text.isBlank()) {
                Log.w(TAG, "Empty document: $fileName")
                return@withContext null
            }

            val sourceId = Uuid.random().toString()
            val sourceName = fileName.substringBeforeLast(".")

            // 1. 创建知识源
            val source = KnowledgeSourceEntity(
                id = sourceId,
                name = sourceName,
                type = KnowledgeSourceType.FILE.name,
                assistantId = assistantId,
                filePath = uri.toString(),
                fileSize = text.length.toLong(),
                createdAt = System.currentTimeMillis(),
            )
            dao.insertSource(source)

            // 2. 分块
            val result = chunker.chunkDocument(text, chunkSize, overlap)
            val chunks = result.chunks.mapIndexed { index, chunk ->
                KnowledgeChunkEntity(
                    id = "${sourceId}_$index",
                    sourceId = sourceId,
                    chunkIndex = index,
                    text = chunk.text,
                    sentenceStart = chunk.sentenceStart,
                    sentenceEnd = chunk.sentenceEnd,
                )
            }
            dao.insertChunks(chunks)

            // 3. 索引FTS5
            indexFts5(chunks)

            // 4. 更新计数
            dao.deleteSource(sourceId)
            dao.insertSource(source.copy(chunkCount = chunks.size))

            Log.i(TAG, "Imported $fileName: ${result.sentenceCount} sentences, ${chunks.size} chunks")
            sourceId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import file: $fileName", e)
            null
        }
    }

    // ---- 聊天记录导入 ----

    suspend fun importChatHistory(
        conversation: Conversation,
        assistantId: String? = null,
        chunkSize: Int = 10,
        overlap: Int = 2,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val text = buildString {
                conversation.messageNodes.forEach { node ->
                    val speaker = if (node.role.isAssistant) "AI" else "User"
                    node.messages.forEach { msg ->
                        val msgText = msg.toText()
                        if (msgText.isNotBlank()) {
                            appendLine("$speaker: $msgText")
                        }
                    }
                }
            }
            if (text.isBlank()) return@withContext null

            val title = conversation.title.ifBlank { "聊天记录" }
            val sourceId = Uuid.random().toString()

            val source = KnowledgeSourceEntity(
                id = sourceId,
                name = title,
                type = KnowledgeSourceType.CHAT.name,
                assistantId = assistantId,
                fileSize = text.length.toLong(),
                createdAt = System.currentTimeMillis(),
            )
            dao.insertSource(source)

            val result = chunker.chunkDocument(text, chunkSize, overlap)
            val chunks = result.chunks.mapIndexed { index, chunk ->
                KnowledgeChunkEntity(
                    id = "${sourceId}_$index",
                    sourceId = sourceId,
                    chunkIndex = index,
                    text = chunk.text,
                    sentenceStart = chunk.sentenceStart,
                    sentenceEnd = chunk.sentenceEnd,
                )
            }
            dao.insertChunks(chunks)
            indexFts5(chunks)

            dao.deleteSource(sourceId)
            dao.insertSource(source.copy(chunkCount = chunks.size))

            Log.i(TAG, "Imported chat '$title': ${result.sentenceCount} sentences, ${chunks.size} chunks")
            sourceId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import chat history", e)
            null
        }
    }

    // ---- 笔记/文本导入 ----

    suspend fun importText(
        title: String,
        text: String,
        assistantId: String? = null,
        chunkSize: Int = 10,
        overlap: Int = 2,
    ): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        try {
            val sourceId = Uuid.random().toString()
            val source = KnowledgeSourceEntity(
                id = sourceId,
                name = title,
                type = KnowledgeSourceType.TEXT.name,
                assistantId = assistantId,
                fileSize = text.length.toLong(),
                createdAt = System.currentTimeMillis(),
            )
            dao.insertSource(source)

            val result = chunker.chunkDocument(text, chunkSize, overlap)
            val chunks = result.chunks.mapIndexed { index, chunk ->
                KnowledgeChunkEntity(
                    id = "${sourceId}_$index",
                    sourceId = sourceId,
                    chunkIndex = index,
                    text = chunk.text,
                    sentenceStart = chunk.sentenceStart,
                    sentenceEnd = chunk.sentenceEnd,
                )
            }
            dao.insertChunks(chunks)
            indexFts5(chunks)

            dao.deleteSource(sourceId)
            dao.insertSource(source.copy(chunkCount = chunks.size))

            Log.i(TAG, "Imported text '$title': ${result.sentenceCount} sentences, ${chunks.size} chunks")
            sourceId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import text", e)
            null
        }
    }

    // ---- Embedding（批量异步） ----

    suspend fun embedAllChunks(settings: Settings) = withContext(Dispatchers.IO) {
        val chunks = dao.getAllEmbeddedChunks()
        val model = findEmbeddingModel(settings) ?: run {
            Log.w(TAG, "No embedding model configured")
            return@withContext
        }

        val batchSize = 5
        chunks.chunked(batchSize).forEachIndexed { batchIndex, batch ->
            try {
                val texts = batch.map { it.text }
                val provider = model.findProvider(settings.providers)
                    ?: run {
                        Log.e(TAG, "Provider not found for embedding model: ${model.id}")
                        return@withContext
                    }
                val providerImpl = providerManager.getProviderByType(provider)
                val result = providerImpl.generateEmbedding(EmbeddingGenerationParams(
                    model = model,
                    input = texts,
                ))
                result.embeddings.forEachIndexed { i, embedding ->
                    val entity = batch[i]
                    val updated = entity.copy(
                        embedding = KnowledgeChunkEntity.floatsToBytes(embedding),
                        embeddingDim = embedding.size,
                    )
                    // Update individually
                    val db = (dao as? androidx.room.RoomDatabase)?.openHelper?.writableDatabase
                    db?.execSQL(
                        "UPDATE knowledge_chunks SET embedding = ?, embedding_dim = ? WHERE id = ?",
                        arrayOf(updated.embedding, updated.embeddingDim, updated.id)
                    )
                }
                Log.i(TAG, "Embedded batch $batchIndex/${chunks.size / batchSize}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to embed batch $batchIndex", e)
            }
        }
        Log.i(TAG, "Embedding complete: ${chunks.size} chunks")
    }

    // ---- 检索 ----

    suspend fun search(
        query: String,
        assistantId: String? = null,
        settings: Settings? = null,
        topK: Int = 5,
        scoreThreshold: Float = 0.25f,
    ): List<KnowledgeSearchResult> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, KnowledgeSearchResult>()

        // 1. FTS5 精确搜索
        try {
            val ftsQuery = query.trim()
                .replace(Regex("""[^\w\u4e00-\u9fff\s]"""), " ") // 只保留中文、英文、数字、空格
                .split(Regex("\\s+"))
                .filter { it.length >= 2 }
                .joinToString(" AND ")
            if (ftsQuery.isNotBlank()) {
                val ftsResults = dao.searchFts(ftsQuery, topK * 2)
                ftsResults.forEach { chunk ->
                    val key = chunk.id
                    val existing = results[key]
                    results[key] = KnowledgeSearchResult(
                        chunk = chunk.toDomain(),
                        score = maxOf(existing?.score ?: 0f, 0.6f),
                        source = KnowledgeSource(), // placeholder, filled below
                        matchType = if (existing?.matchType == MatchType.EMBEDDING) MatchType.HYBRID else MatchType.FTS,
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "FTS5 search failed: ${e.message}")
        }

        // 2. Embedding 语义搜索（需要 settings）
        if (settings != null) {
            try {
                val model = findEmbeddingModel(settings)
                if (model != null) {
                    val provider = model.findProvider(settings.providers)
                    if (provider != null) {
                        val providerImpl = providerManager.getProviderByType(provider)
                        val queryEmbedding = providerImpl.generateEmbedding(EmbeddingGenerationParams(
                            model = model,
                            input = listOf(query),
                        )).embeddings.firstOrNull()

                        if (queryEmbedding != null) {
                            val embeddedChunks = if (assistantId != null) {
                                dao.getEmbeddedChunksForAssistant(assistantId)
                            } else {
                                dao.getAllEmbeddedChunks()
                            }
                            embeddedChunks.forEach { chunk ->
                                val chunkEmbedding = chunk.embedding?.let { KnowledgeChunkEntity.bytesToFloats(it) }
                                if (chunkEmbedding != null && chunkEmbedding.size == queryEmbedding.size) {
                                    val score = cosineSimilarity(queryEmbedding, chunkEmbedding)
                                    if (score >= scoreThreshold) {
                                        val key = chunk.id
                                        val existing = results[key]
                                        results[key] = KnowledgeSearchResult(
                                            chunk = chunk.toDomain(),
                                            score = maxOf(existing?.score ?: 0f, score),
                                            source = KnowledgeSource(),
                                            matchType = if (existing != null) MatchType.HYBRID else MatchType.EMBEDDING,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Embedding search failed: ${e.message}")
            }
        }

        // 3. 合并排序
        val sorted = results.entries
            .sortedByDescending { it.value.score }
            .take(topK)

        // 4. 填充 source 信息
        sorted.map { (_, result) ->
            val sourceEntity = dao.getSourceById(result.chunk.sourceId.toString())
            result.copy(source = sourceEntity?.toDomain() ?: KnowledgeSource())
        }
    }

    // ---- 内部方法 ----

    private fun indexFts5(chunks: List<KnowledgeChunkEntity>) {
        val db = (dao as? androidx.room.RoomDatabase)?.openHelper?.writableDatabase ?: return
        chunks.forEach { chunk ->
            db.execSQL(
                "INSERT INTO knowledge_fts(text, chunk_id, source_id) VALUES (?, ?, ?)",
                arrayOf(chunk.text, chunk.id, chunk.sourceId)
            )
        }
    }

    private fun readDocument(uri: Uri): String? {
        return try {
            val file = uri.toFile()
            if (!file.exists() || !file.isFile) return null
            if (file.length() > 10 * 1024 * 1024) return null // 10MB limit

            val name = file.name.lowercase()
            when {
                name.endsWith(".pdf") -> PdfParser.parserPdf(file)
                name.endsWith(".docx") -> DocxParser.parse(file)
                name.endsWith(".pptx") -> PptxParser.parse(file)
                name.endsWith(".epub") -> EpubParser.parse(file)
                name.endsWith(".txt") || name.endsWith(".md") -> file.readText()
                else -> file.readText() // fallback
            }
        } catch (e: Exception) {
            Log.e(TAG, "readDocument failed", e)
            null
        }
    }

    private fun findEmbeddingModel(settings: Settings?): me.rerere.ai.provider.Model? {
        if (settings == null) return null
        // 优先使用全局聊天模型（默认有embedding能力）
        return settings.findModelById(settings.chatModelId)
    }

    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        if (a.size != b.size) return 0f
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else dotProduct / denom
    }
}

// ---- 扩展函数 ----

private fun KnowledgeChunkEntity.toDomain() = KnowledgeChunk(
    id = id,
    sourceId = kotlin.uuid.Uuid.parse(sourceId),
    chunkIndex = chunkIndex,
    text = text,
    sentenceStart = sentenceStart,
    sentenceEnd = sentenceEnd,
    embedding = embedding?.let { KnowledgeChunkEntity.bytesToFloats(it) },
)

private fun KnowledgeSourceEntity.toDomain() = KnowledgeSource(
    id = kotlin.uuid.Uuid.parse(id),
    name = name,
    type = try { KnowledgeSourceType.valueOf(type) } catch (_: Exception) { KnowledgeSourceType.FILE },
    assistantId = assistantId?.let { kotlin.uuid.Uuid.parse(it) },
    filePath = filePath,
    fileSize = fileSize,
    chunkCount = chunkCount,
    createdAt = createdAt,
)

private fun me.rerere.ai.provider.Model.findProvider(providers: List<me.rerere.ai.provider.ProviderSetting>): me.rerere.ai.provider.ProviderSetting? {
    return providers.find { it.id == this.providerId }
}
