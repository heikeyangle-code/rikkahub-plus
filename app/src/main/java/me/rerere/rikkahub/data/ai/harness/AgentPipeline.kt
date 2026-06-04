package me.rerere.rikkahub.data.ai.harness

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
import me.rerere.rikkahub.data.ai.compaction.AutoCompactor
import me.rerere.rikkahub.data.ai.listener.AgentEvent
import me.rerere.rikkahub.data.ai.listener.AgentEventBus
import me.rerere.rikkahub.data.ai.policy.PolicyEngine
import me.rerere.rikkahub.data.ai.scheduler.CronScheduler
import me.rerere.rikkahub.data.ai.tools.AgentMemoryScope
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
 * 完整管线：
 *   0. injectCronJobs — cron 任务注入
 *   1. injectBackgroundNotifications — 后台结果注入
 *   2. prepareContext — 压缩 (toolResultBudget→snip→micro→truncate→LLM summary)
 *   3. selectRelevantMemories — side-query 侧选记忆
 *   4. LLM call (委托 GenerationHandler)
 *   5. extractMemories — LLM 自动从对话提取记忆
 *   6. consolidateMemories — 记忆数>10时去重整理（Dream）
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
        private const val CONSOLIDATE_THRESHOLD = 10
        private val json = Json { ignoreUnknownKeys = true }
    }

    fun isSlowOperation(toolName: String, command: String): Boolean {
        if (toolName != "execute_command" && toolName != "bash") return false
        return slowKeywords.any { command.lowercase().contains(it) }
    }

    /** 管线入口 — 对标 s20 agent_loop */
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

        // [s20 Step 2] 压缩（含 LLM 摘要压过阈值时）
        pipelineMessages = prepareContext(pipelineMessages, autoCompactor)

        // [s20 Step 3] 侧选记忆（只注入相关）
        val selectedMemories = selectRelevantMemories(memories ?: emptyList(), pipelineMessages)

        // [s20 Step 4] 委托 GenerationHandler 执行 LLM + 工具循环
        val assistantId = if (assistant.useGlobalMemory) "__global__" else assistant.id.toString()
        val flow = generationHandler.generateText(
            settings = settings, model = model,
            messages = pipelineMessages,
            inputTransformers = inputTransformers,
            outputTransformers = outputTransformers,
            assistant = assistant,
            memories = selectedMemories,
            tools = tools, maxSteps = maxSteps,
            processingStatus = processingStatus,
            conversationSystemPrompt = conversationSystemPrompt,
            conversationModeInjectionIds = conversationModeInjectionIds,
            conversationLorebookIds = conversationLorebookIds,
            policyEngine = policyEngine,
            autoCompactor = autoCompactor,
        )

        // [s20 Step 6] emit 后提取记忆 + 整理
        return flow.map { chunk ->
            if (chunk is GenerationChunk.Messages && memoryRepository != null) {
                try {
                    val msgs = chunk.messages
                    val last = msgs.lastOrNull()
                    if (last != null && (last.role == MessageRole.ASSISTANT || last.role == MessageRole.USER)) {
                        val dialogue = buildDialogueText(msgs)
                        if (dialogue.length >= 200) {
                            extractMemoriesWithLLM(settings, model, assistantId, dialogue)
                        }
                    }
                } catch (_: Exception) { }
            }
            chunk
        }
    }

    // ═══════════════════════════════════════════════
    //  管线步骤
    // ═══════════════════════════════════════════════

    /** [s20 Step 0] cron 注入 */
    private fun injectCronJobs(messages: List<UIMessage>): List<UIMessage> {
        val cronJobs = CronScheduler.consumeQueue()
        if (cronJobs.isEmpty()) return messages
        return messages + cronJobs.map { UIMessage.system("[Scheduled] ${it.prompt}") }
            .also { Log.i(TAG, "[cron] injected ${cronJobs.size} job(s)") }
    }

    /** [s20 Step 1] 后台通知注入（预留） */
    private fun injectBackgroundNotifications(messages: List<UIMessage>): List<UIMessage> = messages

    /** [s20 Step 2] 压缩管线 — 超阈值时用 LLM 摘要替代文字拼接 */
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

    /** [s20 Step 3] 侧选记忆 — 关键词匹配，只注入相关（>5条时） */
    private fun selectRelevantMemories(
        allMemories: List<AssistantMemory>,
        recentMessages: List<UIMessage>,
    ): List<AssistantMemory> {
        if (allMemories.size <= 5) return allMemories
        val recentText = recentMessages.takeLast(3)
            .flatMap { it.parts }
            .filterIsInstance<UIMessagePart.Text>()
            .joinToString(" ") { it.text }
            .lowercase().take(500)
        if (recentText.isBlank()) return allMemories.take(3)
        val keywords = recentText.split("\\s+".toRegex()).filter { it.length > 3 }.toSet()
        if (keywords.isEmpty()) return allMemories.take(3)
        val scored = allMemories.mapNotNull { m ->
            val s = keywords.count { m.content.lowercase().contains(it) }
            if (s > 0) m to s else null
        }.sortedByDescending { it.second }
        return if (scored.isEmpty()) allMemories.take(3) else scored.take(5).map { it.first }
    }

    /** 占位符结果 — 对标 s20 后台任务 */
    fun buildBackgroundPlaceholder(toolName: String, command: String, bgId: String): String = buildString {
        appendLine("[Background task $bgId started]")
        appendLine("Command: ${command.take(200)}")
        appendLine("Result will arrive as task_notification.")
    }

    // ═══════════════════════════════════════════════
    //  LLM 辅助调用（s08 摘要 + s09 记忆提取/整理）
    // ═══════════════════════════════════════════════

    /** 构建对话文本（s09/s20 共用） */
    private fun buildDialogueText(messages: List<UIMessage>): String =
        messages.takeLast(10).joinToString("\n") { msg ->
            val role = when (msg.role) { MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"; else -> "system" }
            val text = msg.parts.joinToString(" ") { p ->
                when (p) { is UIMessagePart.Text -> p.text; else -> "" }
            }
            if (text.isNotBlank()) "$role: $text" else ""
        }

    /** 简单 LLM 调用（非流式），用于辅助任务 */
    private suspend fun callLLM(
        settings: Settings, model: Model, prompt: String, maxTokens: Int = 2000,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val provider = model.findProvider(settings.providers) ?: return@withContext null
            val impl = providerManager.getProviderByType(provider) ?: return@withContext null
            val msgs = listOf(UIMessage.user(prompt))
            val result = impl.generateText(
                providerSetting = provider,
                messages = msgs,
                params = TextGenerationParams(
                    model = model, tools = emptyList(),
                    maxTokens = maxTokens,
                    reasoningLevel = ReasoningLevel.OFF,
                ),
            )
            result.choices.firstOrNull()?.message?.toText()
        } catch (e: Exception) {
            Log.w(TAG, "LLM call failed: ${e.message}"); null
        }
    }

    /**
     * [s20/s09] 提取记忆 — 对标 s09 extract_memories。
     * 每轮结束后自动从对话提取用户偏好/项目事实。
     */
    private suspend fun extractMemoriesWithLLM(
        settings: Settings, model: Model,
        assistantId: String, dialogue: String,
    ) {
        val repo = memoryRepository ?: return

        // 获取现有记忆避免重复
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
            val jsonText = response.substringAfter('[').substringBeforeLast(']')
            if (jsonText.isBlank()) return
            val items = json.parseToJsonElement("[$jsonText]").jsonArray
            var count = 0
            for (item in items) {
                val obj = item.jsonObject
                val desc = obj["description"]?.jsonPrimitive?.contentOrNull ?: continue
                val body = obj["body"]?.jsonPrimitive?.contentOrNull ?: continue
                if (desc.isNotBlank() && body.isNotBlank()) {
                    repo.addMemory(assistantId, "$desc\n$body")
                    count++
                }
            }
            if (count > 0) {
                Log.i(TAG, "[memory] extracted $count new memories")
                consolidateMemories(settings, model, assistantId)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Memory parse failed: ${e.message}")
        }
    }

    /**
     * [s09] 记忆整理 (Dream) — 对标 s09 consolidate_memories。
     * 当记忆数 ≥ 10 时调用 LLM 去重合并。
     */
    private suspend fun consolidateMemories(
        settings: Settings, model: Model, assistantId: String,
    ) {
        val repo = memoryRepository ?: return
        val allMemories = repo.getMemoriesOfAssistant(assistantId)
        if (allMemories.size < CONSOLIDATE_THRESHOLD) return

        val catalog = allMemories.joinToString("\n\n") { m ->
            "## ${m.id}\n${m.content}"
        }

        val prompt = buildString {
            appendLine("Consolidate these memories. Rules:")
            appendLine("1. Merge duplicates into one")
            appendLine("2. Remove outdated/contradicted ones")
            appendLine("3. Keep under 30 items")
            appendLine("4. Preserve user preferences above all")
            appendLine()
            appendLine("Return JSON array. Each item: {content: string}")
            appendLine()
            appendLine(catalog.take(12000))
        }

        val response = callLLM(settings, model, prompt, 2000)
        if (response.isNullOrBlank()) return

        try {
            val jsonText = response.substringAfter('[').substringBeforeLast(']')
            if (jsonText.isBlank()) return
            val items = json.parseToJsonElement("[$jsonText]").jsonArray

            // 删旧 + 写新
            repo.deleteMemoriesOfAssistant(assistantId)
            var count = 0
            for (item in items) {
                val content = item.jsonObject["content"]?.jsonPrimitive?.contentOrNull
                if (!content.isNullOrBlank()) {
                    repo.addMemory(assistantId, content)
                    count++
                }
            }
            Log.i(TAG, "[memory] consolidated ${allMemories.size} → $count")
        } catch (e: Exception) {
            Log.w(TAG, "Memory consolidate failed: ${e.message}")
        }
    }
}
