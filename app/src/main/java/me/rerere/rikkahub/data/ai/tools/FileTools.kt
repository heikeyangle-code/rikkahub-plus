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
        if (f.exists() || path.startsWith("/")) {
            // Guard: ensure resolved path stays within allowed area
            if (!path.startsWith("/")) {
                // Relative path starting from cwd — just return as-is (exists check passed)
                return f
            }
            return f
        }
        // 相对路径 → 依次检索 skill 目录
        for (skillDir in skillDirs) {
            val candidate = File(skillDir, path).normalize()
            if (candidate.exists()) {
                // Guard: ensure the resolved path is actually under the skill dir
                val canonicalSkill = File(skillDir).canonicalPath
                val canonicalCandidate = candidate.canonicalPath
                if (canonicalCandidate.startsWith(canonicalSkill)) return candidate
            }
        }
        // 兜底 Download
        val fallback = File(defaultDir, path).normalize()
        val canonicalDownload = File(defaultDir).canonicalPath
        val canonicalFallback = fallback.canonicalPath
        return if (canonicalFallback.startsWith(canonicalDownload)) fallback
        else File(defaultDir, fallback.name).normalize()
    }

    fun resolveDestPath(path: String): File {
        val f = File(path)
        if (path.startsWith("/")) return f
        // 相对路径 → 优先 skill 目录
        for (skillDir in skillDirs) {
            val candidate = File(skillDir, path).normalize()
            val canonicalSkill = File(skillDir).canonicalPath
            val canonicalCandidate = candidate.canonicalPath
            if (candidate.exists() && canonicalCandidate.startsWith(canonicalSkill)) return candidate
        }
        if (skillDirs.isNotEmpty()) {
            val candidate = File(skillDirs.first(), path).normalize()
            val canonicalSkill = File(skillDirs.first()).canonicalPath
            val canonicalCandidate = candidate.canonicalPath
            if (canonicalCandidate.startsWith(canonicalSkill)) return candidate
        }
        val fallback = File(defaultDir, path).normalize()
        val canonicalDownload = File(defaultDir).canonicalPath
        val canonicalFallback = fallback.canonicalPath
        return if (canonicalFallback.startsWith(canonicalDownload)) fallback
        else File(defaultDir, fallback.name).normalize()
    }

    val writeHint = if (skillDirs.isNotEmpty()) {
        " Skills dir: ${skillDirs.joinToString()}. For saving new skills to a skill directory, use this path."
    } else ""

    val moveHint = if (skillDirs.isNotEmpty()) {
        " Skills dir: ${skillDirs.joinToString()}. Use this as destination to move/copy skills into the working directory."
    } else ""

    return listOf(
        // ── file_read ──
        Tool(
            name = "file_read",
            description = "Read a file from the local filesystem. You can access any file directly by using this tool.\nAssume this tool is able to read all files on the device. If the user provides a path to a file assume that path is valid. It is okay to read a file that does not exist; an error will be returned.\n\nUsage:\n- The path parameter must be an absolute path for reliable access\n- Relative paths also supported, resolve against the default directory\n- By default, it reads up to 2000 lines starting from the beginning of the file\n- You can optionally specify offset and limit (handy for large files)\n- When you know which part of the file you need, only read that part\n- Results show line numbers starting at 1\n- This tool can read images (PNG, JPG). When reading an image the content is presented visually\n- This tool can only read files, not directories. To read a directory, use file_list\n- The tool reports if a file is unchanged since last read",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "Absolute or relative file path.")
                        })
                        put("offset", buildJsonObject {
                            put("type", "integer")
                            put("description", "Starting line number (1-indexed). Default: 1")
                        })
                        put("limit", buildJsonObject {
                            put("type", "integer")
                            put("description", "Max lines to read. Default: 2000 (full file if smaller)")
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
                    val offset = args.jsonObject["offset"]?.jsonPrimitive?.intOrNull ?: 1
                    val limit = args.jsonObject["limit"]?.jsonPrimitive?.intOrNull ?: 2000
                    val lines = file.readLines()
                    val totalLines = lines.size
                    val startIdx = (offset - 1).coerceIn(0, totalLines - 1)
                    val endIdx = (startIdx + limit).coerceAtMost(totalLines)
                    val selected = lines.subList(startIdx, endIdx)
                    val result = buildString {
                        selected.forEachIndexed { idx, line ->
                            appendLine("${startIdx + idx + 1}|$line")
                        }
                        if (endIdx < totalLines) {
                            appendLine("... (${totalLines - endIdx} more lines, total $totalLines)")
                        }
                    }
                    listOf(UIMessagePart.Text(result))
                }
            },
        ),

        // ── file_write ──
        Tool(
            name = "file_write",
            description = "Write a file to the local filesystem.\n\nUsage:\n- This tool will overwrite the existing file if there is one at the provided path.\n- If this is an existing file, you MUST use file_read tool first to read the file's contents.\n- Prefer file_edit for modifying existing files — it only sends the diff. Only use this tool to create new files or for complete rewrites.\n- NEVER create documentation files (*.md) or README files unless explicitly requested by the User.\n- Only use emojis if the user explicitly requests it.\n" + writeHint,
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
            description = "List files and directories in the local filesystem. Use this when you need to:\n- See what files exist in a directory\n- Find files by browsing rather than searching\n- Verify the parent directory exists before writing\n\nUsage:\n- Shows file names with size and type info\n- Use file_search for pattern-based file finding\n- Prefer this over execute_command with ls for directory listing",
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
                    "Absolute paths work as-is. Relative paths go to ${defaultDir}.$moveHint",
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
                    "Absolute paths work as-is. Relative paths go to ${defaultDir}.$moveHint",
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

        // ── file_search（按名称或内容）──
        Tool(
            name = "file_search",
            description = "Search for files on the Android filesystem by name or content. Only use when the user asks you to find specific files.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("mode", buildJsonObject {
                            put("type", "string")
                            put("enum", buildJsonArray { add("name"); add("content") })
                            put("description", "name=by filename (glob), content=search inside files (default: name)")
                        })
                        put("pattern", buildJsonObject {
                            put("type", "string")
                            put("description", "Search pattern: filename glob (mode=name) or text/regex (mode=content)")
                        })
                        put("root", buildJsonObject {
                            put("type", "string")
                            put("description", "Directory to search under (default: /storage/emulated/0/Download)")
                        })
                        put("max_results", buildJsonObject {
                            put("type", "integer")
                            put("description", "Max results (default: 20, max: 100)")
                        })
                        // name-mode params
                        put("type_filter", buildJsonObject {
                            put("type", "string")
                            put("enum", buildJsonArray { add("all"); add("file"); add("dir") })
                            put("description", "For mode=name: filter by file/directory type (default: all)")
                        })
                        // content-mode params
                        put("file_pattern", buildJsonObject {
                            put("type", "string")
                            put("description", "For mode=content: only search files matching this glob (e.g. *.kt, *.json)")
                        })
                        put("use_regex", buildJsonObject {
                            put("type", "boolean")
                            put("description", "For mode=content: true if pattern is regex, false for plain text (default: false)")
                        })
                        put("context", buildJsonObject {
                            put("type", "integer")
                            put("description", "For mode=content: context lines before/after each match (default: 2)")
                        })
                    },
                    required = listOf("pattern"),
                )
            },
            execute = { args ->
                val obj = args.jsonObject
                val mode = obj["mode"]?.jsonPrimitive?.contentOrNull ?: "name"
                val pattern = obj["pattern"]?.jsonPrimitive?.contentOrNull ?: error("pattern required")
                val root = obj["root"]?.jsonPrimitive?.contentOrNull ?: "/storage/emulated/0/Download"
                val maxResults = (obj["max_results"]?.jsonPrimitive?.intOrNull ?: 20).coerceIn(1, 100)

                val rootDir = File(root)
                if (!rootDir.exists()) error("Directory not found: $root")
                if (!rootDir.isDirectory) error("Not a directory: $root")

                if (mode == "name") {
                    // ── 按文件名搜索 ──
                    val typeFilter = obj["type_filter"]?.jsonPrimitive?.contentOrNull ?: "all"
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
                        val size = if (f.isFile) " (${formatSize(f.length())})" else ""
                        results.add("$icon ${f.absolutePath}$size")
                    }
                    if (results.isEmpty()) {
                        listOf(UIMessagePart.Text("No files matching '$pattern' found under $root"))
                    } else {
                        listOf(UIMessagePart.Text("Found ${results.size} result(s) for '$pattern':\n${results.joinToString("\n")}"))
                    }
                } else {
                    // ── 按内容搜索 ──
                    val fileGlob = obj["file_pattern"]?.jsonPrimitive?.contentOrNull ?: ""
                    val useRegex = obj["use_regex"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                    val contextLines = (obj["context"]?.jsonPrimitive?.intOrNull ?: 2).coerceIn(0, 10)
                    val cMaxResults = maxResults.coerceAtMost(200)

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
                                if (results.size >= cMaxResults) return@forEach
                                try {
                                    val lines = file.readLines()
                                    var matchCount = 0
                                    lines.forEachIndexed { lineNum, line ->
                                        if (matchCount >= 5 || results.size >= cMaxResults) return@forEachIndexed
                                        if (searchRegex.containsMatchIn(line)) {
                                            matchCount++
                                            results.add("${file.absolutePath}:${lineNum + 1}")
                                            for (c in (lineNum - contextLines).coerceAtLeast(0) until lineNum) {
                                                sb.appendLine("  ${c + 1}: ${lines[c].take(120)}")
                                            }
                                            sb.appendLine("→ ${lineNum + 1}: ${line.take(120)}")
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
