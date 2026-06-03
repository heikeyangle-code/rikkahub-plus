package me.rerere.rikkahub.data.ai.agent

/**
 * Fork Subagent — 对齐官方 forkSubagent.ts。
 *
 * 当 sub_agent 工具调用不传 subagent_type 时，自动进入 fork 模式：
 * - fork 子 agent 继承父 agent 的完全上下文（system prompt + 对话历史）
 * - fork 子 agent 共享父 agent 的工作空间（可以读写同样的文件）
 * - fork 在后台执行，完成时通知
 * - fork 不能嵌套（检查对话历史中的 fork 标签）
 *
 * 与官方区别：
 * - 官方 fork 需要 API 请求前缀完全一致以实现 prompt cache 共享
 * - Android/Rikkahub 无需 cache 优化，fork 即"继承上下文的后台子 agent"
 */

object ForkSubagent {

    /** Fork 标签，用于标记对话中的 fork 起源 */
    const val FORK_BOILERPLATE_TAG = "fork"
    const val FORK_DIRECTIVE_PREFIX = "DIRECTIVE: "

    /** Fork 子 agent 的合成类型名 */
    const val FORK_AGENT_TYPE = "fork"

    /**
     * 构建 fork 子 agent 的指令消息。
     * 对齐官方 buildChildMessage()。
     */
    fun buildChildMessage(directive: String): String {
        return """<$FORK_BOILERPLATE_TAG>
STOP. READ THIS FIRST.

You are a forked worker process. You are NOT the main agent.

RULES (non-negotiable):
1. Do NOT spawn sub-agents; execute directly.
2. Do NOT converse, ask questions, or suggest next steps
3. Do NOT add meta-commentary
4. USE your tools directly
5. If you modify files, commit your changes before reporting
6. Do NOT emit text between tool calls. Use tools silently, then report once at the end.
7. Stay strictly within your directive's scope
8. Keep your report under 500 words unless the directive specifies otherwise
9. Your response MUST begin with "Scope:". No preamble.
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
     * 检查对话历史中是否存在 fork 标签。
     * 用于拒绝嵌套 fork。
     * 对齐官方 isInForkChild()。
     */
    fun isInForkChild(conversationHistory: List<String>): Boolean {
        return conversationHistory.any { text ->
            text.contains("<$FORK_BOILERPLATE_TAG>")
        }
    }

    /**
     * 构建 fork 子 agent 的消息序列。
     * 对齐官方 buildForkedMessages()。
     *
     * @param directive fork 指令
     * @return 一系列消息：原始的 assistant tool_use 消息 + tool_results + 指令
     */
    fun buildForkedPrompt(
        parentAssistantMessage: String,
        directive: String,
    ): String {
        return buildString {
            appendLine(parentAssistantMessage)
            appendLine()
            appendLine("---")
            appendLine()
            append(buildChildMessage(directive))
        }
    }
}
