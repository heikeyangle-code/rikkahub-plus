package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.PermissionMode
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.io.File

fun createGitNativeTools(): List<Tool> = listOf(
    Tool(
        name = "git_status",
        description = "Show working tree status: branch, staged, unstaged, untracked files. Structured JSON output.",
        permissionMode = PermissionMode.READ_ONLY,
        parameters = {{
            InputSchema.Obj(properties = buildJsonObject {
                put("short", buildJsonObject {
                    put("type", "boolean"); put("description", "Compact format (default true)")
                })
            })
        }},
        execute = {{
            val short = it.jsonObject["short"]?.jsonPrimitive?.booleanOrNull ?: true
            val cmd = if (short) arrayOf("git", "status", "--short", "--branch")
                      else arrayOf("git", "status")
            val output = gitExec(*cmd)
            listOf(UIMessagePart.Text(buildJsonObject { put("output", output) }.toString()))
        }},
    ),
    Tool(
        name = "git_diff",
        description = "Show changes between commits, index, and working tree. Supports --cached, paths, and commit ranges.",
        permissionMode = PermissionMode.READ_ONLY,
        parameters = {{
            InputSchema.Obj(properties = buildJsonObject {
                put("path", buildJsonObject { put("type", "string"); put("description", "File path") })
                put("staged", buildJsonObject { put("type", "boolean"); put("description", "Show staged changes") })
                put("commit", buildJsonObject { put("type", "string"); put("description", "Commit/branch to diff against") })
            })
        }},
        execute = {{
            val obj = it.jsonObject
            val args = mutableListOf("diff")
            if (obj["staged"]?.jsonPrimitive?.booleanOrNull == true) args.add("--cached")
            obj["path"]?.jsonPrimitive?.contentOrNull?.let { args.addAll(listOf("--", it)) }
            obj["commit"]?.jsonPrimitive?.contentOrNull?.let { args.add(it) }
            listOf(UIMessagePart.Text(buildJsonObject { put("output", gitExec(*args.toTypedArray())) }.toString()))
        }},
    ),
    Tool(
        name = "git_log",
        description = "Show commit history. Defaults to last 20 commits. Supports author/date/path filters.",
        permissionMode = PermissionMode.READ_ONLY,
        parameters = {{
            InputSchema.Obj(properties = buildJsonObject {
                put("count", buildJsonObject { put("type", "integer"); put("description", "Max commits (default 20)") })
                put("author", buildJsonObject { put("type", "string"); put("description", "Filter by author") })
                put("since", buildJsonObject { put("type", "string"); put("description", "Since date e.g. 2024-01-01") })
                put("oneline", buildJsonObject { put("type", "boolean"); put("description", "Compact format") })
                put("path", buildJsonObject { put("type", "string"); put("description", "Filter by file path") })
            })
        }},
        execute = {{
            val obj = it.jsonObject
            val args = mutableListOf("log")
            val count = obj["count"]?.jsonPrimitive?.intOrNull ?: 20
            args.add("-n$count")
            if (obj["oneline"]?.jsonPrimitive?.booleanOrNull == true) args.add("--oneline")
            obj["author"]?.jsonPrimitive?.contentOrNull?.let { args.add("--author=$it") }
            obj["since"]?.jsonPrimitive?.contentOrNull?.let { args.add("--since=$it") }
            obj["path"]?.jsonPrimitive?.contentOrNull?.let { args.addAll(listOf("--", it)) }
            listOf(UIMessagePart.Text(buildJsonObject { put("output", gitExec(*args.toTypedArray())) }.toString()))
        }},
    ),
    Tool(
        name = "git_show",
        description = "Show a commit with diff, stat, or metadata. Format: patch (default), stat, metadata.",
        permissionMode = PermissionMode.READ_ONLY,
        parameters = {{
            InputSchema.Obj(properties = buildJsonObject {
                put("commit", buildJsonObject { put("type", "string"); put("description", "Commit hash or ref") })
                put("format", buildJsonObject {
                    put("type", "string"); put("enum", buildJsonArray { add("patch"); add("stat"); add("metadata") })
                })
                put("path", buildJsonObject { put("type", "string"); put("description", "Show only this file at the commit") })
            })
        }},
        execute = {{
            val obj = it.jsonObject
            val commit = obj["commit"]?.jsonPrimitive?.contentOrNull ?: error("commit required")
            val args = mutableListOf("show")
            when (obj["format"]?.jsonPrimitive?.contentOrNull) {
                "metadata" -> { args.add("--format=medium"); args.add("--no-patch") }
                "stat" -> args.add("--stat")
            }
            obj["path"]?.jsonPrimitive?.contentOrNull?.let { path ->
                args.add("$commit:$path")
            } ?: args.add(commit)
            listOf(UIMessagePart.Text(buildJsonObject { put("output", gitExec(*args.toTypedArray())) }.toString()))
        }},
    ),
    Tool(
        name = "git_blame",
        description = "Show what revision/author last modified each line of a file. Supports line range.",
        permissionMode = PermissionMode.READ_ONLY,
        parameters = {{
            InputSchema.Obj(properties = buildJsonObject {
                put("path", buildJsonObject { put("type", "string"); put("description", "File path") })
                put("start_line", buildJsonObject { put("type", "integer"); put("description", "Start line (1-based)") })
                put("end_line", buildJsonObject { put("type", "integer"); put("description", "End line (1-based)") })
            })
        }},
        execute = {{
            val obj = it.jsonObject
            val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
            val args = mutableListOf("blame")
            val start = obj["start_line"]?.jsonPrimitive?.intOrNull
            val end = obj["end_line"]?.jsonPrimitive?.intOrNull
            if (start != null && end != null) args.add("-L$start,$end")
            args.add(path)
            listOf(UIMessagePart.Text(buildJsonObject { put("output", gitExec(*args.toTypedArray())) }.toString()))
        }},
    ),
)

private fun gitExec(vararg args: String): String {
    val process = ProcessBuilder(*args)
        .directory(File("."))
        .redirectErrorStream(false)
        .start()
    val stdout = process.inputStream.bufferedReader().readText()
    val stderr = process.errorStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    if (exitCode != 0) error("Git failed: ${stderr.take(200)}")
    return stdout.trim()
}
