package com.geoknoesis.vericore.kms.ibm

import com.geoknoesis.vericore.kms.Algorithm
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class AlgorithmMappingTest {

    @Test
    fun `test to ibm key type for all supported algorithms`() {
        assertEquals("Ed25519", AlgorithmMapping.toIbmKeyType(Algorithm.Ed25519))
        assertEquals("secp256k1", AlgorithmMapping.toIbmKeyType(Algorithm.Secp256k1))
        assertEquals("EC:secp256r1", AlgorithmMapping.toIbmKeyType(Algorithm.P256))
        assertEquals("EC:secp384r1", AlgorithmMapping.toIbmKeyType(Algorithm.P384))
        assertEquals("EC:secp521r1", AlgorithmMapping.toIbmKeyType(Algorithm.P521))
        assertEquals("RSA:2048", AlgorithmMapping.toIbmKeyType(Algorithm.RSA.RSA_2048))
        assertEquals("RSA:3072", AlgorithmMapping.toIbmKeyType(Algorithm.RSA.RSA_3072))
        assertEquals("RSA:4096", AlgorithmMapping.toIbmKeyType(Algorithm.RSA.RSA_4096))
    }

    @Test
    fun `test unsupported algorithm throws exception`() {
        assertThrows<IllegalArgumentException> {
            AlgorithmMapping.toIbmKeyType(Algorithm.BLS12_381)
        }
    }

    @Test
    fun `test from ibm key type for all supported types`() {
        assertEquals(Algorithm.Ed25519, AlgorithmMapping.fromIbmKeyType("Ed25519"))
        assertEquals(Algorithm.Secp256k1, AlgorithmMapping.fromIbmKeyType("secp256k1"))
        assertEquals(Algorithm.P256, AlgorithmMapping.fromIbmKeyType("EC:secp256r1"))
        assertEquals(Algorithm.P256, AlgorithmMapping.fromIbmKeyType("EC-P256"))
        assertEquals(Algorithm.P384, AlgorithmMapping.fromIbmKeyType("EC:secp384r1"))
        assertEquals(Algorithm.P521, AlgorithmMapping.fromIbmKeyType("EC:secp521r1"))
        assertEquals(Algorithm.RSA.RSA_2048, AlgorithmMapping.fromIbmKeyType("RSA:2048"))
        assertEquals(Algorithm.RSA.RSA_3072, AlgorithmMapping.fromIbmKeyType("RSA-3072"))
        assertEquals(Algorithm.RSA.RSA_4096, AlgorithmMapping.fromIbmKeyType("RSA:4096"))
    }

    @Test
    fun `test from ibm key type case insensitive`() {
        assertEquals(Algorithm.Ed25519, AlgorithmMapping.fromIbmKeyType("ed25519"))
        assertEquals(Algorithm.P256, AlgorithmMapping.fromIbmKeyType("ec:secp256r1"))
    }

    @Test
    fun `test from ibm key type unknown returns null`() {
        assertNull(AlgorithmMapping.fromIbmKeyType("unknown-type"))
    }

    @Test
    fun `test to ibm signing algorithm`() {
        assertEquals("EdDSA", AlgorithmMapping.toIbmSigningAlgorithm(Algorithm.Ed25519))
        assertEquals("ECDSA", AlgorithmMapping.toIbmSigningAlgorithm(Algorithm.Secp256k1))
        assertEquals("ECDSA", AlgorithmMapping.toIbmSigningAlgorithm(Algorithm.P256))
        assertEquals("RSA_PKCS1_V1_5", AlgorithmMapping.toIbmSigningAlgorithm(Algorithm.RSA.RSA_2048))
    }

    @Test
    fun `test resolve key id`() {
        assertEquals("key-123", AlgorithmMapping.resolveKeyId("key-123"))
        assertEquals("crn:v1:bluemix:public:kms:us-south:a/xxx:key:xxx", 
            AlgorithmMapping.resolveKeyId("crn:v1:bluemix:public:kms:us-south:a/xxx:key:xxx"))
    }
}

