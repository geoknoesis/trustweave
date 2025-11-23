package com.trustweave.testkit

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.testcontainers.junit.jupiter.Testcontainers
import kotlinx.coroutines.runBlocking

/**
 * Base class for integration tests.
 * 
 * Provides TestContainers support, retry logic for flaky tests, and test isolation helpers.
 * 
 * **Example Usage**:
 * ```kotlin
 * @Testcontainers
 * class MyIntegrationTest : BaseIntegrationTest() {
 *     @Test
 *     fun testWithContainers() = runBlocking {
 *         val fixture = createFixture()
 *         // Integration test code
 *     }
 * }
 * ```
 * 
 * **Note**: Integration tests should be tagged with `@Tag("integration")` to allow
 * running them separately from unit tests.
 */
@Tag("integration")
abstract class BaseIntegrationTest : BasePluginTest() {
    
    /**
     * Maximum number of retries for flaky tests.
     * Override in subclasses if needed.
     */
    protected open val maxRetries: Int = 3
    
    /**
     * Delay between retries in milliseconds.
     */
    protected open val retryDelayMs: Long = 1000
    
    /**
     * Timeout for integration test operations in seconds.
     */
    protected open val operationTimeoutSeconds: Long = 30
    
    /**
     * Sets up integration test environment.
     * Override to add custom setup logic.
     */
    @BeforeEach
    fun setUpIntegration() {
        setUp()
        // Additional integration test setup can be added here
    }
    
    /**
     * Cleans up integration test environment.
     * Override to add custom cleanup logic.
     */
    @AfterEach
    fun tearDownIntegration() {
        tearDown()
        // Additional integration test cleanup can be added here
    }
    
    /**
     * Retries a test operation if it fails.
     * Useful for flaky tests that may fail due to timing issues.
     */
    suspend fun <T> retry(
        maxAttempts: Int = maxRetries,
        delayMs: Long = retryDelayMs,
        block: suspend () -> T
    ): T {
        var lastException: Throwable? = null
        
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: Throwable) {
                lastException = e
                if (attempt < maxAttempts - 1) {
                    kotlinx.coroutines.delay(delayMs)
                }
            }
        }
        
        throw lastException ?: AssertionError("Retry failed after $maxAttempts attempts")
    }
    
    /**
     * Waits for a condition to become true, with timeout.
     */
    suspend fun waitFor(
        timeoutSeconds: Long = operationTimeoutSeconds,
        intervalMs: Long = 500,
        condition: suspend () -> Boolean
    ): Boolean {
        val timeoutMs = timeoutSeconds * 1000
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (condition()) {
                return true
            }
            kotlinx.coroutines.delay(intervalMs)
        }
        
        return false
    }
    
    /**
     * Asserts that a condition becomes true within the timeout period.
     */
    protected suspend fun assertEventually(
        timeoutSeconds: Long = operationTimeoutSeconds,
        intervalMs: Long = 500,
        message: String = "Condition did not become true within timeout",
        condition: suspend () -> Boolean
    ) {
        val result = waitFor(timeoutSeconds, intervalMs, condition)
        if (!result) {
            throw AssertionError(message)
        }
    }
    
    /**
     * Helper to run a test with automatic retry on failure.
     */
    protected inline fun <T> withRetry(
        maxAttempts: Int = maxRetries,
        crossinline test: suspend () -> T
    ): T = runBlocking {
        retry(maxAttempts) {
            test()
        }
    }
}

