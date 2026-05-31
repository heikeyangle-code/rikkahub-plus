package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.InjectionPosition

/**
 * Persona 注入 — 将激活的用户人设注入到系统提示词或对话头部
 */
object PersonaTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val persona = ctx.settings.personas.find { it.id == ctx.settings.activePersonaId }
            ?: return messages
        if (!persona.enabled || persona.description.isBlank()) return messages

        val personaMsg = UIMessage.user("[User Persona: ${persona.name}]\n${persona.description}")

        return when (persona.position) {
            me.rerere.rikkahub.data.model.PersonaInjectionPosition.BEFORE_SYSTEM ->
                listOf(personaMsg) + messages
            me.rerere.rikkahub.data.model.PersonaInjectionPosition.AFTER_SYSTEM ->
                if (messages.isNotEmpty()) {
                    listOf(messages.first()) + personaMsg + messages.drop(1)
                } else listOf(personaMsg) + messages
            me.rerere.rikkahub.data.model.PersonaInjectionPosition.TOP_OF_CHAT ->
                messages + personaMsg
        }
    }
}

/**
 * Author's Note 注入 — 在指定深度插入作者的引导
 */
object AuthorsNoteTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val note = ctx.settings.authorNote
        if (note.isBlank()) return messages

        // 频率检查
        if (ctx.settings.authorNoteFrequency < 1.0f) {
            if (kotlin.random.Random.nextFloat() > ctx.settings.authorNoteFrequency) return messages
        }

        val noteMsg = UIMessage.user("[Author's Note]\n$note")
        val depth = ctx.settings.authorNoteDepth.coerceAtLeast(1)
        val pos = ctx.settings.authorNotePosition

        return when (pos) {
            InjectionPosition.BEFORE_SYSTEM_PROMPT ->
                listOf(noteMsg) + messages
            InjectionPosition.AFTER_SYSTEM_PROMPT ->
                if (messages.isNotEmpty()) {
                    listOf(messages.first()) + noteMsg + messages.drop(1)
                } else listOf(noteMsg) + messages
            InjectionPosition.TOP_OF_CHAT -> {
                // 在第一条用户消息之前插入（系统消息之后）
                val userIdx = messages.indexOfFirst { it.role == MessageRole.USER }
                val insertIdx = if (userIdx >= 0) userIdx else messages.size
                messages.take(insertIdx) + noteMsg + messages.drop(insertIdx)
            }
            InjectionPosition.BOTTOM_OF_CHAT -> {
                // 在最后一条消息之前插入（紧邻最新消息）
                val insertIdx = (messages.size - 1).coerceAtLeast(0)
                messages.take(insertIdx) + noteMsg + messages.drop(insertIdx)
            }
            InjectionPosition.AUTHOR_NOTE -> {
                // 使用用户配置的实际位置（自引用时回退到 AFTER_SYSTEM_PROMPT）
                if (messages.isNotEmpty()) {
                    listOf(messages.first()) + noteMsg + messages.drop(1)
                } else listOf(noteMsg) + messages
            }
            InjectionPosition.AT_DEPTH -> {
                val insertIdx = (messages.size - depth).coerceAtLeast(0)
                messages.take(insertIdx) + noteMsg + messages.drop(insertIdx)
            }
        }
    }
}
