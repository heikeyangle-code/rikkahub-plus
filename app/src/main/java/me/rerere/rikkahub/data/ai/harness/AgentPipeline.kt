package me.rerere.rikkahub.data.ai.harness

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.agent.BackgroundTaskQueue
import me.rerere.rikkahub.data.ai.tools.TaskManager
import me.rerere.rikkahub.data.ai.tools.PlanManager
import me.rerere.rikkahub.data.ai.compaction.AutoCompactor
import me.rerere.rikkahub.data.ai.listener.AgentEvent
import me.rerere.rikkahub.data.ai.listener.AgentEventBus
import me.rerere.rikkahub.data.ai.policy.PolicyEngine
import me.rerere.rikkahub.data.ai.scheduler.CronScheduler
import me.rerere.rikkahub.data.ai.transformers.ContextInjectorTransformer
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OutputMessageTransformer
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.repository.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

private const val TAG = "AgentPipeline"

/**
 * Agent Pipeline — 对标 learn-claude-code s20 的 agent_loop。
 *
 * 管线步骤：
 *   0. prepareContext — 压缩
 *   1. selectRelevantMemories — side-query 侧选记忆
 *   2. contextInject — 通过 InputMessageTransformer 注入 cron/后台/todo/状态
 *   3. LLM call (委托 GenerationHandler)
 *   4. extractMemories — LLM 自动从对话提取记忆（只增加不删除）
 */
class AgentPipeline(
    private val generationHandler: GenerationHandler,
    private val providerManager: ProviderManager,
    private val memoryRepository: MemoryRepository? = null,
) {
    companion object {
        private val slowKeywords = listOf(
            "install", "build", "test", "deploy", "compile",
            "pip install", "npm install", "cargo build",
            "pytest", "make", "gradle", "mvn",
        )
        private val json = Json { ignoreUnknownKeys = true }
        private const val EXTRACT_EVERY_N_TURNS = 5
    }

    private val extractTurnCounter = java.util.concurrent.atomic.AtomicInteger(0)

    fun isSlowOperation(toolName: String, command: String): Boolean {
        if (toolName != "execute_command" && toolName != "bash") return false
        return slowKeywords.any { command.lowercase().contains(it) }
    }

    /** 管线入口 */
    fun run(
        settings: Settings, model: Model,
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
        var pipelineMessages = messages
        pipelineMessages = prepareContext(pipelineMessages, autoCompactor)

        val selectedMemories = selectRelevantMemories(memories ?: emptyList(), pipelineMessages)
        val assistantId = if (assistant.useGlobalMemory) "__global__" else assistant.id.toString()

        // ── 收集注入数据（不污染 pipelineMessages）──
        val cronJobs = CronScheduler.consumeQueue()
        val bgNotifications = BackgroundTaskQueue.collectCompleted()
        val shouldNag = PlanManager.shouldNag()
        if (shouldNag) PlanManager.resetNag()
        val tasks = TaskManager.listTasks()
        val taskCount = tasks.size
        val bgRunning = BackgroundTaskQueue.hasRunning()
        if (cronJobs.isNotEmpty()) Log.i(TAG, "[cron] injected ${cronJobs.size} job(s)")
        if (shouldNag) Log.i(TAG, "[todo] injected reminder")
        if (taskCount > 0 || bgRunning) Log.i(TAG, "[state] summary active")

        val contextInjector = ContextInjectorTransformer(
            cronMessages = cronJobs.map { it.prompt },
            backgroundNotifications = bgNotifications,
            todoReminder = if (shouldNag)
                "<reminder>Update your todo list with todo_write to track current progress.</reminder>"
            else null,
            stateSummary = if (taskCount > 0 || bgRunning) {
                buildString {
                    append("[State: ${taskCount}tasks")
                    val pendingCount = tasks.count { it.status.name == "PENDING" }
                    val runningCount = tasks.count { it.status.name == "IN_PROGRESS" }
                    if (pendingCount > 0) append(", ${pendingCount}pending")
                    if (runningCount > 0) append(", ${runningCount}running")
                    append(" | ${if (bgRunning) "1 bg" else "bg idle"}]")
                }
            } else null,
        )

        val flow = generationHandler.generateText(
            settings = settings, model = model,
            messages = pipelineMessages,
            inputTransformers = inputTransformers + contextInjector,
            outputTransformers = outputTransformers,
            assistant = assistant, memories = selectedMemories,
            tools = tools, maxSteps = maxSteps,
            processingStatus = processingStatus,
            conversationSystemPrompt = conversationSystemPrompt,
            conversationModeInjectionIds = conversationModeInjectionIds,
            conversationLorebookIds = conversationLorebookIds,
            policyEngine = policyEngine, autoCompactor = autoCompactor,
        )

        // emit 后提取记忆（只添加不删除）—— 只在流式完成后提取一次
        var lastMessages: List<UIMessage>? = null
        return flow
            .onEach { chunk ->
                if (chunk is GenerationChunk.Messages) {
                    lastMessages = chunk.messages
                }
            }
            .onCompletion {
                val msgs = lastMessages ?: return@onCompletion
                if (memoryRepository != null && assistant.enableAutoMemoryExtract) {
                    val last = msgs.lastOrNull()
                    if (last != null && (last.role == MessageRole.ASSISTANT || last.role == MessageRole.USER)) {
                        // 节流1：每 N 轮才提取一次
                        val turn = extractTurnCounter.incrementAndGet()
                        if (turn % EXTRACT_EVERY_N_TURNS != 0) return@onCompletion
                        // 节流2：最后一轮有工具调用说明还在干活，纯对话才值得提取
                        if (last.getTools().isNotEmpty()) return@onCompletion
                        val dialogue = buildDialogueText(msgs)
                        if (dialogue.length >= 200) {
                            extractMemoriesWithLLM(settings, model, assistantId, dialogue)
                        }
                    }
                }
            }
    }

    // ═══════════════════ 管线步骤 ═══════════════════

    private fun prepareContext(messages: List<UIMessage>, autoCompactor: AutoCompactor?): List<UIMessage> {
        val compactor = autoCompactor ?: return messages
        var current = compactor.toolResultBudget(messages)
        current = compactor.microCompact(current)
        current = compactor.truncateToolOutput(current)

        if (compactor.estimateTokens(current) > AutoCompactor.DEFAULT_THRESHOLD_TOKENS) {
            val result = compactor.maybeCompact(current)
            if (result != null) {
                AgentEventBus.tryEmit(AgentEvent.CompactTriggered(
                    reason = "threshold exceeded",
                    messagesBefore = messages.size, messagesAfter = result.compactedMessages.size,
                ))
                return result.compactedMessages
            }
        }
        return current
    }

    private fun selectRelevantMemories(
        allMemories: List<AssistantMemory>, recentMessages: List<UIMessage>,
    ): List<AssistantMemory> {
        if (allMemories.size <= 5) return allMemories
        val recentText = recentMessages.takeLast(3)
            .flatMap { it.parts }.filterIsInstance<UIMessagePart.Text>()
            .joinToString(" ") { it.text }.lowercase().take(500)
        if (recentText.isBlank()) return allMemories.take(3)
        val keywords = recentText.split("\\s+".toRegex()).filter { it.length > 3 }.toSet()
        if (keywords.isEmpty()) return allMemories.take(3)
        val scored = allMemories.mapNotNull { m ->
            val s = keywords.count { m.content.lowercase().contains(it) }
            if (s > 0) m to s else null
        }.sortedByDescending { it.second }
        return if (scored.isEmpty()) allMemories.take(3) else scored.take(5).map { it.first }
    }

    fun buildBackgroundPlaceholder(toolName: String, command: String, bgId: String): String = buildString {
        appendLine("[Background task $bgId started]")
        appendLine("Command: ${command.take(200)}")
        appendLine("Result will arrive as task_notification.")
    }

    // ═══════════════════ LLM 辅助调用 ═══════════════════

    private fun buildDialogueText(messages: List<UIMessage>): String =
        messages.takeLast(10).joinToString("\n") { msg ->
            val role = when (msg.role) { MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"; else -> "system" }
            val text = msg.parts.joinToString(" ") { p ->
                when (p) { is UIMessagePart.Text -> p.text; else -> "" }
            }
            if (text.isNotBlank()) "$role: $text" else ""
        }

    private suspend fun callLLM(
        settings: Settings, model: Model, prompt: String, maxTokens: Int = 2000,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val provider = model.findProvider(settings.providers) ?: return@withContext null
            val impl = providerManager.getProviderByType(provider) ?: return@withContext null
            val msgs = listOf(UIMessage.user(prompt))
            val result = impl.generateText(
                providerSetting = provider, messages = msgs,
                params = TextGenerationParams(
                    model = model, tools = emptyList(),
                    maxTokens = maxTokens, reasoningLevel = ReasoningLevel.OFF,
                ),
            )
            result.choices.firstOrNull()?.message?.toText()
        } catch (e: Exception) {
            Log.w(TAG, "LLM call failed: ${e.message}"); null
        }
    }

    /**
     * 提取记忆 — 对标 s09 extract_memories。
     * 用 LLM 从最近对话中提取用户偏好/项目事实，追加到记忆库。
     * 只增加不删除，不影响 AI 手动写的记忆。
     */
    private suspend fun extractMemoriesWithLLM(
        settings: Settings, model: Model, assistantId: String, dialogue: String,
    ) {
        val repo = memoryRepository ?: return

        val existing = repo.getMemoriesOfAssistant(assistantId)
        val existingDesc = if (existing.isEmpty()) "(none)"
            else existing.joinToString("\n") { "- ${it.content.take(100)}" }

        val prompt = buildString {
            appendLine("Extract user preferences, constraints, or project facts from this dialogue.")
            appendLine("Return a JSON array. Each item: {type, description, body}.")
            appendLine("type: one of 'user' (preference), 'feedback' (guidance), 'project' (fact)")
            appendLine("description: one-line summary")
            appendLine("body: full detail")
            appendLine("If nothing new or already covered by existing memories, return [].")
            appendLine()
            appendLine("Existing memories:")
            appendLine(existingDesc)
            appendLine()
            appendLine("Dialogue:")
            appendLine(dialogue.take(4000))
        }

        val response = callLLM(settings, model, prompt, 800)
        if (response.isNullOrBlank()) return

        try {
            // 提取 JSON 数组
            val start = response.indexOf('[')
            val end = response.lastIndexOf(']')
            if (start < 0 || end < 0) return
            val jsonText = response.substring(start, end + 1)
            val items = json.parseToJsonElement(jsonText).jsonArray

            var count = 0
            for (item in items) {
                val obj = item.jsonObject
                val desc = obj["description"]?.jsonPrimitive?.content ?: continue
                val body = obj["body"]?.jsonPrimitive?.content ?: continue
                if (desc.isNotBlank() && body.isNotBlank()) {
                    repo.addMemory(assistantId, "$desc\n$body")
                    count++
                }
            }
            if (count > 0) Log.i(TAG, "[memory] extracted $count new memories")
        } catch (e: Exception) {
            Log.w(TAG, "Memory parse failed: ${e.message}")
        }
    }
}
