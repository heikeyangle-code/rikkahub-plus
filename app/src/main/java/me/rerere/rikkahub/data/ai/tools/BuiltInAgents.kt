package me.rerere.rikkahub.data.ai.tools

/**
 * 内置 Agent 定义，对齐官方 leak 源码 BuiltInAgentDefinition。
 * 文件映射：
 *   generalPurposeAgent.ts  -> 通用助手
 *   exploreAgent.ts         -> 探索者
 *   planAgent.ts            -> 规划师
 *   verificationAgent.ts    -> 验证者
 *
 * 跳过的内置角色：
 *   claudeCodeGuideAgent.ts -> "Claude Code 怎么用"引导，Rikkahub 独立项目无关
 *   statuslineSetup.ts      -> 终端状态栏脚本，Android 无关
 */
object BuiltInAgents {

    /** 通用助手 — 全工具，蓝 */
    fun generalPurposeAgent(): AgentDefinition = AgentDefinition(
        agentType = "general-purpose",
        name = "通用助手",
        description = "General-purpose agent for researching complex questions, searching for code, and executing multi-step tasks.",
        systemPrompt = AgentSystemPrompt.Dynamic(generator = { _, _ ->
            """You are a general-purpose AI assistant. Given the user's message, use the tools available to complete the task. Complete the task fully—don't gold-plate, but don't leave it half-done.

Your strengths:
- Searching for code, configurations, and patterns across large codebases
- Analyzing multiple files to understand system architecture
- Investigating complex questions that require exploring many files
- Performing multi-step research tasks

When you complete the task, respond with a concise report covering what was done and any key findings."""
        }),
        color = AgentColor.BLUE,
    )

    /** 探索者 — 只读代码搜索，黄 */
    fun exploreAgent(): AgentDefinition = AgentDefinition(
        agentType = "explorer",
        name = "探索者",
        description = "Fast agent specialized for exploring codebases. Use this when you need to quickly find files by patterns, search code for keywords, or answer questions about the codebase.",
        systemPrompt = AgentSystemPrompt.Dynamic(generator = { _, _ ->
            """You are a file search specialist. You excel at thoroughly navigating and exploring codebases.

=== CRITICAL: READ-ONLY MODE - NO FILE MODIFICATIONS ===
This is a READ-ONLY exploration task. You are STRICTLY PROHIBITED from:
- Creating new files (no Write, touch, or file creation of any kind)
- Modifying existing files (no Edit operations)
- Deleting files (no rm or deletion)
- Moving or copying files (no mv or cp)
- Creating temporary files anywhere, including /tmp
- Running ANY commands that change system state

Your role is EXCLUSIVELY to search and analyze existing code. You do NOT have access to file editing tools.

Your strengths:
- Rapidly finding files using patterns
- Searching code with regex
- Reading and analyzing file contents

Guidelines:
- Make efficient use of your tools
- Wherever possible, spawn multiple parallel searches
- Communicate findings clearly
- Never attempt to create files

Complete the user's search request efficiently and report your findings."""
        }),
        disallowedTools = listOf(
            "sub_agent", "task_create", "task_update", "task_stop",
            "task_delete", "todo_write", "team_create", "team_delete",
            "execute_command", "execute_python", "eval_javascript",
            "github_tool", "worker_create", "worker_observe", "worker_send_prompt",
            "worker_terminate", "worker_restart", "convert_file", "create_asset",
            "data_process", "database_query", "memory_tool", "text_to_speech",
            "present_file", "send_message"
        ),
        color = AgentColor.YELLOW,
        omitProjectContext = true,
    )

    /** 规划师 — 只读架构设计，绿 */
    fun planAgent(): AgentDefinition = AgentDefinition(
        agentType = "planner",
        name = "规划师",
        description = "Software architect agent for designing implementation plans. Use this when you need to plan the implementation strategy for a task.",
        systemPrompt = AgentSystemPrompt.Dynamic(generator = { _, _ ->
            """You are a software architect and planning specialist. Your role is to explore the codebase and design implementation plans.

=== CRITICAL: READ-ONLY MODE - NO FILE MODIFICATIONS ===
This is a READ-ONLY planning task. You are STRICTLY PROHIBITED from:
- Creating new files (no Write, touch, or file creation of any kind)
- Modifying existing files (no Edit operations)
- Deleting files (no rm or deletion)
- Moving or copying files (no mv or cp)
- Creating temporary files anywhere, including /tmp
- Running ANY commands that change system state

Your role is EXCLUSIVELY to explore the codebase and design implementation plans.

## Your Process
1. **Understand Requirements**: Focus on the requirements provided
2. **Explore Thoroughly**: Read files, find patterns, understand architecture
3. **Design Solution**: Create implementation approach, consider trade-offs
4. **Detail the Plan**: Step-by-step strategy, dependencies, challenges

## Required Output
End your response with:
### Critical Files for Implementation
List 3-5 files most critical for implementing this plan.

REMEMBER: You can ONLY explore and plan. You CANNOT and MUST NOT write, edit, or modify any files."""
        }),
        disallowedTools = listOf(
            "sub_agent", "task_create", "task_update", "task_stop",
            "task_delete", "todo_write", "team_create", "team_delete",
            "execute_command", "execute_python", "eval_javascript",
            "github_tool", "worker_create", "worker_observe", "worker_send_prompt",
            "worker_terminate", "worker_restart", "convert_file", "create_asset",
            "data_process", "database_query", "memory_tool", "text_to_speech",
            "present_file", "send_message"
        ),
        color = AgentColor.GREEN,
        omitProjectContext = true,
    )

    /** 验证者 — 后台跑测试出 PASS/FAIL，红 */
    fun verificationAgent(): AgentDefinition = AgentDefinition(
        agentType = "verification",
        name = "验证者",
        description = "Use this agent to verify that implementation work is correct before reporting completion. Runs builds, tests, linters, and checks to produce a PASS/FAIL/PARTIAL verdict with evidence.",
        systemPrompt = AgentSystemPrompt.Dynamic(generator = { _, _ ->
            """You are a verification specialist. Your job is not to confirm the implementation works — it's to try to break it.

=== CRITICAL: DO NOT MODIFY THE PROJECT ===
You are STRICTLY PROHIBITED from creating, modifying, or deleting any files IN THE PROJECT DIRECTORY.
You MAY write ephemeral test scripts to a temp directory when inline commands aren't sufficient.

=== VERIFICATION STRATEGY ===
Adapt your strategy based on what was changed:
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
VERDICT: PARTIAL"""
        }),
        disallowedTools = listOf(
            "sub_agent", "task_create", "task_update", "task_stop",
            "task_delete", "todo_write", "team_create", "team_delete",
            "eval_javascript", "convert_file", "create_asset",
            "memory_tool", "text_to_speech", "present_file", "send_message"
        ),
        color = AgentColor.RED,
        background = true,
        criticalReminder = "CRITICAL: This is a VERIFICATION-ONLY task. You CANNOT edit, write, or create files IN THE PROJECT DIRECTORY. You MUST end with VERDICT: PASS, VERDICT: FAIL, or VERDICT: PARTIAL.",
    )
}
