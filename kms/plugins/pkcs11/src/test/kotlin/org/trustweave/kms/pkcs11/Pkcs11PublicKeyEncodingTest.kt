package org.trustweave.kms.pkcs11

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.kms.Algorithm
import org.trustweave.kms.JwkKeyTypes
import org.trustweave.kms.JwkKeys
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class Pkcs11PublicKeyEncodingTest {
    @Test
    fun `encodes Ed25519 public key to JWK and multibase`() {
        val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val jwk = Pkcs11KeyManagementService.publicKeyToJwk(kp.public, Algorithm.Ed25519)
        assertNotNull(jwk)
        assertEquals(JwkKeyTypes.OKP, jwk!![JwkKeys.KTY])
        assertEquals("Ed25519", jwk[JwkKeys.CRV])
        assertNotNull(jwk[JwkKeys.X])

        val multibase = Pkcs11KeyManagementService.publicKeyToMultibase(kp.public, Algorithm.Ed25519)
        assertNotNull(multibase)
        assertTrue(multibase!!.startsWith("z6Mk"), "Ed25519 multibase should start with z6Mk, got $multibase")
    }

    @Test
    fun `encodes EC P-256 P-384 P-521 public keys to JWK and multibase`() {
        val cases =
            listOf(
                Triple("secp256r1", Algorithm.P256, "P-256"),
                Triple("secp384r1", Algorithm.P384, "P-384"),
                Triple("secp521r1", Algorithm.P521, "P-521"),
            )
        for ((curve, alg, jwkCrv) in cases) {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec(curve))
            val kp = kpg.generateKeyPair()

            val jwk = Pkcs11KeyManagementService.publicKeyToJwk(kp.public)
            assertNotNull(jwk)
            assertEquals(JwkKeyTypes.EC, jwk!![JwkKeys.KTY])
            assertEquals(jwkCrv, jwk[JwkKeys.CRV])
            assertNotNull(jwk[JwkKeys.X])
            assertNotNull(jwk[JwkKeys.Y])

            val multibase = Pkcs11KeyManagementService.publicKeyToMultibase(kp.public, alg)
            assertNotNull(multibase)
            assertTrue(multibase!!.startsWith("z"), "EC multibase should start with z, got $multibase")
        }
    }

    @Test
    fun `encodes RSA-2048 public key to JWK and multibase`() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        val jwk = Pkcs11KeyManagementService.publicKeyToJwk(kp.public)
        assertNotNull(jwk)
        assertEquals(JwkKeyTypes.RSA, jwk!![JwkKeys.KTY])
        assertNotNull(jwk[JwkKeys.N])
        assertNotNull(jwk[JwkKeys.E])

        val multibase = Pkcs11KeyManagementService.publicKeyToMultibase(kp.public)
        assertNotNull(multibase)
        assertTrue(multibase!!.startsWith("z"), "RSA multibase should start with z, got $multibase")
    }

    @Test
    fun `validateInlineConfigValue rejects CR LF and NUL`() {
        assertThrows<Pkcs11Exception> {
            Pkcs11KeyManagementService.validateInlineConfigValue("libraryPath", "/lib/a.so\nslot=2")
        }
        assertThrows<Pkcs11Exception> {
            Pkcs11KeyManagementService.validateInlineConfigValue("providerName", "bad\rname")
        }
        assertThrows<Pkcs11Exception> {
            Pkcs11KeyManagementService.validateInlineConfigValue("providerName", "bad\u0000name")
        }
    }

    @Test
    fun `Pkcs11KeyManagementService implements AutoCloseable`() {
        assertTrue(AutoCloseable::class.java.isAssignableFrom(Pkcs11KeyManagementService::class.java))
    }
}
