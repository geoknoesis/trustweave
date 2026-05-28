package org.trustweave.signatures.cades

import kotlinx.datetime.Instant
import org.trustweave.core.identifiers.KeyId
import org.trustweave.signatures.trustlists.TrustAnchorMatch
import org.trustweave.signatures.trustlists.TrustAnchorResolver
import org.trustweave.signatures.tsa.TsaConfig
import java.security.cert.X509Certificate
import kotlin.time.Duration

/**
 * CAdES baseline profile (ETSI EN 319 122-1 §5).
 *
 * MVP scope: B-B (basic signature) and B-T (basic + signature-time-stamp). The longer-term
 * profiles (B-LT, B-LTA) are out of MVP scope per
 * [docs/architecture/eidas-qes-design.md](../../../../../../docs/architecture/eidas-qes-design.md) §13.
 */
enum class CadesProfile {
    /** Basic CMS SignedData with mandatory signed attributes (content-type, message-digest, signing-time, signing-cert-v2). */
    B_B,

    /** Basic + at least one signature-time-stamp (`id-aa-signatureTimeStampToken`) unsigned attribute. */
    B_T,
}

/**
 * Input to [CadesSigner.sign].
 *
 * @property profile                Target CAdES profile.
 * @property keyId                  Identifier of the signing key in the configured
 *                                  [org.trustweave.kms.KeyManagementService].
 * @property payload                The arbitrary binary content to sign.
 * @property signerCertificateChain DER-encoded X.509 certificates, signer first. Required to
 *                                  build the CMS `SignerInfo` and the `signing-certificate-v2`
 *                                  signed attribute.
 * @property signingTime            Optional claimed signing time (CMS `signing-time` attribute).
 *                                  Defaults to `kotlinx.datetime.Clock.System.now()` when null.
 * @property detached               When `true`, the produced CMS does NOT embed the payload
 *                                  (detached signature — the standard CAdES form for large
 *                                  binary documents). When `false` the payload is encapsulated
 *                                  inside the CMS SignedData. Defaults to `true`.
 * @property tsaConfig              Required iff [profile] == [CadesProfile.B_T]. The signer will
 *                                  request a signature-time-stamp against this TSA.
 */
data class CadesSigningRequest(
    val profile: CadesProfile,
    val keyId: KeyId,
    val payload: ByteArray,
    val signerCertificateChain: List<ByteArray>,
    val signingTime: Instant? = null,
    val detached: Boolean = true,
    val tsaConfig: TsaConfig? = null,
) {
    init {
        require(signerCertificateChain.isNotEmpty()) {
            "signerCertificateChain must include at least the signer's certificate"
        }
        if (profile == CadesProfile.B_T) {
            require(tsaConfig != null) {
                "tsaConfig is required for CadesProfile.B_T"
            }
        }
    }

    // Generated equals/hashCode for data class with ByteArray fields would compare by reference;
    // override to do content-equality for the two byte-array fields the caller cares about.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CadesSigningRequest) return false
        return profile == other.profile &&
            keyId == other.keyId &&
            payload.contentEquals(other.payload) &&
            signerCertificateChain.size == other.signerCertificateChain.size &&
            signerCertificateChain.zip(other.signerCertificateChain).all { (a, b) -> a.contentEquals(b) } &&
            signingTime == other.signingTime &&
            detached == other.detached &&
            tsaConfig == other.tsaConfig
    }

    override fun hashCode(): Int {
        var r = profile.hashCode()
        r = 31 * r + keyId.hashCode()
        r = 31 * r + payload.contentHashCode()
        r = 31 * r + signerCertificateChain.sumOf { it.contentHashCode() }
        r = 31 * r + (signingTime?.hashCode() ?: 0)
        r = 31 * r + detached.hashCode()
        r = 31 * r + (tsaConfig?.hashCode() ?: 0)
        return r
    }
}

/**
 * Produced CAdES signature.
 *
 * @property encoded     DER-encoded CMS `SignedData` — the standard CAdES wire form. Callers
 *                       distribute this byte stream alongside the original payload (for detached
 *                       signatures) or by itself (for encapsulated signatures).
 * @property detached    Mirrors [CadesSigningRequest.detached].
 * @property profile     Profile that was produced.
 */
data class CadesSignature(
    val encoded: ByteArray,
    val detached: Boolean,
    val profile: CadesProfile,
) {
    override fun equals(other: Any?): Boolean =
        other is CadesSignature && encoded.contentEquals(other.encoded) &&
            detached == other.detached && profile == other.profile

    override fun hashCode(): Int {
        var r = encoded.contentHashCode()
        r = 31 * r + detached.hashCode()
        r = 31 * r + profile.hashCode()
        return r
    }
}

/**
 * Verification policy supplied to [CadesVerifier.verify].
 *
 * @property requiredProfile                       Minimum CAdES profile to accept.
 * @property trustAnchorResolver                   Resolves the signer-cert chain against the
 *                                                 caller-supplied trust graph.
 * @property detachedPayload                       For detached signatures the caller MUST supply
 *                                                 the original payload bytes here. For encapsulated
 *                                                 signatures pass `null` and the verifier will use
 *                                                 the embedded `encapContentInfo` content.
 * @property allowExpiredCertificateAtSigningTime  When `true`, certificate validity is not enforced
 *                                                 at the claimed signing time.
 * @property maxClockSkew                          Tolerance applied to signing-time / TSA-time
 *                                                 comparisons. Default 5 minutes.
 */
data class CadesVerificationOptions(
    val requiredProfile: CadesProfile,
    val trustAnchorResolver: TrustAnchorResolver,
    val detachedPayload: ByteArray? = null,
    val allowExpiredCertificateAtSigningTime: Boolean = false,
    val maxClockSkew: Duration = Duration.parse("PT5M"),
)

/**
 * Outcome of [CadesVerifier.verify].
 *
 * Mirrors the JAdES result-tree layout — two top-level branches: [Valid] when every check passed,
 * [Invalid] for the specific failure modes a B-B/B-T CAdES verifier produces.
 */
sealed class CadesValidationResult {

    /**
     * @property signerCert        The certificate that produced the signature.
     * @property trust             Trust-graph match returned by the [TrustAnchorResolver].
     * @property signingTime       Asserted signing time (CMS `signing-time` attribute), or null
     *                             when the producer omitted it.
     * @property signatureTimeStamp Time recorded by the TSA in the embedded sig-time-stamp; null
     *                             for B-B.
     * @property profile           Profile actually detected on the wire.
     */
    data class Valid(
        val signerCert: X509Certificate,
        val trust: TrustAnchorMatch,
        val signingTime: Instant?,
        val signatureTimeStamp: Instant?,
        val profile: CadesProfile,
    ) : CadesValidationResult()

    sealed class Invalid : CadesValidationResult() {
        /** Cryptographic verification of the SignerInfo failed. */
        data class BadSignature(val reason: String) : Invalid()

        /** Chain did not anchor to a trusted CA/QC service. */
        data class UntrustedSigner(val cert: X509Certificate) : Invalid()

        /** Required profile was B-T but the signature carries no signature-time-stamp. */
        data class WrongProfile(val found: CadesProfile, val required: CadesProfile) : Invalid()

        /** B-T was required but no time-stamp token could be parsed. */
        data class MissingTimeStamp(val reason: String) : Invalid()

        /** Time-stamp present but its `messageImprint` does not match the signature value. */
        data class TimeStampMismatch(val reason: String) : Invalid()

        /** Signer certificate had already expired at the asserted signing time. */
        data class CertificateExpired(val notAfter: Instant) : Invalid()

        /** Input was not well-formed CMS SignedData or lacked a required CAdES attribute. */
        data class Malformed(val reason: String) : Invalid()

        /**
         * Verifier was given a detached signature but no detached-payload bytes — or an
         * encapsulated signature with a non-null detached payload that disagrees with the
         * embedded content.
         */
        data class MissingDetachedPayload(val reason: String) : Invalid()
    }
}
