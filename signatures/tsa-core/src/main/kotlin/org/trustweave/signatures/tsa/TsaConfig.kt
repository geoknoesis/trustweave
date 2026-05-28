package org.trustweave.signatures.tsa

/**
 * Per-endpoint configuration for a [TsaClient].
 *
 * One instance addresses one TSA URL. Multiple [TsaConfig] / [TsaClient] pairs are typically
 * composed behind a round-robin or failover decorator that the application supplies — this
 * module does not attempt to provide one.
 *
 * @property endpointUrl                 Full URL of the TSA endpoint (HTTP or HTTPS).
 * @property requestTimeoutMs            Combined connect+read timeout per request. Default 10 s.
 * @property username                    Optional HTTP Basic auth username (used by some
 *                                       commercial TSAs).
 * @property password                    Optional HTTP Basic auth password.
 * @property expectedPolicyOid           If non-null, the request asks the TSA to use this
 *                                       policy OID and the response is rejected when the
 *                                       echoed policy does not match.
 * @property trustedSignerCertificates   Optional pin-list of DER-encoded X.509 certificates.
 *                                       When non-empty the response token's signer certificate
 *                                       must match one entry by issuer + serial AND the token's
 *                                       cryptographic signature must verify against it.
 *                                       Empty list disables pin verification.
 * @property includeNonce                When `true` (default) and the caller does not supply a
 *                                       nonce explicitly, the default implementation generates
 *                                       a 64-bit random nonce per request. RFC 3161 strongly
 *                                       recommends nonce usage to defeat replay attacks.
 */
data class TsaConfig(
    val endpointUrl: String,
    val requestTimeoutMs: Long = 10_000,
    val username: String? = null,
    val password: String? = null,
    val expectedPolicyOid: String? = null,
    val trustedSignerCertificates: List<ByteArray> = emptyList(),
    val includeNonce: Boolean = true,
) {
    init {
        require(endpointUrl.isNotBlank()) { "endpointUrl must not be blank" }
        require(requestTimeoutMs > 0) { "requestTimeoutMs must be positive" }
        require((username == null) == (password == null)) {
            "username and password must be supplied together (or both omitted)"
        }
    }
}
