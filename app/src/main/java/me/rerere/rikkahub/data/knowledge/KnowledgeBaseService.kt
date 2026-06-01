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
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.dao.KnowledgeBaseDao
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.entity.KnowledgeChunkEntity
import me.rerere.rikkahub.data.db.entity.KnowledgeSourceEntity
import me.rerere.rikkahub.data.model.KnowledgeSearchResult
import me.rerere.rikkahub.data.model.KnowledgeSource
import me.rerere.rikkahub.data.model.KnowledgeSourceType
import me.rerere.rikkahub.data.model.MatchType
import me.rerere.rikkahub.data.model.KnowledgeChunk
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.Conversation
import java.io.File
import kotlin.math.sqrt
import kotlin.uuid.Uuid

private const val TAG = "KnowledgeBaseService"

class KnowledgeBaseService(
    private val context: Context,
    private val database: AppDatabase,
    private val chunker: DocumentChunker,
    private val providerManager: ProviderManager,
    private val settingsStore: SettingsStore,
) {
    private val dao: KnowledgeBaseDao = database.knowledgeBaseDao()
    private val writableDb get() = database.openHelper.writableDatabase
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
        writableDb.execSQL("DELETE FROM knowledge_fts WHERE source_id = ?", arrayOf(sourceId))
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

            // 4. 自动embedding
            autoEmbedIfEnabled(sourceId)

            // 5. 更新计数
            dao.deleteSource(sourceId)
            dao.insertSource(source.copy(chunkCount = chunks.size))

            Log.i(TAG, "Imported $fileName: ${result.sentenceCount} sentences, ${chunks.size} chunks")
            sourceId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import file: $fileName", e)
            null
        }
    }

    // ---- 批量文件夹导入 ----

    suspend fun importFolder(
        folderUri: Uri,
        folderName: String,
        assistantId: String? = null,
        chunkSize: Int = 10,
        overlap: Int = 2,
    ): Int = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val children = resolver.query(folderUri, null, null, null, null)?.use { cursor ->
                val names = mutableListOf<Pair<String, Uri>>()
                while (cursor.moveToNext()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(
                        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    ))
                    val mime = cursor.getString(cursor.getColumnIndexOrThrow(
                        android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                    ))
                    val docId = cursor.getString(cursor.getColumnIndexOrThrow(
                        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID
                    ))
                    if (mime == "application/vnd.google-apps.folder") continue
                    val childUri = android.provider.DocumentsContract.buildDocumentUri(
                        folderUri.authority ?: return@use null,
                        docId
                    )
                    val lower = name.lowercase()
                    if (lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".epub") ||
                        lower.endsWith(".pptx") || lower.endsWith(".txt") || lower.endsWith(".md")) {
                        names.add(name to childUri)
                    }
                }
                names
            } ?: return@withContext 0

            if (children.isEmpty()) return@withContext 0

            var imported = 0
            for ((name, uri) in children) {
                try {
                    val text = readDocument(uri) ?: continue
                    if (text.isBlank()) continue

                    val sourceId = Uuid.random().toString()
                    val source = KnowledgeSourceEntity(
                        id = sourceId,
                        name = name,
                        type = KnowledgeSourceType.BATCH.name,
                        assistantId = assistantId,
                        filePath = uri.toString(),
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
                    imported++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to import $name in folder", e)
                }
            }
            Log.i(TAG, "Folder import complete: $imported files from $folderName")
            imported
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import folder: $folderName", e)
            0
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

    private suspend fun ensureEmbeddings(settings: Settings) = withContext(Dispatchers.IO) {
        // 只embed未处理的chunks
        val unembedded = writableDb.rawQuery(
            "SELECT id, source_id, chunk_index, text, sentence_start, sentence_end FROM knowledge_chunks WHERE embedding IS NULL",
            null
        )
        val chunks = mutableListOf<KnowledgeChunkEntity>()
        unembedded.use { cursor ->
            while (cursor.moveToNext()) {
                chunks.add(KnowledgeChunkEntity(
                    id = cursor.getString(0),
                    sourceId = cursor.getString(1),
                    chunkIndex = cursor.getInt(2),
                    text = cursor.getString(3),
                    sentenceStart = cursor.getInt(4),
                    sentenceEnd = cursor.getInt(5),
                ))
            }
        }
        if (chunks.isEmpty()) return@withContext

        val model = findEmbeddingModel(settings) ?: run {
            Log.w(TAG, "No embedding model configured")
            return@withContext
        }

        Log.i(TAG, "Embedding ${chunks.size} unembedded chunks...")
        val batchSize = 5
        chunks.chunked(batchSize).forEachIndexed { batchIndex, batch ->
            try {
                val texts = batch.map { it.text }
                val provider = model.findProvider(settings.providers) ?: return@forEachIndexed
                val providerImpl = providerManager.getProviderByType(provider)
                val result = providerImpl.generateEmbedding(provider, EmbeddingGenerationParams(
                    model = model,
                    input = texts,
                ))
                result.embeddings.forEachIndexed { i, embedding ->
                    writableDb.execSQL(
                        "UPDATE knowledge_chunks SET embedding = ?, embedding_dim = ? WHERE id = ?",
                        arrayOf(
                            KnowledgeChunkEntity.floatsToBytes(embedding),
                            embedding.size,
                            batch[i].id
                        )
                    )
                }
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

        // 0. Query Rewrite：生成多个搜索变体提高召回率
        val queries = buildList {
            add(query) // 原问题
            // 变体1：去掉疑问词，只保留关键词
            val stripped = query.replace(Regex("(?i)^(what|how|why|where|when|who|which|tell me|show me|给我|请问|如何|为什么|怎么|哪里|什么是|有哪些)\\s*"), "")
            if (stripped.length >= 4 && stripped != query) add(stripped)
            // 变体2：只取中英文词
            val keywords = Regex("""[\w\u4e00-\u9fff]+""").findAll(query).map { it.value }.joinToString(" ")
            if (keywords.length >= 4 && keywords != query) add(keywords)
        }.distinct()

        // 1. FTS5 精确搜索（所有变体）
        try {
            queries.forEach { q ->
                val ftsQuery = q.trim()
                    .replace(Regex("""[^\w\u4e00-\u9fff\s]"""), " ")
                    .split(Regex("\\s+"))
                    .filter { it.length >= 2 }
                    .joinToString(" AND ")
                if (ftsQuery.isNotBlank()) {
                    val cursor = writableDb.rawQuery("""
                        SELECT kc.id, kc.source_id, kc.chunk_index, kc.text, kc.sentence_start, kc.sentence_end
                        FROM knowledge_fts kf
                        INNER JOIN knowledge_chunks kc ON kc.id = kf.chunk_id
                        WHERE kf.text MATCH ?
                        ORDER BY rank
                        LIMIT ?
                    """, arrayOf(ftsQuery, (topK * 2).toString()))
                    cursor.use {
                        while (it.moveToNext()) {
                            val chunk = KnowledgeChunkEntity(
                                id = it.getString(0),
                                sourceId = it.getString(1),
                                chunkIndex = it.getInt(2),
                                text = it.getString(3),
                                sentenceStart = it.getInt(4),
                                sentenceEnd = it.getInt(5),
                            )
                            val key = chunk.id
                            val existing = results[key]
                            results[key] = KnowledgeSearchResult(
                                chunk = chunk.toDomain(),
                                score = maxOf(existing?.score ?: 0f, 0.6f),
                                source = KnowledgeSource(),
                                matchType = if (existing?.matchType == MatchType.EMBEDDING) MatchType.HYBRID else MatchType.FTS,
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "FTS5 search failed: ${e.message}")
        }

        // 2. Embedding 语义搜索（需要 settings）
        if (settings != null) {
            try {
                // 懒embedding：首次搜索时自动embed未处理的chunks
                ensureEmbeddings(settings)

                val model = findEmbeddingModel(settings)
                if (model != null) {
                    val provider = model.findProvider(settings.providers)
                    if (provider != null) {
                        val providerImpl = providerManager.getProviderByType(provider)
                        val queryEmbedding = providerImpl.generateEmbedding(provider, EmbeddingGenerationParams(
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

        // 4. 上下文窗口扩展：每块前后各取1块，拼成完整段落
        val expanded = sorted.map { (_, result) ->
            val allChunks = dao.getChunksBySource(result.chunk.sourceId.toString())
            val idx = allChunks.indexOfFirst { it.id == result.chunk.id }
            if (idx < 0) return@map result

            val start = maxOf(0, idx - 1)
            val end = minOf(allChunks.size - 1, idx + 1)
            val window = allChunks.subList(start, end + 1)
            val fullText = window.joinToString("") { it.text }

            result.copy(
                chunk = result.chunk.copy(text = fullText),
                score = result.score * 1.1f, // 小加分，有上下文的更优先
            )
        }

        // 5. 填充 source 信息
        expanded.map { result ->
            val sourceEntity = dao.getSourceById(result.chunk.sourceId.toString())
            result.copy(source = sourceEntity?.toDomain() ?: KnowledgeSource())
        }
    }

    // ---- 内部方法 ----

    private fun indexFts5(chunks: List<KnowledgeChunkEntity>) {
        chunks.forEach { chunk ->
            writableDb.execSQL(
                "INSERT INTO knowledge_fts(text, chunk_id, source_id) VALUES (?, ?, ?)",
                arrayOf(chunk.text, chunk.id, chunk.sourceId)
            )
        }
    }

    /** 自动embedding：仅当设置开启时 */
    private suspend fun autoEmbedIfEnabled(sourceId: String) {
        try {
            val settings = settingsStore.settingsFlow.value
            if (settings.displaySetting.autoEmbedOnImport) {
                ensureEmbeddings(settings)
            }
        } catch (_: Exception) { }
    }

    /** 手动embed指定来源 */
    suspend fun embedSource(sourceId: String, settings: Settings) = withContext(Dispatchers.IO) {
        try {
            val chunks = dao.getChunksBySource(sourceId)
            if (chunks.isEmpty()) return@withContext
            ensureEmbeddings(settings)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to embed source $sourceId", e)
        }
    }

    /** UI搜索（返回可直接展示的结果） */
    data class SearchResultUi(
        val chunkId: String,
        val text: String,
        val sourceName: String,
        val score: Float,
        val matchType: String,
    )

    suspend fun searchForUi(query: String, settings: Settings): List<SearchResultUi> = withContext(Dispatchers.IO) {
        val results = search(query, settings = settings, topK = 20, scoreThreshold = 0.1f)
        results.map { r ->
            val source = dao.getSourceById(r.chunk.sourceId.toString())
            SearchResultUi(
                chunkId = r.chunk.id,
                text = r.chunk.text.take(300),
                sourceName = source?.name ?: "未知",
                score = r.score,
                matchType = r.matchType.name,
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

    private fun findEmbeddingModel(settings: Settings): me.rerere.ai.provider.Model? {
        // 优先使用专用的embedding模型
        val embeddingId = settings.embeddingModelId
        if (embeddingId != null) {
            val model = settings.findModelById(embeddingId)
            if (model != null) return model
        }
        // fallback到聊天模型
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
