package org.trustweave.azurekms

import org.trustweave.kms.Algorithm
import com.azure.security.keyvault.keys.models.KeyType
import com.azure.security.keyvault.keys.models.KeyCurveName
import com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class AlgorithmMappingTest {

    @Test
    fun `test algorithm mapping to Azure key type`() {
        val (keyType1, curveName1) = AlgorithmMapping.toAzureKeyType(Algorithm.Secp256k1)
        assertEquals(KeyType.EC, keyType1)
        assertEquals(KeyCurveName.P_256K, curveName1)

        val (keyType2, curveName2) = AlgorithmMapping.toAzureKeyType(Algorithm.P256)
        assertEquals(KeyType.EC, keyType2)
        assertEquals(KeyCurveName.P_256, curveName2)

        val (keyType3, curveName3) = AlgorithmMapping.toAzureKeyType(Algorithm.P384)
        assertEquals(KeyType.EC, keyType3)
        assertEquals(KeyCurveName.P_384, curveName3)

        val (keyType4, curveName4) = AlgorithmMapping.toAzureKeyType(Algorithm.P521)
        assertEquals(KeyType.EC, keyType4)
        assertEquals(KeyCurveName.P_521, curveName4)

        val (keyType5, curveName5) = AlgorithmMapping.toAzureKeyType(Algorithm.RSA.RSA_2048)
        assertEquals(KeyType.RSA, keyType5)
        assertNull(curveName5)
    }

    @Test
    fun `test Ed25519 not supported throws exception`() {
        assertThrows<IllegalArgumentException> {
            AlgorithmMapping.toAzureKeyType(Algorithm.Ed25519)
        }
    }

    @Test
    fun `test algorithm mapping to Azure signature algorithm`() {
        assertEquals(SignatureAlgorithm.ES256K, AlgorithmMapping.toAzureSignatureAlgorithm(Algorithm.Secp256k1))
        assertEquals(SignatureAlgorithm.ES256, AlgorithmMapping.toAzureSignatureAlgorithm(Algorithm.P256))
        assertEquals(SignatureAlgorithm.ES384, AlgorithmMapping.toAzureSignatureAlgorithm(Algorithm.P384))
        assertEquals(SignatureAlgorithm.ES512, AlgorithmMapping.toAzureSignatureAlgorithm(Algorithm.P521))
        assertEquals(SignatureAlgorithm.RS256, AlgorithmMapping.toAzureSignatureAlgorithm(Algorithm.RSA.RSA_2048))
        assertEquals(SignatureAlgorithm.RS256, AlgorithmMapping.toAzureSignatureAlgorithm(Algorithm.RSA.RSA_3072))
        assertEquals(SignatureAlgorithm.RS256, AlgorithmMapping.toAzureSignatureAlgorithm(Algorithm.RSA.RSA_4096))
    }

    @Test
    fun `test Ed25519 signature algorithm throws exception`() {
        assertThrows<IllegalArgumentException> {
            AlgorithmMapping.toAzureSignatureAlgorithm(Algorithm.Ed25519)
        }
    }

    @Test
    fun `test resolve key ID`() {
        assertEquals("mykey", AlgorithmMapping.resolveKeyId("mykey"))
        assertEquals("mykey", AlgorithmMapping.resolveKeyId("mykey/abc123def456"))
        assertEquals("mykey", AlgorithmMapping.resolveKeyId("https://myvault.vault.azure.net/keys/mykey"))
        assertEquals("mykey/version123", AlgorithmMapping.resolveKeyId("https://myvault.vault.azure.net/keys/mykey/version123"))
    }

    @Test
    fun `test parse algorithm from key type`() {
        assertEquals(Algorithm.Secp256k1, AlgorithmMapping.parseAlgorithmFromKeyType(KeyType.EC, KeyCurveName.P_256K, null))
        assertEquals(Algorithm.P256, AlgorithmMapping.parseAlgorithmFromKeyType(KeyType.EC, KeyCurveName.P_256, null))
        assertEquals(Algorithm.P384, AlgorithmMapping.parseAlgorithmFromKeyType(KeyType.EC, KeyCurveName.P_384, null))
        assertEquals(Algorithm.P521, AlgorithmMapping.parseAlgorithmFromKeyType(KeyType.EC, KeyCurveName.P_521, null))
        assertEquals(Algorithm.RSA.RSA_2048, AlgorithmMapping.parseAlgorithmFromKeyType(KeyType.RSA, null, 2048))
        assertEquals(Algorithm.RSA.RSA_3072, AlgorithmMapping.parseAlgorithmFromKeyType(KeyType.RSA, null, 3072))
        assertEquals(Algorithm.RSA.RSA_4096, AlgorithmMapping.parseAlgorithmFromKeyType(KeyType.RSA, null, 4096))
    }

    @Test
    fun `test parse algorithm from key type returns null for unsupported`() {
        assertNull(AlgorithmMapping.parseAlgorithmFromKeyType(KeyType.EC, KeyCurveName.P_256K, 2048)) // Invalid combination
        assertNull(AlgorithmMapping.parseAlgorithmFromKeyType(KeyType.RSA, null, 1024)) // Unsupported key size
    }
}

