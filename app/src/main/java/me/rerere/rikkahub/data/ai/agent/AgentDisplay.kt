package me.rerere.rikkahub.data.ai.agent

import me.rerere.rikkahub.data.ai.tools.AgentDefinition
import me.rerere.rikkahub.data.ai.tools.AgentSource
import me.rerere.rikkahub.data.ai.tools.AgentColor
import me.rerere.rikkahub.data.ai.tools.AgentRegistry
import me.rerere.rikkahub.data.ai.tools.formatAgentTools

/**
 * Agent 展示工具，对齐官方 agentDisplay.ts + standaloneAgent.ts。
 *
 * 功能：
 * - resolveAgentOverrides: 标注被覆盖的 agent（高优先级覆盖低）
 * - resolveAgentModelDisplay: 解析 agent 的模型显示名
 * - compareAgentsByName: 按名称排序
 * - AGENT_SOURCE_GROUPS: 来源显示分组顺序
 * - formatAgentLine: 格式化 agent 为一行摘要
 * - getAgentSourceDisplayName: 来源显示名
 */

/** 来源显示分组（从低到高）。对齐官方 AGENT_SOURCE_GROUPS。 */
val AGENT_SOURCE_GROUPS: List<Pair<AgentSource, String>> = listOf(
    AgentSource.BUILT_IN to "内置角色",
    AgentSource.PLUGIN to "插件角色",
    AgentSource.USER to "用户自定义",
    AgentSource.PROJECT to "项目角色",
    AgentSource.FLAG to "启动参数",
    AgentSource.POLICY to "策略推送",
)

/**
 * 标注 agent 的覆盖关系。
 * 当一个同名 agent 有高优先级来源时，低优先级的被标记为"被覆盖"。
 * 对齐官方 resolveAgentOverrides()。
 */
data class ResolvedAgent(
    val agent: AgentDefinition,
    val overriddenBy: AgentSource? = null,
)

fun resolveAgentOverrides(): List<ResolvedAgent> {
    val allAgents = AgentRegistry.list()

    // 对每个 agentType，找出最高优先级的 active agent
    val activeByType = mutableMapOf<String, AgentDefinition>()
    for (agent in allAgents.sortedBy { it.source.priority }) {
        activeByType[agent.agentType] = agent
    }

    return allAgents.map { agent ->
        val active = activeByType[agent.agentType]
        val overriddenBy = if (active != null && active.source != agent.source) {
            active.source
        } else null
        ResolvedAgent(agent, overriddenBy)
    }
}

/**
 * 解析 agent 的模型显示名。
 * 对齐官方 resolveAgentModelDisplay()。
 */
fun resolveAgentModelDisplay(agent: AgentDefinition): String? {
    return agent.modelId ?: "inherit"
}

/**
 * 按名称比较 agent（不区分大小写）。
 * 对齐官方 compareAgentsByName()。
 */
fun compareAgentsByName(a: AgentDefinition, b: AgentDefinition): Int {
    return a.agentType.lowercase().compareTo(b.agentType.lowercase())
}

/**
 * 格式化 agent 为一行描述文本。
 * 对齐官方 formatAgentLine()。
 */
fun formatAgentLine(agent: AgentDefinition): String {
    val toolsDesc = formatAgentTools(agent)
    return "- ${agent.agentType}: ${agent.description} (工具: $toolsDesc)"
}

/**
 * 获取 agent 的显示名称（带来源标记）。
 */
fun getAgentDisplayName(agent: AgentDefinition): String {
    return if (agent.isBuiltin) "${agent.agentType} (内置)"
    else agent.agentType
}

/**
 * 获取独立运行 agent 的名称。
 * 如果 agent 在 team 中则返回 null。
 * 对齐官方 getStandaloneAgentName()。
 */
fun getStandaloneAgentName(agent: AgentDefinition, teamName: String?): String? {
    if (teamName != null) return null
    return agent.name
}
