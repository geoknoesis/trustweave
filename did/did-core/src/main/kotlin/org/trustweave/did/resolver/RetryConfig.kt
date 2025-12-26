package org.trustweave.did.resolver

import kotlinx.coroutines.delay
import kotlin.math.pow

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
    )
) {
    init {
        require(maxRetries >= 0) { "maxRetries must be non-negative" }
        require(initialDelayMs > 0) { "initialDelayMs must be positive" }
        require(maxDelayMs >= initialDelayMs) { "maxDelayMs must be >= initialDelayMs" }
    }

    /**
     * Executes a suspend function with retry logic.
     *
     * @param block The suspend function to execute
     * @return The result of the function
     * @throws Throwable The last exception if all retries are exhausted
     */
    suspend fun <T> executeWithRetry(block: suspend () -> T): T {
        var lastException: Throwable? = null
        var delayMs = initialDelayMs

        for (attempt in 0..maxRetries) {
            try {
                return block()
            } catch (e: Throwable) {
                lastException = e

                // Check if exception is retryable
                val isRetryable = retryableExceptions.any { it.isInstance(e) }

                // Don't retry if:
                // 1. We've exhausted all retries
                // 2. The exception is not retryable
                if (attempt >= maxRetries || !isRetryable) {
                    throw e
                }

                // Exponential backoff with jitter
                delayMs = minOf(
                    (initialDelayMs * 2.0.pow(attempt.toDouble())).toLong(),
                    maxDelayMs
                )

                // Add small random jitter to avoid thundering herd
                val jitter = (delayMs * 0.1).toLong()
                val randomJitter = (Math.random() * jitter).toLong()
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

