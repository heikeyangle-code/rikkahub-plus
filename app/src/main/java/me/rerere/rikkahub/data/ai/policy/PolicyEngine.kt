package me.rerere.rikkahub.data.ai.policy

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.PermissionMode
import me.rerere.ai.core.Tool
import java.io.File

/** Result of a permission check. */
sealed class PermissionResult {
    data object Allowed : PermissionResult()
    data class Denied(val reason: String) : PermissionResult()
}

/**
 * Policy engine that enforces permission modes on tool execution.
 *
 * Checks:
 * 1. Tool's required permissionMode <= current mode
 * 2. File path arguments stay within workspace bounds
 */
class PolicyEngine(
    private val currentMode: PermissionMode = PermissionMode.DANGER_FULL_ACCESS,
    private val baseDir: String? = null,
) {
    private val pathChecker = PathScopeChecker(baseDir)

    /**
     * Check if a tool call is permitted under the current policy.
     *
     * @param tool The tool definition with its required permission
     * @param args The JSON arguments passed to the tool
     * @return Allowed or Denied with reason
     */
    fun check(tool: Tool, args: JsonElement): PermissionResult {
        // 1. Permission level check
        if (tool.permissionMode.ordinal > currentMode.ordinal) {
            return PermissionResult.Denied(
                "工具 '${tool.name}' 需要 ${tool.permissionMode.name} 权限，" +
                "当前模式为 ${currentMode.name}。" +
                "请使用 exit_plan_mode 退出计划模式后再试。"
            )
        }

        // 2. Path scope check for file operations
        val path = extractPath(tool.name, args)
        if (path != null && !pathChecker.isWithinWorkspace(path)) {
            return PermissionResult.Denied(
                "路径 '$path' 超出当前工作区范围，无法执行。" +
                "如需访问外部路径，请切换到 danger_full_access 模式。"
            )
        }

        return PermissionResult.Allowed
    }

    /** Extract the 'path' or 'dir' argument from common file tools. */
    private fun extractPath(toolName: String, args: JsonElement): String? {
        val obj = args.jsonObject
        return when (toolName) {
            "file_read", "file_write", "file_delete",
            "file_copy", "file_move", "present_file",
            "convert_file" -> obj["path"]?.jsonPrimitive?.contentOrNull

            "file_list", "file_mkdir", "file_search" -> obj["dir"]?.jsonPrimitive?.contentOrNull
                ?: obj["path"]?.jsonPrimitive?.contentOrNull

            "execute_command", "execute_python",
            "eval_javascript" -> null // shell commands checked separately

            else -> null
        }
    }
}

/**
 * Checks whether a given file path stays within the current working directory.
 * Prevents directory traversal attacks.
 */
class PathScopeChecker(private val baseDir: String? = null) {
    /**
     * Returns true if the path is within the current workspace.
     *
     * Rules:
     * - Relative paths without traversal: allowed
     * - Absolute paths: must start with the workspace base directory
     * - Parent-directory traversal (..) outside workspace: denied
     * - Symlinks: resolved to canonical path before comparison
     */
    fun isWithinWorkspace(path: String): Boolean {
        if (path.isBlank()) return true

        val trimmed = path.trim()
        // Remove shell quoting
        val cleanPath = trimmed.trimStart('"', '\'').trimEnd('"', '\'')

        // Skip shell variables and obvious non-paths
        if (cleanPath.startsWith('$') || cleanPath.startsWith('-')) return true

        val cwd = baseDir?.let { File(it) } ?: File(".").canonicalFile
        val target = File(cleanPath)

        return try {
            if (target.isAbsolute) {
                // Absolute path: must be inside CWD
                val canonical = target.canonicalFile
                canonical.toPath().startsWith(cwd.toPath())
            } else {
                // Relative path: deny explicit upward traversal
                val normalized = target.normalize().path
                !normalized.startsWith("..") && !normalized.contains(".." + File.separator)
            }
        } catch (e: Exception) {
            // If we can't resolve, deny to be safe
            false
        }
    }
}
