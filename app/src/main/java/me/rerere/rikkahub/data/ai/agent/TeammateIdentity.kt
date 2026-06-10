package me.rerere.rikkahub.data.ai.agent

import me.rerere.rikkahub.data.ai.tools.AgentColor

/**
 * 队友身份数据类。
 * 对应泄露版 InProcessTeammateTask/types.ts 的 TeammateIdentity。
 */
data class TeammateIdentity(
    val agentId: String,
    val agentName: String,
    val teamName: String = "default",
    val color: AgentColor = AgentColor.BLUE,
    val planModeRequired: Boolean = false,
    val parentSessionId: String = "",
)

/**
 * 队友运行时状态。
 * 对应泄露版 InProcessTeammateTaskState。
 */
data class TeammateState(
    val identity: TeammateIdentity,
    val status: TeammateStatus = TeammateStatus.SPAWNING,
    val prompt: String = "",
    val model: String? = null,
    val requestId: String? = null,
    val result: String? = null,
    val error: String? = null,
    val isIdle: Boolean = false,
    val shutdownRequested: Boolean = false,
    val toolUseCount: Int = 0,
    val tokenCount: Int = 0,
)

enum class TeammateStatus {
    SPAWNING,
    RUNNING,
    IDLE,
    COMPLETED,
    FAILED,
    CANCELLED,
}
