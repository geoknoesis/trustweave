package org.trustweave.signatures.jades

import kotlinx.serialization.json.JsonElement
import org.trustweave.signatures.trustlists.TrustAnchorMatch
import java.security.cert.X509Certificate
import kotlin.time.Instant

/**
 * Outcome of [JadesVerifier.verify].
 *
 * Two top-level branches: [Valid] (every check passed) and [Invalid] (at least one check failed).
 * `Invalid` is itself sealed into the distinct failure modes a JAdES B-B/B-T verifier produces;
 * pattern-matching on the leaves lets callers surface specific user-facing errors.
 */
sealed class JadesValidationResult {
    /**
     * Signature was structurally well-formed, mathematically valid, trust-anchored, and
     * (for B-T) carried a matching signature time-stamp.
     *
     * @property header             Reconstructed protected header.
     * @property payload            The decoded JSON payload.
     * @property trust              Trust-graph match returned by the
     *                              [org.trustweave.signatures.trustlists.TrustAnchorResolver].
     * @property signingTime        Asserted signing time (`sigT`).
     * @property signatureTimeStamp Time recorded by the TSA in the embedded `sigTst`; null for B-B.
     */
    data class Valid(
        val header: JadesHeader,
        val payload: JsonElement,
        val trust: TrustAnchorMatch,
        val signingTime: Instant,
        val signatureTimeStamp: Instant?,
        /**
         * Profile the verification actually established (B-B, B-T, B-LT or B-LTA): B-T needs a time-stamp that
         * verified against `timestampTrustAnchors`, B-LT also needs embedded revocation evidence that verified
         * and covers the chain, B-LTA also a trusted archival time-stamp.
         */
        val foundProfile: JadesProfile = JadesProfile.B_B,
        /**
         * Number of embedded `xVals` certificates (long-term validation data). 0 for B-B/B-T.
         */
        val xValsCount: Int = 0,
        /**
         * Number of embedded `rVals` revocation snapshots. 0 for B-B/B-T.
         */
        val rValsCount: Int = 0,
        /**
         * Time recorded by the latest `arcTst` archival time-stamp; null unless the envelope was
         * B-LTA AND the archival time-stamp validated.
         */
        val archivalTimeStamp: Instant? = null,
        /**
         * `true` only when a [org.trustweave.signatures.revocation.RevocationPolicy] other than
         * `NOT_CHECKED` was requested AND the signer and every CA below the trust anchor were shown not
         * revoked by verified, current CRL / OCSP evidence. `false` means status is unknown.
         */
        val revocationChecked: Boolean = false,
    ) : JadesValidationResult()

    sealed class Invalid : JadesValidationResult() {
        /** Cryptographic verification of the signature value failed. */
        data class BadSignature(
            val reason: String,
        ) : Invalid()

        /**
         * Signature was mathematically valid but the signer's certificate did not chain to a
         * CA/QC service in the supplied trust list.
         */
        data class UntrustedSigner(
            val cert: X509Certificate,
        ) : Invalid()

        /**
         * Found-profile differs from required-profile. E.g. caller required B-T but the
         * signature carries no `sigTst`.
         */
        data class WrongProfile(
            val found: JadesProfile,
            val required: JadesProfile,
        ) : Invalid()

        /** B-T was required but no `sigTst` could be parsed. */
        data class MissingTimeStamp(
            val reason: String,
        ) : Invalid()

        /**
         * `sigTst` was structurally valid but its `messageImprint` did not match the SHA-256 of
         * the signature value, or its `genTime` was outside [JadesVerificationOptions.maxClockSkew]
         * of the asserted signing time.
         */
        data class TimeStampMismatch(
            val reason: String,
        ) : Invalid()

        /**
         * Signer certificate had already expired at the asserted signing time, and
         * [JadesVerificationOptions.allowExpiredCertificateAtSigningTime] is false.
         */
        data class CertificateExpired(
            val notAfter: Instant,
        ) : Invalid()

        /** The signer's trust-list service was withdrawn at (or the signature cannot be shown to predate) [withdrawnAt]. */
        data class TrustWithdrawn(
            val cert: X509Certificate,
            val withdrawnAt: Instant,
            val reason: String,
        ) : Invalid()

        /** A certificate in the chain was revoked at or before the verification time. */
        data class CertificateRevoked(
            val cert: X509Certificate,
            val revokedAt: Instant,
            val reason: String,
        ) : Invalid()

        /** Revocation checking was required but a certificate has no usable evidence. */
        data class RevocationUnavailable(
            val reason: String,
        ) : Invalid()

        /** Input was not well-formed JWS JSON Flattened or JWS Compact serialization. */
        data class Malformed(
            val reason: String,
        ) : Invalid()
    }
}
