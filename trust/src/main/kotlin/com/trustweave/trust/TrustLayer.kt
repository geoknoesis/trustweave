package com.trustweave.trust

import com.trustweave.trust.dsl.*
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.CredentialVerificationResult
import com.trustweave.trust.dsl.credential.IssuanceBuilder
import com.trustweave.trust.dsl.credential.VerificationBuilder
import com.trustweave.wallet.Wallet
import com.trustweave.trust.dsl.wallet.WalletBuilder
import com.trustweave.trust.dsl.did.DidBuilder
import com.trustweave.trust.dsl.did.DidDocumentBuilder
import com.trustweave.trust.dsl.did.DelegationBuilder
import com.trustweave.trust.dsl.KeyRotationBuilder
import com.trustweave.did.verifier.DelegationChainResult

/**
 * Trust Layer - The main facade for trust layer operations.
 *
 * TrustLayer provides a unified interface for all trust-related operations including
 * credential issuance/verification, DID management, trust anchor management, and wallet operations.
 * It serves as the primary entry point for applications using the VeriCore trust layer.
 *
 * **Example Usage**:
 * ```kotlin
 * // Build and configure the trust layer
 * val trustLayer = TrustLayer.build {
 *     keys {
 *         provider("inMemory")
 *         algorithm("Ed25519")
 *     }
 *     
 *     did {
 *         method("key") {
 *             algorithm("Ed25519")
 *         }
 *     }
 *     
 *     trust {
 *         provider("inMemory")
 *     }
 * }
 *
 * // Issue credentials
 * val credential = trustLayer.issue {
 *     credential {
 *         id("credential:123")
 *         type("EducationCredential")
 *         issuer("did:key:university")
 *         subject {
 *             id("did:key:student")
 *             claim("degree", "Bachelor of Science")
 *         }
 *     }
 *     by(issuerDid = "did:key:university", keyId = "key-1")
 * }
 *
 * // Manage trust anchors using the trust DSL
 * trustLayer.trust {
 *     addAnchor("did:key:university") {
 *         credentialTypes("EducationCredential")
 *         description("Trusted university")
 *     }
 * }
 *
 * // Or use direct methods
 * trustLayer.addTrustAnchor("did:key:university") {
 *     credentialTypes("EducationCredential")
 *     description("Trusted university")
 * }
 *
 * // Verify credentials with trust checking
 * val result = trustLayer.verify {
 *     credential(credential)
 *     checkTrust(true) // Verify issuer is trusted
 * }
 *
 * // Check trust status
 * val isTrusted = trustLayer.isTrustedIssuer("did:key:university", "EducationCredential")
 * ```
 */
class TrustLayer private constructor(
    private val config: TrustLayerConfig,
    private val context: TrustLayerContext
) {
    /**
     * Get the DSL context for advanced operations.
     */
    fun getDslContext(): TrustLayerContext = context
    /**
     * The underlying configuration.
     * 
     * Provides access to lower-level configuration details if needed.
     * Most operations should be done through the TrustLayer facade methods.
     */
    val configuration: TrustLayerConfig
        get() = config
    
    // ========== Credential Operations ==========
    
    /**
     * Issue a verifiable credential using the configured trust layer.
     * 
     * @param block DSL block for building the credential and specifying issuance parameters
     * @return The issued verifiable credential
     */
    suspend fun issue(block: IssuanceBuilder.() -> Unit): VerifiableCredential {
        return context.issue(block)
    }
    
    /**
     * Verify a verifiable credential using the configured trust layer.
     * 
     * @param block DSL block for specifying verification parameters
     * @return The credential verification result
     */
    suspend fun verify(block: VerificationBuilder.() -> Unit): CredentialVerificationResult {
        return context.verify(block)
    }
    
    // ========== DID Operations ==========
    
    /**
     * Create a DID using the configured trust layer.
     * 
     * @param block DSL block for configuring the DID
     * @return The created DID string
     */
    suspend fun createDid(block: DidBuilder.() -> Unit): String {
        return context.createDid(block)
    }
    
    /**
     * Update a DID document using the configured trust layer.
     * 
     * @param block DSL block for specifying the update
     * @return The updated DID document
     */
    suspend fun updateDid(block: DidDocumentBuilder.() -> Unit): com.trustweave.did.DidDocument {
        return context.updateDid(block)
    }
    
    /**
     * Delegate authority to another DID using the configured trust layer.
     * 
     * @param block DSL block for specifying the delegation
     * @return The delegation chain result
     */
    suspend fun delegate(block: suspend DelegationBuilder.() -> Unit): DelegationChainResult {
        return context.delegate(block)
    }
    
    /**
     * Rotate a key in a DID document using the configured trust layer.
     * 
     * @param block DSL block for specifying the key rotation
     * @return The updated DID document with rotated key
     */
    suspend fun rotateKey(block: KeyRotationBuilder.() -> Unit): Any {
        return context.rotateKey(block)
    }
    
    // ========== Wallet Operations ==========
    
    /**
     * Create a wallet using the configured trust layer.
     * 
     * @param block DSL block for configuring the wallet
     * @return The created wallet
     */
    suspend fun wallet(block: WalletBuilder.() -> Unit): Wallet {
        return context.wallet(block)
    }
    
    // ========== Trust Operations (DSL Style) ==========
    
    /**
     * Perform trust operations using the trust DSL.
     * 
     * Provides a fluent API for managing trust anchors and discovering trust paths.
     * The registry must be configured in the TrustLayerConfig.
     * 
     * **Example**:
     * ```kotlin
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
     * 
     * @param block DSL block for trust operations
     * @throws IllegalStateException if trust registry is not configured
     */
    suspend fun trust(block: suspend TrustBuilder.() -> Unit) {
        context.trust(block)
    }
    
    // ========== Trust Operations (Direct Methods) ==========
    
    /**
     * Add a trust anchor to the registry.
     * 
     * Convenience method for adding a trust anchor without using the DSL.
     * 
     * @param anchorDid The DID of the trust anchor
     * @param block Optional block for configuring the trust anchor metadata
     * @return true if the anchor was added successfully, false if it already exists
     * @throws IllegalStateException if trust registry is not configured
     */
    suspend fun addTrustAnchor(
        anchorDid: String,
        block: TrustAnchorMetadataBuilder.() -> Unit = {}
    ): Boolean {
        val registry = getTrustRegistryOrThrow()
        val builder = TrustAnchorMetadataBuilder()
        builder.block()
        return registry.addTrustAnchor(anchorDid, builder.build())
    }
    
    /**
     * Remove a trust anchor from the registry.
     * 
     * @param anchorDid The DID of the trust anchor to remove
     * @return true if the anchor was removed, false if it didn't exist
     * @throws IllegalStateException if trust registry is not configured
     */
    suspend fun removeTrustAnchor(anchorDid: String): Boolean {
        val registry = getTrustRegistryOrThrow()
        return registry.removeTrustAnchor(anchorDid)
    }
    
    /**
     * Check if an issuer is trusted for a specific credential type.
     * 
     * @param issuerDid The DID of the issuer to check
     * @param credentialType The credential type (null means check for any type)
     * @return true if the issuer is trusted, false otherwise
     * @throws IllegalStateException if trust registry is not configured
     */
    suspend fun isTrustedIssuer(issuerDid: String, credentialType: String? = null): Boolean {
        val registry = getTrustRegistryOrThrow()
        return registry.isTrustedIssuer(issuerDid, credentialType)
    }
    
    /**
     * Find a trust path between two DIDs.
     * 
     * @param fromDid The starting DID (typically the verifier)
     * @param toDid The target DID (typically the issuer)
     * @return TrustPathResult if a path exists, null otherwise
     * @throws IllegalStateException if trust registry is not configured
     */
    suspend fun getTrustPath(fromDid: String, toDid: String): TrustPathResult? {
        val registry = getTrustRegistryOrThrow()
        return registry.getTrustPath(fromDid, toDid)
    }
    
    /**
     * Get all trusted issuers for a specific credential type.
     * 
     * @param credentialType The credential type (null means all types)
     * @return List of trusted issuer DIDs
     * @throws IllegalStateException if trust registry is not configured
     */
    suspend fun getTrustedIssuers(credentialType: String? = null): List<String> {
        val registry = getTrustRegistryOrThrow()
        return registry.getTrustedIssuers(credentialType)
    }
    
    // ========== Internal Helper Methods ==========
    
    /**
     * Get the trust registry or throw an exception if not configured.
     */
    private fun getTrustRegistryOrThrow(): TrustRegistry {
        return config.trustRegistry as? TrustRegistry
            ?: throw IllegalStateException(
                "Trust registry is not configured. " +
                "Configure it in TrustLayer.build { trust { provider(\"inMemory\") } }"
            )
    }
    
    companion object {
        /**
         * Build a TrustLayer with the provided configuration.
         * 
         * This is the recommended way to create a TrustLayer instance.
         * 
         * **Example**:
         * ```kotlin
         * val trustLayer = TrustLayer.build {
         *     keys {
         *         provider("inMemory")
         *         algorithm("Ed25519")
         *     }
         *     
         *     trust {
         *         provider("inMemory")
         *     }
         * }
         * ```
         * 
         * @param name Optional name for the trust layer instance (default: "default")
         * @param registries Optional pre-configured registries (default: new empty registries)
         * @param block DSL block for configuring the trust layer
         * @return A configured TrustLayer instance
         */
        suspend fun build(
            name: String = "default",
            registries: TrustLayerRegistries = TrustLayerRegistries(),
            block: TrustLayerConfig.Builder.() -> Unit
        ): TrustLayer {
            val config = trustLayer(name, registries, block)
            val context = TrustLayerContext(config)
            return TrustLayer(config, context)
        }
        
        /**
         * Create a TrustLayer from an existing TrustLayerConfig.
         * 
         * Useful when you already have a configuration object and want to create
         * the facade wrapper.
         * 
         * @param config The existing TrustLayerConfig
         * @return A TrustLayer instance wrapping the provided config
         */
        fun from(config: TrustLayerConfig): TrustLayer {
            val context = TrustLayerContext(config)
            return TrustLayer(config, context)
        }
    }
}

