package com.trustweave.credential.trust

import com.trustweave.did.identifiers.Did

/**
 * Trust policy for issuer validation during credential verification.
 * 
 * Makes trust checking explicit and configurable. By default, verification
 * only checks cryptographic validity, not issuer trust. Use a trust policy
 * to enforce issuer trust requirements.
 * 
 * **Example Usage:**
 * ```kotlin
 * // Accept all issuers (default)
 * val policy = TrustPolicy.acceptAll()
 * 
 * // Only accept specific issuers
 * val policy = TrustPolicy.allowlist(
 *     trustedIssuers = setOf(
 *         Did("did:web:example.com"),
 *         Did("did:key:z6Mk...")
 *     )
 * )
 * 
 * // Reject specific issuers
 * val policy = TrustPolicy.blocklist(
 *     blockedIssuers = setOf(Did("did:web:untrusted.com"))
 * )
 * 
 * // Use in verification
 * val result = service.verify(credential, trustPolicy = policy)
 * ```
 */
interface TrustPolicy {
    /**
     * Checks if an issuer is trusted according to this policy.
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
        fun acceptAll(): TrustPolicy = AcceptAllTrustPolicy
        
        /**
         * Only accepts issuers in the allowlist.
         * 
         * @param trustedIssuers Set of trusted issuer DIDs
         */
        fun allowlist(trustedIssuers: Set<Did>): TrustPolicy = 
            AllowlistTrustPolicy(trustedIssuers)
        
        /**
         * Rejects issuers in the blocklist, accepts all others.
         * 
         * @param blockedIssuers Set of blocked issuer DIDs
         */
        fun blocklist(blockedIssuers: Set<Did>): TrustPolicy = 
            BlocklistTrustPolicy(blockedIssuers)
    }
}

/**
 * Accepts all issuers - no trust validation.
 */
private object AcceptAllTrustPolicy : TrustPolicy {
    override suspend fun isTrusted(issuer: Did): Boolean = true
}

/**
 * Only accepts issuers in the allowlist.
 */
private data class AllowlistTrustPolicy(
    private val trustedIssuers: Set<Did>
) : TrustPolicy {
    override suspend fun isTrusted(issuer: Did): Boolean = 
        issuer in trustedIssuers
}

/**
 * Rejects issuers in the blocklist.
 */
private data class BlocklistTrustPolicy(
    private val blockedIssuers: Set<Did>
) : TrustPolicy {
    override suspend fun isTrusted(issuer: Did): Boolean = 
        issuer !in blockedIssuers
}

