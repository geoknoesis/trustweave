package com.trustweave.trust.dsl

import com.trustweave.trust.TrustAnchorMetadata
import com.trustweave.trust.TrustPathResult
import com.trustweave.trust.TrustRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

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
     * Find a trust path between two DIDs.
     */
    suspend fun getTrustPath(fromDid: String, toDid: String): TrustPathResult? {
        return registry.getTrustPath(fromDid, toDid)
    }
    
    /**
     * Get all trusted issuers for a credential type.
     */
    suspend fun getTrustedIssuers(credentialType: String? = null): List<String> {
        return registry.getTrustedIssuers(credentialType)
    }
}

/**
 * Trust anchor metadata builder.
 */
class TrustAnchorMetadataBuilder {
    private var credentialTypes: List<String>? = null
    private var description: String? = null
    private var addedAt: Instant = Instant.now()
    
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
 * Extension function to access trust registry via trust layer context.
 */
suspend fun TrustLayerContext.trust(block: suspend TrustBuilder.() -> Unit) {
    val registry = getTrustRegistry() ?: throw IllegalStateException(
        "Trust registry is not configured. Configure it in trustLayer { trust { provider(\"inMemory\") } }"
    )
    val builder = TrustBuilder(registry)
    builder.block()
}

/**
 * Extension function for direct trust operations on trust layer config.
 */
suspend fun TrustLayerConfig.trust(block: suspend TrustBuilder.() -> Unit) {
    dsl().trust(block)
}

