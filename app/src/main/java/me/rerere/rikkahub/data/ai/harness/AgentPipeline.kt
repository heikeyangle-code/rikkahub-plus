package me.rerere.rikkahub.data.ai.harness

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.compaction.AutoCompactor
import me.rerere.rikkahub.data.ai.listener.AgentEvent
import me.rerere.rikkahub.data.ai.listener.AgentEventBus
import me.rerere.rikkahub.data.ai.scheduler.CronScheduler
import me.rerere.rikkahub.data.ai.agent.AgentMemoryManager
import me.rerere.rikkahub.data.ai.tools.AgentMemoryScope
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.ai.policy.PolicyEngine
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.Model
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OutputMessageTransformer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

private const val TAG = "AgentPipeline"

/**
 * Agent Pipeline — 对标 learn-claude-code s20 的 agent_loop。
 *
 * 管线步骤（与 s20 完全对齐）：
 *   0. injectCronJobs — cron 任务注入
 *   1. injectBackgroundNotifications — 后台结果注入
 *   2. prepareContext — 压缩 (toolResultBudget→snip→micro→truncate)
 *   3. selectRelevantMemories — 侧选记忆只注入相关
 *   4. LLM call (委托 GenerationHandler)
 *   5. extractMemories — 自动从对话提取新记忆
 *
 * 使用方式：
 *   ChatService 调 agentPipeline.run(...) 替代直接调 generationHandler.generateText()
 */
class AgentPipeline(
    private val generationHandler: GenerationHandler,
    private val providerManager: ProviderManager,
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

    fun isSlowOperation(toolName: String, command: String): Boolean {
        if (toolName != "execute_command" && toolName != "bash") return false
        return slowKeywords.any { command.lowercase().contains(it) }
    }

    /**
     * 管线入口 — 对标 s20 agent_loop。
     *
     * 在委托 GenerationHandler.generateText() 之前/之后，
     * 注入 cron、压缩、记忆侧选、记忆提取。
     */
    fun run(
        settings: Settings,
        model: Model,
        messages: List<UIMessage>,
        inputTransformers: List<InputMessageTransformer> = emptyList(),
        outputTransformers: List<OutputMessageTransformer> = emptyList(),
        assistant: Assistant,
        memories: List<AssistantMemory>? = null,
        tools: List<Tool> = emptyList(),
        maxSteps: Int = 256,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        conversationSystemPrompt: String? = null,
        conversationModeInjectionIds: Set<Uuid> = emptySet(),
        conversationLorebookIds: Set<Uuid> = emptySet(),
        policyEngine: PolicyEngine? = null,
        autoCompactor: AutoCompactor? = null,
    ): Flow<GenerationChunk> {
        // [s20 Step 0] 注入 cron + 后台通知
        var pipelineMessages = messages
        pipelineMessages = injectCronJobs(pipelineMessages)
        pipelineMessages = injectBackgroundNotifications(pipelineMessages)

        // [s20 Step 2] 压缩
        pipelineMessages = prepareContext(pipelineMessages, autoCompactor)

        // [s20 Step 3] 侧选记忆
        val selectedMemories = selectRelevantMemories(memories ?: emptyList(), pipelineMessages)

        // [s20 Step 4] 委托 GenerationHandler 执行 LLM + 工具循环
        val flow = generationHandler.generateText(
            settings = settings,
            model = model,
            messages = pipelineMessages,
            inputTransformers = inputTransformers,
            outputTransformers = outputTransformers,
            assistant = assistant,
            memories = selectedMemories,
            tools = tools,
            maxSteps = maxSteps,
            processingStatus = processingStatus,
            conversationSystemPrompt = conversationSystemPrompt,
            conversationModeInjectionIds = conversationModeInjectionIds,
            conversationLorebookIds = conversationLorebookIds,
            policyEngine = policyEngine,
            autoCompactor = autoCompactor,
        )

        // [s20 Step 6] 返回的 Flow 中每次 emit 后尝试提取记忆
        val assistantId = if (assistant.useGlobalMemory) "__global__" else assistant.id.toString()
        return flow.map { chunk ->
            if (chunk is GenerationChunk.Messages) {
                // 每次有新消息时尝试提取记忆（非阻塞，不抛异常）
                try {
                    if (memoryManager != null) {
                        val msg = chunk.messages.lastOrNull()
                        if (msg != null && (msg.role == me.rerere.ai.core.MessageRole.ASSISTANT || msg.role == me.rerere.ai.core.MessageRole.USER)) {
                            extractMemories(chunk.messages, "agent", null)
                        }
                    }
                } catch (_: Exception) {}
            }
            chunk
        }
    }

    // ── 管线步骤 ──

    private fun injectCronJobs(messages: List<UIMessage>): List<UIMessage> {
        val cronJobs = CronScheduler.consumeQueue()
        if (cronJobs.isEmpty()) return messages
        val cronMessages = cronJobs.map { job ->
            UIMessage.system("[Scheduled] ${job.prompt}")
        }
        Log.i(TAG, "[cron] injected ${cronJobs.size} job(s)")
        return messages + cronMessages
    }

    private fun injectBackgroundNotifications(messages: List<UIMessage>): List<UIMessage> {
        return messages
    }

    private fun prepareContext(messages: List<UIMessage>, autoCompactor: AutoCompactor?): List<UIMessage> {
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

    private fun selectRelevantMemories(
        allMemories: List<AssistantMemory>,
        recentMessages: List<UIMessage>,
    ): List<AssistantMemory> {
        if (allMemories.size <= 5) return allMemories
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

    fun buildBackgroundPlaceholder(toolName: String, command: String, bgId: String): String {
        return buildString {
            appendLine("[Background task $bgId started]")
            appendLine("Command: ${command.take(200)}")
            appendLine("Result will be available as a task_notification when complete.")
            appendLine("Continue with other work while this runs in the background.")
        }
    }

    private fun extractMemories(
        messages: List<UIMessage>,
        agentType: String,
        llmExtract: (suspend (String) -> String?)?,
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
            val result = llmExtract?.invoke(dialogue)
            if (result.isNullOrBlank()) return
            manager.saveMemory(agentType, AgentMemoryScope.USER, result.take(500))
            Log.i(TAG, "Extracted memory: ${result.take(100)}")
        } catch (e: Exception) {
            Log.w(TAG, "Memory extraction failed: ${e.message}")
        }
    }
}
