package com.geoknoesis.vericore.googlekms

import com.geoknoesis.vericore.kms.Algorithm
import com.google.cloud.kms.v1.CryptoKeyVersion.CryptoKeyVersionAlgorithm
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AlgorithmMappingTest {
    
    @Test
    fun `test Ed25519 algorithm mapping`() {
        val vericoreAlg = Algorithm.Ed25519
        val googleKmsAlg = AlgorithmMapping.toGoogleKmsAlgorithm(vericoreAlg)
        assertEquals(CryptoKeyVersionAlgorithm.EC_SIGN_ED25519, googleKmsAlg)
        
        val backToVericore = AlgorithmMapping.fromGoogleKmsAlgorithm(googleKmsAlg)
        assertEquals(vericoreAlg, backToVericore)
    }
    
    @Test
    fun `test Secp256k1 algorithm mapping`() {
        val vericoreAlg = Algorithm.Secp256k1
        val googleKmsAlg = AlgorithmMapping.toGoogleKmsAlgorithm(vericoreAlg)
        assertEquals(CryptoKeyVersionAlgorithm.EC_SIGN_SECP256K1_SHA256, googleKmsAlg)
        
        val backToVericore = AlgorithmMapping.fromGoogleKmsAlgorithm(googleKmsAlg)
        assertEquals(vericoreAlg, backToVericore)
    }
    
    @Test
    fun `test P256 algorithm mapping`() {
        val vericoreAlg = Algorithm.P256
        val googleKmsAlg = AlgorithmMapping.toGoogleKmsAlgorithm(vericoreAlg)
        assertEquals(CryptoKeyVersionAlgorithm.EC_SIGN_P256_SHA256, googleKmsAlg)
        
        val backToVericore = AlgorithmMapping.fromGoogleKmsAlgorithm(googleKmsAlg)
        assertEquals(vericoreAlg, backToVericore)
    }
    
    @Test
    fun `test P384 algorithm mapping`() {
        val vericoreAlg = Algorithm.P384
        val googleKmsAlg = AlgorithmMapping.toGoogleKmsAlgorithm(vericoreAlg)
        assertEquals(CryptoKeyVersionAlgorithm.EC_SIGN_P384_SHA384, googleKmsAlg)
        
        val backToVericore = AlgorithmMapping.fromGoogleKmsAlgorithm(googleKmsAlg)
        assertEquals(vericoreAlg, backToVericore)
    }
    
    @Test
    fun `test P521 algorithm mapping`() {
        val vericoreAlg = Algorithm.P521
        val googleKmsAlg = AlgorithmMapping.toGoogleKmsAlgorithm(vericoreAlg)
        assertEquals(CryptoKeyVersionAlgorithm.EC_SIGN_P521_SHA512, googleKmsAlg)
        
        val backToVericore = AlgorithmMapping.fromGoogleKmsAlgorithm(googleKmsAlg)
        assertEquals(vericoreAlg, backToVericore)
    }
    
    @Test
    fun `test RSA 2048 algorithm mapping`() {
        val vericoreAlg = Algorithm.RSA.RSA_2048
        val googleKmsAlg = AlgorithmMapping.toGoogleKmsAlgorithm(vericoreAlg)
        assertEquals(CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_2048_SHA256, googleKmsAlg)
        
        val backToVericore = AlgorithmMapping.fromGoogleKmsAlgorithm(googleKmsAlg)
        assertEquals(vericoreAlg, backToVericore)
    }
    
    @Test
    fun `test RSA 3072 algorithm mapping`() {
        val vericoreAlg = Algorithm.RSA.RSA_3072
        val googleKmsAlg = AlgorithmMapping.toGoogleKmsAlgorithm(vericoreAlg)
        assertEquals(CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_3072_SHA256, googleKmsAlg)
        
        val backToVericore = AlgorithmMapping.fromGoogleKmsAlgorithm(googleKmsAlg)
        assertEquals(vericoreAlg, backToVericore)
    }
    
    @Test
    fun `test RSA 4096 algorithm mapping`() {
        val vericoreAlg = Algorithm.RSA.RSA_4096
        val googleKmsAlg = AlgorithmMapping.toGoogleKmsAlgorithm(vericoreAlg)
        assertEquals(CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_4096_SHA256, googleKmsAlg)
        
        val backToVericore = AlgorithmMapping.fromGoogleKmsAlgorithm(googleKmsAlg)
        assertEquals(vericoreAlg, backToVericore)
    }
    
    @Test
    fun `test resolveKeyName with full resource name`() {
        val config = GoogleKmsConfig.builder()
            .projectId("test-project")
            .location("us-east1")
            .keyRing("test-key-ring")
            .build()
        
        val fullName = "projects/test-project/locations/us-east1/keyRings/test-key-ring/cryptoKeys/my-key"
        val resolved = AlgorithmMapping.resolveKeyName(fullName, config)
        assertEquals(fullName, resolved)
    }
    
    @Test
    fun `test resolveKeyName with short name`() {
        val config = GoogleKmsConfig.builder()
            .projectId("test-project")
            .location("us-east1")
            .keyRing("test-key-ring")
            .build()
        
        val shortName = "my-key"
        val resolved = AlgorithmMapping.resolveKeyName(shortName, config)
        assertEquals("projects/test-project/locations/us-east1/keyRings/test-key-ring/cryptoKeys/my-key", resolved)
    }
    
    @Test
    fun `test resolveKeyName fails without key ring`() {
        val config = GoogleKmsConfig.builder()
            .projectId("test-project")
            .location("us-east1")
            .build()
        
        assertThrows<IllegalArgumentException> {
            AlgorithmMapping.resolveKeyName("my-key", config)
        }
    }
    
    @Test
    fun `test extractKeyName from full resource name`() {
        val fullName = "projects/test-project/locations/us-east1/keyRings/test-key-ring/cryptoKeys/my-key"
        val extracted = AlgorithmMapping.extractKeyName(fullName)
        assertEquals("my-key", extracted)
    }
    
    @Test
    fun `test extractKeyName from short name`() {
        val shortName = "my-key"
        val extracted = AlgorithmMapping.extractKeyName(shortName)
        assertEquals("my-key", extracted)
    }
}

