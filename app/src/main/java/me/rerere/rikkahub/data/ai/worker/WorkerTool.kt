package me.rerere.rikkahub.data.ai.worker

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.PermissionMode
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

fun createWorkerTools(workerManager: WorkerManager): List<Tool> = listOf(
    Tool(
        name = "worker_create",
        description = "Create a background worker with cwd and trusted_roots.",
        permissionMode = PermissionMode.DANGER_FULL_ACCESS,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("cwd", buildJsonObject {
                        put("type", "string"); put("description", "Working directory")
                    })
                    put("trusted_roots", buildJsonObject {
                        put("type", "string"); put("description", "Comma-separated trusted paths")
                    })
                },
                required = listOf("cwd"),
            )
        }},
        execute = { args ->
            val obj = args.jsonObject
            val cwd = obj["cwd"]?.jsonPrimitive?.contentOrNull ?: error("cwd required")
            val trusted = obj["trusted_roots"]?.jsonPrimitive?.contentOrNull
                ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            val worker = workerManager.createWorker(cwd, trusted)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("worker_id", worker.id)
                put("status", "spawning")
                put("cwd", cwd)
                put("trust_auto_resolve", trusted.any { cwd.startsWith(it) })
            }.toString()))
        },
    ),
    Tool(
        name = "worker_observe",
        description = "Feed terminal output from a worker. Detects trust/ready states.",
        permissionMode = PermissionMode.READ_ONLY,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("worker_id", buildJsonObject {
                        put("type", "string"); put("description", "Worker ID")
                    })
                    put("screen_text", buildJsonObject {
                        put("type", "string"); put("description", "Terminal screen text")
                    })
                },
                required = listOf("worker_id", "screen_text"),
            )
        }},
        execute = { args ->
            val obj = args.jsonObject
            val wid = obj["worker_id"]?.jsonPrimitive?.contentOrNull ?: error("worker_id required")
            val txt = obj["screen_text"]?.jsonPrimitive?.contentOrNull ?: ""
            val w = workerManager.observe(wid, txt)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("worker_id", wid)
                put("status", w.state::class.simpleName?.lowercase() ?: "unknown")
            }.toString()))
        },
    ),
    Tool(
        name = "worker_send_prompt",
        description = "Send a task to a ready worker. Must be in ready_for_prompt state.",
        permissionMode = PermissionMode.DANGER_FULL_ACCESS,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("worker_id", buildJsonObject {
                        put("type", "string"); put("description", "Worker ID")
                    })
                    put("prompt", buildJsonObject {
                        put("type", "string"); put("description", "Task prompt")
                    })
                },
                required = listOf("worker_id", "prompt"),
            )
        }},
        execute = { args ->
            val obj = args.jsonObject
            val wid = obj["worker_id"]?.jsonPrimitive?.contentOrNull ?: error("worker_id required")
            val prompt = obj["prompt"]?.jsonPrimitive?.contentOrNull ?: error("prompt required")
            val w = workerManager.sendPrompt(wid, prompt)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("worker_id", wid); put("status", "running")
                put("prompt_delivery_attempts", w.promptDeliveryAttempts)
            }.toString()))
        },
    ),
    Tool(
        name = "worker_get",
        description = "Get state of a worker by ID.",
        permissionMode = PermissionMode.READ_ONLY,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("worker_id", buildJsonObject { put("type", "string"); put("description", "Worker ID") })
            }, required = listOf("worker_id"))
        }},
        execute = { args ->
            val wid = args.jsonObject["worker_id"]?.jsonPrimitive?.contentOrNull
                ?: error("worker_id required")
            val worker = workerManager.getWorker(wid) ?: error("Worker $wid not found")
            listOf(UIMessagePart.Text(buildJsonObject {
                put("worker_id", worker.id)
                put("cwd", worker.cwd)
                put("status", worker.state::class.simpleName?.lowercase() ?: "unknown")
            }.toString()))
        },
    ),
    Tool(
        name = "worker_terminate",
        description = "Terminate a running worker.",
        permissionMode = PermissionMode.DANGER_FULL_ACCESS,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("worker_id", buildJsonObject { put("type", "string"); put("description", "Worker ID") })
            }, required = listOf("worker_id"))
        }},
        execute = { args ->
            val wid = args.jsonObject["worker_id"]?.jsonPrimitive?.contentOrNull
                ?: error("worker_id required")
            workerManager.terminate(wid)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("worker_id", wid); put("status", "finished")
            }.toString()))
        },
    ),
    Tool(
        name = "worker_restart",
        description = "Restart a worker, reset to Spawning.",
        permissionMode = PermissionMode.DANGER_FULL_ACCESS,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("worker_id", buildJsonObject { put("type", "string"); put("description", "Worker ID") })
            }, required = listOf("worker_id"))
        }},
        execute = { args ->
            val wid = args.jsonObject["worker_id"]?.jsonPrimitive?.contentOrNull
                ?: error("worker_id required")
            workerManager.restart(wid)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("worker_id", wid); put("status", "spawning")
            }.toString()))
        },
    ),
)
