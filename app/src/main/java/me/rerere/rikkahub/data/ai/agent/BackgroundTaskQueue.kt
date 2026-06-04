package me.rerere.rikkahub.data.ai.agent

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * s13: Background Task Queue — 后台任务调度、轮询、结果注入。
 *
 * 对标 learn-claude-code s13_background_tasks：
 * - startBackgroundTask：后台线程派发 bash 命令
 * - collectBackgroundResults：轮询完成结果，注入为 task_notification
 * - 线程安全（ConcurrentHashMap + ConcurrentLinkedQueue）
 */
private const val TAG = "BackgroundTaskQueue"

data class BackgroundTask(
    val id: String,
    val toolName: String,
    val command: String,
    val status: BgStatus = BgStatus.RUNNING,
)

enum class BgStatus { RUNNING, COMPLETED, FAILED }

object BackgroundTaskQueue {
    private val counter = AtomicInteger(0)

    private val tasks = ConcurrentHashMap<String, BackgroundTask>()
    private val results = ConcurrentHashMap<String, String>()

    /**
     * 派发后台任务。返回 bg_id，结果通过 collectCompleted() 获取。
     */
    fun start(toolName: String, command: String, executor: () -> String): String {
        val id = "bg_${counter.incrementAndGet()}"
        tasks[id] = BackgroundTask(id = id, toolName = toolName, command = command, status = BgStatus.RUNNING)

        Thread {
            try {
                val output = executor()
                tasks[id] = tasks[id]!!.copy(status = BgStatus.COMPLETED)
                results[id] = output
                Log.i(TAG, "[background done] $id: ${command.take(60)} (${output.length} chars)")
            } catch (e: Exception) {
                tasks[id] = tasks[id]!!.copy(status = BgStatus.FAILED)
                results[id] = "Error: ${e.message}"
                Log.w(TAG, "[background failed] $id: ${e.message}")
            }
        }.apply { isDaemon = true }.start()

        Log.i(TAG, "[background] dispatched $id: ${command.take(60)}")
        return id
    }

    /**
     * 收集已完成的背景任务结果，以 task_notification 格式返回。
     * 对标参考 collect_background_results()。
     */
    fun collectCompleted(): List<String> {
        val readyIds = tasks.entries.filter { it.value.status == BgStatus.COMPLETED || it.value.status == BgStatus.FAILED }
            .map { it.key }
        if (readyIds.isEmpty()) return emptyList()

        val notifications = mutableListOf<String>()
        for (id in readyIds) {
            val task = tasks.remove(id) ?: continue
            val output = results.remove(id) ?: ""
            val summary = if (output.length > 200) output.take(200) + "..." else output
            notifications.add(buildString {
                appendLine("<task_notification>")
                appendLine("  <task_id>$id</task_id>")
                appendLine("  <status>${task.status.name.lowercase()}</status>")
                appendLine("  <command>${task.command}</command>")
                appendLine("  <summary>$summary</summary>")
                append("</task_notification>")
            })
        }
        return notifications
    }

    /** 检查是否有正在运行的后台任务 */
    fun hasRunning(): Boolean = tasks.values.any { it.status == BgStatus.RUNNING }

    /** 获取指定后台任务的状态 */
    fun getStatus(id: String): BackgroundTask? = tasks[id]

    /** 清理所有已完成/失败的任务记录（不包含正在运行的） */
    fun cleanup() {
        val toRemove = tasks.entries.filter {
            it.value.status == BgStatus.COMPLETED || it.value.status == BgStatus.FAILED
        }.map { it.key }
        toRemove.forEach { tasks.remove(it); results.remove(it) }
    }

    /** 清空所有 */
    fun clear() {
        tasks.clear()
        results.clear()
    }
}
