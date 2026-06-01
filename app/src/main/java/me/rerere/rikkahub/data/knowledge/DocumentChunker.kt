package me.rerere.rikkahub.data.knowledge

import android.util.Log

/**
 * 文档分块引擎
 *
 * 策略：先分句，再按句组块（chunk），每块记录起止句子索引
 * 检索时命中某句，返回该句所属 chunk 的完整文本（前后文窗口）
 */
class DocumentChunker {

    companion object {
        private const val TAG = "DocumentChunker"
        private const val DEFAULT_CHUNK_SIZE = 10      // 每块默认10句
        private const val DEFAULT_CHUNK_OVERLAP = 2    // 重叠2句
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
     * 将文本切分为句子
     */
    fun splitSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        // 按常见的句子结束符分割
        val sentences = mutableListOf<String>()
        val regex = Regex("""[^。！？.!?\n]+[。！？.!?\n]?""")
        val matches = regex.findAll(text)
        for (match in matches) {
            val sentence = match.value.trim()
            if (sentence.isNotBlank()) {
                sentences.add(sentence)
            }
        }
        // 如果正则没匹配到任何句子（极端情况），整段作为一个句子
        if (sentences.isEmpty() && text.isNotBlank()) {
            sentences.add(text.trim())
        }
        return sentences
    }

    /**
     * 按句组块
     * @param sentences 句子列表
     * @param chunkSize 每块多少句
     * @param overlap 块间重叠句子数
     */
    fun chunkSentences(
        sentences: List<String>,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        overlap: Int = DEFAULT_CHUNK_OVERLAP,
    ): ChunkResult {
        if (sentences.isEmpty()) return ChunkResult(emptyList(), 0)

        val chunks = mutableListOf<SentenceChunk>()
        val step = chunkSize - overlap
        if (step <= 0) {
            Log.w(TAG, "chunkSize=$chunkSize overlap=$overlap: step<=0, forcing step=1")
            return chunkSentences(sentences, chunkSize, chunkSize - 1)
        }

        var start = 0
        while (start < sentences.size) {
            val end = minOf(start + chunkSize, sentences.size)
            val chunkText = sentences.subList(start, end).joinToString("")
            chunks.add(SentenceChunk(
                text = chunkText,
                sentenceStart = start,
                sentenceEnd = end - 1,
            ))
            start += step
        }

        return ChunkResult(chunks, sentences.size)
    }

    /**
     * 语义分块：按段落/标题边界切，不跨段落切分
     *
     * 策略：
     * 1. 先用 \n\n 或 markdown 标题分割成段落
     * 2. 短段落合并成 chunk（不超过 chunkSize 句），长段落独立成 chunk
     * 3. 超长段落按句回退到原始 chunkSentences
     */
    fun chunkDocumentSemantic(
        text: String,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        overlap: Int = DEFAULT_CHUNK_OVERLAP,
    ): ChunkResult {
        if (text.isBlank()) return ChunkResult(emptyList(), 0)

        // 1. 按段落切分（双换行或 markdown 标题）
        val paragraphs = splitParagraphs(text)
        if (paragraphs.size <= 1) {
            // 只有一段，退回到常规句子分块
            return chunkDocument(text, chunkSize, overlap)
        }

        val allSentences = mutableListOf<String>()
        val sentenceParagraphMap = mutableListOf<Int>() // 每句属于哪个段落

        paragraphs.forEachIndexed { paraIdx, para ->
            val sentences = splitSentences(para)
            // 记录段落边界 - 用于后续可能参考
            allSentences.addAll(sentences)
            repeat(sentences.size) { sentenceParagraphMap.add(paraIdx) }
        }

        if (allSentences.isEmpty()) return ChunkResult(emptyList(), 0)

        // 2. 以段落为单位组块（尽量不跨段落）
        val chunks = mutableListOf<SentenceChunk>()
        var start = 0
        while (start < allSentences.size) {
            val end = findSemanticBoundary(allSentences, sentenceParagraphMap, start, chunkSize)
            val chunkText = allSentences.subList(start, end).joinToString("")
            chunks.add(SentenceChunk(
                text = chunkText,
                sentenceStart = start,
                sentenceEnd = end - 1,
            ))
            // 跨段落时步进 overlap，否则直接跳到下个段落边界
            val step = if (overlap > 0 && start > 0) chunkSize - overlap else end - start
            start += maxOf(1, step)
        }

        return ChunkResult(chunks, allSentences.size)
    }

    /**
     * 找到语义边界：优先在段落边界断开，不超过 chunkSize 句
     */
    private fun findSemanticBoundary(
        sentences: List<String>,
        sentenceParagraphMap: List<Int>,
        fromIndex: Int,
        maxSize: Int,
    ): Int {
        val end = minOf(fromIndex + maxSize, sentences.size)
        if (end >= sentences.size) return sentences.size

        // 在 [fromIndex, end) 范围内找最后一个段落边界
        val currentPara = sentenceParagraphMap[fromIndex]
        var bestSplit = end
        for (i in end - 1 downTo fromIndex + 1) {
            if (sentenceParagraphMap[i] != currentPara) {
                bestSplit = i
                break
            }
        }

        // 如果范围内有段落边界，在边界处断开
        if (bestSplit < end && bestSplit > fromIndex) {
            return bestSplit
        }

        // 没有段落边界，硬截断
        return end
    }

    /**
     * 将文本按段落/标题分割
     */
    private fun splitParagraphs(text: String): List<String> {
        // 按双换行或 markdown 标题（##, ===, --- 等）分割
        val segments = text.split(Regex("\\n\\s*\\n|\\n#{1,6}\\s|\\n[-=]{2,}\\n"))
        return segments.map { it.trim() }.filter { it.isNotBlank() }
    }
    
    fun chunkDocument(text: String, chunkSize: Int = DEFAULT_CHUNK_SIZE, overlap: Int = DEFAULT_CHUNK_OVERLAP): ChunkResult {
        val sentences = splitSentences(text)
        return chunkSentences(sentences, chunkSize, overlap)
    }

    /**
     * 从文本中提取上下文窗口（命中句前后各N句）
     */
    fun extractWindow(sentences: List<String>, hitIndex: Int, windowSize: Int = 5): String {
        val start = maxOf(0, hitIndex - windowSize)
        val end = minOf(sentences.size - 1, hitIndex + windowSize)
        return sentences.subList(start, end + 1).joinToString("")
    }
}
