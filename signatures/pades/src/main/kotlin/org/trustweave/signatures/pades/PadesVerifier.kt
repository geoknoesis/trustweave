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
     * @throws NotImplementedError when called on [NotImplementedPadesVerifier].
     */
    suspend fun verify(signedPdfBytes: ByteArray, options: PadesVerificationOptions): PadesValidationResult
}

/**
 * Placeholder [PadesVerifier] that throws [NotImplementedError] on every call.
 */
class NotImplementedPadesVerifier : PadesVerifier {
    override suspend fun verify(
        signedPdfBytes: ByteArray,
        options: PadesVerificationOptions,
    ): PadesValidationResult {
        throw NotImplementedError(
            "PAdES requires the optional Apache PDFBox dependency — " +
                "see docs/architecture/eidas-qes-design.md §12",
        )
    }
}
