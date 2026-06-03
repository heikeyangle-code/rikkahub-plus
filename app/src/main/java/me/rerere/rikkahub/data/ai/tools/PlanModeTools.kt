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
            using task_create/todo_write instead of executing directly. The user will review and
            approve the plan before you exit plan mode and begin execution.
            Use for: complex multi-step tasks, tasks requiring user approval before execution.
        """.trimIndent().replace("\n", " "),
        execute = {
            PlanModeState.isInPlanMode = true
            listOf(UIMessagePart.Text("[进入计划模式] 我将先制定计划，等待你确认后再开始执行。"))
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
            listOf(UIMessagePart.Text("[退出计划模式] 开始执行计划..."))
        },
    ),
)
