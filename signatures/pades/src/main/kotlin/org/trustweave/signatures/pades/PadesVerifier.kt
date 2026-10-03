package org.trustweave.signatures.pades

/**
 * Verifies a PAdES-signed PDF document.
 *
 * Like [PadesSigner], the MVP only ships an interface and a not-implemented placeholder;
 * a working implementation depends on Apache PDFBox. See [NotImplementedPadesSigner] for the
 * rationale and roadmap.
 */
interface PadesVerifier {
    /**
     * Verify [signedPdfBytes].
     *
     * @throws UnsupportedOperationException always when called on [NotImplementedPadesVerifier].
     */
    suspend fun verify(signedPdfBytes: ByteArray, options: PadesVerificationOptions): PadesValidationResult
}

/**
 * Placeholder [PadesVerifier] that throws [UnsupportedOperationException] on every call.
 */
class NotImplementedPadesVerifier : PadesVerifier {
    override suspend fun verify(
        signedPdfBytes: ByteArray,
        options: PadesVerificationOptions,
    ): PadesValidationResult {
        throw UnsupportedOperationException(
            "PAdES is not implemented in TrustWeave: it requires the optional Apache PDFBox dependency " +
                "and the planned signatures:pades-pdfbox module — " +
                "see docs/architecture/eidas-qes-design.md §12",
        )
    }
}
