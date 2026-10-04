package org.trustweave.signatures.xades

import org.trustweave.core.identifiers.KeyId
import org.trustweave.signatures.trustlists.TrustAnchorMatch
import org.trustweave.signatures.trustlists.TrustAnchorResolver
import java.security.cert.X509Certificate
import kotlin.time.Instant

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

    /**
     * XAdES-B-T: B-B plus an RFC 3161 `SignatureTimeStamp` over the `ds:SignatureValue`.
     * Verification only; [XadesSigner] cannot produce it.
     */
    B_T,
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
 *                                                 enforced at the claimed signing time. When
 *                                                 `false` (default) the validity window is checked
 *                                                 against the signed `SigningTime`, or against the
 *                                                 current time when the producer omitted it.
 * @property requireSigningTime                    When `true`, a signature whose signed properties
 *                                                 carry no `SigningTime` is rejected as
 *                                                 [XadesValidationResult.Invalid.Malformed]. When
 *                                                 `false` (default) it is accepted and certificate
 *                                                 validity is judged at verification time, which
 *                                                 cannot show the certificate was valid when the
 *                                                 document was actually signed.
 * @property requireSignatureTimestamp             When `true`, the signature must carry a valid RFC 3161
 *                                                 `SignatureTimeStamp` (B-T) issued by one of
 *                                                 [timestampTrustAnchors]; otherwise it is rejected
 *                                                 with [XadesValidationResult.Invalid.TimeStampInvalid].
 *                                                 Default `false`. Without a timestamp the
 *                                                 `SigningTime` is only the signer's own claim.
 * @property timestampTrustAnchors                 TSA certificates (or the CAs that issued them) the
 *                                                 caller trusts to time-stamp. A time-stamp whose TSA
 *                                                 certificate is neither one of these nor signed by
 *                                                 one is invalid. Empty (default) means no time-stamp
 *                                                 can be trusted: it is ignored, or, when
 *                                                 [requireSignatureTimestamp] is set, rejected.
 * @property allowWithdrawnTrustWithoutAuthenticatedTime
 *                                                 A signer whose trust-list service is
 *                                                 `QualifiedWithdrawn` is accepted only when an
 *                                                 authenticated (time-stamped) signing time precedes
 *                                                 the withdrawal. Set this to `true` to also accept it
 *                                                 when the signing time is merely claimed or absent
 *                                                 (a claimed time at or after the withdrawal is still
 *                                                 refused). Default `false`.
 * @property maxClockSkewSeconds                   Tolerance between the claimed `SigningTime` and the
 *                                                 time-stamp's `genTime`.
 */
data class XadesVerificationOptions
    @JvmOverloads
    constructor(
        val requiredProfile: XadesProfile,
        val trustAnchorResolver: TrustAnchorResolver,
        val allowExpiredCertificateAtSigningTime: Boolean = false,
        val requireSigningTime: Boolean = false,
        val requireSignatureTimestamp: Boolean = false,
        val timestampTrustAnchors: List<X509Certificate> = emptyList(),
        val allowWithdrawnTrustWithoutAuthenticatedTime: Boolean = false,
        val maxClockSkewSeconds: Long = 300,
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
     * @property signingTimeAuthenticated `true` only when a valid, trusted RFC 3161
     *                        `SignatureTimeStamp` backs the signing time. When `false`,
     *                        [signingTime] is merely what the signer claimed and proves nothing
     *                        about when the document was signed.
     * @property signatureTimeStamp The time-stamp's `genTime` when one was validated, else null.
     * @property revocationChecked Always `false` at present: this verifier does not evaluate CRL /
     *                        OCSP data, so certificate revocation is NOT checked. Callers needing
     *                        revocation status must check it themselves.
     */
    data class Valid
        @JvmOverloads
        constructor(
            val signerCert: X509Certificate,
            val trust: TrustAnchorMatch,
            val signingTime: Instant?,
            val profile: XadesProfile,
            val signingTimeAuthenticated: Boolean = false,
            val signatureTimeStamp: Instant? = null,
            val revocationChecked: Boolean = false,
        ) : XadesValidationResult()

    sealed class Invalid : XadesValidationResult() {
        /** XML-DSig signature value did not verify. */
        data class BadSignature(
            val reason: String,
        ) : Invalid()

        /** Signer cert chain did not anchor to a trusted CA/QC service. */
        data class UntrustedSigner(
            val cert: X509Certificate,
        ) : Invalid()

        /** Required profile differs from the profile actually present on the wire. */
        data class WrongProfile(
            val found: XadesProfile,
            val required: XadesProfile,
        ) : Invalid()

        /** Signer certificate had already expired at the asserted signing time. */
        data class CertificateExpired(
            val notAfter: Instant,
        ) : Invalid()

        /**
         * Signer certificate was not yet valid (`notBefore` in the future) at the asserted
         * signing time — or, when the producer omitted `SigningTime`, at verification time.
         */
        data class CertificateNotYetValid(
            val notBefore: Instant,
        ) : Invalid()

        /**
         * The signer's trust-list service was withdrawn at (or the signature cannot be shown to
         * predate) [withdrawnAt]; see [XadesVerificationOptions.allowWithdrawnTrustWithoutAuthenticatedTime].
         */
        data class TrustWithdrawn(
            val cert: X509Certificate,
            val withdrawnAt: Instant,
            val reason: String,
        ) : Invalid()

        /**
         * The certificate chain failed validation: a CA certificate lacks `basicConstraints` CA /
         * `keyCertSign`, a `pathLenConstraint` is exceeded, a certificate is outside its validity
         * window, or the signer certificate's `keyUsage` forbids signing.
         */
        data class CertificateChainInvalid(
            val reason: String,
        ) : Invalid()

        /**
         * A time-stamp was required ([XadesVerificationOptions.requireSignatureTimestamp] or
         * [XadesProfile.B_T]) but is absent, untrusted, malformed or does not match the signature.
         */
        data class TimeStampInvalid(
            val reason: String,
        ) : Invalid()

        /**
         * Input was not well-formed XML, did not contain a `<ds:Signature>` element, or was
         * missing one of the XAdES baseline qualifying properties.
         */
        data class Malformed(
            val reason: String,
        ) : Invalid()
    }
}
