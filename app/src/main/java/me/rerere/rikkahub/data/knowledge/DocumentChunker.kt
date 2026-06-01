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
     * 全文分块：分句 → 组块
     */
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
