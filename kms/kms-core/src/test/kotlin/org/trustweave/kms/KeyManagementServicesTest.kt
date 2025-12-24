package org.trustweave.kms

import org.trustweave.kms.internal.ConfigCacheKey
import org.trustweave.kms.spi.KeyManagementServiceProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests for KeyManagementServices factory, including instance caching behavior.
 */
class KeyManagementServicesTest {

    @BeforeEach
    fun setUp() {
        // Clear cache before each test
        KeyManagementServices.clearCache()
    }

    @AfterEach
    fun tearDown() {
        // Clear cache after each test
        KeyManagementServices.clearCache()
    }

    @Test
    fun `should cache instances with same configuration`() {
        // Skip if no providers are available
        val providers = KeyManagementServices.availableProviders()
        if (providers.isEmpty()) return

        val providerName = providers.first()
        val kms1 = KeyManagementServices.create(providerName, emptyMap())
        val kms2 = KeyManagementServices.create(providerName, emptyMap())

        // Same configuration should return same instance
        assertSame(kms1, kms2, "Same configuration should return cached instance")
        assertEquals(1, KeyManagementServices.getCacheSize(), "Cache should contain one instance")
    }

    @Test
    fun `should create different instances with different configurations`() {
        val providers = KeyManagementServices.availableProviders()
        if (providers.isEmpty()) return

        val providerName = providers.first()
        val kms1 = KeyManagementServices.create(providerName, mapOf("option1" to "value1"))
        val kms2 = KeyManagementServices.create(providerName, mapOf("option2" to "value2"))

        // Different configurations should return different instances
        assertNotSame(kms1, kms2, "Different configurations should return different instances")
        assertEquals(2, KeyManagementServices.getCacheSize(), "Cache should contain two instances")
    }

    @Test
    fun `should cache instances with typed configuration`() {
        val providers = KeyManagementServices.availableProviders()
        if (providers.isEmpty()) return

        val providerName = providers.first()
        val options1 = KmsCreationOptions(enabled = true, priority = 10)
        val options2 = KmsCreationOptions(enabled = true, priority = 10)

        val kms1 = KeyManagementServices.create(providerName, options1)
        val kms2 = KeyManagementServices.create(providerName, options2)

        // Same typed configuration should return same instance
        assertSame(kms1, kms2, "Same typed configuration should return cached instance")
        assertEquals(1, KeyManagementServices.getCacheSize(), "Cache should contain one instance")
    }

    @Test
    fun `should create different instances with different typed configurations`() {
        val providers = KeyManagementServices.availableProviders()
        if (providers.isEmpty()) return

        val providerName = providers.first()
        val options1 = KmsCreationOptions(enabled = true, priority = 10)
        val options2 = KmsCreationOptions(enabled = false, priority = 20)

        val kms1 = KeyManagementServices.create(providerName, options1)
        val kms2 = KeyManagementServices.create(providerName, options2)

        // Different typed configurations should return different instances
        assertNotSame(kms1, kms2, "Different typed configurations should return different instances")
        assertEquals(2, KeyManagementServices.getCacheSize(), "Cache should contain two instances")
    }

    @Test
    fun `should handle empty map configuration`() {
        val providers = KeyManagementServices.availableProviders()
        if (providers.isEmpty()) return

        val providerName = providers.first()
        val kms1 = KeyManagementServices.create(providerName, emptyMap())
        val kms2 = KeyManagementServices.create(providerName, mapOf<String, Any?>())

        // Empty maps should be treated as same configuration
        assertSame(kms1, kms2, "Empty maps should return cached instance")
    }

    @Test
    fun `should clear cache`() {
        val providers = KeyManagementServices.availableProviders()
        if (providers.isEmpty()) return

        val providerName = providers.first()
        val kms1 = KeyManagementServices.create(providerName, emptyMap())
        assertEquals(1, KeyManagementServices.getCacheSize(), "Cache should contain one instance")

        KeyManagementServices.clearCache()
        assertEquals(0, KeyManagementServices.getCacheSize(), "Cache should be empty after clear")

        val kms2 = KeyManagementServices.create(providerName, emptyMap())
        // New instance should be created after cache clear
        assertNotSame(kms1, kms2, "After cache clear, new instance should be created")
        assertEquals(1, KeyManagementServices.getCacheSize(), "Cache should contain one instance again")
    }

    @Test
    fun `should cache instances with additional properties`() {
        val providers = KeyManagementServices.availableProviders()
        if (providers.isEmpty()) return

        val providerName = providers.first()
        val options1 = KmsCreationOptions(
            enabled = true,
            additionalProperties = mapOf("region" to "us-east-1")
        )
        val options2 = KmsCreationOptions(
            enabled = true,
            additionalProperties = mapOf("region" to "us-east-1")
        )

        val kms1 = KeyManagementServices.create(providerName, options1)
        val kms2 = KeyManagementServices.create(providerName, options2)

        assertSame(kms1, kms2, "Same additional properties should return cached instance")
    }

    @Test
    fun `should handle null values in configuration`() {
        val providers = KeyManagementServices.availableProviders()
        if (providers.isEmpty()) return

        val providerName = providers.first()
        val options1 = KmsCreationOptions(enabled = true, priority = null)
        val options2 = KmsCreationOptions(enabled = true, priority = null)

        val kms1 = KeyManagementServices.create(providerName, options1)
        val kms2 = KeyManagementServices.create(providerName, options2)

        assertSame(kms1, kms2, "Same configuration with nulls should return cached instance")
    }

    @Test
    fun `should be thread-safe when creating instances concurrently`() {
        val providers = KeyManagementServices.availableProviders()
        if (providers.isEmpty()) return

        val providerName = providers.first()
        val threadCount = 10
        val iterations = 100
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val instances = mutableSetOf<KeyManagementService>()
        val instanceCounter = AtomicInteger(0)

        try {
            // Create many instances concurrently with same configuration
            repeat(threadCount) {
                executor.submit {
                    try {
                        repeat(iterations) {
                            val kms = KeyManagementServices.create(providerName, emptyMap())
                            synchronized(instances) {
                                instances.add(kms)
                                instanceCounter.incrementAndGet()
                            }
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            
            // All threads should get the same instance (cached)
            assertEquals(1, instances.size, "All concurrent calls should return the same cached instance")
            assertEquals(threadCount * iterations, instanceCounter.get(), "All iterations should complete")
            
            // Cache should contain only one instance
            assertEquals(1, KeyManagementServices.getCacheSize(), "Cache should contain one instance")
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun `should handle concurrent creation with different configurations`() {
        val providers = KeyManagementServices.availableProviders()
        if (providers.isEmpty()) return

        val providerName = providers.first()
        val threadCount = 5
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val instances = mutableSetOf<KeyManagementService>()

        try {
            repeat(threadCount) { index ->
                executor.submit {
                    try {
                        val kms = KeyManagementServices.create(
                            providerName,
                            mapOf("thread" to index)
                        )
                        synchronized(instances) {
                            instances.add(kms)
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            
            // Each different configuration should create a different instance
            assertEquals(threadCount, instances.size, "Different configurations should create different instances")
            assertEquals(threadCount, KeyManagementServices.getCacheSize(), "Cache should contain all instances")
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun `should handle map configuration order independence`() {
        val providers = KeyManagementServices.availableProviders()
        if (providers.isEmpty()) return

        val providerName = providers.first()
        val kms1 = KeyManagementServices.create(providerName, mapOf(
            "key1" to "value1",
            "key2" to "value2"
        ))
        val kms2 = KeyManagementServices.create(providerName, mapOf(
            "key2" to "value2",
            "key1" to "value1"
        ))

        // Same key-value pairs in different order should return same instance
        assertSame(kms1, kms2, "Same configuration in different order should return cached instance")
    }

    @Test
    fun `should get available providers`() {
        val providers = KeyManagementServices.availableProviders()
        // This test should pass even if no providers are available (empty list is valid)
        assertNotNull(providers, "Should return a list (possibly empty)")
    }

    @Test
    fun `should throw exception for unknown provider`() {
        val exception = assertThrows<IllegalArgumentException> {
            KeyManagementServices.create("unknown-provider", emptyMap())
        }
        assertTrue(exception.message?.contains("unknown-provider") == true, "Error should mention provider name")
        assertTrue(exception.message?.contains("Available providers") == true, "Error should list available providers")
    }
}

/**
 * Helper function to assert that an exception is thrown.
 */
private inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
    return try {
        block()
        throw AssertionError("Expected exception ${T::class.simpleName} was not thrown")
    } catch (e: Throwable) {
        if (e is T) {
            e
        } else {
            throw AssertionError("Expected exception ${T::class.simpleName}, but got ${e::class.simpleName}", e)
        }
    }
}

