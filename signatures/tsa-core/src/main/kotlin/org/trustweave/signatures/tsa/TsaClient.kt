package org.trustweave.signatures.tsa

/**
 * RFC 3161 Time-Stamp Authority client.
 *
 * Each [TsaClient] instance is bound to a single TSA endpoint via its [TsaConfig].
 * Implementations MUST be safe for concurrent use from multiple coroutines.
 *
 * MVP scope (TrustWeave 0.7.x): synchronous request/response, one digest algorithm
 * per call, no long-term archival features. See
 * [docs/architecture/eidas-qes-design.md](../../../../../../../docs/architecture/eidas-qes-design.md).
 */
interface TsaClient {

    /**
     * Request a time-stamp token for the given message digest.
     *
     * The caller MUST supply the digest bytes already hashed — this method does NOT
     * hash the input. The TSA receives only the digest; the original message is
     * never transmitted.
     *
     * @param digest        Pre-computed message-digest bytes.
     * @param hashAlgorithm The algorithm that produced [digest]; its OID is sent to the TSA
     *                      and the byte length must match the algorithm's output size.
     * @param nonce         Optional client-side nonce. If non-null the TSA's response MUST
     *                      echo the same nonce; the validation step in the default
     *                      implementation will reject any mismatch.
     * @return A parsed and structurally validated [TimeStampToken] suitable for embedding
     *         in a JAdES `sigTst` header.
     * @throws TsaException on network failure, non-2xx HTTP response, malformed token,
     *                      policy/nonce mismatch, or signer-certificate pin failure.
     */
    suspend fun requestTimeStamp(
        digest: ByteArray,
        hashAlgorithm: TsaHashAlgorithm,
        nonce: ByteArray? = null,
    ): TimeStampToken
}

/**
 * Hash algorithms supported for the message-imprint of an RFC 3161 request.
 *
 * The selection is deliberately narrow: SHA-256, SHA-384 and SHA-512 are the only
 * digest algorithms required by the JAdES B-T profile and by every European
 * qualified TSA we are aware of as of 2026-Q2.
 */
enum class TsaHashAlgorithm(val oid: String, val jcaName: String, val digestSizeBytes: Int) {
    SHA_256("2.16.840.1.101.3.4.2.1", "SHA-256", 32),
    SHA_384("2.16.840.1.101.3.4.2.2", "SHA-384", 48),
    SHA_512("2.16.840.1.101.3.4.2.3", "SHA-512", 64),
}

/**
 * Thrown when a TSA exchange fails.
 *
 * Subclasses are intentionally not exposed in the MVP: callers should treat any
 * [TsaException] as fatal for the current signature operation and decide on
 * retry/fallback policy externally.
 */
class TsaException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
