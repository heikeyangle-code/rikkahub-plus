package me.rerere.rikkahub.data.ai.error

import android.util.Log
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.random.Random

/**
 * s11: Error Recovery — 结构化错误恢复。
 *
 * 对标 learn-claude-code s11_error_recovery：
 * - Path 1: max_tokens 截断 → 升级 8K→64K → 续写提示（最多3次）
 * - Path 2: prompt_too_long → reactive compact → retry（一次）
 * - Path 3: 429/529 → 指数退避+抖动 → fallback 模型切换
 * - withRetry wrapper for transient errors
 *
 * 用法：
 *   val recovery = RecoveryState()
 *   val result = withRetry("primary_model") { LLM调用 }
 *
 * 非流式路径直接使用 withRetry。
 * 流式路径在 GenerationHandler 外层 catch 后调用 handleStreamError。
 */
private const val TAG = "ErrorRecovery"

data class RecoveryState(
    /** 是否已升级 max_tokens（8K→64K） */
    var hasEscalated: Boolean = false,
    /** 当前重试次数 */
    var recoveryCount: Int = 0,
    /** 连续 529 过载次数 */
    var consecutive529: Int = 0,
    /** 是否已尝试 reactive compact */
    var hasAttemptedReactiveCompact: Boolean = false,
    /** 当前生效的模型 ID */
    var currentModel: String = "",
    /** 是否已切换 fallback 模型 */
    var hasSwitchedModel: Boolean = false,
)

/** max_tokens 默认值 */
const val DEFAULT_MAX_TOKENS = 8192

/** max_tokens 升级值 */
const val ESCALATED_MAX_TOKENS = 64000

/** 最大续写重试次数 */
const val MAX_RECOVERY_RETRIES = 3

/** 临时故障最大重试次数 */
const val MAX_RETRIES = 10

/** 退避基时（毫秒） */
const val BASE_DELAY_MS = 500L

/** 连续 529 触发 fallback 的阈值 */
const val MAX_CONSECUTIVE_529 = 3

/** 续写提示 */
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

/**
 * 判断是否是 429 Rate Limit 错误
 */
fun isRateLimitError(e: Exception): Boolean {
    val msg = e.message?.lowercase() ?: ""
    return "ratelimit" in e::class.simpleName?.lowercase().orEmpty()
            || "429" in msg
            || "rate limit" in msg
            || "too many requests" in msg
}

/**
 * 判断是否是 529 Overloaded 错误
 */
fun isOverloadedError(e: Exception): Boolean {
    val msg = e.message?.lowercase() ?: ""
    return "overloaded" in e::class.simpleName?.lowercase().orEmpty()
            || "529" in msg
            || "overloaded" in msg
            || "service unavailable" in msg
}

/**
 * 判断是否是 prompt_too_long / context_length_exceeded 错误
 */
fun isPromptTooLongError(e: Exception): Boolean {
    val msg = e.message?.lowercase() ?: ""
    return ("prompt" in msg && "long" in msg)
            || "prompt_is_too_long" in msg
            || "context_length_exceeded" in msg
            || "max_context_window" in msg
}

/**
 * 判断是否是 max_tokens 截断
 */
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
            // 429 Rate Limit → 指数退避
            if (isRateLimitError(e)) {
                val delayMs = retryDelayMs(attempt)
                Log.w(TAG, "[429 rate limit] retry ${attempt + 1}/$MAX_RETRIES, wait ${delayMs}ms")
                delay(delayMs)
                continue
            }

            // 529 Overloaded → 指数退避 + fallback 模型
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

            // 非 transient → throw 到外层 try/catch
            throw e
        }
    }
    throw RuntimeException("Max retries ($MAX_RETRIES) exceeded")
}

/**
 * Reactive compact：紧急压缩上下文（保留最后 N 条消息 + 压缩标记）。
 * 对标 learn-claude-code s11 的 reactive_compact。
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
 * max_tokens 截断后的续写处理。
 *
 * @return 是否需要继续循环（true = 已处理，继续重试 LLM 调用）
 */
fun handleMaxTokensTruncation(
    state: RecoveryState,
): MaxTokensAction {
    // 第一次截断：不保存截断输出，直接升级 max_tokens 重试
    if (!state.hasEscalated) {
        state.hasEscalated = true
        Log.w(TAG, "[max_tokens] escalating $DEFAULT_MAX_TOKENS -> $ESCALATED_MAX_TOKENS")
        return MaxTokensAction.Escalate
    }
    // 64K 仍然截断：保存截断输出 + 续写提示
    if (state.recoveryCount < MAX_RECOVERY_RETRIES) {
        state.recoveryCount++
        Log.w(TAG, "[max_tokens] continuation ${state.recoveryCount}/$MAX_RECOVERY_RETRIES")
        return MaxTokensAction.Continue
    }
    // 恢复次数耗尽
    Log.w(TAG, "[max_tokens] recovery limit reached")
    return MaxTokensAction.Stop
}

enum class MaxTokensAction {
    /** 升级 max_tokens 并重试（不保存当前输出） */
    Escalate,
    /** 保存截断输出 + 追加续写提示 */
    Continue,
    /** 无法恢复，停止 */
    Stop,
}
