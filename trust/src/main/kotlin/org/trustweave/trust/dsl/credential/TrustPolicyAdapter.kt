package org.trustweave.trust.dsl.credential

import org.trustweave.credential.trust.TrustPolicy as CredentialTrustPolicy
import org.trustweave.did.identifiers.Did
import org.trustweave.trust.TrustRegistry

/**
 * Adapter to convert TrustWeave TrustPolicy to credential-api TrustPolicy.
 */
internal object TrustPolicyAdapter {
    /**
     * Convert TrustRegistry to CredentialTrustPolicy that requires direct trust anchor.
     */
    fun fromRegistry(registry: TrustRegistry): CredentialTrustPolicy {
        return object : CredentialTrustPolicy {
            override suspend fun isTrusted(issuer: Did): Boolean {
                return registry.isTrustedIssuer(issuer.value, null)
            }
        }
    }

    /**
     * Convert TrustRegistry to CredentialTrustPolicy that requires trust path.
     */
    fun fromRegistryWithPath(registry: TrustRegistry, maxPathLength: Int): CredentialTrustPolicy {
        return object : CredentialTrustPolicy {
            override suspend fun isTrusted(issuer: Did): Boolean {
                // For now, check if issuer is directly trusted
                // TODO: Implement trust path checking with maxPathLength
                return registry.isTrustedIssuer(issuer.value, null)
            }
        }
    }
}

