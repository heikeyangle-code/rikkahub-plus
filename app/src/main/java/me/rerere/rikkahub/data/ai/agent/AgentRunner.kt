package me.rerere.rikkahub.data.ai.agent

import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.AgentDefinition
import me.rerere.rikkahub.data.ai.tools.AgentRegistry
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Agent 执行器，对齐官方 runAgent.ts 的核心逻辑。
 *
 * 职责：
 * - 包裹 AgentContextStore 上下文
 * - 注册 AgentLifecycleManager 生命周期
 * - 启动 AgentSummaryService 摘要
 * - 触发 AgentEventBus 事件
 * - Fork 检测
 * - 执行结果返回
 */
object AgentRunner {

    /** Agent MCP 初始化回调。由 ChatService 设置。 */
    var onInitAgentMcp: ((agentDef: AgentDefinition) -> Unit)? = null

    /** Agent MCP 清理回调。由 ChatService 设置。 */
    var onCleanupAgentMcp: ((agentDef: AgentDefinition) -> Unit)? = null

    /** Agent skill 预加载回调。由 ChatService 设置。 */
    var onPreloadSkills: ((agent: AgentDefinition, callback: (String) -> Unit) -> Unit)? = null

    /**
     * 在 Agent 执行前初始化 MCP。
     * 对齐官方 initializeAgentMcpServers()。
     */
    fun initAgentMcp(agentDef: AgentDefinition?) {
        if (agentDef == null) return
        onInitAgentMcp?.invoke(agentDef)
    }

    /**
     * 在 Agent 执行后清理 MCP。
     */
    fun cleanupAgentMcp(agentDef: AgentDefinition?) {
        if (agentDef == null) return
        onCleanupAgentMcp?.invoke(agentDef)
    }

    /** 取消所有后台运行的 agent */
    fun killAll() {
        lifecycleManagers.values.forEach { it.killAll() }
        backgroundScopes.values.forEach { it.cancel("AgentRunner.killAll()") }
        backgroundScopes.clear()
        lifecycleManagers.clear()
        AgentTaskTracker.clear()
    }

    /**
     * 预加载 agent 定义的 skill。
     * 对齐官方 skillsToPreload 逻辑。
     */
    fun preloadSkills(agentDef: AgentDefinition?, onSkill: (String) -> Unit) {
        if (agentDef == null || agentDef.skills.isEmpty()) return
        onPreloadSkills?.invoke(agentDef, onSkill)
    }

    private val backgroundScopes = ConcurrentHashMap<String, CoroutineScope>()
    private val lifecycleManagers = ConcurrentHashMap<String, AgentLifecycleManager>()

    /**
     * 执行 agent，返回结果文本。
     *
     * @param agentDef agent 定义
     * @param agentCallId 唯一 ID
     * @param prompt 要发送给 agent 的 prompt
     * @param subTools 可用工具列表
     * @param runInBackground 是否后台执行
     * @param agentType agent 类型名
     * @param executeBlock 实际的执行逻辑（由 ChatService 提供）
     * @param description 任务描述
     */
    suspend fun run(
        agentDef: AgentDefinition?,
        agentCallId: String,
        prompt: String,
        subTools: List<Tool>,
        runInBackground: Boolean,
        agentType: String,
        description: String,
        executeBlock: suspend () -> List<UIMessagePart>,
    ): List<UIMessagePart> {
        // Fork检测
        val isFork = agentType == ForkSubagent.FORK_AGENT_TYPE

        // 创建上下文
        val context = SubagentContext(
            agentId = agentCallId,
            subagentName = agentType,
            isBuiltIn = agentDef?.isBuiltin ?: true,
        )

        // 注册生命周期
        val lifecycle = AgentLifecycleManager()
        val task = lifecycle.register(agentCallId, agentType, description, agentDef ?: AgentRegistry.get("general-purpose")!!)

        // 发射 STARTED 事件
        AgentEventBus.emit(AgentExecutionEvent(
            agentId = agentCallId,
            agentType = agentType,
            eventType = AgentEventType.STARTED,
            description = "Agent $agentType started: ${description.take(50)}",
        ))
        me.rerere.rikkahub.data.ai.listener.AgentEventBus.emit(
            me.rerere.rikkahub.data.ai.listener.AgentEvent.SubagentStart(
                agentId = agentCallId,
                agentType = agentType,
                description = description,
            )
        )

        return AgentContextStore.runWith(context) {
            if (runInBackground) {
                // 后台模式：立即返回，在协程中执行
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                backgroundScopes[agentCallId] = scope
                lifecycleManagers[agentCallId] = lifecycle
                lifecycle.get(agentCallId)?.scope = scope

                scope.launch {
                    try {
                        val result = executeBlock()
                        val resultText = if (result.isNotEmpty()) {
                            result.joinToString("\n") { part -> part.toString() }
                        } else ""

                        lifecycle.complete(agentCallId, resultText)
                        me.rerere.rikkahub.data.ai.listener.AgentEventBus.emit(
                            me.rerere.rikkahub.data.ai.listener.AgentEvent.SubagentStop(
                                agentId = agentCallId,
                                agentType = agentType,
                                result = resultText.take(200),
                            )
                        )
                        enqueueAgentNotification(
                            agentId = agentCallId,
                            agentType = agentType,
                            description = description,
                            status = AgentLifecycleManager.AgentLifecycleStatus.COMPLETED,
                            result = resultText.take(200),
                        )
                        AgentEventBus.emit(AgentExecutionEvent(
                            agentId = agentCallId, agentType = agentType,
                            eventType = AgentEventType.COMPLETED,
                            description = resultText.take(100),
                            result = resultText,
                        ))
                    } catch (e: Exception) {
                        lifecycle.fail(agentCallId, e.message ?: "Unknown error")
                        enqueueAgentNotification(
                            agentId = agentCallId,
                            agentType = agentType,
                            description = description,
                            status = AgentLifecycleManager.AgentLifecycleStatus.FAILED,
                            error = e.message,
                        )
                        AgentEventBus.emit(AgentExecutionEvent(
                            agentId = agentCallId, agentType = agentType,
                            eventType = AgentEventType.FAILED,
                            description = e.message ?: "Failed",
                            error = e.message,
                        ))
                    } finally {
                        backgroundScopes.remove(agentCallId)
                        lifecycleManagers.remove(agentCallId)
                    }
                }

                // 后台启动摘要服务
                AgentSummaryService.start(
                    agentId = agentCallId,
                    initialProgress = AgentProgress(),
                    scope = scope,
                    onSummary = { summary ->
                        AgentEventBus.emit(AgentExecutionEvent(
                            agentId = agentCallId, agentType = agentType,
                            eventType = AgentEventType.SUMMARY,
                            description = summary,
                        ))
                    },
                )

                // 后台模式立即返回
                lifecycle.get(agentCallId)!!.deferred.let {
                    listOf(UIMessagePart.Text("{\"agentId\":\"$agentCallId\",\"status\":\"background\",\"description\":\"$description\"}"))
                }
            } else {
                // 同步模式：等待执行完成
                try {
                    val result = executeBlock()
                    val resultText = if (result.isNotEmpty()) {
                        result.joinToString("\n") { part -> part.toString() }
                    } else ""

                    lifecycle.complete(agentCallId, resultText)
                    me.rerere.rikkahub.data.ai.listener.AgentEventBus.emit(
                        me.rerere.rikkahub.data.ai.listener.AgentEvent.SubagentStop(
                            agentId = agentCallId,
                            agentType = agentType,
                            result = resultText.take(200),
                        )
                    )
                    AgentEventBus.emit(AgentExecutionEvent(
                        agentId = agentCallId, agentType = agentType,
                        eventType = AgentEventType.COMPLETED,
                        description = resultText.take(100),
                        result = resultText,
                    ))
                    result
                } catch (e: Exception) {
                    lifecycle.fail(agentCallId, e.message ?: "Unknown error")
                    AgentEventBus.emit(AgentExecutionEvent(
                        agentId = agentCallId, agentType = agentType,
                        eventType = AgentEventType.FAILED,
                        description = e.message ?: "Failed",
                        error = e.message,
                    ))
                    // 转换成 UIMessagePart 错误消息
                    val errorMsg = "Agent error: ${e.message}"
                    listOf(UIMessagePart.Text("{\"error\":\"$errorMsg\"}"))
                }
            }
        }
    }

    /** 中止运行的 agent */
    fun kill(agentCallId: String) {
        AgentLifecycleManager().kill(agentCallId)
        backgroundScopes[agentCallId]?.let {
            it.cancel()
            backgroundScopes.remove(agentCallId)
        }
        AgentEventBus.emit(AgentExecutionEvent(
            agentId = agentCallId, agentType = "",
            eventType = AgentEventType.CANCELLED,
            description = "Cancelled by user",
        ))
    }
}
