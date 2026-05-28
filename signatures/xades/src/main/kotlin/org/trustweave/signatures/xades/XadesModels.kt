package org.trustweave.signatures.xades

import kotlinx.datetime.Instant
import org.trustweave.core.identifiers.KeyId
import org.trustweave.signatures.trustlists.TrustAnchorMatch
import org.trustweave.signatures.trustlists.TrustAnchorResolver
import java.security.cert.X509Certificate

/**
 * XAdES baseline profile (ETSI EN 319 132-1 §5).
 *
 * MVP scope: B-B only. The B-T / B-LT / B-LTA profiles are out of MVP scope per
 * [docs/architecture/eidas-qes-design.md](../../../../../../docs/architecture/eidas-qes-design.md) §13;
 * adding them requires wiring an RFC 3161 TSA into the XAdES `SignatureTimeStamp` element and
 * encoding the signed-properties block according to ETSI EN 319 132-1 §5.2.
 */
enum class XadesProfile {
    /** Basic XAdES — XML-DSig signature with the XAdES `SignedProperties` reference. */
    B_B,
}

/**
 * Input to [XadesSigner.sign].
 *
 * MVP scope: **enveloped** signatures only — the `<ds:Signature>` element is appended inside the
 * supplied document root. Detached and enveloping signatures are deferred per the eIDAS-QES design
 * doc §13; see the TODO markers in [XadesSigner] and [XadesVerifier].
 *
 * @property profile                Target XAdES profile (only [XadesProfile.B_B] in MVP).
 * @property keyId                  Identifier of the signing key in the configured
 *                                  [org.trustweave.kms.KeyManagementService].
 * @property document               The XML document to sign, as a fully parsed [org.w3c.dom.Document].
 *                                  The signer will append the produced `<ds:Signature>` element to
 *                                  the document root.
 * @property signerCertificateChain DER-encoded X.509 certificates, signer first. Required to build
 *                                  the XAdES `SigningCertificateV2` qualifying property and the
 *                                  XML-DSig `<KeyInfo>/<X509Data>`.
 * @property signingTime            Optional claimed signing time placed in the XAdES `SigningTime`
 *                                  qualifying property. Defaults to `Clock.System.now()`.
 */
data class XadesSigningRequest(
    val profile: XadesProfile,
    val keyId: KeyId,
    val document: org.w3c.dom.Document,
    val signerCertificateChain: List<ByteArray>,
    val signingTime: Instant? = null,
) {
    init {
        require(signerCertificateChain.isNotEmpty()) {
            "signerCertificateChain must include at least the signer's certificate"
        }
    }
}

/**
 * A produced XAdES signature.
 *
 * @property document  The document tree with the appended `<ds:Signature>` element. Re-serialise
 *                     with any standard XML transformer to obtain the wire form.
 * @property profile   Profile actually produced.
 */
data class XadesSignature(
    val document: org.w3c.dom.Document,
    val profile: XadesProfile,
)

/**
 * Verification policy supplied to [XadesVerifier.verify].
 *
 * @property requiredProfile                       Minimum XAdES profile to accept.
 * @property trustAnchorResolver                   Resolves the signer-cert chain against the
 *                                                 caller-supplied trust graph.
 * @property allowExpiredCertificateAtSigningTime  When `true`, certificate validity is not
 *                                                 enforced at the claimed signing time.
 */
data class XadesVerificationOptions(
    val requiredProfile: XadesProfile,
    val trustAnchorResolver: TrustAnchorResolver,
    val allowExpiredCertificateAtSigningTime: Boolean = false,
)

/**
 * Outcome of [XadesVerifier.verify].
 *
 * Mirrors the JAdES / CAdES result-tree layout. The MVP only ships the B-B failure modes.
 */
sealed class XadesValidationResult {

    /**
     * @property signerCert   The certificate that produced the signature.
     * @property trust        Trust-graph match returned by the [TrustAnchorResolver].
     * @property signingTime  Asserted signing time (XAdES `SigningTime` qualifying property), or
     *                        null when the producer omitted it.
     * @property profile      Profile actually detected on the wire.
     */
    data class Valid(
        val signerCert: X509Certificate,
        val trust: TrustAnchorMatch,
        val signingTime: Instant?,
        val profile: XadesProfile,
    ) : XadesValidationResult()

    sealed class Invalid : XadesValidationResult() {
        /** XML-DSig signature value did not verify. */
        data class BadSignature(val reason: String) : Invalid()

        /** Signer cert chain did not anchor to a trusted CA/QC service. */
        data class UntrustedSigner(val cert: X509Certificate) : Invalid()

        /** Required profile differs from the profile actually present on the wire. */
        data class WrongProfile(val found: XadesProfile, val required: XadesProfile) : Invalid()

        /** Signer certificate had already expired at the asserted signing time. */
        data class CertificateExpired(val notAfter: Instant) : Invalid()

        /**
         * Input was not well-formed XML, did not contain a `<ds:Signature>` element, or was
         * missing one of the XAdES baseline qualifying properties.
         */
        data class Malformed(val reason: String) : Invalid()
    }
}
