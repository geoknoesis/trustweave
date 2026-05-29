package org.trustweave.credential.trust

import org.trustweave.did.identifiers.Did

/**
 * Evaluates whether an issuer is trusted during credential verification.
 *
 * Makes trust checking explicit and configurable. By default, verification
 * only checks cryptographic validity, not issuer trust. Use a trust evaluator
 * to enforce issuer trust requirements.
 *
 * **Example Usage:**
 * ```kotlin
 * // Accept all issuers (default)
 * val evaluator = TrustEvaluator.acceptAll()
 *
 * // Only accept specific issuers
 * val evaluator = TrustEvaluator.allowlist(
 *     trustedIssuers = setOf(
 *         Did("did:web:example.com"),
 *         Did("did:key:z6Mk...")
 *     )
 * )
 *
 * // Reject specific issuers
 * val evaluator = TrustEvaluator.blocklist(
 *     blockedIssuers = setOf(Did("did:web:untrusted.com"))
 * )
 *
 * // Use in verification
 * val result = service.verify(credential, trustEvaluator = evaluator)
 * ```
 */
interface TrustEvaluator {
    /**
     * Checks if an issuer is trusted according to this evaluator.
     *
     * @param issuer The issuer DID to check
     * @return true if the issuer is trusted, false otherwise
     */
    suspend fun isTrusted(issuer: Did): Boolean

    companion object {
        /**
         * Accepts all issuers (no trust validation).
         *
         * This is the default policy - only cryptographic validity is checked.
         */
        fun acceptAll(): TrustEvaluator = AcceptAllTrustEvaluator

        /**
         * Only accepts issuers in the allowlist.
         *
         * @param trustedIssuers Set of trusted issuer DIDs
         */
        fun allowlist(trustedIssuers: Set<Did>): TrustEvaluator =
            AllowlistTrustEvaluator(trustedIssuers)

        /**
         * Rejects issuers in the blocklist, accepts all others.
         *
         * @param blockedIssuers Set of blocked issuer DIDs
         */
        fun blocklist(blockedIssuers: Set<Did>): TrustEvaluator =
            BlocklistTrustEvaluator(blockedIssuers)
    }
}

/**
 * Accepts all issuers - no trust validation.
 */
private object AcceptAllTrustEvaluator : TrustEvaluator {
    override suspend fun isTrusted(issuer: Did): Boolean = true
}

/**
 * Only accepts issuers in the allowlist.
 */
private data class AllowlistTrustEvaluator(
    private val trustedIssuers: Set<Did>
) : TrustEvaluator {
    override suspend fun isTrusted(issuer: Did): Boolean =
        issuer in trustedIssuers
}

/**
 * Rejects issuers in the blocklist.
 */
private data class BlocklistTrustEvaluator(
    private val blockedIssuers: Set<Did>
) : TrustEvaluator {
    override suspend fun isTrusted(issuer: Did): Boolean =
        issuer !in blockedIssuers
}
