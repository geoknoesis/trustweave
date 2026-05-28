package org.trustweave.signatures.pades

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId

class NotImplementedPadesTest {

    @Test
    fun `NotImplementedPadesSigner sign throws NotImplementedError`() {
        val signer = NotImplementedPadesSigner()
        val request = PadesSigningRequest(
            profile = PadesProfile.B_B,
            keyId = KeyId("test-key"),
            pdfBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46), // "%PDF"
            signerCertificateChain = listOf(byteArrayOf(0x00)),
        )
        val error = assertThrows(NotImplementedError::class.java) {
            runBlocking { signer.sign(request) }
        }
        assertTrue(
            error.message!!.contains("PAdES requires the optional Apache PDFBox dependency"),
            "expected message to mention PDFBox dependency; was '${error.message}'",
        )
    }

    @Test
    fun `NotImplementedPadesVerifier verify throws NotImplementedError`() {
        val verifier = NotImplementedPadesVerifier()
        val options = PadesVerificationOptions(requiredProfile = PadesProfile.B_B)
        val error = assertThrows(NotImplementedError::class.java) {
            runBlocking { verifier.verify(byteArrayOf(0x00), options) }
        }
        assertTrue(
            error.message!!.contains("PAdES requires the optional Apache PDFBox dependency"),
            "expected message to mention PDFBox dependency; was '${error.message}'",
        )
    }
}
