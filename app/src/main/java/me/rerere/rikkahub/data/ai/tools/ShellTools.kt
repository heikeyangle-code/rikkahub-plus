package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
                Execute a shell command on the Android device. Prefer file_read/write/list/copy/move for file ops.
                Returns stdout, stderr, and exit code as a JSON object.
                Commands run in the app's sandbox — no root, no system-wide access.
                Use for: logcat, device info, grep, zip.
                Avoid: interactive commands (they will hang), long-running commands (30s timeout).
            """.trimIndent().replace("\n", " "),
            needsApproval = true,
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
                // Read stdout and stderr in parallel to avoid deadlock
                val (stdout, stderr) = coroutineScope {
                    val stdoutDeferred = async {
                        process.inputStream.bufferedReader().readText()
                    }
                    val stderrDeferred = async {
                        process.errorStream.bufferedReader().readText()
                    }
                    stdoutDeferred.await() to stderrDeferred.await()
                }
                val exitCode = if (process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.exitValue()
                } else {
                    process.destroyForcibly()
                    -1
                }
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
