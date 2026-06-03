package me.rerere.rikkahub.data.ai.agent

import java.util.concurrent.ConcurrentHashMap

/**
 * 追踪 agent 任务执行的进度，对标 Claude Code 的 ProgressTracker。
 */
data class AgentProgress(
    val toolUseCount: Int = 0,
    val latestInputTokens: Int = 0,
    val cumulativeOutputTokens: Int = 0,
    val recentActivities: List<ActivityInfo> = emptyList(),
    val summary: String? = null,
)

data class ActivityInfo(
    val toolName: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
)

private const val MAX_RECENT_ACTIVITIES = 5

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

    fun getProgress(agentCallId: String): AgentProgress? = progressMap[agentCallId]

    fun endSession(agentCallId: String) {
        progressMap.remove(agentCallId)
    }

    /** 把 progress 格式化成 UI 可读的文本 */
    fun formatProgress(agentCallId: String): String {
        val p = progressMap[agentCallId] ?: return ""
        val parts = mutableListOf<String>()
        if (p.toolUseCount > 0) parts.add("${p.toolUseCount} 步")
        val totalTokens = p.latestInputTokens + p.cumulativeOutputTokens
        if (totalTokens > 0) parts.add("${totalTokens} tokens")
        if (p.recentActivities.isNotEmpty()) {
            val last = p.recentActivities.last()
            parts.add("最后: ${last.description}")
        }
        return parts.joinToString(" | ")
    }
}
