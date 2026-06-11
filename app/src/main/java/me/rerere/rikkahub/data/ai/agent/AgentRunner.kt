package me.rerere.rikkahub.data.ai.agent

import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.AgentDefinition
import me.rerere.rikkahub.data.ai.tools.AgentRegistry
import me.rerere.rikkahub.data.ai.tools.AgentSystemPrompt
import me.rerere.rikkahub.data.ai.tools.AgentMemoryScope
import me.rerere.rikkahub.data.ai.tools.isToolAllowed
import me.rerere.rikkahub.data.ai.tools.isToolAllowedForAsync
import me.rerere.rikkahub.data.ai.tools.ALL_AGENT_DISALLOWED_TOOLS
import me.rerere.rikkahub.data.ai.tools.CUSTOM_AGENT_DISALLOWED_TOOLS
import me.rerere.rikkahub.data.ai.tools.ASYNC_AGENT_ALLOWED_TOOLS
import me.rerere.rikkahub.data.ai.hooks.HookRegistry
import me.rerere.rikkahub.data.ai.hooks.HookEvent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.listener.AgentEventBus as ListenerEventBus
import me.rerere.rikkahub.data.ai.listener.AgentEvent

/**
 * Agent 执行器，对齐官方 runAgent.ts（975 行）+ agentToolUtils.ts（688 行）。
 *
 * CC 的 runAgent 完整流程：
 * 1. 初始化 MCP 服务器（initializeAgentMcpServers）
 * 2. 获取 userContext + systemContext（getUserContext / getSystemContext）
 * 3. 覆盖权限模式（agentPermissionMode → getAppState）
 * 4. 解析工具列表（resolveAgentTools / filterToolsForAgent）
 * 5. 构建 system prompt（getAgentSystemPrompt → 包含 memory 注入）
 * 6. 执行查询（query → message loop）
 * 7. 后台摘要（startAgentSummarization）
 * 8. 完成处理（finalizeAgentTool / classifyHandoffIfNeeded）
 * 9. 清理（MCP 清理、shell 任务清理、缓存清理）
 */
object AgentRunner {

    /** Agent MCP 初始化回调。由 ChatService 设置。 */
    var onInitAgentMcp: ((agentDef: AgentDefinition) -> Unit)? = null

    /** Agent MCP 清理回调。由 ChatService 设置。 */
    var onCleanupAgentMcp: ((agentDef: AgentDefinition) -> Unit)? = null

    /** Agent skill 预加载回调。由 ChatService 设置。 */
    var onPreloadSkills: ((agent: AgentDefinition, callback: (String) -> Unit) -> Unit)? = null

    /** 占位 Tool，用于 hook 事件传递 */
    private val NOOP_TOOL = Tool(name = "", description = "", execute = { emptyList<UIMessagePart>() })

    /** Agent 摘要服务启动回调。由 ChatService 设置。 */
    var onStartSummary: ((agentId: String, initialProgress: AgentProgress) -> Unit)? = null

    /**
     * 初始化 Agent MCP 服务器。
     * 对齐 CC initializeAgentMcpServers()（第 97-193 行）。
     */
    fun initAgentMcp(agentDef: AgentDefinition?) {
        if (agentDef == null) return
        onInitAgentMcp?.invoke(agentDef)
        // CC: 检查 agent.mcpServers，连接到每个服务器，返回合并后的 clients + cleanup
    }

    /**
     * 清理 Agent MCP 资源。
     * 对齐 CC runAgent 的 finally 块。
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
     * 解析 agent 的有效工具列表。
     * 对齐 CC resolveAgentTools()（agentToolUtils.ts 第 124-227 行）。
     *
     * 过滤链：
     * 1. 所有可用工具 → filterToolsForAgent()（MCP 放行 → 全局禁用 → 自定义禁用 → 异步白名单）
     * 2. agent 自身黑名单（disallowedTools）
     * 3. agent 自身白名单（tools，* = 全部放行）
     */
    fun resolveAgentTools(
        agentDef: AgentDefinition,
        availableTools: List<Tool>,
        isAsync: Boolean = false,
    ): List<String> {
        // 第1步：对所有可用工具执行 5 层过滤
        val filtered = availableTools.filter { tool ->
            isToolAllowed(agentDef, tool.name, isAsync)
        }
        return filtered.map { it.name }
    }

    /**
     * 获取 agent 的权限模式。
     * 对齐 CC runAgent 第 417-436 行。
     *
     * 逻辑：
     * - 如果父 session 在 bypassPermissions/acceptEdits 模式，agent 不能覆盖
     * - 否则用 agent 自身定义的 permissionMode
     * - 保留 CC 的 bubble 模式（权限弹窗冒泡到父终端）
     */
    fun resolvePermissionMode(
        agentDef: AgentDefinition?,
        parentPermissionMode: String?,
    ): String? {
        if (agentDef == null) return parentPermissionMode

        val agentMode = agentDef.permissionMode
        if (agentMode == null) return parentPermissionMode

        // CC 第 423-426 行：bypassPermissions 和 acceptEdits 不能被覆盖
        if (parentPermissionMode == "bypassPermissions" ||
            parentPermissionMode == "acceptEdits") {
            return parentPermissionMode
        }

        return agentMode
    }

    private val backgroundScopes = ConcurrentHashMap<String, CoroutineScope>()
    private val lifecycleManagers = ConcurrentHashMap<String, AgentLifecycleManager>()

    /**
     * 执行 agent，包含完整的生命周期管理。
     * 对齐 CC runAgent() 的核心流程（第 250-500 行）。
     *
     * 完整流程：
     * 1. 初始化 MCP
     * 2. 执行 hooks
     * 3. 加载记忆
     * 4. 解析工具 + 权限
     * 5. 执行
     * 6. 清理 MCP + hooks
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

        // ── 对齐 CC step 1: 初始化 MCP ──
        initAgentMcp(agentDef)

        // ── SUBAGENT_START event + hook ──
        if (agentDef != null) {
            runCatching {
                ListenerEventBus.emit(AgentEvent.SubagentStart(
                    agentId = agentCallId,
                    agentType = agentDef.agentType,
                    description = description,
                ))
            }
            runCatching {
                val args = buildJsonObject { put("prompt", JsonPrimitive(prompt)) }
                HookRegistry.getHooks(HookEvent.SUBAGENT_START).forEach { hook ->
                    hook.execute(NOOP_TOOL.copy(name = agentDef.agentType), args)
                }
            }
        }

        // ── 对齐 CC: 前台执行 ──
        if (!runInBackground) {
            val lifecycleId = if (agentDef != null) {
                registerLifecycle(agentDef, agentCallId)
            } else null

            try {
                val result = executeBlock()
                return result
            } finally {
                if (lifecycleId != null) unregisterLifecycle(lifecycleId)
                // ── SUBAGENT_STOP event + hook ──
                if (agentDef != null) {
                    runCatching {
                        ListenerEventBus.emit(AgentEvent.SubagentStop(
                            agentId = agentCallId,
                            agentType = agentDef.agentType,
                            result = "completed",
                        ))
                    }
                    runCatching {
                        val args = buildJsonObject { put("prompt", JsonPrimitive(prompt)) }
                        HookRegistry.getHooks(HookEvent.SUBAGENT_STOP).forEach { hook ->
                            hook.execute(NOOP_TOOL.copy(name = agentDef.agentType), args)
                        }
                    }
                }
                // ── 对齐 CC: 清理 MCP ──
                cleanupAgentMcp(agentDef)
            }
        }

        // ── 对齐 CC: 后台异步执行 ──
        return executeBackground(
            agentDef = agentDef,
            agentCallId = agentCallId,
            prompt = prompt,
            subTools = subTools,
            agentType = agentType,
            description = description,
            executeBlock = executeBlock,
        )
    }

    /**
     * 后台执行 agent。
     * 对齐 CC runAsyncAgentLifecycle()（agentToolUtils.ts）。
     */
    private suspend fun executeBackground(
        agentDef: AgentDefinition?,
        agentCallId: String,
        prompt: String,
        subTools: List<Tool>,
        agentType: String,
        description: String,
        executeBlock: suspend () -> List<UIMessagePart>,
    ): List<UIMessagePart> {
        val lifecycleId = if (agentDef != null) {
            registerLifecycle(agentDef, agentCallId)
        } else null

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        backgroundScopes[agentCallId] = scope

        // ── 启动后台摘要 ──
        onStartSummary?.invoke(agentCallId, AgentProgress(
            agentType = agentType,
            status = AgentStatus.RUNNING,
            summary = description,
        ))

        val deferred = scope.async {
            try {
                AgentEventBus.emit(AgentExecutionEvent(
                    agentId = agentCallId,
                    agentType = agentType,
                    eventType = AgentEventType.STARTED,
                    description = description,
                ))

                val result = executeBlock()

                AgentEventBus.emit(AgentExecutionEvent(
                    agentId = agentCallId,
                    agentType = agentType,
                    eventType = AgentEventType.COMPLETED,
                    description = description,
                    result = result.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text },
                ))

                // 对齐 CC: finalizeAgentTool()
                result
            } catch (e: Exception) {
                AgentEventBus.emit(AgentExecutionEvent(
                    agentId = agentCallId,
                    agentType = agentType,
                    eventType = AgentEventType.FAILED,
                    description = description,
                    error = e.message ?: "Unknown error",
                ))
                throw e
            } finally {
                if (lifecycleId != null) unregisterLifecycle(lifecycleId)
                backgroundScopes.remove(agentCallId)
                // ── 对齐 CC: 清理 ──
                cleanupAgentMcp(agentDef)
            }
        }

        return deferred.await()
    }

    /**
     * 预加载 agent 定义的 skill。
     * 对齐 CC skillsToPreload 逻辑。
     */
    fun preloadSkills(agentDef: AgentDefinition?, onSkill: (String) -> Unit) {
        if (agentDef == null || agentDef.skills.isEmpty()) return
        onPreloadSkills?.invoke(agentDef, onSkill)
    }

    private fun registerLifecycle(agentDef: AgentDefinition, callId: String): String {
        val manager = AgentLifecycleManager()
        manager.register(
            agentId = callId,
            agentType = agentDef.agentType,
            description = agentDef.description,
            definition = agentDef,
        )
        lifecycleManagers[callId] = manager
        return callId
    }

    private fun unregisterLifecycle(id: String) {
        lifecycleManagers.remove(id)?.let { manager ->
            manager.cleanup(id)
        }
    }

    fun killAgent(agentCallId: String) {
        lifecycleManagers[agentCallId]?.kill(agentCallId)
        backgroundScopes[agentCallId]?.cancel("AgentRunner.killAgent($agentCallId)")
    }
}
