package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.PermissionMode
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.mcp.McpManager

fun createMcpResourceTools(mcpManager: McpManager): List<Tool> = listOf(
    Tool(
        name = "list_mcp_resources",
        description = "List available resources from connected MCP servers.",
        permissionMode = PermissionMode.READ_ONLY,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("server", buildJsonObject {
                    put("type", "string"); put("description", "Server name (optional, lists all if omitted)")
                })
            })
        }},
        execute = { args ->
            val server = args.jsonObject["server"]?.jsonPrimitive?.contentOrNull
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
        },
    ),
    Tool(
        name = "read_mcp_resource",
        description = "Read a specific resource from an MCP server by URI.",
        permissionMode = PermissionMode.READ_ONLY,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("server", buildJsonObject {
                    put("type", "string"); put("description", "Server name")
                })
                put("uri", buildJsonObject {
                    put("type", "string"); put("description", "Resource URI")
                })
            }, required = listOf("uri"))
        }},
        execute = { args ->
            val obj = args.jsonObject
            val server = obj["server"]?.jsonPrimitive?.contentOrNull ?: "default"
            val uri = obj["uri"]?.jsonPrimitive?.contentOrNull ?: error("uri required")
            val content = mcpManager.readResource(server, uri)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("uri", uri); put("content", content)
            }.toString()))
        },
    ),
)
