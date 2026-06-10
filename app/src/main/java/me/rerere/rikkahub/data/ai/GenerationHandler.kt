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
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.MessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OutputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.onGenerationFinish
import me.rerere.rikkahub.data.ai.transformers.transforms
import me.rerere.rikkahub.data.ai.transformers.visualTransforms
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
    data class Messages(
        val messages: List<UIMessage>
    ) : GenerationChunk
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
        policyEngine: me.rerere.rikkahub.data.ai.policy.PolicyEngine? = null,
        autoCompactor: me.rerere.rikkahub.data.ai.compaction.AutoCompactor? = null,
    ): Flow<GenerationChunk> = flow {
        val provider = model.findProvider(settings.providers) ?: error("Provider not found")
        val providerImpl = providerManager.getProviderByType(provider)

        var messages: List<UIMessage> = messages

    fun describeTool(name: String): String = when {
        name.startsWith("github_") -> "🔧 GitHub → 正在操作..."
        name.startsWith("execute_python") -> "🔧 Python → 正在执行代码..."
        name.startsWith("execute_command") -> "🔧 Shell → 正在执行命令..."
        name == "file" -> "🔧 文件 → 正在操作..."
        name.startsWith("data_process") -> "🔧 数据 → 正在处理..."
        name.startsWith("database_") -> "🔧 数据库 → 正在查询..."
        name.startsWith("search_web") || name.startsWith("scrape_") -> "🔧 搜索 → 正在搜索..."
        name.startsWith("convert_file") -> "🔧 转换 → 正在转换格式..."
        name.startsWith("create_asset") -> "🔧 创作 → 正在生成..."
        name.startsWith("use_skill") -> "🔧 知识 → 正在读取..."
        name.startsWith("clipboard") -> "🔧 剪贴板 → 正在操作..."
        name.startsWith("get_time") -> "🔧 时间 → 获取中..."
        name.startsWith("text_to_speech") -> "🔧 语音 → 正在朗读..."
        name.startsWith("present_file") -> "🔧 文件 → 正在分享..."
        name.startsWith("eval_javascript") -> "🔧 JS → 正在执行..."
        name.startsWith("memory_") -> "🔧 记忆 → 正在处理..."
            else -> "🔧 $name → 正在处理..."
    }

    /**
     * 缓存 system prompt（循环不变，避免每步重建 PromptContext + tool.systemPrompt）
     */
    suspend fun buildCachedSystemPrompt(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
        memories: List<AssistantMemory>,
        conversationSystemPrompt: String?,
        tools: List<Tool>,
        model: Model,
        context: android.content.Context,
        conversationRepo: me.rerere.rikkahub.data.repository.ConversationRepository,
    ): String {
        val assemblerContext = me.rerere.rikkahub.data.ai.prompts.PromptContext(
            identitySection = buildString {
                val effectiveSystemPrompt =
                    if (assistant.allowConversationSystemPrompt && !conversationSystemPrompt.isNullOrBlank()) {
                        conversationSystemPrompt
                    } else {
                        if (assistant.tavernData != null) {
                            val persona = settings.personas.find { it.id == settings.activePersonaId }
                            assistant.assembleContext(
                                userName = settings.displaySetting.userNickname.ifBlank { "User" },
                                personaDesc = persona?.description ?: ""
                            )
                        } else {
                            assistant.systemPrompt
                        }
                    }
                append(effectiveSystemPrompt)
            },
            leadInInstructions = buildString {
                appendLine("## Tool Use Guidelines")
                appendLine()
                appendLine("Think first:")
                appendLine("- Can you answer from what you know? → Answer directly. Do NOT call a tool.")
                appendLine("- Need a file? → Use file tools. Do NOT use shell (cat/grep/ls) when a dedicated tool exists.")
                appendLine()
                appendLine("File operations:")
                appendLine("- Read files → use file action=\"read\" (supports line numbers, offset, image preview). Do NOT use cat/head.")
                appendLine("- Search files → use file action=\"search\" (by name or content). Do NOT use grep/find.")
                appendLine("- List directories → use file action=\"list\". Do NOT use ls.")
                appendLine("- Write files → use file action=\"write\". Do NOT use echo/heredoc.")
                appendLine("- Copy/move/delete → use file action=\"copy\"/\"move\"/\"delete\".")
                appendLine("- Edit files → use file action=\"patch\" for surgical find-and-replace. Do NOT use sed.")
                appendLine("- Reserve execute_command for: git, builds, installs, and operations without a dedicated tool.")
                appendLine("- Do NOT list or search the filesystem proactively. Only access files when the task explicitly requires it.")
                appendLine()
                appendLine("Calculator:")
                appendLine("- Simple math (2+2, percentages, mental-arithmetic) → answer directly. Do NOT call calculator.")
                appendLine("- Complex math (physics formulas, finance, stats, matrices, unit conversion) → use calculator.")
                appendLine("- If calculator can do it, do NOT use execute_python for it.")
                appendLine()
                appendLine("Python execution:")
                appendLine("- Data processing, API calls, file generation → use execute_python.")
                appendLine("- For math → use calculator, NOT execute_python.")
                appendLine("- For format conversion → use convert_file, NOT execute_python.")
                appendLine()
                appendLine("Work ethic:")
                appendLine("When the user asks you to build, fix, or run something:")
                appendLine("- Do NOT describe what you will do — just do it.")
                appendLine("- Do NOT stop after writing a stub. Complete the task, then report.")
                appendLine("- Do NOT fabricate results. If a tool fails, say so and try another approach.")
                appendLine("When chatting or discussing ideas:")
                appendLine("- Explain your thinking before acting — that's normal conversation.")
                appendLine("- If you need user input, ask directly.")
            },
            workspaceDescription = "Working directory: ${context.filesDir?.absolutePath ?: "."}",
            memories = if (assistant.enableMemory) memories else emptyList(),
            extraInstructions = buildString {
                if (assistant.enableRecentChatsReference) {
                    appendLine()
                    append(buildRecentChatsPrompt(assistant, conversationRepo))
                }
            },
            constraints = emptyList(),
        )
        val system = me.rerere.rikkahub.data.ai.prompts.SystemPromptAssembler.assemble(assemblerContext)
        return buildString {
            append(system)
            tools.forEach { tool ->
                appendLine()
                append(tool.systemPrompt(model, messages))
            }
        }
    }

    // ── 预构建：tools + systemPrompt（循环不变，移到外面）──
    val toolsInternal = buildList {
        Log.i(TAG, "generateInternal: build tools($assistant)")
        if (assistant?.enableMemory == true) {
            val memoryAssistantId = if (assistant.useGlobalMemory) {
                MemoryRepository.GLOBAL_MEMORY_ID
            } else {
                assistant.id.toString()
            }
            buildMemoryTools(
                json = json,
                onCreation = { content ->
                    memoryRepo.addMemory(memoryAssistantId, content)
                },
                onUpdate = { id, content ->
                    memoryRepo.updateContent(id, content)
                },
                onDelete = { id ->
                    memoryRepo.deleteMemory(id)
                }
            ).let(this::addAll)
        }
        addAll(tools)
    }
    val statusTrackedTools = toolsInternal.map { tool ->
        if (tool.name == "ask_user") tool else tool.copy(
            execute = { args ->
                processingStatus.value = describeTool(tool.name)
                if (tool.name.contains("github")) {
                    me.rerere.rikkahub.data.ai.tools.GhProgress.processingRef = processingStatus
                }
                try {
                    val result = tool.execute(args)
                    if (tool.name.contains("github")) {
                        me.rerere.rikkahub.data.ai.tools.GhProgress.processingRef = null
                    }
                    processingStatus.value = null
                    result
                } catch (e: Exception) {
                    if (tool.name.contains("github")) {
                        me.rerere.rikkahub.data.ai.tools.GhProgress.processingRef = null
                    }
                    processingStatus.value = null
                    throw e
                }
            }
        )
    }
    // ── 预构建：system prompt 全文（循环不变，移到外面）──
    // buildString 不是 suspend 上下文，直接调用即可
    val prebuiltSystemPrompt = buildCachedSystemPrompt(assistant, settings, messages, memories ?: emptyList(), conversationSystemPrompt, tools, model, context, conversationRepo)

    for (stepIndex in 0 until maxSteps) {
            Log.i(TAG, "streamText: start step #$stepIndex (${model.id})")

            // Check if we have tool calls ready to continue after user interaction.
            val pendingTools = messages.lastOrNull()?.getTools()?.filter {
                it.canResumeExecution
            } ?: emptyList()

            val toolsToProcess: List<UIMessagePart.Tool>

            // Skip generation if we have approved/denied tool calls to handle
            if (pendingTools.isEmpty()) {
                generateInternal(
                    assistant = assistant,
                    settings = settings,
                    messages = messages,
                    onUpdateMessages = {
                        messages = it.transforms(
                            transformers = outputTransformers,
                            context = context,
                            model = model,
                            assistant = assistant,
                            settings = settings
                        )
                        emit(
                            GenerationChunk.Messages(
                                messages.visualTransforms(
                                    transformers = outputTransformers,
                                    context = context,
                                    model = model,
                                    assistant = assistant,
                                    settings = settings
                                ).filter { it.role != MessageRole.SYSTEM }
                            )
                        )
                    },
                    transformers = inputTransformers,
                    model = model,
                    providerImpl = providerImpl,
                    provider = provider,
                    tools = statusTrackedTools,
                    memories = memories ?: emptyList(),
                    stream = assistant.streamOutput,
                    processingStatus = processingStatus,
                    conversationSystemPrompt = conversationSystemPrompt,
                    conversationModeInjectionIds = conversationModeInjectionIds,
                    conversationLorebookIds = conversationLorebookIds,
                    prebuiltSystemPrompt = prebuiltSystemPrompt,
                )
                messages = messages.visualTransforms(
                    transformers = outputTransformers,
                    context = context,
                    model = model,
                    assistant = assistant,
                    settings = settings
                )
                messages = messages.onGenerationFinish(
                    transformers = outputTransformers,
                    context = context,
                    model = model,
                    assistant = assistant,
                    settings = settings
                )
                messages = messages.slice(0 until messages.lastIndex) + messages.last().copy(
                    finishedAt = Clock.System.now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                )
                emit(GenerationChunk.Messages(messages.filter { it.role != MessageRole.SYSTEM }))

                val tools = messages.last().getTools().filter { !it.isExecuted }
                if (tools.isEmpty()) {
                    // no tool calls, break
                    break
                }

                // 1. Deduplicate tools: same (toolName, input) only execute once
                val seenTools = mutableSetOf<Pair<String, String>>()
                val uniqueTools = tools.filter { tool ->
                    val key = tool.toolName to tool.input
                    if (key in seenTools) {
                        Log.w(TAG, "Deduplicated duplicate tool call: ${tool.toolName}")
                        false
                    } else {
                        seenTools.add(key)
                        true
                    }
                }

                // Check for tools that need approval
                var hasPendingApproval = false
                val updatedTools = uniqueTools.map { tool ->
                    val toolDef = statusTrackedTools.find { it.name == tool.toolName }
                    when {
                        // Tool needs approval and state is Auto -> set to Pending
                        toolDef?.needsApproval == true && tool.approvalState is ToolApprovalState.Auto -> {
                            hasPendingApproval = true
                            tool.copy(approvalState = ToolApprovalState.Pending)
                        }
                        // State is Pending -> keep waiting
                        tool.approvalState is ToolApprovalState.Pending -> {
                            hasPendingApproval = true
                            tool
                        }

                        else -> tool
                    }
                }

                // If any tools were updated to Pending, update the message and break
                if (updatedTools != uniqueTools) {
                    val lastMessage = messages.last()
                    val updatedParts = lastMessage.parts.map { part ->
                        if (part is UIMessagePart.Tool) {
                            updatedTools.find { it.toolCallId == part.toolCallId } ?: part
                        } else {
                            part
                        }
                    }
                    messages = messages.dropLast(1) + lastMessage.copy(parts = updatedParts)
                    emit(GenerationChunk.Messages(messages.filter { it.role != MessageRole.SYSTEM }))
                }

                // 3. Guardrail: same tool called N+ times in one batch → break
                if (!hasPendingApproval) {
                    val toolNameCount = updatedTools.groupingBy { it.toolName }.eachCount()
                    val looped = toolNameCount.entries.find { it.value >= assistant.toolRecurringLimit }
                    if (looped != null) {
                        Log.w(TAG, "Guardrail: ${looped.key} called ${looped.value} times in one batch, breaking")
                        break
                    }
                }

                // If there are pending approvals, break and wait for user
                if (hasPendingApproval) {
                    Log.i(TAG, "generateText: waiting for tool approval")
                    break
                }

                toolsToProcess = updatedTools
            } else {
                // Resuming after user interaction - use the resumable tools directly.
                Log.i(TAG, "generateText: resuming with ${pendingTools.size} resumable tools")
                toolsToProcess = messages.last().getTools().filter { it.canResumeExecution }
            }

            // Handle tools (execute approved tools, handle denied tools)
            val executedTools = arrayListOf<UIMessagePart.Tool>()
            val isParallel = assistant.enableParallelToolExecution && toolsToProcess.size > 1

            if (isParallel) {
                // 并行执行所有工具
                coroutineScope {
                    val deferreds = toolsToProcess.map { tool ->
                        async {
                            tool to runCatching {
                                kotlinx.coroutines.withTimeout(assistant.toolExecTimeout * 1000L) {
                                    executeToolCall(tool, toolsInternal, json, policyEngine)
                                }
                            }
                        }
                    }
                    deferreds.forEach { deferred ->
                        val (tool, result) = deferred.await()
                        addToolResult(executedTools, tool, result, json)
                    }
                }
            } else {
                // 顺序执行（原版行为）
                toolsToProcess.forEach { tool ->
                    val result = runCatching {
                        kotlinx.coroutines.withTimeout(assistant.toolExecTimeout * 1000L) {
                            executeToolCall(tool, toolsInternal, json, policyEngine)
                        }
                    }
                    addToolResult(executedTools, tool, result, json)
                }
            }

            if (executedTools.isEmpty()) {
                // No results to add (all tools were pending)
                break
            }

            // Update last message with executed tools (NOT create TOOL message)
            val lastMessage = messages.last()
            val updatedParts = lastMessage.parts.map { part ->
                if (part is UIMessagePart.Tool) {
                    executedTools.find { it.toolCallId == part.toolCallId } ?: part
                } else part
            }
            messages = messages.dropLast(1) + lastMessage.copy(parts = updatedParts)
            emit(
                GenerationChunk.Messages(
                    messages.transforms(
                        transformers = outputTransformers,
                        context = context,
                        model = model,
                        assistant = assistant,
                        settings = settings
                    ).filter { it.role != MessageRole.SYSTEM }
                )
            )
        }

    }.flowOn(Dispatchers.IO)

    private suspend fun generateInternal(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
        onUpdateMessages: suspend (List<UIMessage>) -> Unit,
        transformers: List<MessageTransformer>,
        model: Model,
        providerImpl: Provider<ProviderSetting>,
        provider: ProviderSetting,
        tools: List<Tool>,
        memories: List<AssistantMemory>,
        stream: Boolean,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        conversationSystemPrompt: String? = null,
        conversationModeInjectionIds: Set<Uuid> = emptySet(),
        conversationLorebookIds: Set<Uuid> = emptySet(),
        prebuiltSystemPrompt: String = "",
    ) {
        val internalMessages = buildList {
            val fullSystem = if (prebuiltSystemPrompt.isNotBlank()) prebuiltSystemPrompt else buildString {
                // ── s10: 使用 SystemPromptAssembler 替代硬编码 ──
                val assemblerContext = me.rerere.rikkahub.data.ai.prompts.PromptContext(
                identitySection = buildString {
                    val effectiveSystemPrompt =
                        if (assistant.allowConversationSystemPrompt && !conversationSystemPrompt.isNullOrBlank()) {
                            conversationSystemPrompt
                        } else {
                            if (assistant.tavernData != null) {
                                val persona = settings.personas.find { it.id == settings.activePersonaId }
                                assistant.assembleContext(
                                    userName = settings.displaySetting.userNickname.ifBlank { "User" },
                                    personaDesc = persona?.description ?: ""
                                )
                            } else {
                                assistant.systemPrompt
                            }
                        }
                    append(effectiveSystemPrompt)
                },
                leadInInstructions = buildString {
                    appendLine("Guidelines:")
                    appendLine("- Prefer dedicated tools over shell commands for file operations")
                    appendLine("- When a tool fails, try an alternative approach before giving up")
                    appendLine("- If you need clarification, ask the user directly")
                },
                workspaceDescription = "Working directory: ${context.filesDir?.absolutePath ?: "."}",
                memories = if (assistant.enableMemory) memories else emptyList(),
                extraInstructions = buildString {
                    if (assistant.enableRecentChatsReference) {
                        appendLine()
                        append(buildRecentChatsPrompt(assistant, conversationRepo))
                    }
                },
                constraints = emptyList(),
            )
            val system = me.rerere.rikkahub.data.ai.prompts.SystemPromptAssembler.assemble(assemblerContext)

            // ── 工具prompt（追加在 assembler 结果之后）──
            append(system)
            tools.forEach { tool ->
                appendLine()
                append(tool.systemPrompt(model, messages))
            }
            }
            val systemMsg = fullSystem.ifBlank { null }
            if (systemMsg != null) add(UIMessage.system(prompt = systemMsg))
            addAll(messages.limitContext(assistant.contextMessageSize))
        }.transforms(
            transformers = transformers,
            context = context,
            model = model,
            assistant = assistant,
            settings = settings,
            conversationModeInjectionIds = conversationModeInjectionIds,
            conversationLorebookIds = conversationLorebookIds,
            processingStatus = processingStatus,
        )

        var messages: List<UIMessage> = messages
        val params = TextGenerationParams(
            model = model,
            temperature = assistant.temperature,
            topP = assistant.topP,
            maxTokens = assistant.maxTokens,
            tools = tools,
            reasoningLevel = assistant.reasoningLevel,
            customHeaders = buildList {
                addAll(assistant.customHeaders)
                addAll(model.customHeaders)
            },
            customBody = buildList {
                addAll(assistant.customBodies)
                addAll(model.customBodies)
            }
        )
        if (stream) {
            aiLoggingManager.addLog(
                AILogging.Generation(
                    params = params,
                    messages = messages,
                    providerSetting = provider,
                    stream = true
                )
            )
            // Streaming: retry once on transient error (429/5xx/timeout)
            try {
                providerImpl.streamText(
                    providerSetting = provider,
                    messages = internalMessages,
                    params = params
                ).collect {
                    messages = messages.handleMessageChunk(chunk = it, model = model)
                    it.usage?.let { usage ->
                        messages = messages.mapIndexed { index, message ->
                            if (index == messages.lastIndex) {
                                message.copy(usage = message.usage.merge(usage))
                            } else {
                                message
                            }
                        }
                    }
                    onUpdateMessages(messages)
                }
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("429 ") || msg.contains("5") || msg.contains("timeout") || msg.contains("reset")) {
                    Log.w(TAG, "streamText: retrying once after: ${e.message}")
                    providerImpl.streamText(
                        providerSetting = provider,
                        messages = internalMessages,
                        params = params
                    ).collect {
                        messages = messages.handleMessageChunk(chunk = it, model = model)
                        it.usage?.let { usage ->
                            messages = messages.mapIndexed { index, message ->
                                if (index == messages.lastIndex) {
                                    message.copy(usage = message.usage.merge(usage))
                                } else {
                                    message
                                }
                            }
                        }
                        onUpdateMessages(messages)
                    }
                } else {
                    throw e
                }
            }
        } else {
            aiLoggingManager.addLog(
                AILogging.Generation(
                    params = params,
                    messages = messages,
                    providerSetting = provider,
                    stream = false
                )
            )
            val recoveryState = me.rerere.rikkahub.data.ai.error.RecoveryState()
            val chunk = try {
                me.rerere.rikkahub.data.ai.error.withRetry(
                    block = suspend {
                        providerImpl.generateText(
                            providerSetting = provider,
                            messages = internalMessages,
                            params = params.copy(
                                maxTokens = if (recoveryState.hasEscalated)
                                    me.rerere.rikkahub.data.ai.error.ESCALATED_MAX_TOKENS
                                else params.maxTokens
                            ),
                        )
                    },
                    state = recoveryState,
                )
            } catch (e: Exception) {
                Log.e(TAG, "generateText failed after retries: ${e.message}")
                throw e
            }
            messages = messages.handleMessageChunk(chunk = chunk, model = model)
            chunk.usage?.let { usage ->
                messages = messages.mapIndexed { index, message ->
                    if (index == messages.lastIndex) {
                        message.copy(
                            usage = message.usage.merge(usage)
                        )
                    } else {
                        message
                    }
                }
            }
            onUpdateMessages(messages)
        }
    }

    fun translateText(
        settings: Settings,
        sourceText: String,
        targetLanguage: Locale,
        onStreamUpdate: ((String) -> Unit)? = null
    ): Flow<String> = flow {
        val model = settings.providers.findModelById(settings.translateModeId)
            ?: error("Translation model not found")
        val provider = model.findProvider(settings.providers)
            ?: error("Translation provider not found")

        val providerHandler = providerManager.getProviderByType(provider)

        if (!ModelRegistry.QWEN_MT.match(model.modelId)) {
            // Use regular translation with prompt
            val prompt = settings.translatePrompt.applyPlaceholders(
                "source_text" to sourceText,
                "target_lang" to targetLanguage.toString(),
            )

            var messages = listOf(UIMessage.user(prompt))
            var translatedText = ""

            providerHandler.streamText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    reasoningLevel = ReasoningLevel.fromBudgetTokens(settings.translateThinkingBudget),
                ),
            ).collect { chunk ->
                messages = messages.handleMessageChunk(chunk)
                translatedText = messages.lastOrNull()?.toText() ?: ""

                if (translatedText.isNotBlank()) {
                    onStreamUpdate?.invoke(translatedText)
                    emit(translatedText)
                }
            }
        } else {
            // Use Qwen MT model with special translation options
            val messages = listOf(UIMessage.user(sourceText))
            val chunk = providerHandler.generateText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.3f,
                    topP = 0.95f,
                    customBody = listOf(
                        CustomBody(
                            key = "translation_options",
                            value = buildJsonObject {
                                put("source_lang", JsonPrimitive("auto"))
                                put(
                                    "target_lang",
                                    JsonPrimitive(targetLanguage.getDisplayLanguage(Locale.ENGLISH))
                                )
                            }
                        )
                    )
                ),
            )
            val translatedText = chunk.choices.firstOrNull()?.message?.toText() ?: ""

            if (translatedText.isNotBlank()) {
                onStreamUpdate?.invoke(translatedText)
                emit(translatedText)
            }
        }
    }.flowOn(Dispatchers.IO)
}

/**
 * 执行单个工具调用（提取逻辑以避免并行/串行分支重复）
 */
private suspend fun executeToolCall(
    tool: UIMessagePart.Tool,
    toolsInternal: List<Tool>,
    json: kotlinx.serialization.json.Json,
    policyEngine: me.rerere.rikkahub.data.ai.policy.PolicyEngine? = null,
): UIMessagePart.Tool {
    return when (tool.approvalState) {
        is ToolApprovalState.Denied -> {
            val reason = (tool.approvalState as ToolApprovalState.Denied).reason
            tool.copy(
                output = listOf(
                    UIMessagePart.Text(
                        json.encodeToString(
                            buildJsonObject {
                                put(
                                    "error",
                                    JsonPrimitive("Tool execution denied by user. Reason: ${reason.ifBlank { "No reason provided" }}")
                                )
                            }
                        )
                    )
                )
            )
        }

        is ToolApprovalState.Answered -> {
            val answer = (tool.approvalState as ToolApprovalState.Answered).answer
            tool.copy(
                output = listOf(UIMessagePart.Text(answer))
            )
        }

        is ToolApprovalState.Pending -> tool

        else -> {
            val toolDef = toolsInternal.find { it.name == tool.toolName }
                ?: error("Tool ${tool.toolName} not found")
            val args = runCatching {
                json.parseToJsonElement(tool.input.ifBlank { "{}" })
            }.getOrElse {
                error("Invalid tool arguments JSON for ${tool.toolName}: ${it.message}")
            }
            Log.i(TAG, "generateText: executing tool ${toolDef.name} with args: $args")

            // PolicyEngine check
            if (policyEngine != null) {
                when (val result = policyEngine.check(toolDef, args)) {
                    is me.rerere.rikkahub.data.ai.policy.PermissionResult.Denied -> {
                        Log.w(TAG, "PolicyEngine denied ${toolDef.name}: ${result.reason}")
                        return tool.copy(output = listOf(UIMessagePart.Text(
                            json.encodeToString(buildJsonObject { put("error", JsonPrimitive("Permission denied: ${result.reason}")) })
                        )))
                    }
                    is me.rerere.rikkahub.data.ai.policy.PermissionResult.NeedsApproval -> {
                        Log.w(TAG, "PolicyEngine needs approval for ${toolDef.name}: ${result.reason}")
                        return tool.copy(output = listOf(UIMessagePart.Text(
                            json.encodeToString(buildJsonObject {
                                put("error", JsonPrimitive("⚠ ${result.reason}: tool '${result.toolName}' needs your approval before execution. Type 'approve' to allow or 'deny' to reject."))
                                put("needs_approval", true)
                                put("tool_name", JsonPrimitive(result.toolName))
                                put("reason", JsonPrimitive(result.reason))
                            })
                        )))
                    }
                    is me.rerere.rikkahub.data.ai.policy.PermissionResult.Allowed -> {}
                }
            }

            // Pre-tool check via AgentEventBus (SafetyHook — blocking with reply)
            // 只对 execute_command 走完整检查流程，其他工具直接放行
            val allowed = if (toolDef.name == "execute_command") {
                val reply = kotlinx.coroutines.CompletableDeferred<Boolean>()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    me.rerere.rikkahub.data.ai.listener.AgentEventBus.emit(
                        me.rerere.rikkahub.data.ai.listener.AgentEvent.PreToolCheck(toolDef, args, reply)
                    )
                    reply.await()
                }
            } else true
            if (!allowed) {
                return tool.copy(output = listOf(UIMessagePart.Text(
                    json.encodeToString(buildJsonObject { put("error", JsonPrimitive("Tool blocked by safety check")) })
                )))
            }

            val result = toolDef.execute(args)

            // Post-tool notification (fire-and-forget)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                me.rerere.rikkahub.data.ai.listener.AgentEventBus.emit(
                    me.rerere.rikkahub.data.ai.listener.AgentEvent.PostToolNotify(toolDef, args, result)
                )
            }

            tool.copy(output = result)
        }
    }
}


/**
 * 将工具执行结果添加到列表中（处理成功和失败两种情况）
 */
private fun addToolResult(
    executedTools: ArrayList<UIMessagePart.Tool>,
    tool: UIMessagePart.Tool,
    result: Result<UIMessagePart.Tool>,
    json: kotlinx.serialization.json.Json,
) {
    result.onSuccess { executedTools.add(it) }
        .onFailure {
            it.printStackTrace()
            executedTools.add(
                tool.copy(
                    output = listOf(
                        UIMessagePart.Text(
                            json.encodeToString(
                                buildJsonObject {
                                    put(
                                        "error",
                                        JsonPrimitive(buildString {
                                            append("[${it.javaClass.name}] ${it.message}")
                                            append("\n${it.stackTraceToString()}")
                                        })
                                    )
                                }
                            )
                        )
                    )
                )
            )
        }
}
