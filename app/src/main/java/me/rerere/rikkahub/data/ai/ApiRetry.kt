package me.rerere.rikkahub.data.ai

import android.util.Log
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val TAG = "ApiRetry"

/**
 * Maximum number of retry attempts for API calls.
 */
private const val MAX_RETRIES = 3

/**
 * Jittered exponential backoff for API retries.
 *
 * Matches Hermes' retry_utils.py pattern: base_delay × 2^(attempt-1) + jitter.
 * Prevents thundering-herd when multiple concurrent sessions hit rate limits.
 */
fun computeBackoffDelay(attempt: Int): Duration {
    val exponent = (attempt - 1).coerceAtLeast(0)
    val baseDelay = 2.0 // 2 seconds base
    val maxDelay = 60.0 // 60 seconds cap
    val delay = min(baseDelay * (2.0).pow(exponent), maxDelay)
    val jitter = Random.nextDouble(0.0, delay * 0.5)
    return (delay + jitter).seconds
}

/**
 * HTTP status codes that are safe to retry.
 */
private val RETRYABLE_STATUS_CODES = setOf(
    429, // rate limited
    500, 502, 503, 504, // server errors
)

/**
 * Error message patterns that indicate transient failures.
 */
private val RETRYABLE_MESSAGE_PATTERNS = listOf(
    Regex("Failed to get response", RegexOption.IGNORE_CASE),
    Regex("timeout|timed? ?out", RegexOption.IGNORE_CASE),
    Regex("rate.?limit|too many requests", RegexOption.IGNORE_CASE),
    Regex("overloaded|service unavailable", RegexOption.IGNORE_CASE),
    Regex("server error|internal error", RegexOption.IGNORE_CASE),
    Regex("connection reset|broken pipe", RegexOption.IGNORE_CASE),
    Regex("upstream connect error|upstream request timeout", RegexOption.IGNORE_CASE),
)

/**
 * Status codes that MUST NOT be retried (waste of time).
 */
private val NON_RETRYABLE_STATUS_CODES = setOf(
    400, // bad request
    401, // unauthorized
    403, // forbidden
    404, // not found
    413, // payload too large
)

/**
 * Classify whether an exception from a provider call is retryable.
 *
 * @return Pair(retryable, reason) — if retryable is true, reason describes the error class.
 */
fun classifyApiError(error: Throwable): Pair<Boolean, String> {
    val message = error.message ?: ""

    // Check non-retryable status codes first
    for (code in NON_RETRYABLE_STATUS_CODES) {
        if (message.contains("$code ")) {
            return false to "http_$code"
        }
    }

    // Check retryable status codes
    for (code in RETRYABLE_STATUS_CODES) {
        if (message.contains("$code ")) {
            return true to "http_$code"
        }
    }

    // Check message patterns
    for (pattern in RETRYABLE_MESSAGE_PATTERNS) {
        if (pattern.containsMatchIn(message)) {
            return true to "transient"
        }
    }

    // Anything else: might be a programming error, don't retry
    return false to "other"
}

/**
 * Execute a suspend block with API retry logic.
 * Uses jittered exponential backoff between retries.
 * Logs each retry attempt.
 *
 * @param operationName Human-readable name for logging (e.g. "generateText", "streamText").
 * @param block The suspend operation to retry.
 * @return The result of the operation on success.
 * @throws The last exception if all retries are exhausted.
 */
suspend fun <T> withApiRetry(
    operationName: String,
    block: suspend () -> T,
): T {
    var lastError: Throwable? = null
    var attempt = 0

    while (attempt <= MAX_RETRIES) {
        attempt++
        try {
            return block()
        } catch (e: Throwable) {
            lastError = e

            val (retryable, reason) = classifyApiError(e)
            if (!retryable || attempt > MAX_RETRIES) {
                Log.w(TAG, "$operationName: non-retryable or exhausted (attempt $attempt/$MAX_RETRIES, reason=$reason): ${e.message}")
                throw e
            }

            val delay = computeBackoffDelay(attempt)
            Log.w(TAG, "$operationName: retry #$attempt after $delay (reason=$reason)")
            kotlinx.coroutines.delay(delay)
        }
    }

    // Unreachable but satisfies the compiler
    throw lastError ?: Exception("Unknown error in $operationName after $MAX_RETRIES retries")
}
