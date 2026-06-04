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
 * 四层上下文压缩（s08 标准 + s08+ 增强）。
 *
 * L1: snip_compact — 裁掉无关的旧对话（最便宜，无API调用）
 * L2: micro_compact — 旧工具结果替换为占位符（最便宜，无API调用）
 * L2b: tool_result_budget — 大结果持久化到磁盘 + preview
 * L3: context_trim — 裁剪旧消息，保留最近 N 条（免费，无API调用）
 * L4: emergency trim — API 拒绝 prompt_too_long 时的应急裁剪
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
        const val MICRO_COMPACT_MAX_LENGTH = 120
    }

    private var toolResultsDir: File? = null
    private var transcriptDir: File? = null

    fun setToolResultsDir(dir: File) { toolResultsDir = dir; dir.mkdirs() }
    fun setTranscriptDir(dir: File) { transcriptDir = dir; dir.mkdirs() }

    fun estimateTokens(messages: List<UIMessage>): Int {
        return messages.sumOf { msg ->
            msg.parts.sumOf { part ->
                when (part) {
                    is UIMessagePart.Text -> maxOf(1, (part.text.length * 0.75).toInt())
                    is UIMessagePart.Reasoning -> maxOf(1, (part.reasoning.length * 0.75).toInt())
                    is UIMessagePart.Image -> 170
                    is UIMessagePart.Tool -> {
                        maxOf(1, (part.input.length * 0.75).toInt()) +
                        part.output.sumOf {
                            when (it) {
                                is UIMessagePart.Text -> maxOf(1, (it.text.length * 0.75).toInt())
                                is UIMessagePart.Image -> 170
                                else -> 0
                            }
                        }
                    }
                    else -> 0
                }
            }
        }
    }

    /**
     * L1: Snip compact — 裁掉中间无关的旧对话。
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
     * L2: Micro compact — 将旧的工具结果替换为占位符。
     * 对标 learn-claude-code s08 micro_compact。
     */
    fun microCompact(messages: List<UIMessage>): List<UIMessage> {
        val toolResults = mutableListOf<Triple<Int, Int, UIMessagePart.Tool>>()
        for ((mi, msg) in messages.withIndex()) {
            for ((bi, part) in msg.parts.withIndex()) {
                if (part is UIMessagePart.Tool) {
                    toolResults.add(Triple(mi, bi, part))
                }
            }
        }
        if (toolResults.size <= MICRO_COMPACT_KEEP_RECENT) return messages

        val toCompact = toolResults.dropLast(MICRO_COMPACT_KEEP_RECENT)
        val result = messages.toMutableList()
        for ((mi, bi, toolPart) in toCompact) {
            val totalLen = toolPart.output.sumOf { out ->
                when (out) { is UIMessagePart.Text -> out.text.length; else -> 0 }
            }
            if (totalLen > MICRO_COMPACT_MAX_LENGTH) {
                val msg = result[mi]
                val newParts = msg.parts.toMutableList()
                newParts[bi] = UIMessagePart.Tool(
                    toolCallId = toolPart.toolCallId,
                    toolName = toolPart.toolName,
                    input = toolPart.input,
                    output = listOf(UIMessagePart.Text("[Earlier tool output compacted. Re-run if needed.]")),
                    approvalState = toolPart.approvalState,
                )
                result[mi] = UIMessage(
                    role = msg.role,
                    parts = newParts,
                    createdAt = msg.createdAt,
                    modelId = msg.modelId,
                    usage = msg.usage,
                )
            }
        }
        return result
    }

    /**
     * Persist large tool output to disk, return preview reference.
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
     * L2b: tool_result_budget — 过大结果持久化到磁盘。
     */
    fun toolResultBudget(messages: List<UIMessage>): List<UIMessage> {
        return messages.map { msg ->
            val newParts = msg.parts.map { part ->
                if (part is UIMessagePart.Tool) {
                    val totalLen = part.output.sumOf { out ->
                        when (out) { is UIMessagePart.Text -> out.text.length; else -> 0 }
                    }
                    if (totalLen > TOOL_RESULT_BUDGET_BYTES) {
                        val newOutput = part.output.map { out ->
                            if (out is UIMessagePart.Text && out.text.length > PERSIST_THRESHOLD_BYTES) {
                                UIMessagePart.Text(persistLargeOutput(part.toolCallId, out.text))
                            } else out
                        }
                        UIMessagePart.Tool(
                            toolCallId = part.toolCallId, toolName = part.toolName,
                            input = part.input, output = newOutput, approvalState = part.approvalState,
                        )
                    } else part
                } else part
            }
            UIMessage(role = msg.role, parts = newParts, createdAt = msg.createdAt,
                      modelId = msg.modelId, usage = msg.usage)
        }
    }

    /**
     * Write transcript to disk.
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
     */
    fun truncateToolOutput(
        messages: List<UIMessage>,
        maxChars: Int = TOOL_OUTPUT_MAX_CHARS,
    ): List<UIMessage> {
        val skipTools = setOf("file_read", "file_write", "present_file", "convert_file")
        return messages.map { msg ->
            val newParts = msg.parts.map { part ->
                if (part is UIMessagePart.Tool && part.toolName !in skipTools) {
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
     * 完整压缩管线。
     *
     * 执行顺序: toolResultBudget(L2b) → snipCompact(L1) → microCompact(L2) → truncateToolOutput(L2)
     * → if still over threshold: trim(L3) → if still over: emergencyTrim(L4)
     */
    fun maybeCompact(
        messages: List<UIMessage>,
        threshold: Int = DEFAULT_THRESHOLD_TOKENS,
        preserveRecent: Int = PRESERVE_RECENT_MESSAGES,
    ): CompactionResult? {
        var current = messages
        var totalRemoved = 0

        // Step 0: toolResultBudget — 大结果持久化
        current = toolResultBudget(current)

        // Step 1: L1 snipCompact — 裁中间
        val snipResult = snipCompact(current, preserveRecent)
        totalRemoved += snipResult.removedCount
        current = snipResult.messages

        // Step 2: L2 microCompact — 旧结果占位符
        current = microCompact(current)

        // Step 3: L2 truncateToolOutput — 截断工具输出
        current = truncateToolOutput(current)

        var estimated = estimateTokens(current)
        if (estimated <= threshold) {
            val summary = buildString {
                appendLine("=== Context compacted ===")
                appendLine("Tool outputs capped at ${TOOL_OUTPUT_MAX_CHARS} chars each")
                appendLine("Estimated tokens: $estimated / $threshold")
                if (totalRemoved > 0) appendLine("Messages removed: $totalRemoved")
            }
            return CompactionResult(totalRemoved, summary, current)
        }

        // Step 4: L3 context trim
        if (current.size <= preserveRecent * 2) return null

        val toCompact = current.dropLast(preserveRecent)
        val toKeep = current.takeLast(preserveRecent)
        val trimRemoved = toCompact.size

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

        estimated = estimateTokens(compacted)
        if (estimated > threshold) {
            val trimmed = emergencyTrim(compacted, preserveRecent)
            return CompactionResult(
                removedCount = totalRemoved + trimRemoved + (compacted.size - trimmed.size),
                summary = summary + "\n(emergency trim applied)",
                compactedMessages = trimmed,
            )
        }

        return CompactionResult(
            removedCount = totalRemoved + trimRemoved,
            summary = summary,
            compactedMessages = compacted,
        )
    }
}
