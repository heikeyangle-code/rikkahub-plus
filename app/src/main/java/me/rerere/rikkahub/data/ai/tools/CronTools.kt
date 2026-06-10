package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.PermissionMode
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.scheduler.CronScheduler

/**
 * s14: Cron 调度工具 — schedule_cron / list_crons / cancel_cron。
 *
 * AI 通过这三个工具管理定时任务。
 */
fun buildCronTools(): List<Tool> = listOf(
    Tool(
        description = "Schedule a periodic or one-shot cron job.\n\n" +
                "- Set up recurring tasks: daily reports, monitoring, reminders\n" +
                "- Schedule one-shot delayed actions\n" +
                "- One-time immediate tasks should just be executed directly\n" +
                "- Cron runs autonomously — no interactive input\n\n" +
                "Args:\n" +
                "- id: Unique job identifier\n" +
                "- cron: 5-field expression (minute hour dom month dow)\n" +
                "- prompt: Message to inject when job fires\n" +
                "- recurring: True=repeat, False=one-shot (default: true)\n" +
                "- durable: True=survives app restart (default: false)",
                "- Tasks needing interactive input (cron runs autonomously)\n\n" +
                "Args:\n" +
                "- id: Unique job identifier\n" +
                "- cron: 5-field expression (minute hour dom month dow)\n" +
                "- prompt: Message to inject when job fires\n" +
                "- recurring: True=repeat, False=one-shot (default: true)\n" +
                "- durable: True=survives app restart (default: false)",
        permissionMode = PermissionMode.READ_ONLY,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("id", buildJsonObject {
                    put("type", "string")
                    put("description", "Unique job identifier, e.g. 'daily-test-run'")
                })
                put("cron", buildJsonObject {
                    put("type", "string")
                    put("description", "5-field cron expression (minute hour dom month dow)")
                })
                put("prompt", buildJsonObject {
                    put("type", "string")
                    put("description", "Message to inject when the job fires")
                })
                put("recurring", buildJsonObject {
                    put("type", "boolean")
                    put("description", "True = repeat on schedule, False = one-shot (auto-removed after fire)")
                })
                put("durable", buildJsonObject {
                    put("type", "boolean")
                    put("description", "True = persist to disk (survives app restart)")
                })
            }, required = listOf("id", "cron", "prompt"))
        },
        execute = { args ->
            val obj = args.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: error("id required")
            val cron = obj["cron"]?.jsonPrimitive?.contentOrNull ?: error("cron required")
        description = "List all scheduled cron jobs with their status.\n\n" +
                "Args: (none)",
        },
    ),
    Tool(
        name = "list_crons",
        description = "List all scheduled cron jobs with their status.\n\n" +
                "Args: (none)",
        permissionMode = PermissionMode.READ_ONLY,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {})
        },
        execute = {
            val jobs = CronScheduler.listJobs()
            if (jobs.isEmpty()) {
                listOf(UIMessagePart.Text("No scheduled cron jobs."))
            } else {
                val output = jobs.joinToString("\n") { job ->
                    val type = if (job.recurring) "recurring" else "one-shot"
                    val persist = if (job.durable) " (persistent)" else ""
                    "  [${job.id}] $type$persist: ${job.cron} -> ${job.prompt.take(60)}"
                }
        description = "Cancel a scheduled cron job by its ID.\n\n" +
                "- Stop a running cron job that is no longer needed\n\n" +
                "Args:\n" +
                "- id: Job ID to cancel",
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("id", buildJsonObject {
                    put("type", "string")
                    put("description", "Job ID to cancel")
                })
            }, required = listOf("id"))
        },
        execute = { args ->
            val id = args.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: error("id required")
            val removed = CronScheduler.cancelJob(id)
            if (removed) {
                listOf(UIMessagePart.Text("Cancelled cron job '$id'"))
            } else {
                listOf(UIMessagePart.Text("Cron job '$id' not found"))
            }
        },
    ),
)
