package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.PermissionMode
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.agent.TeammateRunner

/**
 * 队友 spawn 工具集合。
 * 对应泄露版 spawnMultiAgent.ts (35KB) + SendMessageTool。
 *
 * 提供：
 * - spawn_teammate: 创建并启动一个并行队友
 * - teammate_list: 列出所有队友
 * - teammate_kill: 杀掉指定队友
 */
fun createTeammateTools(teammateRunner: TeammateRunner): List<Tool> = listOf(
    Tool(
        name = "spawn_teammate",
        description = buildString {
            appendLine("Spawn a teammate agent to work alongside you in parallel.")
            appendLine()
            appendLine("Teammates run in the same process as coroutines. They have access to tools and can communicate via send_message.")
            appendLine()
            appendLine("Usage:")
            appendLine("  {\"name\": \"researcher\", \"prompt\": \"search for the latest news about AI agents\"}")
            appendLine()
            appendLine("After spawning, the teammate starts immediately. Check progress via teammate_list.")
            appendLine("The teammate will send a message via mailbox when done — call get_teammate_messages to read it.")
            appendLine()
            appendLine("You can spawn multiple teammates with different names. Each runs independently.")
            appendLine("Teammates use the same model as the main agent unless model is specified.")
            appendLine("Up to 3 teammates can run concurrently.")
        },
        permissionMode = PermissionMode.DANGER_FULL_ACCESS,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("name", buildJsonObject {
                        put("type", "string")
                        put("description", "Unique name for the teammate (e.g., \"researcher\", \"coder\"). Used as address for send_message.")
                    })
                    put("prompt", buildJsonObject {
                        put("type", "string")
                        put("description", "The task for the teammate to perform. Be specific about what to do and how to report back.")
                    })
                    put("model", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional model override for this teammate. Omit to use the default model.")
                    })
                },
                required = listOf("name", "prompt"),
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: error("name required")
            val prompt = obj["prompt"]?.jsonPrimitive?.contentOrNull ?: error("prompt required")
            val model = obj["model"]?.jsonPrimitive?.contentOrNull

            // Check concurrent limit
            val runningCount = teammateRunner.listRunning().size
            if (runningCount >= 3) {
                error("Maximum 3 concurrent teammates reached. Wait for one to finish or kill one first.")
            }

            val agentId = teammateRunner.spawn(
                name = name,
                prompt = prompt,
                model = model,
                executeBlock = { agentName, taskPrompt ->
                    // 队友将在协程中执行此 block
                    // 实际执行体由 ChatService 注册时注入
                    // 这里仅返回占位，实际逻辑在 ChatService 的 promptHandler 中
                    "Teammate $agentName is processing..."
                },
            )

            listOf(UIMessagePart.Text(buildJsonObject {
                put("agent_id", agentId)
                put("name", name)
                put("status", "running")
                put("message", "Teammate '$name' started")
                put("prompt", prompt.take(100))
            }.toString()))
        },
    ),
    Tool(
        name = "teammate_list",
        description = "List all teammates and their status. Shows running, completed, and failed teammates.",
        permissionMode = PermissionMode.READ_ONLY,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {},
                required = emptyList(),
            )
        },
        execute = {
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
                            put("tool_use_count", state.toolUseCount)
                            put("token_count", state.tokenCount)
                        })
                    }
                }
                listOf(UIMessagePart.Text(json.toString()))
            }
        },
    ),
    Tool(
        name = "teammate_kill",
        description = "Kill a running teammate by name.",
        permissionMode = PermissionMode.DANGER_FULL_ACCESS,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("name", buildJsonObject {
                        put("type", "string")
                        put("description", "Name of the teammate to kill")
                    })
                },
                required = listOf("name"),
            )
        },
        execute = { args ->
            val name = args.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: error("name required")
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
        },
    ),
)
