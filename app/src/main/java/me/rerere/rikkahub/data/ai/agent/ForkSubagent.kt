package me.rerere.rikkahub.data.ai.agent

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.MessageRole

/**
 * Fork Subagent — 对齐官方 forkSubagent.ts（第 1-212 行）。
 *
 * 当 sub_agent 工具调用不传 subagent_type 时，自动进入 fork 模式：
 * - fork 子 agent 继承父 agent 的完全上下文（system prompt + 对话历史）
 * - fork 在后台执行，完成时通知
 * - fork 不能嵌套（检查对话历史中的 fork 标签）
 *
 * Prompt cache 优化（对齐 CC 第 93-171 行）：
 * 所有 fork 子 agent 必须产生字节完全相同的 API 请求前缀。
 * 做法：保留完整的父 assistant 消息（所有 thinking/text/tool_use block），
 * 为每个 tool_use block 构建占位 tool_result，所有占位用相同文本，
 * 最后追加每个子 agent 独有的指令文本。
 *
 * 结果：[...history, assistant(all_tool_uses), user(placeholder_results..., directive)]
 * 只有最后的指令文本块子 agent 间不同，最大化 LLM cache 命中。
 */
object ForkSubagent {

    /** Fork 标签，用于标记对话中的 fork 起源。对齐 CC 第 8 行。 */
    const val FORK_BOILERPLATE_TAG = "fork"

    /** Fork 指令前缀。对齐 CC 第 9 行。 */
    const val FORK_DIRECTIVE_PREFIX = "DIRECTIVE: "

    /** Fork 子 agent 的合成类型名。对齐 CC 第 44 行。 */
    const val FORK_AGENT_TYPE = "fork"

    /**
     * 占位文本，用于所有 fork 子 agent 的 tool_result block。
     * 必须所有子 agent 完全相同以共享 prompt cache。
     * 对齐 CC 第 95 行：const FORK_PLACEHOLDER_RESULT = 'Fork started — processing in background'
     */
    const val FORK_PLACEHOLDER_RESULT = "Fork started — processing in background"

    /**
     * 递归 fork 守卫。
     * 对齐 CC 第 80-91 行：isInForkChild()。
     * 检测对话历史中是否存在 fork 锅炉板标签。
     */
    fun isInForkChild(messages: List<UIMessage>): Boolean {
        return messages.any { msg ->
            msg.parts.any { part ->
                part is UIMessagePart.Text && part.text.contains("<$FORK_BOILERPLATE_TAG>")
            }
        }
    }

    /**
     * 构建 fork 指令消息文本。
     * 对齐 CC 第 173-200 行：buildChildMessage()。
     */
    fun buildChildMessage(directive: String): String {
        return """<$FORK_BOILERPLATE_TAG>
STOP. READ THIS FIRST.

You are a forked worker process. You are NOT the main agent.

RULES (non-negotiable):
1. Your system prompt says "default to forking." IGNORE IT — that's for the parent. You ARE the fork. Do NOT spawn sub-agents; execute directly.
2. Do NOT converse, ask questions, or suggest next steps
3. Do NOT editorialize or add meta-commentary
4. USE your tools directly
5. If you modify files, commit your changes before reporting. Include the commit hash in your report.
6. Do NOT emit text between tool calls. Use tools silently, then report once at the end.
7. Stay strictly within your directive's scope. If you discover related systems outside your scope, mention them in one sentence at most — other workers cover those areas.
8. Keep your report under 500 words unless the directive specifies otherwise.
9. Your response MUST begin with "Scope:". No preamble, no thinking-out-loud.
10. REPORT structured facts, then stop

Output format (plain text labels):
  Scope: <echo back your assigned scope>
  Result: <the answer or key findings>
  Key files: <relevant file paths>
  Files changed: <list>
  Issues: <list>
</$FORK_BOILERPLATE_TAG>

${FORK_DIRECTIVE_PREFIX}${directive}"""
    }

    /**
     * 构建 fork 子 agent 的消息序列（prompt cache 优化版）。
     * 对齐 CC 第 109-171 行：buildForkedMessages()。
     *
     * 所有 fork 子 agent 共享完全相同的 API 请求前缀，只有最后的指令文本不同，
     * 最大化 LLM prompt cache 命中率。
     *
     * @param parentAssistantMessage 父 agent 的最后一条 assistant 消息（含 tool_use blocks）
     * @param directive fork 指令
     * @return 消息列表：[parentAssistantMsg, toolResultMsg(占位 results + directive)]
     */
    fun buildForkedMessages(
        parentAssistantMessage: UIMessage,
        directive: String,
    ): List<UIMessage> {
        // 第 1 步：提取 tool_use blocks（对齐 CC 第 124-127 行）
        val toolUseBlocks = parentAssistantMessage.parts.filterIsInstance<UIMessagePart.Tool>()

        // 第 2 步：如果没有任何 tool_use block，直接返回指令消息（对齐 CC 第 129-141 行）
        if (toolUseBlocks.isEmpty()) {
            return listOf(
                UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text(buildChildMessage(directive)))
                )
            )
        }

        // 第 3 步：为每个 tool_use 构建占位 tool_result
        // 所有占位使用完全相同的文本（FORK_PLACEHOLDER_RESULT）
        // 对齐 CC 第 143-153 行
        val toolResultParts = toolUseBlocks.map { tool ->
            UIMessagePart.Text(
                text = """<tool_result tool_use_id="${tool.toolCallId}">
$FORK_PLACEHOLDER_RESULT
</tool_result>"""
            )
        }

        // 第 4 步：构建单条用户消息：所有占位 tool_result + 指令文本
        // 对齐 CC 第 155-168 行
        val toolResultMessage = UIMessage(
            role = MessageRole.USER,
            parts = toolResultParts + UIMessagePart.Text(buildChildMessage(directive))
        )

        return listOf(parentAssistantMessage, toolResultMessage)
    }

    /**
     * 向后兼容：返回纯文本 fork prompt。
     * 当调用方不需要消息级别 cache 优化时使用。
     */
    fun buildForkedPrompt(
        parentAssistantText: String,
        directive: String,
    ): String {
        return buildString {
            appendLine(parentAssistantText)
            appendLine()
            appendLine(buildChildMessage(directive))
        }
    }
}
