package me.rerere.rikkahub.data.ai.listener

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.compaction.AutoCompactor
import me.rerere.rikkahub.data.ai.session.SessionStore
import me.rerere.rikkahub.data.ai.tools.AgentDefinition
import me.rerere.rikkahub.data.ai.tools.AgentSystemPrompt

private const val TAG = "AgentService"

/**
 * Agent 事件处理器。
 * 订阅 AgentEventBus，处理所有 agent 相关功能。
 *
 * 设计原则：
 * - SafetyHook：阻塞（PreToolCheck 的 CompletableDeferred）
 * - 其他全部：fire-and-forget，不阻塞主流程
 * - 出错只打日志，不抛异常
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
                        is AgentEvent.PreToolCheck -> handlePreToolCheck(event)
                        is AgentEvent.PostToolNotify -> handlePostToolNotify(event)
                        is AgentEvent.ConversationModified -> handleConversationModified(event)
                        is AgentEvent.GenerationRoundComplete -> handleGenerationRoundComplete(event)
                        is AgentEvent.GenerationStarted -> handleGenerationStarted(event)
                        is AgentEvent.GenerationCompleted -> handleGenerationCompleted(event)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Event handler error: ${e.message}", e)
                }
            }
        }
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
    }

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
        // 预留：AgentMemory 加载、摘要服务启动等
    }

    private suspend fun handleGenerationCompleted(event: AgentEvent.GenerationCompleted) {
        // 预留：清理、通知推送等
    }
}
