package me.rerere.rikkahub.data.ai.hooks

import kotlinx.serialization.json.JsonElement
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/**
 * 工具执行钩子接口，对标 Claude Code 的 PreToolUse / PostToolUse hooks。
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
 * Hook 执行结果
 */
sealed class HookResult {
    data object Allow : HookResult()
    data class Block(val reason: String) : HookResult()
    data class ModifiedInput(val newArgs: JsonElement) : HookResult()
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
