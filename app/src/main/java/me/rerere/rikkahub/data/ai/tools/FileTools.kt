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

    val skillsHint = if (skillDirs.isNotEmpty()) {
        " These are your working directories: ${skillDirs.joinToString()}."
    } else ""

    return listOf(
        // ── file_read ──
        Tool(
            name = "file_read",
            description = "Read a file from the Android filesystem. Returns the file content as text. " +
                "PREFERRED over execute_command for reading files. " +
                "Absolute paths work as-is. Relative paths are resolved against enabled skill directories first, then ${defaultDir}.$skillsHint",
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
                    "PREFERRED over execute_command for writing files. " +
                    "Relative paths go to skill dirs first, then ${defaultDir}.$skillsHint",
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
            description = buildString {
                append("List files. Default: $defaultDir/. App work dir: /data/data/me.rerere.rikkahub/files/.")
                if (skillDirs.isNotEmpty()) {
                    append(" Skills: ${skillDirs.joinToString()}.")
                }
            },
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
                    "Absolute paths work as-is. Relative paths resolve to skill dirs first, then ${defaultDir}.$skillsHint",
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
                    "Absolute paths work as-is. Relative paths resolve to skill dirs first, then ${defaultDir}.$skillsHint",
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
                    "Absolute paths work as-is. Relative paths resolve to skill dirs first, then ${defaultDir}.$skillsHint",
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
                    "Absolute paths work as-is. Relative paths resolve to skill dirs first, then ${defaultDir}.$skillsHint. " +
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
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}
