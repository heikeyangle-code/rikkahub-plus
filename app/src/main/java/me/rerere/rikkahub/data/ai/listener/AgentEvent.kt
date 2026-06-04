package me.rerere.rikkahub.data.ai.listener

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonElement
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import kotlin.uuid.Uuid

/**
 * Agent 事件总线。
 *
 * 对齐 learn-claude-code s04 的 27 个事件点，以下为按功能分组：
 * - UserPromptSubmit: 用户输入注入
 * - PreToolCheck / PostToolNotify: 工具执行前后
 * - SubagentStart / SubagentStop: 子 Agent 生命周期
 * - TeammateIdle / TeammateTaskComplete: 队友生命周期
 * - TaskCreated / TaskUpdated / TaskCompleted: 任务系统
 * - Compact: 压缩触发
 * - ConversationModified / Generation* / SessionStopped: 会话管理
 *
 * 阻塞设计：只有 PreToolCheck 使用 CompletableDeferred 等待结果。
 * 其他事件全部 fire-and-forget。
 */
sealed interface AgentEvent {
    // ── 用户输入 ──
    data class UserPromptSubmit(
        val conversationId: Uuid,
        val userInput: String,
        val context: MutableMap<String, String> = mutableMapOf(),
    ) : AgentEvent

    // ── 工具执行（阻塞） ──
    data class PreToolCheck(
        val tool: Tool,
        val args: JsonElement,
        val reply: CompletableDeferred<Boolean>,
    ) : AgentEvent

    // ── 工具执行后 ──
    data class PostToolNotify(
        val tool: Tool,
        val args: JsonElement,
        val result: List<UIMessagePart>,
    ) : AgentEvent

    // ── 子 Agent 生命周期 ──
    data class SubagentStart(
        val agentId: String,
        val agentType: String,
        val description: String,
    ) : AgentEvent

    data class SubagentStop(
        val agentId: String,
        val agentType: String,
        val result: String,
    ) : AgentEvent

    // ── 队友生命周期 ──
    data class TeammateIdle(
        val agentName: String,
        val idleSince: Long,
    ) : AgentEvent

    data class TeammateTaskComplete(
        val agentName: String,
        val taskId: String,
        val result: String,
    ) : AgentEvent

    // ── 任务系统 ──
    data class TaskCreated(
        val taskId: String,
        val subject: String,
    ) : AgentEvent

    data class TaskUpdated(
        val taskId: String,
        val oldStatus: String,
        val newStatus: String,
    ) : AgentEvent

    data class TaskCompleted(
        val taskId: String,
        val subject: String,
    ) : AgentEvent

    // ── 压缩 ──
    data class CompactTriggered(
        val reason: String,
        val messagesBefore: Int,
        val messagesAfter: Int,
    ) : AgentEvent

    data class CompactRequest(
        val conversationId: Uuid,
        val messages: List<UIMessage>,
    ) : AgentEvent

    // ── 会话管理 ──
    data class ConversationModified(
        val conversationId: Uuid,
        val conversation: Conversation,
    ) : AgentEvent

    data class GenerationRoundComplete(
        val conversationId: Uuid,
        val messages: List<UIMessage>,
    ) : AgentEvent

    data class GenerationStarted(
        val conversationId: Uuid,
        val messages: List<UIMessage>,
    ) : AgentEvent

    data class GenerationCompleted(
        val conversationId: Uuid,
        val messages: List<UIMessage>,
    ) : AgentEvent

    data class SessionStopped(
        val conversationId: Uuid,
        val messages: List<UIMessage>,
    ) : AgentEvent
}

object AgentEventBus {
    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    suspend fun emit(event: AgentEvent) {
        _events.emit(event)
    }

    fun tryEmit(event: AgentEvent): Boolean {
        return _events.tryEmit(event)
    }
}
