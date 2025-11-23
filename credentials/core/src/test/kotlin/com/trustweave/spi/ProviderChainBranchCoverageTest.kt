package com.trustweave.core

import com.trustweave.core.TrustWeaveException  // From :common module
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Branch coverage tests for ProviderChain.
 */
class ProviderChainBranchCoverageTest {

    @Test
    fun `test ProviderChain constructor requires non-empty providers`() {
        assertFailsWith<IllegalArgumentException> {
            ProviderChain<TestProvider>(emptyList())
        }
    }

    @Test
    fun `test ProviderChain execute succeeds on first provider`() = runBlocking {
        val provider1 = TestProvider("provider1", succeeds = true)
        val provider2 = TestProvider("provider2", succeeds = true)
        val chain = ProviderChain(listOf(provider1, provider2))
        
        val result = chain.execute { it.operation() }
        
        assertEquals("provider1-result", result)
        assertTrue(provider1.called)
        assertFalse(provider2.called) // Should not be called if first succeeds
    }

    @Test
    fun `test ProviderChain execute falls back to second provider`() = runBlocking {
        val provider1 = TestProvider("provider1", succeeds = false)
        val provider2 = TestProvider("provider2", succeeds = true)
        val chain = ProviderChain(listOf(provider1, provider2))
        
        val result = chain.execute { it.operation() }
        
        assertEquals("provider2-result", result)
        assertTrue(provider1.called)
        assertTrue(provider2.called)
    }

    @Test
    fun `test ProviderChain execute throws when all providers fail`() = runBlocking {
        val provider1 = TestProvider("provider1", succeeds = false)
        val provider2 = TestProvider("provider2", succeeds = false)
        val chain = ProviderChain(listOf(provider1, provider2))
        
        assertFailsWith<ProviderChainException> {
            chain.execute { it.operation() }
        }
    }

    @Test
    fun `test ProviderChain execute with selector skips providers`() = runBlocking {
        val provider1 = TestProvider("provider1", succeeds = true)
        val provider2 = TestProvider("provider2", succeeds = true)
        val chain = ProviderChain(listOf(provider1, provider2)) { it.name == "provider2" }
        
        val result = chain.execute { it.operation() }
        
        assertEquals("provider2-result", result)
        assertFalse(provider1.called)
        assertTrue(provider2.called)
    }

    @Test
    fun `test ProviderChain execute with selector that excludes all throws`() = runBlocking {
        val provider1 = TestProvider("provider1", succeeds = true)
        val provider2 = TestProvider("provider2", succeeds = true)
        val chain = ProviderChain(listOf(provider1, provider2)) { false }
        
        assertFailsWith<ProviderChainException> {
            chain.execute { it.operation() }
        }
    }

    @Test
    fun `test ProviderChain executeAndTransform transforms result`() = runBlocking {
        val provider1 = TestProvider("provider1", succeeds = true)
        val chain = ProviderChain(listOf(provider1))
        
        val result = chain.executeAndTransform(
            operation = { it.operation() },
            transform = { it.uppercase() }
        )
        
        assertEquals("PROVIDER1-RESULT", result)
    }

    @Test
    fun `test ProviderChain executeAndTransform falls back on failure`() = runBlocking {
        val provider1 = TestProvider("provider1", succeeds = false)
        val provider2 = TestProvider("provider2", succeeds = true)
        val chain = ProviderChain(listOf(provider1, provider2))
        
        val result = chain.executeAndTransform(
            operation = { it.operation() },
            transform = { it.uppercase() }
        )
        
        assertEquals("PROVIDER2-RESULT", result)
    }

    @Test
    fun `test ProviderChain size returns correct count`() {
        val provider1 = TestProvider("provider1", succeeds = true)
        val provider2 = TestProvider("provider2", succeeds = true)
        val chain = ProviderChain(listOf(provider1, provider2))
        
        assertEquals(2, chain.size())
    }

    @Test
    fun `test ProviderChain isEmpty returns false when not empty`() {
        val provider1 = TestProvider("provider1", succeeds = true)
        val chain = ProviderChain(listOf(provider1))
        
        assertFalse(chain.isEmpty())
    }

    @Test
    fun `test ProviderChainException contains last exception`() {
        val cause = RuntimeException("Test error")
        val exception = ProviderChainException("All providers failed", cause)
        
        assertEquals("All providers failed", exception.message)
        assertEquals(cause, exception.lastException)
    }

    @Test
    fun `test ProviderChainException without cause`() {
        val exception = ProviderChainException("All providers failed")
        
        assertEquals("All providers failed", exception.message)
        assertNull(exception.lastException)
    }

    @Test
    fun `test createProviderChain with valid plugin IDs`() {
        val registry = PluginRegistry
        PluginRegistry.clear()
        
        val provider1 = TestProvider("provider1", succeeds = true)
        val metadata = PluginMetadata(
            id = "plugin1",
            name = "Test Plugin 1",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities()
        )
        PluginRegistry.register(metadata, provider1)
        
        val chain = createProviderChain<TestProvider>(listOf("plugin1"), registry)
        
        assertNotNull(chain)
        assertEquals(1, chain.size())
    }

    @Test
    fun `test createProviderChain throws when no providers found`() {
        val registry = PluginRegistry
        PluginRegistry.clear()
        
        assertFailsWith<IllegalArgumentException> {
            createProviderChain<TestProvider>(listOf("nonexistent"), registry)
        }
    }

    @Test
    fun `test createProviderChain filters out null providers`() {
        val registry = PluginRegistry
        PluginRegistry.clear()
        
        val provider1 = TestProvider("provider1", succeeds = true)
        val metadata = PluginMetadata(
            id = "plugin1",
            name = "Test Plugin 1",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities()
        )
        PluginRegistry.register(metadata, provider1)
        // plugin2 not registered
        
        val chain = createProviderChain<TestProvider>(listOf("plugin1", "plugin2"), registry)
        
        assertNotNull(chain)
        assertEquals(1, chain.size())
    }

    @Test
    fun `test createProviderChainFromConfig returns chain when found`() {
        val registry = PluginRegistry
        PluginRegistry.clear()
        
        val provider1 = TestProvider("provider1", succeeds = true)
        val metadata = PluginMetadata(
            id = "plugin1",
            name = "Test Plugin 1",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities()
        )
        PluginRegistry.register(metadata, provider1)
        
        val config = PluginConfiguration(
            providerChains = mapOf("test-chain" to listOf("plugin1"))
        )
        
        val chain = createProviderChainFromConfig<TestProvider>("test-chain", config, registry)
        
        assertNotNull(chain)
        assertEquals(1, chain?.size())
    }

    @Test
    fun `test createProviderChainFromConfig returns null when chain not found`() {
        val registry = PluginRegistry
        PluginRegistry.clear()
        
        val config = PluginConfiguration(
            providerChains = emptyMap()
        )
        
        val chain = createProviderChainFromConfig<TestProvider>("nonexistent", config, registry)
        
        assertNull(chain)
    }

    private class TestProvider(
        val name: String,
        private val succeeds: Boolean
    ) {
        var called = false
        
        suspend fun operation(): String {
            called = true
            if (succeeds) {
                return "$name-result"
            } else {
                throw RuntimeException("Provider $name failed")
            }
        }
    }
}

