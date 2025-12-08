package com.trustweave.trust

import com.trustweave.credential.model.CredentialType

/**
 * Trust policy for credential verification.
 *
 * Defines how trust should be evaluated during credential verification.
 * Makes trust checking explicit and configurable.
 *
 * **Example Usage:**
 * ```kotlin
 * val policy = TrustPolicy.RequireTrustAnchor(registry)
 * val result = trustWeave.verifyCredential(credential, VerificationConfig(trustPolicy = policy))
 * ```
 */
sealed class TrustPolicy {
    /**
     * No trust checking required.
     *
     * Credentials will be verified for signature, expiration, and revocation,
     * but issuer trust will not be checked.
     */
    object NoTrustRequired : TrustPolicy()

    /**
     * Require issuer to be a direct trust anchor.
     *
     * The issuer must be directly registered as a trust anchor in the registry.
     *
     * @param registry The trust registry to check against
     */
    data class RequireTrustAnchor(
        val registry: TrustRegistry
    ) : TrustPolicy()

    /**
     * Require a trust path from verifier to issuer.
     *
     * The issuer must be reachable through a trust path in the registry,
     * with a maximum path length constraint.
     *
     * @param maxPathLength Maximum length of trust path (default: 3)
     * @param registry The trust registry to check against
     * @param credentialType Optional credential type filter for trust path discovery
     */
    data class RequireTrustPath(
        val maxPathLength: Int = 3,
        val registry: TrustRegistry,
        val credentialType: CredentialType? = null
    ) : TrustPolicy() {
        init {
            require(maxPathLength > 0) { "maxPathLength must be positive" }
        }
    }
}

