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
 * ChatService / GenerationHandler 在关键节点发射事件。
 * AgentService 订阅并处理。
 *
 * 阻塞设计：只有 PreToolCheck 使用 CompletableDeferred 等待结果（SafetyHook 必须阻止危险命令）。
 * 其他事件全部 fire-and-forget。
 */
sealed interface AgentEvent {
    /** 用户输入提交后、LLM 调用前。context 可被修改以注入额外信息 */
    data class UserPromptSubmit(
        val conversationId: Uuid,
        val userInput: String,
        val context: MutableMap<String, String> = mutableMapOf(),
    ) : AgentEvent

    /** 工具执行前检查。reply 回复 true=允许执行，false=阻止 */
    data class PreToolCheck(
        val tool: Tool,
        val args: JsonElement,
        val reply: CompletableDeferred<Boolean>,
    ) : AgentEvent

    /** 工具执行后通知 */
    data class PostToolNotify(
        val tool: Tool,
        val args: JsonElement,
        val result: List<UIMessagePart>,
    ) : AgentEvent

    /** 会话被修改 */
    data class ConversationModified(
        val conversationId: Uuid,
        val conversation: Conversation,
    ) : AgentEvent

    /** 生成中间轮完成 */
    data class GenerationRoundComplete(
        val conversationId: Uuid,
        val messages: List<UIMessage>,
    ) : AgentEvent

    /** 生成开始 */
    data class GenerationStarted(
        val conversationId: Uuid,
        val messages: List<UIMessage>,
    ) : AgentEvent

    /** 生成完成 */
    data class GenerationCompleted(
        val conversationId: Uuid,
        val messages: List<UIMessage>,
    ) : AgentEvent

    /** 会话停止时触发（用于摘要生成等） */
    data class SessionStopped(
        val conversationId: Uuid,
        val messages: List<UIMessage>,
    ) : AgentEvent
}

object AgentEventBus {
    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    suspend fun emit(event: AgentEvent) {
        _events.emit(event)
    }
}
