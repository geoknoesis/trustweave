package com.trustweave.trust.dsl

import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.trust.TrustPolicy as CredentialTrustPolicy
import com.trustweave.did.identifiers.Did
import com.trustweave.trust.TrustRegistry
import com.trustweave.trust.types.IssuerIdentity
import com.trustweave.trust.types.TrustPath
import com.trustweave.trust.types.VerifierIdentity
import kotlinx.datetime.Instant

/**
 * Infix operators for expressive trust DSL.
 * 
 * These operators make trust relationships read naturally and provide
 * a beautiful, fluent syntax for trust management.
 * 
 * **Example Usage:**
 * ```kotlin
 * trustWeave.trust {
 *     // Natural language: "universityDid trusts EducationCredential"
 *     universityDid trusts "EducationCredential" because {
 *         description("Trusted university")
 *     }
 *     
 *     // Find trust path: "resolve(verifierDid trustsPath issuerDid)"
 *     val path = resolve(verifierDid trustsPath issuerDid)
 *     when (path) {
 *         is TrustPath.Verified -> println("Path found: ${path.length} hops")
 *         is TrustPath.NotFound -> println("No path found")
 *     }
 * }
 * ```
 */

/**
 * Infix operator to express trust relationship: `did trusts credentialType`.
 * 
 * Creates a trust anchor builder that can be configured with metadata.
 * 
 * **Example:**
 * ```kotlin
 * trustWeave.trust {
 *     universityDid trusts "EducationCredential" because {
 *         description("Trusted university")
 *         credentialTypes("EducationCredential", "DegreeCredential")
 *     }
 * }
 * ```
 * 
 * @param credentialType The credential type this DID trusts
 * @return TrustAnchorBuilder for configuring trust anchor metadata
 */
infix fun Did.trusts(credentialType: String): TrustAnchorBuilder {
    return TrustAnchorBuilder().apply {
        credentialTypes(credentialType)
    }
}

/**
 * Infix operator to express trust relationship for multiple credential types.
 * 
 * **Example:**
 * ```kotlin
 * trustWeave.trust {
 *     universityDid trusts listOf("EducationCredential", "DegreeCredential") because {
 *         description("Trusted university")
 *     }
 * }
 * ```
 */
infix fun Did.trusts(credentialTypes: List<String>): TrustAnchorBuilder {
    return TrustAnchorBuilder().apply {
        credentialTypes(credentialTypes)
    }
}

/**
 * Infix operator to express trust relationship for any credential type.
 * 
 * **Example:**
 * ```kotlin
 * trustWeave.trust {
 *     caDid trustsAll because {
 *         description("Root CA - trusts all credential types")
 *     }
 * }
 * ```
 */
val Did.trustsAll: TrustAnchorBuilder
    get() = TrustAnchorBuilder()

/**
 * Infix operator to configure trust anchor metadata: `builder because { ... }`.
 * 
 * **Example:**
 * ```kotlin
 * trustWeave.trust {
 *     universityDid trusts "EducationCredential" because {
 *         description("Trusted university")
 *         addedAt(Instant.parse("2024-01-01T00:00:00Z"))
 *     }
 * }
 * ```
 */
infix fun TrustAnchorBuilder.because(block: TrustAnchorMetadataBuilder.() -> Unit): TrustAnchorConfig {
    val metadataBuilder = TrustAnchorMetadataBuilder()
    metadataBuilder.block()
    // Merge with existing configuration from TrustAnchorBuilder
    credentialTypes?.let { types ->
        metadataBuilder.credentialTypes(types)
    }
    description?.let { desc ->
        metadataBuilder.description(desc)
    }
    addedAt?.let { instant ->
        metadataBuilder.addedAt(instant)
    }
    return TrustAnchorConfig(metadataBuilder)
}

/**
 * Configuration holder for trust anchor.
 */
class TrustAnchorConfig(
    val metadataBuilder: TrustAnchorMetadataBuilder
)

/**
 * Extension to add trust anchor using infix syntax.
 */
suspend fun TrustBuilder.addAnchor(did: Did, config: TrustAnchorConfig): Boolean {
    return addAnchor(did.value) {
        config.metadataBuilder.credentialTypes?.let { types ->
            credentialTypes(types)
        }
        config.metadataBuilder.description?.let { desc ->
            description(desc)
        }
        config.metadataBuilder.addedAt?.let { instant ->
            addedAt(instant)
        }
    }
}

/**
 * Extension function to find trust path within TrustBuilder context.
 */
suspend fun TrustBuilder.findTrustPath(from: Did, to: Did): TrustPath {
    return findTrustPath(
        from = VerifierIdentity(from),
        to = IssuerIdentity(to)  // IssuerIdentity is a typealias for Did
    )
}

/**
 * Infix operator to find trust path: `fromDid trustsPath toDid`.
 * 
 * Creates a TrustPathFinder that can be resolved within TrustBuilder context.
 * 
 * **Example:**
 * ```kotlin
 * trustWeave.trust {
 *     val path = resolve(verifierDid trustsPath issuerDid)
 *     when (path) {
 *         is TrustPath.Verified -> println("Path found: ${path.length} hops")
 *         is TrustPath.NotFound -> println("No path found")
 *     }
 * }
 * ```
 * 
 * @param target The target DID to find a trust path to
 * @return TrustPathFinder that can be resolved using `resolve()` in TrustBuilder context
 */
infix fun Did.trustsPath(target: Did): TrustPathFinder {
    return TrustPathFinder(this, target)
}

/**
 * Helper class for trust path discovery using infix syntax.
 * 
 * This allows the natural syntax: `fromDid trustsPath toDid`
 */
class TrustPathFinder(
    val from: Did,
    val to: Did
) {
    /**
     * Resolve the trust path using the provided TrustBuilder.
     * 
     * **Example:**
     * ```kotlin
     * trustWeave.trust {
     *     val path = (verifierDid trustsPath issuerDid).resolve(this)
     * }
     * ```
     */
    suspend fun resolve(builder: TrustBuilder): TrustPath {
        return builder.findTrustPath(from, to)
    }
}

/**
 * Extension to resolve trust path automatically within TrustBuilder context.
 * 
 * This allows the natural syntax: `val path = resolve(fromDid trustsPath toDid)`
 * when called within a TrustBuilder receiver scope.
 * 
 * **Example:**
 * ```kotlin
 * trustWeave.trust {
 *     val path = resolve(verifierDid trustsPath issuerDid)
 *     when (path) {
 *         is TrustPath.Verified -> println("Path found: ${path.length} hops")
 *         is TrustPath.NotFound -> println("No path found")
 *     }
 * }
 * ```
 */
suspend fun TrustBuilder.resolve(pathFinder: TrustPathFinder): TrustPath {
    return pathFinder.resolve(this)
}

/**
 * Policy composition operators for combining trust policies.
 * 
 * **Example:**
 * ```kotlin
 * val policy = requireAnchor(caDid) and requireSchema(degreeSchema) or allowExpired()
 * ```
 */

/**
 * Compose two trust policies with AND logic.
 * 
 * Both policies must pass for the issuer to be trusted.
 * 
 * **Example:**
 * ```kotlin
 * val policy = TrustPolicy.allowlist(issuers) and TrustPolicy.blocklist(blocked)
 * ```
 */
infix fun CredentialTrustPolicy.and(other: CredentialTrustPolicy): CredentialTrustPolicy {
    return object : CredentialTrustPolicy {
        override suspend fun isTrusted(issuer: Did): Boolean {
            return this@and.isTrusted(issuer) && other.isTrusted(issuer)
        }
    }
}

/**
 * Compose two trust policies with OR logic.
 * 
 * Either policy can pass for the issuer to be trusted.
 * 
 * **Example:**
 * ```kotlin
 * val policy = requireAnchor(caDid) or requirePath(maxLength = 3)
 * ```
 */
infix fun CredentialTrustPolicy.or(other: CredentialTrustPolicy): CredentialTrustPolicy {
    return object : CredentialTrustPolicy {
        override suspend fun isTrusted(issuer: Did): Boolean {
            return this@or.isTrusted(issuer) || other.isTrusted(issuer)
        }
    }
}

/**
 * Negate a trust policy.
 * 
 * **Example:**
 * ```kotlin
 * val policy = !TrustPolicy.blocklist(blockedIssuers)
 * ```
 */
operator fun CredentialTrustPolicy.not(): CredentialTrustPolicy {
    return object : CredentialTrustPolicy {
        override suspend fun isTrusted(issuer: Did): Boolean {
            return !this@not.isTrusted(issuer)
        }
    }
}

