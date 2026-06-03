package me.rerere.rikkahub.data.ai.agent

import kotlinx.coroutines.Deferred

/**
 * Agent 上下文信息，对齐官方 agentContext.ts + agentId.ts。
 *
 * SubagentContext:
 * - 子 Agent 在进程内运行，用于快速授权任务
 * - 包含 agentId、parentSessionId、subagentName 等元信息
 *
 * agentId.ts:
 * - 确定性 ID 格式: agentName@teamName
 * - 请求 ID 格式: requestType-timestamp@agentId
 */

/** 子 Agent 上下文 */
data class SubagentContext(
    val agentId: String,
    val parentSessionId: String? = null,
    val subagentName: String? = null,
    val isBuiltIn: Boolean = false,
    val invokingRequestId: String? = null,
    val invocationKind: String = "spawn", // spawn | resume
    val deferred: Deferred<String>? = null,
)

/**
 * Agent 上下文存储。
 * 使用 ThreadLocal 而非 AsyncLocalStorage（Android 无此 API）。
 */
object AgentContextStore {
    private val context = ThreadLocal<SubagentContext>()

    fun set(ctx: SubagentContext) {
        context.set(ctx)
    }

    fun get(): SubagentContext? = context.get()

    fun clear() {
        context.remove()
    }

    /**
     * 在指定上下文中执行代码块。
     * 对齐官方 runWithAgentContext()。
     */
    fun <T> runWith(ctx: SubagentContext, fn: () -> T): T {
        val previous = context.get()
        context.set(ctx)
        try {
            return fn()
        } finally {
            if (previous != null) context.set(previous) else context.remove()
        }
    }
}

/**
 * 格式化 Agent ID: agentName@teamName。
 * 对齐官方 formatAgentId()。
 */
fun formatAgentId(agentName: String, teamName: String): String {
    return "$agentName@$teamName"
}

/**
 * 解析 Agent ID。
 * 对齐官方 parseAgentId()。
 */
data class AgentIdParts(val agentName: String, val teamName: String)

fun parseAgentId(agentId: String): AgentIdParts? {
    val atIndex = agentId.indexOf('@')
    if (atIndex == -1) return null
    return AgentIdParts(
        agentName = agentId.substring(0, atIndex),
        teamName = agentId.substring(atIndex + 1),
    )
}

/**
 * 生成请求 ID: {requestType}-{timestamp}@{agentId}
 */
fun generateRequestId(requestType: String, agentId: String): String {
    return "${requestType}-${System.currentTimeMillis()}@$agentId"
}
