package me.rerere.rikkahub.data.ai.worker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 在 Android 上，Worker 无法 spawn 真实子进程。
 * 改用同进程协程模拟：sendPrompt 时启动一个协程执行 promptHandler，完成时更新状态。
 */
typealias WorkerPromptHandler = suspend (workerId: String, prompt: String) -> String

class WorkerManager(
    private val scope: CoroutineScope,
    /** 当 sendPrompt 被调用时，执行此 handler 来处理 prompt（Android 上必须设置） */
    private val promptHandler: WorkerPromptHandler? = null,
) {
    private val workers = ConcurrentHashMap<String, Worker>()
    private val _workerStates = MutableStateFlow<Map<String, WorkerState>>(emptyMap())
    val workerStates: StateFlow<Map<String, WorkerState>> = _workerStates.asStateFlow()

    private val counter = AtomicInteger(0)

    fun createWorker(cwd: String, trustedRoots: List<String> = emptyList()): Worker {
        val id = "worker-${counter.incrementAndGet()}-${System.currentTimeMillis() % 10000}"
        // Android 无真实子进程，直接就绪
        val worker = Worker(id = id, cwd = cwd, trustedRoots = trustedRoots, state = WorkerState.ReadyForPrompt)
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
            screenText.contains("ready for input", ignoreCase = true) ||
            screenText.contains("ready", ignoreCase = true) ||
            screenText.trimEnd().endsWith('$') || screenText.trimEnd().endsWith('#') -> WorkerState.ReadyForPrompt
            else -> worker.state
        }
        return updateWorker(workerId) { it.copy(state = newState) }
    }

    fun resolveTrust(workerId: String): Worker {
        return updateWorker(workerId) { it.copy(state = WorkerState.ReadyForPrompt, lastError = null) }
    }

    /**
     * 发送 prompt 给 worker。
     * 如果有 promptHandler，会自动在后台协程中执行，完成后设 Finished/Failed。
     */
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

        // 如果有 handler，在后台执行
        if (promptHandler != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val result = promptHandler(workerId, prompt)
                    updateWorker(workerId) { it.copy(state = WorkerState.Finished(result)) }
                } catch (e: Exception) {
                    updateWorker(workerId) {
                        it.copy(state = WorkerState.Failed(e.message ?: "Unknown error"))
                    }
                }
            }
        }

        return updated
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
