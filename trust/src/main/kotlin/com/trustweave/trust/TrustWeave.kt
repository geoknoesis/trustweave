package com.trustweave.trust

import com.trustweave.trust.dsl.*
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.trust.dsl.credential.IssuanceBuilder
import com.trustweave.trust.dsl.credential.VerificationBuilder
import com.trustweave.wallet.Wallet
import com.trustweave.trust.dsl.wallet.WalletBuilder
import com.trustweave.trust.dsl.did.DidBuilder
import com.trustweave.trust.dsl.did.DidDocumentBuilder
import com.trustweave.trust.dsl.did.DelegationBuilder
import com.trustweave.trust.dsl.KeyRotationBuilder
import com.trustweave.did.DidDocument
import com.trustweave.did.verifier.DelegationChainResult
import com.trustweave.trust.types.Did
import com.trustweave.trust.types.VerificationResult

/**
 * TrustWeave - The main facade for trust layer operations.
 *
 * TrustWeave provides a unified interface for all trust-related operations including
 * credential issuance/verification, DID management, trust anchor management, and wallet operations.
 * It serves as the primary entry point for applications using TrustWeave.
 *
 * **Example Usage**:
 * ```kotlin
 * // Build and configure TrustWeave
 * val trustWeave = TrustWeave.build {
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
 * val credential = trustWeave.issue {
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
 * trustWeave.trust {
 *     addAnchor("did:key:university") {
 *         credentialTypes("EducationCredential")
 *         description("Trusted university")
 *     }
 * }
 *
 * // Verify credentials with trust checking
 * val result = trustWeave.verify {
 *     credential(credential)
 *     checkTrust(true) // Verify issuer is trusted
 * }
 *
 * // Check trust status using DSL
 * trustWeave.trust {
 *     val isTrusted = isTrusted("did:key:university", "EducationCredential")
 * }
 * ```
 */
class TrustWeave private constructor(
    private val config: TrustWeaveConfig,
    private val context: TrustWeaveContext
) {
    /**
     * Get the DSL context for advanced operations.
     * Internal use only - prefer using TrustWeave facade methods.
     */
    internal fun getDslContext(): TrustWeaveContext = context
    /**
     * The underlying configuration.
     * 
     * Provides access to lower-level configuration details if needed.
     * Most operations should be done through the TrustWeave facade methods.
     */
    val configuration: TrustWeaveConfig
        get() = config
    
    // ========== Credential Operations ==========
    
    /**
     * Issue a verifiable credential using the configured TrustWeave instance.
     * 
     * @param block DSL block for building the credential and specifying issuance parameters
     * @return The issued verifiable credential
     */
    suspend fun issue(block: IssuanceBuilder.() -> Unit): VerifiableCredential {
        return context.issue(block)
    }
    
    /**
     * Verify a verifiable credential using the configured TrustWeave instance.
     * 
     * @param block DSL block for specifying verification parameters
     * @return The credential verification result (sealed type for exhaustive error handling)
     */
    suspend fun verify(block: VerificationBuilder.() -> Unit): VerificationResult {
        return context.verify(block)
    }
    
    // ========== DID Operations ==========
    
    /**
     * Create a DID using the configured TrustWeave instance.
     * 
     * @param block DSL block for configuring the DID
     * @return The created DID (type-safe)
     */
    suspend fun createDid(block: DidBuilder.() -> Unit): Did {
        return context.createDid(block)
    }
    
    /**
     * Update a DID document using the configured TrustWeave instance.
     * 
     * @param block DSL block for specifying the update
     * @return The updated DID document
     */
    suspend fun updateDid(block: DidDocumentBuilder.() -> Unit): DidDocument {
        return context.updateDid(block)
    }
    
    /**
     * Delegate authority to another DID using the configured TrustWeave instance.
     * 
     * @param block DSL block for specifying the delegation
     * @return The delegation chain result
     */
    suspend fun delegate(block: suspend DelegationBuilder.() -> Unit): DelegationChainResult {
        return context.delegate(block)
    }
    
    /**
     * Rotate a key in a DID document using the configured TrustWeave instance.
     * 
     * @param block DSL block for specifying the key rotation
     * @return The updated DID document with rotated key
     */
    suspend fun rotateKey(block: KeyRotationBuilder.() -> Unit): Any {
        return context.rotateKey(block)
    }
    
    // ========== Wallet Operations ==========
    
    /**
     * Create a wallet using the configured TrustWeave instance.
     * 
     * @param block DSL block for configuring the wallet
     * @return The created wallet
     */
    suspend fun wallet(block: WalletBuilder.() -> Unit): Wallet {
        return context.wallet(block)
    }
    
    // ========== Trust Operations ==========
    
    /**
     * Perform trust operations using the trust DSL.
     * 
     * Provides a fluent API for managing trust anchors and discovering trust paths.
     * The registry must be configured in the TrustWeaveConfig.
     * 
     * **Example**:
     * ```kotlin
     * trustWeave.trust {
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
    
    
    companion object {
        /**
         * Build a TrustWeave instance with the provided configuration.
         * 
         * This is the recommended way to create a TrustWeave instance.
         * 
         * **Example**:
         * ```kotlin
         * val trustWeave = TrustWeave.build {
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
         * @param name Optional name for the TrustWeave instance (default: "default")
         * @param registries Optional pre-configured registries (default: new empty registries)
         * @param block DSL block for configuring TrustWeave
         * @return A configured TrustWeave instance
         */
        suspend fun build(
            name: String = "default",
            registries: TrustWeaveRegistries = TrustWeaveRegistries(),
            block: TrustWeaveConfig.Builder.() -> Unit
        ): TrustWeave {
            val config = trustWeave(name, registries, block)
            val context = TrustWeaveContext(config)
            return TrustWeave(config, context)
        }
        
        /**
         * Create a TrustWeave instance from an existing TrustWeaveConfig.
         * 
         * Useful when you already have a configuration object and want to create
         * the facade wrapper.
         * 
         * @param config The existing TrustWeaveConfig
         * @return A TrustWeave instance wrapping the provided config
         */
        fun from(config: TrustWeaveConfig): TrustWeave {
            val context = TrustWeaveContext(config)
            return TrustWeave(config, context)
        }
    }
}

