package me.rerere.rikkahub.data.ai.agent

/**
 * Fork Subagent — 对齐官方 forkSubagent.ts（第 1-212 行）。
 *
 * 当 sub_agent 工具调用不传 subagent_type 时，自动进入 fork 模式：
 * - fork 子 agent 继承父 agent 的完全上下文（system prompt + 对话历史）
 * - fork 在后台执行，完成时通知
 * - fork 不能嵌套（检查对话历史中的 fork 标签）
 */
object ForkSubagent {

    /** Fork 标签，用于标记对话中的 fork 起源。对齐 CC 第 8 行。 */
    const val FORK_BOILERPLATE_TAG = "fork"

    /** Fork 指令前缀。对齐 CC 第 9 行。 */
    const val FORK_DIRECTIVE_PREFIX = "DIRECTIVE: "

    /** Fork 子 agent 的合成类型名。对齐 CC 第 44 行。 */
    const val FORK_AGENT_TYPE = "fork"

    /**
     * 递归 fork 守卫。
     * 对齐 CC 第 80-91 行：isInForkChild()。
     * 检测对话历史中是否存在 fork 锅炉板标签。
     */
    fun isInForkChild(conversationHistory: List<String>): Boolean {
        return conversationHistory.any { text ->
            text.contains("<$FORK_BOILERPLATE_TAG>")
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
     * 构建 fork prompt 纯文本。
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
