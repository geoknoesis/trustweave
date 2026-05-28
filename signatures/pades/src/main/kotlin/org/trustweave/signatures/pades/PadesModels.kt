package org.trustweave.signatures.pades

import kotlinx.datetime.Instant
import org.trustweave.core.identifiers.KeyId
import java.security.cert.X509Certificate

/**
 * PAdES baseline profile (ETSI EN 319 142-1 §5).
 *
 * Listed here for API parity; actual signature production is **not** implemented in MVP — see
 * [PadesSigner] for the rationale and the dependency PAdES requires.
 */
enum class PadesProfile {
    /** Basic PAdES — PDF "byte-range" signature with CAdES detached embedded in the PDF signature dictionary. */
    B_B,

    /** Basic + RFC 3161 sig-time-stamp embedded in the CAdES signature. */
    B_T,
}

/**
 * Input to [PadesSigner.sign].
 *
 * @property profile                Target PAdES profile.
 * @property keyId                  Identifier of the signing key in the configured
 *                                  [org.trustweave.kms.KeyManagementService].
 * @property pdfBytes               The PDF document to sign, as bytes.
 * @property signerCertificateChain DER-encoded X.509 certificates, signer first.
 * @property signingTime            Optional claimed signing time.
 * @property signatureReason        Free-form reason string placed in the PDF signature dictionary
 *                                  (`/Reason` entry). Defaults to null.
 * @property signatureLocation      Free-form location string placed in the PDF signature dictionary
 *                                  (`/Location` entry). Defaults to null.
 * @property signerName             Optional `/Name` entry — typically the signer's legal name.
 */
data class PadesSigningRequest(
    val profile: PadesProfile,
    val keyId: KeyId,
    val pdfBytes: ByteArray,
    val signerCertificateChain: List<ByteArray>,
    val signingTime: Instant? = null,
    val signatureReason: String? = null,
    val signatureLocation: String? = null,
    val signerName: String? = null,
)

/**
 * A produced PAdES signature.
 *
 * @property signedPdfBytes  The PDF with the embedded signature dictionary. Distribute as a normal
 *                           PDF document.
 * @property profile         Profile actually produced.
 */
data class PadesSignature(
    val signedPdfBytes: ByteArray,
    val profile: PadesProfile,
)

/**
 * Verification policy supplied to [PadesVerifier.verify].
 */
data class PadesVerificationOptions(
    val requiredProfile: PadesProfile,
    val allowExpiredCertificateAtSigningTime: Boolean = false,
)

/**
 * Outcome of [PadesVerifier.verify].
 */
sealed class PadesValidationResult {

    /**
     * @property signerCert   The certificate that produced the PAdES signature.
     * @property signingTime  Asserted signing time (PDF `/M` entry).
     * @property profile      Profile actually detected on the wire.
     */
    data class Valid(
        val signerCert: X509Certificate,
        val signingTime: Instant?,
        val profile: PadesProfile,
    ) : PadesValidationResult()

    sealed class Invalid : PadesValidationResult() {
        data class BadSignature(val reason: String) : Invalid()
        data class Malformed(val reason: String) : Invalid()
    }
}
