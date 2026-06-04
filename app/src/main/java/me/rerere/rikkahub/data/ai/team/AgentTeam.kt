package me.rerere.rikkahub.data.ai.team

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * s15-s17: Agent Teams + Protocols + Autonomous Kanban。
 *
 * 对标 learn-claude-code s15_agent_teams / s16_team_protocols / s17_autonomous_agents：
 *
 * s15:
 * - MessageBus: 文件收件箱 (.mailboxes/*.jsonl)
 * - spawnTeammate: 创建队友线程
 * - Teammate 运行简化 agent loop (bash/read/write/send_message)
 * - Lead 工具: spawn_teammate, send_message, check_inbox
 *
 * s16:
 * - ProtocolState: 请求-响应状态机 (pending → approved/rejected)
 * - dispatchMessage: 按消息类型路由到处理器
 * - matchResponse: 通过 request_id 关联回复与请求
 * - shutdown_request / plan_approval_request 协议
 *
 * s17:
 * - idlePoll: 空闲时每5秒轮询
 * - scanUnclaimedTasks: 扫描可认领任务
 * - autoClaim: 自动认领
 */
private const val TAG = "AgentTeam"

// ── MessageBus (s15) ──

object MessageBus {
    private val mailboxesDir: File by lazy {
        File(System.getProperty("java.io.tmpdir"), ".rikkahub-mailboxes").also { it.mkdirs() }
    }

    private val inboxMemory = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()

    /**
     * 发送消息到指定队友的收件箱。
     */
    fun send(toAgent: String, message: String) {
        val mailbox = inboxMemory.getOrPut(toAgent) { CopyOnWriteArrayList() }
        mailbox.add(message)

        // 也写入文件
        val file = mailboxFile(toAgent)
        file.parentFile?.mkdirs()
        file.appendText("$message\n")
        Log.d(TAG, "Message sent to '$toAgent': ${message.take(100)}")
    }

    /**
     * 读取并清空收件箱。
     */
    fun readInbox(agentName: String): List<String> {
        val mailbox = inboxMemory[agentName] ?: return emptyList()
        val messages = mailbox.toList()
        mailbox.clear()
        return messages
    }

    /**
     * 检查是否有未读消息。
     */
    fun hasMessages(agentName: String): Boolean {
        return inboxMemory[agentName]?.isNotEmpty() == true
    }

    private fun mailboxFile(agentName: String): File {
        return File(mailboxesDir, "${agentName.replace(" ", "_")}.jsonl")
    }

    fun clear() {
        inboxMemory.clear()
        mailboxesDir.deleteRecursively()
    }
}

// ── Protocol (s16) ──

data class ProtocolState(
    val requestId: String,
    val type: String,          // "shutdown" | "plan_approval"
    val sender: String,
    val recipient: String,
    val status: String,        // "pending" | "approved" | "rejected"
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

object ProtocolManager {
    private val pendingRequests = ConcurrentHashMap<String, ProtocolState>()
    private val counter = AtomicInteger(0)

    fun createRequest(type: String, sender: String, recipient: String, content: String = ""): ProtocolState {
        val id = "req_${counter.incrementAndGet()}_${Random.nextInt(10000)}"
        val request = ProtocolState(
            requestId = id, type = type, sender = sender,
            recipient = recipient, status = "pending", content = content,
        )
        pendingRequests[id] = request
        return request
    }

    fun getRequest(requestId: String): ProtocolState? = pendingRequests[requestId]

    fun respondToRequest(requestId: String, approved: Boolean): ProtocolState? {
        val req = pendingRequests[requestId] ?: return null
        val newStatus = if (approved) "approved" else "rejected"
        val updated = req.copy(status = newStatus)
        pendingRequests[requestId] = updated
        return updated
    }

    fun getPendingFor(agentName: String): List<ProtocolState> {
        return pendingRequests.values.filter { it.recipient == agentName && it.status == "pending" }
    }

    fun getMyRequests(agentName: String): List<ProtocolState> {
        return pendingRequests.values.filter { it.sender == agentName }
    }
}

// ── Kanban / Autonomous (s17) ──

data class KanbanTask(
    val id: String,
    val subject: String,
    val description: String = "",
    val status: String = "pending",   // pending | in_progress | completed
    val owner: String? = null,
    val blockedBy: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

object KanbanBoard {
    private val tasks = ConcurrentHashMap<String, KanbanTask>()
    private val counter = AtomicInteger(0)

    fun createTask(subject: String, description: String = "", blockedBy: List<String> = emptyList()): KanbanTask {
        val id = "kanban_${counter.incrementAndGet()}"
        val task = KanbanTask(id = id, subject = subject, description = description, blockedBy = blockedBy)
        tasks[id] = task
        return task
    }

    fun listTasks(): List<KanbanTask> = tasks.values.toList().sortedBy { it.createdAt }

    fun getUnclaimedTasks(): List<KanbanTask> {
        return tasks.values.filter { it.status == "pending" && it.owner == null }
    }

    fun claimTask(taskId: String, owner: String): String? {
        val task = tasks[taskId] ?: return "Task $taskId not found"
        if (task.status != "pending") return "Task $taskId is ${task.status}"
        if (task.owner != null) return "Task $taskId already claimed by ${task.owner}"

        // Check dependencies
        for (dep in task.blockedBy) {
            val depTask = tasks[dep]
            if (depTask == null || depTask.status != "completed") {
                return "Task $taskId blocked by $dep (${depTask?.status ?: "not found"})"
            }
        }

        tasks[taskId] = task.copy(status = "in_progress", owner = owner)
        return null
    }

    fun completeTask(taskId: String): String? {
        val task = tasks[taskId] ?: return "Task $taskId not found"
        if (task.status != "in_progress") return "Task $taskId is ${task.status}"
        tasks[taskId] = task.copy(status = "completed")

        // Check newly unblocked tasks
        val unblocked = tasks.values.filter {
            it.status == "pending" && it.owner == null &&
                    it.blockedBy.all { dep -> tasks[dep]?.status == "completed" }
        }
        val msg = buildString {
            append("Completed ${task.subject}")
            if (unblocked.isNotEmpty()) {
                append(". Unblocked: ")
                append(unblocked.joinToString(", ") { it.subject })
            }
        }
        return msg
    }
}
