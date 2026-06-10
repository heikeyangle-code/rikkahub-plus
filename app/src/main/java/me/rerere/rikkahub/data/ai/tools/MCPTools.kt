package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.PermissionMode
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.mcp.McpManager

fun createMcpResourceTools(mcpManager: McpManager): List<Tool> = listOf(
    Tool(
        name = "mcp_resource",
        description = "List and read resources from connected MCP servers. Use action=list to see available resources, action=read to read a specific resource by URI.",
        permissionMode = PermissionMode.READ_ONLY,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray { add("list"); add("read") })
                        put("description", "list=show available resources, read=read resource content")
                    })
                    put("server", buildJsonObject {
                        put("type", "string"); put("description", "Server name (optional for list, required for read)")
                    })
                    put("uri", buildJsonObject {
                        put("type", "string"); put("description", "Resource URI (used by: read)")
                    })
                },
                required = listOf("action"),
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val action = obj["action"]?.jsonPrimitive?.contentOrNull ?: error("action required")
            when (action) {
                "list" -> {
                    val server = obj["server"]?.jsonPrimitive?.contentOrNull
                    val resources = mcpManager.listResources(server)
                    listOf(UIMessagePart.Text(buildJsonObject {
                        put("server", server ?: "all")
                        put("resources", buildJsonArray {
                            resources.forEach { r ->
                                add(buildJsonObject {
                                    put("uri", r.uri); put("name", r.name)
                                    r.description?.let { put("description", it) }
                                })
                            }
                        })
                    }.toString()))
                }
                "read" -> {
                    val server = obj["server"]?.jsonPrimitive?.contentOrNull ?: "default"
                    val uri = obj["uri"]?.jsonPrimitive?.contentOrNull ?: error("uri required")
                    val content = mcpManager.readResource(server, uri)
                    listOf(UIMessagePart.Text(buildJsonObject {
                        put("uri", uri); put("content", content)
                    }.toString()))
                }
                else -> error("Unknown action: $action")
            }
        },
    ),
)
