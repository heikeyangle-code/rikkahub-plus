package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.PermissionMode
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

object PlanModeState {
    @Volatile
    var isInPlanMode: Boolean = false
    @Volatile
    var effectiveMode: PermissionMode = PermissionMode.DANGER_FULL_ACCESS

    fun enterPlan() {
        isInPlanMode = true
        effectiveMode = PermissionMode.READ_ONLY
    }

    fun exitPlan(restoreMode: PermissionMode = PermissionMode.WORKSPACE_WRITE) {
        isInPlanMode = false
        effectiveMode = restoreMode
    }
}

fun createPlanModeTools(): List<Tool> = listOf(
    Tool(
        name = "enter_plan_mode",
        description = "Switch to planning mode (read-only). Create a step-by-step plan using task_create/todo_write. " +
            "Do NOT execute state-modifying tools while in plan mode.",
        execute = {
            PlanModeState.enterPlan()
            listOf(UIMessagePart.Text(
                "[进入计划模式] 当前仅允许只读操作，不能修改文件或执行危险命令。"
            ))
        },
    ),
    Tool(
        name = "exit_plan_mode",
        description = "Exit planning mode and begin execution. Call after user approves the plan.",
        execute = {
            PlanModeState.exitPlan()
            listOf(UIMessagePart.Text(
                "[退出计划模式] 开始执行计划..."
            ))
        },
    ),
)
