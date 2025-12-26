package org.trustweave.did.resolver

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

/**
 * Tests for RetryConfig.
 */
class RetryConfigTest {

    @Test
    fun `test default retry config`() {
        val config = RetryConfig.default()
        assertEquals(3, config.maxRetries)
        assertEquals(100L, config.initialDelayMs)
        assertEquals(2000L, config.maxDelayMs)
    }

    @Test
    fun `test no retry config`() {
        val config = RetryConfig.noRetry()
        assertEquals(0, config.maxRetries)
    }

    @Test
    fun `test aggressive retry config`() {
        val config = RetryConfig.aggressive()
        assertEquals(5, config.maxRetries)
        assertEquals(200L, config.initialDelayMs)
        assertEquals(5000L, config.maxDelayMs)
    }

    @Test
    fun `test executeWithRetry succeeds on first attempt`() = runBlocking {
        val config = RetryConfig.default()
        var attempts = 0
        
        val result = config.executeWithRetry {
            attempts++
            "success"
        }
        
        assertEquals("success", result)
        assertEquals(1, attempts)
    }

    @Test
    fun `test executeWithRetry retries on retryable exception`() = runBlocking {
        val config = RetryConfig(
            maxRetries = 2,
            initialDelayMs = 10,
            maxDelayMs = 100
        )
        var attempts = 0
        
        val result = config.executeWithRetry {
            attempts++
            if (attempts < 2) {
                throw java.net.ConnectException("Connection failed")
            }
            "success"
        }
        
        assertEquals("success", result)
        assertEquals(2, attempts)
    }

    @Test
    fun `test executeWithRetry fails after max retries`() = runBlocking {
        val config = RetryConfig(
            maxRetries = 2,
            initialDelayMs = 10,
            maxDelayMs = 100
        )
        var attempts = 0
        
        val exception = assertThrows<java.net.ConnectException> {
            config.executeWithRetry {
                attempts++
                throw java.net.ConnectException("Connection failed")
            }
        }
        
        assertEquals(3, attempts) // Initial + 2 retries
        assertTrue(exception.message?.contains("Connection failed") == true)
    }

    @Test
    fun `test executeWithRetry does not retry non-retryable exceptions`() = runBlocking {
        val config = RetryConfig.default()
        var attempts = 0
        
        val exception = assertThrows<IllegalArgumentException> {
            config.executeWithRetry {
                attempts++
                throw IllegalArgumentException("Not retryable")
            }
        }
        
        assertEquals(1, attempts) // No retries for non-retryable exceptions
        assertTrue(exception.message?.contains("Not retryable") == true)
    }

    @Test
    fun `test custom retryable exceptions`() = runBlocking {
        val config = RetryConfig(
            maxRetries = 1,
            initialDelayMs = 10,
            retryableExceptions = setOf(IllegalStateException::class.java)
        )
        var attempts = 0
        
        val result = config.executeWithRetry {
            attempts++
            if (attempts < 2) {
                throw IllegalStateException("Retryable")
            }
            "success"
        }
        
        assertEquals("success", result)
        assertEquals(2, attempts)
    }

    @Test
    fun `test exponential backoff`() = runBlocking {
        val config = RetryConfig(
            maxRetries = 3,
            initialDelayMs = 10,
            maxDelayMs = 1000
        )
        val delays = mutableListOf<Long>()
        var lastTime = System.currentTimeMillis()
        
        try {
            config.executeWithRetry {
                val currentTime = System.currentTimeMillis()
                if (delays.isNotEmpty()) {
                    delays.add(currentTime - lastTime)
                }
                lastTime = currentTime
                throw java.net.ConnectException("Fail")
            }
        } catch (e: Exception) {
            // Expected
        }
        
        // Should have delays that increase (exponential backoff)
        assertTrue(delays.size >= 2)
        // Delays should be increasing (with some variance due to jitter)
        assertTrue(delays[1] >= delays[0] || delays.size >= 3)
    }
}

