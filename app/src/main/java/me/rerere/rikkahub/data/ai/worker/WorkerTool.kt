package me.rerere.rikkahub.data.ai.worker

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.PermissionMode
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

fun createWorkerTools(workerManager: WorkerManager): List<Tool> = listOf(
    Tool(
        name = "worker",
        description = buildString {
            appendLine("Create, manage, and communicate with background workers.")
            appendLine()
            appendLine("When to use:")
            appendLine("- create: Start a new background worker process with optional working directory")
            appendLine("- send: Assign a task to an idle worker (runs in background, notifies on completion)")
            appendLine("- get: Check worker status and retrieve result")
            appendLine("- list: Show all workers and their states")
            appendLine("- terminate: Stop a running worker")
            appendLine("- restart: Reset a worker to ready state")
            appendLine()
            appendLine("Args:")
            appendLine("- action: create | send | get | list | terminate | restart")
            appendLine("- name: Human-readable name (create)")
            appendLine("- cwd: Working directory (create)")
            appendLine("- worker_id: Worker identifier (send, get, terminate, restart)")
            appendLine("- prompt: Task description (send)")
        },
        permissionMode = PermissionMode.DANGER_FULL_ACCESS,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray { add("create"); add("send"); add("get"); add("list"); add("terminate"); add("restart") })
                        put("description", "Operation to perform")
                    })
                    put("name", buildJsonObject {
                        put("type", "string"); put("description", "Human-readable name (used by: create)")
                    })
                    put("cwd", buildJsonObject {
                        put("type", "string"); put("description", "Working directory (used by: create)")
                    })
                    put("worker_id", buildJsonObject {
                        put("type", "string"); put("description", "Worker ID (used by: send, get, terminate, restart)")
                    })
                    put("prompt", buildJsonObject {
                        put("type", "string"); put("description", "Task description (used by: send)")
                    })
                },
                required = listOf("action"),
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val action = obj["action"]?.jsonPrimitive?.contentOrNull ?: error("action required")

            when (action) {
                "create" -> {
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                    val cwd = obj["cwd"]?.jsonPrimitive?.contentOrNull ?: ""
                    val worker = workerManager.createWorker(name, cwd)
                    listOf(UIMessagePart.Text(buildJsonObject {
                        put("worker_id", worker.id)
                        put("name", worker.name.ifBlank { worker.id })
                        put("status", "ready")
                    }.toString()))
                }
                "send" -> {
                    val wid = obj["worker_id"]?.jsonPrimitive?.contentOrNull ?: error("worker_id required")
                    val prompt = obj["prompt"]?.jsonPrimitive?.contentOrNull ?: error("prompt required")
                    val w = workerManager.sendPrompt(wid, prompt)
                    listOf(UIMessagePart.Text(buildJsonObject {
                        put("worker_id", wid)
                        put("name", w.name.ifBlank { wid })
                        put("status", "running")
                    }.toString()))
                }
                "get" -> {
                    val wid = obj["worker_id"]?.jsonPrimitive?.contentOrNull ?: error("worker_id required")
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
                }
                "list" -> {
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
                }
                "terminate" -> {
                    val wid = obj["worker_id"]?.jsonPrimitive?.contentOrNull ?: error("worker_id required")
                    workerManager.terminate(wid)
                    listOf(UIMessagePart.Text(buildJsonObject {
                        put("worker_id", wid); put("status", "finished")
                    }.toString()))
                }
                "restart" -> {
                    val wid = obj["worker_id"]?.jsonPrimitive?.contentOrNull ?: error("worker_id required")
                    workerManager.restart(wid)
                    listOf(UIMessagePart.Text(buildJsonObject {
                        put("worker_id", wid); put("status", "ready")
                    }.toString()))
                }
                else -> error("Unknown action: $action")
            }
        },
    ),
)
