package org.trustweave.did.resolver

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.did.identifiers.Did
import org.trustweave.did.dsl.universalResolver
import kotlin.test.*

/**
 * Integration tests for RetryConfig with DefaultUniversalResolver.
 */
class RetryConfigIntegrationTest {

    @Test
    fun `test resolver with default retry config`() {
        val resolver = DefaultUniversalResolver(
            baseUrl = "https://dev.uniresolver.io",
            retryConfig = RetryConfig.default()
        )
        
        assertEquals("https://dev.uniresolver.io", resolver.baseUrl)
    }

    @Test
    fun `test resolver with no retry config`() {
        val resolver = DefaultUniversalResolver(
            baseUrl = "https://dev.uniresolver.io",
            retryConfig = RetryConfig.noRetry()
        )
        
        assertEquals("https://dev.uniresolver.io", resolver.baseUrl)
    }

    @Test
    fun `test resolver with aggressive retry config`() {
        val resolver = DefaultUniversalResolver(
            baseUrl = "https://dev.uniresolver.io",
            retryConfig = RetryConfig.aggressive()
        )
        
        assertEquals("https://dev.uniresolver.io", resolver.baseUrl)
    }

    @Test
    fun `test resolver with custom retry config`() {
        val customRetry = RetryConfig(
            maxRetries = 2,
            initialDelayMs = 50,
            maxDelayMs = 500
        )
        
        val resolver = DefaultUniversalResolver(
            baseUrl = "https://dev.uniresolver.io",
            retryConfig = customRetry
        )
        
        assertEquals("https://dev.uniresolver.io", resolver.baseUrl)
    }

    @Test
    fun `test resolver builder with retry config`() {
        val resolver = universalResolver("https://dev.uniresolver.io") {
            timeout = 30
            retry {
                maxRetries = 3
                initialDelayMs = 100
            }
        }
        
        assertEquals("https://dev.uniresolver.io", resolver.baseUrl)
    }
}

