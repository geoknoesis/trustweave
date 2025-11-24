package com.trustweave.core.plugin

import com.trustweave.core.exception.TrustWeaveError
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for ProviderChain.
 * 
 * **Test Isolation**: Each test uses its own isolated PluginRegistry instance.
 */
class ProviderChainTest {
    
    private lateinit var registry: PluginRegistry

    @BeforeEach
    fun setup() {
        registry = DefaultPluginRegistry()
    }

    @Test
    fun `test execute with successful provider`() = runBlocking {
        val providers = listOf(
            TestProvider("provider-1", shouldFail = false),
            TestProvider("provider-2", shouldFail = false)
        )
        
        val chain = ProviderChain(providers)
        
        val result = chain.execute { provider ->
            provider.getValue()
        }
        
        assertEquals("provider-1", result)
    }

    @Test
    fun `test execute with fallback to second provider`() = runBlocking {
        val providers = listOf(
            TestProvider("provider-1", shouldFail = true),
            TestProvider("provider-2", shouldFail = false)
        )
        
        val chain = ProviderChain(providers)
        
        val result = chain.execute { provider ->
            provider.getValue()
        }
        
        assertEquals("provider-2", result)
    }

    @Test
    fun `test execute throws AllProvidersFailed when all fail`() = runBlocking {
        val providers = listOf(
            TestProvider("provider-1", shouldFail = true),
            TestProvider("provider-2", shouldFail = true)
        )
        
        val chain = ProviderChain(providers)
        
        assertFailsWith<TrustWeaveError.AllProvidersFailed> {
            chain.execute { provider ->
                provider.getValue()
            }
        }
    }

    @Test
    fun `test execute with selector`() = runBlocking {
        val providers = listOf(
            TestProvider("provider-1", shouldFail = false),
            TestProvider("provider-2", shouldFail = false)
        )
        
        val chain = ProviderChain(providers) { it.name == "provider-2" }
        
        val result = chain.execute { provider ->
            provider.getValue()
        }
        
        assertEquals("provider-2", result)
    }

    @Test
    fun `test executeAndTransform`() = runBlocking {
        val providers = listOf(TestProvider("provider-1", shouldFail = false))
        val chain = ProviderChain(providers)
        
        val result = chain.executeAndTransform(
            operation = { it.getValue() },
            transform = { it.uppercase() }
        )
        
        assertEquals("PROVIDER-1", result)
    }

    @Test
    fun `test size`() {
        val providers = listOf(
            TestProvider("provider-1"),
            TestProvider("provider-2"),
            TestProvider("provider-3")
        )
        
        val chain = ProviderChain(providers)
        
        assertEquals(3, chain.size())
    }

    @Test
    fun `test selectedSize`() {
        val providers = listOf(
            TestProvider("provider-1"),
            TestProvider("provider-2"),
            TestProvider("provider-3")
        )
        
        val chain = ProviderChain(providers) { it.name != "provider-2" }
        
        assertEquals(3, chain.size())
        assertEquals(2, chain.selectedSize())
    }

    @Test
    fun `test isEmpty`() {
        val chain = ProviderChain(listOf(TestProvider("provider-1")))
        
        assertFalse(chain.isEmpty())
    }

    @Test
    fun `test ProviderChain requires at least one provider`() {
        assertFailsWith<IllegalArgumentException> {
            ProviderChain<TestProvider>(emptyList())
        }
    }

    @Test
    fun `test createProviderChain from plugin IDs`() = runBlocking {
        val metadata = PluginMetadata(
            id = "test-plugin",
            name = "Test Plugin",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities()
        )
        val instance = TestProvider("test-provider")
        
        registry.register(metadata, instance)
        
        val chain = createProviderChain<TestProvider>(listOf("test-plugin"), registry)
        
        assertEquals(1, chain.size())
    }

    @Test
    fun `test createProviderChainFromConfig`() = runBlocking {
        val metadata = PluginMetadata(
            id = "test-plugin",
            name = "Test Plugin",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities()
        )
        registry.register(metadata, TestProvider("test-provider"))
        
        val config = PluginConfiguration(
            providerChains = mapOf("credential-service" to listOf("test-plugin"))
        )
        
        val chain = createProviderChainFromConfig<TestProvider>("credential-service", config, registry)
        
        assertNotNull(chain)
        assertEquals(1, chain?.size())
    }

    @Test
    fun `test createProviderChainFromConfig returns null when chain not found`() {
        val config = PluginConfiguration()
        
        val chain = createProviderChainFromConfig<TestProvider>("nonexistent-chain", config, registry)
        
        assertNull(chain)
    }

    @Test
    fun `test execute with selector that filters some providers`() = runBlocking {
        val providers = listOf(
            TestProvider("provider-1", shouldFail = true),
            TestProvider("provider-2", shouldFail = false),
            TestProvider("provider-3", shouldFail = false)
        )
        
        // Selector filters out provider-1, so execution starts with provider-2
        val chain = ProviderChain(providers) { it.name != "provider-1" }
        
        val result = chain.execute { provider ->
            provider.getValue()
        }
        
        assertEquals("provider-2", result)
    }

    @Test
    fun `test execute with selector where first selected fails`() = runBlocking {
        val providers = listOf(
            TestProvider("provider-1", shouldFail = false),
            TestProvider("provider-2", shouldFail = true),
            TestProvider("provider-3", shouldFail = false)
        )
        
        // Selector filters out provider-1, so execution starts with provider-2 (fails), then provider-3
        val chain = ProviderChain(providers) { it.name != "provider-1" }
        
        val result = chain.execute { provider ->
            provider.getValue()
        }
        
        assertEquals("provider-3", result)
    }

    @Test
    fun `test executeAndTransform when operation fails`() = runBlocking {
        val providers = listOf(
            TestProvider("provider-1", shouldFail = true),
            TestProvider("provider-2", shouldFail = false)
        )
        val chain = ProviderChain(providers)
        
        val result = chain.executeAndTransform(
            operation = { it.getValue() },
            transform = { it.uppercase() }
        )
        
        // Should succeed with second provider
        assertEquals("PROVIDER-2", result)
    }

    private class TestProvider(val name: String, val shouldFail: Boolean = false) {
        suspend fun getValue(): String {
            if (shouldFail) {
                throw RuntimeException("Provider failed")
            }
            return name
        }
    }
}

