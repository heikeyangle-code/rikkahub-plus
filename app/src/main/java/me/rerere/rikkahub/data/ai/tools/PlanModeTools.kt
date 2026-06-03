package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/** 计划模式状态 */
object PlanModeState {
    @Volatile
    var isInPlanMode: Boolean = false
}

fun createPlanModeTools(): List<Tool> = listOf(

    Tool(
        name = "enter_plan_mode",
        description = """
            Switch to planning mode. In this mode, analyze the task and create a step-by-step plan
            using task_create/todo_write instead of executing directly. Do NOT execute any tools that
            modify state (file_write, shell, etc.) while in plan mode.

            When to use:
            - Complex multi-step tasks requiring careful planning
            - Tasks where the user needs to approve the approach before execution
            - When the user explicitly asks for a plan first

            Process:
            1. Enter plan mode
            2. Analyze the task and break it into steps
            3. Create tasks for each step using task_create or todo_write
            4. Present the plan to the user
            5. Wait for user approval
            6. Exit plan mode and begin execution
        """.trimIndent().replace("\n", " "),
        execute = {
            PlanModeState.isInPlanMode = true
            listOf(UIMessagePart.Text("[Entering plan mode] I will create a plan first. Waiting for your approval before executing."))
        },
    ),

    Tool(
        name = "exit_plan_mode",
        description = """
            Exit planning mode and begin executing the approved plan.
            Call this after the user has reviewed and approved the plan.
        """.trimIndent().replace("\n", " "),
        execute = {
            PlanModeState.isInPlanMode = false
            listOf(UIMessagePart.Text("[Exiting plan mode] Starting execution..."))
        },
    ),
)
