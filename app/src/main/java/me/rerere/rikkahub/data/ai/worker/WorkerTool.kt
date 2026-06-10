package me.rerere.rikkahub.data.ai.worker

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.PermissionMode
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

fun createWorkerTools(workerManager: WorkerManager): List<Tool> = listOf(
    Tool(
        name = "worker_create",
        description = "Create a background worker. Workers are persistent agents that can receive tasks and run asynchronously. Use worker_send to assign work, worker_get to check status. Workers notify you when done.",
        permissionMode = PermissionMode.DANGER_FULL_ACCESS,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("name", buildJsonObject {
                        put("type", "string"); put("description", "Optional name for the worker (e.g. \"analyzer\", \"builder\")")
                    })
                    put("cwd", buildJsonObject {
                        put("type", "string"); put("description", "Working directory (optional)")
                    })
                },
                required = emptyList(),
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
            val cwd = obj["cwd"]?.jsonPrimitive?.contentOrNull ?: ""
            val worker = workerManager.createWorker(name, cwd)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("worker_id", worker.id)
                put("name", worker.name.ifBlank { worker.id })
                put("status", "ready")
            }.toString()))
        },
    ),
    Tool(
        name = "worker_send_prompt",
        description = "Send a task to a ready worker. The worker runs in background and notifies you when done.",
        permissionMode = PermissionMode.DANGER_FULL_ACCESS,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("worker_id", buildJsonObject {
                        put("type", "string"); put("description", "Worker ID")
                    })
                    put("prompt", buildJsonObject {
                        put("type", "string"); put("description", "Task description")
                    })
                },
                required = listOf("worker_id", "prompt"),
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val wid = obj["worker_id"]?.jsonPrimitive?.contentOrNull ?: error("worker_id required")
            val prompt = obj["prompt"]?.jsonPrimitive?.contentOrNull ?: error("prompt required")
            val w = workerManager.sendPrompt(wid, prompt)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("worker_id", wid)
                put("name", w.name.ifBlank { wid })
                put("status", "running")
            }.toString()))
        },
    ),
    Tool(
        name = "worker_get",
        description = "Get worker state and result. Returns current status and completed result if finished.",
        permissionMode = PermissionMode.READ_ONLY,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("worker_id", buildJsonObject { put("type", "string"); put("description", "Worker ID") })
            }, required = listOf("worker_id"))
        },
        execute = { args ->
            val wid = args.jsonObject["worker_id"]?.jsonPrimitive?.contentOrNull
                ?: error("worker_id required")
            val worker = workerManager.getWorker(wid) ?: error("Worker $wid not found")
            listOf(UIMessagePart.Text(buildJsonObject {
                put("worker_id", worker.id)
                put("name", worker.name.ifBlank { worker.id })
                put("status", when (worker.state) {
                    is WorkerState.ReadyForPrompt -> "ready"
                    is WorkerState.Running -> "running"
                    is WorkerState.Finished -> "finished"
                    is WorkerState.Failed -> "failed"
                })
                put("created_at", worker.createdAt)
                worker.finishedAt?.let { put("finished_at", it) }
                val result = when (val s = worker.state) {
                    is WorkerState.Finished -> s.result
                    is WorkerState.Failed -> "Error: ${s.error}"
                    else -> null
                }
                if (result != null) put("result", result)
            }.toString()))
        },
    ),
    Tool(
        name = "worker_list",
        description = "List all workers and their current status.",
        permissionMode = PermissionMode.READ_ONLY,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {})
        },
        execute = {
            val all = workerManager.listWorkers()
            if (all.isEmpty()) {
                listOf(UIMessagePart.Text("No workers."))
            } else {
                val json = buildJsonArray {
                    all.forEach { w ->
                        add(buildJsonObject {
                            put("worker_id", w.id)
                            put("name", w.name.ifBlank { w.id })
                            put("status", when (w.state) {
                                is WorkerState.ReadyForPrompt -> "ready"
                                is WorkerState.Running -> "running"
                                is WorkerState.Finished -> "finished"
                                is WorkerState.Failed -> "failed"
                            })
                            put("created_at", w.createdAt)
                            w.finishedAt?.let { put("finished_at", it) }
                        })
                    }
                }
                listOf(UIMessagePart.Text(json.toString()))
            }
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
        },
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
        description = "Restart a worker, resetting it to ready state for new tasks.",
        permissionMode = PermissionMode.DANGER_FULL_ACCESS,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("worker_id", buildJsonObject { put("type", "string"); put("description", "Worker ID") })
            }, required = listOf("worker_id"))
        },
        execute = { args ->
            val wid = args.jsonObject["worker_id"]?.jsonPrimitive?.contentOrNull
                ?: error("worker_id required")
            workerManager.restart(wid)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("worker_id", wid); put("status", "ready")
            }.toString()))
        },
    ),
)
