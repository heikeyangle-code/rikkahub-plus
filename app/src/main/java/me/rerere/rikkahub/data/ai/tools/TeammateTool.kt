package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.PermissionMode
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.agent.TeammateRunner

/**
 * 队友工具 — spawn/list/kill 合在一个工具中。
 */
fun createTeammateTools(teammateRunner: TeammateRunner): List<Tool> = listOf(
    Tool(
        name = "spawn_teammate",
        description = buildString {
            appendLine("Spawn, list, or kill teammate agents.")
            appendLine()
            appendLine("Actions: spawn, list, kill")
            appendLine()
            appendLine("- spawn: Create a new teammate in background. Provide name + prompt.")
            appendLine("  Teammates use the same model as the main agent unless model is specified.")
            appendLine("  Max 3 concurrent teammates.")
            appendLine("- list: Show all teammates and their status.")
            appendLine("- kill: Terminate a running teammate by name.")
            appendLine()
            appendLine("After spawning, the teammate starts immediately. Check progress via list action.")
            appendLine("The teammate will send a message via mailbox when done — call get_teammate_messages to read it.")
            appendLine("Each spawn generates a unique request_id — when the teammate responds via get_teammate_messages,")
            appendLine("the response includes the matching request_id so you can correlate results with requests.")
        },
        permissionMode = PermissionMode.DANGER_FULL_ACCESS,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray { add("spawn"); add("list"); add("kill") })
                        put("description", "Operation to perform")
                    })
                    put("name", buildJsonObject {
                        put("type", "string")
                        put("description", "Unique teammate name (used by: spawn, kill)")
                    })
                    put("prompt", buildJsonObject {
                        put("type", "string")
                        put("description", "Task description (used by: spawn)")
                    })
                    put("model", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional model override (used by: spawn)")
                    })
                },
                required = listOf("action"),
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val action = obj["action"]?.jsonPrimitive?.contentOrNull ?: error("action required")

            when (action) {
                "spawn" -> {
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: error("name required")
                    val prompt = obj["prompt"]?.jsonPrimitive?.contentOrNull ?: error("prompt required")
                    val model = obj["model"]?.jsonPrimitive?.contentOrNull

                    val runningCount = teammateRunner.listRunning().size
                    if (runningCount >= 3) {
                        error("Maximum 3 concurrent teammates reached.")
                    }

                    val requestId = java.util.UUID.randomUUID().toString().take(8)
                    val agentId = teammateRunner.spawn(
                        name = name, prompt = prompt, model = model, requestId = requestId,
                    )
                    listOf(UIMessagePart.Text(buildJsonObject {
                        put("agent_id", agentId)
                        put("name", name)
                        put("status", "running")
                        put("message", "Teammate '$name' started")
                        put("request_id", requestId)
                    }.toString()))
                }
                "list" -> {
                    val states = teammateRunner.list()
                    if (states.isEmpty()) {
                        listOf(UIMessagePart.Text("No teammates."))
                    } else {
                        val json = buildJsonArray {
                            states.forEach { state ->
                                add(buildJsonObject {
                                    put("name", state.identity.agentName)
                                    put("agent_id", state.identity.agentId)
                                    put("status", state.status.name.lowercase())
                                    put("is_idle", state.isIdle)
                                    state.error?.let { put("error", it) }
                                    state.result?.let { put("result", it.take(200)) }
                                    state.requestId?.let { put("request_id", it) }
                                    put("tool_use_count", state.toolUseCount)
                                    put("token_count", state.tokenCount)
                                })
                            }
                        }
                        listOf(UIMessagePart.Text(json.toString()))
                    }
                }
                "kill" -> {
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: error("name required")
                    val state = teammateRunner.getByName(name)
                    if (state == null) {
                        listOf(UIMessagePart.Text("Teammate '$name' not found."))
                    } else {
                        teammateRunner.kill(state.identity.agentId)
                        listOf(UIMessagePart.Text(buildJsonObject {
                            put("success", true)
                            put("message", "Teammate '$name' killed")
                        }.toString()))
                    }
                }
                else -> error("Unknown action: $action")
            }
        },
    ),
)
