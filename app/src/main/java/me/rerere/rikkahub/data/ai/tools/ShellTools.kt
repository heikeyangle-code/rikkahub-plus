package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.agent.AgentContextStore
import me.rerere.rikkahub.data.ai.agent./* BackgroundTaskQueue */
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shell 执行工具 — execute_command。
 * 对齐 CC 的 BashTool（BashTool.tsx 160KB + prompt.ts 21KB）。
 *
 * 针对 Android 适配：
 * - 用 Runtime.exec() 替代桌面端复杂的 shell 系统
 * - 不支持 sandbox（Android 已有沙箱）
 * - 支持 timeout / run_in_background / read-only 检测
 */

private val READ_ONLY_AGENT_TYPES = setOf("Explore", "Plan")

/** 只读模式下允许的命令前缀 */
private val READ_ONLY_COMMAND_PREFIXES = listOf(
    "ls", "find", "grep", "cat", "head", "tail",
    "pwd", "echo", "printf", "which", "type",
    "git status", "git log", "git diff", "git branch", "git show",
    "df", "du", "stat", "file", "wc", "sort", "uniq",
    "env", "printenv", "getprop",
)

/** 只读模式下明确禁止的命令 */
private val DESTRUCTIVE_COMMANDS = listOf(
    "rm", "mv", "cp", "mkdir", "touch", "chmod", "chown",
    "dd", "mkfs", "mount", "umount",
    "git add", "git commit", "git push", "git merge", "git rebase", "git reset",
    "git checkout --", "git restore", "git clean",
    "npm install", "npm run", "pip install", "apt", "pkg",
    "sh -c", "bash -c",
)

fun isAgentReadOnly(): Boolean {
    val ctx = AgentContextStore.get()
    val agentType = ctx?.subagentName ?: return false
    return agentType in READ_ONLY_AGENT_TYPES
}

private fun isCommandAllowedInReadOnly(command: String): Boolean {
    val trimmed = command.trim()
    // Check destructive commands first
    for (destructive in DESTRUCTIVE_COMMANDS) {
        if (trimmed.startsWith(destructive)) return false
    }
    // Check allowed prefixes
    for (prefix in READ_ONLY_COMMAND_PREFIXES) {
        if (trimmed.startsWith(prefix)) return true
    }
    // Default: block unknown commands in read-only mode
    return false
}

fun createShellTools(shellTimeoutSec: Int = 120): List<Tool> {
    return listOf(
        Tool(
            name = "execute_command",
            description = buildString {
                appendLine("Execute shell commands on the Android device.")
                appendLine()
                appendLine("Use this tool for git operations, Android diagnostics (logcat, pm, dumpsys, am), build scripts, and any operation without a dedicated tool.")
                appendLine()
                appendLine("When to use:")
                appendLine("- git operations")
                appendLine("- Android diagnostics: logcat -d, pm, dumpsys, am")
                appendLine("- zip/unzip for archives")
                appendLine("- Running build scripts or tools")
                appendLine("- Any operation without a dedicated tool")
                appendLine()
                appendLine("When NOT to use:")
                appendLine("- File operations (use file tool: read/write/patch/list/search)")
                appendLine("- Calculations (use calculator)")
                appendLine("- Web fetching (use web_fetch)")
                appendLine()
                appendLine("Args:")
                appendLine("- command: Shell command to execute (required)")
                appendLine("- timeout: Max execution time in ms (default: ${shellTimeoutSec * 1000}, max: 600000)")
                appendLine("- run_in_background: true for long-running commands (notified on completion)")
                appendLine()
                appendLine("Notes:")
                appendLine("- Working directory persists between commands, shell state does not")
                appendLine("- Interactive commands (requiring stdin) will hang — avoid them")
                appendLine("- For many independent commands, make parallel tool calls")
                appendLine("- Chain dependent commands with &&")
                appendLine("- Default timeout: ${shellTimeoutSec}s")
                appendLine("- Read-only agents cannot run destructive commands")
            }.trimEnd(),
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("command", buildJsonObject {
                            put("type", "string")
                            put("description", "Shell command to execute (e.g., 'logcat -d | grep Error', 'ls -la')")
                        })
                        put("timeout", buildJsonObject {
                            put("type", "integer")
                            put("description", "Optional timeout in milliseconds (max 600000). Default: 120000.")
                        })
                        put("run_in_background", buildJsonObject {
                            put("type", "boolean")
                            put("description", "Set to true to run this command in the background. You'll be notified when it completes.")
                        })
                    },
                    required = listOf("command"),
                )
            },
            execute = { args ->
                val obj = args.jsonObject
                val command = obj["command"]?.jsonPrimitive?.content
                    ?: error("command parameter is required")
                val customTimeout = obj["timeout"]?.jsonPrimitive?.intOrNull
                    ?: 120_000
                val runInBackground = obj["run_in_background"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                    ?: false
                val timeoutMs = customTimeout.coerceIn(1_000, 600_000)

                // Read-only mode check
                if (isAgentReadOnly() && !isCommandAllowedInReadOnly(command)) {
                    error("Cannot execute destructive command in read-only mode (agent type: ${AgentContextStore.get()?.subagentName ?: "unknown"}). Allowed: ls, find, grep, cat, head, tail, git status/log/diff, pwd, echo, df, stat, file, env, getprop")
                }

                if (runInBackground) {
                    val bgId = /* BackgroundTaskQueue */.start(
                        toolName = "execute_command",
                        command = command,
                        executor = {
                            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                            val stdout = proc.inputStream.bufferedReader().readText()
                            val stderr = proc.errorStream.bufferedReader().readText()
                            proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                            buildJsonObject {
                                put("stdout", JsonPrimitive(stdout))
                                put("stderr", JsonPrimitive(stderr))
                                put("exit_code", JsonPrimitive(proc.exitValue()))
                            }.toString()
                        }
                    )

                    listOf(UIMessagePart.Text(buildJsonObject {
                        put("status", "running")
                        put("bg_id", bgId)
                        put("message", "Command started in background")
                    }.toString()))
                } else {
                    // Foreground execution
                    val process = try {
                        Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                    } catch (e: Exception) {
                        error("Failed to start command: ${e.message}")
                    }
                    try {
                        withTimeout(timeoutMs.toLong()) {
                            val (stdout, stderr) = coroutineScope {
                                val stdoutDeferred = async {
                                    process.inputStream.bufferedReader().readText()
                                }
                                val stderrDeferred = async {
                                    process.errorStream.bufferedReader().readText()
                                }
                                stdoutDeferred.await() to stderrDeferred.await()
                            }
                            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                            val exitCode = process.exitValue()
                            val payload = buildJsonObject {
                                put("stdout", JsonPrimitive(stdout))
                                put("stderr", JsonPrimitive(stderr))
                                put("exit_code", JsonPrimitive(exitCode))
                            }
                            listOf(UIMessagePart.Text(payload.toString()))
                        }
                    } catch (e: TimeoutCancellationException) {
                        process.destroyForcibly()
                        error("Command timed out after ${timeoutMs / 1000} seconds")
                    }
                }
            },
        ),
    )
}