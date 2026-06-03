package me.rerere.rikkahub.data.ai.compaction

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

data class CompactionResult(
    val removedCount: Int,
    val summary: String,
    val compactedMessages: List<UIMessage>,
)

/**
 * Auto-compacts conversation history when token count exceeds threshold.
 *
 * Preserves recent N messages and compresses older ones into a summary.
 */
class AutoCompactor {

    companion object {
        const val DEFAULT_THRESHOLD_TOKENS = 100_000
        const val PRESERVE_RECENT_MESSAGES = 8
    }

    /**
     * Rough token estimation (1 token ~= 4 chars for English text).
     */
    fun estimateTokens(messages: List<UIMessage>): Int {
        return messages.sumOf { msg ->
            msg.parts.sumOf { part ->
                when (part) {
                    is UIMessagePart.Text -> part.text.length / 4
                    is UIMessagePart.Reasoning -> part.reasoning.length / 4
                    is UIMessagePart.Image -> 170 // approx 170 tokens per image (rough estimate for 512x512)
                    is UIMessagePart.Tool -> {
                        part.input.length / 4 +
                        part.output.sumOf {
                            when (it) { is UIMessagePart.Text -> it.text.length / 4; is UIMessagePart.Image -> 170; else -> 0 }
                        }
                    }
                    else -> 0
                }
            }
        }
    }

    /**
     * Check if compaction is needed. Returns a CompactionResult if yes, null if no.
     */
    fun maybeCompact(
        messages: List<UIMessage>,
        threshold: Int = DEFAULT_THRESHOLD_TOKENS,
        preserveRecent: Int = PRESERVE_RECENT_MESSAGES,
    ): CompactionResult? {
        val estimated = estimateTokens(messages)
        if (estimated <= threshold) return null
        if (messages.size <= preserveRecent * 2) return null

        val toCompact = messages.dropLast(preserveRecent)
        val toKeep = messages.takeLast(preserveRecent)

        val summary = buildString {
            appendLine("=== 会话自动压缩摘要 ===")
            appendLine("压缩前: ${toCompact.size} 条消息，估算 ~${estimateTokens(toCompact)} tokens")
            appendLine()
            toCompact.forEachIndexed { i, msg ->
                val role = when (msg.role) {
                    MessageRole.USER -> "用户"
                    MessageRole.ASSISTANT -> "助手"
                    else -> msg.role.name
                }
                val text = msg.toText().take(200)
                if (text.isNotBlank()) {
                    appendLine("[$role] ${text}...")
                }
            }
            appendLine()
            appendLine("=== 以上为自动压缩的历史记录 ===")
        }

        val compacted = listOf(UIMessage.system(prompt = summary)) + toKeep

        return CompactionResult(
            removedCount = toCompact.size,
            summary = summary,
            compactedMessages = compacted,
        )
    }
}
