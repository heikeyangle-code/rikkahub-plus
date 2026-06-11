package me.rerere.rikkahub.data.ai.listener

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.agent.AgentEventBus as AgentExecBus
import me.rerere.rikkahub.data.ai.agent.AgentExecutionEvent
import me.rerere.rikkahub.data.ai.agent.AgentEventType
import me.rerere.rikkahub.data.ai.compaction.AutoCompactor
import me.rerere.rikkahub.data.ai.hooks.HookRegistry
import me.rerere.rikkahub.data.ai.hooks.HookEvent
import me.rerere.rikkahub.data.ai.session.SessionStore
import me.rerere.rikkahub.data.ai.tools.AgentDefinition
import me.rerere.rikkahub.data.ai.tools.AgentSystemPrompt
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val TAG = "AgentService"

/**
 * Agent 事件处理器。
 * 订阅 AgentEventBus，处理所有 agent 相关功能。
 */
private val NOOP_TOOL = Tool(name = "", description = "", execute = { emptyList<UIMessagePart>() })

class AgentService(
    private val appScope: CoroutineScope,
    private val autoCompactor: AutoCompactor? = null,
    private val sessionStore: SessionStore? = null,
) {
    private val blockedPatterns = listOf(
        "rm -rf /" to "禁止删除根目录",
        "mkfs" to "禁止格式化磁盘",
        ":(){ :|:& };:" to "禁止 fork 炸弹",
    )

    /** 活跃会话计数 */
    private val activeConversations = mutableSetOf<String>()

    /** 工具调用统计 */
    private val toolCallStats = mutableMapOf<String, Int>()

    init {
        appScope.launch {
            AgentEventBus.events.collect { event ->
                try {
                    when (event) {
                        is AgentEvent.UserPromptSubmit -> handleUserPromptSubmit(event)
                        is AgentEvent.PreToolCheck -> handlePreToolCheck(event)
                        is AgentEvent.PostToolNotify -> handlePostToolNotify(event)
                        is AgentEvent.SubagentStart -> handleSubagentStart(event)
                        is AgentEvent.SubagentStop -> handleSubagentStop(event)
                        is AgentEvent.TeammateIdle -> handleTeammateIdle(event)
                        is AgentEvent.TeammateTaskComplete -> handleTeammateTaskComplete(event)
                        is AgentEvent.TaskCreated -> handleTaskCreated(event)
                        is AgentEvent.TaskUpdated -> handleTaskUpdated(event)
                        is AgentEvent.TaskCompleted -> handleTaskCompleted(event)
                        is AgentEvent.CompactTriggered -> handleCompactTriggered(event)
                        is AgentEvent.CompactRequest -> handleCompactRequest(event)
                        is AgentEvent.ConversationModified -> handleConversationModified(event)
                        is AgentEvent.GenerationRoundComplete -> handleGenerationRoundComplete(event)
                        is AgentEvent.GenerationStarted -> handleGenerationStarted(event)
                        is AgentEvent.GenerationCompleted -> handleGenerationCompleted(event)
                        is AgentEvent.SessionStopped -> handleSessionStopped(event)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Event handler error: ${e.message}", e)
                }
            }
        }
    }

    private suspend fun handleUserPromptSubmit(event: AgentEvent.UserPromptSubmit) {
        val convId = event.conversationId.toString()
        activeConversations.add(convId)
        Log.d(TAG, "User prompt submitted: ${event.userInput.take(100)} [conv=$convId]")
    }

    private suspend fun handlePreToolCheck(event: AgentEvent.PreToolCheck) {
        val allowed = if (event.tool.name == "execute_command") {
            val command = event.args.jsonObject["command"]?.toString() ?: ""
            val isBlocked = blockedPatterns.any { (pattern, _) ->
                command.contains(pattern, ignoreCase = true)
            }
            if (isBlocked) {
                val reason = blockedPatterns.firstOrNull { command.contains(it.first, ignoreCase = true) }?.second
                    ?: "危险命令"
                Log.w(TAG, "Blocked dangerous command: $reason")
                false
            } else true
        } else true
        event.reply.complete(allowed)
    }

    private suspend fun handlePostToolNotify(event: AgentEvent.PostToolNotify) {
        // 统计工具调用次数
        val count = toolCallStats.getOrDefault(event.tool.name, 0) + 1
        toolCallStats[event.tool.name] = count

        val resultLen = event.result.sumOf { p ->
            (p as? UIMessagePart.Text)?.text?.length ?: 0
        }

        // 推送到 AgentExecutionEvent 总线（UI 渲染）
        AgentExecBus.emit(AgentExecutionEvent(
            agentId = "main",
            agentType = "main",
            eventType = AgentEventType.TOOL_USE,
            description = "${event.tool.name} → ${resultLen}chars (#$count)",
        ))

        Log.d(TAG, "Tool ${event.tool.name} executed (#$count, result=${resultLen}chars)")
    }

    // ── 子 Agent 生命周期 ──
    private suspend fun handleSubagentStart(event: AgentEvent.SubagentStart) {
        val msg = "Subagent started: ${event.agentType} - ${event.description.take(50)}"
        Log.i(TAG, msg)

        // 推送到 UI 总线
        AgentExecBus.emit(AgentExecutionEvent(
            agentId = event.agentId,
            agentType = event.agentType,
            eventType = AgentEventType.STARTED,
            description = event.description.take(100),
        ))

        // 触发 ToolHook
        runCatching {
            HookRegistry.getHooks(HookEvent.SUBAGENT_START).forEach { hook ->
                hook.execute(
                    NOOP_TOOL.copy(name = event.agentType),
                    buildJsonObject { put("description", JsonPrimitive(event.description)) }
                )
            }
        }
    }

    private suspend fun handleSubagentStop(event: AgentEvent.SubagentStop) {
        val msg = "Subagent stopped: ${event.agentType} - ${event.result.take(100)}"
        Log.i(TAG, msg)

        AgentExecBus.emit(AgentExecutionEvent(
            agentId = event.agentId,
            agentType = event.agentType,
            eventType = AgentEventType.COMPLETED,
            description = event.result.take(100),
            result = event.result.take(200),
        ))

        runCatching {
            HookRegistry.getHooks(HookEvent.SUBAGENT_STOP).forEach { hook ->
                hook.execute(
                    NOOP_TOOL.copy(name = event.agentType),
                    buildJsonObject {
                        put("result", JsonPrimitive(event.result.take(200)))
                    }
                )
            }
        }
    }

    // ── 队友生命周期 ──
    private suspend fun handleTeammateIdle(event: AgentEvent.TeammateIdle) {
        val idleTime = System.currentTimeMillis() - event.idleSince
        Log.d(TAG, "Teammate idle: ${event.agentName} for ${idleTime}ms")
        if (idleTime > 300_000) {
            Log.w(TAG, "Teammate ${event.agentName} idle for ${idleTime / 1000}s, consider cancellation")
        }
    }

    private suspend fun handleTeammateTaskComplete(event: AgentEvent.TeammateTaskComplete) {
        Log.i(TAG, "Teammate ${event.agentName} completed task ${event.taskId}: ${event.result.take(100)}")
    }

    // ── 任务系统审计 ──
    private suspend fun handleTaskCreated(event: AgentEvent.TaskCreated) {
        Log.d(TAG, "Task created: ${event.taskId} - ${event.subject.take(50)}")
    }

    private suspend fun handleTaskUpdated(event: AgentEvent.TaskUpdated) {
        Log.d(TAG, "Task updated: ${event.taskId} ${event.oldStatus} -> ${event.newStatus}")
    }

    private suspend fun handleTaskCompleted(event: AgentEvent.TaskCompleted) {
        Log.i(TAG, "Task completed: ${event.taskId} - ${event.subject.take(50)}")
    }

    // ── 压缩 ──
    private suspend fun handleCompactTriggered(event: AgentEvent.CompactTriggered) {
        Log.i(TAG, "Compact triggered: ${event.reason} (${event.messagesBefore} -> ${event.messagesAfter} msgs)")
    }

    private suspend fun handleCompactRequest(event: AgentEvent.CompactRequest) {
        autoCompactor?.let { compactor ->
            val result = compactor.maybeCompact(event.messages)
            if (result != null) {
                Log.i(TAG, "Compact result: removed ${result.removedCount} messages")
                AgentEventBus.emit(AgentEvent.CompactTriggered(
                    reason = "auto",
                    messagesBefore = event.messages.size,
                    messagesAfter = result.compactedMessages.size,
                ))
                // ── COMPACT hook ──
                runCatching {
                    HookRegistry.getHooks(HookEvent.COMPACT).forEach { hook ->
                        hook.execute(
                            NOOP_TOOL.copy(name = "compaction"),
                            buildJsonObject {
                                put("removed", JsonPrimitive(result.removedCount))
                                put("before", JsonPrimitive(event.messages.size))
                                put("after", JsonPrimitive(result.compactedMessages.size))
                            }
                        )
                    }
                }
            }
        }
    }

    // ── 会话管理 ──
    private suspend fun handleConversationModified(event: AgentEvent.ConversationModified) {
        sessionStore?.saveSnapshot(
            me.rerere.rikkahub.data.ai.session.SessionSnapshot(
                sessionId = event.conversationId.toString(),
                messages = event.conversation.currentMessages,
                taskState = emptyList(),
                planModeState = me.rerere.rikkahub.data.ai.session.PlanModeSnapshot(
                    isInPlanMode = false,
                    effectiveMode = "NORMAL",
                ),
            )
        )
        Log.d(TAG, "Conversation modified: ${event.conversationId}, snapshot saved")
    }

    private suspend fun handleGenerationRoundComplete(event: AgentEvent.GenerationRoundComplete) {
        autoCompactor?.maybeCompact(event.messages)?.let { result ->
            Log.i(TAG, "Auto-compacted ${result.removedCount} messages")
            runCatching {
                HookRegistry.getHooks(HookEvent.COMPACT).forEach { hook ->
                    hook.execute(
                        NOOP_TOOL.copy(name = "auto_compact"),
                        buildJsonObject {
                            put("removed", JsonPrimitive(result.removedCount))
                            put("round_complete", JsonPrimitive(true))
                        }
                    )
                }
            }
        }
    }

    private suspend fun handleGenerationStarted(event: AgentEvent.GenerationStarted) {
        val convId = event.conversationId.toString()
        Log.d(TAG, "Generation started: conv=$convId, msgs=${event.messages.size}")

        AgentExecBus.emit(AgentExecutionEvent(
            agentId = convId,
            agentType = "main",
            eventType = AgentEventType.STARTED,
            description = "Generation started (${event.messages.size} messages in context)",
        ))
    }

    private suspend fun handleGenerationCompleted(event: AgentEvent.GenerationCompleted) {
        val convId = event.conversationId.toString()
        val lastMsg = event.messages.lastOrNull()
        val text = lastMsg?.parts?.filterIsInstance<UIMessagePart.Text>()?.joinToString("") { it.text } ?: ""
        Log.d(TAG, "Generation completed: conv=$convId, last=${text.take(50)}")

        AgentExecBus.emit(AgentExecutionEvent(
            agentId = convId,
            agentType = "main",
            eventType = AgentEventType.COMPLETED,
            description = text.take(100),
        ))
    }

    private suspend fun handleSessionStopped(event: AgentEvent.SessionStopped) {
        val convId = event.conversationId.toString()
        activeConversations.remove(convId)
        Log.i(TAG, "Session stopped: conv=$convId")

        AgentExecBus.emit(AgentExecutionEvent(
            agentId = convId,
            agentType = "main",
            eventType = AgentEventType.CANCELLED,
            description = "Session stopped",
        ))
    }

    /** 获取工具调用统计快照 */
    fun getToolCallStats(): Map<String, Int> = toolCallStats.toMap()

    /** 获取活跃会话数 */
    fun getActiveConversationCount(): Int = activeConversations.size
}
