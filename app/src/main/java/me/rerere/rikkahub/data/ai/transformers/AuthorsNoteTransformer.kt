package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.AuthorNotePosition
import me.rerere.rikkahub.data.model.PersonaInjectionPosition

object AuthorsNoteTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val settings = ctx.settings

        val persona = settings.personas.find { it.id == settings.activePersonaId }
        val personaActive = persona != null && persona.enabled && persona.description.isNotBlank() &&
            (persona.lockedCharacterIds.isEmpty() || ctx.assistant.id in persona.lockedCharacterIds)
        val personaDesc = persona?.takeIf { personaActive }?.description.orEmpty()
        val personaTop = personaActive && persona?.position == PersonaInjectionPosition.TOP_OF_CHAT
        val personaBottom = personaActive && persona?.position == PersonaInjectionPosition.BOTTOM_OF_CHAT

        // 官方：人设 TOP/BOTTOM 合并进导演备注并跟随其节奏；即使备注文本为空也会单独注入人设
        if (!settings.authorNoteEnabled || (settings.authorNote.isBlank() && !personaTop && !personaBottom)) {
            return messages
        }

        // 官方间隔语义（authors-note.js）：1=每次注入，0=关闭，N=当前对话用户消息数为 N 的整数倍时注入
        val interval = settings.authorNoteInterval
        val userCount = ctx.chatUserMessageCount ?: messages.count {
            it.role == MessageRole.USER && !it.isInjectedBlock()
        }
        val shouldInject = when {
            interval == 1 -> true
            interval <= 0 -> false
            else -> userCount >= interval && userCount % interval == 0
        }

        val noteText = when {
            personaTop -> "$personaDesc\n${settings.authorNote}"
            personaBottom -> "${settings.authorNote}\n$personaDesc"
            else -> settings.authorNote
        }

        // 内部标记：PlaceholderTransformer 发送前会剥离，避免注入块被当成真实消息
        val noteMsg = when (settings.authorNoteRole) {
            MessageRole.ASSISTANT -> UIMessage.assistant("[Author's Note]\n$noteText")
            MessageRole.USER -> UIMessage.user("[Author's Note]\n$noteText")
            else -> UIMessage.system("[Author's Note]\n$noteText")
        }

        // 官方无人设间隔概念：人设 TOP/BOTTOM 合并进备注后每条消息都注入（仅受备注开关门控）。
        // 本地 interval 是备注文本的扩展，非注入轮（interval>0 且未到倍数）人设单独注入，避免整条丢失；
        // interval<=0 视为备注整体关闭，人设也一并跳过（保持原有"0=关闭"语义）
        if (!shouldInject) {
            if (interval <= 0 || (!personaTop && !personaBottom)) return messages
            val personaMsg = when (settings.authorNoteRole) {
                MessageRole.ASSISTANT -> UIMessage.assistant("[User Persona]\n$personaDesc")
                MessageRole.USER -> UIMessage.user("[User Persona]\n$personaDesc")
                else -> UIMessage.system("[User Persona]\n$personaDesc")
            }
            return injectAt(settings, ctx, messages, personaMsg)
        }

        return injectAt(settings, ctx, messages, noteMsg)
    }
}

private fun injectAt(
    settings: me.rerere.rikkahub.data.datastore.Settings,
    ctx: TransformerContext,
    messages: List<UIMessage>,
    msg: UIMessage,
): List<UIMessage> = when (settings.authorNotePosition) {
    // Before Main Prompt / Story String：整个提示词最前面
    AuthorNotePosition.BEFORE_PROMPT -> listOf(msg) + messages

    // After Main Prompt / Story String：紧跟主提示词（官方插入到 main 集合末尾）
    AuthorNotePosition.IN_PROMPT -> {
        val idx = (messages.indexOfFirst { it.role == MessageRole.SYSTEM } + 1).coerceAtLeast(0)
        messages.take(idx) + msg + messages.drop(idx)
    }

    // In-chat @ Depth：从对话最末尾往前数 depth 条；depth 0 = 对话最末尾
    AuthorNotePosition.IN_CHAT -> {
        val chatSize = ctx.chatMessageCount ?: messages.size
        val depth = settings.authorNoteDepth.coerceAtLeast(0)
        val insertIdx = findSafeInsertIndex(
            messages,
            (messages.size - minOf(depth, chatSize))
                .coerceIn(messages.size - chatSize, messages.size),
        )
        messages.take(insertIdx) + msg + messages.drop(insertIdx)
    }
}

/** 注入块内部标记识别（与 PlaceholderTransformer 的剥离逻辑保持一致） */
internal fun UIMessage.isInjectedBlock(): Boolean {
    val text = parts.filterIsInstance<me.rerere.ai.ui.UIMessagePart.Text>()
        .joinToString("") { it.text }
    return text.startsWith("[Author's Note]") || text.startsWith("[User Persona]")
}
