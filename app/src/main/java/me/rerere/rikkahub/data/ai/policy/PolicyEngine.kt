package me.rerere.rikkahub.data.ai.policy

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.PermissionMode
import me.rerere.ai.core.Tool
import android.os.Environment
import java.io.File

/** Result of a permission check. */
sealed class PermissionResult {
    data object Allowed : PermissionResult()
    data class Denied(val reason: String) : PermissionResult()
    data class NeedsApproval(val reason: String, val toolName: String, val args: Map<String, String>) : PermissionResult()
}

/**
 * 三闸门权限引擎（s03 标准）。
 *
 * Gate 1: Hard deny list — 永远禁止的操作（rm -rf /, sudo, mkfs...）
 * Gate 2: Rule matching — 取决于上下文的操作（写工作区外、危险命令）
 * Gate 3: User approval — 闸门2命中后，暂停等用户确认
 *
 * 三道都没命中 → 直接执行。大部分日常操作走这条路。
 */
class PolicyEngine(
    private val currentMode: PermissionMode = PermissionMode.DANGER_FULL_ACCESS,
    private val baseDir: String? = null,
) {
    private val pathChecker = PathScopeChecker(baseDir)

    // ── Gate 1: Hard deny list ──
    private val denyList = listOf(
        "rm -rf /" to "禁止删除根目录",
        "sudo" to "禁止使用 sudo",
        "shutdown" to "禁止关机",
        "reboot" to "禁止重启",
        "mkfs" to "禁止格式化磁盘",
        "dd if=" to "禁止磁盘写入",
        ":(){ :|:& };:" to "禁止 fork 炸弹",
        "> /dev/sda" to "禁止直接写入设备",
        "chmod 777 /" to "禁止根目录权限修改",
    )

    // ── Gate 2: Rule matching ──
    private data class PermissionRule(
        val tools: List<String>,
        val check: (toolName: String, args: JsonObject) -> Boolean,
        val message: String,
    )

    private val rules = listOf(
        PermissionRule(
            tools = listOf("execute_command", "bash"),
            check = { _, args ->
                val cmd = args["command"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
                cmd.contains("rm ") || cmd.contains("> /etc/") || cmd.contains("chmod 777")
            },
            message = "潜在危险命令",
        ),
        PermissionRule(
            tools = listOf("file"),
            check = { _, args ->
                val path = args["path"]?.jsonPrimitive?.contentOrNull ?: ""
                path.isNotBlank() && !pathChecker.isWithinWorkspace(path)
            },
            message = "写入工作区之外",
        ),
        PermissionRule(
            tools = listOf("file"),
            check = { _, args ->
                val path = args["path"]?.jsonPrimitive?.contentOrNull ?: ""
                path.isNotBlank() && File(path).let { it.isFile && it.length() > 50000 }
            },
            message = "修改大文件（>50KB）",
        ),
    )

    /**
     * 三闸门检���管线。
     */
    fun check(tool: Tool, args: JsonElement): PermissionResult {
        // 0. Permission level check (existing)
        if (tool.permissionMode.ordinal > currentMode.ordinal) {
            return PermissionResult.Denied(
                "工具 '${tool.name}' 需要 ${tool.permissionMode.name} 权限，" +
                "当前模式为 ${currentMode.name}。"
            )
        }

        val obj = args.jsonObject

        // Gate 1: Hard deny list
        if (tool.name == "execute_command" || tool.name == "bash") {
            val command = obj["command"]?.jsonPrimitive?.contentOrNull ?: ""
            for ((pattern, reason) in denyList) {
                if (command.contains(pattern, ignoreCase = true)) {
                    return PermissionResult.Denied("⛔ $reason: '$pattern'")
                }
            }
        }

        // Gate 2: Rule matching
        for (rule in rules) {
            if (tool.name in rule.tools && rule.check(tool.name, obj)) {
                val argMap = obj.keys.associateWith { key ->
                    obj[key]?.jsonPrimitive?.contentOrNull ?: obj[key].toString()
                }
                return PermissionResult.NeedsApproval(
                    reason = rule.message,
                    toolName = tool.name,
                    args = argMap,
                )
            }
        }

        // Path scope check
        val path = extractPath(tool.name, args)
        if (path != null && !pathChecker.isWithinWorkspace(path)) {
            return PermissionResult.Denied("路径 '$path' 超出当前工作区范围")
        }

        return PermissionResult.Allowed
    }

    fun isDenyListMatch(toolName: String, args: JsonElement): String? {
        if (toolName != "execute_command" && toolName != "bash") return null
        val command = args.jsonObject["command"]?.jsonPrimitive?.contentOrNull ?: return null
        for ((pattern, reason) in denyList) {
            if (command.contains(pattern, ignoreCase = true)) return reason
        }
        return null
    }

    private fun extractPath(toolName: String, args: JsonElement): String? {
        val obj = args.jsonObject
        return when (toolName) {
            "present_file",
            "convert_file" -> obj["path"]?.jsonPrimitive?.contentOrNull
            "file" -> {
                val action = obj["action"]?.jsonPrimitive?.contentOrNull ?: return@extractPath null
                when (action) {
                    "read", "write", "mkdir", "delete" -> obj["path"]?.jsonPrimitive?.contentOrNull
                    "copy", "move" -> obj["source"]?.jsonPrimitive?.contentOrNull
                    "list" -> obj["dir"]?.jsonPrimitive?.contentOrNull ?: obj["path"]?.jsonPrimitive?.contentOrNull
                    "search" -> obj["root"]?.jsonPrimitive?.contentOrNull
                    else -> null
                }
            }

            "execute_command", "execute_python", "eval_javascript" -> null
            else -> null
        }
    }
}

/**
 * Path scope checker — prevents directory traversal.
 */
class PathScopeChecker(private val baseDir: String? = null) {
    private val workspaceRoot: File? = baseDir?.let { File(it).absoluteFile }
    private val externalRoots = listOfNotNull(
        Environment.getExternalStorageDirectory().absoluteFile,
    )

    fun isWithinWorkspace(path: String): Boolean {
        if (path.isBlank()) return true
        val cleanPath = path.trim().trimStart('"', '\'').trimEnd('"', '\'')
        if (cleanPath.startsWith("$") || cleanPath.startsWith("~")) return true
        val target = File(cleanPath).absoluteFile
        if (!target.isAbsolute) return true // relative paths always OK
        // Check workspace sandbox
        val inWorkspace = workspaceRoot?.let { target.toPath().startsWith(it.toPath()) } ?: true
        if (inWorkspace) return true
        // Check external storage (user has MANAGE_EXTERNAL_STORAGE)
        return externalRoots.any { root -> target.toPath().startsWith(root.toPath()) }
    }
}
