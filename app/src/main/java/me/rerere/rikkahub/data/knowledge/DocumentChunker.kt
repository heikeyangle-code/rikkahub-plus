package me.rerere.rikkahub.data.knowledge

import android.util.Log

/**
 * 文档分块引擎（递归多级分块，对标 LlamaIndex SentenceSplitter）
 *
 * 分块策略（按优先级降序）：
 * 1. 按段落分隔符（\n\n\n）切
 * 2. 按句子边界（中英文句号/问号/感叹号）切
 * 3. 按短语（逗号/分号）切
 * 4. 按空格切
 * 5. 按字符切
 *
 * 超出 chunkSize 时递归使用下一级切分方式。
 * 用 token 数（而非句数）控制块大小。
 */
class DocumentChunker {

    companion object {
        private const val TAG = "DocumentChunker"
        private const val DEFAULT_CHUNK_SIZE = 1024     // 默认每块 token 数（对标 LlamaIndex）
        private const val DEFAULT_CHUNK_OVERLAP = 200   // 默认重叠 token 数（对标 LlamaIndex）
        private const val PARAGRAPH_SEP = "\n\n\n"
    }

    data class ChunkResult(
        val chunks: List<SentenceChunk>,
        val sentenceCount: Int,
    )

    data class SentenceChunk(
        val text: String,
        val sentenceStart: Int,
        val sentenceEnd: Int,
    )

    /**
     * 递归多级分块
     */
    fun chunkDocument(
        text: String,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        overlap: Int = DEFAULT_CHUNK_OVERLAP,
    ): ChunkResult {
        if (text.isBlank()) return ChunkResult(emptyList(), 0)
        if (overlap >= chunkSize) return chunkDocument(text, chunkSize, chunkSize / 5)

        // 1. 递归切分到不超过 chunkSize 的片段
        val splits = splitRecursive(text, chunkSize)

        // 2. 合并成块（含重叠）
        val chunks = mergeWithOverlap(splits, chunkSize, overlap)

        return ChunkResult(chunks, chunks.size)
    }

    /**
     * 语义分块（保留段落边界优先）
     * 与 chunkDocument 相同策略，段落感知已内建
     */
    fun chunkDocumentSemantic(
        text: String,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        overlap: Int = DEFAULT_CHUNK_OVERLAP,
    ): ChunkResult = chunkDocument(text, chunkSize, overlap)

    // ========== 拆分 ==========

    /** 内部拆分节点 */
    private data class Split(
        val text: String,
        val isComplete: Boolean,  // true=完整句子/段落，false=碎片
        val tokenSize: Int,
    )

    /** 估算 token 数（中英文混合，每字~0.75 token） */
    private fun estimateTokens(text: String): Int = maxOf(1, (text.length * 0.75).toInt())

    /**
     * 递归拆分：先用段落分隔符，不行用句子，再不行用短语，最后字符
     */
    private fun splitRecursive(text: String, chunkSize: Int): List<Split> {
        val tokenSize = estimateTokens(text)
        if (tokenSize <= chunkSize) {
            return listOf(Split(text, isComplete = true, tokenSize))
        }

        // 第1级：按段落切
        val byParagraph = splitByParagraph(text)
        if (byParagraph.size > 1) {
            return byParagraph.flatMap { splitRecursive(it, chunkSize) }
        }

        // 第2级：按句子切（中英文句号）
        val bySentence = splitBySentences(text)
        if (bySentence.size > 1) {
            return bySentence.flatMap { splitRecursive(it, chunkSize) }
        }

        // 第3级：按短语/逗号切
        val byPhrase = splitByPhrases(text)
        if (byPhrase.size > 1) {
            return byPhrase.flatMap { splitRecursive(it, chunkSize) }
        }

        // 第4级：按空格切
        val byWord = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (byWord.size > 1) {
            return byWord.map { Split(it, isComplete = false, estimateTokens(it)) }
        }

        // 第5级：逐字符（极极端情况）
        return text.map { c ->
            Split(c.toString(), isComplete = false, 1)
        }
    }

    private fun splitByParagraph(text: String): List<String> {
        val parts = text.split(PARAGRAPH_SEP)
        if (parts.size <= 1) return parts
        return parts.map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun splitBySentences(text: String): List<String> {
        val regex = Regex("""[^。！？.!?\n]+[。！？.!?\n]?""")
        val matches = regex.findAll(text).map { it.value.trim() }.filter { it.isNotBlank() }.toList()
        return matches.ifEmpty { listOf(text.trim()) }
    }

    private fun splitByPhrases(text: String): List<String> {
        // 中英文逗号/分号/冒号
        val regex = Regex("""[^，,；;：:]+[，,；;：:]?""")
        val matches = regex.findAll(text).map { it.value.trim() }.filter { it.isNotBlank() }.toList()
        return matches.ifEmpty { listOf(text.trim()) }
    }

    // ========== 合并 ==========

    /**
     * 合并 splits 成 chunks，块间重叠 overlap 个 token
     */
    private fun mergeWithOverlap(
        splits: List<Split>,
        chunkSize: Int,
        overlap: Int,
    ): List<SentenceChunk> {
        val chunks = mutableListOf<SentenceChunk>()
        var sentenceIndex = 0
        var i = 0

        while (i < splits.size) {
            val currentChunk = mutableListOf<Split>()
            var currentTokens = 0

            // 单块至少包含一条完整内容
            while (i < splits.size) {
                val s = splits[i]
                if (currentTokens + s.tokenSize > chunkSize && currentChunk.isNotEmpty()) {
                    break
                }
                currentChunk.add(s)
                currentTokens += s.tokenSize
                i++
            }

            if (currentChunk.isEmpty() && i < splits.size) {
                // 单条就超 chunkSize，强制塞一条
                currentChunk.add(splits[i])
                i++
            }

            if (currentChunk.isNotEmpty()) {
                val text = currentChunk.joinToString("") { it.text }
                chunks.add(SentenceChunk(
                    text = text,
                    sentenceStart = sentenceIndex,
                    sentenceEnd = sentenceIndex + currentChunk.size - 1,
                ))
                sentenceIndex += currentChunk.size

                // 计算重叠：从 i 往回退
                if (overlap > 0 && i < splits.size) {
                    var overlapTokens = 0
                    var backtrack = i - 1
                    while (backtrack >= 0 && overlapTokens < overlap) {
                        overlapTokens += splits[backtrack].tokenSize
                        backtrack--
                    }
                    i = maxOf(backtrack + 1, i - (overlap * 2)) // 至少回退一点
                    i = maxOf(0, i)
                }
            }
        }

        return chunks
    }
}
