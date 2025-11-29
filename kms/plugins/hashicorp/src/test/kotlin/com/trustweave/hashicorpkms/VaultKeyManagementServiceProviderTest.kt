package com.trustweave.hashicorpkms

import com.trustweave.kms.Algorithm
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class VaultKeyManagementServiceProviderTest {

    @Test
    fun `test provider name`() {
        val provider = VaultKeyManagementServiceProvider()
        assertEquals("vault", provider.name)
    }

    @Test
    fun `test provider supported algorithms`() {
        val provider = VaultKeyManagementServiceProvider()
        assertEquals(VaultKeyManagementService.SUPPORTED_ALGORITHMS, provider.supportedAlgorithms)
        assertTrue(provider.supportsAlgorithm(Algorithm.Ed25519))
        assertTrue(provider.supportsAlgorithm(Algorithm.Secp256k1))
        assertTrue(provider.supportsAlgorithm(Algorithm.P256))
        assertFalse(provider.supportsAlgorithm(Algorithm.BLS12_381))
    }

    @Test
    fun `test provider create with options`() {
        val provider = VaultKeyManagementServiceProvider()

        // Note: This will fail without actual Vault instance, but tests the creation logic
        assertThrows<IllegalArgumentException> {
            provider.create(emptyMap())
        }
    }

    @Test
    fun `test provider create with address`() {
        val provider = VaultKeyManagementServiceProvider()

        val kms = provider.create(mapOf(
            "address" to "http://localhost:8200",
            "token" to "test-token"
        ))

        assertNotNull(kms)
        assertTrue(kms is VaultKeyManagementService)
    }

    @Test
    fun `test provider supports algorithm by name`() {
        val provider = VaultKeyManagementServiceProvider()

        assertTrue(provider.supportsAlgorithm("Ed25519"))
        assertTrue(provider.supportsAlgorithm("ed25519"))
        assertTrue(provider.supportsAlgorithm("secp256k1"))
        assertFalse(provider.supportsAlgorithm("BLS12-381"))
    }
}

