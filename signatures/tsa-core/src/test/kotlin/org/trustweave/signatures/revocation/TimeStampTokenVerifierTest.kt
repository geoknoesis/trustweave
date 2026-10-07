package org.trustweave.signatures.revocation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.trustweave.signatures.revocation.TimeStampTokenVerifier.Result
import java.security.MessageDigest
import java.util.Date

class TimeStampTokenVerifierTest {
    private val digest = MessageDigest.getInstance("SHA-256").digest("signature".toByteArray())

    @Test
    fun `a token from a configured TSA verifies and exposes its imprint`() {
        val tsa = TestTsa()
        val result = TimeStampTokenVerifier.verify(tsa.token(digest), listOf(tsa.cert))
        assertTrue(result is Result.Valid, "got ${(result as? Result.Invalid)?.reason}")
        result as Result.Valid
        assertEquals("2.16.840.1.101.3.4.2.1", result.imprintOid)
        assertTrue(MessageDigest.isEqual(digest, result.imprintDigest))
    }

    @Test
    fun `a token from a TSA that is not an anchor is rejected`() {
        val result = TimeStampTokenVerifier.verify(TestTsa().token(digest), listOf(TestTsa().cert))
        assertTrue(result is Result.Invalid && result.reason.contains("anchor"), "got $result")
    }

    @Test
    fun `no anchors means nothing can be trusted`() {
        assertTrue(TimeStampTokenVerifier.verify(TestTsa().token(digest), emptyList()) is Result.Invalid)
    }

    @Test
    fun `a TSA certificate that was not valid at genTime is rejected`() {
        val tsa = TestTsa(notBefore = TestCa.hours(-24 * 10), notAfter = TestCa.hours(-24 * 5))
        val result = TimeStampTokenVerifier.verify(tsa.token(digest, genTime = Date()), listOf(tsa.cert))
        assertTrue(result is Result.Invalid && result.reason.contains("not valid"), "got $result")
    }

    @Test
    fun `a TSA certificate issued by an anchor CA is accepted`() {
        // The anchor is the CA that issued the TSA certificate rather than the TSA certificate itself.
        val ca = TestCa("CN=TSA Issuing CA")
        val tsaKey = TestCa.leafKey()
        val tsaCert = ca.issue("CN=Issued TSA", tsaKey.public)
        assertTrue(CertificatePaths.signedBy(tsaCert, ca.cert))
        assertTrue(TimeStampTokenVerifier.digestFor("2.16.840.1.101.3.4.2.2") == "SHA-384")
    }

    @Test
    fun `garbage is not a token`() {
        assertTrue(TimeStampTokenVerifier.verify(ByteArray(32), listOf(TestTsa().cert)) is Result.Invalid)
    }
}
