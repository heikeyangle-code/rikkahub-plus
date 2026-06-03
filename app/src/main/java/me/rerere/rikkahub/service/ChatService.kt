package me.rerere.rikkahub.service

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import me.rerere.rikkahub.data.db.AppDatabase
import org.koin.java.KoinJavaComponent
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.PermissionMode
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.canResumeToolExecution
import me.rerere.ai.ui.finishPendingTools
import me.rerere.ai.ui.finishReasoning
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.common.android.Logging
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.lane.LaneTracker
import me.rerere.rikkahub.data.ai.compaction.AutoCompactor
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.policy.PolicyEngine
import me.rerere.rikkahub.data.ai.session.SessionStore
import me.rerere.rikkahub.data.ai.tools.LocalTools
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.ai.tools.createSearchTools
import me.rerere.rikkahub.data.ai.tools.createSkillTools
import me.rerere.rikkahub.data.ai.tools.createAssetTool
import me.rerere.rikkahub.data.ai.tools.createDataProcessTool
import me.rerere.rikkahub.data.ai.tools.createFileTools
import me.rerere.rikkahub.data.ai.tools.createShellTools
import me.rerere.rikkahub.data.ai.tools.createPythonTool
import me.rerere.rikkahub.data.ai.tools.createGitHubTool
import me.rerere.rikkahub.data.ai.tools.createConvertFileTool
import me.rerere.rikkahub.data.ai.tools.createDatabaseQueryTool
import me.rerere.rikkahub.data.ai.tools.createTaskTools
import me.rerere.rikkahub.data.ai.tools.createToolSearchTool
import me.rerere.rikkahub.data.ai.tools.createPlanModeTools
import me.rerere.rikkahub.data.ai.tools.createCalculatorTool
import me.rerere.rikkahub.data.ai.tools.createMcpResourceTools
import me.rerere.rikkahub.data.ai.tools.ToolRegistry
import me.rerere.rikkahub.data.ai.tools.PlanModeState
import me.rerere.rikkahub.data.ai.tools.TaskManager
import me.rerere.rikkahub.data.ai.tools.AgentRegistry
import me.rerere.rikkahub.data.ai.agent.AgentTaskTracker
import me.rerere.rikkahub.data.ai.worker.WorkerManager
import me.rerere.rikkahub.data.ai.worker.createWorkerTools
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.PromptInjectionTransformer
import me.rerere.rikkahub.data.ai.transformers.RegexOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.ai.transformers.TimeReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.AuthorsNoteTransformer
import me.rerere.rikkahub.data.ai.transformers.SkillAutoTriggerTransformer
import me.rerere.rikkahub.data.ai.transformers.KnowledgeBaseTransformer
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.web.BadRequestException
import me.rerere.rikkahub.web.NotFoundException
import me.rerere.rikkahub.utils.applyPlaceholders
import me.rerere.rikkahub.utils.sendNotification
import me.rerere.rikkahub.utils.cancelNotification
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

private const val TAG = "ChatService"

data class ChatError(
    val id: Uuid = Uuid.random(),
    val title: String? = null,
    val error: Throwable,
    val conversationId: Uuid? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val solution: ChatErrorSolution? = null,
)

enum class ChatErrorSolution {
    CheckTitleModelSettings,
}

private val inputTransformers by lazy {
    listOf(TimeReminderTransformer, PromptInjectionTransformer, AuthorsNoteTransformer, PlaceholderTransformer, DocumentAsPromptTransformer, OcrTransformer, SkillAutoTriggerTransformer)
}

private val outputTransformers by lazy {
    listOf(ThinkTagTransformer, Base64ImageToLocalFileTransformer, RegexOutputTransformer)
}

class ChatService(
    private val context: Application,
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val generationHandler: GenerationHandler,
    private val templateTransformer: TemplateTransformer,
    private val providerManager: ProviderManager,
    private val localTools: LocalTools,
    val mcpManager: McpManager,
    private val filesManager: FilesManager,
    private val skillManager: SkillManager,
    private val knowledgeBaseTransformer: KnowledgeBaseTransformer,
) {
    private val sessions = ConcurrentHashMap<Uuid, ConversationSession>()
    private val _sessionsVersion = MutableStateFlow(0L)
    private val database: AppDatabase by lazy { KoinJavaComponent.get<AppDatabase>(AppDatabase::class.java) }

    // Session persistence, auto compactor, worker manager
    private val sessionStore: SessionStore by lazy { SessionStore(context) }
    private val autoCompactor: AutoCompactor by lazy { AutoCompactor() }
    private val workerManager: WorkerManager by lazy { WorkerManager(appScope) }

    private val _errors = MutableStateFlow<List<ChatError>>(emptyList())
    val errors: StateFlow<List<ChatError>> = _errors.asStateFlow()

    fun addError(error: Throwable, conversationId: Uuid? = null, title: String? = null, solution: ChatErrorSolution? = null) {
        if (error is CancellationException) return
        _errors.update { it + ChatError(title = title, error = error, conversationId = conversationId, solution = solution) }
    }

    fun dismissError(id: Uuid) { _errors.update { it.filter { it.id != id } } }
    fun clearAllErrors() { _errors.value = emptyList() }

    private val _generationDoneFlow = MutableSharedFlow<Uuid>()
    val generationDoneFlow: SharedFlow<Uuid> = _generationDoneFlow.asSharedFlow()

    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> { _isForeground.value = true; stopGenerationForeground() }
            Lifecycle.Event.ON_STOP -> _isForeground.value = false
            else -> {}
        }
    }

    init { ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver); AgentRegistry.registerBuiltin() }

    fun cleanup() = runCatching {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        sessions.values.forEach { it.cleanup() }; sessions.clear()
    }

    private fun getOrCreateSession(conversationId: Uuid): ConversationSession {
        return sessions.computeIfAbsent(conversationId) { id ->
            val settings = settingsStore.settingsFlow.value
            ConversationSession(id = id, initial = Conversation.ofId(id = id, assistantId = settings.getCurrentAssistant().id), scope = appScope, onIdle = { removeSession(it) })
                .also { _sessionsVersion.value++ }
        }
    }

    private fun removeSession(conversationId: Uuid) {
        val session = sessions[conversationId] ?: return
        if (session.isInUse) return
        if (sessions.remove(conversationId, session)) { session.cleanup(); _sessionsVersion.value++ }
    }

    fun addConversationReference(conversationId: Uuid) { getOrCreateSession(conversationId).acquire() }
    fun removeConversationReference(conversationId: Uuid) { sessions[conversationId]?.release() }

    private fun launchWithConversationReference(conversationId: Uuid, block: suspend () -> Unit): Job = appScope.launch {
        addConversationReference(conversationId); try { block() } finally { removeConversationReference(conversationId) }
    }

    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> = getOrCreateSession(conversationId).state

    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> {
        val session = sessions[conversationId] ?: return flowOf(null); return session.generationJob
    }

    fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?> {
        val session = sessions[conversationId] ?: return MutableStateFlow(null); return session.processingStatus
    }

    fun getConversationJobs(): Flow<Map<Uuid, Job?>> {
        return _sessionsVersion.flatMapLatest {
            val currentSessions = sessions.values.toList()
            if (currentSessions.isEmpty()) flowOf(emptyMap())
            else combine(currentSessions.map { s -> s.generationJob.map { job -> s.id to job } }) { pairs -> pairs.filter { it.second != null }.toMap() }
        }
    }

    suspend fun initializeConversation(conversationId: Uuid) {
        getOrCreateSession(conversationId)
        val conversation = conversationRepo.getConversationById(conversationId)
        if (conversation != null) {
            updateConversation(conversationId, conversation)
            settingsStore.updateAssistant(conversation.assistantId)
            sessionStore.loadSnapshot(conversationId.toString())?.let { snapshot ->
                runCatching {
                    snapshot.taskState.forEach { ts -> TaskManager.restoreTask(id = ts.id, subject = ts.subject, description = ts.description,
                        status = ts.status, dependsOn = ts.dependsOn, owner = ts.owner,
                        activeForm = ts.activeForm, metadata = ts.metadata, blockedBy = ts.blockedBy) }
                    snapshot.planModeState?.let { pms ->
                        PlanModeState.isInPlanMode = pms.isInPlanMode
                        PlanModeState.effectiveMode = try { PermissionMode.valueOf(pms.effectiveMode) } catch (_: Exception) { PermissionMode.DANGER_FULL_ACCESS }
                    }
                }
            }
        } else {
            val currentSettings = settingsStore.settingsFlowRaw.first()
            val assistant = currentSettings.getCurrentAssistant()
            updateConversation(conversationId, Conversation.ofId(id = conversationId, assistantId = assistant.id, newConversation = true).updateCurrentMessages(assistant.presetMessages))
        }
    }

    fun sendMessage(conversationId: Uuid, content: List<UIMessagePart>, answer: Boolean = true) {
        if (content.isEmptyInputMessage()) return
        val session = getOrCreateSession(conversationId)
        session.getJob()?.cancel()
        val job = appScope.launch {
            try {
                runCatching { session.getJob()?.join() }
                finishInterruptedPendingTools(conversationId)
                val currentConversation = session.state.value
                val settings = settingsStore.settingsFlow.first()
                val assistant = settings.getAssistantById(currentConversation.assistantId) ?: settings.getCurrentAssistant()
                val processed = preprocessUserInputParts(content, assistant)
                val newConversation = currentConversation.copy(messageNodes = currentConversation.messageNodes + UIMessage(role = MessageRole.USER, parts = processed).toMessageNode())
                saveConversation(conversationId, newConversation)
                if (answer) handleMessageComplete(conversationId)
                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) { e.printStackTrace(); addError(e, conversationId, title = context.getString(R.string.error_title_send_message)) }
        }
        session.setJob(job)
    }

    private fun preprocessUserInputParts(parts: List<UIMessagePart>, assistant: Assistant): List<UIMessagePart> {
        return parts.map { part -> when (part) { is UIMessagePart.Text -> part.copy(text = part.text.replaceRegexes(assistant = assistant, scope = AssistantAffectScope.USER, visual = false)); else -> part } }
    }

    fun regenerateAtMessage(conversationId: Uuid, message: UIMessage, regenerateAssistantMsg: Boolean = true) {
        val session = getOrCreateSession(conversationId)
        session.getJob()?.cancel()
        val job = appScope.launch {
            try {
                val conversation = session.state.value
                if (message.role == MessageRole.USER) {
                    val node = conversation.getMessageNodeByMessage(message)
                    val idx = conversation.messageNodes.indexOf(node)
                    saveConversation(conversationId, conversation.copy(messageNodes = conversation.messageNodes.subList(0, idx + 1)))
                    handleMessageComplete(conversationId)
                } else {
                    if (regenerateAssistantMsg) {
                        val node = conversation.getMessageNodeByMessage(message)
                        handleMessageComplete(conversationId, messageRange = 0 ..< conversation.messageNodes.indexOf(node))
                    } else saveConversation(conversationId, conversation)
                }
                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) { addError(e, conversationId, title = context.getString(R.string.error_title_regenerate_message)) }
        }
        session.setJob(job)
    }

    fun handleToolApproval(conversationId: Uuid, toolCallId: String, approved: Boolean, reason: String = "", answer: String? = null) {
        val session = getOrCreateSession(conversationId)
        session.getJob()?.cancel()
        val job = appScope.launch {
            try {
                val conversation = session.state.value
                val newState = when { answer != null -> ToolApprovalState.Answered(answer); approved -> ToolApprovalState.Approved; else -> ToolApprovalState.Denied(reason) }
                val updatedNodes = conversation.messageNodes.map { node ->
                    node.copy(messages = node.messages.map { msg -> msg.copy(parts = msg.parts.map { part -> if (part is UIMessagePart.Tool && part.toolCallId == toolCallId) part.copy(approvalState = newState) else part }) })
                }
                val updatedConversation = conversation.copy(messageNodes = updatedNodes)
                saveConversation(conversationId, updatedConversation)
                val hasPending = updatedNodes.any { node -> node.currentMessage.parts.any { it is UIMessagePart.Tool && it.isPending } }
                if (!hasPending) handleMessageComplete(conversationId)
                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) { addError(e, conversationId, title = context.getString(R.string.error_title_tool_approval)) }
        }
        session.setJob(job)
    }

    private suspend fun handleMessageComplete(conversationId: Uuid, messageRange: ClosedRange<Int>? = null) {
        val settings = settingsStore.settingsFlow.first()
        val initialConversation = getConversationFlow(conversationId).value
        val assistant = settings.getAssistantById(initialConversation.assistantId) ?: settings.getCurrentAssistant()
        val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId) ?: return
        val senderName = if (assistant.useAssistantAvatar) assistant.name.ifEmpty { context.getString(R.string.assistant_page_default_assistant) } else model.displayName

        runCatching {
            updateConversation(conversationId, initialConversation.copy(chatSuggestions = emptyList()))
            if (!model.abilities.contains(ModelAbility.TOOL)) {
                if (settings.enableWebSearch || mcpManager.getAllAvailableTools().isNotEmpty())
                    addError(IllegalStateException(context.getString(R.string.tools_warning)), conversationId, title = context.getString(R.string.error_title_tool_unavailable))
            }
            checkInvalidMessages(conversationId)
            val conversation = getConversationFlow(conversationId).value
            val session = getOrCreateSession(conversationId)

            if (!isForeground.value && settings.displaySetting.enableNotificationOnMessageGeneration)
                startGenerationForeground(senderName, conversationId.toString())

            val policyEngine = PolicyEngine(currentMode = PlanModeState.effectiveMode, baseDir = context.filesDir.absolutePath)

            // Auto-compact conversation history if threshold exceeded
            if (assistant.enableAutoCompact) {
                val compactResult = autoCompactor.maybeCompact(conversation.currentMessages)
                if (compactResult != null) {
                    val compactedNodes = compactResult.compactedMessages.map { msg ->
                        MessageNode(messages = listOf(msg), selectIndex = 0)
                    }
                    updateConversation(conversationId, conversation.copy(messageNodes = compactedNodes))
                    Log.i("ChatService", "Auto-compacted ${compactResult.removedCount} old messages")
                }
            }

            generationHandler.generateText(
                settings = settings, model = model, processingStatus = session.processingStatus,
                messages = conversation.currentMessages.let { if (messageRange != null) it.subList(messageRange.start, messageRange.endInclusive + 1) else it },
                assistant = assistant, maxSteps = assistant.totalStepsLimit,
                conversationSystemPrompt = conversation.customSystemPrompt,
                conversationModeInjectionIds = conversation.modeInjectionIds,
                conversationLorebookIds = conversation.lorebookIds,
                memories = if (assistant.useGlobalMemory) memoryRepository.getGlobalMemories() else memoryRepository.getMemoriesOfAssistant(assistant.id.toString()),
                policyEngine = policyEngine,
                autoCompactor = autoCompactor,
                inputTransformers = buildList { addAll(inputTransformers); add(templateTransformer); add(knowledgeBaseTransformer) },
                outputTransformers = outputTransformers,
                tools = buildList {
                    val skillDirs = assistant.enabledSkills.mapNotNull { skillManager.getSkillDir(it)?.absolutePath }
                    if (assistant.localTools.contains(LocalToolOption.FileTools)) addAll(createFileTools(skillDirs))
                    if (assistant.localTools.contains(LocalToolOption.AssetGenerator)) add(createAssetTool(context.filesDir.absolutePath))
                    if (assistant.localTools.contains(LocalToolOption.DataProcess)) add(createDataProcessTool())
                    if (settings.enableWebSearch) addAll(createSearchTools(settings))
                    addAll(localTools.getTools(assistant.localTools))
                    if (assistant.localTools.contains(LocalToolOption.ShellTools)) addAll(createShellTools())
                    if (assistant.localTools.contains(LocalToolOption.PythonEngine)) add(createPythonTool(context, assistant.toolExecTimeout))
                    if (assistant.localTools.contains(LocalToolOption.GitHubTools)) {
                        add(createGitHubTool(settingsStore, assistant.enableCiTimeout, assistant.enableAutoFixCi))
                    }
                    if (assistant.localTools.contains(LocalToolOption.ConvertFile)) add(createConvertFileTool(context))
                    if (assistant.localTools.contains(LocalToolOption.DatabaseQuery)) add(createDatabaseQueryTool(database))
                    if (assistant.enabledSkills.isNotEmpty()) addAll(createSkillTools(enabledSkills = assistant.enabledSkills, allSkills = skillManager.listSkills(), skillManager = skillManager))
                   mcpManager.getAllAvailableTools().forEach { (serverId, tool) ->
                        add(Tool(name = "mcp__" + tool.name, description = tool.description ?: "", parameters = { tool.inputSchema }, needsApproval = tool.needsApproval, execute = { mcpManager.callTool(serverId, tool.name, it.jsonObject) }))
                    }
                    if (assistant.mcpServers.isNotEmpty()) addAll(createMcpResourceTools(mcpManager))
                    if (assistant.localTools.contains(LocalToolOption.TaskTools)) addAll(createTaskTools())
                    if (assistant.localTools.contains(LocalToolOption.ToolSearch)) { ToolRegistry.registerBuiltin(); add(createToolSearchTool()) }
                    if (assistant.localTools.contains(LocalToolOption.PlanMode)) addAll(createPlanModeTools())
                    if (assistant.localTools.contains(LocalToolOption.Calculator)) add(createCalculatorTool())
                    if (assistant.localTools.contains(LocalToolOption.WorkerTools)) {
                        addAll(createWorkerTools(workerManager))
                    }
                    if (assistant.localTools.contains(LocalToolOption.Agents)) {
                        add(
                        Tool(
                            name = "sub_agent",
                            description = """Launch a specialized agent to handle a subtask. Agents have different capabilities:
- general-purpose (default): Research, search, execute multi-step tasks
- explorer: Deep code analysis - trace execution paths and understand features
- planner: Architecture design and implementation planning
Set subagent_type to choose which agent to use.""".trimIndent().replace("\n", " "),
                            needsApproval = false,
                            parameters = {
                                InputSchema.Obj(
                                    properties = buildJsonObject {
                                        put("goal", buildJsonObject {
                                            put("type", "string")
                                            put("description", "What the agent should accomplish. Be specific and self-contained.")
                                        })
                                        put("context", buildJsonObject {
                                            put("type", "string")
                                            put("description", "Background information: code, data, text, etc.")
                                        })
                                        put("subagent_type", buildJsonObject {
                                            put("type", "string")
                                            put("description", "Agent role: 'general-purpose' (default), 'explorer' (code analysis), 'planner' (architecture design)")
                                        })
                                        put("model", buildJsonObject {
                                            put("type", "string")
                                            put("description", "Optional model override for this agent (e.g. 'sonnet', 'opus'). Uses default if omitted.")
                                        })
                                    },
                                    required = listOf("goal"),
                                )
                            },
                            execute = {
                                val obj = it.jsonObject
                                val goal = obj["goal"]?.jsonPrimitive?.content
                                    ?: error("goal is required")
                                val context = obj["context"]?.jsonPrimitive?.contentOrNull ?: ""
                                val agentType = obj["subagent_type"]?.jsonPrimitive?.contentOrNull ?: "general-purpose"
                                val modelOverride = obj["model"]?.jsonPrimitive?.contentOrNull
                                val runInBackground = obj["run_in_background"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                                val teamName = obj["team_name"]?.jsonPrimitive?.contentOrNull

                                // Load agent definition
                                val agentDef = AgentRegistry.get(agentType)
                                val agentCallId = "agent_${System.currentTimeMillis()}"

                                // Track progress
                                AgentTaskTracker.createSession(agentCallId)

                                // Register in team if specified
                                if (!teamName.isNullOrBlank()) {
                                    TaskManager.createTeam(teamName, "Agent team: $teamName")
                                    TaskManager.createTask(subject = goal, description = context, activeForm = agentCallId)
                                }

                                // 记住进入子Agent前的主Agent状态，退出后恢复
                                val preSubStatus = session.processingStatus.value

                                // 创建 LaneTracker 追踪子 Agent 执行生命周期
                                val laneTracker = LaneTracker()
                                laneTracker.started()

                                // Resolve model for sub-agent
                                val effectiveModelId = agentDef?.modelId?.let { Uuid.parse(it) }
                                    ?: assistant.subAgentModelId
                                    ?: assistant.chatModelId
                                    ?: settings.chatModelId
                                val subModel = settings.findModelById(effectiveModelId)
                                    ?: error("Model not found for sub-agent")
                                val providerSetting = subModel.findProvider(settings.providers)
                                    ?: error("Provider not found for model ${subModel.id}")
                                @Suppress("UNCHECKED_CAST")
                                val providerImpl = providerManager.getProviderByType(providerSetting)
                                    as me.rerere.ai.provider.Provider<me.rerere.ai.provider.ProviderSetting>

                                // Build curated tools for sub-agent
                                val skillDirs = assistant.enabledSkills
                                    .mapNotNull { skillManager.getSkillDir(it)?.absolutePath }

                                // Agent's tool whitelist (if restricted)
                                val allowedToolNames = agentDef?.tools
                                val allTools = buildList {
                                    if (settings.enableWebSearch) {
                                        addAll(createSearchTools(settings))
                                    }
                                    if (assistant.localTools.contains(LocalToolOption.FileTools)) {
                                        addAll(createFileTools(skillDirs))
                                    }
                                    addAll(localTools.getTools(listOf(LocalToolOption.TimeInfo)))
                                }
                                val subTools = if (allowedToolNames != null && allowedToolNames != listOf("*")) {
                                    allTools.filter { it.name in allowedToolNames }
                                } else {
                                    allTools
                                }

                                // Build prompt with agent system prompt and memory
                                val prompt = buildString {
                                    val sysPrompt = agentDef?.systemPrompt
                                    if (!sysPrompt.isNullOrBlank()) {
                                        appendLine(sysPrompt)
                                        appendLine()
                                    }
                                    // Load agent memory from repository
                                    val agentMemories = memoryRepository.getMemoriesOfAssistant("agent:$agentType")
                                    if (agentMemories.isNotEmpty()) {
                                        appendLine("## Persistent Agent Memory")
                                        agentMemories.forEach { m ->
                                            appendLine("- ${m.content}")
                                        }
                                        appendLine()
                                    }
                                    appendLine("Goal: $goal")
                                    if (context.isNotBlank()) {
                                        appendLine()
                                        appendLine("Context: $context")
                                    }
                                    appendLine()
                                    appendLine("You have access to tools. Use them when needed.")
                                    appendLine("After using tools, continue working until the goal is complete.")
                                    appendLine("When done, summarize what was accomplished.")
                                }

                                // Define the agent execution function (shared by sync and background modes)
                                suspend fun runAgent(): List<UIMessagePart> {

                                // Tool loop (max N rounds, no refunds)
                                val messages = mutableListOf(UIMessage.user(prompt))
                                    var finalText = ""
                                    var remainingSteps = assistant.subAgentMaxSteps
                                    val stepLog = StringBuilder()

                                    while (remainingSteps > 0) {
                                        remainingSteps--

                                        try {
                                        // Show real-time progress in UI (不覆盖主Agent状态，只追加)
                                        val stepNum = stepLog.count { it == '\n' } + 1
                                        val currentStatus = session.processingStatus.value
                                        session.processingStatus.value = currentStatus + " | 子Agent: 第${stepNum}步..."

                                        val chunk = providerImpl.generateText(
                                            providerSetting = providerSetting,
                                            messages = messages,
                                            params = me.rerere.ai.provider.TextGenerationParams(
                                                model = subModel,
                                                tools = subTools,
                                                reasoningLevel = me.rerere.ai.core.ReasoningLevel.OFF,
                                            ),
                                        )

                                        val assistantMsg = chunk.choices.firstOrNull()?.message
                                        if (assistantMsg == null) break

                                        // Save any assistant text content for fallback
                                        val assistantText = assistantMsg.toText()
                                        val toolCalls = assistantMsg.getTools()
                                            .filter { !it.isExecuted }

                                        if (toolCalls.isEmpty()) {
                                            // No tool calls — done
                                            finalText = assistantText
                                            stepLog.appendLine("→ 分析完成，生成回答")
                                            break
                                        }

                                        // Log tool calls for this step
                                        stepLog.append("→ 第${stepNum}步：")
                                        stepLog.appendLine(toolCalls.joinToString("、") { tc ->
                                            val args = tc.input.ifBlank { "{}" }
                                            "${tc.toolName}(${args.take(40)})"
                                        })
                                        session.processingStatus.value = "子Agent: 调${toolCalls.first().toolName}..."

                                        // Execute tools
                                        val executedTools = toolCalls.map { toolCall ->
                                            AgentTaskTracker.recordToolUse(agentCallId, toolCall.toolName, toolCall.toolName)
                                            val toolDef = subTools.find { it.name == toolCall.toolName }
                                            if (toolDef == null) {
                                                toolCall.copy(
                                                    output = listOf(UIMessagePart.Text("Error: tool ${toolCall.toolName} not found"))
                                                )
                                            } else {
                                                val args = try {
                                                    kotlinx.serialization.json.Json.parseToJsonElement(
                                                        toolCall.input.ifBlank { "{}" }
                                                    )
                                                } catch (e: Exception) {
                                                    error("Invalid arguments: ${e.message}")
                                                }
                                                val result = toolDef.execute(args)
                                                toolCall.copy(output = result)
                                            }
                                        }

                                        // Append assistant message (with tool calls) + tool results
                                        messages.add(assistantMsg.copy(
                                            parts = assistantMsg.parts.map { part ->
                                                if (part is UIMessagePart.Tool) {
                                                    executedTools.find { it.toolCallId == part.toolCallId } ?: part
                                                } else part
                                            }
                                        ))
                                        } catch (e: Exception) {
                                            laneTracker.failed(e.message ?: e.javaClass.simpleName)
                                            stepLog.appendLine("→ 错误: ${e.message?.take(100) ?: e.javaClass.simpleName}")
                                            AgentTaskTracker.endSession(agentCallId)
                                            break
                                        }
                                    }

                                    // Fallback: if loop ended without text, extract from most recent message
                                    if (finalText.isBlank()) {
                                        finalText = messages.lastOrNull()?.toText()?.takeIf { it.isNotBlank() } ?: ""
                                    }

                                    // Prepend step log to final output
                                    val outputText = if (stepLog.isNotEmpty()) {
                                        stepLog.appendLine()
                                        stepLog.append(finalText)
                                        stepLog.toString()
                                    } else {
                                        finalText
                                    }

                                    // End agent session tracking
                                    AgentTaskTracker.endSession(agentCallId)

                                    // Restore main agent status

                                    // 在最终返回值前标记 LaneTracker 完成
                                    laneTracker.finished("Sub-agent completed")

                                    // 恢复主Agent状态，清除子Agent残留文字
                                    session.processingStatus.value = preSubStatus
                                    listOf(UIMessagePart.Text(outputText))
                                }

                                // Execute: sync or background
                                if (runInBackground) {
                                    appScope.launch {
                                        runAgent()
                                        if (!teamName.isNullOrBlank()) {
                                            TaskManager.listTasks().lastOrNull()?.let {
                                                TaskManager.updateTask(it.id, me.rerere.rikkahub.data.ai.tools.TaskStatus.COMPLETED)
                                            }
                                        }
                                    }
                                    listOf(UIMessagePart.Text(buildJsonObject {
                                        put("status", "async_launched")
                                        put("agent_id", agentCallId)
                                        put("goal", goal)
                                    }.toString()))
                                } else {
                                    runAgent()
                                }
                            },
                            )
                        )
                    }
                },
            ).onCompletion {
                cancelLiveUpdateNotification(conversationId)
                stopGenerationForeground()
                val updatedConversation = getConversationFlow(conversationId).value.copy(
                    messageNodes = getConversationFlow(conversationId).value.messageNodes.map { it.copy(messages = it.messages.map { msg -> msg.finishReasoning() }) },
                    updateAt = Instant.now())
                updateConversation(conversationId, updatedConversation)
                if (!isForeground.value && settings.displaySetting.enableNotificationOnMessageGeneration)
                    sendGenerationDoneNotification(conversationId, senderName)
            }.collect { chunk ->
                when (chunk) {
                    is GenerationChunk.Messages -> {
                        val updatedConversation = getConversationFlow(conversationId).value.updateCurrentMessages(chunk.messages)
                        updateConversation(conversationId, updatedConversation)
                        if (isForeground.value) stopGenerationForeground()
                        if (!isForeground.value && settings.displaySetting.enableNotificationOnMessageGeneration && settings.displaySetting.enableLiveUpdateNotification)
                            sendLiveUpdateNotification(conversationId, chunk.messages, senderName)
                    }
                }
            }
        }.onFailure {
            cancelLiveUpdateNotification(conversationId); stopGenerationForeground()
            it.printStackTrace(); addError(it, conversationId, title = context.getString(R.string.error_title_generation))
            Logging.log(TAG, "handleMessageComplete: $it")
        }.onSuccess {
            val finalConversation = getConversationFlow(conversationId).value
            saveConversation(conversationId, finalConversation)
            launchWithConversationReference(conversationId) { generateTitle(conversationId, finalConversation) }
            launchWithConversationReference(conversationId) { generateSuggestion(conversationId, finalConversation) }
        }
    }

    private suspend fun saveConversationSnapshot(conversationId: Uuid, messages: List<UIMessage>) {
        try {
            appScope.launch {
                runCatching {
                    sessionStore.saveSnapshot(me.rerere.rikkahub.data.ai.session.SessionSnapshot(
                        sessionId = conversationId.toString(), messages = messages,
                        taskState = TaskManager.listTasks().map { me.rerere.rikkahub.data.ai.session.TaskSnapshot(
                            id = it.id, subject = it.subject, description = it.description,
                            status = it.status.name, dependsOn = it.dependsOn,
                            owner = it.owner, activeForm = it.activeForm,
                            metadata = it.metadata, blockedBy = it.blockedBy,
                        ) },
                        planModeState = me.rerere.rikkahub.data.ai.session.PlanModeSnapshot(PlanModeState.isInPlanMode, PlanModeState.effectiveMode.name),
                    ))
                }
            }
        } catch (_: Exception) { }
    }

    private fun checkInvalidMessages(conversationId: Uuid) {
        val conversation = getConversationFlow(conversationId).value
        var nodes = conversation.messageNodes
        nodes = nodes.mapIndexed { _, node ->
            if (node.currentMessage.getTools().any { !it.isExecuted }) {
                if (node.currentMessage.getTools().any { !it.isExecuted && it.approvalState.canResumeToolExecution() }) return@mapIndexed node
                if (node.currentMessage.getTools().all { it.isExecuted } && node.currentMessage.getTools().isNotEmpty()) return@mapIndexed node
                return@mapIndexed node.copy(messages = node.messages.filter { it.id != node.currentMessage.id }, selectIndex = node.selectIndex - 1)
            }
            node
        }
        nodes = nodes.map { if (it.messages.isNotEmpty() && it.selectIndex !in it.messages.indices) it.copy(selectIndex = 0) else it }
        nodes = nodes.filter { it.messages.isNotEmpty() }
        updateConversation(conversationId, conversation.copy(messageNodes = nodes))
    }

    private fun cancelToolByUser(tool: UIMessagePart.Tool) = tool.copy(output = listOf(UIMessagePart.Text("""{"status":"cancelled"}""")), approvalState = ToolApprovalState.Denied("cancelled"))

    private suspend fun finishInterruptedPendingTools(conversationId: Uuid) {
        val current = getConversationFlow(conversationId).value
        val lastNode = current.messageNodes.lastOrNull() ?: return
        val lastMsg = lastNode.currentMessage
        val updated = lastMsg.finishPendingTools(::cancelToolByUser) ?: return
        saveConversation(conversationId, current.copy(messageNodes = current.messageNodes.dropLast(1) + lastNode.copy(messages = lastNode.messages.map { if (it.id == lastMsg.id) updated else it })))
    }

    suspend fun generateTitle(conversationId: Uuid, conversation: Conversation, force: Boolean = false) {
        if (!force && conversation.title.isNotBlank()) return
        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model = settings.findModelById(settings.titleModelId) ?: return
            val provider = model.findProvider(settings.providers) ?: return
            val result = providerManager.getProviderByType(provider).generateText(providerSetting = provider,
                messages = listOf(UIMessage.user(settings.titlePrompt.applyPlaceholders("locale" to Locale.getDefault().displayName, "content" to conversation.currentMessages.takeLast(4).joinToString("\n\n") { it.summaryAsText() }))),
                params = TextGenerationParams(model = model, reasoningLevel = ReasoningLevel.OFF))
            conversationRepo.getConversationById(conversation.id)?.let { saveConversation(conversationId, it.copy(title = result.choices[0].message?.toText()?.trim() ?: "")) }
        }.onFailure { addError(error = it, conversationId = conversationId, title = context.getString(R.string.error_title_generate_title), solution = ChatErrorSolution.CheckTitleModelSettings) }
    }

    suspend fun generateSuggestion(conversationId: Uuid, conversation: Conversation) {
        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model = settings.findModelById(settings.suggestionModelId) ?: return
            val provider = model.findProvider(settings.providers) ?: return
            sessions[conversationId]?.let { updateConversation(conversationId, it.state.value.copy(chatSuggestions = emptyList())) }
            val result = providerManager.getProviderByType(provider).generateText(providerSetting = provider,
                messages = listOf(UIMessage.user(settings.suggestionPrompt.applyPlaceholders("locale" to Locale.getDefault().displayName, "content" to conversation.currentMessages.takeLast(8).joinToString("\n\n") { it.summaryAsText() }))),
                params = TextGenerationParams(model = model, reasoningLevel = ReasoningLevel.OFF))
            val suggestions = result.choices[0].message?.toText()?.split("\n")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            val latest = conversationRepo.getConversationById(conversationId) ?: sessions[conversationId]?.state?.value ?: conversation
            saveConversation(conversationId, latest.copy(chatSuggestions = suggestions.take(10)))
        }.onFailure { it.printStackTrace() }
    }

    suspend fun generateForAssistant(assistant: Assistant, settings: Settings, prompt: String, history: List<UIMessage>, onChunk: ((String, List<UIMessagePart>?) -> Unit)? = null): String {
        val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId) ?: error("No model")
        val messages = history + UIMessage.user(prompt)
        var result = ""
        val skillDirs = assistant.enabledSkills.mapNotNull { skillManager.getSkillDir(it)?.absolutePath }
        val policyEngine = PolicyEngine(currentMode = PlanModeState.effectiveMode, baseDir = context.filesDir.absolutePath)

        generationHandler.generateText(settings = settings, model = model, messages = messages, assistant = assistant,
            policyEngine = policyEngine,
            memories = if (assistant.useGlobalMemory) memoryRepository.getGlobalMemories() else memoryRepository.getMemoriesOfAssistant(assistant.id.toString()),
            tools = buildList {
                if (assistant.localTools.contains(LocalToolOption.FileTools)) addAll(createFileTools(skillDirs))
                if (settings.enableWebSearch) addAll(createSearchTools(settings))
                addAll(localTools.getTools(assistant.localTools))
                if (assistant.localTools.contains(LocalToolOption.ShellTools)) addAll(createShellTools())
                if (assistant.localTools.contains(LocalToolOption.GitHubTools)) {
                    add(createGitHubTool(settingsStore, assistant.enableCiTimeout, assistant.enableAutoFixCi))
                }
                if (assistant.localTools.contains(LocalToolOption.ConvertFile)) add(createConvertFileTool(context))
                if (assistant.localTools.contains(LocalToolOption.DatabaseQuery)) add(createDatabaseQueryTool(database))
                if (assistant.enabledSkills.isNotEmpty()) addAll(createSkillTools(enabledSkills = assistant.enabledSkills, allSkills = skillManager.listSkills(), skillManager = skillManager))
                mcpManager.getAllAvailableTools().forEach { (serverId, tool) ->
                    add(Tool(name = "mcp__" + tool.name, description = tool.description ?: "", parameters = { tool.inputSchema }, needsApproval = tool.needsApproval, execute = { mcpManager.callTool(serverId, tool.name, it.jsonObject) }))
                }
                if (assistant.mcpServers.isNotEmpty()) addAll(createMcpResourceTools(mcpManager))
                if (assistant.localTools.contains(LocalToolOption.TaskTools)) addAll(createTaskTools())
                if (assistant.localTools.contains(LocalToolOption.ToolSearch)) { ToolRegistry.registerBuiltin(); add(createToolSearchTool()) }
                if (assistant.localTools.contains(LocalToolOption.PlanMode)) addAll(createPlanModeTools())
                if (assistant.localTools.contains(LocalToolOption.Calculator)) add(createCalculatorTool())
                if (assistant.localTools.contains(LocalToolOption.WorkerTools)) addAll(createWorkerTools(workerManager))
            },
            inputTransformers = buildList { addAll(inputTransformers); add(templateTransformer); add(knowledgeBaseTransformer) },
            outputTransformers = outputTransformers,
        ).collect { chunk ->
            when (chunk) {
                is GenerationChunk.Messages -> {
                    val lastMsg = chunk.messages.lastOrNull()
                    val text = lastMsg?.toText() ?: ""; result = text
                    onChunk?.invoke(text, lastMsg?.parts)
                }
            }
        }
        return result
    }

    suspend fun compressConversation(conversationId: Uuid, conversation: Conversation, additionalPrompt: String, targetTokens: Int, keepRecentMessages: Int = 32): Result<Unit> = runCatching {
        val settings = settingsStore.settingsFlow.first()
        val model = settings.findModelById(settings.compressModelId) ?: settings.getCurrentChatModel() ?: error("No model")
        val provider = model.findProvider(settings.providers) ?: error("No provider")
        val providerHandler = providerManager.getProviderByType(provider)
        val allMessages = conversation.currentMessages
        val (toCompress, toKeep) = if (keepRecentMessages > 0 && allMessages.size > keepRecentMessages)
            allMessages.dropLast(keepRecentMessages) to allMessages.takeLast(keepRecentMessages)
        else if (keepRecentMessages > 0) error("Not enough messages") else allMessages to emptyList()

        fun split(messages: List<UIMessage>): List<List<UIMessage>> {
            if (messages.size <= 256) return listOf(messages)
            val mid = messages.size / 2; return split(messages.subList(0, mid)) + split(messages.subList(mid, messages.size))
        }

        suspend fun compress(messages: List<UIMessage>): String {
            val chunk = providerHandler.generateText(providerSetting = provider,
                messages = listOf(UIMessage.user(settings.compressPrompt.applyPlaceholders("content" to messages.joinToString("\n\n") { it.summaryAsText() }, "target_tokens" to targetTokens.toString(), "additional_context" to additionalPrompt.ifBlank { "" }, "locale" to Locale.getDefault().displayName))),
                params = TextGenerationParams(model = model))
            return chunk.choices[0].message?.toText()?.trim() ?: error("Compression failed")
        }

        val summaries = coroutineScope { split(toCompress).map { async { compress(it) } }.awaitAll() }
        val newNodes = buildList { summaries.forEach { add(UIMessage.user(it).toMessageNode()) }; addAll(toKeep.map { it.toMessageNode() }) }
        saveConversation(conversationId, conversation.copy(messageNodes = newNodes, chatSuggestions = emptyList()))
    }

    private fun sendGenerationDoneNotification(conversationId: Uuid, senderName: String) {
        cancelLiveUpdateNotification(conversationId)
        val conversation = getConversationFlow(conversationId).value
        context.sendNotification(channelId = CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID, notificationId = 1) {
            title = senderName; content = conversation.currentMessages.lastOrNull()?.toText()?.take(50)?.trim() ?: ""
            autoCancel = true; useDefaults = true; category = NotificationCompat.CATEGORY_MESSAGE
            contentIntent = getPendingIntent(context, conversationId)
        }
    }

    private fun getLiveUpdateNotificationId(conversationId: Uuid) = conversationId.hashCode() + 10000

    private fun sendLiveUpdateNotification(conversationId: Uuid, messages: List<UIMessage>, senderName: String) {
        val lastMessage = messages.lastOrNull() ?: return
        val parts = lastMessage.parts
        val lastReasoning = parts.filterIsInstance<UIMessagePart.Reasoning>().lastOrNull()
        val lastTool = parts.filterIsInstance<UIMessagePart.Tool>().lastOrNull()
        val lastText = parts.filterIsInstance<UIMessagePart.Text>().lastOrNull()
        val (chip, sub, content) = when {
            lastTool != null && !lastTool.isExecuted -> Triple(context.getString(R.string.notification_live_update_chip_tool), context.getString(R.string.notification_live_update_tool, lastTool.toolName.removePrefix("mcp__")), lastTool.input.take(100))
            lastReasoning != null && lastReasoning.finishedAt == null -> Triple(context.getString(R.string.notification_live_update_chip_thinking), context.getString(R.string.notification_live_update_thinking), lastReasoning.reasoning.takeLast(200))
            lastText != null -> Triple(context.getString(R.string.notification_live_update_chip_writing), context.getString(R.string.notification_live_update_writing), lastText.text.takeLast(200))
            else -> Triple(context.getString(R.string.notification_live_update_chip_writing), context.getString(R.string.notification_live_update_title), "")
        }
        context.sendNotification(channelId = CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID, notificationId = getLiveUpdateNotificationId(conversationId)) {
            title = senderName; this.content = content; subText = sub; ongoing = true; onlyAlertOnce = true
            category = NotificationCompat.CATEGORY_PROGRESS; useBigTextStyle = true
            contentIntent = getPendingIntent(context, conversationId); requestPromotedOngoing = true; shortCriticalText = chip
        }
    }

    private fun cancelLiveUpdateNotification(conversationId: Uuid) { context.cancelNotification(getLiveUpdateNotificationId(conversationId)) }

    private fun startGenerationForeground(title: String, conversationId: String) {
        context.startForegroundService(Intent(context, GenerationForegroundService::class.java).apply {
            action = GenerationForegroundService.ACTION_START; putExtra(GenerationForegroundService.EXTRA_TITLE, title)
            putExtra(GenerationForegroundService.EXTRA_CONVERSATION_ID, conversationId)
        })
    }

    private fun stopGenerationForeground() { context.stopService(Intent(context, GenerationForegroundService::class.java).apply { action = GenerationForegroundService.ACTION_STOP }) }

    private fun getPendingIntent(context: Context, conversationId: Uuid): PendingIntent {
        return PendingIntent.getActivity(context, conversationId.hashCode(),
            Intent(context, RouteActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("conversationId", conversationId.toString())
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun updateConversation(conversationId: Uuid, conversation: Conversation) {
        if (conversation.id != conversationId) return
        val session = getOrCreateSession(conversationId)
        checkFilesDelete(conversation, session.state.value)
        session.state.value = conversation
    }

    fun updateConversationState(conversationId: Uuid, update: (Conversation) -> Conversation) {
        val current = getConversationFlow(conversationId).value; updateConversation(conversationId, update(current))
    }

    private fun checkFilesDelete(newConversation: Conversation, oldConversation: Conversation) {
        val newFiles = newConversation.files
        val oldFiles = oldConversation.files
        val deletedFiles = oldFiles.filter { file -> newFiles.none { it == file } }
        if (deletedFiles.isNotEmpty()) {
            filesManager.deleteChatFiles(deletedFiles)
            Log.w(TAG, "checkFilesDelete: $deletedFiles")
        }
    }

    suspend fun saveConversation(conversationId: Uuid, conversation: Conversation) {
        val exists = conversationRepo.existsConversationById(conversation.id)
        if (!exists && conversation.title.isBlank() && conversation.messageNodes.isEmpty()) return
        val updated = conversation.copy()
        updateConversation(conversationId, updated)
        if (!exists) conversationRepo.insertConversation(updated) else conversationRepo.updateConversation(updated)
        saveConversationSnapshot(conversationId, updated.currentMessages)
    }

    fun translateMessage(conversationId: Uuid, message: UIMessage, targetLanguage: Locale) {
        appScope.launch(Dispatchers.IO) {
            try {
                val settings = settingsStore.settingsFlow.first()
                val messageText = message.parts.filterIsInstance<UIMessagePart.Text>().joinToString("\n\n") { it.text }.trim()
                if (messageText.isBlank()) return@launch
                updateTranslationField(conversationId, message.id, context.getString(R.string.translating))
                generationHandler.translateText(settings = settings, sourceText = messageText, targetLanguage = targetLanguage) { translatedText ->
                    updateTranslationField(conversationId, message.id, translatedText)
                }.collect { }
                saveConversation(conversationId, getConversationFlow(conversationId).value)
            } catch (e: Exception) { clearTranslationField(conversationId, message.id); addError(e, conversationId, title = context.getString(R.string.error_title_translate_message)) }
        }
    }

    private fun updateTranslationField(conversationId: Uuid, messageId: Uuid, translationText: String) {
        val current = getConversationFlow(conversationId).value
        val updated = current.copy(messageNodes = current.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) node.copy(messages = node.messages.map { if (it.id == messageId) it.copy(translation = translationText) else it }) else node
        })
        updateConversation(conversationId, updated)
    }

    suspend fun editMessage(conversationId: Uuid, messageId: Uuid, parts: List<UIMessagePart>) {
        if (parts.isEmptyInputMessage()) return
        val currentConversation = getConversationFlow(conversationId).value
        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.getAssistantById(currentConversation.assistantId) ?: settings.getCurrentAssistant()
        val processedParts = preprocessUserInputParts(parts, assistant)
        var edited = false
        val updatedNodes = currentConversation.messageNodes.mapIndexed { index, node ->
            if (!node.messages.any { it.id == messageId }) return@mapIndexed node
            edited = true; node.copy(messages = node.messages + UIMessage(role = node.role, parts = processedParts), selectIndex = node.messages.size)
        }
        if (!edited) return
        val editIndex = updatedNodes.indexOfFirst { it.messages.any { msg -> msg.id == messageId } }
        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes.take(editIndex + 1)))
        handleMessageComplete(conversationId)
    }

    suspend fun forkConversationAtMessage(conversationId: Uuid, messageId: Uuid): Conversation {
        val current = getConversationFlow(conversationId).value
        val idx = current.messageNodes.indexOfFirst { it.messages.any { msg -> msg.id == messageId } }
        if (idx == -1) throw NotFoundException("Message not found")
        val copied = current.messageNodes.subList(0, idx + 1).map { it.copy(id = Uuid.random(), messages = it.messages.map { msg -> msg.copy(parts = msg.parts.map { part -> copyWithForkedFileUrl(part) }) }) }
        val fork = Conversation(id = Uuid.random(), assistantId = current.assistantId, messageNodes = copied,
            customSystemPrompt = current.customSystemPrompt, modeInjectionIds = current.modeInjectionIds, lorebookIds = current.lorebookIds)
        saveConversation(fork.id, fork); return fork
    }

    private fun copyWithForkedFileUrl(part: UIMessagePart): UIMessagePart {
        fun copyIfNeeded(url: String): String {
            if (!url.startsWith("file:")) return url
            return filesManager.createChatFilesByContents(listOf(url.toUri())).firstOrNull()?.toString() ?: url
        }
        return when (part) {
            is UIMessagePart.Image -> part.copy(url = copyIfNeeded(part.url))
            is UIMessagePart.Document -> part.copy(url = copyIfNeeded(part.url))
            is UIMessagePart.Video -> part.copy(url = copyIfNeeded(part.url))
            is UIMessagePart.Audio -> part.copy(url = copyIfNeeded(part.url))
            else -> part
        }
    }

    suspend fun selectMessageNode(conversationId: Uuid, nodeId: Uuid, selectIndex: Int) {
        val current = getConversationFlow(conversationId).value
        val target = current.messageNodes.firstOrNull { it.id == nodeId } ?: throw NotFoundException("Node not found")
        if (selectIndex !in target.messages.indices) throw BadRequestException("Invalid index")
        if (target.selectIndex == selectIndex) return
        saveConversation(conversationId, current.copy(messageNodes = current.messageNodes.map { if (it.id == nodeId) it.copy(selectIndex = selectIndex) else it }))
    }

    suspend fun deleteMessage(conversationId: Uuid, messageId: Uuid, failIfMissing: Boolean = true) {
        val current = getConversationFlow(conversationId).value
        val targetIdx = current.messageNodes.indexOfFirst { it.messages.any { msg -> msg.id == messageId } }
        if (targetIdx == -1) { if (failIfMissing) throw NotFoundException("Message not found"); return }
        val updated = current.messageNodes.mapIndexedNotNull { idx, node ->
            if (idx != targetIdx) return@mapIndexedNotNull node
            val remaining = node.messages.filterNot { it.id == messageId }
            if (remaining.isEmpty()) null else node.copy(messages = remaining, selectIndex = node.selectIndex.coerceAtMost(remaining.lastIndex))
        }
        saveConversation(conversationId, current.copy(messageNodes = updated))
    }

    suspend fun deleteMessage(conversationId: Uuid, message: UIMessage) { deleteMessage(conversationId, message.id, failIfMissing = false) }

    fun clearTranslationField(conversationId: Uuid, messageId: Uuid) {
        val current = getConversationFlow(conversationId).value
        updateConversation(conversationId, current.copy(messageNodes = current.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) node.copy(messages = node.messages.map { if (it.id == messageId) it.copy(translation = null) else it }) else node
        }))
    }

    suspend fun stopGeneration(conversationId: Uuid) {
        val job = sessions[conversationId]?.getJob() ?: return
        job.cancel(); runCatching { job.join() }; finishInterruptedPendingTools(conversationId)
    }
}
