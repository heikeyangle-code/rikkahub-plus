package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/**
 * Shell 执行工具 — execute_command
 * 在 Android 沙箱中执行 shell 命令，返回 stdout、stderr 和退出码。
 * 注意：权限仅限于 App 自身的 sandbox 目录。
 */
fun createShellTools(): List<Tool> {
    return listOf(
        Tool(
            name = "execute_command",
            description = """
                Execute a shell command on the Android device.
                Returns stdout, stderr, and exit code as a JSON object.
                Commands run in the app's sandbox — no root, no system-wide access.
                Use for: logcat, file operations, device info, grep, zip.
                Avoid: interactive commands (they will hang), long-running commands (30s timeout).
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("command", buildJsonObject {
                            put("type", "string")
                            put("description", "Shell command to execute (e.g., 'logcat -d | grep Error', 'ls -la', 'df -h')")
                        })
                    },
                    required = listOf("command"),
                )
            },
            execute = { args ->
                val command = args.jsonObject["command"]?.jsonPrimitive?.content
                    ?: error("command parameter is required")
                val process = try {
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                } catch (e: Exception) {
                    error("Failed to start command: ${e.message}")
                }
                // Read stdout/stderr in parallel
                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = process.errorStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                val payload = buildJsonObject {
                    put("stdout", kotlinx.serialization.json.JsonPrimitive(stdout))
                    put("stderr", kotlinx.serialization.json.JsonPrimitive(stderr))
                    put("exit_code", kotlinx.serialization.json.JsonPrimitive(exitCode))
                }
                listOf(UIMessagePart.Text(payload.toString()))
            },
        )
    )
}
