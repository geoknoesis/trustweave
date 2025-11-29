package com.trustweave.hashicorpkms

import com.trustweave.kms.Algorithm
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class AlgorithmMappingTest {

    @Test
    fun `test to vault key type for all supported algorithms`() {
        assertEquals("ed25519", AlgorithmMapping.toVaultKeyType(Algorithm.Ed25519))
        assertEquals("ecdsa-p256k1", AlgorithmMapping.toVaultKeyType(Algorithm.Secp256k1))
        assertEquals("ecdsa-p256", AlgorithmMapping.toVaultKeyType(Algorithm.P256))
        assertEquals("ecdsa-p384", AlgorithmMapping.toVaultKeyType(Algorithm.P384))
        assertEquals("ecdsa-p521", AlgorithmMapping.toVaultKeyType(Algorithm.P521))
        assertEquals("rsa-2048", AlgorithmMapping.toVaultKeyType(Algorithm.RSA.RSA_2048))
        assertEquals("rsa-3072", AlgorithmMapping.toVaultKeyType(Algorithm.RSA.RSA_3072))
        assertEquals("rsa-4096", AlgorithmMapping.toVaultKeyType(Algorithm.RSA.RSA_4096))
    }

    @Test
    fun `test unsupported algorithm throws exception`() {
        assertThrows<IllegalArgumentException> {
            AlgorithmMapping.toVaultKeyType(Algorithm.BLS12_381)
        }
    }

    @Test
    fun `test from vault key type for all supported types`() {
        assertEquals(Algorithm.Ed25519, AlgorithmMapping.fromVaultKeyType("ed25519"))
        assertEquals(Algorithm.Secp256k1, AlgorithmMapping.fromVaultKeyType("ecdsa-p256k1"))
        assertEquals(Algorithm.P256, AlgorithmMapping.fromVaultKeyType("ecdsa-p256"))
        assertEquals(Algorithm.P384, AlgorithmMapping.fromVaultKeyType("ecdsa-p384"))
        assertEquals(Algorithm.P521, AlgorithmMapping.fromVaultKeyType("ecdsa-p521"))
        assertEquals(Algorithm.RSA.RSA_2048, AlgorithmMapping.fromVaultKeyType("rsa-2048"))
        assertEquals(Algorithm.RSA.RSA_3072, AlgorithmMapping.fromVaultKeyType("rsa-3072"))
        assertEquals(Algorithm.RSA.RSA_4096, AlgorithmMapping.fromVaultKeyType("rsa-4096"))
    }

    @Test
    fun `test from vault key type case insensitive`() {
        assertEquals(Algorithm.Ed25519, AlgorithmMapping.fromVaultKeyType("ED25519"))
        assertEquals(Algorithm.P256, AlgorithmMapping.fromVaultKeyType("ECDSA-P256"))
    }

    @Test
    fun `test from vault key type unknown returns null`() {
        assertNull(AlgorithmMapping.fromVaultKeyType("unknown-type"))
    }

    @Test
    fun `test to vault hash algorithm`() {
        assertEquals("sha2-256", AlgorithmMapping.toVaultHashAlgorithm(Algorithm.Ed25519))
        assertEquals("sha2-256", AlgorithmMapping.toVaultHashAlgorithm(Algorithm.Secp256k1))
        assertEquals("sha2-256", AlgorithmMapping.toVaultHashAlgorithm(Algorithm.P256))
        assertEquals("sha2-384", AlgorithmMapping.toVaultHashAlgorithm(Algorithm.P384))
        assertEquals("sha2-512", AlgorithmMapping.toVaultHashAlgorithm(Algorithm.P521))
        assertEquals("sha2-256", AlgorithmMapping.toVaultHashAlgorithm(Algorithm.RSA.RSA_2048))
    }

    @Test
    fun `test resolve key name`() {
        val config = VaultKmsConfig.builder()
            .address("http://localhost:8200")
            .transitPath("transit")
            .build()

        assertEquals("my-key", AlgorithmMapping.resolveKeyName("my-key", config))
        assertEquals("my-key", AlgorithmMapping.resolveKeyName("transit/keys/my-key", config))
        assertEquals("my-key", AlgorithmMapping.resolveKeyName("/transit/keys/my-key", config))
    }

    @Test
    fun `test resolve key name with custom transit path`() {
        val config = VaultKmsConfig.builder()
            .address("http://localhost:8200")
            .transitPath("custom-transit")
            .build()

        assertEquals("my-key", AlgorithmMapping.resolveKeyName("custom-transit/keys/my-key", config))
    }
}

