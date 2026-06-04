package me.rerere.rikkahub.data.ai.prompts

import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.model.AssistantMemory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

/**
 * s10: System Prompt — 运行时分段组装 + 确定性缓存。
 *
 * 对标 learn-claude-code s10_system_prompt：
 * - 分节（identity / tools / workspace / memory / instructions）
 * - 根据当前上下文选择包含哪些节
 * - json.dumps(context) 确定性 key，命中缓存跳过组装
 *
 * 用法：每次 LLM 调用前调用 assemble()，传入当前 context。
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

        // Always: tools description
        if (context.enabledTools.isNotEmpty()) {
            val toolNames = context.enabledTools.joinToString(", ")
            sections.add("Available tools: $toolNames.")
        }

        // Conditional: workspace
        if (context.workspaceDescription.isNotBlank()) {
            sections.add(context.workspaceDescription)
        }

        // Conditional: memory
        if (context.memories.isNotEmpty()) {
            val memoryText = context.memories.joinToString("\n") { memory ->
                "- ${memory.description}${if (memory.content.isNotBlank()) ": ${memory.content}" else ""}"
            }
            sections.add("Relevant knowledge:\n$memoryText")
        }

        // Conditional: active plan
        if (context.activePlanSummary.isNotBlank()) {
            sections.add(context.activePlanSummary)
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
    val identitySection: String = "You are an AI assistant. Act, don't explain.",

    /** 启用的工具名称列表，用于生成 "Available tools: ..." 段 */
    val enabledTools: List<String> = emptyList(),

    /** 工作空间描述，如 "Working directory: /path" */
    val workspaceDescription: String = "",

    /** 当前活跃的记忆列表 */
    val memories: List<AssistantMemory> = emptyList(),

    /** AI 当前的执行计划摘要（来自 TodoWrite/PlanManager） */
    val activePlanSummary: String = "",

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
            "tools" to enabledTools.sorted().toString(),
            "workspace" to workspaceDescription,
            "memories" to memories.map {
                linkedMapOf("description" to it.description, "content" to it.content)
            }.toString(),
            "plan" to activePlanSummary,
            "instructions" to extraInstructions,
            "constraints" to constraints.sorted().toString(),
        )
        return map.entries.joinToString("|") { "${it.key}=${it.value}" }
    }
}
