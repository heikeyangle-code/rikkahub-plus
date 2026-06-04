package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage

/**
 * 管线上下文注入 Transformer。
 *
 * 在 LLM 调用前将额外上下文（cron 任务、后台通知、todo 提醒、状态摘要）
 * 注入到消息列表中。AI 能看到这些消息，但它们不会出现在输出 chunk 中，
 * 因此不会污染对话状态或 UI 渲染。
 *
 * 替换 AgentPipeline 中直接修改 pipelineMessages 的做法。
 */
class ContextInjectorTransformer(
    private val cronMessages: List<String> = emptyList(),
    private val backgroundNotifications: List<String> = emptyList(),
    /** null = 不注入提醒 */
    private val todoReminder: String? = null,
    /** null = 不注入状态摘要 */
    private val stateSummary: String? = null,
) : InputMessageTransformer {

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (cronMessages.isEmpty() && backgroundNotifications.isEmpty()
            && todoReminder == null && stateSummary == null
        ) {
            return messages
        }

        val injections = mutableListOf<UIMessage>()

        // 1. Cron 任务（最早注入，AI 最先看到）
        cronMessages.forEach { prompt ->
            injections.add(UIMessage.system("[Scheduled] $prompt"))
        }

        // 2. 后台任务结果
        backgroundNotifications.forEach { msg ->
            injections.add(UIMessage.system(msg))
        }

        // 3. Todo 提醒（保持 USER 角色，AI 视为用户交互）
        if (todoReminder != null) {
            injections.add(UIMessage.user(todoReminder))
        }

        // 4. 状态摘要
        if (stateSummary != null) {
            injections.add(UIMessage.system(stateSummary))
        }

        return messages + injections
    }
}
