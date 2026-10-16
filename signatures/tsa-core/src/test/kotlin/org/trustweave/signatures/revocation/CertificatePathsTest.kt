package org.trustweave.signatures.revocation

import org.bouncycastle.asn1.x509.KeyUsage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CertificatePathsTest {
    private val root = TestCa("CN=Paths Root")
    private val intermediate = TestCa("CN=Paths Intermediate", parent = root)
    private val leaf = intermediate.issue("CN=Paths Leaf", TestCa.leafKey().public)

    @Test
    fun `walk orders the chain from the nearest issuer upwards whatever the input order`() {
        assertEquals(listOf(intermediate.cert, root.cert), CertificatePaths.walk(leaf, listOf(root.cert, intermediate.cert)))
        assertEquals(listOf(intermediate.cert, root.cert), CertificatePaths.walk(leaf, listOf(intermediate.cert, root.cert, leaf)))
    }

    @Test
    fun `walk ignores unrelated and duplicate certificates`() {
        val stray = TestCa("CN=Paths Stray").cert
        assertEquals(listOf(intermediate.cert), CertificatePaths.walk(leaf, listOf(stray, intermediate.cert, intermediate.cert)))
        assertEquals(emptyList<Any>(), CertificatePaths.walk(leaf, listOf(stray)))
    }

    @Test
    fun `walk does not follow a certificate that merely copies the issuer name`() {
        val impostor = TestCa("CN=Paths Intermediate").cert
        assertEquals(emptyList<Any>(), CertificatePaths.walk(leaf, listOf(impostor)))
    }

    @Test
    fun `self-signed means signed by its own key`() {
        assertTrue(CertificatePaths.isSelfSigned(root.cert))
        assertFalse(CertificatePaths.isSelfSigned(intermediate.cert))
        assertFalse(CertificatePaths.isSelfSigned(root.issue(root.subject, TestCa.leafKey().public, ca = true, usage = null)))
    }

    @Test
    fun `a CA or a certificate that cannot sign is not a signer`() {
        assertNull(CertificatePaths.signerProblem(leaf))
        assertNotNull(CertificatePaths.signerProblem(intermediate.cert))
        val encipherOnly = intermediate.issue("CN=Paths Encrypt", TestCa.leafKey().public, usage = KeyUsage.keyEncipherment)
        assertNotNull(CertificatePaths.signerProblem(encipherOnly))
        val noUsage = intermediate.issue("CN=Paths NoUsage", TestCa.leafKey().public, usage = null)
        assertNull(CertificatePaths.signerProblem(noUsage))
        val nonRepudiation = intermediate.issue("CN=Paths NR", TestCa.leafKey().public, usage = KeyUsage.nonRepudiation)
        assertNull(CertificatePaths.signerProblem(nonRepudiation))
    }
}
