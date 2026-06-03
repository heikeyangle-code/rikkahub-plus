package me.rerere.rikkahub.data.ai.agent

import java.util.concurrent.ConcurrentHashMap

/**
 * Agent 进度状态，对齐官方 AgentProgress 类型。
 */
data class AgentProgress(
    val toolUseCount: Int = 0,
    val latestInputTokens: Int = 0,
    val cumulativeOutputTokens: Int = 0,
    val recentActivities: List<ActivityInfo> = emptyList(),
    val summary: String? = null,
    val status: AgentStatus = AgentStatus.RUNNING,
)

enum class AgentStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class ActivityInfo(
    val toolName: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
)

private const val MAX_RECENT_ACTIVITIES = 10

/**
 * Agent 任务追踪器，对齐官方 LocalAgentTask.tsx 的 ProgressTracker。
 *
 * 追踪：
 * - 工具调用次数与活动记录
 * - token 消耗
 * - 摘要
 * - 状态（运行中/完成/失败/取消）
 */
object AgentTaskTracker {
    private val progressMap = ConcurrentHashMap<String, AgentProgress>()

    fun createSession(agentCallId: String) {
        progressMap[agentCallId] = AgentProgress()
    }

    fun recordToolUse(agentCallId: String, toolName: String, activityDescription: String) {
        progressMap.computeIfPresent(agentCallId) { _, p ->
            val activities = (p.recentActivities + ActivityInfo(toolName, activityDescription))
                .takeLast(MAX_RECENT_ACTIVITIES)
            p.copy(
                toolUseCount = p.toolUseCount + 1,
                recentActivities = activities,
            )
        }
    }

    fun recordTokenUsage(agentCallId: String, inputTokens: Int, outputTokens: Int) {
        progressMap.computeIfPresent(agentCallId) { _, p ->
            p.copy(
                latestInputTokens = inputTokens,
                cumulativeOutputTokens = p.cumulativeOutputTokens + outputTokens,
            )
        }
    }

    fun recordSummary(agentCallId: String, summary: String) {
        progressMap.computeIfPresent(agentCallId) { _, p ->
            p.copy(summary = summary)
        }
    }

    fun updateStatus(agentCallId: String, status: AgentStatus) {
        progressMap.computeIfPresent(agentCallId) { _, p ->
            p.copy(status = status)
        }
    }

    fun getProgress(agentCallId: String): AgentProgress? = progressMap[agentCallId]

    fun endSession(agentCallId: String) {
        progressMap.remove(agentCallId)
    }

    fun isRunning(agentCallId: String): Boolean {
        return progressMap[agentCallId]?.status == AgentStatus.RUNNING
    }

    /** 所有正在运行的 agent 列表 */
    fun runningAgents(): List<Pair<String, AgentProgress>> {
        return progressMap.mapNotNull { (id, p) ->
            if (p.status == AgentStatus.RUNNING) id to p else null
        }
    }

    /** 格式化进度为 UI 可读文本 */
    fun formatProgress(agentCallId: String): String {
        val p = progressMap[agentCallId] ?: return ""
        val parts = mutableListOf<String>()
        parts.add("[${p.status.name.lowercase()}]")
        if (p.toolUseCount > 0) parts.add("${p.toolUseCount} calls")
        val totalTokens = p.latestInputTokens + p.cumulativeOutputTokens
        if (totalTokens > 0) parts.add("${totalTokens} tokens")
        if (p.recentActivities.isNotEmpty()) {
            val last = p.recentActivities.last()
            parts.add(last.description)
        }
        return parts.joinToString(" | ")
    }
}
