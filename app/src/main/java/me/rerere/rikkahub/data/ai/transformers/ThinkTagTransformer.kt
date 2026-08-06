package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.time.Clock

private val THINKING_REGEX = Regex("<think>([\\s\\S]*?)(?:</think>|$)", RegexOption.DOT_MATCHES_ALL)

/**
 * 预设 reasoning 模板（prefix/suffix）→ 思维链解析正则。
 * 官方 reasoning.js 用 prefix/suffix 识别模型输出的思维链区域，
 * separator 分隔多段推理；本地默认 <think> 标签，导入 reasoning 预设后按模板解析。
 */
private fun reasoningRegex(prefix: String?, suffix: String?): Regex? {
    val p = prefix?.takeIf { it.isNotBlank() } ?: return null
    val s = suffix?.takeIf { it.isNotBlank() } ?: return null
    return Regex(Regex.escape(p) + "([\\s\\S]*?)(?:" + Regex.escape(s) + "|$)", RegexOption.DOT_MATCHES_ALL)
}

// 部分供应商不会返回reasoning parts, 所以需要这个transformer
object ThinkTagTransformer : OutputMessageTransformer {
    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return transformMessages(ctx, messages, finishAll = false)
    }

    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return transformMessages(ctx, messages, finishAll = true)
    }

    private fun transformMessages(
        ctx: TransformerContext,
        messages: List<UIMessage>,
        finishAll: Boolean,
    ): List<UIMessage> {
        val regex = reasoningRegex(ctx.assistant.reasoningPrefix, ctx.assistant.reasoningSuffix) ?: THINKING_REGEX
        val closingMarker = ctx.assistant.reasoningSuffix?.takeIf { it.isNotBlank() } ?: "</think>"
        val now = Clock.System.now()
        return messages.map { message ->
            if (message.role == MessageRole.ASSISTANT && message.hasPart<UIMessagePart.Text>()) {
                message.copy(
                    parts = message.parts.flatMap { part ->
                        if (part is UIMessagePart.Text && regex.containsMatchIn(part.text)) {
                            val stripped = part.text.replace(regex, "")
                            val reasoning =
                                regex.find(part.text)?.groupValues?.getOrNull(1)?.trim()
                                    ?: ""
                            val hasClosingTag = closingMarker.isNotEmpty() && part.text.contains(closingMarker)
                            listOf(
                                UIMessagePart.Reasoning(
                                    reasoning = reasoning,
                                    createdAt = message.createdAt.toInstant(timeZone = TimeZone.currentSystemDefault()),
                                    finishedAt = if (finishAll || hasClosingTag) now else null,
                                ),
                                part.copy(text = stripped),
                            )
                        } else {
                            listOf(part)
                        }
                    }
                )
            } else {
                message
            }
        }
    }
}
