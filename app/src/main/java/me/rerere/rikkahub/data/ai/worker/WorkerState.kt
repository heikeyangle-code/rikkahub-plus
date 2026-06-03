package me.rerere.rikkahub.data.ai.worker

sealed class WorkerState {
    data object Spawning : WorkerState()
    data class TrustRequired(val prompt: String) : WorkerState()
    data object ReadyForPrompt : WorkerState()
    data class Running(val taskId: String, val prompt: String) : WorkerState()
    data class Finished(val result: String) : WorkerState()
    data class Failed(val error: String) : WorkerState()
}

data class Worker(
    val id: String,
    val cwd: String,
    val trustedRoots: List<String> = emptyList(),
    val state: WorkerState = WorkerState.Spawning,
    val promptDeliveryAttempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
