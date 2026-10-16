package org.trustweave.kms.spi

import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.KmsCreationOptions
import org.trustweave.kms.inmemory.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyManagementServiceProviderDefaultsTest {
    private class Recording(
        override val supportedAlgorithms: Set<Algorithm> = setOf(Algorithm.Ed25519, Algorithm.RSA.RSA_2048),
        override val requiredEnvironmentVariables: List<String> = emptyList(),
    ) : KeyManagementServiceProvider {
        override val name = "recording"
        var lastOptions: Map<String, Any?>? = null

        override fun create(options: Map<String, Any?>): KeyManagementService {
            lastOptions = options
            return InMemoryKeyManagementService()
        }
    }

    @Test
    fun `algorithm support by name parses and then checks the set`() {
        val provider = Recording()

        assertTrue(provider.supportsAlgorithm("ed25519"))
        assertTrue(provider.supportsAlgorithm("RSA-2048"))
        assertFalse(provider.supportsAlgorithm("RSA-4096"))
        assertFalse(provider.supportsAlgorithm("P-256"))
    }

    @Test
    fun `an unparseable algorithm name is unsupported instead of an error`() {
        val provider = Recording()

        assertFalse(provider.supportsAlgorithm("not valid!"))
        assertFalse(provider.supportsAlgorithm("RSA"))
        assertFalse(provider.supportsAlgorithm(""))
    }

    @Test
    fun `no required environment variables is trivially satisfied`() {
        assertTrue(Recording().hasRequiredEnvironmentVariables())
    }

    @Test
    fun `optional variables never block and missing required variables do`() {
        val missing = "TRUSTWEAVE_TEST_DEFINITELY_UNSET_VARIABLE"
        assertTrue(Recording(requiredEnvironmentVariables = listOf("?$missing")).hasRequiredEnvironmentVariables())
        assertFalse(Recording(requiredEnvironmentVariables = listOf(missing)).hasRequiredEnvironmentVariables())
        assertFalse(Recording(requiredEnvironmentVariables = listOf("?$missing", missing)).hasRequiredEnvironmentVariables())
    }

    @Test
    fun `a variable that is present satisfies the requirement`() {
        val present = System.getenv().keys.first()
        assertTrue(Recording(requiredEnvironmentVariables = listOf(present)).hasRequiredEnvironmentVariables())
        assertTrue(Recording(requiredEnvironmentVariables = listOf("?$present")).hasRequiredEnvironmentVariables())
    }

    @Test
    fun `typed creation options are flattened into the map form`() {
        val provider = Recording()

        provider.create(KmsCreationOptions(enabled = false, priority = 7, additionalProperties = mapOf("region" to "eu")))

        assertEquals(mapOf<String, Any?>("enabled" to false, "priority" to 7, "region" to "eu"), provider.lastOptions)
    }
}
