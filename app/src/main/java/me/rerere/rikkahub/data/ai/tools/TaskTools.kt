package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.util.concurrent.ConcurrentHashMap

// ============================================================
// 数据模型
// ============================================================

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

// ============================================================
// 管理器（进程内共享）
// ============================================================

object TaskManager {
    private val tasks = ConcurrentHashMap<String, Task>()
    val teams = ConcurrentHashMap<String, Team>()
    @Volatile
    var activeTeam: String? = null

    private var counter = 0

    fun createTask(
        subject: String,
        description: String = "",
        dependsOn: List<String> = emptyList(),
    ): Task {
        val id = "task-${++counter}"
        val task = Task(id = id, subject = subject, description = description, dependsOn = dependsOn)
        tasks[id] = task
        return task
    }

    fun getTask(id: String): Task? = tasks[id]

    fun listTasks(): List<Task> = tasks.values.toList().sortedBy { it.createdAt }

    fun updateTask(
        id: String,
        status: TaskStatus? = null,
        owner: String? = null,
        description: String? = null,
    ): Task? {
        val t = tasks[id] ?: return null
        tasks[id] = t.copy(
            status = status ?: t.status,
            owner = owner ?: t.owner,
            description = description ?: t.description,
        )
        return tasks[id]
    }

    fun stopTask(id: String): Task? = updateTask(id, TaskStatus.CANCELLED)

    fun taskOutput(id: String): String? {
        val t = tasks[id] ?: return null
        return buildString {
            appendLine("[${t.id}] ${t.subject}")
            appendLine("状态: ${t.status}")
            if (t.owner != null) appendLine("负责人: ${t.owner}")
            if (t.description.isNotBlank()) appendLine("描述: ${t.description}")
            if (t.dependsOn.isNotEmpty()) appendLine("依赖: ${t.dependsOn.joinToString(", ")}")
        }
    }

    fun createTeam(name: String, description: String = ""): Team {
        val team = Team(name = name, description = description)
        teams[name] = team
        activeTeam = name
        return team
    }

    fun deleteTeam(name: String) {
        teams.remove(name)
        if (activeTeam == name) activeTeam = null
    }

    // ============================================================
    // 消息系统 (Agent间通信)
    // ============================================================

    data class Message(
        val id: String,
        val from: String,
        val to: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
    )

    private val messages = java.util.concurrent.ConcurrentLinkedQueue<Message>()
    private val msgCounter = java.util.concurrent.atomic.AtomicInteger(0)

    fun sendMessage(from: String, to: String, content: String): Message {
        val msg = Message(
            id = "msg-${msgCounter.incrementAndGet()}",
            from = from,
            to = to,
            content = content,
        )
        messages.add(msg)
        return msg
    }

    fun readMessages(agentName: String): List<Message> {
        return messages.filter { it.to == agentName || it.to == "*" }
    }

    fun clearMessages(agentName: String) {
        messages.removeAll { it.to == agentName }
    }

    fun listPendingMessages(): List<Message> {
        return messages.toList()
    }
}

// ============================================================
// 工具创建
// ============================================================

fun createTaskTools(): List<Tool> = listOf(

    // ── TaskCreate ──
    Tool(
        name = "task_create",
        description = """
            Create a new task in the task list. Tasks track progress and can be assigned to agents.
            Use for: complex multi-step tasks, tracking progress, organizing work.
            Create tasks with clear, specific subjects. Include enough detail for another agent to understand.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("subject", buildJsonObject {
                        put("type", "string")
                        put("description", "Brief actionable title in imperative form (e.g. 'Fix login bug')")
                    })
                    put("description", buildJsonObject {
                        put("type", "string")
                        put("description", "What needs to be done, in detail")
                    })
                    put("depends_on", buildJsonObject {
                        put("type", "string")
                        put("description", "Comma-separated task IDs this depends on (e.g. 'task-1,task-2')")
                    })
                },
                required = listOf("subject"),
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val subject = obj["subject"]?.jsonPrimitive?.contentOrNull ?: error("subject required")
            val description = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
            val dependsOn = obj["depends_on"]?.jsonPrimitive?.contentOrNull
                ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            val task = TaskManager.createTask(subject, description, dependsOn)
            listOf(UIMessagePart.Text("[${task.id}] 已创建: ${task.subject}"))
        },
    ),

    // ── TaskGet ──
    Tool(
        name = "task_get",
        description = "Get details of a specific task by ID.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("id", buildJsonObject {
                        put("type", "string")
                        put("description", "Task ID (e.g. task-1)")
                    })
                },
                required = listOf("id"),
            )
        },
        execute = { args ->
            val id = args.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: error("id required")
            val output = TaskManager.taskOutput(id) ?: error("Task $id not found")
            listOf(UIMessagePart.Text(output))
        },
    ),

    // ── TaskList ──
    Tool(
        name = "task_list",
        description = "List all tasks, optionally filtered by status.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("status", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("pending"); add("in_progress"); add("done"); add("failed"); add("cancelled")
                        })
                        put("description", "Optional: filter by status")
                    })
                },
                required = emptyList(),
            )
        },
        execute = { args ->
            val filterStatus = args.jsonObject["status"]?.jsonPrimitive?.contentOrNull
            val all = TaskManager.listTasks()
            val filtered = if (filterStatus != null) {
                all.filter { it.status.name.lowercase() == filterStatus }
            } else all
            if (filtered.isEmpty()) {
                listOf(UIMessagePart.Text("(没有任务)"))
            } else {
                val output = filtered.joinToString("\n") { t ->
                    val icon = when (t.status) {
                        TaskStatus.PENDING -> "⏳"
                        TaskStatus.IN_PROGRESS -> "🔄"
                        TaskStatus.DONE -> "✅"
                        TaskStatus.FAILED -> "❌"
                        TaskStatus.CANCELLED -> "🚫"
                    }
                    val owner = if (t.owner != null) " [${t.owner}]" else ""
                    val deps = if (t.dependsOn.isNotEmpty()) " (依赖: ${t.dependsOn.joinToString(",")})" else ""
                    "$icon ${t.id}: ${t.subject}$owner$deps"
                }
                listOf(UIMessagePart.Text(output))
            }
        },
    ),

    // ── TaskUpdate ──
    Tool(
        name = "task_update",
        description = "Update a task's status, owner, or description.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("id", buildJsonObject {
                        put("type", "string")
                        put("description", "Task ID")
                    })
                    put("status", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("pending"); add("in_progress"); add("done"); add("failed"); add("cancelled")
                        })
                        put("description", "New status")
                    })
                    put("owner", buildJsonObject {
                        put("type", "string")
                        put("description", "Assign to an agent (use agent name)")
                    })
                    put("description", buildJsonObject {
                        put("type", "string")
                        put("description", "Updated description / notes")
                    })
                },
                required = listOf("id"),
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: error("id required")
            val status = obj["status"]?.jsonPrimitive?.contentOrNull
            val owner = obj["owner"]?.jsonPrimitive?.contentOrNull
            val description = obj["description"]?.jsonPrimitive?.contentOrNull
            val task = TaskManager.updateTask(
                id = id,
                status = status?.let { TaskStatus.valueOf(it.uppercase()) },
                owner = owner,
                description = description,
            ) ?: error("Task $id not found")
            listOf(UIMessagePart.Text("[${task.id}] 已更新: ${task.status.name}"))
        },
    ),

    // ── TaskStop ──
    Tool(
        name = "task_stop",
        description = "Cancel/stop a task by ID.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("id", buildJsonObject {
                        put("type", "string")
                        put("description", "Task ID to stop")
                    })
                },
                required = listOf("id"),
            )
        },
        execute = { args ->
            val id = args.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: error("id required")
            val task = TaskManager.stopTask(id) ?: error("Task $id not found")
            listOf(UIMessagePart.Text("[${task.id}] 已停止"))
        },
    ),

    // ── TaskOutput ──
    Tool(
        name = "task_output",
        description = "Get the result/output of a completed task.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("id", buildJsonObject {
                        put("type", "string")
                        put("description", "Task ID")
                    })
                },
                required = listOf("id"),
            )
        },
        execute = { args ->
            val id = args.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: error("id required")
            val output = TaskManager.taskOutput(id) ?: error("Task $id not found")
            listOf(UIMessagePart.Text(output))
        },
    ),

    // ── TodoWrite ──
    Tool(
        name = "todo_write",
        description = """
            Create and manage a lightweight task list. Use for tracking progress.
            When a task requires 3+ steps, create a todo list. Mark items as in_progress BEFORE starting.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("todos", buildJsonObject {
                        put("type", "array")
                        put("description", "List of todo items")
                        put("items", buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {
                                put("subject", buildJsonObject {
                                    put("type", "string")
                                    put("description", "What to do")
                                })
                                put("status", buildJsonObject {
                                    put("type", "string")
                                    put("enum", buildJsonArray { add("pending"); add("in_progress"); add("done") })
                                })
                            })
                            put("required", listOf("subject"))
                        })
                    })
                },
                required = listOf("todos"),
            )
        },
        execute = { args ->
            val todos = args.jsonObject["todos"]?.jsonArray ?: error("todos required")
            val results = todos.map { item ->
                val obj = item.jsonObject
                val subject = obj["subject"]?.jsonPrimitive?.contentOrNull ?: ""
                val status = obj["status"]?.jsonPrimitive?.contentOrNull ?: "pending"
                val icon = when (status) { "done" -> "✅"; "in_progress" -> "🔄"; else -> "⏳" }
                TaskManager.createTask(subject, status = TaskStatus.valueOf(status.uppercase()))
                "$icon $subject"
            }
            listOf(UIMessagePart.Text(results.joinToString("\n")))
        },
    ),

    // ── TeamCreate ──
    Tool(
        name = "team_create",
        description = "Create a team to coordinate multiple agents. Teams have their own task list.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("name", buildJsonObject {
                        put("type", "string")
                        put("description", "Team name (e.g. 'feature-build')")
                    })
                    put("description", buildJsonObject {
                        put("type", "string")
                        put("description", "What this team is working on")
                    })
                },
                required = listOf("name"),
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: error("name required")
            val description = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
            TaskManager.createTeam(name, description)
            listOf(UIMessagePart.Text("团队 '$name' 已创建"))
        },
    ),

    // ── TeamDelete ──
    Tool(
        name = "team_delete",
        description = "Delete a team and clean up its task list.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("name", buildJsonObject {
                        put("type", "string")
                        put("description", "Team name to delete")
                    })
                },
                required = listOf("name"),
            )
        },
        execute = { args ->
            val name = args.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: error("name required")
            TaskManager.deleteTeam(name)
            listOf(UIMessagePart.Text("团队 '$name' 已删除"))
        },
    ),

    // ── send_message ──
    Tool(
        name = "send_message",
        description = "Send a message to another agent. Messages are stored and can be read by the target agent. Use this to coordinate with other agents.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("to", buildJsonObject {
                        put("type", "string")
                        put("description", "Target agent name, or '*' for broadcast")
                    })
                    put("message", buildJsonObject {
                        put("type", "string")
                        put("description", "Message content")
                    })
                    put("from", buildJsonObject {
                        put("type", "string")
                        put("description", "Sender agent name")
                    })
                },
                required = listOf("to", "message"),
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val to = obj["to"]?.jsonPrimitive?.contentOrNull ?: error("to required")
            val msg = obj["message"]?.jsonPrimitive?.contentOrNull ?: error("message required")
            val from = obj["from"]?.jsonPrimitive?.contentOrNull ?: "main_agent"
            val message = TaskManager.sendMessage(from, to, msg)
            listOf(UIMessagePart.Text("[${message.id}] $from → $to: ${message.content.take(100)}"))
        },
    ),

    // ── read_messages ──
    Tool(
        name = "read_messages",
        description = "Read all messages sent to you for this agent. Clears inbox after reading.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("agent_name", buildJsonObject {
                        put("type", "string")
                        put("description", "Your agent name")
                    })
                },
                required = listOf("agent_name"),
            )
        },
        execute = { args ->
            val agentName = args.jsonObject["agent_name"]?.jsonPrimitive?.contentOrNull ?: error("agent_name required")
            val msgs = TaskManager.readMessages(agentName)
            TaskManager.clearMessages(agentName)
            if (msgs.isEmpty()) {
                listOf(UIMessagePart.Text("(no messages)"))
            } else {
                val output = msgs.joinToString("\n") { m ->
                    "[${m.id}] ${m.from} -> ${m.to}: ${m.content}"
                }
                listOf(UIMessagePart.Text(output))
            }
        },
    ),
)
