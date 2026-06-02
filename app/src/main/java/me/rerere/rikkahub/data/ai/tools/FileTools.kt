package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.io.File

/**
 * 文件操作工具 — file_read, file_write, file_list
 * AI 可直接读写 Android 文件系统中的文件。
 * skillDirs: 已启用的 skill 目录列表，用于解析相对路径（优先检索）
 */
fun createFileTools(skillDirs: List<String> = emptyList()): List<Tool> {
    val defaultDir = "/storage/emulated/0/Download"

    fun resolveFile(path: String): File {
        val f = File(path)
        if (f.exists() || path.startsWith("/")) return f
        // 相对路径 → 依次检索 skill 目录
        for (skillDir in skillDirs) {
            val candidate = File(skillDir, path)
            if (candidate.exists()) return candidate
        }
        // 兜底 Download
        return File(defaultDir, path)
    }

    fun resolveDestPath(path: String): File {
        val f = File(path)
        if (path.startsWith("/")) return f
        // 相对路径 → 优先 skill 目录
        for (skillDir in skillDirs) {
            val candidate = File(skillDir, path)
            if (candidate.exists() || skillDirs.size == 1) return candidate
        }
        return File(defaultDir, path)
    }

    val writeHint = if (skillDirs.isNotEmpty()) {
        " Skills dir: ${skillDirs.joinToString()}. For saving new skills to a skill directory, use this path."
    } else ""

    return listOf(
        // ── file_read ──
        Tool(
            name = "file_read",
            description = "Read a file from the Android filesystem. Returns the file content as text. " +
                "Absolute paths work as-is. Relative paths resolve against ${defaultDir} first.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "Absolute or relative file path.")
                        })
                    },
                    required = listOf("path"),
                )
            },
            execute = { args ->
                val path = args.jsonObject["path"]?.jsonPrimitive?.content
                    ?: error("path required")
                val file = resolveFile(path)
                if (!file.exists()) error("File not found: $path")
                if (!file.canRead()) error("Cannot read file: $path")
                if (file.isDirectory) {
                    // Directory: list contents
                    val listing = file.listFiles()?.map { f ->
                        val icon = if (f.isDirectory) "📁" else "📄"
                        val size = if (f.isFile) " (${formatSize(f.length())})" else ""
                        "$icon ${f.name}$size"
                    }?.joinToString("\n") ?: "(empty)"
                    listOf(UIMessagePart.Text("[${file.absolutePath}] 目录内容:\n$listing"))
                } else {
                    if (file.length() > 5 * 1024 * 1024) error("文件超过 5MB，为防止内存溢出无法读取: $path")
                    val content = file.readText()
                    listOf(UIMessagePart.Text(content))
                }
            },
        ),

        // ── file_write ──
        Tool(
            name = "file_write",
            description = "Create or overwrite a file on the Android filesystem. " +
                    "Relative paths go to $defaultDir.$writeHint",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "File path. If relative (no leading /), saved under ${defaultDir}/")
                        })
                        put("content", buildJsonObject {
                            put("type", "string")
                            put("description", "Text content to write to the file")
                        })
                    },
                    required = listOf("path", "content"),
                )
            },
            execute = { args ->
                val obj = args.jsonObject
                val rawPath = obj["path"]?.jsonPrimitive?.content
                    ?: error("path required")
                val content = obj["content"]?.jsonPrimitive?.content
                    ?: error("content required")

                val path = resolveDestPath(rawPath)
                path.parentFile?.mkdirs()
                path.writeText(content)
                listOf(UIMessagePart.Text("OK: wrote ${content.length} bytes to ${path.absolutePath}"))
            },
        ),

        // ── file_list ──
        Tool(
            name = "file_list",
            description = "List files and directories. Default: ${defaultDir}/.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("dir", buildJsonObject {
                            put("type", "string")
                            put("description", "Directory to list. Defaults to ${defaultDir} if empty.")
                        })
                    },
                    required = emptyList(),
                )
            },
            execute = { args ->
                val dirPath = args.jsonObject["dir"]?.jsonPrimitive?.content
                    ?.takeIf { it.isNotBlank() } ?: defaultDir

                val dir = resolveFile(dirPath)
                if (!dir.exists()) error("Directory not found: $dirPath")
                if (!dir.isDirectory) error("Not a directory: $dirPath")

                val entries = dir.listFiles()?.sortedBy { it.name } ?: emptyList()
                val listing = buildString {
                    appendLine("Contents of ${dir.absolutePath} (${entries.size} items):")
                    appendLine()
                    for (f in entries) {
                        val prefix = if (f.isDirectory) "📁" else "📄"
                        val size = if (f.isFile) " (${formatSize(f.length())})" else ""
                        appendLine("$prefix ${f.name}$size")
                    }
                }
                listOf(UIMessagePart.Text(listing))
            },
        ),

        // ── file_copy ──
        Tool(
            name = "file_copy",
            description = "Copy a file or directory from source to destination. " +
                    "Absolute paths work as-is. Relative paths go to ${defaultDir}.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("source", buildJsonObject {
                            put("type", "string")
                            put("description", "Source file or directory path")
                        })
                        put("destination", buildJsonObject {
                            put("type", "string")
                            put("description", "Destination file or directory path")
                        })
                    },
                    required = listOf("source", "destination"),
                )
            },
            execute = { args ->
                val obj = args.jsonObject
                val source = obj["source"]?.jsonPrimitive?.content ?: error("source required")
                val dest = obj["destination"]?.jsonPrimitive?.content ?: error("destination required")
                val srcFile = resolveFile(source)
                if (!srcFile.exists()) error("Source not found: $source")
                val dstFile = resolveDestPath(dest)
                dstFile.parentFile?.mkdirs()
                if (srcFile.isDirectory) {
                    srcFile.copyRecursively(dstFile, overwrite = true)
                } else {
                    srcFile.copyTo(dstFile, overwrite = true)
                }
                listOf(UIMessagePart.Text("OK: copied $source → $dest"))
            },
        ),

        // ── file_move ──
        Tool(
            name = "file_move",
            description = "Move or rename a file or directory. " +
                    "Absolute paths work as-is. Relative paths go to ${defaultDir}.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("source", buildJsonObject {
                            put("type", "string")
                            put("description", "Source file or directory path")
                        })
                        put("destination", buildJsonObject {
                            put("type", "string")
                            put("description", "Destination path (new location or name)")
                        })
                    },
                    required = listOf("source", "destination"),
                )
            },
            execute = { args ->
                val obj = args.jsonObject
                val source = obj["source"]?.jsonPrimitive?.content ?: error("source required")
                val dest = obj["destination"]?.jsonPrimitive?.content ?: error("destination required")
                val srcFile = resolveFile(source)
                if (!srcFile.exists()) error("Source not found: $source")
                val dstFile = resolveDestPath(dest)
                dstFile.parentFile?.mkdirs()
                if (!srcFile.renameTo(dstFile)) {
                    // renameTo can fail across mount points — fallback to copy+delete
                    if (srcFile.isDirectory) {
                        srcFile.copyRecursively(dstFile, overwrite = true)
                        srcFile.deleteRecursively()
                    } else {
                        srcFile.copyTo(dstFile, overwrite = true)
                        srcFile.delete()
                    }
                }
                listOf(UIMessagePart.Text("OK: moved $source → $dest"))
            },
        ),

        // ── file_mkdir ──
        Tool(
            name = "file_mkdir",
            description = "Create a new directory (and parent directories if needed). " +
                    "Absolute paths work as-is. Relative paths go to ${defaultDir}.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "Directory path to create")
                        })
                    },
                    required = listOf("path"),
                )
            },
            execute = { args ->
                val path = args.jsonObject["path"]?.jsonPrimitive?.content
                    ?: error("path required")
                val dir = resolveDestPath(path)
                if (dir.exists() && dir.isDirectory) {
                    listOf(UIMessagePart.Text("Directory already exists: $path"))
                } else {
                    val created = dir.mkdirs()
                    if (created) {
                        listOf(UIMessagePart.Text("OK: created directory $path"))
                    } else {
                        error("Failed to create directory: $path")
                    }
                }
            },
        ),

        // ── file_delete ──
        Tool(
            name = "file_delete",
            description = "Delete a file or directory from the Android filesystem. " +
                    "Directories are deleted recursively (all contents removed). " +
                    "Absolute paths work as-is. Relative paths go to ${defaultDir}. " +
                    "WARNING: This is destructive and irreversible.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "File or directory path to delete")
                        })
                    },
                    required = listOf("path"),
                )
            },
            execute = { args ->
                val path = args.jsonObject["path"]?.jsonPrimitive?.content
                    ?: error("path required")
                val file = resolveFile(path)
                if (!file.exists()) error("File not found: $path")
                if (!file.canWrite()) error("Cannot delete (no write permission): $path")
                val deleted = if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
                if (deleted) {
                    val type = if (file.isDirectory) "directory" else "file"
                    listOf(UIMessagePart.Text("OK: deleted $type $path"))
                } else {
                    error("Failed to delete: $path")
                }
            },
        ),

        // ── file_search ──
        Tool(
            name = "file_search",
            description = "Search for files on the Android filesystem by name pattern (glob). " +
                    "Returns matching file paths. Use this when the user wants to find a file but doesn't know where it is. " +
                    "Only searches under the app's accessible paths and Download directory.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("pattern", buildJsonObject {
                            put("type", "string")
                            put("description", "File name pattern (e.g. '*.pdf', '*report*', 'photo*.jpg')")
                        })
                        put("root", buildJsonObject {
                            put("type", "string")
                            put("description", "Optional root directory to search under. Default: /storage/emulated/0/Download")
                        })
                        put("max_results", buildJsonObject {
                            put("type", "integer")
                            put("description", "Maximum number of results to return (default: 20, max: 100)")
                        })
                        put("type_filter", buildJsonObject {
                            put("type", "string")
                            put("enum", buildJsonArray { add("all"); add("file"); add("dir") })
                            put("description", "Filter by file/directory type (default: all)")
                        })
                    },
                    required = listOf("pattern"),
                )
            },
            execute = { args ->
                val obj = args.jsonObject
                val pattern = obj["pattern"]?.jsonPrimitive?.contentOrNull ?: error("pattern required")
                val root = obj["root"]?.jsonPrimitive?.contentOrNull ?: "/storage/emulated/0/Download"
                val maxResults = (obj["max_results"]?.jsonPrimitive?.intOrNull ?: 20).coerceIn(1, 100)
                val typeFilter = obj["type_filter"]?.jsonPrimitive?.contentOrNull ?: "all"

                val rootDir = File(root)
                if (!rootDir.exists()) error("Directory not found: $root")
                if (!rootDir.isDirectory) error("Not a directory: $root")

                val regex = pattern.toGlobRegex()
                val results = mutableListOf<String>()
                val walker = rootDir.walkTopDown().filter { f ->
                    if (results.size >= maxResults) return@filter false
                    if (typeFilter == "file" && f.isDirectory) return@filter false
                    if (typeFilter == "dir" && f.isFile) return@filter false
                    regex.matches(f.name)
                }
                for (f in walker) {
                    if (results.size >= maxResults) break
                    val icon = if (f.isDirectory) "📁" else "📄"
                    val size = if (f.isFile) " (${fmtSize(f.length())})" else ""
                    results.add("$icon ${f.absolutePath}$size")
                }

                if (results.isEmpty()) {
                    listOf(UIMessagePart.Text("No files matching '$pattern' found under $root"))
                } else {
                    listOf(UIMessagePart.Text("Found ${results.size} result(s) for '$pattern':\n${results.joinToString("\n")}"))
                }
            },
        ),

        // ── grep_search（在文件内容中搜索文本）──
        Tool(
            name = "grep_search",
            description = "Search for text content inside files on the Android filesystem (like grep -r). " +
                    "Supports plain text or regex patterns. Can filter by file glob pattern. " +
                    "Returns matching file paths, line numbers, and the matching lines with context. " +
                    "Use this when the user wants to find code, configuration, or any text inside files.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("pattern", buildJsonObject {
                            put("type", "string")
                            put("description", "Text or regex pattern to search for")
                        })
                        put("root", buildJsonObject {
                            put("type", "string")
                            put("description", "Root directory to search under (default: /storage/emulated/0/Download)")
                        })
                        put("file_pattern", buildJsonObject {
                            put("type", "string")
                            put("description", "Optional file filter (glob), e.g. '*.kt', '*.json', 'build.gradle*'")
                        })
                        put("use_regex", buildJsonObject {
                            put("type", "boolean")
                            put("description", "Set true if pattern is a regex, false for plain text (default: false)")
                        })
                        put("context", buildJsonObject {
                            put("type", "integer")
                            put("description", "Number of context lines before/after each match (default: 2)")
                        })
                        put("max_results", buildJsonObject {
                            put("type", "integer")
                            put("description", "Max results (default: 30)")
                        })
                    },
                    required = listOf("pattern"),
                )
            },
            execute = { args ->
                val obj = args.jsonObject
                val pattern = obj["pattern"]?.jsonPrimitive?.contentOrNull ?: error("pattern required")
                val root = obj["root"]?.jsonPrimitive?.contentOrNull ?: "/storage/emulated/0/Download"
                val fileGlob = obj["file_pattern"]?.jsonPrimitive?.contentOrNull ?: ""
                val useRegex = obj["use_regex"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                val contextLines = (obj["context"]?.jsonPrimitive?.intOrNull ?: 2).coerceIn(0, 10)
                val maxResults = (obj["max_results"]?.jsonPrimitive?.intOrNull ?: 30).coerceIn(1, 200)

                val rootDir = File(root)
                if (!rootDir.exists()) error("Directory not found: $root")
                if (!rootDir.isDirectory) error("Not a directory: $root")

                val searchRegex = if (useRegex) {
                    try { Regex(pattern, setOf(RegexOption.IGNORE_CASE)) }
                    catch (e: Exception) { error("Invalid regex: ${e.message}") }
                } else {
                    Regex(Regex.escape(pattern), RegexOption.IGNORE_CASE)
                }
                val fileFilter = if (fileGlob.isNotBlank()) fileGlob.toGlobRegexForFile() else null

                val results = mutableListOf<String>()
                val sb = StringBuilder()

                try {
                    rootDir.walkTopDown()
                        .filter { it.isFile && it.length() > 0 && it.length() <= 512_000 }
                        .filter { f -> fileFilter?.matches(f.name) ?: true }
                        .forEach { file ->
                            if (results.size >= maxResults) return@forEach
                            try {
                                val lines = file.readLines()
                                var matchCount = 0
                                lines.forEachIndexed { lineNum, line ->
                                    if (matchCount >= 5 || results.size >= maxResults) return@forEachIndexed
                                    if (searchRegex.containsMatchIn(line)) {
                                        matchCount++
                                        val relPath = file.absolutePath.removePrefix(rootDir.absolutePath).trimStart('/')
                                        val tag = if (fileFilter != null) "" else relPath.takeLastWhile { it != '/' }
                                        results.add("${file.absolutePath}:${lineNum + 1}")
                                        // Context before
                                        for (c in (lineNum - contextLines).coerceAtLeast(0) until lineNum) {
                                            sb.appendLine("  ${c + 1}: ${lines[c].take(120)}")
                                        }
                                        sb.appendLine("→ ${lineNum + 1}: ${line.take(120)}")
                                        // Context after
                                        for (c in (lineNum + 1)..(lineNum + contextLines).coerceAtMost(lines.size - 1)) {
                                            sb.appendLine("  ${c + 1}: ${lines[c].take(120)}")
                                        }
                                        sb.appendLine()
                                    }
                                }
                            } catch (_: Exception) { /* skip unreadable files */ }
                        }
                } catch (_: Exception) { /* walk errors */ }

                if (results.isEmpty()) {
                    listOf(UIMessagePart.Text("No matches found for '$pattern' under $root${if (fileGlob.isNotBlank()) " in files matching '$fileGlob'" else ""}"))
                } else {
                    val summary = "Found ${results.size} match(es) for '$pattern'${if (fileGlob.isNotBlank()) " in $fileGlob files" else ""}:\n"
                    listOf(UIMessagePart.Text(summary + sb.toString().take(15000)))
                }
            },
        ),
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}

private fun fmtSize(bytes: Long): String = formatSize(bytes)

/** 将 glob 模式转换为正则表达式 */
private fun String.toGlobRegex(): Regex {
    val pattern = this
        .replace(".", "\\.")
        .replace("*", ".*")
        .replace("?", ".")
        .let { "^$it$" }
    return Regex(pattern, RegexOption.IGNORE_CASE)
}

/** 将 glob 模式转换为正则（用于 grep 的文件过滤） */
private fun String.toGlobRegexForFile(): Regex {
    val parts = split("/").map { part ->
        part.replace(".", "\\.").replace("*", ".*").replace("?", ".")
    }
    return Regex(parts.joinToString("/"), RegexOption.IGNORE_CASE)
}
