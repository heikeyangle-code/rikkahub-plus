package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/**
 * Sleep / delay tool. Aligned to leaked SleepTool.
 * Provides a controlled delay mechanism for AI operations.
 */
fun createSleepTool(): Tool = Tool(
    name = "sleep",
    description = "Sleep (pause) for a specified number of milliseconds.\n\n" +
        "Usage:\n" +
        "- Only use when you need to wait for an external process to complete\n" +
        "- Keep durations short (1-5 seconds max) to avoid blocking the user\n" +
        "- Do NOT sleep between commands that can run immediately\n" +
        "- Do NOT retry failing commands in a sleep loop — diagnose root cause\n" +
        "- If polling an external process, use a check command rather than sleeping first",
    needsApproval = false,
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("duration_ms", buildJsonObject {
                    put("type", "number")
                    put("description", "Number of milliseconds to sleep (1000 = 1 second). Max 30000 (30 seconds).")
                })
            },
            required = listOf("duration_ms"),
        )
    },
    execute = { args ->
        val ms = args.jsonObject["duration_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: error("duration_ms required")
        if (ms > 30000) error("Sleep duration exceeds maximum of 30000ms")
        if (ms < 0) error("Sleep duration must be non-negative")
        Thread.sleep(ms)
        listOf(UIMessagePart.Text("Slept for ${ms}ms"))
    },
)
