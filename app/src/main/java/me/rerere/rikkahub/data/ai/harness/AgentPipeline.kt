package me.rerere.rikkahub.data.ai.harness

import android.util.Log
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.compaction.AutoCompactor
import me.rerere.rikkahub.data.ai.listener.AgentEvent
import me.rerere.rikkahub.data.ai.listener.AgentEventBus
import me.rerere.rikkahub.data.ai.scheduler.CronScheduler
import me.rerere.rikkahub.data.ai.agent.AgentMemoryManager
import me.rerere.rikkahub.data.ai.tools.AgentMemoryScope
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.model.AssistantMemory

private const val TAG = "AgentPipeline"

/**
 * Agent Pipeline — 对标 learn-claude-code s20 的 agent_loop。
 *
 * 把整个生成流程做成显式管线，每步清楚可见：
 *
 * ```
 * pipeline():
 *   ┌─ 0. Inject cron jobs
 *   ├─ 1. Inject background notifications
 *   ├─ 2. Prepare context (compact)
 *   ├─ 3. Select relevant memories
 *   ├─ 4. LLM call (withRetry + max_tokens escalation)
 *   ├─ 5. For each tool_use:
 *   │    ├─ 5a. notify_subagent_start   (s04 SubagentStart)
 *   │    ├─ 5b. is_slow_operation → bg  (s13 should_run_background)
 *   │    ├─ 5c. PreToolUse hooks        (s04 permission hook)
 *   │    ├─ 5d. Execute tool
 *   │    └─ 5e. PostToolUse hooks       (s04 log hook)
 *   ├─ 6. Extract memories              (s09 extract_memories)
 *   └─ 7. Repeat
 * ```
 *
 * 用法：ChatService 调用 pipeline() 替代直接调 GenerationHandler.generateText()。
 * 管线不替代 GenerationHandler，而是它的外层编排层。
 */
class AgentPipeline(
    private val autoCompactor: AutoCompactor? = null,
    private val memoryManager: AgentMemoryManager? = null,
    private val memoryRepository: MemoryRepository? = null,
) {
    companion object {
        private val slowKeywords = listOf(
            "install", "build", "test", "deploy", "compile",
            "pip install", "npm install", "cargo build",
            "pytest", "make", "gradle", "mvn",
        )
    }

    /**
     * 判断是否应后台执行 — 对标 s20 should_run_background。
     */
    fun isSlowOperation(toolName: String, command: String): Boolean {
        if (toolName != "execute_command" && toolName != "bash") return false
        return slowKeywords.any { command.lowercase().contains(it) }
    }

    /**
     * [s20 Step 0] 注入 cron 任务。
     * 在每次 LLM 调用前，将触发的 cron 任务作为系统消息注入。
     */
    private fun injectCronJobs(messages: List<UIMessage>): List<UIMessage> {
        val cronJobs = CronScheduler.consumeQueue()
        if (cronJobs.isEmpty()) return messages
        val cronMessages = cronJobs.map { job ->
            UIMessage.system("[Scheduled] ${job.prompt}")
        }
        Log.i(TAG, "[cron] injected ${cronJobs.size} job(s)")
        return messages + cronMessages
    }

    /**
     * [s20 Step 1] 注入后台任务通知。
     */
    private fun injectBackgroundNotifications(messages: List<UIMessage>): List<UIMessage> {
        // 后台通知由外部收集后注入，此处为预留点位
        return messages
    }

    /**
     * [s20 Step 2] 准备上下文 — 压缩管线。
     * 执行：toolResultBudget → snipCompact → microCompact → truncateToolOutput
     */
    private fun prepareContext(messages: List<UIMessage>): List<UIMessage> {
        val compactor = autoCompactor ?: return messages
        var current = compactor.toolResultBudget(messages)
        val snipResult = compactor.snipCompact(current)
        current = snipResult.messages
        current = compactor.microCompact(current)
        current = compactor.truncateToolOutput(current)

        if (compactor.estimateTokens(current) > AutoCompactor.DEFAULT_THRESHOLD_TOKENS) {
            val result = compactor.maybeCompact(current)
            if (result != null) {
                AgentEventBus.emit(AgentEvent.CompactTriggered(
                    reason = "threshold exceeded",
                    messagesBefore = messages.size,
                    messagesAfter = result.compactedMessages.size,
                ))
                return result.compactedMessages
            }
        }
        return current
    }

    /**
     * [s20 Step 3] 侧选记忆 — 只注入相关记忆。
     */
    private fun selectRelevantMemories(
        allMemories: List<AssistantMemory>,
        recentMessages: List<UIMessage>,
    ): List<AssistantMemory> {
        if (allMemories.size <= 5) return allMemories
        // 简版：取最近 3 条用户消息做关键词匹配
        val recentText = recentMessages.takeLast(3)
            .flatMap { it.parts }
            .filterIsInstance<UIMessagePart.Text>()
            .joinToString(" ") { it.text }
            .lowercase()
            .take(500)
        if (recentText.isBlank()) return allMemories.take(3)

        val keywords = recentText.split("\\s+".toRegex())
            .filter { it.length > 3 }
            .toSet()
        if (keywords.isEmpty()) return allMemories.take(3)

        val scored = allMemories.mapNotNull { mem ->
            val score = keywords.count { kw -> mem.content.lowercase().contains(kw) }
            if (score > 0) mem to score else null
        }.sortedByDescending { it.second }

        return if (scored.isEmpty()) allMemories.take(3)
        else scored.take(5).map { it.first }
    }

    /**
     * [s20 Step 5b] 构建后台任务占位结果。
     * 当 isSlowOperation 返回 true 时，返回占位符替代实际执行。
     */
    fun buildBackgroundPlaceholder(toolName: String, command: String, bgId: String): String {
        return buildString {
            appendLine("[Background task $bgId started]")
            appendLine("Command: ${command.take(200)}")
            appendLine("Result will be available as a task_notification when complete.")
            appendLine("Continue with other work while this runs in the background.")
        }
    }

    /**
     * [s20 Step 6] 从对话提取记忆 — 对标 s09 extract_memories。
     * 调用外部 LLM 提取新知识。
     */
    suspend fun extractMemories(
        messages: List<UIMessage>,
        assistantId: String,
        llmExtract: suspend (String) -> String?,
    ) {
        val manager = memoryManager ?: return
        try {
            val dialogue = messages.takeLast(10).joinToString("\n") { msg ->
                val role = when (msg.role) {
                    me.rerere.ai.core.MessageRole.USER -> "user"
                    me.rerere.ai.core.MessageRole.ASSISTANT -> "assistant"
                    else -> "system"
                }
                val text = msg.parts.joinToString(" ") { part ->
                    when (part) { is UIMessagePart.Text -> part.text; else -> "" }
                }
                if (text.isNotBlank()) "$role: $text" else ""
            }
            if (dialogue.isBlank() || dialogue.length < 200) return

            val result = llmExtract(dialogue)
            if (result.isNullOrBlank()) return

            manager.saveMemory(assistantId, AgentMemoryScope.USER, result.take(500))
            Log.i(TAG, "Extracted memory: ${result.take(100)}")
        } catch (e: Exception) {
            Log.w(TAG, "Memory extraction failed: ${e.message}")
        }
    }
}
