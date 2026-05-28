package org.trustweave.did.resolver

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.reflect.KClass

/**
 * Configuration for retry logic in HTTP operations.
 *
 * Provides exponential backoff retry strategy for transient failures.
 *
 * **Example Usage:**
 * ```kotlin
 * val resolver = DefaultUniversalResolver(
 *     baseUrl = "https://resolver.example.com",
 *     retryConfig = RetryConfig(
 *         maxRetries = 3,
 *         initialDelayMs = 100,
 *         maxDelayMs = 2000,
 *         retryableStatusCodes = setOf(500, 502, 503, 504)
 *     )
 * )
 * ```
 *
 * @param maxRetries Maximum number of retry attempts (default: 3)
 * @param initialDelayMs Initial delay before first retry in milliseconds (default: 100)
 * @param maxDelayMs Maximum delay between retries in milliseconds (default: 2000)
 * @param retryableStatusCodes HTTP status codes that should trigger retry (default: 5xx errors)
 * @param retryableExceptions Exception types that should trigger retry (default: network exceptions)
 * @param nonRetryableExceptions Exception types that must never be retried even if they extend a retryable type
 */
data class RetryConfig(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 100,
    val maxDelayMs: Long = 2000,
    val retryableStatusCodes: Set<Int> = setOf(500, 502, 503, 504),
    val retryableExceptions: Set<Class<out Throwable>> = setOf(
        java.net.ConnectException::class.java,
        java.net.SocketTimeoutException::class.java,
        java.io.IOException::class.java
    ),
    val nonRetryableExceptions: List<KClass<out Throwable>> = listOf()
) {
    init {
        require(maxRetries >= 0) { "maxRetries must be non-negative" }
        require(initialDelayMs > 0) { "initialDelayMs must be positive" }
        require(maxDelayMs >= initialDelayMs) { "maxDelayMs must be >= initialDelayMs" }
    }

    /**
     * Executes a suspend function with retry logic.
     *
     * **Interruption policy**: [java.io.InterruptedIOException] (excluding
     * [java.net.SocketTimeoutException]) is always rethrown immediately, regardless of
     * [retryableExceptions] or [nonRetryableExceptions] configuration. It signals thread
     * interruption and must not be swallowed or retried. [java.net.SocketTimeoutException] is a
     * subclass of [java.io.InterruptedIOException] but represents a transient network condition
     * and is allowed to follow the normal retry path.
     *
     * @param block The suspend function to execute
     * @return The result of the function
     * @throws Throwable The last exception if all retries are exhausted
     */
    suspend fun <T> executeWithRetry(block: suspend () -> T): T {
        var lastException: Throwable? = null

        for (attempt in 0..maxRetries) {
            try {
                return block()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e  // Never retry cancellation
                if (e is Error) throw e  // Never retry JVM errors (OutOfMemoryError, StackOverflowError, etc.)
                if (e is InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw e
                }
                // Never retry thread-interruption-caused IOExceptions, but SocketTimeoutException
                // (a subclass of InterruptedIOException) is a transient network condition and
                // must follow the normal retry path rather than being re-thrown immediately.
                if (e is java.io.InterruptedIOException && e !is java.net.SocketTimeoutException) throw e

                lastException = e

                // Non-retryable exceptions should not be retried even if they extend a retryable type
                val nonRetryable = nonRetryableExceptions.any { it.isInstance(e) }
                if (nonRetryable) throw e

                // Check if exception is retryable
                val isRetryable = retryableExceptions.any { it.isInstance(e) }

                // Don't retry if:
                // 1. We've exhausted all retries
                // 2. The exception is not retryable
                if (attempt >= maxRetries || !isRetryable) {
                    throw e
                }

                // Exponential backoff with jitter
                val delayMs = minOf(
                    (initialDelayMs * 2.0.pow(attempt.toDouble())).toLong(),
                    maxDelayMs
                )

                // Add small random jitter (0–20% of delayMs) to avoid thundering herd.
                // Compute as Double before truncating to avoid zero-jitter when delayMs < 10.
                // Minimum jitter is 1 ms so there is always some spread even for tiny delays.
                val randomJitter = maxOf(1L, (delayMs * kotlin.random.Random.nextDouble(0.0, 0.2)).toLong())
                val finalDelay = delayMs + randomJitter

                delay(finalDelay)
            }
        }

        // Should never reach here, but handle it just in case
        throw lastException ?: IllegalStateException("Retry logic failed without exception")
    }

    companion object {
        /**
         * Default retry configuration with sensible defaults.
         */
        fun default(): RetryConfig = RetryConfig()

        /**
         * No retry configuration (fails immediately).
         */
        fun noRetry(): RetryConfig = RetryConfig(maxRetries = 0)

        /**
         * Aggressive retry configuration for unreliable networks.
         */
        fun aggressive(): RetryConfig = RetryConfig(
            maxRetries = 5,
            initialDelayMs = 200,
            maxDelayMs = 5000
        )
    }
}

