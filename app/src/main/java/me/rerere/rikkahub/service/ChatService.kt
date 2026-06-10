package me.rerere.rikkahub.service

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
import kotlinx.coroutines.flow.drop
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
import me.rerere.rikkahub.data.ai.listener.AgentEvent
import me.rerere.rikkahub.data.ai.listener.AgentEventBus as ListenerEventBus
import me.rerere.rikkahub.data.ai.listener.AgentService
import me.rerere.rikkahub.data.ai.session.SessionStore
import me.rerere.rikkahub.data.ai.compaction.AutoCompactor
import me.rerere.rikkahub.data.ai.policy.PolicyEngine
import me.rerere.rikkahub.data.ai.tools.PlanModeState
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.tools.LocalTools
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.ai.tools.createSearchTools
import me.rerere.rikkahub.data.ai.tools.createSendMessageTool
import me.rerere.rikkahub.data.ai.tools.createGetTeammateMessagesTool
import me.rerere.rikkahub.data.ai.tools.createKanbanTools
import me.rerere.rikkahub.data.ai.tools.createWebFetchTool
import me.rerere.rikkahub.data.ai.tools.createSleepTool
import me.rerere.rikkahub.data.ai.tools.createCalculatorTool
import me.rerere.rikkahub.data.ai.tools.createTaskTools
import me.rerere.rikkahub.data.ai.tools.createTeammateTools
import me.rerere.rikkahub.data.ai.tools.createPlanModeTools
import me.rerere.rikkahub.data.ai.tools.createMcpResourceTools
import me.rerere.rikkahub.data.ai.worker.createWorkerTools
import me.rerere.rikkahub.data.ai.worker.WorkerManager
import me.rerere.rikkahub.data.ai.tools.AgentRegistry
import me.rerere.rikkahub.data.ai.tools.AgentLoader
import me.rerere.rikkahub.data.ai.tools.AgentSystemPrompt
import me.rerere.rikkahub.data.ai.agent.AgentTaskTracker
import me.rerere.rikkahub.data.ai.agent.AgentRunner
import me.rerere.rikkahub.data.ai.agent.AgentMemoryManager
import me.rerere.rikkahub.data.ai.lane.LaneTracker
import me.rerere.rikkahub.data.ai.tools.TaskManager
import me.rerere.rikkahub.data.ai.tools.isToolAllowed
import me.rerere.rikkahub.data.ai.tools.AgentColor
import me.rerere.rikkahub.data.ai.tools.AgentDefinition
import me.rerere.rikkahub.data.ai.tools.AgentMemoryScope
import me.rerere.rikkahub.data.ai.tools.AgentSource
import me.rerere.rikkahub.data.ai.agent.AgentExecutionEvent
import me.rerere.rikkahub.data.ai.agent.AgentEventBus
import me.rerere.rikkahub.data.ai.agent.TeammateRunner
import me.rerere.rikkahub.data.ai.tools.isToolAllowedForAsync
import me.rerere.rikkahub.data.ai.tools.formatAgentTools
import me.rerere.rikkahub.data.ai.tools.createSkillTools
import me.rerere.rikkahub.data.ai.tools.createAssetTool
import me.rerere.rikkahub.data.ai.tools.createDataProcessTool
import me.rerere.rikkahub.data.ai.tools.createFileTools
import me.rerere.rikkahub.data.ai.tools.createShellTools
import me.rerere.rikkahub.data.ai.tools.createPythonTool
import me.rerere.rikkahub.data.ai.tools.createGitHubTool
import me.rerere.rikkahub.data.ai.tools.createConvertFileTool
import me.rerere.rikkahub.data.ai.tools.createDatabaseQueryTool
import me.rerere.rikkahub.data.ai.tools.deduplicateTools
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
    listOf(
        TimeReminderTransformer,
        PromptInjectionTransformer,
        AuthorsNoteTransformer,
        PlaceholderTransformer,
        DocumentAsPromptTransformer,
        OcrTransformer,
        SkillAutoTriggerTransformer,
    )
}

private val outputTransformers by lazy {
    listOf(
        ThinkTagTransformer,
        Base64ImageToLocalFileTransformer,
        RegexOutputTransformer,
    )
}

class ChatService(
    private val context: Application,
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val generationHandler: GenerationHandler,
    private val agentPipeline: me.rerere.rikkahub.data.ai.harness.AgentPipeline,
    private val templateTransformer: TemplateTransformer,
    private val providerManager: ProviderManager,
    private val localTools: LocalTools,
    val mcpManager: McpManager,
    private val filesManager: FilesManager,
    private val skillManager: SkillManager,
    private val knowledgeBaseTransformer: KnowledgeBaseTransformer,
) {
    // 统一会话管理
    private val sessions = ConcurrentHashMap<Uuid, ConversationSession>()
    private val _sessionsVersion = MutableStateFlow(0L)

    private val database: AppDatabase by lazy {
        KoinJavaComponent.get<AppDatabase>(AppDatabase::class.java)
    }
    private val workerManager: WorkerManager by lazy {
        WorkerManager(appScope) { workerId, prompt ->
            // Android 同进程 Worker：执行一次带工具的 LLM 调用
            val s = settingsStore.settingsFlow.value
            val m = s.getCurrentChatModel() ?: return@WorkerManager "No model"
            val p = m.findProvider(s.providers) ?: return@WorkerManager "No provider"
            @Suppress("UNCHECKED_CAST")
            val impl = providerManager.getProviderByType(p)
                as me.rerere.ai.provider.Provider<me.rerere.ai.provider.ProviderSetting>
            val tools = buildList {
                if (s.enableWebSearch) {
                    addAll(createSearchTools(s))
                    add(createWebFetchTool())
                }
                addAll(localTools.getTools(listOf(me.rerere.rikkahub.data.ai.tools.LocalToolOption.TimeInfo)))
                add(createSleepTool())
            }
            val messages = listOf(
                me.rerere.ai.ui.UIMessage.system("You are a helpful worker agent. Complete the task using tools as needed. Summarize what you did."),
                me.rerere.ai.ui.UIMessage.user(prompt),
            )
            val chunk = impl.generateText(
                providerSetting = p,
                messages = messages,
                params = me.rerere.ai.provider.TextGenerationParams(
                    model = m,
                    tools = tools,
                    reasoningLevel = me.rerere.ai.core.ReasoningLevel.OFF,
                ),
            )
            chunk.choices.firstOrNull()?.message?.toText() ?: "No response"
        }
    }
    private val teammateRunner: TeammateRunner by lazy {
        TeammateRunner(appScope) { agentName, prompt ->
            val s = settingsStore.settingsFlow.value
            val assistant = s.assistants.find { it.id == s.assistantId }
            val modelId = assistant?.subAgentModelId ?: assistant?.chatModelId ?: s.chatModelId
            val m = s.findModelById(modelId) ?: return@TeammateRunner "No model"
            val p = m.findProvider(s.providers) ?: return@TeammateRunner "No provider"
            @Suppress("UNCHECKED_CAST")
            val impl = providerManager.getProviderByType(p)
                as me.rerere.ai.provider.Provider<me.rerere.ai.provider.ProviderSetting>
            val tools = deduplicateTools(buildList {
                if (s.enableWebSearch) {
                    addAll(createSearchTools(s))
                    add(createWebFetchTool())
                }
                addAll(me.rerere.rikkahub.data.ai.tools.createFileTools(context.filesDir.absolutePath))
                addAll(me.rerere.rikkahub.data.ai.tools.createShellTools())
                addAll(localTools.getTools(listOf(me.rerere.rikkahub.data.ai.tools.LocalToolOption.TimeInfo)))
            })
            val messages = listOf(
                me.rerere.ai.ui.UIMessage.system("You are a teammate agent. Complete the assigned task and report the results concisely."),
                me.rerere.ai.ui.UIMessage.user(prompt),
            )
            val chunk = impl.generateText(
                providerSetting = p,
                messages = messages,
                params = me.rerere.ai.provider.TextGenerationParams(
                    model = m,
                    tools = tools,
                    reasoningLevel = me.rerere.ai.core.ReasoningLevel.OFF,
                ),
            )
            chunk.choices.firstOrNull()?.message?.toText() ?: "No response"
        }
    }
    private val autoCompactor: AutoCompactor by lazy {
        AutoCompactor().apply {
            setToolResultsDir(java.io.File(context.filesDir, "tool_results"))
            setTranscriptDir(java.io.File(context.filesDir, "transcripts"))
        }
    }
    private val sessionStore: SessionStore by lazy { SessionStore(context) }
    private val agentService: AgentService by lazy {
        AgentService(appScope, autoCompactor, sessionStore)
    }

    // s13: 后台 Agent 完成通知 + s14: CronScheduler 启动 + s04: UserPromptSubmit 发射
    init {
        // s14: 启动 cron 调度器
        val cronDataDir = java.io.File(context.filesDir, ".cron_jobs")
        cronDataDir.parentFile?.mkdirs()
        me.rerere.rikkahub.data.ai.scheduler.CronScheduler.setDurableFile(
            java.io.File(context.filesDir, ".cron_jobs/scheduled_tasks.json")
        )
        me.rerere.rikkahub.data.ai.scheduler.CronScheduler.start(appScope) { job ->
            Log.i(TAG, "[cron] job '${job.id}' fired: ${job.prompt.take(60)}")
        }

        // s15: MessageBus 持久化目录
        me.rerere.rikkahub.data.ai.team.MessageBus.setBaseDir(
            java.io.File(context.filesDir, "mailboxes")
        )

        // s17: KanbanBoard 持久化
        me.rerere.rikkahub.data.ai.team.KanbanBoard.setDurableFile(
            java.io.File(context.filesDir, ".kanban/kanban_tasks.json")
        )

        // s13: 后台 agent 完成通知
        me.rerere.rikkahub.data.ai.agent.addNotificationListener { notification ->
            Log.i(TAG, "[bg notification] ${notification.agentType} ${notification.status}: ${notification.summary?.take(80)}")
        }
    }

    // 错误状态
    private val _errors = MutableStateFlow<List<ChatError>>(emptyList())
    val errors: StateFlow<List<ChatError>> = _errors.asStateFlow()

    fun addError(
        error: Throwable,
        conversationId: Uuid? = null,
        title: String? = null,
        solution: ChatErrorSolution? = null,
    ) {
        if (error is CancellationException) return
        _errors.update {
            it + ChatError(title = title, error = error, conversationId = conversationId, solution = solution)
        }
    }

    fun dismissError(id: Uuid) {
        _errors.update { list -> list.filter { it.id != id } }
    }

    fun clearAllErrors() {
        _errors.value = emptyList()
    }

    // 生成完成流
    private val _generationDoneFlow = MutableSharedFlow<Uuid>()
    val generationDoneFlow: SharedFlow<Uuid> = _generationDoneFlow.asSharedFlow()

    // 前台状态管理
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                _isForeground.value = true
                stopGenerationForeground()
            }
            Lifecycle.Event.ON_STOP -> _isForeground.value = false
            else -> {}
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        AgentRegistry.registerBuiltin()
        // 从 Settings 加载用户自定义 Agent（非阻塞，等数据就绪后注册）
        appScope.launch {
            val settings = settingsStore.settingsFlowRaw.first()
            AgentLoader.reload(user = settings.agents)
        }
        // 提前初始化 AgentService（启动事件监听协程）
        agentService
    }

    fun cleanup() = runCatching {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        sessions.values.forEach { it.cleanup() }
        sessions.clear()
        me.rerere.rikkahub.data.ai.agent.AgentTaskTracker.clear()
        teammateRunner.killAll()
        me.rerere.rikkahub.data.ai.agent.AgentMailbox.clear()
    }

    // ---- Session 管理 ----

    private fun getOrCreateSession(conversationId: Uuid): ConversationSession {
        return sessions.computeIfAbsent(conversationId) { id ->
            val settings = settingsStore.settingsFlow.value
            ConversationSession(
                id = id,
                initial = Conversation.ofId(
                    id = id,
                    assistantId = settings.getCurrentAssistant().id
                ),
                scope = appScope,
                onIdle = { removeSession(it) }
            ).also {
                _sessionsVersion.value++
                Log.i(TAG, "createSession: $id (total: ${sessions.size + 1})")
            }
        }
    }

    private fun removeSession(conversationId: Uuid) {
        val session = sessions[conversationId] ?: return
        if (session.isInUse) {
            Log.d(TAG, "removeSession: skipped $conversationId (still in use)")
            return
        }
        if (sessions.remove(conversationId, session)) {
            session.cleanup()
            appScope.launch {
                val msgs = getConversationFlow(conversationId).value.currentMessages
                ListenerEventBus.emit(AgentEvent.SessionStopped(conversationId, msgs))
            }
            _sessionsVersion.value++
            Log.i(TAG, "removeSession: $conversationId (remaining: ${sessions.size})")
        }
    }

    // ---- 引用管理 ----

    fun addConversationReference(conversationId: Uuid) {
        getOrCreateSession(conversationId).acquire()
    }

    fun removeConversationReference(conversationId: Uuid) {
        sessions[conversationId]?.release()
    }

    private fun launchWithConversationReference(
        conversationId: Uuid,
        block: suspend () -> Unit
    ): Job = appScope.launch {
        addConversationReference(conversationId)
        try {
            block()
        } finally {
            removeConversationReference(conversationId)
        }
    }

    // ---- 对话状态访问 ----

    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> {
        return getOrCreateSession(conversationId).state
    }

    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> {
        val session = sessions[conversationId] ?: return flowOf(null)
        return session.generationJob
    }

    fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?> {
        val session = sessions[conversationId] ?: return MutableStateFlow(null)
        return session.processingStatus
    }

    fun getConversationJobs(): Flow<Map<Uuid, Job?>> {
        return _sessionsVersion.flatMapLatest {
            val currentSessions = sessions.values.toList()
            if (currentSessions.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(currentSessions.map { s ->
                    s.generationJob.map { job -> s.id to job }
                }) { pairs ->
                    pairs.filter { it.second != null }.toMap()
                }
            }
        }
    }

    // ---- 初始化对话 ----

    suspend fun initializeConversation(conversationId: Uuid) {
        getOrCreateSession(conversationId) // 确保 session 存在
        val conversation = conversationRepo.getConversationById(conversationId)
        if (conversation != null) {
            updateConversation(conversationId, conversation)
            settingsStore.updateAssistant(conversation.assistantId)
        } else {
            // 新建对话, 并添加预设消息
            val currentSettings = settingsStore.settingsFlowRaw.first()
            val assistant = currentSettings.getCurrentAssistant()
            val newConversation = Conversation.ofId(
                id = conversationId,
                assistantId = assistant.id,
                newConversation = true
            ).updateCurrentMessages(assistant.presetMessages)
            updateConversation(conversationId, newConversation)
        }
    }

    // ---- 发送消息 ----

    fun sendMessage(conversationId: Uuid, content: List<UIMessagePart>, answer: Boolean = true) {
        if (content.isEmptyInputMessage()) return

        val session = getOrCreateSession(conversationId)
        val previousJob = session.getJob()
        previousJob?.cancel()

        val job = appScope.launch {
            try {
                runCatching { previousJob?.join() }
                finishInterruptedPendingTools(conversationId)

                val currentConversation = session.state.value
                val settings = settingsStore.settingsFlow.first()
                val assistant = settings.getAssistantById(currentConversation.assistantId)
                    ?: settings.getCurrentAssistant()
                val processedContent = preprocessUserInputParts(content, assistant)

                // 添加消息到列表
                val newConversation = currentConversation.copy(
                    messageNodes = currentConversation.messageNodes + UIMessage(
                        role = MessageRole.USER,
                        parts = processedContent,
                    ).toMessageNode(),
                )
                saveConversation(conversationId, newConversation)

                // s04: 发射 UserPromptSubmit 事件
                appScope.launch {
                    ListenerEventBus.emit(
                        AgentEvent.UserPromptSubmit(
                            conversationId = conversationId,
                            userInput = content.joinToString("") { part ->
                                if (part is UIMessagePart.Text) part.text else ""
                            },
                        )
                    )
                }

                // 开始补全
                if (answer) {
                    handleMessageComplete(conversationId)
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                e.printStackTrace()
                addError(e, conversationId, title = context.getString(R.string.error_title_send_message))
            }
        }
        session.setJob(job)
    }

    private fun preprocessUserInputParts(parts: List<UIMessagePart>, assistant: Assistant): List<UIMessagePart> {
        return parts.map { part ->
            when (part) {
                is UIMessagePart.Text -> {
                    part.copy(
                        text = part.text.replaceRegexes(
                            assistant = assistant,
                            scope = AssistantAffectScope.USER,
                            visual = false
                        )
                    )
                }

                else -> part
            }
        }
    }

    // ---- 重新生成消息 ----

    fun regenerateAtMessage(
        conversationId: Uuid,
        message: UIMessage,
        regenerateAssistantMsg: Boolean = true
    ) {
        val session = getOrCreateSession(conversationId)
        session.getJob()?.cancel()

        val job = appScope.launch {
            try {
                val conversation = session.state.value

                if (message.role == MessageRole.USER) {
                    // 如果是用户消息，则截止到当前消息
                    val node = conversation.getMessageNodeByMessage(message)
                    val indexAt = conversation.messageNodes.indexOf(node)
                    val newConversation = conversation.copy(
                        messageNodes = conversation.messageNodes.subList(0, indexAt + 1)
                    )
                    saveConversation(conversationId, newConversation)
                    handleMessageComplete(conversationId)
                } else {
                    if (regenerateAssistantMsg) {
                        val node = conversation.getMessageNodeByMessage(message)
                        val nodeIndex = conversation.messageNodes.indexOf(node)
                        handleMessageComplete(conversationId, messageRange = 0..<nodeIndex)
                    } else {
                        saveConversation(conversationId, conversation)
                    }
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                addError(e, conversationId, title = context.getString(R.string.error_title_regenerate_message))
            }
        }

        session.setJob(job)
    }

    // ---- 处理工具调用审批 ----

    fun handleToolApproval(
        conversationId: Uuid,
        toolCallId: String,
        approved: Boolean,
        reason: String = "",
        answer: String? = null,
    ) {
        val session = getOrCreateSession(conversationId)
        session.getJob()?.cancel()

        val job = appScope.launch {
            try {
                val conversation = session.state.value
                val newApprovalState = when {
                    answer != null -> ToolApprovalState.Answered(answer)
                    approved -> ToolApprovalState.Approved
                    else -> ToolApprovalState.Denied(reason)
                }

                // Update the tool approval state
                val updatedNodes = conversation.messageNodes.map { node ->
                    node.copy(
                        messages = node.messages.map { msg ->
                            msg.copy(
                                parts = msg.parts.map { part ->
                                    when {
                                        part is UIMessagePart.Tool && part.toolCallId == toolCallId -> {
                                            part.copy(approvalState = newApprovalState)
                                        }

                                        else -> part
                                    }
                                }
                            )
                        }
                    )
                }
                val updatedConversation = conversation.copy(messageNodes = updatedNodes)
                saveConversation(conversationId, updatedConversation)

                // Check if there are still pending tools
                val hasPendingTools = updatedNodes.any { node ->
                    node.currentMessage.parts.any { part ->
                        part is UIMessagePart.Tool && part.isPending
                    }
                }

                // Only continue generation when all pending tools are handled
                if (!hasPendingTools) {
                    handleMessageComplete(conversationId)
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                addError(e, conversationId, title = context.getString(R.string.error_title_tool_approval))
            }
        }

        session.setJob(job)
    }

    // ---- 处理消息补全 ----

    private suspend fun handleMessageComplete(
        conversationId: Uuid,
        messageRange: ClosedRange<Int>? = null
    ) {
        val settings = settingsStore.settingsFlow.first()
        val initialConversation = getConversationFlow(conversationId).value
        val assistant = settings.getAssistantById(initialConversation.assistantId)
            ?: settings.getCurrentAssistant()
        val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId) ?: return

        // Fire-and-forget: 通知 AgentService 生成开始
        coroutineScope {
            launch {
                val msgs = getConversationFlow(conversationId).value.currentMessages
                ListenerEventBus.emit(AgentEvent.GenerationStarted(conversationId, msgs))
            }
        }

        val senderName = if (assistant.useAssistantAvatar) {
            assistant.name.ifEmpty { context.getString(R.string.assistant_page_default_assistant) }
        } else {
            model.displayName
        }

        runCatching {

            // reset suggestions
            updateConversation(conversationId, initialConversation.copy(chatSuggestions = emptyList()))

            // memory tool
            if (!model.abilities.contains(ModelAbility.TOOL)) {
                if (settings.enableWebSearch || mcpManager.getAllAvailableTools().isNotEmpty()) {
                    addError(
                        IllegalStateException(context.getString(R.string.tools_warning)),
                        conversationId,
                        title = context.getString(R.string.error_title_tool_unavailable)
                    )
                }
            }

            // check invalid messages
            checkInvalidMessages(conversationId)
            val conversation = getConversationFlow(conversationId).value

            // start generating
            val session = getOrCreateSession(conversationId)

            // 如果不在前台，提前启动前台 Service（不等第一块数据）
            if (!isForeground.value && settings.displaySetting.enableNotificationOnMessageGeneration) {
                startGenerationForeground(senderName, conversationId.toString())
            }

            // 监听前后台切换：一切后台立即启动 FG Service 保活
            val fgJob: Job? = if (settings.displaySetting.enableNotificationOnMessageGeneration) {
                appScope.launch {
                    isForeground.drop(1).collect { foreground ->
                        if (!foreground) {
                            startGenerationForeground(senderName, conversationId.toString())
                        } else {
                            stopGenerationForeground()
                        }
                    }
                }
            } else null

            agentPipeline.run(
                settings = settings,
                model = model,
                processingStatus = session.processingStatus,
                messages = conversation.currentMessages.let {
                    if (messageRange != null) {
                        it.subList(messageRange.start, messageRange.endInclusive + 1)
                    } else {
                        it
                    }
                },
                assistant = assistant,
                maxSteps = assistant.totalStepsLimit,
                conversationSystemPrompt = conversation.customSystemPrompt,
                conversationModeInjectionIds = conversation.modeInjectionIds,
                conversationLorebookIds = conversation.lorebookIds,
                memories = if (assistant.useGlobalMemory) {
                    memoryRepository.getGlobalMemories()
                } else {
                    memoryRepository.getMemoriesOfAssistant(assistant.id.toString())
                },
                inputTransformers = buildList {
                    addAll(inputTransformers)
                    add(templateTransformer)
                    add(knowledgeBaseTransformer)
                },
                outputTransformers = outputTransformers,
                policyEngine = PolicyEngine(currentMode = me.rerere.rikkahub.data.ai.tools.PlanModeState.effectiveMode, baseDir = context.filesDir.absolutePath),
                autoCompactor = if (assistant.enableAutoCompact) autoCompactor else null,
                tools = deduplicateTools(buildList {
                val skillDirs = assistant.enabledSkills.mapNotNull { skillManager.getSkillDir(it)?.absolutePath }
                    if (assistant.localTools.contains(LocalToolOption.FileTools)) {
                        addAll(createFileTools(context.filesDir.absolutePath, skillDirs))
                    }
                    if (assistant.localTools.contains(LocalToolOption.AssetGenerator)) {
                        add(createAssetTool(context.filesDir.absolutePath))
                    }
                    if (assistant.localTools.contains(LocalToolOption.DataProcess)) {
                        add(createDataProcessTool())
                    }
                    if (settings.enableWebSearch) {
                        addAll(createSearchTools(settings))
                        add(createWebFetchTool())
                    }
                    addAll(localTools.getTools(assistant.localTools))
                    if (assistant.localTools.contains(LocalToolOption.ShellTools)) {
                        addAll(createShellTools(assistant.shellTimeout))
                    }
                    if (assistant.localTools.contains(LocalToolOption.PythonEngine)) {
                        add(createPythonTool(context, assistant.toolExecTimeout))
                    }
                    if (assistant.localTools.contains(LocalToolOption.GitHubTools)) {
                        add(createGitHubTool(settingsStore, assistant.enableCiTimeout, assistant.enableAutoFixCi))
                    }
                    if (assistant.localTools.contains(LocalToolOption.ConvertFile)) {
                        add(createConvertFileTool(context))
                    }
                    if (assistant.localTools.contains(LocalToolOption.DatabaseQuery)) {
                        add(createDatabaseQueryTool(database))
                    }
                    if (assistant.localTools.contains(LocalToolOption.Calculator)) {
                        add(createCalculatorTool(context))
                    }
                    if (assistant.enabledSkills.isNotEmpty()) {
                        addAll(
                            createSkillTools(
                                enabledSkills = assistant.enabledSkills,
                                allSkills = skillManager.listSkills(),
                                skillManager = skillManager,
                            )
                        )
                    }
                    mcpManager.getAllAvailableTools().forEach { (serverId, tool) ->
                        add(
                            Tool(
                                name = "mcp__" + tool.name,
                                description = tool.description ?: "",
                                parameters = { tool.inputSchema },
                                needsApproval = tool.needsApproval,
                                execute = {
                                    mcpManager.callTool(serverId, tool.name, it.jsonObject)
                                },
                            )
                        )
                    }
                    if (assistant.mcpServers.isNotEmpty()) addAll(createMcpResourceTools(mcpManager))
                    if (assistant.localTools.contains(LocalToolOption.TaskTools)) addAll(createTaskTools())
                    if (assistant.localTools.contains(LocalToolOption.PlanMode)) addAll(createPlanModeTools())
                    if (assistant.localTools.contains(LocalToolOption.WorkerTools)) addAll(createWorkerTools(workerManager))
                    if (assistant.localTools.contains(LocalToolOption.TeammateTools)) addAll(createTeammateTools(teammateRunner))
                    if (assistant.localTools.contains(LocalToolOption.SendMessage)) {
                        add(createSendMessageTool())
                        add(createGetTeammateMessagesTool())
                        // s17: 看板工具 — 多 Agent 认领任务
                        addAll(createKanbanTools())
                    }
                    if (assistant.enableSubAgent) {
                        val allAgentTypes = AgentRegistry.list()
                        add(
                            Tool(
                                name = "sub_agent",
                                description = buildString {
                                    appendLine("Launch a specialized agent to handle multi-step tasks autonomously.")
                                    appendLine()
                                    appendLine("Available agent types and the tools they have access to:")
                                    allAgentTypes.forEach { agent ->
                                        appendLine("- ${agent.agentType}: ${agent.description} (Tools: ${formatAgentTools(agent)})")
                                    }
                                    appendLine()
                                    appendLine("When to use sub_agent:")
                                    appendLine("- For researching complex questions that require exploring many files")
                                    appendLine("- For code review and getting a second opinion")
                                    appendLine("- For running verification and tests")
                                    appendLine("- For independent sub-tasks that can run in parallel")
                                    appendLine("- Launch multiple agents concurrently by sending a single message with multiple sub_agent tool calls")
                                    appendLine()
                                    appendLine("When NOT to use sub_agent:")
                                    appendLine("- If you can do it directly with your own tools (read, search, write)")
                                    appendLine("- If you need to read a specific file, use file_read instead")
                                    appendLine("- The agent result is returned to you — you must relay it to the user")
                                    appendLine()
                                    appendLine("Usage notes:")
                                    appendLine("- Always include a short description (3-5 words) summarizing what the agent will do")
                                    appendLine("- When the agent is done, it returns a message back to you — relay it to the user")
                                    appendLine("- For background agents: use run_in_background=true, continue working, you'll be notified on completion")
                                    appendLine("- Use foreground (default) when you need the agent's results before proceeding")
                                    appendLine("- Each agent starts fresh — provide a complete task description")
                                    appendLine("- Clearly tell the agent whether you expect it to write code or just do research")
                                    appendLine("- The agent's outputs should generally be trusted")
                                    appendLine("- To continue a previously spawned agent, use send_message with its name as the \"to\" field")
                                    appendLine()
                                    appendLine("Example:")
                                    appendLine("  sub_agent({")
                                    appendLine("    description: \"Review migration safety\",")
                                    appendLine("    subagent_type: \"verification\",")
                                    appendLine("    prompt: \"Review migration 0042_user_schema.sql for safety...\"")
                                    appendLine("  })")
                                },
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
                                                put("description", buildString {
                                                    append("Agent role to use. Available: ")
                                                    append(AgentRegistry.list().joinToString(", ") { it.agentType })
                                                    append(". Default: general-purpose.")
                                                })
                                            })
                                            put("model", buildJsonObject {
                                                put("type", "string")
                                                put("description", "Optional model override (e.g. model UUID). Uses default if omitted.")
                                            })
                                            put("run_in_background", buildJsonObject {
                                                put("type", "boolean")
                                                put("description", "Set to true to run this agent in the background. You will be notified when it completes.")
                                            })
                                            put("name", buildJsonObject {
                                                put("type", "string")
                                                put("description", "Optional name for the agent for display.")
                                            })
                                        },
                                        required = listOf("goal"),
                                    )
                                },
                                execute = {
                                    val obj = it.jsonObject
                                    val goal = obj["goal"]?.jsonPrimitive?.content
                                        ?: error("goal is required")
                                    val toolContext = obj["context"]?.jsonPrimitive?.contentOrNull ?: ""
                                    val agentType = obj["subagent_type"]?.jsonPrimitive?.contentOrNull ?: "general-purpose"
                                    val modelOverride = obj["model"]?.jsonPrimitive?.contentOrNull
                                    val runInBackground = obj["run_in_background"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                                    val agentName = obj["name"]?.jsonPrimitive?.contentOrNull

                                    // Load agent definition
                                    val agentDef = AgentRegistry.get(agentType)
                                    val agentCallId = agentName ?: "agent_${System.currentTimeMillis()}"

                                    // Resolve model for sub-agent
                                    val effectiveModelId = try {
                                        modelOverride?.let { Uuid.parse(it) }
                                    } catch (_: Exception) { null }
                                        ?: agentDef?.modelId?.let { Uuid.parse(it) }
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

                                    // Build sub-agent tools — 全工具池（对齐主Agent），按 agent 过滤
                                    val skillDirs = assistant.enabledSkills
                                        .mapNotNull { skillManager.getSkillDir(it)?.absolutePath }
                                    val allTools = deduplicateTools(buildList {
                                        if (settings.enableWebSearch) {
                                            addAll(createSearchTools(settings))
                                            add(createWebFetchTool())
                                        }
                                        if (assistant.localTools.contains(LocalToolOption.FileTools)) {
                                            addAll(createFileTools(context.filesDir.absolutePath, skillDirs))
                                        }
                                        if (assistant.localTools.contains(LocalToolOption.AssetGenerator)) {
                                            add(createAssetTool(context.filesDir.absolutePath))
                                        }
                                        if (assistant.localTools.contains(LocalToolOption.DataProcess)) {
                                            add(createDataProcessTool())
                                        }
                                        if (assistant.localTools.contains(LocalToolOption.PythonEngine)) {
                                            add(createPythonTool(context, assistant.toolExecTimeout))
                                        }
                                        addAll(localTools.getTools(assistant.localTools))
                                        if (assistant.localTools.contains(LocalToolOption.ShellTools)) {
                                            addAll(createShellTools(assistant.shellTimeout))
                                        }
                                        if (assistant.localTools.contains(LocalToolOption.GitHubTools)) {
                                            add(createGitHubTool(settingsStore, assistant.enableCiTimeout, assistant.enableAutoFixCi))
                                        }
                                        if (assistant.localTools.contains(LocalToolOption.ConvertFile)) {
                                            add(createConvertFileTool(context))
                                        }
                                        if (assistant.localTools.contains(LocalToolOption.DatabaseQuery)) {
                                            add(createDatabaseQueryTool(database))
                                        }
                                        if (assistant.localTools.contains(LocalToolOption.Calculator)) {
                                            add(createCalculatorTool(context))
                                        }
                                        if (assistant.enabledSkills.isNotEmpty()) {
                                            addAll(createSkillTools(
                                                enabledSkills = assistant.enabledSkills,
                                                allSkills = skillManager.listSkills(),
                                                skillManager = skillManager,
                                            ))
                                        }
                                        mcpManager.getAllAvailableTools().forEach { (serverId, tool) ->
                                            add(Tool(
                                                name = "mcp__" + tool.name,
                                                description = tool.description ?: "",
                                                parameters = { tool.inputSchema },
                                                needsApproval = tool.needsApproval,
                                                execute = { mcpManager.callTool(serverId, tool.name, it.jsonObject) },
                                            ))
                                        }
                                        if (assistant.mcpServers.isNotEmpty()) addAll(createMcpResourceTools(mcpManager))
                                        if (assistant.localTools.contains(LocalToolOption.TaskTools)) addAll(createTaskTools())
                                        if (assistant.localTools.contains(LocalToolOption.PlanMode)) addAll(createPlanModeTools())
                                        if (assistant.localTools.contains(LocalToolOption.WorkerTools)) addAll(createWorkerTools(workerManager))
                                        if (assistant.localTools.contains(LocalToolOption.TeammateTools)) addAll(createTeammateTools(teammateRunner))
                                        if (assistant.localTools.contains(LocalToolOption.SendMessage)) {
                                            add(createSendMessageTool())
                                            add(createGetTeammateMessagesTool())
                                            addAll(createKanbanTools())
                                        }
                                        add(createSleepTool())
                                    })

                                    // 过滤规则：
                                    // 1. Agent 通用禁用（sub_agent防递归）
                                    // 2. Agent 自身 disallowedTools
                                    // 3. 后台模式 → 只允许 ASYNC_AGENT_ALLOWED_TOOLS
                                    val subTools = allTools.filter { tool ->
                                        if (tool.name == "sub_agent") return@filter false
                                        if (agentDef != null && !isToolAllowed(agentDef, tool.name)) return@filter false
                                        if (runInBackground && !isToolAllowedForAsync(tool.name)) return@filter false
                                        true
                                    }

                                    // Resolve system prompt
                                    val resolvedSysPrompt = agentDef?.let { def ->
                                        when (val sp = def.systemPrompt) {
                                            is AgentSystemPrompt.Static -> sp.text
                                            is AgentSystemPrompt.Dynamic -> sp.generator(def.agentType, def)
                                        }
                                    } ?: ""

                                    // Build prompt with agent system prompt and memory
                                    val prompt = buildString {
                                        if (resolvedSysPrompt.isNotBlank()) {
                                            appendLine(resolvedSysPrompt)
                                            appendLine()
                                        }
                                        // Agent initialPrompt: 每次执行附加的首条消息
                                        agentDef?.initialPrompt?.let {
                                            if (it.isNotBlank()) {
                                                appendLine(it)
                                                appendLine()
                                            }
                                        }
                                        // Load agent memory via AgentMemoryManager
                                        val memoryPrompt = agentDef?.let { def ->
                                            kotlinx.coroutines.runBlocking {
                                                AgentMemoryManager(memoryRepository).loadMemoryPrompt(def)
                                            }
                                        } ?: ""
                                        if (memoryPrompt.isNotBlank()) {
                                            appendLine(memoryPrompt)
                                            appendLine()
                                        }
                                        appendLine("Goal: $goal")
                                        // omitProjectContext: 跳过项目上下文
                                        if (!(agentDef?.omitProjectContext == true)) {
                                            if (toolContext.isNotBlank()) {
                                                appendLine()
                                                appendLine("Context: $toolContext")
                                            }
                                        }
                                        // criticalReminder: 每轮注入的关键提醒（也通过 executeSubAgentLoop 每轮注入）
                                        agentDef?.criticalReminder?.let {
                                            if (it.isNotBlank()) {
                                                appendLine()
                                                appendLine("=== CRITICAL REMINDER ===")
                                                appendLine(it)
                                            }
                                        }
                                        // permissionMode: 权限模式提示
                                        agentDef?.permissionMode?.let {
                                            if (it.isNotBlank()) {
                                                appendLine()
                                                appendLine("Permission mode: $it")
                                            }
                                        }
                                        appendLine()
                                        appendLine("You have access to tools. Use them when needed.")
                                        appendLine("After using tools, continue working until the goal is complete.")
                                        appendLine("When done, summarize what was accomplished.")
                                    }

                                    if (runInBackground) {
                                        // 后台执行
                                        appScope.launch {
                                            runCatching {
                                                AgentRunner.run(
                                                    agentDef = agentDef,
                                                    agentCallId = agentCallId,
                                                    prompt = prompt,
                                                    subTools = subTools,
                                                    runInBackground = true,
                                                    agentType = agentType,
                                                    description = goal.take(50),
                                                ) {
                                                    executeSubAgentLoop(conversationId, subModel, providerSetting, providerImpl, subTools, assistant, prompt, agentCallId, agentDef)
                                                }
                                            }.onFailure { e ->
                                                Log.w("SubAgent", "Background agent failed: ${e.message}")
                                            }
                                        }
                                        listOf(UIMessagePart.Text("{\"status\":\"running\",\"agentId\":\"$agentCallId\",\"message\":\"Agent '$agentType' started in background\"}"))
                                    } else {
                                        // 前台执行
                                        val laneTracker = LaneTracker()
                                        laneTracker.started()
                                        try {
                                            val outputText = AgentRunner.run(
                                                agentDef = agentDef,
                                                agentCallId = agentCallId,
                                                prompt = prompt,
                                                subTools = subTools,
                                                runInBackground = false,
                                                agentType = agentType,
                                                description = goal.take(50),
                                            ) {
                                                executeSubAgentLoop(conversationId, subModel, providerSetting, providerImpl, subTools, assistant, prompt, agentCallId, agentDef)
                                            }
                                            laneTracker.completed()
                                            outputText
                                        } catch (e: Exception) {
                                            laneTracker.failed(e.message ?: e.javaClass.simpleName)
                                            throw e
                                        }
                                    }
                                },
                            )
                        )
                    }
                    // create_agent: AI 自主创建 agent 并持久化
                    add(
                        Tool(
                            name = "create_agent",
                            description = "Create a new agent type and persist it. The agent will appear in the agent list immediately and survive app restart.",
                            needsApproval = false,
                            parameters = {
                                InputSchema.Obj(
                                    properties = buildJsonObject {
                                        put("agent_type", buildJsonObject {
                                            put("type", "string")
                                            put("description", "Unique identifier for the agent (e.g. code-reviewer). Use lowercase letters, numbers, and hyphens.")
                                        })
                                        put("name", buildJsonObject {
                                            put("type", "string")
                                            put("description", "Display name for the agent.")
                                        })
                                        put("description", buildJsonObject {
                                            put("type", "string")
                                            put("description", "Description of when to use this agent. Will be shown in the agent list.")
                                        })
                                        put("prompt", buildJsonObject {
                                            put("type", "string")
                                            put("description", "System prompt. Defines the agent's role, behavior, and output format.")
                                        })
                                        put("tools", buildJsonObject {
                                            put("type", "string")
                                            put("description", "Comma-separated tool allowlist. Default: all tools. Example: file_read, file_search, web_search")
                                        })
                                        put("disallowed_tools", buildJsonObject {
                                            put("type", "string")
                                            put("description", "Comma-separated tools to forbid. Example: file_write, execute_command")
                                        })
                                        put("color", buildJsonObject {
                                            put("type", "string")
                                            put("description", "Agent color: red, blue, green, yellow, purple, orange, pink, cyan. Default: blue")
                                        })
                                        put("model", buildJsonObject {
                                            put("type", "string")
                                            put("description", "Model override. Default: inherit from parent.")
                                        })
                                        put("background", buildJsonObject {
                                            put("type", "boolean")
                                            put("description", "Run in background. Default: false")
                                        })
                                        put("memory", buildJsonObject {
                                            put("type", "string")
                                            put("description", "Memory scope: user, project, local. Default: none")
                                        })
                                        put("max_turns", buildJsonObject {
                                            put("type", "integer")
                                            put("description", "Maximum turns before stopping. Default: no limit")
                                        })
                                        put("skills", buildJsonObject {
                                            put("type", "string")
                                            put("description", "Comma-separated skill names to preload.")
                                        })
                                        put("initial_prompt", buildJsonObject {
                                            put("type", "string")
                                            put("description", "Text prepended to the first user message each run.")
                                        })
                                        put("critical_reminder", buildJsonObject {
                                            put("type", "string")
                                            put("description", "Critical reminder injected every turn.")
                                        })
                                        put("effort", buildJsonObject {
                                            put("type", "integer")
                                            put("description", "AI effort level. Higher values = more thorough. Default: none")
                                        })
                                        put("permission_mode", buildJsonObject {
                                            put("type", "string")
                                            put("description", "Permission mode: plan (needs approval), acceptEdits (auto allow), bubble (bubble to parent). Default: none")
                                        })
                                        put("omit_project_context", buildJsonObject {
                                            put("type", "boolean")
                                            put("description", "Skip project context (AGENTS.md, etc.). Default: false")
                                        })
                                    },
                                    required = listOf("agent_type", "name", "description", "prompt"),
                                )
                            },
                            execute = { args ->
                                val obj = args.jsonObject
                                val agentType = obj["agent_type"]?.jsonPrimitive?.content
                                    ?: error("agent_type is required")
                                val name = obj["name"]?.jsonPrimitive?.content
                                    ?: error("name is required")
                                val description = obj["description"]?.jsonPrimitive?.content
                                    ?: error("description is required")
                                val prompt = obj["prompt"]?.jsonPrimitive?.content
                                    ?: error("prompt is required")

                                val tools = obj["tools"]?.jsonPrimitive?.content?.let {
                                    it.split(",").map { s -> s.trim() }.filter { s -> s.isNotBlank() }
                                }
                                val disallowedTools = obj["disallowed_tools"]?.jsonPrimitive?.content?.let {
                                    it.split(",").map { s -> s.trim() }.filter { s -> s.isNotBlank() }
                                }
                                val color = obj["color"]?.jsonPrimitive?.content?.let {
                                    try { AgentColor.valueOf(it.uppercase()) } catch (_: Exception) { null }
                                } ?: AgentColor.BLUE
                                val modelId = obj["model"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                                val background = obj["background"]?.jsonPrimitive?.let {
                                    it.content.toBooleanStrictOrNull()
                                } ?: false
                                val memory = obj["memory"]?.jsonPrimitive?.content?.let {
                                    try { AgentMemoryScope.valueOf(it.uppercase()) } catch (_: Exception) { null }
                                }
                                val maxTurns = obj["max_turns"]?.jsonPrimitive?.content?.toIntOrNull()
                                val skills = obj["skills"]?.jsonPrimitive?.content?.let {
                                    it.split(",").map { s -> s.trim() }.filter { s -> s.isNotBlank() }
                                }
                                val initialPrompt = obj["initial_prompt"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                                val criticalReminder = obj["critical_reminder"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                                val effort = obj["effort"]?.jsonPrimitive?.content?.toIntOrNull()
                                val permissionMode = obj["permission_mode"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                                val omitProjectContext = obj["omit_project_context"]?.jsonPrimitive?.let {
                                    it.content.toBooleanStrictOrNull()
                                } ?: false

                                val agentDef = AgentDefinition(
                                    agentType = agentType,
                                    name = name,
                                    description = description,
                                    systemPrompt = AgentSystemPrompt.Static(prompt),
                                    tools = tools ?: listOf("*"),
                                    disallowedTools = disallowedTools ?: emptyList(),
                                    color = color,
                                    modelId = modelId,
                                    background = background,
                                    memory = memory,
                                    maxTurns = maxTurns,
                                    effort = effort,
                                    permissionMode = permissionMode,
                                    omitProjectContext = omitProjectContext,
                                    skills = skills ?: emptyList(),
                                    initialPrompt = initialPrompt,
                                    criticalReminder = criticalReminder,
                                    source = AgentSource.USER,
                                    isBuiltin = false,
                                )

                                AgentRegistry.register(agentDef)
                                val savedAgents = AgentRegistry.listBySource(AgentSource.USER)
                                kotlinx.coroutines.runBlocking {
                                    settingsStore.update { s -> s.copy(agents = savedAgents) }
                                }

                                listOf(UIMessagePart.Text("Agent '$agentType' created successfully and persisted."))
                            },
                        )
                    )
                }),
            ).onCompletion {
                // 取消 Live Update 通知 + 前台服务
                fgJob?.cancel()
                cancelLiveUpdateNotification(conversationId)
                stopGenerationForeground()

                // 可能被取消了，或者意外结束，兜底更新
                val updatedConversation = getConversationFlow(conversationId).value.copy(
                    messageNodes = getConversationFlow(conversationId).value.messageNodes.map { node ->
                        node.copy(messages = node.messages.map { it.finishReasoning() })
                    },
                    updateAt = Instant.now()
                )
                updateConversation(conversationId, updatedConversation)

                // Show notification if app is not in foreground
                if (!isForeground.value && settings.displaySetting.enableNotificationOnMessageGeneration) {
                    sendGenerationDoneNotification(conversationId, senderName)
                }
            }.collect { chunk ->
                when (chunk) {
                    is GenerationChunk.Messages -> {
                        val updatedConversation = getConversationFlow(conversationId).value
                            .updateCurrentMessages(chunk.messages)
                        updateConversation(conversationId, updatedConversation)

                        // 前台时停止前台 Service（用户切回来了）
                        if (isForeground.value) {
                            stopGenerationForeground()
                        }

                        // Fire-and-forget: 通知 AgentService 轮次完成（触发 AutoCompactor）
                        kotlinx.coroutines.coroutineScope {
                            launch { ListenerEventBus.emit(AgentEvent.GenerationRoundComplete(conversationId, chunk.messages)) }
                        }

                        // 如果应用不在前台，发送 Live Update 通知
                        if (!isForeground.value && settings.displaySetting.enableNotificationOnMessageGeneration && settings.displaySetting.enableLiveUpdateNotification) {
                            sendLiveUpdateNotification(conversationId, chunk.messages, senderName)
                        }
                    }
                }
            }
        }.onFailure {
            // 取消 Live Update 通知 + 前台服务
            cancelLiveUpdateNotification(conversationId)
            stopGenerationForeground()

            it.printStackTrace()
            addError(it, conversationId, title = context.getString(R.string.error_title_generation))
            Logging.log(TAG, "handleMessageComplete: $it")
            Logging.log(TAG, it.stackTraceToString())
        }.onSuccess {
            val finalConversation = getConversationFlow(conversationId).value
            // 异步保存，不阻塞 loading 状态更新
            appScope.launch {
                saveConversation(conversationId, finalConversation)
            }

            launchWithConversationReference(conversationId) {
                generateTitle(conversationId, finalConversation)
            }
            launchWithConversationReference(conversationId) {
                generateSuggestion(conversationId, finalConversation)
            }

            // Fire-and-forget: 通知 AgentService 生成完成
            kotlinx.coroutines.coroutineScope {
                launch { ListenerEventBus.emit(AgentEvent.GenerationCompleted(conversationId, finalConversation.currentMessages)) }
            }
        }
    }

    // ---- 检查无效消息 ----

    private fun checkInvalidMessages(conversationId: Uuid) {
        val conversation = getConversationFlow(conversationId).value
        var messagesNodes = conversation.messageNodes

        // 移除无效 tool (未执行的 Tool)
        messagesNodes = messagesNodes.mapIndexed { _, node ->
            // Check for Tool type with non-executed tools
            val hasPendingTools = node.currentMessage.getTools().any { !it.isExecuted }

            if (hasPendingTools) {
                // Keep messages that are ready to resume, such as approved/denied/answered tools.
                val hasResumableTool = node.currentMessage.getTools().any {
                    !it.isExecuted && it.approvalState.canResumeToolExecution()
                }
                if (hasResumableTool) {
                    return@mapIndexed node
                }

                // If all tools are executed, it's valid
                val allToolsExecuted = node.currentMessage.getTools().all { it.isExecuted }
                if (allToolsExecuted && node.currentMessage.getTools().isNotEmpty()) {
                    return@mapIndexed node
                }

                // Remove messages that still have unresolved tool approvals.
                return@mapIndexed node.copy(
                    messages = node.messages.filter { it.id != node.currentMessage.id },
                    selectIndex = node.selectIndex - 1
                )
            }
            node
        }

        // 更新index
        messagesNodes = messagesNodes.map { node ->
            if (node.messages.isNotEmpty() && node.selectIndex !in node.messages.indices) {
                node.copy(selectIndex = 0)
            } else {
                node
            }
        }

        // 移除无效消息
        messagesNodes = messagesNodes.filter { it.messages.isNotEmpty() }

        updateConversation(conversationId, conversation.copy(messageNodes = messagesNodes))
    }

    private fun cancelToolByUser(tool: UIMessagePart.Tool): UIMessagePart.Tool {
        return tool.copy(
            output = listOf(
                UIMessagePart.Text(
                    """{"status":"cancelled","error":"Generation cancelled by user before tool execution completed."}"""
                )
            ),
            approvalState = ToolApprovalState.Denied("Generation cancelled by user")
        )
    }

    private suspend fun finishInterruptedPendingTools(conversationId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val lastNode = currentConversation.messageNodes.lastOrNull() ?: return
        val lastMessage = lastNode.currentMessage
        val updatedMessage = lastMessage.finishPendingTools(::cancelToolByUser)
        if (updatedMessage == lastMessage) {
            return
        }

        val updatedConversation = currentConversation.copy(
            messageNodes = currentConversation.messageNodes.dropLast(1) + lastNode.copy(
                messages = lastNode.messages.map { message ->
                    if (message.id == lastMessage.id) updatedMessage else message
                }
            )
        )
        saveConversation(conversationId, updatedConversation)
    }

    // ---- 生成标题 ----

    suspend fun generateTitle(
        conversationId: Uuid,
        conversation: Conversation,
        force: Boolean = false
    ) {
        val shouldGenerate = when {
            force -> true
            conversation.title.isBlank() -> true
            else -> false
        }
        if (!shouldGenerate) return

        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model = settings.findModelById(settings.titleModelId) ?: return
            val provider = model.findProvider(settings.providers) ?: return

            val providerHandler = providerManager.getProviderByType(provider)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        prompt = settings.titlePrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(4).joinToString("\n\n") { it.summaryAsText() })
                    ),
                ),
                params = TextGenerationParams(
                    model = model,
                    reasoningLevel = ReasoningLevel.OFF,
                ),
            )

            // 生成完，conversation可能不是最新了，因此需要重新获取
            conversationRepo.getConversationById(conversation.id)?.let {
                saveConversation(
                    conversationId,
                    it.copy(title = result.choices[0].message?.toText()?.trim() ?: "")
                )
            }
        }.onFailure {
            it.printStackTrace()
            addError(
                error = it,
                conversationId = conversationId,
                title = context.getString(R.string.error_title_generate_title),
                solution = ChatErrorSolution.CheckTitleModelSettings,
            )
        }
    }

    // ---- 生成建议 ----

    suspend fun generateSuggestion(conversationId: Uuid, conversation: Conversation) {
        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model = settings.findModelById(settings.suggestionModelId) ?: return
            val provider = model.findProvider(settings.providers) ?: return

            sessions[conversationId]?.let { session ->
                updateConversation(
                    conversationId,
                    session.state.value.copy(chatSuggestions = emptyList())
                )
            }

            val providerHandler = providerManager.getProviderByType(provider)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        settings.suggestionPrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(8).joinToString("\n\n") { it.summaryAsText() }),
                    )
                ),
                params = TextGenerationParams(
                    model = model,
                    reasoningLevel = ReasoningLevel.OFF,
                ),
            )
            val suggestions =
                result.choices[0].message?.toText()?.split("\n")?.map { it.trim() }
                    ?.filter { it.isNotBlank() } ?: emptyList()

            val latestConversation = conversationRepo.getConversationById(conversationId)
                ?: sessions[conversationId]?.state?.value
                ?: conversation
            saveConversation(
                conversationId,
                latestConversation.copy(
                    chatSuggestions = suggestions.take(
                        10
                    )
                )
            )
        }.onFailure {
            it.printStackTrace()
        }
    }

    /**
     * 为指定 Assistant 生成回复（群聊用），支持流式回调
     */
    suspend fun generateForAssistant(
        assistant: Assistant,
        settings: Settings,
        prompt: String,
        history: List<UIMessage>,
        onChunk: ((String, List<UIMessagePart>?) -> Unit)? = null,
    ): String {
        val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId)
            ?: error("No model configured for assistant '${assistant.name}'")

        val messages = history + UIMessage.user(prompt)
        var result = ""

        val skillDirs = assistant.enabledSkills.mapNotNull { skillManager.getSkillDir(it)?.absolutePath }

        agentPipeline.run(
            settings = settings,
            model = model,
            messages = messages,
            assistant = assistant,
            memories = if (assistant.useGlobalMemory) {
                memoryRepository.getGlobalMemories()
            } else {
                memoryRepository.getMemoriesOfAssistant(assistant.id.toString())
            },
            policyEngine = PolicyEngine(currentMode = PlanModeState.effectiveMode, baseDir = context.filesDir.absolutePath),
            tools = deduplicateTools(buildList {
                if (assistant.localTools.contains(LocalToolOption.FileTools)) {
                    addAll(createFileTools(context.filesDir.absolutePath, skillDirs))
                }
                if (assistant.localTools.contains(LocalToolOption.AssetGenerator)) {
                    add(createAssetTool(context.filesDir.absolutePath))
                }
                if (assistant.localTools.contains(LocalToolOption.DataProcess)) {
                    add(createDataProcessTool())
                }
                if (assistant.localTools.contains(LocalToolOption.PythonEngine)) {
                    add(createPythonTool(context, assistant.toolExecTimeout))
                }
                if (settings.enableWebSearch) {
                    addAll(createSearchTools(settings))
                }
                addAll(localTools.getTools(assistant.localTools))
                if (assistant.localTools.contains(LocalToolOption.ShellTools)) {
                    addAll(createShellTools(assistant.shellTimeout))
                }
                if (assistant.localTools.contains(LocalToolOption.GitHubTools)) {
                    add(createGitHubTool(settingsStore, assistant.enableCiTimeout, assistant.enableAutoFixCi))
                }
                if (assistant.localTools.contains(LocalToolOption.ConvertFile)) {
                    add(createConvertFileTool(context))
                }
                if (assistant.localTools.contains(LocalToolOption.DatabaseQuery)) {
                    add(createDatabaseQueryTool(database))
                }
                if (assistant.enabledSkills.isNotEmpty()) {
                    addAll(
                        createSkillTools(
                            enabledSkills = assistant.enabledSkills,
                            allSkills = skillManager.listSkills(),
                            skillManager = skillManager,
                        )
                    )
                }
                mcpManager.getAllAvailableTools().forEach { (serverId, tool) ->
                    add(
                        Tool(
                            name = "mcp__" + tool.name,
                            description = tool.description ?: "",
                            parameters = { tool.inputSchema },
                            needsApproval = tool.needsApproval,
                            execute = {
                                mcpManager.callTool(serverId, tool.name, it.jsonObject)
                            },
                        )
                    )
                }
                if (assistant.mcpServers.isNotEmpty()) addAll(createMcpResourceTools(mcpManager))
                if (assistant.localTools.contains(LocalToolOption.Calculator)) add(createCalculatorTool(context))
                if (assistant.localTools.contains(LocalToolOption.TaskTools)) addAll(createTaskTools())
                if (assistant.localTools.contains(LocalToolOption.PlanMode)) addAll(createPlanModeTools())
                if (assistant.localTools.contains(LocalToolOption.WorkerTools)) addAll(createWorkerTools(workerManager))
                if (assistant.localTools.contains(LocalToolOption.TeammateTools)) addAll(createTeammateTools(teammateRunner))
                if (assistant.localTools.contains(LocalToolOption.SendMessage)) {
                    add(createSendMessageTool())
                    add(createGetTeammateMessagesTool())
                    addAll(createKanbanTools())
                }
            }),
            inputTransformers = buildList {
                addAll(inputTransformers)
                add(templateTransformer)
                add(knowledgeBaseTransformer)
            },
            outputTransformers = outputTransformers,
        ).collect { chunk ->
            when (chunk) {
                is GenerationChunk.Messages -> {
                    val lastMsg = chunk.messages.lastOrNull()
                    val text = lastMsg?.toText() ?: ""
                    result = text
                    onChunk?.invoke(text, lastMsg?.parts)
                }
            }
        }

        return result
    }

    // ---- 压缩对话历史 ----

    suspend fun compressConversation(
        conversationId: Uuid,
        conversation: Conversation,
        additionalPrompt: String,
        targetTokens: Int,
        keepRecentMessages: Int = 32
    ): Result<Unit> = runCatching {
        val settings = settingsStore.settingsFlow.first()
        val model = settings.findModelById(settings.compressModelId)
            ?: settings.getCurrentChatModel()
            ?: throw IllegalStateException("No model available for compression")
        val provider = model.findProvider(settings.providers)
            ?: throw IllegalStateException("Provider not found")

        val providerHandler = providerManager.getProviderByType(provider)

        val maxMessagesPerChunk = 256
        val allMessages = conversation.currentMessages

        // Split messages into those to compress and those to keep
        val messagesToCompress: List<UIMessage>
        val messagesToKeep: List<UIMessage>

        if (keepRecentMessages > 0 && allMessages.size > keepRecentMessages) {
            messagesToCompress = allMessages.dropLast(keepRecentMessages)
            messagesToKeep = allMessages.takeLast(keepRecentMessages)
        } else if (keepRecentMessages > 0) {
            // Not enough messages to compress while keeping recent ones
            throw IllegalStateException(context.getString(R.string.chat_page_compress_not_enough_messages))
        } else {
            messagesToCompress = allMessages
            messagesToKeep = emptyList()
        }

        fun splitMessages(messages: List<UIMessage>): List<List<UIMessage>> {
            if (messages.size <= maxMessagesPerChunk) return listOf(messages)
            val mid = messages.size / 2
            val left = splitMessages(messages.subList(0, mid))
            val right = splitMessages(messages.subList(mid, messages.size))
            return left + right
        }

        suspend fun compressMessages(messages: List<UIMessage>): String {
            val contentToCompress = messages.joinToString("\n\n") { it.summaryAsText() }
            val prompt = settings.compressPrompt.applyPlaceholders(
                "content" to contentToCompress,
                "target_tokens" to targetTokens.toString(),
                "additional_context" to if (additionalPrompt.isNotBlank()) {
                    "Additional instructions from user: $additionalPrompt"
                } else "",
                "locale" to Locale.getDefault().displayName
            )

            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = TextGenerationParams(
                    model = model,
                ),
            )

            return result.choices[0].message?.toText()?.trim()
                ?: throw IllegalStateException("Failed to generate compressed summary")
        }

        val compressedSummaries = coroutineScope {
            splitMessages(messagesToCompress)
                .map { chunk -> async { compressMessages(chunk) } }
                .awaitAll()
        }

        // Create new conversation with compressed history as multiple user messages + kept messages
        val newMessageNodes = buildList {
            compressedSummaries.forEach { summary ->
                add(UIMessage.user(summary).toMessageNode())
            }
            addAll(messagesToKeep.map { it.toMessageNode() })
        }
        val newConversation = conversation.copy(
            messageNodes = newMessageNodes,
            chatSuggestions = emptyList(),
        )

        saveConversation(conversationId, newConversation)
    }

    // ---- 通知 ----

    private fun sendGenerationDoneNotification(conversationId: Uuid, senderName: String) {
        // 先取消 Live Update 通知
        cancelLiveUpdateNotification(conversationId)

        val conversation = getConversationFlow(conversationId).value
        context.sendNotification(
            channelId = CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
            notificationId = 1
        ) {
            title = senderName
            content = conversation.currentMessages.lastOrNull()?.toText()?.take(50)?.trim() ?: ""
            autoCancel = true
            useDefaults = true
            category = NotificationCompat.CATEGORY_MESSAGE
            contentIntent = getPendingIntent(context, conversationId)
        }
    }

    private fun getLiveUpdateNotificationId(conversationId: Uuid): Int {
        return conversationId.hashCode() + 10000
    }

    private fun sendLiveUpdateNotification(
        conversationId: Uuid,
        messages: List<UIMessage>,
        senderName: String
    ) {
        val lastMessage = messages.lastOrNull() ?: return
        val parts = lastMessage.parts

        // 确定当前状态
        val (chipText, statusText, contentText) = determineNotificationContent(parts)

        context.sendNotification(
            channelId = CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID,
            notificationId = getLiveUpdateNotificationId(conversationId)
        ) {
            title = senderName
            content = contentText
            subText = statusText
            ongoing = true
            onlyAlertOnce = true
            category = NotificationCompat.CATEGORY_PROGRESS
            useBigTextStyle = true
            contentIntent = getPendingIntent(context, conversationId)
            requestPromotedOngoing = true
            shortCriticalText = chipText
        }
    }

    private fun determineNotificationContent(parts: List<UIMessagePart>): Triple<String, String, String> {
        // 检查最近的 part 来确定状态
        val lastReasoning = parts.filterIsInstance<UIMessagePart.Reasoning>().lastOrNull()
        val lastTool = parts.filterIsInstance<UIMessagePart.Tool>().lastOrNull()
        val lastText = parts.filterIsInstance<UIMessagePart.Text>().lastOrNull()

        return when {
            // 正在执行工具
            lastTool != null && !lastTool.isExecuted -> {
                val toolName = lastTool.toolName.removePrefix("mcp__")
                Triple(
                    context.getString(R.string.notification_live_update_chip_tool),
                    context.getString(R.string.notification_live_update_tool, toolName),
                    lastTool.input.take(100)
                )
            }
            // 正在思考（Reasoning 未结束）
            lastReasoning != null && lastReasoning.finishedAt == null -> {
                Triple(
                    context.getString(R.string.notification_live_update_chip_thinking),
                    context.getString(R.string.notification_live_update_thinking),
                    lastReasoning.reasoning.takeLast(200)
                )
            }
            // 正在写回复
            lastText != null -> {
                Triple(
                    context.getString(R.string.notification_live_update_chip_writing),
                    context.getString(R.string.notification_live_update_writing),
                    lastText.text.takeLast(200)
                )
            }
            // 默认状态
            else -> {
                Triple(
                    context.getString(R.string.notification_live_update_chip_writing),
                    context.getString(R.string.notification_live_update_title),
                    ""
                )
            }
        }
    }

    private fun cancelLiveUpdateNotification(conversationId: Uuid) {
        context.cancelNotification(getLiveUpdateNotificationId(conversationId))
    }

    // region Foreground Service — 后台生成时保持进程存活

    private fun startGenerationForeground(title: String, conversationId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !me.rerere.rikkahub.utils.NotificationUtil.hasNotificationPermission(context)
        ) {
            Log.w(TAG, "startGenerationForeground: no notification permission, skipping")
            return
        }
        val intent = Intent(context, GenerationForegroundService::class.java).apply {
            action = GenerationForegroundService.ACTION_START
            putExtra(GenerationForegroundService.EXTRA_TITLE, title)
            putExtra(GenerationForegroundService.EXTRA_CONVERSATION_ID, conversationId)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun updateGenerationForeground(text: String) {
        val intent = Intent(context, GenerationForegroundService::class.java).apply {
            action = GenerationForegroundService.ACTION_UPDATE
            putExtra(GenerationForegroundService.EXTRA_TEXT, text.take(200))
        }
        context.startService(intent)
    }

    private fun stopGenerationForeground() {
        val intent = Intent(context, GenerationForegroundService::class.java).apply {
            action = GenerationForegroundService.ACTION_STOP
        }
        context.stopService(intent)
    }

    // endregion

    private fun getPendingIntent(context: Context, conversationId: Uuid): PendingIntent {
        val intent = Intent(context, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conversationId", conversationId.toString())
        }
        return PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // ---- 对话状态更新 ----

    private fun updateConversation(conversationId: Uuid, conversation: Conversation) {
        if (conversation.id != conversationId) return
        val session = getOrCreateSession(conversationId)
        checkFilesDelete(conversation, session.state.value)
        session.state.value = conversation
    }

    fun updateConversationState(conversationId: Uuid, update: (Conversation) -> Conversation) {
        val current = getConversationFlow(conversationId).value
        updateConversation(conversationId, update(current))
    }

    private fun checkFilesDelete(newConversation: Conversation, oldConversation: Conversation) {
        val newFiles = newConversation.files
        val oldFiles = oldConversation.files
        val deletedFiles = oldFiles.filter { file ->
            newFiles.none { it == file }
        }
        if (deletedFiles.isNotEmpty()) {
            filesManager.deleteChatFiles(deletedFiles)
            Log.w(TAG, "checkFilesDelete: $deletedFiles")
        }
    }

    suspend fun saveConversation(conversationId: Uuid, conversation: Conversation) {
        val exists = conversationRepo.existsConversationById(conversation.id)
        if (!exists && conversation.title.isBlank() && conversation.messageNodes.isEmpty()) {
            return // 新会话且为空时不保存
        }

        val updatedConversation = conversation.copy()
        updateConversation(conversationId, updatedConversation)

        if (!exists) {
            conversationRepo.insertConversation(updatedConversation)
        } else {
            conversationRepo.updateConversation(updatedConversation)
        }

        // Fire-and-forget: AgentService 异步处理
        kotlinx.coroutines.coroutineScope {
            launch { ListenerEventBus.emit(AgentEvent.ConversationModified(conversationId, updatedConversation)) }
        }
    }

    // ---- 翻译消息 ----

    fun translateMessage(
        conversationId: Uuid,
        message: UIMessage,
        targetLanguage: Locale
    ) {
        appScope.launch(Dispatchers.IO) {
            try {
                val settings = settingsStore.settingsFlow.first()

                val messageText = message.parts.filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n\n") { it.text }
                    .trim()

                if (messageText.isBlank()) return@launch

                // Set loading state for translation
                val loadingText = context.getString(R.string.translating)
                updateTranslationField(conversationId, message.id, loadingText)

                generationHandler.translateText(
                    settings = settings,
                    sourceText = messageText,
                    targetLanguage = targetLanguage
                ) { translatedText ->
                    // Update translation field in real-time
                    updateTranslationField(conversationId, message.id, translatedText)
                }.collect { /* Final translation already handled in onStreamUpdate */ }

                // Save the conversation after translation is complete
                saveConversation(conversationId, getConversationFlow(conversationId).value)
            } catch (e: Exception) {
                // Clear translation field on error
                clearTranslationField(conversationId, message.id)
                addError(e, conversationId, title = context.getString(R.string.error_title_translate_message))
            }
        }
    }

    private fun updateTranslationField(
        conversationId: Uuid,
        messageId: Uuid,
        translationText: String
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = translationText)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // ---- 消息操作 ----

    fun editMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>
    ) {
        if (parts.isEmptyInputMessage()) return

        val session = getOrCreateSession(conversationId)
        session.getJob()?.cancel()

        val job = appScope.launch {
            val currentConversation = getConversationFlow(conversationId).value
            val settings = settingsStore.settingsFlow.first()
            val assistant = settings.getAssistantById(currentConversation.assistantId)
                ?: settings.getCurrentAssistant()
            val processedParts = preprocessUserInputParts(parts, assistant)
            var edited = false

            val updatedNodes = currentConversation.messageNodes.mapIndexed { index, node ->
                if (!node.messages.any { it.id == messageId }) {
                    return@mapIndexed node
                }
                edited = true

                // 追加新版本（保留编辑历史）
                node.copy(
                    messages = node.messages + UIMessage(
                        role = node.role,
                        parts = processedParts,
                    ),
                    selectIndex = node.messages.size
                )
            }

            if (!edited) return@launch

            // 截断：保留到编辑位置，去掉之后的所有回复
            val editIndex = updatedNodes.indexOfFirst { node ->
                node.messages.any { it.id == messageId }
            }
            val truncated = updatedNodes.take(editIndex + 1)
            saveConversation(conversationId, currentConversation.copy(messageNodes = truncated))

            // 编辑后自动生成回复（替换旧的）
            handleMessageComplete(conversationId)
        }
        session.setJob(job)
    }

    suspend fun forkConversationAtMessage(
        conversationId: Uuid,
        messageId: Uuid
    ): Conversation {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNodeIndex = currentConversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            throw NotFoundException("Message not found")
        }

        val copiedNodes = currentConversation.messageNodes
            .subList(0, targetNodeIndex + 1)
            .map { node ->
                node.copy(
                    id = Uuid.random(),
                    messages = node.messages.map { message ->
                        message.copy(
                            parts = message.parts.map { part ->
                                part.copyWithForkedFileUrl()
                            }
                        )
                    }
                )
            }

        val forkConversation = Conversation(
            id = Uuid.random(),
            assistantId = currentConversation.assistantId,
            messageNodes = copiedNodes,
            customSystemPrompt = currentConversation.customSystemPrompt,
            modeInjectionIds = currentConversation.modeInjectionIds,
            lorebookIds = currentConversation.lorebookIds,
        )

        saveConversation(forkConversation.id, forkConversation)
        return forkConversation
    }

    suspend fun selectMessageNode(
        conversationId: Uuid,
        nodeId: Uuid,
        selectIndex: Int
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNode = currentConversation.messageNodes.firstOrNull { it.id == nodeId }
            ?: throw NotFoundException("Message node not found")

        if (selectIndex !in targetNode.messages.indices) {
            throw BadRequestException("Invalid selectIndex")
        }

        if (targetNode.selectIndex == selectIndex) {
            return
        }

        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.id == nodeId) {
                node.copy(selectIndex = selectIndex)
            } else {
                node
            }
        }

        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        messageId: Uuid,
        failIfMissing: Boolean = true,
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedConversation = buildConversationAfterMessageDelete(currentConversation, messageId)

        if (updatedConversation == null) {
            if (failIfMissing) {
                throw NotFoundException("Message not found")
            }
            return
        }

        saveConversation(conversationId, updatedConversation)
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        message: UIMessage,
    ) {
        deleteMessage(conversationId, message.id, failIfMissing = false)
    }

    private fun buildConversationAfterMessageDelete(
        conversation: Conversation,
        messageId: Uuid,
    ): Conversation? {
        val targetNodeIndex = conversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            return null
        }

        val updatedNodes = conversation.messageNodes.mapIndexedNotNull { index, node ->
            if (index != targetNodeIndex) {
                return@mapIndexedNotNull node
            }

            val nextMessages = node.messages.filterNot { it.id == messageId }
            if (nextMessages.isEmpty()) {
                return@mapIndexedNotNull null
            }

            val nextSelectIndex = node.selectIndex.coerceAtMost(nextMessages.lastIndex)
            node.copy(
                messages = nextMessages,
                selectIndex = nextSelectIndex,
            )
        }

        return conversation.copy(messageNodes = updatedNodes)
    }

    private fun UIMessagePart.copyWithForkedFileUrl(): UIMessagePart {
        fun copyLocalFileIfNeeded(url: String): String {
            if (!url.startsWith("file:")) return url
            val copied = filesManager.createChatFilesByContents(listOf(url.toUri())).firstOrNull()
            return copied?.toString() ?: url
        }

        return when (this) {
            is UIMessagePart.Image -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Document -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Video -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Audio -> copy(url = copyLocalFileIfNeeded(url))
            else -> this
        }
    }

    fun clearTranslationField(conversationId: Uuid, messageId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = null)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // 停止当前会话生成任务（不清理会话缓存）
    suspend fun stopGeneration(conversationId: Uuid) {
        val job = sessions[conversationId]?.getJob() ?: return
        job.cancel()
        runCatching { kotlinx.coroutines.withTimeout(5000) { job.join() } }
        finishInterruptedPendingTools(conversationId)
        me.rerere.rikkahub.data.ai.agent.AgentRunner.killAll()
    }

    /**
     * 子 Agent 执行循环。
     * 被 AgentRunner.run() 调用，运行在子 Agent 上下文中。
     */
    private suspend fun executeSubAgentLoop(
        conversationId: kotlin.uuid.Uuid,
        subModel: me.rerere.ai.provider.Model,
        providerSetting: me.rerere.ai.provider.ProviderSetting,
        providerImpl: me.rerere.ai.provider.Provider<me.rerere.ai.provider.ProviderSetting>,
        subTools: List<Tool>,
        assistant: Assistant,
        prompt: String,
        agentCallId: String = conversationId.toString(),
        agentDef: me.rerere.rikkahub.data.ai.tools.AgentDefinition? = null,
    ): List<UIMessagePart> {
        val session = getOrCreateSession(conversationId)
        val messages = mutableListOf(UIMessage.user(prompt))
        var finalText = ""
        // maxTurns: 取 agentDef.maxTurns 与 assistant.subAgentMaxSteps 的较小值
        val maxSteps = agentDef?.maxTurns?.coerceAtMost(assistant.subAgentMaxSteps) ?: assistant.subAgentMaxSteps
        var remainingSteps = maxSteps
        val stepLog = StringBuilder()
        // criticalReminder: 每轮注入
        val reminder = agentDef?.criticalReminder?.takeIf { it.isNotBlank() }

        while (remainingSteps > 0) {
            remainingSteps--

            try {
                val stepNum = stepLog.count { it == '\n' } + 1

                // effort → reasoningLevel 映射
                val reasoningLevel = when (agentDef?.effort) {
                    null, 0 -> me.rerere.ai.core.ReasoningLevel.OFF
                    1 -> me.rerere.ai.core.ReasoningLevel.LOW
                    2 -> me.rerere.ai.core.ReasoningLevel.MEDIUM
                    else -> me.rerere.ai.core.ReasoningLevel.HIGH
                }

                val chunk = providerImpl.generateText(
                    providerSetting = providerSetting,
                    messages = messages,
                    params = me.rerere.ai.provider.TextGenerationParams(
                        model = subModel,
                        tools = subTools,
                        reasoningLevel = reasoningLevel,
                    ),
                )

                val assistantMsg = chunk.choices.firstOrNull()?.message ?: break
                val assistantText = assistantMsg.toText()
                val toolCalls = assistantMsg.getTools().filter { !it.isExecuted }

                // 更新 Agent 进度追踪
                chunk.usage?.let { usage ->
                    AgentTaskTracker.recordTokenUsage(agentCallId, usage.promptTokens, usage.completionTokens)
                }
                if (toolCalls.isNotEmpty()) {
                    AgentTaskTracker.recordToolUse(
                        agentCallId,
                        toolCalls.first().toolName,
                        "调 ${toolCalls.first().toolName}: ${toolCalls.first().input.take(40)}",
                    )
                    me.rerere.rikkahub.data.ai.agent.AgentEventBus.emit(
                        me.rerere.rikkahub.data.ai.agent.AgentExecutionEvent(
                            agentId = agentCallId,
                            agentType = "general-purpose",
                            eventType = me.rerere.rikkahub.data.ai.agent.AgentEventType.TOOL_USE,
                            description = "调 ${toolCalls.first().toolName}",
                        )
                    )
                }

                if (toolCalls.isEmpty()) {
                    finalText = assistantText
                    stepLog.appendLine("→ 分析完成")
                    break
                }

                stepLog.append("→ 第${stepNum}步：")
                stepLog.appendLine(toolCalls.joinToString("、") { tc ->
                    "${tc.toolName}(${tc.input.take(40)})"
                })

                val executedTools = toolCalls.map { toolCall ->
                    val toolDef = subTools.find { it.name == toolCall.toolName }
                    if (toolDef == null) {
                        toolCall.copy(output = listOf(UIMessagePart.Text("Error: tool ${toolCall.toolName} not found")))
                    } else {
                        val args = try {
                            kotlinx.serialization.json.Json.parseToJsonElement(toolCall.input.ifBlank { "{}" })
                        } catch (e: Exception) {
                            error("Invalid args: ${e.message}")
                        }
                        val result = toolDef.execute(args)
                        toolCall.copy(output = result)
                    }
                }

                messages.add(assistantMsg.copy(
                    parts = assistantMsg.parts.map { part ->
                        if (part is UIMessagePart.Tool) executedTools.find { it.toolCallId == part.toolCallId } ?: part else part
                    }
                ))
                // criticalReminder: 每轮注入（用 USER 消息避免 API 限制）
                if (reminder != null) {
                    messages.add(UIMessage.user(reminder))
                }
            } catch (e: Exception) {
                stepLog.appendLine("→ 错误: ${e.message?.take(100)}")
                break
            }
        }

        if (finalText.isBlank()) {
            finalText = messages.lastOrNull()?.toText()?.takeIf { it.isNotBlank() } ?: ""
        }

        val outputText = if (stepLog.isNotEmpty()) {
            stepLog.appendLine().append(finalText)
            stepLog.toString()
        } else {
            finalText
        }

        return listOf(UIMessagePart.Text(outputText))
    }
}
