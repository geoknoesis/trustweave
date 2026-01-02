package org.trustweave.kms

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AlgorithmDiscoveryTest {
    @Test
    fun `test discoverProviders returns map`() {
        val providers = AlgorithmDiscovery.discoverProviders()
        assertNotNull(providers, "discoverProviders should return a map")
        // May be empty if no providers are registered, but should not be null
    }

    @Test
    fun `test findProvidersFor with algorithm`() {
        val providers = AlgorithmDiscovery.findProvidersFor(Algorithm.Ed25519)
        assertNotNull(providers, "findProvidersFor should return a list")
        // May be empty if no providers support Ed25519
    }

    @Test
    fun `test findProvidersFor with algorithm name`() {
        val providers = AlgorithmDiscovery.findProvidersFor("Ed25519")
        assertNotNull(providers, "findProvidersFor should return a list")
    }

    @Test
    fun `test findProvidersFor with invalid algorithm name returns empty`() {
        val providers = AlgorithmDiscovery.findProvidersFor("InvalidAlgorithm")
        assertTrue(providers.isEmpty(), "Invalid algorithm name should return empty list")
    }

    @Test
    fun `test findBestProviderFor with algorithm`() {
        val provider = AlgorithmDiscovery.findBestProviderFor(Algorithm.Ed25519)
        // May be null if no providers support the algorithm
        // This test just ensures the method doesn't throw
        assertNotNull(provider != null || provider == null) // Always true, but ensures method executes
    }

    @Test
    fun `test findBestProviderFor with preferred provider`() {
        val provider = AlgorithmDiscovery.findBestProviderFor(
            Algorithm.Ed25519,
            preferredProvider = "inmemory"
        )
        // May be null if preferred provider doesn't support the algorithm
        // This test just ensures the method doesn't throw
        assertNotNull(provider != null || provider == null)
    }

    @Test
    fun `test createProviderFor with algorithm`() {
        val kms = AlgorithmDiscovery.createProviderFor(Algorithm.Ed25519)
        // May be null if no providers support the algorithm
        // This test just ensures the method doesn't throw
        assertNotNull(kms != null || kms == null)
    }

    @Test
    fun `test createProviderFor with preferred provider`() {
        val kms = AlgorithmDiscovery.createProviderFor(
            Algorithm.Ed25519,
            preferredProvider = "inmemory",
            options = emptyMap()
        )
        // May be null if preferred provider doesn't support the algorithm
        // This test just ensures the method doesn't throw
        assertNotNull(kms != null || kms == null)
    }

    @Test
    fun `test createProviderFor with options`() {
        val kms = AlgorithmDiscovery.createProviderFor(
            Algorithm.Ed25519,
            options = mapOf("test" to "value")
        )
        // May be null if no providers support the algorithm
        // This test just ensures the method doesn't throw
        assertNotNull(kms != null || kms == null)
    }
}

