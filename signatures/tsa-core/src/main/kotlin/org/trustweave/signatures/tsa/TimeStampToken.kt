package org.trustweave.signatures.tsa

import kotlinx.datetime.Instant

/**
 * Validated RFC 3161 time-stamp token in eagerly-parsed value-object form.
 *
 * The [encoded] field is the DER-encoded `TimeStampToken` (a CMS `SignedData`) as returned
 * by the TSA. This is the exact byte sequence that gets placed into a JAdES `sigTst`
 * header's `tstTokens[i].val` field. The remaining fields are decoded once at construction
 * time so that verifier code does not need to re-parse the ASN.1 structure for routine
 * inspection (gen-time comparison, hash-algorithm matching, etc.).
 *
 * Equality is defined over [encoded] only — two tokens are considered equal iff their
 * DER serialisations are byte-for-byte identical. The other fields are derived from
 * [encoded] and so do not need to participate in equality directly.
 */
data class TimeStampToken(
    /** DER-encoded `TimeStampToken` (CMS `SignedData`). */
    val encoded: ByteArray,
    /** The TSA-asserted signing time, parsed from `TSTInfo.genTime`. */
    val genTime: Instant,
    /**
     * RFC 2253 subject Distinguished Name of the TSA's signer certificate.
     * Empty string if the token did not embed a certificate chain.
     */
    val tsaSubject: String,
    /** The hash algorithm asserted by `MessageImprint.hashAlgorithm`. */
    val messageImprintAlgorithm: TsaHashAlgorithm,
    /** The exact bytes from `MessageImprint.hashedMessage`. */
    val messageImprint: ByteArray,
    /** Serial number of the TSA response (`TSTInfo.serialNumber`), big-endian. */
    val serialNumber: ByteArray,
    /** Optional policy OID asserted in `TSTInfo.policy`. */
    val policyOid: String?,
) {
    override fun equals(other: Any?): Boolean =
        other is TimeStampToken && encoded.contentEquals(other.encoded)

    override fun hashCode(): Int = encoded.contentHashCode()
}
