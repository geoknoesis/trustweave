package com.trustweave.core.plugin

import com.trustweave.core.exception.TrustWeaveException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Error handling tests for ProviderChain.
 *
 * **Test Isolation**: Each test uses its own isolated PluginRegistry instance.
 */
class ProviderChainErrorTest {

    private lateinit var registry: PluginRegistry

    @BeforeEach
    fun setup() {
        registry = DefaultPluginRegistry()
    }

    @Test
    fun `test ProviderChain constructor throws when providers list is empty`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ProviderChain<TestProvider>(emptyList())
        }

        assertTrue(exception.message?.contains("at least one provider") == true)
    }

    @Test
    fun `test ProviderChain constructor throws when selector filters all providers`() {
        val providers = listOf(
            TestProvider("provider-1"),
            TestProvider("provider-2")
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            ProviderChain(providers) { false }
        }

        assertTrue(exception.message?.contains("filters out all providers") == true)
    }

    @Test
    fun `test execute throws AllProvidersFailed when all providers fail`() = runBlocking {
        val providers = listOf(
            TestProvider("provider-1", shouldFail = true),
            TestProvider("provider-2", shouldFail = true)
        )
        val chain = ProviderChain(providers)

        val exception = assertFailsWith<TrustWeaveException.AllProvidersFailed> {
            chain.execute { it.operation() }
        }

        assertEquals("ALL_PROVIDERS_FAILED", exception.code)
        assertEquals(2, exception.attemptedProviders.size)
        // Provider ID might be qualified name or simple name
        assertTrue(exception.attemptedProviders.any { it.contains("TestProvider") })
        assertNotNull(exception.lastException)
        assertTrue(exception.providerErrors.isNotEmpty())
    }

    @Test
    fun `test execute throws AllProvidersFailed with provider error details`() = runBlocking {
        val providers = listOf(
            TestProvider("provider-1", shouldFail = true, errorMessage = "Error 1"),
            TestProvider("provider-2", shouldFail = true, errorMessage = "Error 2")
        )
        val chain = ProviderChain(providers)

        val exception = assertFailsWith<TrustWeaveException.AllProvidersFailed> {
            chain.execute { it.operation() }
        }

        assertEquals(2, exception.providerErrors.size)
        // Verify both error messages are captured correctly
        assertTrue(exception.providerErrors.values.any { it == "Error 1" },
            "Should contain Error 1")
        assertTrue(exception.providerErrors.values.any { it == "Error 2" },
            "Should contain Error 2")
    }

    @Test
    fun `test execute does not catch Error types`() = runBlocking {
        val providers = listOf(
            TestProvider("provider-1", shouldThrowError = true)
        )
        val chain = ProviderChain(providers)

        // Should propagate Error types (OutOfMemoryError, etc.)
        assertFailsWith<OutOfMemoryError> {
            chain.execute { it.operation() }
        }
    }

    @Test
    fun `test createProviderChain throws NoProvidersFound when no providers exist`() {
        val exception = assertFailsWith<TrustWeaveException.NoProvidersFound> {
            createProviderChain<TestProvider>(listOf("nonexistent-1", "nonexistent-2"), registry)
        }

        assertEquals("NO_PROVIDERS_FOUND", exception.code)
        assertEquals(2, exception.pluginIds.size)
        assertTrue(exception.pluginIds.contains("nonexistent-1"))
        assertTrue(exception.pluginIds.contains("nonexistent-2"))
    }

    @Test
    fun `test createProviderChain throws PartialProvidersFound when some providers missing`() {
        val metadata = PluginMetadata(
            id = "plugin-1",
            name = "Plugin 1",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities()
        )
        registry.register(metadata, TestProvider("provider-1"))

        val exception = assertFailsWith<TrustWeaveException.PartialProvidersFound> {
            createProviderChain<TestProvider>(listOf("plugin-1", "plugin-2", "plugin-3"), registry)
        }

        assertEquals("PARTIAL_PROVIDERS_FOUND", exception.code)
        assertEquals(3, exception.requestedIds.size)
        assertEquals(1, exception.foundIds.size)
        assertEquals(2, exception.missingIds.size)
        assertTrue(exception.foundIds.contains("plugin-1"))
        assertTrue(exception.missingIds.contains("plugin-2"))
        assertTrue(exception.missingIds.contains("plugin-3"))
    }

    @Test
    fun `test createProviderChain succeeds when all providers found`() {
        val metadata1 = PluginMetadata(
            id = "plugin-1",
            name = "Plugin 1",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities()
        )
        val metadata2 = PluginMetadata(
            id = "plugin-2",
            name = "Plugin 2",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities()
        )

        registry.register(metadata1, TestProvider("provider-1"))
        registry.register(metadata2, TestProvider("provider-2"))

        val chain = createProviderChain<TestProvider>(listOf("plugin-1", "plugin-2"), registry)

        assertNotNull(chain)
        assertEquals(2, chain.size())
    }

    @Test
    fun `test createProviderChainFromConfig returns null when chain not found`() {
        val config = PluginConfiguration()

        val chain = createProviderChainFromConfig<TestProvider>("nonexistent-chain", config, registry)

        assertNull(chain)
    }

    @Test
    fun `test selectedSize returns correct count`() {
        val providers = listOf(
            TestProvider("provider-1"),
            TestProvider("provider-2"),
            TestProvider("provider-3")
        )

        val chain = ProviderChain(providers) { it.name != "provider-2" }

        assertEquals(3, chain.size())
        assertEquals(2, chain.selectedSize())
    }

    private class TestProvider(
        val name: String,
        val shouldFail: Boolean = false,
        val errorMessage: String = "Provider failed",
        val shouldThrowError: Boolean = false
    ) {
        suspend fun operation(): String {
            if (shouldThrowError) {
                throw OutOfMemoryError("Test error")
            }
            if (shouldFail) {
                throw RuntimeException(errorMessage)
            }
            return "$name-result"
        }
    }
}

