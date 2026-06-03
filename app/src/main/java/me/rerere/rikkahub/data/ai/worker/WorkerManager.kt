package me.rerere.rikkahub.data.ai.worker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class WorkerManager(private val scope: CoroutineScope) {

    private val workers = ConcurrentHashMap<String, Worker>()
    private val _workerStates = MutableStateFlow<Map<String, WorkerState>>(emptyMap())
    val workerStates: StateFlow<Map<String, WorkerState>> = _workerStates.asStateFlow()

    private val counter = AtomicInteger(0)

    fun createWorker(cwd: String, trustedRoots: List<String> = emptyList()): Worker {
        val id = "worker-${counter.incrementAndGet()}-${System.currentTimeMillis() % 10000}"
        val worker = Worker(id = id, cwd = cwd, trustedRoots = trustedRoots)
        workers[id] = worker
        updateState()
        return worker
    }

    fun observe(workerId: String, screenText: String): Worker {
        val worker = workers[workerId] ?: error("Worker $workerId not found")
        val newState: WorkerState = when {
            screenText.contains("trust", ignoreCase = true) &&
                (screenText.contains("folder", ignoreCase = true) ||
                 screenText.contains("file", ignoreCase = true)) -> {
                if (worker.trustedRoots.any { worker.cwd.startsWith(it) }) {
                    WorkerState.ReadyForPrompt
                } else {
                    WorkerState.TrustRequired(screenText.take(200))
                }
            }
            screenText.contains("Ready for input", ignoreCase = true) ||
            screenText.contains("ready", ignoreCase = true) ||
            screenText.contains('>') -> WorkerState.ReadyForPrompt
            else -> worker.state
        }
        return updateWorker(workerId) { it.copy(state = newState) }
    }

    fun resolveTrust(workerId: String): Worker {
        return updateWorker(workerId) { it.copy(state = WorkerState.Spawning, lastError = null) }
    }

    fun sendPrompt(workerId: String, prompt: String): Worker {
        val worker = workers[workerId] ?: error("Worker $workerId not found")
        require(worker.state is WorkerState.ReadyForPrompt) {
            "Worker $workerId is not ready (state: ${worker.state::class.simpleName})"
        }
        return updateWorker(workerId) {
            it.copy(
                state = WorkerState.Running(prompt.hashCode().toString(), prompt),
                promptDeliveryAttempts = it.promptDeliveryAttempts + 1,
            )
        }
    }

    fun getWorker(workerId: String): Worker? = workers[workerId]

    fun terminate(workerId: String): Worker {
        return updateWorker(workerId) { it.copy(state = WorkerState.Finished("Terminated")) }
    }

    fun restart(workerId: String): Worker {
        return updateWorker(workerId) {
            it.copy(state = WorkerState.Spawning, promptDeliveryAttempts = 0, lastError = null)
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
