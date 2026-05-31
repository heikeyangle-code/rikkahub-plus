package me.rerere.rikkahub.data.ai

import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe iteration budget counter.
 *
 * Each agent (main or sub-agent) gets its own IterationBudget.
 * [consume] returns false when the budget is exhausted — the loop
 * should stop. [refund] returns budget for read-only tool calls
 * that don't need a separate LLM iteration (e.g. file_read, search).
 */
class IterationBudget(val maxTotal: Int) {
    private val _used = AtomicInteger(0)

    val used: Int get() = _used.get()
    val remaining: Int get() = (maxTotal - _used.get()).coerceAtLeast(0)

    /**
     * Try to consume one iteration.
     * Returns true if within budget, false if exhausted.
     */
    fun consume(): Boolean {
        while (true) {
            val current = _used.get()
            if (current >= maxTotal) return false
            if (_used.compareAndSet(current, current + 1)) return true
        }
    }

    /**
     * Refund one iteration (e.g. for read-only tool calls).
     * Never goes below 0.
     */
    fun refund() {
        while (true) {
            val current = _used.get()
            if (current <= 0) return
            if (_used.compareAndSet(current, current - 1)) return
        }
    }
}
