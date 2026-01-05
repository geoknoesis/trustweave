package org.trustweave.trust.dsl

import org.trustweave.trust.TrustAnchorMetadata
import org.trustweave.trust.TrustRegistry
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.TrustPath
import org.trustweave.trust.types.VerifierIdentity
import org.trustweave.trust.types.IssuerIdentity
import org.trustweave.did.identifiers.Did
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * Trust Registry DSL.
 *
 * Provides a fluent API for managing trust anchors and discovering trust paths.
 *
 * **Example Usage**:
 * ```kotlin
 * val trustLayer = trustLayer {
 *     trust {
 *         provider("inMemory")
 *     }
 * }
 *
 * trustLayer.trust {
 *     addAnchor("did:key:university") {
 *         credentialTypes("EducationCredential")
 *         description("Trusted university")
 *     }
 *
 *     val isTrusted = isTrusted("did:key:university", "EducationCredential")
 *     val path = getTrustPath("did:key:verifier", "did:key:issuer")
 * }
 * ```
 */
class TrustBuilder(
    private val registry: TrustRegistry
) {
    /**
     * Add a trust anchor with metadata.
     */
    suspend fun addAnchor(did: String, block: TrustAnchorMetadataBuilder.() -> Unit = {}): Boolean {
        val builder = TrustAnchorMetadataBuilder()
        builder.block()
        return registry.addTrustAnchor(did, builder.build())
    }

    /**
     * Remove a trust anchor.
     */
    suspend fun removeAnchor(did: String): Boolean {
        return registry.removeTrustAnchor(did)
    }

    /**
     * Check if an issuer is trusted.
     */
    suspend fun isTrusted(issuerDid: String, credentialType: String? = null): Boolean {
        return registry.isTrustedIssuer(issuerDid, credentialType)
    }

    /**
     * Find a trust path between two identities (first-class type).
     *
     * Returns a sealed TrustPath type that makes trust relationships explicit.
     *
     * **Example:**
     * ```kotlin
     * when (val path = findTrustPath(verifier, issuer)) {
     *     is TrustPath.Verified -> {
     *         println("Trusted via path: ${path.anchors.map { it.did }}")
     *         println("Path length: ${path.length}")
     *     }
     *     is TrustPath.NotFound -> {
     *         println("No trust path found: ${path.reason}")
     *     }
     * }
     * ```
     *
     * @param from The verifier identity
     * @param to The issuer identity
     * @return TrustPath.Verified if a path exists, TrustPath.NotFound otherwise
     */
    suspend fun findTrustPath(from: VerifierIdentity, to: IssuerIdentity): TrustPath {
        return registry.findTrustPath(from, to)
    }

    /**
     * Get all trusted issuers for a credential type.
     */
    suspend fun getTrustedIssuers(credentialType: String? = null): List<String> {
        return registry.getTrustedIssuers(credentialType)
    }

    /**
     * Add trust anchor using infix syntax.
     * 
     * **Example:**
     * ```kotlin
     * trustWeave.trust {
     *     addAnchor(universityDid trusts "EducationCredential" because {
     *         description("Trusted university")
     *     })
     * }
     * ```
     */
    suspend fun addAnchor(config: TrustAnchorConfig): Boolean {
        // This will be called with the result of "did trusts type because { }"
        // We need to extract the DID from the config
        throw IllegalStateException(
            "addAnchor with TrustAnchorConfig requires DID. " +
            "Use: addAnchor(did, did trusts type because { ... })"
        )
    }

    /**
     * Add trust anchor using infix syntax with explicit DID.
     * 
     * **Example:**
     * ```kotlin
     * trustWeave.trust {
     *     addAnchor(universityDid, universityDid trusts "EducationCredential" because {
     *         description("Trusted university")
     *     })
     * }
     * ```
     */
    suspend fun addAnchor(did: Did, config: TrustAnchorConfig): Boolean {
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
     * Resolve trust path using infix syntax.
     * 
     * This is the recommended way to use the `trustsPath` infix operator.
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
    suspend fun resolve(pathFinder: TrustPathFinder): TrustPath {
        return pathFinder.resolve(this)
    }
}

/**
 * Trust anchor metadata builder.
 */
class TrustAnchorMetadataBuilder {
    internal var credentialTypes: List<String>? = null
    internal var description: String? = null
    internal var addedAt: Instant = Clock.System.now()

    /**
     * Set credential types this anchor is trusted for.
     */
    fun credentialTypes(vararg types: String) {
        credentialTypes = types.toList()
    }

    /**
     * Set credential types this anchor is trusted for.
     */
    fun credentialTypes(types: List<String>) {
        credentialTypes = types
    }

    /**
     * Set description of the trust anchor.
     */
    fun description(desc: String) {
        description = desc
    }

    /**
     * Set when the anchor was added.
     */
    fun addedAt(instant: Instant) {
        addedAt = instant
    }

    /**
     * Build trust anchor metadata.
     */
    internal fun build(): TrustAnchorMetadata {
        return TrustAnchorMetadata(
            credentialTypes = credentialTypes,
            description = description,
            addedAt = addedAt
        )
    }
}

/**
 * Extension function to access trust registry via TrustWeave.
 */
suspend fun TrustWeave.trust(block: suspend TrustBuilder.() -> Unit) {
    val registry = getTrustRegistry() ?: throw IllegalStateException(
        "Trust registry is not configured. Configure it in trustWeave { trust { provider(\"inMemory\") } }"
    )
    val builder = TrustBuilder(registry)
    builder.block()
}


