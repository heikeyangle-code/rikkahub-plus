package me.rerere.rikkahub.data.ai.harness

import android.util.Log
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.compaction.AutoCompactor
import me.rerere.rikkahub.data.ai.listener.AgentEvent
import me.rerere.rikkahub.data.ai.listener.AgentEventBus
import me.rerere.rikkahub.data.ai.agent.AgentMemoryManager
import me.rerere.rikkahub.data.ai.tools.AgentMemoryScope
import me.rerere.rikkahub.data.ai.hooks.HookEvent
import me.rerere.rikkahub.data.ai.hooks.HookRegistry
import me.rerere.rikkahub.data.ai.hooks.HookResult
import me.rerere.rikkahub.data.repository.MemoryRepository

private const val TAG = "AgentHarness"

/**
 * 统一 Agent Harness — 对标 learn-claude-code s20 的 agent_loop。
 *
 * 显式管线，每个步骤一目了然：
 *   1. prepareContext — 压缩管线 (L2b→L1→L2)
 *   2. selectMemories — side-query 记忆选择（只注入相关的）
 *   3. extractMemories — 从对话提取新记忆
 *   4. isSlowOperation — 后台任务自动分发
 *   5. buildBackgroundNotification — 后台结果通知格式化
 *
 * 不替代现有 GenerationHandler，而是它的 READABLE 包装层。
 * 所有实际执行逻辑仍由现有组件完成。
 */
class AgentHarness(
    private val autoCompactor: AutoCompactor? = null,
    private val memoryManager: AgentMemoryManager? = null,
    private val memoryRepository: MemoryRepository? = null,
) {

    /**
     * 压缩管线 — 对标 s20 prepare_context。
     * 执行顺序：toolResultBudget → snipCompact → microCompact → truncateToolOutput
     */
    fun prepareContext(messages: List<UIMessage>): List<UIMessage> {
        val compactor = autoCompactor ?: return messages

        var current = compactor.toolResultBudget(messages)
        val snipResult = compactor.snipCompact(current)
        current = snipResult.messages
        current = compactor.microCompact(current)
        current = compactor.truncateToolOutput(current)

        if (compactor.estimateTokens(current) > AutoCompactor.DEFAULT_THRESHOLD_TOKENS) {
            val result = compactor.maybeCompact(current)
            if (result != null) {
                Log.i(TAG, "Compact triggered: ${result.removedCount} messages removed")
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
     * Hook 管线 — 对标 s20 trigger_hooks。
     */
    suspend fun triggerHooks(event: HookEvent, vararg args: Any): HookResult? {
        for (hook in HookRegistry.getHooks(event)) {
            when (event) {
                HookEvent.PRE_TOOL_USE -> { /* PolicyEngine 已在 GenerationHandler 中处理 */ }
                HookEvent.USER_PROMPT_SUBMIT -> { /* AgentService 已处理 */ }
                else -> {}
            }
        }
        return null
    }

    /**
     * 后台自动分发 — 对标 s20 should_run_background + is_slow_operation。
     * 检测慢命令并建议后台执行。
     */
    fun isSlowOperation(toolName: String, command: String): Boolean {
        if (toolName != "execute_command" && toolName != "bash") return false
        val slowKeywords = listOf(
            "install", "build", "test", "deploy", "compile",
            "pip install", "npm install", "cargo build",
            "pytest", "make", "gradle", "mvn",
        )
        return slowKeywords.any { command.lowercase().contains(it) }
    }

    /**
     * 后台结果通知 — 对标 s20 collect_background_results 的输出格式。
     */
    fun buildBackgroundNotification(bgId: String, command: String, output: String): String {
        val summary = output.take(200).ifEmpty { output }
        return "<task_notification>\n" +
               "  <task_id>$bgId</task_id>\n" +
               "  <status>completed</status>\n" +
               "  <command>$command</command>\n" +
               "  <summary>$summary</summary>\n" +
               "</task_notification>"
    }

    /**
     * 记忆提取 — 对标 s09 extract_memories。
     * 每轮结束后自动从对话提取新记忆。
     */
    suspend fun extractMemoriesFromDialogue(
        messages: List<UIMessage>,
        agentType: String,
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
            if (dialogue.isBlank()) return

            val result = llmExtract(dialogue)
            if (result.isNullOrBlank()) return

            manager.saveMemory(agentType, AgentMemoryScope.USER, result.take(500))
            Log.i(TAG, "Extracted memory: ${result.take(100)}")
        } catch (e: Exception) {
            Log.w(TAG, "Memory extraction failed: ${e.message}")
        }
    }

    /**
     * 记忆侧选 (side-query) — 对标 s09 select_relevant_memories。
     * 只注入与当前对话相关的记忆，减少 token 浪费。
     *
     * @param allMemories 所有记忆
     * @param recentUserMessages 最近 N 条用户消息
     * @param llmSelect 调用 LLM 选择相关记忆的 key
     * @return 筛选后的记忆列表
     */
    suspend fun selectRelevantMemories(
        allMemories: List<me.rerere.rikkahub.data.model.AssistantMemory>,
        recentUserMessages: List<String>,
        llmSelect: suspend (String) -> List<String>?,
    ): List<me.rerere.rikkahub.data.model.AssistantMemory> {
        if (allMemories.isEmpty()) return emptyList()
        if (allMemories.size <= 5) return allMemories // 少于 5 条不筛

        val recent = recentUserMessages.joinToString(" ").take(1000)
        if (recent.isBlank()) return allMemories.take(5)

        // 构建记忆目录供 LLM 选择
        val catalog = allMemories.mapIndexed { i, mem ->
            "$i: ${mem.content.take(100)}"
        }.joinToString("\n")

        val prompt = "Recent conversation: $recent\n\nMemory catalog:\n$catalog\n\nSelect indices of relevant memories as JSON array."
        val selected = llmSelect(prompt)

        if (selected == null || selected.isEmpty()) return allMemories.take(3)
        return allMemories.filter { mem ->
            selected.any { mem.content.contains(it, ignoreCase = true) }
        }.ifEmpty { allMemories.take(3) }
    }
}
