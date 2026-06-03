package me.rerere.rikkahub.data.ai.hooks

import kotlinx.serialization.json.JsonElement
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/**
 * Hook 执行结果
 */
sealed class HookResult {
    /** 允许继续执行 */
    data object Allow : HookResult()
    /** 阻止执行，带上原因 */
    data class Block(val reason: String) : HookResult()
    /** 修改工具输入后继续 */
    data class ModifiedInput(val newArgs: JsonElement) : HookResult()
}

/**
 * 工具执行钩子，对标 Claude Code 的 PreToolUse / PostToolUse hooks。
 *
 * 在工具执行前后自动触发：
 * - PreToolUse: 执行前检查、修改参数、阻止执行
 * - PostToolUse: 执行后处理结果、自动格式化、日志记录
 */
interface ToolHook {
    val name: String
    val event: HookEvent
    suspend fun execute(
        tool: Tool,
        args: JsonElement,
        result: List<UIMessagePart>? = null,
    ): HookResult
}

enum class HookEvent {
    PRE_TOOL_USE,
    POST_TOOL_USE,
    STOP,
}

/**
 * Hook 注册表
 */
object HookRegistry {
    private val hooks = mutableListOf<ToolHook>()

    fun register(hook: ToolHook) {
        hooks.add(hook)
    }

    fun unregister(name: String) {
        hooks.removeAll { it.name == name }
    }

    fun getHooks(event: HookEvent): List<ToolHook> = hooks.filter { it.event == event }

    fun clear() {
        hooks.clear()
    }
}

/**
 * 安全钩子：阻止危险命令执行
 */
class SafetyHook : ToolHook {
    override val name = "safety"
    override val event = HookEvent.PRE_TOOL_USE

    private val blockedPatterns = listOf(
        "rm -rf /" to "禁止删除根目录",
        "mkfs" to "禁止格式化磁盘",
        ":(){ :|:& };:" to "禁止 fork 炸弹",
    )

    override suspend fun execute(
        tool: Tool,
        args: JsonElement,
        result: List<UIMessagePart>?,
    ): HookResult {
        if (tool.name != "execute_command") return HookResult.Allow
        val command = args.jsonObject["command"]?.toString() ?: return HookResult.Allow
        for ((pattern, reason) in blockedPatterns) {
            if (command.contains(pattern, ignoreCase = true)) {
                return HookResult.Block(reason)
            }
        }
        return HookResult.Allow
    }
}
