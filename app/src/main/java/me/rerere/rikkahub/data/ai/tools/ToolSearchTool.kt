package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.util.concurrent.ConcurrentHashMap

/**
 * 工具注册表 — 记录所有可用工具的元数据
 */
object ToolRegistry {
    data class ToolInfo(
        val name: String,
        val description: String,
        val category: String = "其他",
        val enabled: Boolean = true,
    )

    private val tools = ConcurrentHashMap<String, ToolInfo>()
    private val toolsLock = Any()

    fun register(name: String, description: String, category: String = "其他", enabled: Boolean = true) {
        tools[name] = ToolInfo(name, description, category, enabled)
    }

    fun search(query: String, maxResults: Int = 10): List<ToolInfo> {
        val q = query.lowercase()
        val exact = q.startsWith("select:")
        val requirePrefix = q.startsWith("+")

        return tools.values.filter { it.enabled }.filter { t ->
            val name = t.name.lowercase()
            val desc = t.description.lowercase()
            when {
                exact -> {
                    val names = q.removePrefix("select:").split(",").map { it.trim().lowercase() }
                    names.any { name.contains(it) }
                }
                requirePrefix -> {
                    val term = q.removePrefix("+").trim()
                    name.contains(term)
                }
                else -> name.contains(q) || desc.contains(q)
            }
        }.take(maxResults)
    }

    fun listByCategory(): Map<String, List<ToolInfo>> = tools.values.filter { it.enabled }.groupBy { it.category }

    fun registerBuiltin() {
        register("file_read", "Read file contents", "文件")
        register("file_write", "Create or overwrite files", "文件")
        register("file_list", "List directory contents", "文件")
        register("file_copy", "Copy files", "文件")
        register("file_move", "Move/rename files", "文件")
        register("file_mkdir", "Create directories", "文件")
        register("file_delete", "Delete files/directories", "文件")
        register("file_search", "Search files by name/content", "文件")
        register("execute_command", "Execute shell commands", "执行")
        register("execute_python", "Execute Python code", "执行")
        register("eval_javascript", "Execute JavaScript code", "执行")
        register("search_web", "Search the web", "网络")
        register("scrape_web", "Scrape webpage content", "网络")
        register("github_tool", "Interact with GitHub API", "开发")
        register("data_process", "Process/transform text data", "工具")
        register("database_query", "Query local SQLite database", "工具")
        register("convert_file", "Convert file formats", "工具")
        register("use_skill", "Load and execute a skill", "技能")
        register("clipboard_tool", "Read/write clipboard", "工具")
        register("get_time_info", "Get current date/time", "工具")
        register("text_to_speech", "Speak text aloud", "工具")
        register("present_file", "Share a file", "工具")
        register("ask_user", "Ask user questions", "交互")
        register("memory_tool", "Store/retrieve long-term memories", "记忆")
        register("task_create", "Create tasks for tracking", "任务")
        register("task_get", "Get task details", "任务")
        register("task_list", "List all tasks", "任务")
        register("task_update", "Update task status", "任务")
        register("task_stop", "Cancel a task", "任务")
        register("task_output", "Get task result", "任务")
        register("todo_write", "Create lightweight todo list", "任务")
        register("team_create", "Create agent team", "任务")
        register("team_delete", "Delete agent team", "任务")
        register("sub_agent", "Delegate to sub-agent", "Agent")
        register("send_message", "Send message to another agent", "Agent")
        register("read_messages", "Read incoming messages", "Agent")
        register("enter_plan_mode", "Switch to planning mode", "计划")
        register("exit_plan_mode", "Exit planning mode", "计划")
        register("calculator", "Perform precise calculation", "工具")
        register("mcp__*", "MCP server tools (dynamic)", "MCP")
    }
}

fun createToolSearchTool(): Tool = Tool(
    name = "tool_search",
    description = """
        Search available tools by name or keyword. Returns tool descriptions and parameter info.
        Use this when you need to find a tool for a specific task.

        ToolSearch fetches full schema definitions for deferred tools so they can be called.
        Until fetched, only the name is known — there is no parameter schema, so the tool cannot be invoked.

        Query forms:
        - "select:Read,Write,Grep" — fetch these exact tools by name
        - "notebook jupyter" — keyword search, up to max_results best matches
        - "+slack send" — require "slack" in name, rank by remaining terms

        Result format: each matched tool appears with its name, description, and category.
        After searching, matched tools become fully callable.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "Search query. Use 'select:name1,name2' for exact match, or keywords.")
                })
                put("max_results", buildJsonObject {
                    put("type", "integer")
                    put("description", "Max results (default: 10)")
                })
            },
            required = listOf("query"),
        )
    },
    execute = { args ->
        val obj = args.jsonObject
        val query = obj["query"]?.jsonPrimitive?.contentOrNull ?: error("query required")
        val maxResults = obj["max_results"]?.jsonPrimitive?.intOrNull ?: 10
        val results = ToolRegistry.search(query, maxResults)
        if (results.isEmpty()) {
            listOf(UIMessagePart.Text("No tools matching '$query'"))
        } else {
            val byCategory = results.groupBy { it.category }
            val output = byCategory.flatMap { (cat, tools) ->
                listOf("[$cat]") + tools.map { "  - ${it.name}: ${it.description.take(80)}" }
            }.joinToString("\n")
            listOf(UIMessagePart.Text("Found ${results.size} tool(s):\n$output"))
        }
    },
)
