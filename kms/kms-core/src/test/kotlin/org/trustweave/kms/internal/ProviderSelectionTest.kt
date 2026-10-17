package org.trustweave.kms.internal

import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.inmemory.InMemoryKeyManagementService
import org.trustweave.kms.spi.KeyManagementServiceProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ProviderSelectionTest {
    @Test
    fun `duplicate names do not redirect creation to an incompatible provider`() {
        for (preferred in listOf(null, "duplicate")) {
            val incompatible = RecordingProvider("duplicate", setOf(Algorithm.P256))
            val compatible = RecordingProvider("duplicate")
            val options = mapOf("region" to "test")
            val selection = ProviderSelection(listOf(incompatible, compatible))

            assertEquals("duplicate", selection.findBestProviderFor(Algorithm.Ed25519, preferred))
            assertSame(compatible.service, selection.createProviderFor(Algorithm.Ed25519, preferred, options))
            assertEquals(0, incompatible.receivedOptions.size)
            assertSame(options, compatible.receivedOptions.single())
        }
    }

    @Test
    fun `selection skips incompatible providers and stops at the first match`() {
        val incompatible = RecordingProvider("incompatible", setOf(Algorithm.P256))
        val first = RecordingProvider("first")
        val later = RecordingProvider("later")

        assertEquals("first", ProviderSelection(listOf(incompatible, first, later)).findBestProviderFor(Algorithm.Ed25519))
        assertEquals(listOf(1, 1, 0), listOf(incompatible.supportChecks, first.supportChecks, later.supportChecks))
    }

    @Test
    fun `preference overrides an earlier compatible provider without checking its support`() {
        val earlier = RecordingProvider("earlier")
        val preferred = RecordingProvider("preferred")

        assertEquals(
            "preferred",
            ProviderSelection(listOf(earlier, preferred)).findBestProviderFor(Algorithm.Ed25519, "preferred"),
        )
        assertEquals(0, earlier.supportChecks)
        assertEquals(1, preferred.supportChecks)
    }

    @Test
    fun `incompatible preference falls back in discovery order`() {
        val first = RecordingProvider("first")
        val preferred = RecordingProvider("preferred", setOf(Algorithm.P256))
        val later = RecordingProvider("later")

        assertEquals(
            "first",
            ProviderSelection(listOf(first, preferred, later)).findBestProviderFor(Algorithm.Ed25519, "preferred"),
        )
        assertEquals(listOf(1, 1, 0), listOf(first.supportChecks, preferred.supportChecks, later.supportChecks))
    }

    @Test
    fun `missing preference falls back to the first compatible provider`() {
        val first = RecordingProvider("first")
        val later = RecordingProvider("later")

        assertEquals("first", ProviderSelection(listOf(first, later)).findBestProviderFor(Algorithm.Ed25519, "missing"))
        assertEquals(1, first.supportChecks)
        assertEquals(0, later.supportChecks)
    }

    @Test
    fun `creation forwards the original options only to the selected provider`() {
        val earlier = RecordingProvider("earlier")
        val preferred = RecordingProvider("preferred")
        val options = mapOf("region" to "test-region", "nested" to mapOf("enabled" to true))

        val service = ProviderSelection(listOf(earlier, preferred)).createProviderFor(Algorithm.Ed25519, "preferred", options)

        assertSame(preferred.service, service)
        assertSame(options, preferred.receivedOptions.single())
        assertEquals(0, earlier.receivedOptions.size)
    }

    @Test
    fun `unsupported algorithm does not create any service`() {
        val provider = RecordingProvider("only", setOf(Algorithm.P256))
        val selection = ProviderSelection(listOf(provider))

        assertNull(selection.findBestProviderFor(Algorithm.Ed25519))
        assertNull(selection.createProviderFor(Algorithm.Ed25519))
        assertEquals(0, provider.receivedOptions.size)
        assertNull(ProviderSelection(emptyList()).createProviderFor(Algorithm.Ed25519))
    }

    private class RecordingProvider(
        override val name: String,
        override val supportedAlgorithms: Set<Algorithm> = setOf(Algorithm.Ed25519),
    ) : KeyManagementServiceProvider {
        var supportChecks = 0
            private set
        val receivedOptions = mutableListOf<Map<String, Any?>>()
        val service = InMemoryKeyManagementService()

        override fun supportsAlgorithm(algorithm: Algorithm): Boolean {
            supportChecks++
            return algorithm in supportedAlgorithms
        }

        override fun create(options: Map<String, Any?>): KeyManagementService {
            receivedOptions += options
            return service
        }
    }
}
