package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * 官方 SillyTavern 预设类型（preset-manager.js PresetManager.masterSections + openai.js）
 */
@Serializable
enum class PresetType {
    @SerialName("chat_completion") CHAT_COMPLETION,   // OpenAI Chat Completion 预设（整包 oai 设置）
    @SerialName("instruct") INSTRUCT,                 // Instruct Template（input_sequence/output_sequence）
    @SerialName("context") CONTEXT,                   // Context Template（story_string）
    @SerialName("sysprompt") SYSPROMPT,               // System Prompt（name+content）
    @SerialName("text_completion") TEXT_COMPLETION,   // Text Completion 预设（temp/top_k/top_p/rep_pen）
    @SerialName("reasoning") REASONING,               // Reasoning 模板（prefix/suffix/separator）
    @SerialName("start_reply_with") START_REPLY_WITH, // Start Reply With（value/show）
    @SerialName("unknown") UNKNOWN,
}

/**
 * 导入的官方预设存档。
 * 字段名对齐官方预设键（openai.js settingsToUpdate / preset-manager.js），
 * rawJson 整包保留实现无损往返（官方 savePreset 同样整包存库）。
 */
@Serializable
data class ChatPreset(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val type: PresetType = PresetType.UNKNOWN,
    // Chat / Text Completion 生成参数（官方键名）
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val minP: Float? = null,
    val frequencyPenalty: Float? = null,
    val presencePenalty: Float? = null,
    val repetitionPenalty: Float? = null,
    val maxTokens: Int? = null,
    val maxContext: Int? = null,
    val seed: Int? = null,
    // 其他类型的映射字段
    val systemPrompt: String? = null,      // SYSPROMPT: content
    val contextTemplate: String? = null,   // CONTEXT: story_string
    val messageTemplate: String? = null,   // INSTRUCT: input_sequence/output_sequence 拼接
    val rawJson: String = "",              // 官方原始 JSON 整包（无损往返）
)
