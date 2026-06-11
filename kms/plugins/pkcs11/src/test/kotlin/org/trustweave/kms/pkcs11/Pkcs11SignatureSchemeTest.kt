package org.trustweave.kms.pkcs11

import org.junit.jupiter.api.Test
import org.trustweave.kms.Algorithm
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Mapping-level unit tests for [Pkcs11KeyManagementService.signatureSchemeFor].
 *
 * These run without an HSM: they pin the algorithm -> JCA-scheme table so that
 *  - each NIST curve uses the hash matching its strength (a P-384/P-521 key signed with
 *    SHA-256 truncates security and diverges from every other plugin), and
 *  - RSA uses PKCS#1 v1.5 in BOTH the requested-algorithm path and the key-type fallback
 *    path (previously the requested path used RSA-PSS while other call sites used v1.5,
 *    producing signatures that could not be verified consistently).
 */
class Pkcs11SignatureSchemeTest {

    @Test
    fun `EC curves map to the hash matching the curve strength`() {
        assertEquals("SHA256withECDSA", Pkcs11KeyManagementService.signatureSchemeFor(Algorithm.P256, "EC"))
        assertEquals("SHA384withECDSA", Pkcs11KeyManagementService.signatureSchemeFor(Algorithm.P384, "EC"))
        assertEquals("SHA512withECDSA", Pkcs11KeyManagementService.signatureSchemeFor(Algorithm.P521, "EC"))
    }

    @Test
    fun `RSA maps to PKCS1 v1_5 with hash sized to the key`() {
        assertEquals(
            "SHA256withRSA",
            Pkcs11KeyManagementService.signatureSchemeFor(Algorithm.RSA.RSA_2048, "RSA")
        )
        assertEquals(
            "SHA384withRSA",
            Pkcs11KeyManagementService.signatureSchemeFor(Algorithm.RSA.RSA_3072, "RSA")
        )
        assertEquals(
            "SHA512withRSA",
            Pkcs11KeyManagementService.signatureSchemeFor(Algorithm.RSA.RSA_4096, "RSA")
        )
    }

    @Test
    fun `Ed25519 maps to Ed25519`() {
        assertEquals("Ed25519", Pkcs11KeyManagementService.signatureSchemeFor(Algorithm.Ed25519, "Ed25519"))
    }

    @Test
    fun `unsupported algorithms map to null`() {
        assertNull(Pkcs11KeyManagementService.signatureSchemeFor(Algorithm.Secp256k1, "EC"))
        assertNull(Pkcs11KeyManagementService.signatureSchemeFor(Algorithm.BLS12_381, "EC"))
        assertNull(Pkcs11KeyManagementService.signatureSchemeFor(Algorithm.Custom("X-curve"), "EC"))
    }

    @Test
    fun `key-type fallback uses deterministic SHA-256 schemes with PKCS1 v1_5 for RSA`() {
        assertEquals("Ed25519", Pkcs11KeyManagementService.signatureSchemeFor(null, "Ed25519"))
        assertEquals("Ed25519", Pkcs11KeyManagementService.signatureSchemeFor(null, "EdDSA"))
        assertEquals("SHA256withECDSA", Pkcs11KeyManagementService.signatureSchemeFor(null, "EC"))
        assertEquals("SHA256withRSA", Pkcs11KeyManagementService.signatureSchemeFor(null, "RSA"))
        assertNull(Pkcs11KeyManagementService.signatureSchemeFor(null, "DSA"))
    }
}
