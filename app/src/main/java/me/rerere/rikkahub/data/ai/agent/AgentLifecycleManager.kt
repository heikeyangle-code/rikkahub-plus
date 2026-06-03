package me.rerere.rikkahub.data.ai.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.tools.AgentDefinition
import java.util.concurrent.ConcurrentHashMap

/**
 * Agent 生命周期管理器，对齐官方 LocalAgentTask.tsx 的 registerAsyncAgent / killAsyncAgent / completeAgentTask。
 *
 * 管理 agent 的完整生命周期：
 * - 注册（register / registerAsync）
 * - 进度追踪（updateProgress / getProgress）
 * - 摘要（updateSummary）
 * - 取消（kill）
 * - 完成（complete / fail）
 * - 恢复（resume）
 */
class AgentLifecycleManager {

    data class AgentTask(
        val agentId: String,
        val agentType: String,
        val description: String,
        val definition: AgentDefinition,
        var status: AgentLifecycleStatus = AgentLifecycleStatus.QUEUED,
        var progress: AgentProgress? = null,
        var summary: String? = null,
        var result: String? = null,
        var error: String? = null,
        val deferred: CompletableDeferred<String> = CompletableDeferred(),
        var cancelJob: Job? = null,
        var scope: CoroutineScope? = null,
    )

    enum class AgentLifecycleStatus {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED,
    }

    private val tasks = ConcurrentHashMap<String, AgentTask>()
    private val listeners = mutableListOf<(AgentTask) -> Unit>()

    fun addListener(listener: (AgentTask) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (AgentTask) -> Unit) {
        listeners.remove(listener)
    }

    private fun notify(task: AgentTask) {
        listeners.forEach { it(task) }
    }

    // ==================== 注册 ====================

    /**
     * 注册一个同步 agent 任务。
     */
    fun register(
        agentId: String,
        agentType: String,
        description: String,
        definition: AgentDefinition,
    ): AgentTask {
        val task = AgentTask(
            agentId = agentId,
            agentType = agentType,
            description = description,
            definition = definition,
            status = AgentLifecycleStatus.RUNNING,
        )
        tasks[agentId] = task
        AgentTaskTracker.createSession(agentId)
        notify(task)
        return task
    }

    /**
     * 注册一个异步（后台）agent 任务。
     */
    fun registerAsync(
        agentId: String,
        agentType: String,
        description: String,
        definition: AgentDefinition,
        scope: CoroutineScope,
    ): AgentTask {
        val task = AgentTask(
            agentId = agentId,
            agentType = agentType,
            description = description,
            definition = definition,
            status = AgentLifecycleStatus.QUEUED,
            scope = scope,
        )
        tasks[agentId] = task
        notify(task)
        return task
    }

    // ==================== 更新 ====================

    fun updateProgress(agentId: String, progress: AgentProgress) {
        val task = tasks[agentId] ?: return
        task.status = AgentLifecycleStatus.RUNNING
        task.progress = progress
        AgentTaskTracker.recordToolUse(
            agentId,
            progress.recentActivities.lastOrNull()?.toolName ?: "",
            progress.recentActivities.lastOrNull()?.description ?: "",
        )
        notify(task)
    }

    fun updateSummary(agentId: String, summary: String) {
        val task = tasks[agentId] ?: return
        task.summary = summary
        AgentTaskTracker.recordSummary(agentId, summary)
        notify(task)
    }

    // ==================== 完成 / 失败 / 取消 ====================

    fun complete(agentId: String, result: String) {
        val task = tasks[agentId] ?: return
        task.status = AgentLifecycleStatus.COMPLETED
        task.result = result
        AgentTaskTracker.updateStatus(agentId, AgentStatus.COMPLETED)
        task.deferred.complete(result)
        notify(task)
    }

    fun fail(agentId: String, error: String) {
        val task = tasks[agentId] ?: return
        task.status = AgentLifecycleStatus.FAILED
        task.error = error
        AgentTaskTracker.updateStatus(agentId, AgentStatus.FAILED)
        AgentTaskTracker.recordSummary(agentId, "Failed: $error")
        task.deferred.completeExceptionally(RuntimeException(error))
        notify(task)
    }

    fun kill(agentId: String) {
        val task = tasks[agentId] ?: return
        task.status = AgentLifecycleStatus.CANCELLED
        task.cancelJob?.cancel()
        task.scope?.cancel("Agent killed: $agentId")
        AgentTaskTracker.updateStatus(agentId, AgentStatus.CANCELLED)
        task.deferred.complete("cancelled")
        notify(task)
        AgentTaskTracker.endSession(agentId)
    }

    fun killAll() {
        tasks.keys.toList().forEach { kill(it) }
    }

    // ==================== 查询 ====================

    fun get(agentId: String): AgentTask? = tasks[agentId]

    fun getProgress(agentId: String): AgentProgress? = AgentTaskTracker.getProgress(agentId)

    fun listRunning(): List<AgentTask> = tasks.values.filter { it.status == AgentLifecycleStatus.RUNNING }

    fun listAll(): List<AgentTask> = tasks.values.toList()

    fun isRunning(agentId: String): Boolean = tasks[agentId]?.status == AgentLifecycleStatus.RUNNING

    fun cleanup(agentId: String) {
        tasks.remove(agentId)
        AgentTaskTracker.endSession(agentId)
    }
}
