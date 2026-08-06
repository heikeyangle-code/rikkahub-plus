package me.rerere.rikkahub.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.ChatPreset
import me.rerere.rikkahub.data.model.PresetType

/**
 * 官方 SillyTavern 预设类型识别与解析。
 * 特征表 1:1 移植 preset-manager.js isPossibly* 系列 + openai.js settingsToUpdate：
 *   instruct → input_sequence + output_sequence
 *   context → story_string
 *   sysprompt → content
 *   text-completion → temp + top_k + top_p + rep_pen
 *   reasoning → prefix + suffix + separator
 *   start-reply-with → value + show
 *   chat-completion → openai_max_tokens / preset_settings_openai / temp_openai 等预设体键
 */
object PresetDetector {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): ChatPreset? = runCatching {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return null
        val type = detect(root)
        val name = (root["name"] as? JsonPrimitive)?.contentOrNull ?: ""
        val fallback = "未命名预设"
        when (type) {
            PresetType.CHAT_COMPLETION -> parseChatCompletion(root, name.ifBlank { fallback })
            PresetType.TEXT_COMPLETION -> parseTextCompletion(root, name.ifBlank { fallback })
            PresetType.SYSPROMPT -> ChatPreset(
                name = name.ifBlank { fallback },
                type = type,
                systemPrompt = (root["content"] as? JsonPrimitive)?.contentOrNull,
                rawJson = raw,
            )
            PresetType.CONTEXT -> ChatPreset(
                name = name.ifBlank { fallback },
                type = type,
                contextTemplate = (root["story_string"] as? JsonPrimitive)?.contentOrNull,
                rawJson = raw,
            )
            PresetType.INSTRUCT -> ChatPreset(
                name = name.ifBlank { fallback },
                type = type,
                messageTemplate = buildMessageTemplate(
                    input = (root["input_sequence"] as? JsonPrimitive)?.contentOrNull,
                    output = (root["output_sequence"] as? JsonPrimitive)?.contentOrNull,
                ),
                rawJson = raw,
            )
            else -> ChatPreset(
                name = name.ifBlank { fallback },
                type = type,
                rawJson = raw,
            )
        }
    }.getOrNull()

    /** 官方 isPossibly* 特征顺序（preset-manager.js performMasterImport） */
    fun detect(root: JsonObject): PresetType = when {
        hasAll(root, "input_sequence", "output_sequence") -> PresetType.INSTRUCT
        hasAll(root, "story_string") -> PresetType.CONTEXT
        hasAll(root, "content") -> PresetType.SYSPROMPT
        hasAll(root, "temp", "top_k", "top_p", "rep_pen") -> PresetType.TEXT_COMPLETION
        hasAll(root, "prefix", "suffix", "separator") -> PresetType.REASONING
        hasAll(root, "value", "show") -> PresetType.START_REPLY_WITH
        // Chat Completion 预设体（openai.js settingsToUpdate 的键），与上面 6 类特征互斥
        hasAny(root, "openai_max_tokens", "openai_max_context", "preset_settings_openai", "temp_openai", "freq_pen_openai") ->
            PresetType.CHAT_COMPLETION
        else -> PresetType.UNKNOWN
    }

    private fun parseChatCompletion(root: JsonObject, name: String): ChatPreset {
        val fileName = ""
        return ChatPreset(
            name = name.ifBlank { fileName },
            type = PresetType.CHAT_COMPLETION,
            temperature = float(root, "temperature"),
            topP = float(root, "top_p"),
            topK = int(root, "top_k"),
            minP = float(root, "min_p"),
            frequencyPenalty = float(root, "frequency_penalty"),
            presencePenalty = float(root, "presence_penalty"),
            repetitionPenalty = float(root, "repetition_penalty"),
            maxTokens = int(root, "openai_max_tokens"),
            maxContext = int(root, "openai_max_context"),
            seed = int(root, "seed"),
            rawJson = root.toString(),
        )
    }

    private fun parseTextCompletion(root: JsonObject, name: String): ChatPreset {
        return ChatPreset(
            name = name,
            type = PresetType.TEXT_COMPLETION,
            temperature = float(root, "temp"),
            topP = float(root, "top_p"),
            topK = int(root, "top_k"),
            minP = float(root, "min_p"),
            repetitionPenalty = float(root, "rep_pen"),
            rawJson = root.toString(),
        )
    }

    /** 官方 instruct 模板的输入/输出序列 → 本地 messageTemplate（"{{ message }}" 风格） */
    private fun buildMessageTemplate(input: String?, output: String?): String? {
        if (input == null && output == null) return null
        val inSeq = input ?: ""
        val outSeq = output ?: ""
        return listOf(inSeq, "{{ message }}", outSeq).joinToString("\n")
    }

    private fun hasAll(root: JsonObject, vararg keys: String): Boolean = keys.all { root[it] != null && root[it] !is JsonNull }
    private fun hasAny(root: JsonObject, vararg keys: String): Boolean = keys.any { root[it] != null && root[it] !is JsonNull }

    private fun float(root: JsonObject, key: String): Float? =
        (root[key] as? JsonPrimitive)?.floatOrNull?.takeIf { !it.isNaN() }

    private fun int(root: JsonObject, key: String): Int? =
        (root[key] as? JsonPrimitive)?.intOrNull
}

/**
 * 官方预设应用语义：预设里非 null 的字段覆盖助手当前设置，
 * null 的字段保持助手现状（官方 preset 应用同样整包覆盖当前设置）。
 */
fun ChatPreset.applyTo(assistant: Assistant): Assistant = assistant.copy(
    temperature = this.temperature ?: assistant.temperature,
    topP = this.topP ?: assistant.topP,
    maxTokens = this.maxTokens ?: assistant.maxTokens,
    maxContextTokens = this.maxContext ?: assistant.maxContextTokens,
    frequencyPenalty = this.frequencyPenalty ?: assistant.frequencyPenalty,
    presencePenalty = this.presencePenalty ?: assistant.presencePenalty,
    topK = this.topK ?: assistant.topK,
    minP = this.minP ?: assistant.minP,
    repetitionPenalty = this.repetitionPenalty ?: assistant.repetitionPenalty,
    seed = this.seed ?: assistant.seed,
    systemPrompt = this.systemPrompt ?: assistant.systemPrompt,
    contextTemplate = this.contextTemplate ?: assistant.contextTemplate,
    messageTemplate = this.messageTemplate ?: assistant.messageTemplate,
)
