package me.rerere.rikkahub.data.ai.error

import android.util.Log
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.random.Random

/**
 * s11: Error Recovery — 结构化错误恢复 + diminishing returns 检测。
 *
 * 对标 learn-claude-code s11_error_recovery：
 * - Path 1: max_tokens 截断 → 升级 8K→64K → 续写提示（最多3次）
 * - Path 2: prompt_too_long → reactive compact → retry（一次）
 * - Path 3: 429/529 → 指数退避+抖动 → fallback 模型切换
 * - Diminishing returns: 连续3次续写 <500 token → 停止
 * - Streaming hold: 流式输出暂扣恢复
 */
private const val TAG = "ErrorRecovery"

data class RecoveryState(
    var hasEscalated: Boolean = false,
    var recoveryCount: Int = 0,
    var consecutive529: Int = 0,
    var hasAttemptedReactiveCompact: Boolean = false,
    var currentModel: String = "",
    var hasSwitchedModel: Boolean = false,
    // s11+: diminishing returns — 连续3次续写 <500 token 则停止
    var lowTokenContinuations: Int = 0,
    var lastContinuationTokens: Int = 0,
)

const val DEFAULT_MAX_TOKENS = 8192
const val ESCALATED_MAX_TOKENS = 64000
const val MAX_RECOVERY_RETRIES = 3
const val MAX_RETRIES = 10
const val BASE_DELAY_MS = 500L
const val MAX_CONSECUTIVE_529 = 3
/** 续写少于该 token 数视为 diminishing returns */
const val DIMINISHING_RETURNS_THRESHOLD = 500
/** 连续 N 次 diminishing returns 后停止 */
const val DIMINISHING_RETURNS_LIMIT = 3

const val CONTINUATION_PROMPT = "Output token limit hit. Resume directly — no apology, no recap. Pick up mid-thought."

/**
 * 指数退避+抖动。Retry-After 优先。
 */
fun retryDelayMs(attempt: Int, retryAfterMs: Long? = null): Long {
    if (retryAfterMs != null) return retryAfterMs
    val base = minOf(BASE_DELAY_MS * (2.0.pow(attempt)).toLong(), 32000L)
    val jitter = (Random.nextDouble() * base * 0.25).toLong()
    return base + jitter
}

fun isRateLimitError(e: Exception): Boolean {
    val msg = e.message?.lowercase() ?: ""
    return "ratelimit" in e::class.simpleName?.lowercase().orEmpty()
            || "429" in msg
            || "rate limit" in msg
            || "too many requests" in msg
}

fun isOverloadedError(e: Exception): Boolean {
    val msg = e.message?.lowercase() ?: ""
    return "overloaded" in e::class.simpleName?.lowercase().orEmpty()
            || "529" in msg
            || "overloaded" in msg
            || "service unavailable" in msg
}

fun isPromptTooLongError(e: Exception): Boolean {
    val msg = e.message?.lowercase() ?: ""
    return ("prompt" in msg && "long" in msg)
            || "prompt_is_too_long" in msg
            || "context_length_exceeded" in msg
            || "max_context_window" in msg
}

fun isMaxTokensTruncation(stopReason: String?): Boolean {
    return stopReason == "max_tokens"
}

/**
 * 对 transient errors 进行指数退避重试。
 * 非 transient error 会 throw 到外层。
 */
suspend fun <T> withRetry(
    block: suspend () -> T,
    state: RecoveryState,
): T {
    for (attempt in 0 until MAX_RETRIES) {
        try {
            val result = block()
            state.consecutive529 = 0
            return result
        } catch (e: Exception) {
            if (isRateLimitError(e)) {
                val delayMs = retryDelayMs(attempt)
                Log.w(TAG, "[429 rate limit] retry ${attempt + 1}/$MAX_RETRIES, wait ${delayMs}ms")
                delay(delayMs)
                continue
            }

            if (isOverloadedError(e)) {
                state.consecutive529++
                if (state.consecutive529 >= MAX_CONSECUTIVE_529) {
                    if (!state.hasSwitchedModel && state.currentModel.isNotBlank()) {
                        Log.w(TAG, "[529 x$MAX_CONSECUTIVE_529] switching to fallback model")
                        state.hasSwitchedModel = true
                        state.consecutive529 = 0
                    } else {
                        state.consecutive529 = 0
                        Log.w(TAG, "[529 x$MAX_CONSECUTIVE_529] no fallback, continuing retry")
                    }
                }
                val delayMs = retryDelayMs(attempt)
                Log.w(TAG, "[529 overloaded] retry ${attempt + 1}/$MAX_RETRIES, wait ${delayMs}ms")
                delay(delayMs)
                continue
            }

            throw e
        }
    }
    throw RuntimeException("Max retries ($MAX_RETRIES) exceeded")
}

/**
 * Reactive compact：紧急压缩上下文（保留最后 N 条消息 + 压缩标记）。
 */
fun reactiveCompact(messages: List<*>): List<*> {
    Log.w(TAG, "[reactive compact] trimming to last ${MAX_RECOVERY_RETRIES + 2} messages")
    val tail = messages.takeLast(MAX_RECOVERY_RETRIES + 2)
    return buildList {
        add(
            mapOf(
                "role" to "user",
                "content" to "[Reactive compact] Earlier conversation trimmed. Continue from where you left off."
            )
        )
        addAll(tail)
    }
}

/**
 * max_tokens 截断后的续写处理 + diminishing returns 检测。
 *
 * @return 是否需要继续循环
 */
fun handleMaxTokensTruncation(
    state: RecoveryState,
    lastOutputTokens: Int = 0,
): MaxTokensAction {
    // Diminishing returns: 连续3次续写 <500 token 增量 → 停止
    if (state.hasEscalated && lastOutputTokens > 0 && lastOutputTokens < DIMINISHING_RETURNS_THRESHOLD) {
        state.lowTokenContinuations++
        Log.w(TAG, "[diminishing returns] $lastOutputTokens tokens, ${state.lowTokenContinuations}/$DIMINISHING_RETURNS_LIMIT")
        if (state.lowTokenContinuations >= DIMINISHING_RETURNS_LIMIT) {
            Log.w(TAG, "[diminishing returns] limit reached, stopping")
            return MaxTokensAction.Stop
        }
    } else if (lastOutputTokens > DIMINISHING_RETURNS_THRESHOLD) {
        state.lowTokenContinuations = 0
    }

    // 第一次截断：不保存截断输出，直接升级 max_tokens 重试
    if (!state.hasEscalated) {
        state.hasEscalated = true
        Log.w(TAG, "[max_tokens] escalating $DEFAULT_MAX_TOKENS -> $ESCALATED_MAX_TOKENS")
        return MaxTokensAction.Escalate
    }
    // 64K 仍然截断：保存截断输出 + 续写提示
    if (state.recoveryCount < MAX_RECOVERY_RETRIES) {
        state.recoveryCount++
        state.lastContinuationTokens = lastOutputTokens
        Log.w(TAG, "[max_tokens] continuation ${state.recoveryCount}/$MAX_RECOVERY_RETRIES (last: ${lastOutputTokens}t)")
        return MaxTokensAction.Continue
    }
    Log.w(TAG, "[max_tokens] recovery limit reached")
    return MaxTokensAction.Stop
}

enum class MaxTokensAction {
    Escalate,
    Continue,
    Stop,
}
