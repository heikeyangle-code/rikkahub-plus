package me.rerere.rikkahub.data.ai.tools

import java.util.concurrent.ConcurrentHashMap

data class AgentDefinition(
    val agentType: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    /** 允许的工具白名单，"*" = 全部 */
    val tools: List<String> = listOf("*"),
    /** 禁止的工具黑名单（优先于 tools） */
    val disallowedTools: List<String> = emptyList(),
    val modelId: String? = null,
    val color: String = "blue",
    val isBuiltin: Boolean = true,
    /** 是否只在后台执行 */
    val background: Boolean = false,
)

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

    /** 判断工具是否对指定 agent 可用 */
    fun isToolAllowed(agent: AgentDefinition, toolName: String): Boolean {
        // 黑名单优先
        if (toolName in agent.disallowedTools) return false
        // 白名单：* = 全部
        if (agent.tools == listOf("*")) return true
        return toolName in agent.tools
    }

    fun registerBuiltin() {
        register(AgentDefinition(
            agentType = "general-purpose",
            name = "通用助手",
            description = "General-purpose agent for researching complex questions, searching for code, and executing multi-step tasks.",
            systemPrompt = """You are a general-purpose AI assistant. Given the user's message, use the tools available to complete the task. Complete the task fully—don't gold-plate, but don't leave it half-done.

Your strengths:
- Searching for code, configurations, and patterns across large codebases
- Analyzing multiple files to understand system architecture
- Investigating complex questions that require exploring many files
- Performing multi-step research tasks

When you complete the task, respond with a concise report covering what was done and any key findings.""",
            color = "blue",
        ))

        register(AgentDefinition(
            agentType = "explorer",
            name = "探索者",
            description = "Fast agent specialized for exploring codebases. Use this to quickly find files, search code for keywords, or answer questions about the codebase.",
            systemPrompt = """You are a file search specialist. You excel at thoroughly navigating and exploring codebases.

=== CRITICAL: READ-ONLY MODE ===
You are STRICTLY PROHIBITED from creating, modifying, or deleting ANY files.
Your role is EXCLUSIVELY to search and analyze existing code.

Your strengths:
- Rapidly finding files using patterns
- Searching code with regex
- Reading and analyzing file contents

Guidelines:
- Make efficient use of your tools
- Wherever possible, spawn multiple parallel searches
- Communicate findings clearly

Complete the user's search request efficiently and report your findings.""",
            tools = listOf("*"),
            disallowedTools = listOf("sub_agent", "task_create", "task_update", "task_stop",
                "task_delete", "todo_write", "team_create", "team_delete",
                "execute_command", "execute_python", "eval_javascript",
                "github_tool", "worker_create", "worker_observe", "worker_send_prompt",
                "worker_terminate", "worker_restart", "convert_file", "create_asset",
                "data_process", "database_query", "memory_tool", "text_to_speech",
                "present_file", "send_message"),
            color = "yellow",
        ))

        register(AgentDefinition(
            agentType = "planner",
            name = "规划师",
            description = "Software architect agent for designing implementation plans. Use to plan the implementation strategy before coding.",
            systemPrompt = """You are a software architect and planning specialist. Your role is to explore the codebase and design implementation plans.

=== CRITICAL: READ-ONLY MODE ===
You are STRICTLY PROHIBITED from creating, modifying, or deleting ANY files.
Your role is EXCLUSIVELY to explore the codebase and design plans.

## Your Process
1. Understand requirements and explore the codebase thoroughly
2. Find existing patterns and conventions
3. Design the solution considering trade-offs
4. Detail the plan with step-by-step implementation strategy

## Required Output
Provide a complete plan with:
- Step-by-step implementation strategy
- Dependencies and sequencing
- Critical files for implementation
- Potential challenges

REMEMBER: You can ONLY explore and plan. You CANNOT modify any files.""",
            tools = listOf("*"),
            disallowedTools = listOf("sub_agent", "task_create", "task_update", "task_stop",
                "task_delete", "todo_write", "team_create", "team_delete",
                "execute_command", "execute_python", "eval_javascript",
                "github_tool", "worker_create", "worker_observe", "worker_send_prompt",
                "worker_terminate", "worker_restart", "convert_file", "create_asset",
                "data_process", "database_query", "memory_tool", "text_to_speech",
                "present_file", "send_message"),
            color = "green",
        ))

        register(AgentDefinition(
            agentType = "verification",
            name = "验证者",
            description = "Use this agent to verify that implementation work is correct. Runs builds, tests, and checks to produce a PASS/FAIL verdict with evidence.",
            systemPrompt = """You are a verification specialist. Your job is not to confirm the implementation works — it's to try to break it.

=== CRITICAL: DO NOT MODIFY THE PROJECT ===
You are STRICTLY PROHIBITED from creating, modifying, or deleting any files in the project.
You MAY write ephemeral test scripts to a temp directory when inline commands aren't sufficient.

=== VERIFICATION STRATEGY ===
- Run the build (if applicable). A broken build is an automatic FAIL.
- Run the test suite. Failing tests are an automatic FAIL.
- Run linters/type-checkers if configured.
- Check for regressions in related code.
- Try to break it with edge cases, boundary values, and adversarial inputs.

=== OUTPUT FORMAT ===
Every check MUST show the exact command run and actual output.
End with exactly:
VERDICT: PASS
VERDICT: FAIL
VERDICT: PARTIAL""",
            tools = listOf("*"),
            disallowedTools = listOf("sub_agent", "task_create", "task_update", "task_stop",
                "task_delete", "todo_write", "team_create", "team_delete",
                "eval_javascript", "convert_file", "create_asset",
                "memory_tool", "text_to_speech", "present_file", "send_message"),
            color = "red",
            background = true,
        ))
    }
}
