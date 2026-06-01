package me.rerere.rikkahub.data.ai.transformers

import android.util.Log
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.knowledge.KnowledgeBaseService
import me.rerere.rikkahub.data.model.Assistant

private const val TAG = "KnowledgeBaseTransformer"

/**
 * 知识库检索注入 Transformer
 *
 * 在生成前检索知识库，将相关片段注入到 system prompt 中
 */
class KnowledgeBaseTransformer(
    private val knowledgeBaseService: KnowledgeBaseService,
) : InputMessageTransformer {

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val assistant = ctx.assistant
        if (!assistant.enableKnowledgeBase) return messages

        val query = messages.lastOrNull()?.toText() ?: return messages
        if (query.isBlank()) return messages

        try {
            val results = knowledgeBaseService.search(
                query = query,
                assistantId = assistant.id.toString(),
                topK = 5,
                scoreThreshold = 0.25f,
            )

            if (results.isEmpty()) return messages

            val contextText = buildString {
                appendLine("\n\n以下是从知识库中找到的相关信息（请优先基于这些内容回答）：")
                results.forEachIndexed { i, result ->
                    appendLine("\n--- 资料${i + 1}: ${result.source.name} ---")
                    appendLine(result.chunk.text)
                }
                appendLine("\n--- 知识库结束 ---")
            }

            // 注入到 system prompt 位置（第一条 system 消息的末尾）
            val injected = messages.toMutableList()
            val systemIndex = injected.indexOfFirst { it.role == me.rerere.ai.core.MessageRole.SYSTEM }
            if (systemIndex >= 0) {
                val sysMsg = injected[systemIndex]
                val newText = sysMsg.toText() + contextText
                injected[systemIndex] = sysMsg.copy(
                    parts = sysMsg.parts.toMutableList().apply {
                        add(UIMessagePart.Text(contextText))
                    }
                )
            }
            // 如果没有system消息，加在开头
            // else {
            //     injected.add(0, UIMessage.system(contextText))
            // }

            return injected
        } catch (e: Exception) {
            Log.e(TAG, "Knowledge base search failed", e)
            return messages
        }
    }
}

private fun UIMessage.toText(): String =
    parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }
