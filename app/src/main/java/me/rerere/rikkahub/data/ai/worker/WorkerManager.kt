package me.rerere.rikkahub.data.ai.worker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.agent.AgentNotification
import me.rerere.rikkahub.data.ai.agent.AgentLifecycleManager
import me.rerere.rikkahub.data.ai.agent.enqueueAgentNotification
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

typealias WorkerPromptHandler = suspend (workerId: String, prompt: String) -> String

class WorkerManager(
    private val scope: CoroutineScope,
    private val promptHandler: WorkerPromptHandler? = null,
) {
    private val workers = ConcurrentHashMap<String, Worker>()
    private val _workerStates = MutableStateFlow<Map<String, WorkerState>>(emptyMap())
    val workerStates: StateFlow<Map<String, WorkerState>> = _workerStates.asStateFlow()

    private val counter = AtomicInteger(0)

    fun createWorker(name: String = "", cwd: String = ""): Worker {
        val id = "worker-${counter.incrementAndGet()}-${System.currentTimeMillis() % 10000}"
        val worker = Worker(id = id, name = name, cwd = cwd, state = WorkerState.ReadyForPrompt)
        workers[id] = worker
        updateState()
        return worker
    }

    fun sendPrompt(workerId: String, prompt: String): Worker {
        val worker = workers[workerId] ?: error("Worker $workerId not found")
        require(worker.state is WorkerState.ReadyForPrompt) {
            "Worker $workerId is not ready (state: ${worker.state::class.simpleName})"
        }
        val taskId = prompt.hashCode().toString()
        val updated = updateWorker(workerId) {
            it.copy(
                state = WorkerState.Running(taskId, prompt),
                promptDeliveryAttempts = it.promptDeliveryAttempts + 1,
            )
        }

        if (promptHandler != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val result = promptHandler(workerId, prompt)
                    val now = System.currentTimeMillis()
                    updateWorker(workerId) {
                        it.copy(state = WorkerState.Finished(result), finishedAt = now)
                    }
                    // 通知主 Agent
                    enqueueAgentNotification(
                        agentId = workerId,
                        agentType = "worker",
                        description = "Worker ${worker.name.ifBlank { workerId }} completed",
                        status = AgentLifecycleManager.AgentLifecycleStatus.COMPLETED,
                        result = result.take(200),
                    )
                } catch (e: Exception) {
                    val now = System.currentTimeMillis()
                    updateWorker(workerId) {
                        it.copy(
                            state = WorkerState.Failed(e.message ?: "Unknown error"),
                            finishedAt = now,
                            lastError = e.message,
                        )
                    }
                    enqueueAgentNotification(
                        agentId = workerId,
                        agentType = "worker",
                        description = "Worker ${worker.name.ifBlank { workerId }} failed",
                        status = AgentLifecycleManager.AgentLifecycleStatus.FAILED,
                        error = e.message,
                    )
                }
            }
        }

        return updated
    }

    fun getWorker(workerId: String): Worker? = workers[workerId]

    fun listWorkers(): List<Worker> = workers.values.toList()

    fun terminate(workerId: String): Worker {
        return updateWorker(workerId) {
            val now = System.currentTimeMillis()
            it.copy(state = WorkerState.Finished("Terminated"), finishedAt = now)
        }
    }

    fun restart(workerId: String): Worker {
        return updateWorker(workerId) {
            it.copy(state = WorkerState.ReadyForPrompt, promptDeliveryAttempts = 0, lastError = null, finishedAt = null)
        }
    }

    private fun updateWorker(workerId: String, transform: (Worker) -> Worker): Worker {
        val updated = transform(workers[workerId] ?: error("Worker $workerId not found"))
        workers[workerId] = updated
        updateState()
        return updated
    }

    private fun updateState() {
        _workerStates.value = workers.mapValues { it.value.state }
    }
}
