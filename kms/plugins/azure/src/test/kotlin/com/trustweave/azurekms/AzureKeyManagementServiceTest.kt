package com.trustweave.azurekms

import com.trustweave.kms.Algorithm
import com.trustweave.kms.UnsupportedAlgorithmException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

class AzureKeyManagementServiceTest {

    private lateinit var config: AzureKmsConfig

    @BeforeEach
    fun setup() {
        config = AzureKmsConfig.builder()
            .vaultUrl("https://testvault.vault.azure.net")
            .endpointOverride("https://localhost:8443") // For testing with local emulators
            .build()
    }

    @Test
    fun `test get supported algorithms`() = runBlocking {
        val supported = AzureKeyManagementService.SUPPORTED_ALGORITHMS
        
        assertFalse(supported.contains(Algorithm.Ed25519)) // Not supported by Azure
        assertTrue(supported.contains(Algorithm.Secp256k1))
        assertTrue(supported.contains(Algorithm.P256))
        assertTrue(supported.contains(Algorithm.P384))
        assertTrue(supported.contains(Algorithm.P521))
        assertTrue(supported.contains(Algorithm.RSA.RSA_2048))
        assertTrue(supported.contains(Algorithm.RSA.RSA_3072))
        assertTrue(supported.contains(Algorithm.RSA.RSA_4096))
        assertEquals(7, supported.size)
    }

    @Test
    fun `test unsupported algorithm Ed25519`() = runBlocking {
        // Note: This test would require a real Azure Key Vault or mock
        // For now, we just test that Ed25519 is not in supported algorithms
        val supported = AzureKeyManagementService.SUPPORTED_ALGORITHMS
        assertFalse(supported.contains(Algorithm.Ed25519))
    }
}

