package me.rerere.rikkahub.data.ai.listener

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.compaction.AutoCompactor
import me.rerere.rikkahub.data.ai.hooks.HookRegistry
import me.rerere.rikkahub.data.ai.hooks.HookEvent
import me.rerere.ai.core.Tool
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.rikkahub.data.ai.session.SessionStore
import me.rerere.rikkahub.data.ai.tools.AgentDefinition
import me.rerere.rikkahub.data.ai.tools.AgentSystemPrompt
import kotlinx.serialization.json.jsonObject

private const val TAG = "AgentService"

/**
 * Agent 事件处理器。
 * 订阅 AgentEventBus，处理所有 agent 相关功能。
 *
 * 对齐 learn-claude-code 20 事件点：
 * - UserPromptSubmit：用户输入注入
 * - PreToolCheck：SafetyHook 阻塞审核
 * - PostToolNotify：工具日志
 * - SubagentStart/Stop：子 Agent 生命周期
 * - TeammateIdle/TaskComplete：队友生命周期
 * - TaskCreated/Updated/Completed：任务审计日志
 * - CompactTriggered：压缩审计
 * - Generation* / SessionStopped：会话管理
 */
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
        Log.d(TAG, "User prompt submitted: ${event.userInput.take(100)}")
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
        // 预留：PostToolUse hooks、日志记录等
        Log.d(TAG, "Tool ${event.tool.name} executed")
    }

    // ── 子 Agent 生命周期 ──
    private suspend fun handleSubagentStart(event: AgentEvent.SubagentStart) {
        Log.i(TAG, "Subagent started: ${event.agentType} - ${event.description.take(50)}")
    }

    private suspend fun handleSubagentStop(event: AgentEvent.SubagentStop) {
        Log.i(TAG, "Subagent stopped: ${event.agentType} - ${event.result.take(100)}")
    }

    // ── 队友生命周期 ──
    private suspend fun handleTeammateIdle(event: AgentEvent.TeammateIdle) {
        Log.d(TAG, "Teammate idle: ${event.agentName} for ${System.currentTimeMillis() - event.idleSince}ms")
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
                            Tool(name = "compaction", description = ""),
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
    }

    private suspend fun handleGenerationRoundComplete(event: AgentEvent.GenerationRoundComplete) {
        autoCompactor?.maybeCompact(event.messages)?.let { result ->
            Log.i(TAG, "Auto-compacted ${result.removedCount} messages")
        }
    }

    private suspend fun handleGenerationStarted(event: AgentEvent.GenerationStarted) {
        // 预留：AgentMemory 加载
    }

    private suspend fun handleGenerationCompleted(event: AgentEvent.GenerationCompleted) {
        // 预留：清理、通知
    }

    private suspend fun handleSessionStopped(event: AgentEvent.SessionStopped) {
        Log.i(TAG, "Session stopped: ${event.conversationId}")
    }
}
