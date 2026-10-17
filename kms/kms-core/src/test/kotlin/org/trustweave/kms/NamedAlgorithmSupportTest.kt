package org.trustweave.kms

import kotlinx.coroutines.runBlocking
import org.trustweave.kms.inmemory.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NamedAlgorithmSupportTest {
    @Test
    fun `service rejects malformed names while accepting supported names case insensitively`() =
        runBlocking {
            val service: KeyManagementService = InMemoryKeyManagementService()
            assertTrue(service.supportsAlgorithm("ed25519"))
            for (name in listOf("", " ", "not an algorithm", "RSA", "RSA-9999", "unregistered-custom")) {
                assertFalse(service.supportsAlgorithm(name), "Unexpected support for '$name'")
            }
        }
}
