package org.trustweave.kms

import kotlinx.coroutines.runBlocking
import org.trustweave.kms.inmemory.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlgorithmDiscoveryTest {
    @Test
    fun `discovery includes the registered in-memory provider and its algorithms`() {
        assertEquals(
            InMemoryKeyManagementService.SUPPORTED_ALGORITHMS,
            AlgorithmDiscovery.discoverProviders()["inmemory"],
        )
    }

    @Test
    fun `algorithm lookup includes the in-memory provider`() {
        assertTrue("inmemory" in AlgorithmDiscovery.findProvidersFor(Algorithm.Ed25519))
    }

    @Test
    fun `algorithm name lookup is case insensitive`() {
        assertEquals(
            AlgorithmDiscovery.findProvidersFor(Algorithm.Ed25519),
            AlgorithmDiscovery.findProvidersFor("ed25519"),
        )
    }

    @Test
    fun `unregistered custom algorithm name returns no providers`() {
        assertTrue(AlgorithmDiscovery.findProvidersFor("InvalidAlgorithm").isEmpty())
    }

    @Test
    fun `malformed algorithm names return no providers`() {
        // A syntactically valid unknown name is a custom algorithm, not a parse failure.
        for (name in listOf("", " ", "not an algorithm", "RSA", "RSA-9999")) {
            assertTrue(AlgorithmDiscovery.findProvidersFor(name).isEmpty(), "Unexpected provider for '$name'")
        }
    }

    @Test
    fun `default selection follows discovery order`() {
        assertEquals(
            AlgorithmDiscovery.findProvidersFor(Algorithm.Ed25519).first(),
            AlgorithmDiscovery.findBestProviderFor(Algorithm.Ed25519),
        )
    }

    @Test
    fun `supported preferred provider takes priority`() {
        assertEquals("inmemory", AlgorithmDiscovery.findBestProviderFor(Algorithm.Ed25519, "inmemory"))
    }

    @Test
    fun `missing preferred provider falls back to the first compatible provider`() {
        assertEquals(
            AlgorithmDiscovery.findBestProviderFor(Algorithm.Ed25519),
            AlgorithmDiscovery.findBestProviderFor(Algorithm.Ed25519, "missing-provider"),
        )
    }

    @Test
    fun `unsupported algorithm returns no providers or instances`() {
        val algorithm = Algorithm.Custom("unsupported-test-algorithm")
        assertTrue(AlgorithmDiscovery.findProvidersFor(algorithm).isEmpty())
        assertNull(AlgorithmDiscovery.findBestProviderFor(algorithm, "inmemory"))
        assertNull(AlgorithmDiscovery.createProviderFor(algorithm, "inmemory"))
    }

    @Test
    fun `preferred selection creates the in-memory service`() {
        assertIs<InMemoryKeyManagementService>(
            AlgorithmDiscovery.createProviderFor(
                Algorithm.Ed25519,
                preferredProvider = "inmemory",
                options = mapOf("test" to "value"),
            ),
        )
    }

    @Test
    fun `default selection creates a compatible service`() =
        runBlocking {
            val service = assertNotNull(AlgorithmDiscovery.createProviderFor(Algorithm.Ed25519))
            assertTrue(service.supportsAlgorithm(Algorithm.Ed25519))
        }
}
