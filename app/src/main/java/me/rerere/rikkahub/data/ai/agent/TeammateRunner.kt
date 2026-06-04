package me.rerere.rikkahub.data.ai.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import me.rerere.rikkahub.data.ai.tools.AgentColor
import java.util.concurrent.ConcurrentHashMap

/**
 * 队友协程管理器。
 * 对应泄露版 spawnMultiAgent.ts + InProcessTeammateTask。
 *
 * Android 上无法 spawn 子进程，改用协程模拟并行队友：
 * - 每个队友是一个独立协程，运行带工具的 LLM 调用
 * - 通过 AgentMailbox 收发消息
 * - 通过 TeammateRunner 管理生命周期
 */
class TeammateRunner(private val scope: CoroutineScope) {

    private val teammates = ConcurrentHashMap<String, TeammateState>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val counter = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * 创建一个队友协程。
     * @param name 队友名称（用于 send_message 寻址）
     * @param teamName 团队名称
     * @param prompt 任务描述
     * @param model 可选模型 override
     * @param planModeRequired 是否需要 plan 审批
     * @param executeBlock 实际执行体：suspend (agentName, prompt) -> String
     * @return 队友 agentId
     */
    fun spawn(
        name: String,
        teamName: String = "default",
        prompt: String,
        model: String? = null,
        planModeRequired: Boolean = false,
        executeBlock: suspend (agentName: String, prompt: String) -> String,
    ): String {
        val id = "teammate-${counter.incrementAndGet()}-${System.currentTimeMillis() % 10000}"
        val color = AgentColor.entries[counter.get() % AgentColor.entries.size]
        val identity = TeammateIdentity(
            agentId = id,
            agentName = name,
            teamName = teamName,
            color = color,
            planModeRequired = planModeRequired,
        )
        val state = TeammateState(
            identity = identity,
            status = TeammateStatus.RUNNING,
            prompt = prompt,
            model = model,
        )
        teammates[id] = state
        updateState()

        val job = scope.launch(Dispatchers.IO) {
            try {
                val result = executeBlock(name, prompt)
                teammates[id] = state.copy(
                    status = TeammateStatus.COMPLETED,
                    result = result,
                    isIdle = true,
                )
                // 通知主 agent 队友完成
                AgentMailbox.send(
                    to = "team-lead",
                    from = name,
                    message = result,
                    summary = "Teammate $name completed",
                )
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                teammates[id] = state.copy(status = TeammateStatus.FAILED, error = "Timeout")
            } catch (e: Exception) {
                teammates[id] = state.copy(
                    status = TeammateStatus.FAILED,
                    error = e.message ?: "Unknown error",
                    isIdle = true,
                )
            } finally {
                updateState()
                jobs.remove(id)
            }
        }
        jobs[id] = job
        return id
    }

    /** 杀掉队友 */
    fun kill(agentId: String) {
        val state = teammates[agentId] ?: return
        teammates[agentId] = state.copy(status = TeammateStatus.CANCELLED, isIdle = true)
        jobs[agentId]?.cancel()
        jobs.remove(agentId)
        updateState()
    }

    fun get(agentId: String): TeammateState? = teammates[agentId]

    fun getByName(name: String): TeammateState? {
        return teammates.values.find { it.identity.agentName == name }
    }

    fun list(): List<TeammateState> = teammates.values.toList()

    fun listRunning(): List<TeammateState> = teammates.values.filter { it.status == TeammateStatus.RUNNING }

    fun isRunning(agentId: String): Boolean = teammates[agentId]?.status == TeammateStatus.RUNNING

    fun killAll() {
        teammates.keys.toList().forEach { kill(it) }
    }

    /** 清理已完成的队友 */
    fun cleanup() {
        val toRemove = teammates.values
            .filter { it.status == TeammateStatus.COMPLETED || it.status == TeammateStatus.FAILED || it.status == TeammateStatus.CANCELLED }
            .map { it.identity.agentId }
        toRemove.forEach { teammates.remove(it) }
        updateState()
    }

    private fun updateState() {
        // 用于 UI 观察（预留）
    }
}
