package me.rerere.rikkahub.data.ai.compaction

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import java.io.File

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
        const val PERSIST_THRESHOLD_BYTES = 30_000
        const val TOOL_RESULT_BUDGET_BYTES = 200_000
        const val MICRO_COMPACT_KEEP_RECENT = 3
    }

    // ── 持久化目录（可选）──
    private var toolResultsDir: File? = null
    private var transcriptDir: File? = null

    fun setToolResultsDir(dir: File) { toolResultsDir = dir; dir.mkdirs() }
    fun setTranscriptDir(dir: File) { transcriptDir = dir; dir.mkdirs() }

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
     * Persist large tool output to disk, return preview reference.
     * 对标 s20 persist_large_output。
     */
    fun persistLargeOutput(toolUseId: String, output: String): String {
        if (output.length <= PERSIST_THRESHOLD_BYTES) return output
        val dir = toolResultsDir ?: return output.take(TOOL_OUTPUT_MAX_CHARS)
        val file = File(dir, "${toolUseId}.txt")
        try {
            file.writeText(output)
        } catch (_: Exception) {}
        return "<persisted-output>\nFull output: ${file.absolutePath}\n" +
               "Preview:\n${output.take(2000)}\n</persisted-output>"
    }

    /**
     * Write transcript to disk.
     * 对标 s20 write_transcript。
     */
    fun writeTranscript(messages: List<UIMessage>): File? {
        val dir = transcriptDir ?: return null
        val file = File(dir, "transcript_${System.currentTimeMillis()}.jsonl")
        try {
            file.writeText(messages.joinToString("\n") { msg ->
                """{"role":"${msg.role.name}","parts":${msg.parts.size}}"""
            })
        } catch (_: Exception) { return null }
        return file
    }

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
                        toolCallId = part.toolCallId,
                        toolName = part.toolName,
                        input = part.input,
                        output = truncatedOutput,
                        approvalState = part.approvalState,
                    )
                } else part
            }
            UIMessage(
                role = msg.role,
                parts = newParts,
                createdAt = msg.createdAt,
                modelId = msg.modelId,
                usage = msg.usage,
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
     * 完整压缩管线：智能选择压缩方式。
     *
     * 1. 先跑 L2 截断工具输出（免费、安全）
     * 2. 还超阈值才跑 L3 LLM 摘要（原版行为，花一次 API）
     * 3. API 报错时由外部调用 emergencyTrim
     *
     * 不跑 L1 裁旧对话——直接裁消息比摘要损失更多信息。
     */
    fun maybeCompact(
        messages: List<UIMessage>,
        threshold: Int = DEFAULT_THRESHOLD_TOKENS,
        preserveRecent: Int = PRESERVE_RECENT_MESSAGES,
    ): CompactionResult? {
        var current = messages
        var totalRemoved = 0

        // Step 1: L2 — Truncate tool output (free, safe)
        current = truncateToolOutput(current)

        var estimated = estimateTokens(current)

        // If under threshold after L2, no need for L3
        if (estimated <= threshold) {
            val summary = buildString {
                appendLine("=== Context compacted ===")
                appendLine("Tool outputs capped at ${TOOL_OUTPUT_MAX_CHARS} chars each")
                appendLine("Estimated tokens: $estimated / $threshold")
            }
            return CompactionResult(
                removedCount = 0,
                summary = summary,
                compactedMessages = current,
            )
        }

        // Step 2: L3 — LLM summary (original behavior, costs 1 API call)
        if (current.size <= preserveRecent * 2) return null

        val toCompact = current.dropLast(preserveRecent)
        val toKeep = current.takeLast(preserveRecent)
        totalRemoved = toCompact.size

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
