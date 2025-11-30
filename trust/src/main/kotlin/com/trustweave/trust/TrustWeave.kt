package com.trustweave.trust

import com.trustweave.anchor.services.BlockchainService
import com.trustweave.contract.ContractService
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
import com.trustweave.trust.types.IssuerIdentity
import com.trustweave.trust.types.VerifierIdentity
import com.trustweave.trust.types.HolderIdentity
import com.trustweave.trust.types.CredentialType
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
 * **Concurrency Model:**
 * - All operations are `suspend` functions and use structured concurrency
 * - I/O-bound operations use configurable dispatchers (default: [Dispatchers.IO])
 * - All operations support cancellation via coroutine cancellation
 * - Timeout support is built-in for all I/O operations
 * - Operations are non-blocking and can be safely called from coroutine scopes
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
 *     signedBy(issuerDid = "did:key:university", keyId = "key-1")
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

    /**
     * Blockchain anchoring service.
     *
     * Provides methods for anchoring data to blockchains and reading anchored data.
     *
     * **Example:**
     * ```kotlin
     * val anchor = trustWeave.blockchains.anchor(
     *     data = myData,
     *     serializer = MyData.serializer(),
     *     chainId = "algorand:testnet"
     * )
     * val data = trustWeave.blockchains.read<MyData>(
     *     ref = anchorRef,
     *     serializer = MyData.serializer()
     * )
     * ```
     */
    val blockchains: BlockchainService by lazy {
        BlockchainService(config.registries.blockchainRegistry)
    }

    /**
     * Smart contract operations service.
     *
     * Provides methods for creating, binding, and executing smart contracts.
     *
     * **Example:**
     * ```kotlin
     * val contract = trustWeave.contracts.draft(request)
     * val bound = trustWeave.contracts.bindContract(
     *     contractId = contract.id,
     *     issuerDid = issuerDid,
     *     issuerKeyId = issuerKeyId,
     *     chainId = "algorand:testnet"
     * )
     * ```
     */
    val contracts: ContractService by lazy {
        ContractService(
            credentialRegistry = config.registries.credentialRegistry,
            blockchainRegistry = config.registries.blockchainRegistry
        )
    }

    // ========== Credential Operations ==========

    /**
     * Issue a verifiable credential using the configured TrustWeave instance.
     *
     * @param timeout Maximum time to wait for issuance (default: 30 seconds)
     * @param block DSL block for building the credential and specifying issuance parameters
     * @return The issued verifiable credential
     */
    suspend fun issue(
        timeout: Duration = 30.seconds,
        block: IssuanceBuilder.() -> Unit
    ): VerifiableCredential {
        return withTimeout(timeout) {
            context.issue(block)
        }
    }

    /**
     * Issue a verifiable credential using simple parameters (non-DSL overload).
     *
     * Simple overload for common case - issues a credential with minimal configuration.
     * For complex cases, use the DSL-based `issue { }` method.
     *
     * **Example:**
     * ```kotlin
     * val credential = trustWeave.issueCredential(
     *     issuer = "did:key:university",
     *     keyId = "key-1",
     *     subject = mapOf(
     *         "id" to "did:key:student",
     *         "degree" to mapOf(
     *             "type" to "BachelorDegree",
     *             "name" to "Bachelor of Science"
     *         )
     *     ),
     *     credentialType = "DegreeCredential",
     *     timeout = 30.seconds
     * )
     * ```
     *
     * @param issuer The issuer DID
     * @param keyId The key ID for signing (can be fragment like "key-1" or full like "did:key:...#key-1")
     * @param subject The credential subject as a map of properties
     * @param credentialType The credential type (default: "VerifiableCredential")
     * @param credentialId Optional credential ID (auto-generated if not provided)
     * @param timeout Maximum time to wait for issuance (default: 30 seconds)
     * @return The issued verifiable credential
     */
    suspend fun issueCredential(
        issuer: String,
        keyId: String,
        subject: Map<String, Any>,
        credentialType: String = "VerifiableCredential",
        credentialId: String? = null,
        timeout: Duration = 30.seconds
    ): VerifiableCredential {
        return withTimeout(timeout) {
            context.issue {
                credential {
                    if (credentialId != null) {
                        id(credentialId)
                    }
                    type(credentialType)
                    issuer(issuer)
                    // Convert subject map to JSON structure
                    val subjectId = subject["id"] as? String
                    if (subjectId != null) {
                        this.subject {
                            id(subjectId)
                            // Add other properties
                            subject.filterKeys { it != "id" }.forEach { (key, value) ->
                                when (value) {
                                    is Map<*, *> -> {
                                        // Nested object
                                        key {
                                            (value as Map<String, Any>).forEach { (nestedKey, nestedValue) ->
                                                nestedKey to nestedValue
                                            }
                                        }
                                    }
                                    else -> {
                                        key to value
                                    }
                                }
                            }
                        }
                    } else {
                        this.subject {
                            subject.forEach { (key, value) ->
                                when (value) {
                                    is Map<*, *> -> {
                                        key {
                                            (value as Map<String, Any>).forEach { (nestedKey, nestedValue) ->
                                                nestedKey to nestedValue
                                            }
                                        }
                                    }
                                    else -> {
                                        key to value
                                    }
                                }
                            }
                        }
                    }
                    issued(java.time.Instant.now())
                }
                signedBy(issuerDid = issuer, keyId = keyId)
            }
        }
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
     * @param timeout Maximum time to wait for verification (default: 10 seconds)
     * @return Sealed result type with success or detailed failure information
     */
    suspend fun verifyCredential(
        credential: VerifiableCredential,
        timeout: Duration = 10.seconds
    ): VerificationResult {
        return withTimeout(timeout) {
            context.verify {
                this.credential(credential)
            }
        }
    }

    /**
     * Verify a verifiable credential using the configured TrustWeave instance.
     *
     * DSL-based overload for complex verification configurations.
     *
     * **Example:**
     * ```kotlin
     * val result = trustWeave.verify(timeout = 30.seconds) {
     *     credential(credential)
     *     checkRevocation()
     *     checkExpiration()
     * }
     * ```
     *
     * @param timeout Maximum time to wait for verification (default: 10 seconds)
     * @param block DSL block for specifying verification parameters
     * @return The credential verification result (sealed type for exhaustive error handling)
     */
    suspend fun verify(
        timeout: Duration = 10.seconds,
        block: VerificationBuilder.() -> Unit
    ): VerificationResult {
        return withTimeout(timeout) {
            context.verify(block)
        }
    }

    // ========== DID Operations ==========

    /**
     * Create a DID using the configured TrustWeave instance.
     *
     * Single method that supports both simple and complex configurations.
     *
     * **Simple case:**
     * ```kotlin
     * val did = trustWeave.createDid()  // Uses default method "key"
     * val did = trustWeave.createDid(method = "key")
     * ```
     *
     * **Complex case:**
     * ```kotlin
     * val did = trustWeave.createDid(method = "key", timeout = 10.seconds) {
     *     algorithm("Ed25519")
     *     purpose(KeyPurpose.AUTHENTICATION)
     * }
     * ```
     *
     * @param method DID method to use (default: "key")
     * @param timeout Maximum time to wait for DID creation (default: 10 seconds)
     * @param configure Optional DSL block for configuring the DID
     * @return The created DID (type-safe)
     */
    suspend fun createDid(
        method: String = "key",
        timeout: Duration = 10.seconds,
        configure: (DidBuilder.() -> Unit)? = null
    ): Did {
        return withTimeout(timeout) {
            if (configure != null) {
                context.createDid {
                    this.method(method)
                    configure()
                }
            } else {
                context.createDid {
                    this.method(method)
                }
            }
        }
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
     * @param timeout Maximum time to wait for update (default: 30 seconds)
     * @param block DSL block for specifying the update
     * @return The updated DID document
     */
    suspend fun updateDid(
        timeout: Duration = 30.seconds,
        block: DidDocumentBuilder.() -> Unit
    ): DidDocument {
        return withTimeout(timeout) {
            context.updateDid(block)
        }
    }

    /**
     * Delegate authority to another DID using the configured TrustWeave instance.
     *
     * @param timeout Maximum time to wait for delegation (default: 30 seconds)
     * @param block DSL block for specifying the delegation
     * @return The delegation chain result
     */
    suspend fun delegate(
        timeout: Duration = 30.seconds,
        block: suspend DelegationBuilder.() -> Unit
    ): DelegationChainResult {
        return withTimeout(timeout) {
            context.delegate(block)
        }
    }

    /**
     * Rotate a key in a DID document using the configured TrustWeave instance.
     *
     * @param timeout Maximum time to wait for key rotation (default: 30 seconds)
     * @param block DSL block for specifying the key rotation
     * @return The updated DID document with rotated key
     */
    suspend fun rotateKey(
        timeout: Duration = 30.seconds,
        block: KeyRotationBuilder.() -> Unit
    ): DidDocument {
        return withTimeout(timeout) {
            context.rotateKey(block)
        }
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
     * Find a trust path between verifier and issuer (first-class type).
     *
     * Returns a sealed TrustPath type that makes trust relationships explicit.
     *
     * **Example**:
     * ```kotlin
     * when (val path = trustWeave.findTrustPath(verifier, issuer, timeout = 10.seconds)) {
     *     is TrustPath.Verified -> {
     *         println("Trusted via path: ${path.anchors.map { it.did }}")
     *         println("Path length: ${path.length}")
     *         println("Trust score: ${path.trustScore}")
     *     }
     *     is TrustPath.NotFound -> {
     *         println("No trust path found: ${path.reason}")
     *     }
     * }
     * ```
     *
     * @param verifier The verifier identity
     * @param issuer The issuer identity
     * @param timeout Maximum time to wait for trust path discovery (default: 10 seconds)
     * @return TrustPath.Verified if a path exists, TrustPath.NotFound otherwise
     * @throws IllegalStateException if trust registry is not configured
     */
    suspend fun findTrustPath(
        verifier: VerifierIdentity,
        issuer: IssuerIdentity,
        timeout: Duration = 10.seconds
    ): com.trustweave.trust.types.TrustPath {
        return withTimeout(timeout) {
            val registry = config.trustRegistry
                ?: throw IllegalStateException("Trust registry is not configured. Configure it in TrustWeave.build { trust { ... } }")
            
            registry.findTrustPath(verifier, issuer)
        }
    }

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
     *     val path = findTrustPath(VerifierIdentity(Did("did:key:verifier")), IssuerIdentity.from("did:key:issuer", "key-1"))
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
     * @param timeout Maximum time to wait for revocation (default: 10 seconds)
     * @param block DSL block for specifying revocation parameters
     * @return true if revocation succeeded
     */
    suspend fun revoke(
        timeout: Duration = 10.seconds,
        block: com.trustweave.trust.dsl.credential.RevocationBuilder.() -> Unit
    ): Boolean {
        return withTimeout(timeout) {
            context.revoke(block)
        }
    }


    companion object {
        /**
         * Create a TrustWeave instance with in-memory providers (for testing).
         *
         * Simple factory for common testing scenarios. Uses in-memory providers
         * for all services with sensible defaults.
         *
         * **Example**:
         * ```kotlin
         * val trustWeave = TrustWeave.inMemory(
         *     kmsFactory = TestkitKmsFactory(),
         *     didMethodFactory = TestkitDidMethodFactory()
         * )
         * val did = trustWeave.createDid()
         * ```
         *
         * **Example with custom dispatcher for testing**:
         * ```kotlin
         * val testDispatcher = Dispatchers.Unconfined
         * val trustWeave = TrustWeave.inMemory(
         *     dispatcher = testDispatcher,
         *     kmsFactory = TestkitKmsFactory(),
         *     didMethodFactory = TestkitDidMethodFactory()
         * )
         * ```
         *
         * @param dispatcher Coroutine dispatcher for I/O operations (defaults to Dispatchers.IO)
         * @param kmsFactory KMS factory (required)
         * @param didMethodFactory DID method factory (required)
         * @param anchorClientFactory Blockchain anchor client factory (optional)
         * @param statusListManagerFactory Status list manager factory (optional)
         * @param trustRegistryFactory Trust registry factory (optional)
         * @param walletFactory Wallet factory (optional)
         * @return A configured TrustWeave instance with in-memory providers
         */
        suspend fun inMemory(
            dispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
            kmsFactory: com.trustweave.kms.services.KmsFactory,
            didMethodFactory: com.trustweave.did.services.DidMethodFactory,
            anchorClientFactory: com.trustweave.anchor.services.BlockchainAnchorClientFactory? = null,
            statusListRegistryFactory: com.trustweave.revocation.services.StatusListRegistryFactory? = null,
            trustRegistryFactory: com.trustweave.trust.services.TrustRegistryFactory? = null,
            walletFactory: com.trustweave.wallet.services.WalletFactory? = null
        ): TrustWeave {
            return build {
                dispatcher(dispatcher)
                factories(
                    kmsFactory = kmsFactory,
                    didMethodFactory = didMethodFactory,
                    anchorClientFactory = anchorClientFactory,
                    statusListRegistryFactory = statusListRegistryFactory,
                    trustRegistryFactory = trustRegistryFactory,
                    walletFactory = walletFactory
                )
                keys {
                    provider("inMemory")
                    algorithm("Ed25519")
                }
                did {
                    method("key") {
                        algorithm("Ed25519")
                    }
                }
                trust {
                    provider("inMemory")
                }
            }
        }

        /**
         * Build a TrustWeave instance with the provided configuration.
         *
         * This is the recommended way to create a TrustWeave instance.
         *
         * **Example**:
         * ```kotlin
         * val trustWeave = TrustWeave.build {
         *     factories(
         *         kmsFactory = TestkitKmsFactory(),
         *         didMethodFactory = TestkitDidMethodFactory()
         *     )
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

