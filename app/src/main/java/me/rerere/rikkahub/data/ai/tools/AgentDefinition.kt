package me.rerere.rikkahub.data.ai.tools

import java.util.concurrent.ConcurrentHashMap

/**
 * 一个 Agent 角色的定义，对标 Claude Code 的 AgentDefinition。
 *
 * 每个 agent 有：
 * - agentType: 唯一标识（如 "explorer"、"planner"）
 * - description: 什么场景用这个 agent（AI 根据描述决定调度哪个）
 * - tools: 允许的工具列表，"*" = 全部
 * - systemPrompt: 角色专属 system prompt
 * - modelId: 可选，指定模型（null=用主模型）
 * - color: UI 颜色标签
 */
data class AgentDefinition(
    val agentType: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val tools: List<String> = listOf("*"),
    val modelId: String? = null,
    val color: String = "blue",
    val isBuiltin: Boolean = true,
)

/**
 * Agent 注册表 — 管理所有可用的 agent 角色
 */
object AgentRegistry {
    private val agents = ConcurrentHashMap<String, AgentDefinition>()

    fun register(agent: AgentDefinition) {
        agents[agent.agentType] = agent
    }

    fun get(agentType: String): AgentDefinition? = agents[agentType]

    fun list(): List<AgentDefinition> = agents.values.toList()

    fun delete(agentType: String) {
        val def = agents[agentType]
        if (def != null && !def.isBuiltin) agents.remove(agentType)
    }

    /** 注册内置 agent */
    fun registerBuiltin() {
        register(
            AgentDefinition(
                agentType = "general-purpose",
                name = "通用助手",
                description = "General-purpose agent for researching questions, searching code, and executing multi-step tasks.",
                systemPrompt = """You are a general-purpose AI assistant. Complete the user's task thoroughly.
Use the tools available to research, search, and execute multi-step operations.
When you complete the task, respond with a concise report covering what was done and any key findings.""",
                color = "blue",
            )
        )
        register(
            AgentDefinition(
                agentType = "explorer",
                name = "探索者",
                description = "Deeply analyze codebases by tracing execution paths, understanding patterns, and documenting dependencies. Best for understanding how existing features work.",
                systemPrompt = """You are a code analysis expert specializing in understanding how features work.
Trace implementation from entry points to data storage through all abstraction layers.
Map the architecture: find entry points, core files, data flow, and dependencies.
Document your findings clearly so others can understand the implementation.""",
                tools = listOf("file_read", "file_list", "file_search", "search_web", "get_time"),
                color = "yellow",
            )
        )
        register(
            AgentDefinition(
                agentType = "planner",
                name = "规划师",
                description = "Design architectures and create implementation plans. Best for planning complex features before coding.",
                systemPrompt = """You are a senior software architect. Design comprehensive implementation blueprints.
1. Analyze the codebase to understand existing patterns and conventions
2. Design the architecture with clear component boundaries and data flow
3. Create a step-by-step implementation plan with specific files to create/modify
4. Break the plan into clear phases with dependencies
Always be decisive — pick one approach and commit. Provide complete, actionable plans.""",
                tools = listOf("file_read", "file_list", "file_search", "task_create", "task_update", "task_list", "todo_write"),
                color = "green",
            )
        )
    }
}
