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
 * 四层上下文压缩（s08 标准）。
 *
 * L1: snip_compact — 裁掉无关的旧对话（最便宜，无API调用）
 * L2: tool_output_truncation — 截断过长的工具输出（最便宜，无API调用）
 * L3: LLM 摘要 — 把旧消息压缩成摘要文本（中等成本，1次API调用）
 * L4: emergency trim — API 拒绝 prompt_too_long 时的应急裁剪
 *
 * 核心设计：便宜的先跑，贵的后跑。
 */
class AutoCompactor {

    companion object {
        const val DEFAULT_THRESHOLD_TOKENS = 100_000
        const val PRESERVE_RECENT_MESSAGES = 8
        const val TOOL_OUTPUT_MAX_CHARS = 5000
        const val SNIP_KEEP_OLDEST = 3
    }

    /** 粗略 token 估算 */
    fun estimateTokens(messages: List<UIMessage>): Int {
        return messages.sumOf { msg ->
            msg.parts.sumOf { part ->
                when (part) {
                    is UIMessagePart.Text -> part.text.length / 4
                    is UIMessagePart.Reasoning -> part.reasoning.length / 4
                    is UIMessagePart.Image -> 170
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
     * L1: Snip compact — 裁掉中间无关的旧对话。
     * 保留最旧的 N 条（上下文种子）+ 最新的 M 条（当前工作）。
     */
    fun snipCompact(
        messages: List<UIMessage>,
        preserveRecent: Int = PRESERVE_RECENT_MESSAGES,
        keepOldest: Int = SNIP_KEEP_OLDEST,
    ): SnipResult {
        if (messages.size <= keepOldest + preserveRecent + 2) {
            return SnipResult(messages, 0)
        }
        val oldest = messages.take(keepOldest)
        val recent = messages.takeLast(preserveRecent)
        val removed = messages.size - oldest.size - recent.size
        val summary = listOf(UIMessage.system(
            prompt = "[Snip compact] ${removed} intermediate messages removed. " +
                     "Oldest ${keepOldest} and latest ${preserveRecent} messages preserved."
        ))
        return SnipResult(oldest + summary + recent, removed)
    }

    data class SnipResult(val messages: List<UIMessage>, val removedCount: Int)

    /**
     * L2: Tool output truncation — 截断过长的工具输出。
     * 每个工具输出保留前 maxChars 字符。
     */
    fun truncateToolOutput(
        messages: List<UIMessage>,
        maxChars: Int = TOOL_OUTPUT_MAX_CHARS,
    ): List<UIMessage> {
        return messages.map { msg ->
            val newParts = msg.parts.map { part ->
                if (part is UIMessagePart.Tool) {
                    val truncatedOutput = part.output.map { out ->
                        when (out) {
                            is UIMessagePart.Text -> {
                                if (out.text.length > maxChars) {
                                    UIMessagePart.Text(out.text.take(maxChars) +
                                        "\n... [truncated ${out.text.length - maxChars} chars]")
                                } else out
                            }
                            else -> out
                        }
                    }
                    UIMessagePart.Tool(
                        id = part.id,
                        name = part.name,
                        input = part.input,
                        output = truncatedOutput,
                        isExecuted = part.isExecuted,
                    )
                } else part
            }
            UIMessage(
                role = msg.role,
                parts = newParts,
                usage = msg.usage,
                createAt = msg.createAt,
                model = msg.model,
                annotations = msg.annotations,
            )
        }
    }

    /**
     * L4: Emergency trim — API 拒绝 prompt_too_long 时的应急裁剪。
     * 保留最新 N 条 + 压缩摘要标记。
     */
    fun emergencyTrim(
        messages: List<UIMessage>,
        preserveRecent: Int = PRESERVE_RECENT_MESSAGES,
    ): List<UIMessage> {
        if (messages.size <= preserveRecent + 1) return messages
        val recent = messages.takeLast(preserveRecent)
        val marker = UIMessage.system(
            prompt = "[Emergency compact] Earlier conversation trimmed due to context limit. Continue from where you left off."
        )
        return listOf(marker) + recent
    }

    /**
     * 完整压缩管线：按需触发 L1 → L2 → L3。
     * 先跑便宜的（L1/L2），token 仍然超阈值才跑贵的（L3 摘要）。
     */
    fun maybeCompact(
        messages: List<UIMessage>,
        threshold: Int = DEFAULT_THRESHOLD_TOKENS,
        preserveRecent: Int = PRESERVE_RECENT_MESSAGES,
    ): CompactionResult? {
        var current = messages
        var totalRemoved = 0

        // L1: Snip old turns (cheapest)
        val snipResult = snipCompact(current, preserveRecent)
        totalRemoved += snipResult.removedCount
        current = snipResult.messages

        // L2: Truncate tool output (cheap)
        current = truncateToolOutput(current)

        var estimated = estimateTokens(current)

        // If under threshold after L1+L2, no need for L3
        if (estimated <= threshold) {
            val summary = buildString {
                appendLine("=== Context compacted ===")
                appendLine("L1 snip: removed ${snipResult.removedCount} intermediate messages")
                appendLine("L2 truncation: tool outputs capped at ${TOOL_OUTPUT_MAX_CHARS} chars each")
                appendLine("Estimated tokens: $estimated / $threshold")
            }
            return CompactionResult(
                removedCount = totalRemoved,
                summary = summary,
                compactedMessages = current,
            )
        }

        // L3: LLM summary (original behavior — keep for backward compatibility)
        if (current.size <= preserveRecent * 2) return null

        val toCompact = current.dropLast(preserveRecent)
        val toKeep = current.takeLast(preserveRecent)
        totalRemoved += toCompact.size

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
            removedCount = totalRemoved,
            summary = summary,
            compactedMessages = compacted,
        )
    }
}
