package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.PermissionMode
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * s05: TodoWrite — AI 自主写计划、按步骤执行。
 *
 * 对标 learn-claude-code s05_todo_write：
 * - todo_write 工具：AI 创建/更新当前任务执行计划
 * - 计划保存在会话内存中，工具只做规划不做实际工作
 * - Nag reminder：连续多轮没更新计划时注入提醒
 *
 * 与 TaskSystem 区别：
 * - TodoWrite：当前会话的执行清单，进程内存储
 * - TaskSystem：跨会话持久化的任务 DAG
 */
data class TodoItem(
    val content: String,
    val status: String, // "pending" | "in_progress" | "completed"
)

object PlanManager {
    private val currentTodos = CopyOnWriteArrayList<TodoItem>()
    private val roundsSinceTodoUpdate = AtomicInteger(0)
    private const val NAG_THRESHOLD = 3

    fun updateTodos(todos: List<TodoItem>): String {
        currentTodos.clear()
        currentTodos.addAll(todos)
        roundsSinceTodoUpdate.set(0)
        return formatTodos()
    }

    fun getTodos(): List<TodoItem> = currentTodos.toList()

    fun getPlanSummary(): String {
        if (currentTodos.isEmpty()) return ""
        return buildString {
            appendLine("## Current Plan")
            currentTodos.forEachIndexed { i, item ->
                val icon = when (item.status) {
                    "completed" -> "✅"
                    "in_progress" -> "▶"
                    else -> "⬜"
                }
                appendLine("$icon [$i] ${item.content} (${item.status})")
            }
        }
    }

    fun shouldNag(): Boolean {
        if (currentTodos.isEmpty()) return false
        val rounds = roundsSinceTodoUpdate.incrementAndGet()
        return rounds > NAG_THRESHOLD
    }

    fun resetNag() {
        roundsSinceTodoUpdate.set(0)
    }

    fun clear() {
        currentTodos.clear()
        roundsSinceTodoUpdate.set(0)
    }

    private fun formatTodos(): String {
        if (currentTodos.isEmpty()) return "Plan cleared (0 tasks)"
        return buildString {
            appendLine("Updated ${currentTodos.size} tasks:")
            currentTodos.forEach { item ->
                val icon = when (item.status) {
                    "completed" -> "✓"
                    "in_progress" -> "▸"
                    else -> " "
                }
                appendLine("  [$icon] ${item.content} [${item.status}]")
            }
        }
    }
}

fun buildTodoWriteTool(): Tool {
    return Tool(
        name = "todo_write",
        description = "Create and manage a task plan for the current session. " +
                "Before starting any multi-step task, use this to plan your steps. " +
                "Update status as you go (pending → in_progress → completed).",
        inputSchema = InputSchema(
            type = "object",
            properties = mapOf(
                "todos" to JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("array"),
                        "description" to JsonPrimitive("List of tasks with content and status"),
                        "items" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("object"),
                                "properties" to JsonObject(
                                    mapOf(
                                        "content" to JsonObject(
                                            mapOf("type" to JsonPrimitive("string"))
                                        ),
                                        "status" to JsonObject(
                                            mapOf(
                                                "type" to JsonPrimitive("string"),
                                                "enum" to JsonArray(
                                                    listOf(
                                                        JsonPrimitive("pending"),
                                                        JsonPrimitive("in_progress"),
                                                        JsonPrimitive("completed")
                                                    )
                                                )
                                            )
                                        )
                                    )
                                ),
                                "required" to JsonArray(
                                    listOf(JsonPrimitive("content"), JsonPrimitive("status"))
                                )
                            )
                        )
                    )
                )
            ),
            required = listOf("todos"),
        ),
        permissionMode = PermissionMode.AUTO,
    )
}

suspend fun handleTodoWrite(
    args: JsonElement,
): List<UIMessagePart> {
    val todosJson = args.jsonObject["todos"] ?: return listOf(UIMessagePart.Text("Error: missing 'todos' field"))

    val todos = try {
        val arr = if (todosJson is JsonPrimitive && todosJson.isString) {
            Json.parseToJsonElement(todosJson.content).jsonArray
        } else {
            todosJson.jsonArray
        }
        arr.map { element ->
            val obj = element.jsonObject
            TodoItem(
                content = obj["content"]?.jsonPrimitive?.content ?: "",
                status = obj["status"]?.jsonPrimitive?.content ?: "pending",
            )
        }
    } catch (e: Exception) {
        return listOf(UIMessagePart.Text("Error parsing todos: ${e.message}"))
    }

    val result = PlanManager.updateTodos(todos)
    return listOf(UIMessagePart.Text(result))
}
