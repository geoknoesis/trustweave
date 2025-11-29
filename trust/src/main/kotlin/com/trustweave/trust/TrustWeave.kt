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
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.core.types.Did as CoreDid
import com.trustweave.trust.types.Did
import com.trustweave.trust.types.VerificationResult
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
     * Simple overload for common case - verifies credential with default settings.
     *
     * **Example:**
     * ```kotlin
     * when (val result = trustWeave.verifyCredential(credential)) {
     *     is VerificationResult.Valid -> println("Valid!")
     *     is VerificationResult.Invalid.Expired -> println("Expired")
     *     // ... compiler ensures all cases handled
     * }
     * ```
     *
     * @param credential The verifiable credential to verify
     * @return Sealed result type with success or detailed failure information
     */
    suspend fun verifyCredential(credential: VerifiableCredential): VerificationResult {
        return context.verify {
            this.credential(credential)
        }
    }

    /**
     * Verify a verifiable credential using the configured TrustWeave instance.
     *
     * DSL-based overload for complex verification configurations.
     *
     * **Example:**
     * ```kotlin
     * val result = trustWeave.verify {
     *     credential(credential)
     *     checkRevocation()
     *     checkExpiration()
     * }
     * ```
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
     * Simple overload for common case - creates a DID with default method ("key").
     *
     * **Example:**
     * ```kotlin
     * val did = trustWeave.createDid()  // Uses default method "key"
     * ```
     *
     * @param method DID method to use (default: "key")
     * @return The created DID (type-safe)
     */
    suspend fun createDid(method: String = "key"): Did {
        return context.createDid {
            this.method(method)
        }
    }

    /**
     * Create a DID using the configured TrustWeave instance.
     *
     * DSL-based overload for complex configurations.
     *
     * **Example:**
     * ```kotlin
     * val did = trustWeave.createDid {
     *     method("key")
     *     algorithm("Ed25519")
     * }
     * ```
     *
     * @param block DSL block for configuring the DID
     * @return The created DID (type-safe)
     */
    suspend fun createDid(block: DidBuilder.() -> Unit): Did {
        return context.createDid(block)
    }

    /**
     * Resolve a DID to a DID document.
     *
     * Returns a sealed result type for exhaustive error handling.
     * This is the recommended way to resolve DIDs as it provides
     * type-safe error handling without exceptions.
     *
     * **Example:**
     * ```kotlin
     * when (val result = trustWeave.resolveDid("did:key:z6Mk...")) {
     *     is DidResolutionResult.Success -> {
     *         println("Resolved: ${result.document.id}")
     *     }
     *     is DidResolutionResult.Failure.NotFound -> {
     *         println("DID not found: ${result.did}")
     *     }
     *     is DidResolutionResult.Failure.InvalidFormat -> {
     *         println("Invalid format: ${result.reason}")
     *     }
     *     is DidResolutionResult.Failure.MethodNotRegistered -> {
     *         println("Method not registered: ${result.method}")
     *     }
     *     is DidResolutionResult.Failure.ResolutionError -> {
     *         println("Resolution error: ${result.reason}")
     *     }
     * }
     * ```
     *
     * @param did The DID string to resolve (can be String or Did type)
     * @param timeout Maximum time to wait for resolution (default: 30 seconds)
     * @return Sealed result type with success or detailed failure information
     */
    suspend fun resolveDid(
        did: String,
        timeout: Duration = 30.seconds
    ): DidResolutionResult {
        return withTimeout(timeout) {
            try {
                // Use registry directly for non-nullable result
                val result = config.registries.didRegistry.resolve(did)
                
                // Convert to sealed class if needed (registry returns sealed class already)
                when (result) {
                    is DidResolutionResult.Success -> result
                    is DidResolutionResult.Failure -> result
                }
            } catch (e: Exception) {
                // Handle exceptions from registry.resolve
                val methodName = if (did.startsWith("did:")) {
                    did.substringAfter("did:").substringBefore(":")
                } else {
                    did.substringBefore(":")
                }
                val availableMethods = config.registries.didRegistry.getAllMethodNames()
                DidResolutionResult.Failure.ResolutionError(
                    did = CoreDid(did),
                    reason = e.message ?: "Unknown resolution error",
                    cause = e
                )
            }
        }
    }

    /**
     * Resolve a DID to a DID document (type-safe overload).
     *
     * @param did Type-safe Did identifier
     * @param timeout Maximum time to wait for resolution (default: 30 seconds)
     * @return Sealed result type with success or detailed failure information
     */
    suspend fun resolveDid(
        did: Did,
        timeout: Duration = 30.seconds
    ): DidResolutionResult {
        return resolveDid(did.value, timeout)
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

    /**
     * Revoke a credential using the configured TrustWeave instance.
     *
     * @param block DSL block for specifying revocation parameters
     * @return true if revocation succeeded
     */
    suspend fun revoke(block: com.trustweave.trust.dsl.credential.RevocationBuilder.() -> Unit): Boolean {
        return config.revoke(block)
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

