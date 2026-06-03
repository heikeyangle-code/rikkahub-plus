package me.rerere.rikkahub.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.core.merge
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.ai.ui.limitContext
import me.rerere.rikkahub.data.ai.policy.PermissionResult
import me.rerere.rikkahub.data.ai.policy.PolicyEngine
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.MessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OutputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.onGenerationFinish
import me.rerere.rikkahub.data.ai.transformers.transforms
import me.rerere.rikkahub.data.ai.transformers.visualTransforms
import me.rerere.rikkahub.data.ai.tools.PlanModeState
import me.rerere.rikkahub.data.ai.tools.buildMemoryTools
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.assembleContext
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.utils.applyPlaceholders
import java.util.Locale
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val TAG = "GenerationHandler"

@Serializable
sealed interface GenerationChunk {
    data class Messages(val messages: List<UIMessage>) : GenerationChunk
}

class GenerationHandler(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val json: Json,
    private val memoryRepo: MemoryRepository,
    private val conversationRepo: ConversationRepository,
    private val aiLoggingManager: AILoggingManager,
) {
    fun generateText(
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
    ): Flow<GenerationChunk> = flow {
        val provider = model.findProvider(settings.providers) ?: error("Provider not found")
        val providerImpl = providerManager.getProviderByType(provider)

        var messages: List<UIMessage> = messages

        fun describeTool(name: String): String = when {
            name.startsWith("github_") -> "\uD83D\uDD27 GitHub \u2192 正在操作..."
            name.startsWith("execute_python") -> "\uD83D\uDD27 Python \u2192 正在执行代码..."
            name.startsWith("execute_command") -> "\uD83D\uDD27 Shell \u2192 正在执行命令..."
            name.startsWith("file_") || name.startsWith("git_") -> "\uD83D\uDD27 文件/Git \u2192 正在操作..."
            name.startsWith("data_process") -> "\uD83D\uDD27 数据 \u2192 正在处理..."
            name.startsWith("database_") -> "\uD83D\uDD27 数据库 \u2192 正在查询..."
            name.startsWith("search_web") || name.startsWith("scrape_") -> "\uD83D\uDD27 搜索 \u2192 正在搜索..."
            name.startsWith("convert_file") -> "\uD83D\uDD27 转换 \u2192 正在转换格式..."
            name.startsWith("create_asset") -> "\uD83D\uDD27 创作 \u2192 正在生成..."
            name.startsWith("use_skill") -> "\uD83D\uDD27 知识 \u2192 正在读取..."
            name.startsWith("clipboard") -> "\uD83D\uDD27 剪贴板 \u2192 正在操作..."
            name.startsWith("get_time") -> "\uD83D\uDD27 时间 \u2192 获取中..."
            name.startsWith("text_to_speech") -> "\uD83D\uDD27 语音 \u2192 正在朗读..."
            name.startsWith("present_file") -> "\uD83D\uDD27 文件 \u2192 正在分享..."
            name.startsWith("eval_javascript") -> "\uD83D\uDD27 JS \u2192 正在执行..."
            name.startsWith("memory_") -> "\uD83D\uDD27 记忆 \u2192 正在处理..."
            name.startsWith("worker_") -> "\uD83D\uDD27 Worker \u2192 正在管理..."
            name.startsWith("mcp__") || name.startsWith("list_mcp") || name.startsWith("read_mcp") -> "\uD83D\uDD27 MCP \u2192 正在操作..."
            else -> "\uD83D\uDD27 $name \u2192 正在处理..."
        }

        for (stepIndex in 0 until maxSteps) {
            Log.i(TAG, "streamText: start step #$stepIndex (${model.id})")

            val toolsInternal = buildList {
                if (assistant?.enableMemory == true) {
                    val memoryAssistantId = if (assistant.useGlobalMemory) MemoryRepository.GLOBAL_MEMORY_ID else assistant.id.toString()
                    buildMemoryTools(json = json,
                        onCreation = { memoryRepo.addMemory(memoryAssistantId, it) },
                        onUpdate = { id, content -> memoryRepo.updateContent(id, content) },
                        onDelete = { memoryRepo.deleteMemory(it) },
                    ).let(this::addAll)
                }
                addAll(tools)
            }

            val statusTrackedTools = toolsInternal.map { tool ->
                if (tool.name == "ask_user") tool else tool.copy(
                    execute = { args ->
                        processingStatus.value = describeTool(tool.name)
                        if (tool.name.contains("github")) GhProgress.processingRef = processingStatus
                        try {
                            val result = tool.execute(args)
                            GhProgress.processingRef = null
                            processingStatus.value = null
                            result
                        } catch (e: Exception) {
                            GhProgress.processingRef = null
                            processingStatus.value = null
                            throw e
                        }
                    }
                )
            }

            val pendingTools = messages.lastOrNull()?.getTools()?.filter { it.canResumeExecution } ?: emptyList()
            val toolsToProcess: List<UIMessagePart.Tool>

            if (pendingTools.isEmpty()) {
                generateInternal(
                    assistant = assistant, settings = settings, messages = messages,
                    onUpdateMessages = {
                        messages = it.transforms(transformers = outputTransformers, context = context, model = model, assistant = assistant, settings = settings)
                        emit(GenerationChunk.Messages(messages.visualTransforms(transformers = outputTransformers, context = context, model = model, assistant = assistant, settings = settings)))
                    },
                    transformers = inputTransformers, model = model, providerImpl = providerImpl, provider = provider,
                    tools = statusTrackedTools, memories = memories ?: emptyList(), stream = assistant.streamOutput,
                    processingStatus = processingStatus, conversationSystemPrompt = conversationSystemPrompt,
                    conversationModeInjectionIds = conversationModeInjectionIds, conversationLorebookIds = conversationLorebookIds,
                )
                messages = messages.visualTransforms(transformers = outputTransformers, context = context, model = model, assistant = assistant, settings = settings)
                messages = messages.onGenerationFinish(transformers = outputTransformers, context = context, model = model, assistant = assistant, settings = settings)
                messages = messages.slice(0 until messages.lastIndex) + messages.last().copy(finishedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))
                emit(GenerationChunk.Messages(messages))

                val tools = messages.last().getTools().filter { !it.isExecuted }
                if (tools.isEmpty()) break

                val seenTools = mutableSetOf<Pair<String, String>>()
                val uniqueTools = tools.filter { tool -> seenTools.add(tool.toolName to tool.input) }

                var hasPendingApproval = false
                val updatedTools = uniqueTools.map { tool ->
                    val toolDef = statusTrackedTools.find { it.name == tool.toolName }
                    when {
                        toolDef?.needsApproval == true && tool.approvalState is ToolApprovalState.Auto -> { hasPendingApproval = true; tool.copy(approvalState = ToolApprovalState.Pending) }
                        tool.approvalState is ToolApprovalState.Pending -> { hasPendingApproval = true; tool }
                        else -> tool
                    }
                }

                if (updatedTools != uniqueTools) {
                    val lastMessage = messages.last()
                    val updatedParts = lastMessage.parts.map { part ->
                        if (part is UIMessagePart.Tool) updatedTools.find { it.toolCallId == part.toolCallId } ?: part else part
                    }
                    messages = messages.dropLast(1) + lastMessage.copy(parts = updatedParts)
                    emit(GenerationChunk.Messages(messages))
                }

                if (!hasPendingApproval) {
                    val toolNameCount = updatedTools.groupingBy { it.toolName }.eachCount()
                    val looped = toolNameCount.entries.find { it.value >= assistant.toolRecurringLimit }
                    if (looped != null) { Log.w(TAG, "Guardrail: ${looped.key} looped"); break }
                }

                if (hasPendingApproval) break
                toolsToProcess = updatedTools
            } else {
                toolsToProcess = messages.last().getTools().filter { it.canResumeExecution }
            }

            val executedTools = arrayListOf<UIMessagePart.Tool>()
            val isParallel = assistant.enableParallelToolExecution && toolsToProcess.size > 1

            if (isParallel) {
                coroutineScope {
                    val deferreds = toolsToProcess.map { tool ->
                        async { tool to runCatching { kotlinx.coroutines.withTimeout(assistant.toolExecTimeout * 1000L) { executeToolCall(tool, toolsInternal, json, policyEngine) } } }
                    }
                    deferreds.forEach { val (tool, result) = it.await(); addToolResult(executedTools, tool, result, json) }
                }
            } else {
                toolsToProcess.forEach { tool ->
                    val result = runCatching { kotlinx.coroutines.withTimeout(60_000) { executeToolCall(tool, toolsInternal, json, policyEngine) } }
                    addToolResult(executedTools, tool, result, json)
                }
            }

            if (executedTools.isEmpty()) break

            val lastMessage = messages.last()
            val updatedParts = lastMessage.parts.map { part ->
                if (part is UIMessagePart.Tool) executedTools.find { it.toolCallId == part.toolCallId } ?: part else part
            }
            messages = messages.dropLast(1) + lastMessage.copy(parts = updatedParts)
            emit(GenerationChunk.Messages(messages.transforms(transformers = outputTransformers, context = context, model = model, assistant = assistant, settings = settings)))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun generateInternal(
        assistant: Assistant, settings: Settings, messages: List<UIMessage>,
        onUpdateMessages: suspend (List<UIMessage>) -> Unit, transformers: List<MessageTransformer>,
        model: Model, providerImpl: Provider<ProviderSetting>, provider: ProviderSetting, tools: List<Tool>,
        memories: List<AssistantMemory>, stream: Boolean, processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        conversationSystemPrompt: String? = null, conversationModeInjectionIds: Set<Uuid> = emptySet(),
        conversationLorebookIds: Set<Uuid> = emptySet(),
    ) {
        val internalMessages = buildList {
            val system = buildString {
                val effectiveSystemPrompt = if (assistant.allowConversationSystemPrompt && !conversationSystemPrompt.isNullOrBlank()) conversationSystemPrompt
                else if (assistant.tavernData != null) {
                    val persona = settings.personas.find { it.id == settings.activePersonaId }
                    assistant.assembleContext(userName = settings.displaySetting.userNickname.ifBlank { "User" }, personaDesc = persona?.description ?: "")
                } else assistant.systemPrompt
                if (effectiveSystemPrompt.isNotBlank()) append(effectiveSystemPrompt)
                if (assistant.enableMemory) { appendLine(); append(buildMemoryPrompt(memories = memories)) }
                if (assistant.enableRecentChatsReference) { appendLine(); append(buildRecentChatsPrompt(assistant, conversationRepo)) }
                tools.forEach { tool -> appendLine(); append(tool.systemPrompt(model, messages)) }
                appendLine()
                appendLine("## Executing actions with care")
                appendLine("Carefully consider the reversibility and blast radius of actions.")
                appendLine("- Reversible + low risk: proceed directly")
                appendLine("- Reversible + high risk: notify user before proceeding")
                appendLine("- Irreversible + low risk: confirm with user first")
                appendLine("- Irreversible + high risk: always confirm with user")
                appendLine()
                appendLine("When encountering an obstacle, do not use destructive shortcuts.")
                appendLine("Measure twice, cut once.")
            }
            if (system.isNotBlank()) add(UIMessage.system(prompt = system))
            addAll(messages.limitContext(assistant.contextMessageSize))
        }.transforms(transformers = transformers, context = context, model = model, assistant = assistant, settings = settings,
            conversationModeInjectionIds = conversationModeInjectionIds, conversationLorebookIds = conversationLorebookIds, processingStatus = processingStatus)

        var msgs: List<UIMessage> = messages
        val params = TextGenerationParams(model = model, temperature = assistant.temperature, topP = assistant.topP,
            maxTokens = assistant.maxTokens, tools = tools, reasoningLevel = assistant.reasoningLevel,
            customHeaders = buildList { addAll(assistant.customHeaders); addAll(model.customHeaders) },
            customBody = buildList { addAll(assistant.customBodies); addAll(model.customBodies) })

        if (stream) {
            aiLoggingManager.addLog(AILogging.Generation(params = params, messages = msgs, providerSetting = provider, stream = true))
            try {
                providerImpl.streamText(providerSetting = provider, messages = internalMessages, params = params).collect {
                    msgs = msgs.handleMessageChunk(chunk = it, model = model)
                    it.usage?.let { usage -> msgs = msgs.mapIndexed { idx, msg -> if (idx == msgs.lastIndex) msg.copy(usage = msg.usage.merge(usage)) else msg } }
                    onUpdateMessages(msgs)
                }
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("429") || msg.contains("5") || msg.contains("timeout") || msg.contains("reset")) {
                    Log.w(TAG, "retrying: ${e.message}")
                    providerImpl.streamText(providerSetting = provider, messages = internalMessages, params = params).collect {
                        msgs = msgs.handleMessageChunk(chunk = it, model = model)
                        it.usage?.let { usage -> msgs = msgs.mapIndexed { idx, msg -> if (idx == msgs.lastIndex) msg.copy(usage = msg.usage.merge(usage)) else msg } }
                        onUpdateMessages(msgs)
                    }
                } else throw e
            }
        } else {
            aiLoggingManager.addLog(AILogging.Generation(params = params, messages = msgs, providerSetting = provider, stream = false))
            val chunk = try {
                providerImpl.generateText(providerSetting = provider, messages = internalMessages, params = params)
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("429") || msg.contains("5") || msg.contains("timeout") || msg.contains("reset")) {
                    Log.w(TAG, "retrying: ${e.message}")
                    providerImpl.generateText(providerSetting = provider, messages = internalMessages, params = params)
                } else throw e
            }
            msgs = msgs.handleMessageChunk(chunk = chunk, model = model)
            chunk.usage?.let { usage -> msgs = msgs.mapIndexed { idx, msg -> if (idx == msgs.lastIndex) msg.copy(usage = msg.usage.merge(usage)) else msg } }
            onUpdateMessages(msgs)
        }
    }

    fun translateText(settings: Settings, sourceText: String, targetLanguage: Locale, onStreamUpdate: ((String) -> Unit)? = null): Flow<String> = flow {
        val model = settings.providers.findModelById(settings.translateModeId) ?: error("Translation model not found")
        val provider = model.findProvider(settings.providers) ?: error("Translation provider not found")
        val providerHandler = providerManager.getProviderByType(provider)
        if (!ModelRegistry.QWEN_MT.match(model.modelId)) {
            val prompt = settings.translatePrompt.applyPlaceholders("source_text" to sourceText, "target_lang" to targetLanguage.toString())
            var messages = listOf(UIMessage.user(prompt))
            var translatedText = ""
            providerHandler.streamText(providerSetting = provider, messages = messages,
                params = TextGenerationParams(model = model, reasoningLevel = ReasoningLevel.fromBudgetTokens(settings.translateThinkingBudget)),
            ).collect { chunk ->
                messages = messages.handleMessageChunk(chunk); translatedText = messages.lastOrNull()?.toText() ?: ""
                if (translatedText.isNotBlank()) { onStreamUpdate?.invoke(translatedText); emit(translatedText) }
            }
        } else {
            val msgs = listOf(UIMessage.user(sourceText))
            val chunk = providerHandler.generateText(providerSetting = provider, messages = msgs,
                params = TextGenerationParams(model = model, temperature = 0.3f, topP = 0.95f,
                    customBody = listOf(CustomBody(key = "translation_options", value = buildJsonObject {
                        put("source_lang", JsonPrimitive("auto"))
                        put("target_lang", JsonPrimitive(targetLanguage.getDisplayLanguage(Locale.ENGLISH)))
                    }))))
            val text = chunk.choices.firstOrNull()?.message?.toText() ?: ""
            if (text.isNotBlank()) { onStreamUpdate?.invoke(text); emit(text) }
        }
    }.flowOn(Dispatchers.IO)
}

private suspend fun executeToolCall(
    tool: UIMessagePart.Tool, toolsInternal: List<Tool>, json: Json, policyEngine: PolicyEngine? = null,
): UIMessagePart.Tool {
    return when (tool.approvalState) {
        is ToolApprovalState.Denied -> {
            val reason = (tool.approvalState as ToolApprovalState.Denied).reason
            tool.copy(output = listOf(UIMessagePart.Text(json.encodeToString(buildJsonObject {
                put("error", JsonPrimitive("Tool execution denied by user. Reason: ${reason.ifBlank { "No reason" }}"))
            }))))
        }
        is ToolApprovalState.Answered -> {
            val answer = (tool.approvalState as ToolApprovalState.Answered).answer
            tool.copy(output = listOf(UIMessagePart.Text(answer)))
        }
        is ToolApprovalState.Pending -> tool
        else -> {
            val toolDef = toolsInternal.find { it.name == tool.toolName } ?: error("Tool ${tool.toolName} not found")
            val args = runCatching { json.parseToJsonElement(tool.input.ifBlank { "{}" }) }.getOrElse { error("Invalid JSON for ${tool.toolName}: ${it.message}") }

            // PolicyEngine check
            if (policyEngine != null) {
                when (val result = policyEngine.check(toolDef, args)) {
                    is PermissionResult.Denied -> {
                        Log.w(TAG, "PolicyEngine denied ${tool.toolName}: ${result.reason}")
                        return tool.copy(output = listOf(UIMessagePart.Text(
                            json.encodeToString(buildJsonObject { put("error", JsonPrimitive("Permission denied: ${result.reason}")) })
                        )))
                    }
                    is PermissionResult.Allowed -> {}
                }
            }

            Log.i(TAG, "executing tool ${toolDef.name}")
            val result = toolDef.execute(args)
            tool.copy(output = result)
        }
    }
}

private fun addToolResult(executedTools: ArrayList<UIMessagePart.Tool>, tool: UIMessagePart.Tool, result: Result<UIMessagePart.Tool>, json: Json) {
    result.onSuccess { executedTools.add(it) }.onFailure {
        it.printStackTrace()
        executedTools.add(tool.copy(output = listOf(UIMessagePart.Text(json.encodeToString(buildJsonObject {
            put("error", JsonPrimitive("[${it.javaClass.name}] ${it.message}\n${it.stackTraceToString()}"))
        })))))
    }
}
