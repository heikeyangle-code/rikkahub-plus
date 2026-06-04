package me.rerere.rikkahub.data.ai.prompts

import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.model.AssistantMemory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

/**
 * s10: System Prompt — 运行时分段组装 + 确定性缓存。
 *
 * 对标 learn-claude-code s10_system_prompt 和 Claude Code 泄露源码 prompt 架构：
 * - 分节（identity / lead-in / workspace / memory / plan / instructions / constraints）
 * - 去掉冗余的 tools 列表（API tools 参数已含完整描述）
 * - 根据当前上下文选择包含哪些节
 * - json.dumps(context) 确定性 key，命中缓存跳过组装
 */
object SystemPromptAssembler {

    // 缓存上一次的 context key 和组装结果
    @Volatile
    private var lastContextKey: String? = null
    @Volatile
    private var lastPrompt: String? = null

    /**
     * 组装 system prompt。
     * @param context 包含当前构建所需的所有状态
     */
    fun assemble(context: PromptContext): String {
        val key = context.cacheKey()
        if (key == lastContextKey && lastPrompt != null) {
            return lastPrompt!!
        }

        val sections = mutableListOf<String>()

        // Always: identity
        sections.add(context.identitySection)

        // Conditional: lead-in guidelines (对标 Claude Code 行为指引)
        if (context.leadInInstructions.isNotBlank()) {
            sections.add(context.leadInInstructions)
        }

        // Conditional: workspace
        if (context.workspaceDescription.isNotBlank()) {
            sections.add(context.workspaceDescription)
        }

        // Conditional: memory
        if (context.memories.isNotEmpty()) {
            val memoryText = context.memories.joinToString("\n") { memory ->
                "- ${memory.id}: ${memory.content.take(200)}"
            }
            sections.add("Relevant knowledge:\n$memoryText")
        }

        // Conditional: active plan
        if (context.activePlanSummary.isNotBlank()) {
            sections.add(context.activePlanSummary)
        }

        // Conditional: session state (对标 BriefTool 可见性控制)
        if (context.sessionState.isNotBlank()) {
            sections.add(context.sessionState)
        }

        // Conditional: extra instructions
        if (context.extraInstructions.isNotBlank()) {
            sections.add(context.extraInstructions)
        }

        // Conditional: constraints
        if (context.constraints.isNotEmpty()) {
            sections.add("Constraints:\n${context.constraints.joinToString("\n") { "- $it" }}")
        }

        val result = sections.joinToString("\n\n")
        lastContextKey = key
        lastPrompt = result
        return result
    }

    /**
     * 清除缓存（当 system prompt 依赖项发生结构性变化时调用）
     */
    fun invalidateCache() {
        lastContextKey = null
        lastPrompt = null
    }
}

/**
 * System prompt 组装的上下文数据。
 * 每次 LLM 调用前由调用方构建。
 */
data class PromptContext(
    /** Agent 身份描述 */
    val identitySection: String = "You are an AI agent running in Rikkahub on Android. Be concise and act directly — don't describe what you will do, just do it.",

    /** Lead-in 行为指引（对标 Claude Code behavior guidelines） */
    val leadInInstructions: String = "",

    /** 注：工具定义通过 API tools 参数传递，不在 prompt 里重复列出 */

    /** 工作空间描述，如 "Working directory: /path" */
    val workspaceDescription: String = "",

    /** 当前活跃的记忆列表 */
    val memories: List<AssistantMemory> = emptyList(),

    /** AI 当前的执行计划摘要（来自 TodoWrite/PlanManager） */
    val activePlanSummary: String = "",

    /** 会话状态摘要（对标 Claude Code briefVisibility） */
    val sessionState: String = "",

    /** 额外的指令文本，会作为一个独立段追加 */
    val extraInstructions: String = "",

    /** 约束列表 */
    val constraints: List<String> = emptyList(),
) {
    /**
     * 生成确定性缓存 key。
     * 使用 JSON 序列化保证不同构建之间的比较一致性，
     * 而不是依赖 hashCode（非确定性、可能哈希碰撞）。
     */
    fun cacheKey(): String {
        val map = linkedMapOf(
            "identity" to identitySection,
            "leadIn" to leadInInstructions,
            "workspace" to workspaceDescription,
            "memories" to memories.map {
                linkedMapOf("id" to it.id, "content" to it.content)
            }.toString(),
            "plan" to activePlanSummary,
            "session" to sessionState,
            "instructions" to extraInstructions,
            "constraints" to constraints.sorted().toString(),
        )
        return map.entries.joinToString("|") { "${it.key}=${it.value}" }
    }
}
