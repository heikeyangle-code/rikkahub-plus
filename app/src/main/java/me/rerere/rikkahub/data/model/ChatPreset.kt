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
    // 官方其他可映射设置（openai.js settingsToUpdate）
    val stream: Boolean? = null,           // stream_openai → 本地 streamOutput
    val enableWebSearch: Boolean? = null,  // enable_web_search → 本地 enableWebSearch
    val toolRecurringLimit: Int? = null,   // tool_call_recurse_limit → 本地 toolRecurringLimit
    val reasoningEffort: String? = null,   // reasoning_effort（auto/min/low/medium/high/max）→ ReasoningLevel
    val modelName: String? = null,         // openai_model / claude_model / google_model（按内容取其一）
    // 官方行为开关（openai.js settingsToUpdate，非 null 时覆盖助手）
    val useSysprompt: Boolean? = null,         // use_sysprompt：false 时 system 降级为 user 角色（仅 Claude/Google 后端，OpenAI 不受影响）
    val squashSystemMessages: Boolean? = null, // squash_system_messages：合并相邻 system 消息
    val continuePrefill: Boolean? = null,      // continue_prefill：true=最后一条消息作为预填继续，false=末尾追加 nudge 提示词
    val assistantPrefill: String? = null,      // assistant_prefill：continue 预填前缀（仅 Claude 语义）
    val newChatPrompt: String? = null,         // new_chat_prompt：对话历史前注入的起始提示
    val newGroupChatPrompt: String? = null,    // new_group_chat_prompt：群聊版（{{group}} 宏）
    val continueNudgePrompt: String? = null,   // continue_nudge_prompt：续写引导（null 用官方默认文本）
    val groupNudgePrompt: String? = null,      // group_nudge_prompt：群聊引导（null 用官方默认文本）
    val maxContextUnlocked: Boolean? = null,   // max_context_unlocked：解锁模型上下文上限到 2M
    // 其他类型的映射字段
    val systemPrompt: String? = null,      // SYSPROMPT: content
    val contextTemplate: String? = null,   // CONTEXT: story_string
    val messageTemplate: String? = null,   // INSTRUCT: input_sequence/output_sequence 拼接
    // Reasoning 模板（prefix/suffix/separator：模型输出思维链的解析分隔符，官方 reasoning.js）
    val reasoningPrefix: String? = null,
    val reasoningSuffix: String? = null,
    val reasoningSeparator: String? = null,
    // Start Reply With（value/show：value 拼到发送的用户消息前，官方 script.js）
    val startReplyValue: String? = null,
    val startReplyShow: Boolean? = null,
    // 官方 Prompt Manager（openai.js settingsToUpdate 的 prompts/prompt_order 键）
    val prompts: List<PresetPrompt> = emptyList(),      // 自定义提示词条目（官方内容非默认的）
    val promptOrder: List<PresetPromptOrder> = emptyList(), // 官方 prompt_order（enabled 开关）
    val unsupportedKeys: List<String> = emptyList(),   // 官方有但本地无对应的字段名（内容已整包保留在 rawJson）
    val rawJson: String = "",              // 官方原始 JSON 整包（无损往返）
)

/**
 * 官方 Prompt Manager 条目（PromptManager.js Prompt，仅保留本地消费的字段）
 */
@Serializable
data class PresetPrompt(
    val identifier: String? = null,
    val name: String? = null,
    val content: String? = null,
)

/** 官方 prompt_order 条目：{identifier, enabled} */
@Serializable
data class PresetPromptOrder(
    val identifier: String? = null,
    val enabled: Boolean = true,
)

/** 官方 12 个内置 prompt 的默认 content（PromptManager.js chatCompletionDefaultPrompts），
 *  其余 8 个 marker 型条目无 content。预设导出整包携带这些默认条目，
 *  与默认相同的内容本地已有等价注入链，应用时跳过。 */
val DEFAULT_PROMPT_CONTENT: Map<String, String> = mapOf(
    "main" to "Write {{char}}'s next reply in a fictional chat between {{charIfNotGroup}} and {{user}}.",
    "enhanceDefinitions" to "If you have more knowledge of {{char}}, add to the character's lore and personality to enhance them but keep the Character Sheet's definitions absolute.",
)

/** 预设中自定义的提示词条目：content 非空、与官方默认不同、且官方组装会注入的（保持官方数组顺序）。
 *  官方语义（PromptManager.js getPromptsForCharacter）：只注入 prompt_order 里 enabled=true 的条目，
 *  不在 order 里的 prompt（未挂载模块）不参与组装；旧格式预设无 prompt_order 时回退为全部条目生效。 */
fun ChatPreset.customPrompts(): List<PresetPrompt> {
    val orderPresent = promptOrder.isNotEmpty()
    val enabledIds = promptOrder.filter { it.enabled }.mapNotNull { it.identifier }.toSet()
    val disabledIds = promptOrder.filter { !it.enabled }.mapNotNull { it.identifier }.toSet()
    return prompts.filter { p ->
        val content = p.content?.takeIf { it.isNotBlank() } ?: return@filter false
        p.identifier?.let { id -> DEFAULT_PROMPT_CONTENT[id]?.let { return@filter content != it } }
        if (orderPresent) p.identifier in enabledIds else p.identifier !in disabledIds
    }
}

/** main prompt 的自定义内容（覆盖本地 systemPrompt） */
fun ChatPreset.mainPromptContent(): String? =
    customPrompts().firstOrNull { it.identifier == "main" }?.content

/** jailbreak prompt 的自定义内容（注入聊天历史之后） */
fun ChatPreset.jailbreakContent(): String? =
    customPrompts().firstOrNull { it.identifier == "jailbreak" }?.content
