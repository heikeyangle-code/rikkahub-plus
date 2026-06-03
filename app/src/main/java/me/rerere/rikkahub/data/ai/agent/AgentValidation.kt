package me.rerere.rikkahub.data.ai.agent

import me.rerere.rikkahub.data.ai.tools.AgentDefinition
import me.rerere.rikkahub.data.ai.tools.AgentRegistry
import me.rerere.rikkahub.data.ai.tools.AgentMemoryScope
import me.rerere.rikkahub.data.ai.tools.AgentColor

/**
 * Agent 验证与文件操作工具，对齐官方 validateAgent.ts + agentFileUtils.ts。
 */

/** 验证结果 */
data class AgentValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

/**
 * 验证 agent 类型名。
 * 对齐官方 validateAgentType()。
 */
fun validateAgentType(agentType: String): String? {
    if (agentType.isBlank()) return "Agent 类型名不能为空"
    if (!agentType.matches(Regex("^[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]$"))) {
        return "Agent 类型名必须以字母数字开头和结尾，只能包含字母、数字和连字符"
    }
    if (agentType.length < 3) return "Agent 类型名至少 3 个字符"
    if (agentType.length > 50) return "Agent 类型名不能超过 50 个字符"
    return null
}

/**
 * 验证 agent 定义。
 * 对齐官方 validateAgent()。
 */
fun validateAgent(agent: AgentDefinition): AgentValidationResult {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    // 验证类型名
    val typeError = validateAgentType(agent.agentType)
    if (typeError != null) errors.add(typeError)

    // 检查重名
    val existing = AgentRegistry.getAll(agent.agentType).filter { it.source != agent.source }
    if (existing.isNotEmpty()) {
        errors.add("Agent 类型 \"${agent.agentType}\" 已存在于 ${existing.first().source}")
    }

    // 验证描述
    if (agent.description.isBlank()) {
        errors.add("描述不能为空")
    } else if (agent.description.length < 10) {
        warnings.add("描述应更详细（至少 10 个字符）")
    }

    // 验证 system prompt
    when (val sp = agent.systemPrompt) {
        is me.rerere.rikkahub.data.ai.tools.AgentSystemPrompt.Static -> {
            if (sp.text.isBlank()) {
                errors.add("系统提示词不能为空")
            }
        }
        is me.rerere.rikkahub.data.ai.tools.AgentSystemPrompt.Dynamic -> {}
    }

    // 验证工具
    if (agent.disallowedTools.isNotEmpty() && agent.tools != listOf("*")) {
        warnings.add("同时配置了白名单和黑名单，黑名单优先")
    }

    return AgentValidationResult(
        isValid = errors.isEmpty(),
        errors = errors,
        warnings = warnings,
    )
}

/**
 * 格式化 agent 为可导出/保存的文本格式。
 * 对齐官方 formatAgentAsMarkdown()。
 */
fun formatAgentAsExport(agent: AgentDefinition): String {
    val sysPromptText = when (val sp = agent.systemPrompt) {
        is me.rerere.rikkahub.data.ai.tools.AgentSystemPrompt.Static -> sp.text
        is me.rerere.rikkahub.data.ai.tools.AgentSystemPrompt.Dynamic -> "(dynamic)"
    }

    return buildString {
        appendLine("=== Agent: ${agent.agentType} ===")
        appendLine("名称: ${agent.name}")
        appendLine("描述: ${agent.description}")
        appendLine("颜色: ${agent.color.name.lowercase()}")
        if (agent.modelId != null) appendLine("模型: ${agent.modelId}")
        if (agent.background) appendLine("模式: 后台执行")
        if (agent.memory != null) appendLine("持久记忆: ${agent.memory.name.lowercase()}")
        if (agent.maxTurns != null) appendLine("最大轮次: ${agent.maxTurns}")
        if (agent.skills.isNotEmpty()) appendLine("预加载技能: ${agent.skills.joinToString(", ")}")
        if (agent.disallowedTools.isNotEmpty()) appendLine("禁用工具: ${agent.disallowedTools.joinToString(", ")}")
        appendLine()
        appendLine("--- 系统提示词 ---")
        appendLine(sysPromptText)
    }
}
