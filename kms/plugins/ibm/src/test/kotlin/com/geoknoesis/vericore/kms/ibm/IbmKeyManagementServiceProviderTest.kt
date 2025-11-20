package com.geoknoesis.vericore.kms.ibm

import com.geoknoesis.vericore.kms.Algorithm
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class IbmKeyManagementServiceProviderTest {

    @Test
    fun `test provider name`() {
        val provider = IbmKeyManagementServiceProvider()
        assertEquals("ibm", provider.name)
    }

    @Test
    fun `test provider supported algorithms`() {
        val provider = IbmKeyManagementServiceProvider()
        assertEquals(IbmKeyManagementService.SUPPORTED_ALGORITHMS, provider.supportedAlgorithms)
        assertTrue(provider.supportsAlgorithm(Algorithm.Ed25519))
        assertTrue(provider.supportsAlgorithm(Algorithm.Secp256k1))
        assertTrue(provider.supportsAlgorithm(Algorithm.P256))
        assertFalse(provider.supportsAlgorithm(Algorithm.BLS12_381))
    }

    @Test
    fun `test provider create with options`() {
        val provider = IbmKeyManagementServiceProvider()
        
        val kms = provider.create(mapOf(
            "apiKey" to "test-key",
            "instanceId" to "test-instance"
        ))
        
        assertNotNull(kms)
        assertTrue(kms is IbmKeyManagementService)
    }

    @Test
    fun `test provider create without required fields throws exception`() {
        val provider = IbmKeyManagementServiceProvider()
        
        assertThrows<IllegalArgumentException> {
            provider.create(emptyMap())
        }
    }
}

