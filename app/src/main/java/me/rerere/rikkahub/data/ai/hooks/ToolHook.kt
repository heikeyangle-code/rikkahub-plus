package me.rerere.rikkahub.data.ai.hooks

import kotlinx.serialization.json.JsonElement
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/**
 * 工具执行钩子接口，对标 Claude Code 的 PreToolUse / PostToolUse hooks。
 * 扩展：增加 UserPromptSubmit（用户输入注入）、Stop（会话停止摘要）。
 *
 * 四个标准事件点：
 * - USER_PROMPT_SUBMIT: 用户输入后、LLM 调用前注入上下文
 * - PRE_TOOL_USE: 工具执行前权限检查/修改入参
 * - POST_TOOL_USE: 工具执行后日志/通知
 * - STOP: 会话停止时生成摘要/清理
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
    USER_PROMPT_SUBMIT,
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

    fun getAllHooks(): List<ToolHook> = hooks.toList()

    fun clear() {
        hooks.clear()
    }

    fun hasHooks(event: HookEvent): Boolean = hooks.any { it.event == event }
}
