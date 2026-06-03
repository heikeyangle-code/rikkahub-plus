package me.rerere.rikkahub.data.ai.agent

import me.rerere.rikkahub.data.ai.tools.AgentDefinition

/**
 * Agent 执行渲染系统，对齐官方 AgentTool/UI.tsx。
 *
 * 定义 agent 执行过程中的消息格式和进度显示。
 * UI 组件在 chat 页面中渲染这些数据。
 */

/**
 * Agent 执行进度消息，用于聊天 UI 显示。
 * 对齐官方 AgentProgress + ProgressTracker 的 UI 输出。
 */
data class AgentExecutionEvent(
    val agentId: String,
    val agentType: String,
    val eventType: AgentEventType,
    val description: String,
    val progress: AgentProgress? = null,
    val result: String? = null,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class AgentEventType {
    /** Agent 已注册/开始 */
    STARTED,
    /** 工具调用 */
    TOOL_USE,
    /** 进度更新 */
    PROGRESS,
    /** 摘要 */
    SUMMARY,
    /** 完成 */
    COMPLETED,
    /** 失败 */
    FAILED,
    /** 取消 */
    CANCELLED,
}

/**
 * Agent 执行事件总线。
 * 事件消费者可以是 UI 组件，负责渲染。
 */
object AgentEventBus {
    private val listeners = mutableListOf<(AgentExecutionEvent) -> Unit>()

    fun subscribe(listener: (AgentExecutionEvent) -> Unit) {
        listeners.add(listener)
    }

    fun unsubscribe(listener: (AgentExecutionEvent) -> Unit) {
        listeners.remove(listener)
    }

    fun emit(event: AgentExecutionEvent) {
        listeners.forEach { it(event) }
    }

    fun clear() {
        listeners.clear()
    }
}

/**
 * 格式化 agent 执行为聊天可读文本。
 * 用于在不渲染 UI 组件时的 fallback 显示。
 */
fun formatAgentExecutionResult(agent: AgentDefinition, result: String): String {
    return buildString {
        appendLine("【${agent.name} (${agent.agentType})】")
        appendLine()
        append(result)
    }
}
