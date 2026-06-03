package me.rerere.rikkahub.data.ai.agent

/**
 * Agent 类型和路径常量，对齐官方 types.ts + utils.ts。
 */

object AgentConstants {
    const val FOLDER_NAME = ".claude"
    const val AGENTS_DIR = "agents"
}

/**
 * Agent 显示模式状态，对齐官方 ModeState。
 */
sealed class AgentModeState {
    data object MainMenu : AgentModeState()
    data class ListAgents(val source: String = "all") : AgentModeState()
    data class ViewAgent(val agentType: String) : AgentModeState()
    data object CreateAgent : AgentModeState()
    data class EditAgent(val agentType: String) : AgentModeState()
    data class DeleteConfirm(val agentType: String) : AgentModeState()
}

/**
 * Agent 来源显示名，对齐官方 getAgentSourceDisplayName()。
 */
fun getAgentSourceDisplay(source: String): String = when (source) {
    "all" -> "所有角色"
    "built-in" -> "内置角色"
    "plugin" -> "插件角色"
    "user" -> "用户自定义"
    "project" -> "项目角色"
    "policy" -> "策略推送"
    "flag" -> "启动参数"
    else -> source
}
