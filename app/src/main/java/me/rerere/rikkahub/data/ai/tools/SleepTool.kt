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
    description = "Pause execution for a specified duration.\n\n" +
        "When to use:\n" +
        "- Wait for an external process to complete before proceeding\n\n" +
        "When NOT to use:\n" +
        "- Waiting between independent commands (run them in parallel instead)\n" +
        "- Retrying failing commands in a loop (diagnose root cause)\n\n" +
        "Args:\n" +
        "- duration_ms: Milliseconds to sleep (1000 = 1s, max 30000 = 30s)",
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
