package me.rerere.rikkahub.data.ai.session

import kotlinx.serialization.Serializable
import me.rerere.ai.ui.UIMessage

/**
 * A point-in-time snapshot of the conversation and tool state.
 * Saved to JSON files for crash recovery and session resume.
 */
@Serializable
data class SessionSnapshot(
    val sessionId: String,
    val messages: List<UIMessage>,
    val timestamp: Long = System.currentTimeMillis(),
    val taskState: List<TaskSnapshot> = emptyList(),
    val planModeState: PlanModeSnapshot? = null,
    val forkStates: List<ForkSnapshot> = emptyList(),
)

@Serializable
data class TaskSnapshot(
    val id: String,
    val subject: String,
    val description: String,
    val status: String,
    val owner: String? = null,
    val dependsOn: List<String> = emptyList(),
    val activeForm: String = "",
    val metadata: Map<String, String> = emptyMap(),
    val blockedBy: List<String> = emptyList(),
)

@Serializable
data class PlanModeSnapshot(
    val isInPlanMode: Boolean,
    val effectiveMode: String,
)

@Serializable
data class ForkSnapshot(
    val name: String,
    val goal: String,
    val status: String,
    val result: String = "",
)
