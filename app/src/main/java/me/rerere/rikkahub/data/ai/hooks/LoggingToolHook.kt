package me.rerere.rikkahub.data.ai.hooks

import android.util.Log
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

private const val TAG = "ToolHook"

/**
 * 默认 ToolHook 实现 — 日志记录 + 状态追踪。
 *
 * 每个事件点一个实例，注册到全局 HookRegistry。
 * 覆盖所有 7 个事件：
 * - USER_PROMPT_SUBMIT：记录用户输入摘要
 * - PRE_TOOL_USE：记录工具调用参数（放行）
 * - POST_TOOL_USE：记录工具结果大小
 * - STOP：标记生成结束
 * - SUBAGENT_START/STOP：标记子 Agent 生命周期
 * - COMPACT：记录压缩信息
 */
class LoggingToolHook private constructor(
    override val name: String,
    override val event: HookEvent,
) : ToolHook {

    override suspend fun execute(
        tool: Tool,
        args: JsonElement,
        result: List<UIMessagePart>?,
    ): HookResult {
        return when (event) {
            HookEvent.USER_PROMPT_SUBMIT -> {
                val snippet = args.jsonObject["messages"]?.jsonPrimitive?.content?.take(80) ?: ""
                Log.i(TAG, "[USER_PROMPT_SUBMIT] tool=${tool.name} input=\"$snippet\"")
                HookResult.Allow
            }

            HookEvent.PRE_TOOL_USE -> {
                val inputSnippet = args.toString().take(120)
                Log.i(TAG, "[PRE_TOOL_USE] tool=${tool.name} args=$inputSnippet")
                HookResult.Allow
            }

            HookEvent.POST_TOOL_USE -> {
                val resultLen = result?.sumOf { p -> (p as? UIMessagePart.Text)?.text?.length ?: 0 } ?: 0
                Log.i(TAG, "[POST_TOOL_USE] tool=${tool.name} result=${resultLen}chars")
                HookResult.Allow
            }

            HookEvent.STOP -> {
                Log.i(TAG, "[STOP] generation ended, no tool calls")
                HookResult.Allow
            }

            HookEvent.SUBAGENT_START -> {
                Log.i(TAG, "[SUBAGENT_START] agentType=${tool.name}")
                HookResult.Allow
            }

            HookEvent.SUBAGENT_STOP -> {
                Log.i(TAG, "[SUBAGENT_STOP] agentType=${tool.name}")
                HookResult.Allow
            }

            HookEvent.COMPACT -> {
                val removed = args.jsonObject["removed"]?.jsonPrimitive?.content ?: "?"
                Log.i(TAG, "[COMPACT] removed=$removed messages")
                HookResult.Allow
            }
        }
    }

    companion object {
        private var registered = false

        /** 注册所有事件点的默认 Logger（幂等） */
        fun registerAll() {
            if (registered) return
            HookEvent.entries.forEach { event ->
                HookRegistry.register(LoggingToolHook("default_logger", event))
            }
            registered = true
            Log.i(TAG, "Registered LoggingToolHook for ${HookEvent.entries.size} events")
        }

        /** 注销所有 Logger 实例 */
        fun unregisterAll() {
            HookRegistry.getHooks(HookEvent.USER_PROMPT_SUBMIT)
                .filter { it.name == "default_logger" }
                .forEach { HookRegistry.unregister(it.name) }
            HookEvent.entries.forEach { event ->
                HookRegistry.getHooks(event)
                    .filter { it.name == "default_logger" }
                    .forEach { HookRegistry.unregister(it.name) }
            }
            registered = false
        }
    }
}
