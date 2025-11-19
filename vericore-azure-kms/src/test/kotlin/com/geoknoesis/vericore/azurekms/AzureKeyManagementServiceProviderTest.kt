package com.geoknoesis.vericore.azurekms

import com.geoknoesis.vericore.kms.Algorithm
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class AzureKeyManagementServiceProviderTest {

    @Test
    fun `test AzureKeyManagementServiceProvider`() {
        val provider = AzureKeyManagementServiceProvider()
        
        assertEquals("azure", provider.name)
        assertEquals(AzureKeyManagementService.SUPPORTED_ALGORITHMS, provider.supportedAlgorithms)
        assertTrue(provider.supportsAlgorithm(Algorithm.P256))
        assertTrue(provider.supportsAlgorithm(Algorithm.Secp256k1))
        assertFalse(provider.supportsAlgorithm(Algorithm.Ed25519)) // Not supported
        assertFalse(provider.supportsAlgorithm(Algorithm.BLS12_381)) // Not supported
    }

    @Test
    fun `test AzureKeyManagementServiceProvider create with options`() {
        val provider = AzureKeyManagementServiceProvider()
        
        val kms = provider.create(mapOf(
            "vaultUrl" to "https://testvault.vault.azure.net"
        ))
        
        assertNotNull(kms)
        assertTrue(kms is AzureKeyManagementService)
    }

    @Test
    fun `test AzureKeyManagementServiceProvider create without vaultUrl throws exception`() {
        val provider = AzureKeyManagementServiceProvider()
        
        assertThrows<IllegalArgumentException> {
            provider.create(emptyMap())
        }
    }

    @Test
    fun `test AzureKeyManagementServiceProvider create with service principal`() {
        val provider = AzureKeyManagementServiceProvider()
        
        val kms = provider.create(mapOf(
            "vaultUrl" to "https://testvault.vault.azure.net",
            "clientId" to "test-client-id",
            "clientSecret" to "test-client-secret",
            "tenantId" to "test-tenant-id"
        ))
        
        assertNotNull(kms)
        assertTrue(kms is AzureKeyManagementService)
    }
}

