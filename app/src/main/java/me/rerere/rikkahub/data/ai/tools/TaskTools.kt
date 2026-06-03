package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.PermissionMode
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class TaskStatus {
    PENDING, IN_PROGRESS, DONE, FAILED, CANCELLED
}

data class Task(
    val id: String,
    val subject: String,
    val description: String = "",
    val status: TaskStatus = TaskStatus.PENDING,
    val owner: String? = null,
    val dependsOn: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

data class Team(
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

object TaskManager {
    private val tasks = ConcurrentHashMap<String, Task>()
    val teams = ConcurrentHashMap<String, Team>()
    @Volatile
    var activeTeam: String? = null

    private var counter = 0

    fun createTask(subject: String, description: String = "", dependsOn: List<String> = emptyList()): Task {
        val id = "task-${++counter}"
        val task = Task(id = id, subject = subject, description = description, dependsOn = dependsOn)
        tasks[id] = task
        return task
    }

    fun restoreTask(id: String, subject: String, description: String, status: String, dependsOn: List<String>) {
        if (!tasks.containsKey(id)) {
            val task = Task(
                id = id, subject = subject, description = description,
                status = try { TaskStatus.valueOf(status) } catch (_: Exception) { TaskStatus.PENDING },
                dependsOn = dependsOn,
            )
            tasks[id] = task
        }
    }

    fun getTask(id: String): Task? = tasks[id]
    fun listTasks(): List<Task> = tasks.values.toList().sortedBy { it.createdAt }

    fun updateTask(id: String, status: TaskStatus? = null, owner: String? = null,
                   description: String? = null, dependsOn: List<String>? = null): Task? {
        val t = tasks[id] ?: return null
        tasks[id] = t.copy(
            status = status ?: t.status,
            owner = owner ?: t.owner,
            description = description ?: t.description,
            dependsOn = dependsOn ?: t.dependsOn,
        )
        return tasks[id]
    }

    fun stopTask(id: String): Task? = updateTask(id, TaskStatus.CANCELLED)

    fun taskOutput(id: String): String? {
        val t = tasks[id] ?: return null
        return buildString {
            appendLine("[${t.id}] ${t.subject}")
            appendLine("Status: ${t.status}")
            if (t.owner != null) appendLine("Owner: ${t.owner}")
            if (t.description.isNotBlank()) appendLine("Description: ${t.description}")
            if (t.dependsOn.isNotEmpty()) appendLine("Depends on: ${t.dependsOn.joinToString(", ")}")
        }
    }

    fun createTeam(name: String, description: String = ""): Team {
        val team = Team(name = name, description = description)
        teams[name] = team; activeTeam = name
        return team
    }

    fun deleteTeam(name: String) { teams.remove(name); if (activeTeam == name) activeTeam = null }

    // Fork management
    data class ForkInfo(val name: String, val goal: String, val status: ForkStatus = ForkStatus.RUNNING, val result: String = "")
    enum class ForkStatus { RUNNING, DONE, FAILED }

    private val forks = ConcurrentHashMap<String, ForkInfo>()
    private val forkNotifications = java.util.concurrent.ConcurrentLinkedQueue<String>()

    fun registerFork(name: String, goal: String): Boolean {
        if (forks.containsKey(name)) return false
        forks[name] = ForkInfo(name = name, goal = goal); return true
    }

    fun completeFork(name: String, result: String) {
        forks[name] = forks[name]?.copy(status = ForkStatus.DONE, result = result)
        forkNotifications.add(name)
    }

    fun failFork(name: String, error: String) {
        forks[name] = forks[name]?.copy(status = ForkStatus.FAILED, result = error)
        forkNotifications.add(name)
    }

    fun getForkStatus(name: String): ForkInfo? = forks[name]
    fun listForks(): List<ForkInfo> = forks.values.toList()

    fun consumeForkNotifications(): List<ForkInfo> {
        val names = mutableListOf<String>()
        while (true) { val name = forkNotifications.poll() ?: break; names.add(name) }
        return names.mapNotNull { forks[it] }
    }

    // Agent message system
    data class Message(val id: String, val from: String, val to: String, val content: String, val timestamp: Long = System.currentTimeMillis())
    private val messages = java.util.concurrent.ConcurrentLinkedQueue<Message>()
    private val msgCounter = java.util.concurrent.atomic.AtomicInteger(0)

    fun sendMessage(from: String, to: String, content: String): Message {
        val msg = Message(id = "msg-${msgCounter.incrementAndGet()}", from = from, to = to, content = content)
        messages.add(msg); return msg
    }

    fun readMessages(agentName: String): List<Message> = messages.filter { it.to == agentName || it.to == "*" }
    fun clearMessages(agentName: String) { messages.removeAll { it.to == agentName } }
}

fun createTaskTools(): List<Tool> = listOf(
    Tool(name = "task_create", description = "Create a new task for tracking. Use for complex multi-step tasks.",
        parameters = {{
            InputSchema.Obj(properties = buildJsonObject {
                put("subject", buildJsonObject { put("type", "string"); put("description", "Task title") })
                put("description", buildJsonObject { put("type", "string"); put("description", "Details") })
                put("depends_on", buildJsonObject { put("type", "string"); put("description", "Comma-separated task IDs this depends on") })
            }, required = listOf("subject"))
        }},
        execute = { args ->
            val obj = args.jsonObject; val subject = obj["subject"]?.jsonPrimitive?.contentOrNull ?: error("subject required")
            val desc = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
            val deps = obj["depends_on"]?.jsonPrimitive?.contentOrNull?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            val task = TaskManager.createTask(subject, desc, deps)
            listOf(UIMessagePart.Text("[${task.id}] created: ${task.subject}"))
        },
    ),
    Tool(name = "task_get", description = "Get details of a task by ID.", permissionMode = PermissionMode.READ_ONLY,
        parameters = {{
            InputSchema.Obj(properties = buildJsonObject {
                put("id", buildJsonObject { put("type", "string"); put("description", "Task ID") })
            }, required = listOf("id"))
        }},
        execute = { args ->
            val id = args.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: error("id required")
            listOf(UIMessagePart.Text(TaskManager.taskOutput(id) ?: error("Task $id not found")))
        },
    ),
    Tool(name = "task_list", description = "List all tasks, optionally filtered.", permissionMode = PermissionMode.READ_ONLY,
        parameters = {{
            InputSchema.Obj(properties = buildJsonObject {
                put("status", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add("pending"); add("in_progress"); add("done"); add("failed"); add("cancelled") }) })
                put("owner", buildJsonObject { put("type", "string"); put("description", "Filter by owner") })
            })
        }},
        execute = { args ->
            val fs = args.jsonObject["status"]?.jsonPrimitive?.contentOrNull
            val fo = args.jsonObject["owner"]?.jsonPrimitive?.contentOrNull
            val filtered = TaskManager.listTasks().filter { t ->
                (fs == null || t.status.name.lowercase() == fs) &&
                (fo == null || t.owner == fo)
            }
            if (filtered.isEmpty()) return@Tool listOf(UIMessagePart.Text("(no tasks)"))
            listOf(UIMessagePart.Text(filtered.joinToString("\n") { t ->
                val icon = when (t.status) { TaskStatus.DONE -> "✅"; TaskStatus.IN_PROGRESS -> "🔄"; TaskStatus.FAILED -> "❌"; TaskStatus.CANCELLED -> "🚫"; else -> "⏳" }
                "$icon ${t.id}: ${t.subject}${if (t.owner != null) " [${t.owner}]" else ""}"
            }))
        },
    ),
    Tool(name = "task_update", description = "Update a task status, owner, or dependencies.",
        parameters = {{
            InputSchema.Obj(properties = buildJsonObject {
                put("id", buildJsonObject { put("type", "string"); put("description", "Task ID") })
                put("status", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add("pending"); add("in_progress"); add("done"); add("failed"); add("cancelled") }) })
                put("owner", buildJsonObject { put("type", "string"); put("description", "Assign to agent") })
                put("description", buildJsonObject { put("type", "string") })
                put("depends_on", buildJsonObject { put("type", "string"); put("description", "Comma-separated task IDs") })
            }, required = listOf("id"))
        }},
        execute = { args ->
            val obj = args.jsonObject; val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: error("id required")
            val task = TaskManager.updateTask(id = id,
                status = obj["status"]?.jsonPrimitive?.contentOrNull?.let { TaskStatus.valueOf(it.uppercase()) },
                owner = obj["owner"]?.jsonPrimitive?.contentOrNull,
                description = obj["description"]?.jsonPrimitive?.contentOrNull,
                dependsOn = obj["depends_on"]?.jsonPrimitive?.contentOrNull?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
            ) ?: error("Task $id not found")
            listOf(UIMessagePart.Text("[${task.id}] updated: ${task.status.name}"))
        },
    ),
    Tool(name = "task_stop", description = "Cancel a task.",
        execute = { args ->
            val id = args.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: error("id required")
            TaskManager.stopTask(id) ?: error("Task $id not found")
            listOf(UIMessagePart.Text("Task $id stopped"))
        },
    ),
    Tool(name = "task_output", description = "Get task result/output.", permissionMode = PermissionMode.READ_ONLY,
        execute = { args ->
            val id = args.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: error("id required")
            listOf(UIMessagePart.Text(TaskManager.taskOutput(id) ?: error("Task $id not found")))
        },
    ),
    Tool(name = "todo_write", description = "Create a lightweight todo list.",
        parameters = {{
            InputSchema.Obj(properties = buildJsonObject {
                put("todos", buildJsonObject {
                    put("type", "array"); put("description", "Todo items")
                    put("items", buildJsonObject {
                        put("type", "object"); put("properties", buildJsonObject {
                            put("subject", buildJsonObject { put("type", "string") })
                            put("status", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add("pending"); add("in_progress"); add("done") }) })
                        }); put("required", listOf("subject"))
                    })
                })
            }, required = listOf("todos"))
        }},
        execute = { args ->
            val todos = args.jsonObject["todos"]?.jsonArray ?: error("todos required")
            val results = todos.map { item ->
                val obj = item.jsonObject; val s = obj["subject"]?.jsonPrimitive?.contentOrNull ?: ""
                val st = obj["status"]?.jsonPrimitive?.contentOrNull ?: "pending"
                TaskManager.createTask(s, status = TaskStatus.valueOf(st.uppercase()))
                "${when (st) { "done" -> "✅"; "in_progress" -> "🔄"; else -> "⏳" }} $s"
            }
            listOf(UIMessagePart.Text(results.joinToString("\n")))
        },
    ),
    Tool(name = "team_create", description = "Create a team for coordinating agents.",
        execute = { args ->
            val obj = args.jsonObject; val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: error("name required")
            TaskManager.createTeam(name, obj["description"]?.jsonPrimitive?.contentOrNull ?: "")
            listOf(UIMessagePart.Text("Team '$name' created"))
        },
    ),
    Tool(name = "team_delete", description = "Delete a team.",
        execute = { args ->
            val name = args.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: error("name required")
            TaskManager.deleteTeam(name); listOf(UIMessagePart.Text("Team '$name' deleted"))
        },
    ),
    Tool(name = "send_message", description = "Send a message to another agent.",
        execute = { args ->
            val obj = args.jsonObject; val to = obj["to"]?.jsonPrimitive?.contentOrNull ?: error("to required")
            val msg = obj["message"]?.jsonPrimitive?.contentOrNull ?: error("message required")
            val from = obj["from"]?.jsonPrimitive?.contentOrNull ?: "main_agent"
            val m = TaskManager.sendMessage(from, to, msg)
            listOf(UIMessagePart.Text("[${m.id}] $from -> $to"))
        },
    ),
    Tool(name = "read_messages", description = "Read messages for your agent. Clears inbox.", permissionMode = PermissionMode.READ_ONLY,
        execute = { args ->
            val name = args.jsonObject["agent_name"]?.jsonPrimitive?.contentOrNull ?: error("agent_name required")
            val msgs = TaskManager.readMessages(name); TaskManager.clearMessages(name)
            if (msgs.isEmpty()) listOf(UIMessagePart.Text("(no messages)"))
            else listOf(UIMessagePart.Text(msgs.joinToString("\n") { m -> "[${m.id}] ${m.from}: ${m.content.take(200)}" }))
        },
    ),

    // ── NEW: run_task_packet ──
    Tool(name = "run_task_packet", description = "Create a structured task with acceptance criteria, commit policy, and escalation rules.",
        permissionMode = PermissionMode.DANGER_FULL_ACCESS,
        parameters = {{
            InputSchema.Obj(properties = buildJsonObject {
                put("objective", buildJsonObject { put("type", "string"); put("description", "What to accomplish") })
                put("scope", buildJsonObject { put("type", "string"); put("description", "Module, file, or repo") })
                put("acceptance_tests", buildJsonObject { put("type", "string"); put("description", "Comma-separated test commands") })
                put("commit_policy", buildJsonObject { put("type", "string"); put("description", "single_commit | no_commit") })
                put("escalation_policy", buildJsonObject { put("type", "string"); put("description", "On-failure notification target") })
            }, required = listOf("objective"))
        }},
        execute = { args ->
            val obj = args.jsonObject
            val objective = obj["objective"]?.jsonPrimitive?.contentOrNull ?: error("objective required")
            val scope = obj["scope"]?.jsonPrimitive?.contentOrNull ?: ""
            val tests = obj["acceptance_tests"]?.jsonPrimitive?.contentOrNull ?: ""
            val commitPolicy = obj["commit_policy"]?.jsonPrimitive?.contentOrNull ?: "single_commit"
            val task = TaskManager.createTask(subject = objective,
                description = "Scope: $scope\nTests: $tests\nCommit: $commitPolicy")
            listOf(UIMessagePart.Text(buildJsonObject {
                put("task_id", task.id); put("status", "created")
                put("objective", objective); put("acceptance_tests", tests)
            }.toString()))
        },
    ),

    // ── NEW: task_dag ──
    Tool(name = "task_dag", description = "Show the task dependency graph. Displays blockers.",
        permissionMode = PermissionMode.READ_ONLY,
        execute = {
            val tasks = TaskManager.listTasks()
            val output = buildString {
                appendLine("Task Dependency Graph:")
                tasks.filter { it.dependsOn.isNotEmpty() }.forEach { t ->
                    appendLine("  ${t.id} [${t.status.name}] -> depends: ${t.dependsOn.joinToString(", ")}")
                }
                val blocked = tasks.filter { t -> t.dependsOn.any { depId ->
                    val dep = TaskManager.getTask(depId); dep != null && dep.status != TaskStatus.DONE
                }}
                if (blocked.isNotEmpty()) {
                    appendLine("\nBlocked tasks:")
                    blocked.forEach { appendLine("  ${it.id}: ${it.subject}") }
                }
            }
            listOf(UIMessagePart.Text(output.ifEmpty { "(no dependencies)" }))
        },
    ),
)
