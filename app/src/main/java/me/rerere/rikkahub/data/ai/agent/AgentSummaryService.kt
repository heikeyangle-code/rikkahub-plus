package me.rerere.rikkahub.data.ai.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Agent 后台摘要服务，对齐官方 agentSummary.ts。
 *
 * 在 agent 执行期间每隔 ~30 秒生成一次进度摘要。
 * 摘要为 3-5 字当前动作描述，用于 UI 显示。
 */

object AgentSummaryService {
    private val summaryJobs = mutableMapOf<String, Job>()
    private const val SUMMARY_INTERVAL_MS = 30_000L

    /**
     * 为指定 agent 启动后台摘要生成。
     * 对齐官方 startAgentSummarization()。
     *
     * @param agentId agent 唯一 ID
     * @param initialProgress 当前进度
     * @param scope 协程作用域
     * @param onSummary 摘要生成后的回调
     */
    fun start(
        agentId: String,
        initialProgress: AgentProgress,
        scope: CoroutineScope,
        onSummary: (String) -> Unit,
    ): () -> Unit {
        val job = scope.launch {
            var previousSummary: String? = initialProgress.summary

            while (isActive) {
                delay(SUMMARY_INTERVAL_MS)

                val progress = AgentTaskTracker.getProgress(agentId) ?: continue
                val summary = generateSummary(progress, previousSummary)

                if (summary != null && summary != previousSummary) {
                    previousSummary = summary
                    AgentTaskTracker.recordSummary(agentId, summary)
                    onSummary(summary)
                }
            }
        }

        summaryJobs[agentId] = job

        return { stop(agentId) }
    }

    /**
     * 停止指定 agent 的摘要生成。
     */
    fun stop(agentId: String) {
        summaryJobs[agentId]?.cancel()
        summaryJobs.remove(agentId)
    }

    /**
     * 根据进度生成摘要文本。
     * 对齐官方 buildSummaryPrompt()。
     *
     * 格式：3-5 字，现在进行时，描述当前工具/文件操作。
     */
    private fun generateSummary(
        progress: AgentProgress,
        previousSummary: String?,
    ): String? {
        val lastActivity = progress.recentActivities.lastOrNull() ?: return null

        // 根据工具名生成摘要
        return when {
            lastActivity.toolName.contains("search", ignoreCase = true) ||
            lastActivity.toolName.contains("grep", ignoreCase = true) ||
            lastActivity.toolName.contains("find", ignoreCase = true) -> {
                "搜索中: ${lastActivity.description.take(30)}"
            }
            lastActivity.toolName.contains("read", ignoreCase = true) ||
            lastActivity.toolName.contains("file", ignoreCase = true) -> {
                "读取: ${lastActivity.description.take(30)}"
            }
            lastActivity.toolName.contains("write", ignoreCase = true) ||
            lastActivity.toolName.contains("edit", ignoreCase = true) ||
            lastActivity.toolName.contains("create", ignoreCase = true) -> {
                "修改: ${lastActivity.description.take(30)}"
            }
            lastActivity.toolName.contains("exec", ignoreCase = true) ||
            lastActivity.toolName.contains("bash", ignoreCase = true) ||
            lastActivity.toolName.contains("command", ignoreCase = true) -> {
                "执行: ${lastActivity.description.take(30)}"
            }
            lastActivity.toolName.contains("test", ignoreCase = true) ||
            lastActivity.toolName.contains("build", ignoreCase = true) -> {
                "验证: ${lastActivity.description.take(30)}"
            }
            lastActivity.toolName.contains("web", ignoreCase = true) ||
            lastActivity.toolName.contains("search", ignoreCase = true) -> {
                "搜索: ${lastActivity.description.take(30)}"
            }
            else -> {
                "${lastActivity.description.take(40)}"
            }
        }
    }

    fun stopAll() {
        summaryJobs.values.forEach { it.cancel() }
        summaryJobs.clear()
    }
}
