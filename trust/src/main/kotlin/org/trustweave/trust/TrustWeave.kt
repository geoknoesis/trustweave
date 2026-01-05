package org.trustweave.trust

import org.trustweave.anchor.services.BlockchainService
import org.trustweave.contract.ContractService
import org.trustweave.trust.dsl.*
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.trust.dsl.credential.IssuanceBuilder
import org.trustweave.trust.dsl.credential.VerificationBuilder
import org.trustweave.wallet.Wallet
import org.trustweave.trust.dsl.wallet.WalletBuilder
import org.trustweave.trust.dsl.did.DidBuilder
import org.trustweave.trust.dsl.did.DidDocumentBuilder
import org.trustweave.trust.dsl.did.DelegationBuilder
import org.trustweave.trust.dsl.KeyRotationBuilder
import org.trustweave.did.model.DidDocument
import org.trustweave.did.verifier.DelegationChainResult
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.identifiers.Did
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.model.CredentialType
import org.trustweave.trust.types.VerificationResult
import org.trustweave.trust.types.IssuerIdentity
import org.trustweave.trust.types.VerifierIdentity
import org.trustweave.trust.types.HolderIdentity
import org.trustweave.trust.types.WalletCreationResult
import org.trustweave.trust.types.TrustPath
import org.trustweave.wallet.exception.WalletException
import java.io.Closeable
import org.trustweave.revocation.services.StatusListRegistryFactory
import org.trustweave.trust.dsl.credential.RevocationBuilder
import org.trustweave.trust.services.TrustRegistryFactory
import org.trustweave.wallet.services.WalletFactory
import org.trustweave.credential.CredentialService
import org.trustweave.did.DidMethod
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.services.KmsService
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.schema.SchemaRegistry
import org.trustweave.trust.TrustRegistry
import org.trustweave.credential.model.ProofType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
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
 *             "degree" to "Bachelor of Science"
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
    private val config: TrustWeaveConfig
) : DidResolver, Closeable {
    private val logger = LoggerFactory.getLogger(TrustWeave::class.java)
    
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
        BlockchainService(config.blockchainRegistry)
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
            credentialService = null, // CredentialService from credential-api (set via TrustWeaveConfig)
            blockchainRegistry = config.blockchainRegistry
        )
    }

    // ========== Internal Configuration Access Methods ==========
    
    /**
     * Get a DID method by name.
     */
    internal fun getDidMethod(name: String): DidMethod? {
        val method = config.didRegistry.get(name) as? DidMethod
        logger.debug("Getting DID method: name={}, found={}", name, method != null)
        return method
    }

    /**
     * Get a DID resolver for delegation operations.
     * This returns the native DID resolver interface used by did:core.
     */
    internal fun getDidResolver(): DidResolver {
        return DidResolver { did ->
            runCatching { config.didRegistry.resolve(did.value) }
                .getOrElse { null } ?: DidResolutionResult.Failure.ResolutionError(
                    did = did,
                    reason = "DID resolution failed"
                )
        }
    }

    /**
     * Get wallet factory.
     */
    internal fun getWalletFactory(): WalletFactory? {
        return config.walletFactory
    }

    /**
     * Get an anchor client by chain ID.
     *
     * @param chainId The blockchain chain identifier (e.g., "algorand:testnet")
     * @return The blockchain anchor client, or null if not registered
     */
    internal fun getAnchorClient(chainId: String): BlockchainAnchorClient? {
        return config.blockchainRegistry.get(chainId)
    }

    /**
     * Get the credential issuer/service.
     */
    internal fun getIssuer() = config.issuer // CredentialService from credential-api

    /**
     * Get the revocation manager.
     */
    internal fun getRevocationManager(): CredentialRevocationManager? {
        return config.revocationManager
    }

    /**
     * Get the default proof type.
     */
    internal fun getDefaultProofType(): ProofType {
        return config.credentialConfig.defaultProofType
    }

    /**
     * Get the DID registry.
     *
     * @return The DID method registry
     */
    internal fun getDidRegistry(): DidMethodRegistry {
        return config.didRegistry
    }

    /**
     * Get the trust registry.
     */
    internal fun getTrustRegistry(): TrustRegistry? {
        return config.trustRegistry
    }

    /**
     * Get the KMS service adapter.
     */
    internal fun getKmsService(): KmsService? {
        return config.kmsService
    }

    /**
     * Get KMS from TrustWeave.
     */
    internal fun getKms(): KeyManagementService? {
        return config.kms as? KeyManagementService
    }

    /**
     * Get the schema registry.
     */
    internal fun getSchemaRegistry(): SchemaRegistry? {
        return org.trustweave.credential.schema.SchemaRegistries.default()
    }

    /**
     * Get the configured I/O dispatcher.
     * 
     * Returns the dispatcher configured in TrustWeaveConfig, or Dispatchers.IO as default.
     */
    private fun getIoDispatcher() = config.ioDispatcher

    // ========== Credential Operations ==========

    /**
     * Issue a verifiable credential using the configured TrustWeave instance.
     *
     * Returns a sealed result type for exhaustive error handling.
     * This is the recommended way to issue credentials as it provides
     * type-safe error handling without exceptions.
     *
     * **Example:**
     * ```kotlin
     * when (val result = trustWeave.issue {
     *     credential { ... }
     *     signedBy(issuerDid, keyId)
     * }) {
     *     is IssuanceResult.Success -> {
     *         println("Issued: ${result.credential.id}")
     *     }
     *     is IssuanceResult.Failure.IssuerResolutionFailed -> {
     *         println("Failed to resolve issuer: ${result.issuerDid}")
     *     }
     *     is IssuanceResult.Failure.KeyNotFound -> {
     *         println("Key not found: ${result.keyId}")
     *     }
     *     // ... compiler ensures all cases handled
     * }
     * ```
     *
     * @param timeout Maximum time to wait for issuance (default: 30 seconds)
     * @param block DSL block for building the credential and specifying issuance parameters
     * @return Sealed result type with success or detailed failure information
     */
    suspend fun issue(
        timeout: Duration = 30.seconds,
        block: IssuanceBuilder.() -> Unit
    ): IssuanceResult {
        return withTimeout(timeout) {
            withContext(getIoDispatcher()) {
                val issuerAny = getIssuer()
                val credentialService = issuerAny as? CredentialService
                    ?: return@withContext IssuanceResult.Failure.AdapterNotReady(
                        format = org.trustweave.credential.format.ProofSuiteId.VC_LD,
                        reason = "CredentialService is not available. Issuer must be a CredentialService instance."
                    )
                val didResolver = config.didResolver
                val defaultProofType = getDefaultProofType()
                val defaultProofSuite = when (defaultProofType) {
                    is ProofType.Ed25519Signature2020 -> org.trustweave.credential.format.ProofSuiteId.VC_LD
                    is ProofType.JsonWebSignature2020 -> org.trustweave.credential.format.ProofSuiteId.VC_JWT
                    is ProofType.BbsBlsSignature2020 -> org.trustweave.credential.format.ProofSuiteId.SD_JWT_VC
                    is ProofType.Custom -> org.trustweave.credential.format.ProofSuiteId.VC_LD
                }
                val builder = IssuanceBuilder(
                    credentialService = credentialService,
                    revocationManager = getRevocationManager() as? CredentialRevocationManager,
                    defaultProofSuite = defaultProofSuite,
                    ioDispatcher = getIoDispatcher(),
                    didResolver = didResolver
                )
                builder.block()
                builder.build()
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
            val dispatcher = getIoDispatcher()
            val credentialService = getIssuer() as? CredentialService
                ?: throw IllegalStateException("CredentialService is not available. Configure it in TrustWeave.build { ... }")
            val builder = VerificationBuilder(
                credentialService = credentialService,
                ioDispatcher = dispatcher
            )
            builder.block()
            val credential = builder.credential ?: throw IllegalStateException("Credential is required")
            val result = builder.build()
            VerificationResult.from(credential, result)
        }
    }

    // ========== DID Operations ==========

    /**
     * Create a DID using the configured TrustWeave instance.
     *
     * Returns a sealed result type for exhaustive error handling.
     * This is the recommended way to create DIDs as it provides
     * type-safe error handling without exceptions.
     *
     * **Example:**
     * ```kotlin
     * // With configuration block
     * when (val result = trustWeave.createDid(method = "key") {
     *     algorithm("Ed25519")
     * }) {
     *     is DidCreationResult.Success -> {
     *         println("Created: ${result.did.value}")
     *         println("Document: ${result.document.id}")
     *     }
     *     is DidCreationResult.Failure.MethodNotRegistered -> {
     *         println("Method not registered: ${result.method}")
     *         println("Available: ${result.availableMethods}")
     *     }
     *     is DidCreationResult.Failure.KeyGenerationFailed -> {
     *         println("Key generation failed: ${result.reason}")
     *     }
     *     // ... compiler ensures all cases handled
     * }
     * 
     * // With defaults (uses "key" method with default algorithm)
     * val result = trustWeave.createDid()
     * ```
     *
     * @param method DID method to use (default: first registered method from config, or "key")
     * @param timeout Maximum time to wait for DID creation (default: 10 seconds)
     * @param block Optional DSL block for configuring the DID (default: empty block)
     * @return Sealed result type with success or detailed failure information
     */
    suspend fun createDid(
        method: String? = null,
        timeout: Duration = 10.seconds,
        block: DidBuilder.() -> Unit = {}
    ): DidCreationResult {
        val resolvedMethod = method 
            ?: config.defaultDidMethod 
            ?: "key"
        return withTimeout(timeout) {
            withContext(getIoDispatcher()) {
                val builder = DidBuilder(this@TrustWeave, getIoDispatcher())
                builder.method(resolvedMethod)
                builder.block()
                builder.build()
            }
        }
    }

    /**
     * Create a DID and automatically extract the key ID.
     *
     * This is a convenience method that combines DID creation with key ID extraction,
     * eliminating the need for manual resolution and extraction.
     *
     * **Example:**
     * ```kotlin
     * val (did, keyId) = trustWeave.createDidWithKey().getOrFail()
     * // Now you can use both did and keyId directly
     * ```
     *
     * @param method DID method to use (default: first registered method from config, or "key")
     * @param timeout Maximum time to wait for DID creation (default: 10 seconds)
     * @param block Optional DSL block for configuring the DID (default: empty block)
     * @return Result containing Pair<Did, String> where String is the key ID, or failure
     */
    suspend fun createDidWithKey(
        method: String? = null,
        timeout: Duration = 10.seconds,
        block: DidBuilder.() -> Unit = {}
    ): Result<Pair<Did, String>> {
        return withContext(getIoDispatcher()) {
            when (val result = createDid(method, timeout, block)) {
                is DidCreationResult.Success -> {
                    val keyId = getKeyId(result.did)
                    Result.success(result.did to keyId)
                }
                is DidCreationResult.Failure.MethodNotRegistered -> {
                    Result.failure(
                        IllegalStateException(
                            "DID method '${result.method}' not registered. " +
                            "Available methods: ${result.availableMethods.joinToString()}"
                        )
                    )
                }
                is DidCreationResult.Failure.KeyGenerationFailed -> {
                    Result.failure(
                        IllegalStateException("Key generation failed: ${result.reason}")
                    )
                }
                is DidCreationResult.Failure.DocumentCreationFailed -> {
                    Result.failure(
                        IllegalStateException("Document creation failed: ${result.reason}")
                    )
                }
                is DidCreationResult.Failure.InvalidConfiguration -> {
                    Result.failure(
                        IllegalStateException("Invalid configuration: ${result.reason}")
                    )
                }
                is DidCreationResult.Failure.Other -> {
                    Result.failure(
                        IllegalStateException(
                            "DID creation failed: ${result.reason}",
                            result.cause
                        )
                    )
                }
            }
        }
    }

    /**
     * Get the first key ID from a DID document.
     *
     * Simplifies the common pattern of resolving a DID and extracting its key ID.
     *
     * **Example:**
     * ```kotlin
     * val did = trustWeave.createDid().getOrFail()
     * val keyId = trustWeave.getKeyId(did)
     * ```
     *
     * @param did The DID to extract the key ID from
     * @return The key ID string (fragment part without the '#')
     * @throws IllegalStateException if DID resolution fails or no verification method found
     */
    suspend fun getKeyId(did: Did): String {
        val resolutionResult = resolveDid(did)
        val document = when (resolutionResult) {
            is DidResolutionResult.Success -> resolutionResult.document
            else -> throw IllegalStateException("Failed to resolve DID: ${did.value}")
        }
        return document.verificationMethod.firstOrNull()?.let { vm ->
            vm.id.value.substringAfter("#").takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("No key ID found in verification method: ${vm.id.value}")
        } ?: throw IllegalStateException("No verification method found for DID: ${did.value}")
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
            withContext(getIoDispatcher()) {
                try {
                    // Use registry directly for non-nullable result
                    val result = config.didRegistry.resolve(did)
                    
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
                    val availableMethods = config.didRegistry.getAllMethodNames()
                    DidResolutionResult.Failure.ResolutionError(
                        did = Did(did),
                        reason = e.message ?: "Unknown resolution error",
                        cause = e
                    )
                }
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
     * Implement DidResolver interface.
     * 
     * This allows TrustWeave to be used directly wherever a DidResolver is needed,
     * such as when creating CredentialService instances.
     * 
     * **Example:**
     * ```kotlin
     * val trustweave = TrustWeave.build { ... }
     * val credentialService = CredentialServices.createCredentialService(
     *     kms = kms,
     *     didResolver = trustweave  // TrustWeave IS a DidResolver!
     * )
     * ```
     */
    override suspend fun resolve(did: Did): DidResolutionResult {
        return resolveDid(did)
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
            withContext(getIoDispatcher()) {
                val builder = DidDocumentBuilder(this@TrustWeave)
                builder.block()
                builder.update()
            }
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
            withContext(getIoDispatcher()) {
                val builder = DelegationBuilder(this@TrustWeave)
                builder.block()
                builder.verify()
            }
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
            withContext(getIoDispatcher()) {
                val kms = getKms() ?: throw IllegalStateException(
                    "KMS is not configured. Configure it in TrustWeave.build { keys { provider(\"inMemory\") } }"
                )
                val kmsService = getKmsService() ?: throw IllegalStateException(
                    "KmsService is not configured. Configure it in TrustWeave.build { keys { provider(\"inMemory\") } }"
                )
                val builder = KeyRotationBuilder(this@TrustWeave, kms, kmsService, config.ioDispatcher)
                builder.block()
                builder.rotate()
            }
        }
    }

    // ========== Wallet Operations ==========

    /**
     * Create a wallet using the configured TrustWeave instance.
     *
     * @param block DSL block for configuring the wallet
     * @return Sealed result type with success or detailed failure information
     */
    suspend fun wallet(block: WalletBuilder.() -> Unit): WalletCreationResult {
        return withContext(getIoDispatcher()) {
            try {
                val builder = WalletBuilder(this@TrustWeave)
                builder.block()
                val wallet = builder.build()
                WalletCreationResult.Success(wallet)
            } catch (e: WalletException.WalletFactoryNotConfigured) {
                WalletCreationResult.Failure.FactoryNotConfigured(e.reason)
            } catch (e: WalletException.InvalidHolderDid) {
                WalletCreationResult.Failure.InvalidHolderDid(
                    holderDid = e.holderDid,
                    reason = e.reason
                )
            } catch (e: WalletException.WalletCreationFailed) {
                WalletCreationResult.Failure.Other(
                    reason = e.reason,
                    cause = e
                )
            } catch (e: Throwable) {
                WalletCreationResult.Failure.Other(
                    reason = e.message ?: "Wallet creation failed",
                    cause = e
                )
            }
        }
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
    ): TrustPath {
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
        val registry = getTrustRegistry() ?: throw IllegalStateException(
            "Trust registry is not configured. Configure it in trustWeave { trust { provider(\"inMemory\") } }"
        )
        val builder = TrustBuilder(registry)
        builder.block()
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
        block: RevocationBuilder.() -> Unit
    ): Boolean {
        return withTimeout(timeout) {
            val revocationManager = getRevocationManager()
            val builder = RevocationBuilder(revocationManager)
            builder.block()
            withContext(config.ioDispatcher) {
                builder.revoke()
            }
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
         * val trustWeave = TrustWeave.inMemory()
         * val did = trustWeave.createDid()
         * ```
         *
         * **Example with blockchain anchor**:
         * ```kotlin
         * val trustWeave = TrustWeave.inMemory(chainId = "algorand:testnet")
         * ```
         *
         * **Example with custom dispatcher for testing**:
         * ```kotlin
         * val testDispatcher = Dispatchers.Unconfined
         * val trustWeave = TrustWeave.inMemory(dispatcher = testDispatcher)
         * ```
         *
         * Note: KMS, DID methods, and Anchor clients are auto-discovered via SPI.
         * Only factories for Wallet, TrustRegistry, and StatusListRegistry are needed if required.
         *
         * @param chainId Optional blockchain chain ID for anchoring (e.g., "algorand:testnet")
         * @param dispatcher Coroutine dispatcher for I/O operations (defaults to Dispatchers.IO)
         * @param statusListRegistryFactory Status list registry factory (optional)
         * @param trustRegistryFactory Trust registry factory (optional)
         * @param walletFactory Wallet factory (optional)
         * @return A configured TrustWeave instance with in-memory providers
         */
        suspend fun inMemory(
            chainId: String? = null,
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            statusListRegistryFactory: StatusListRegistryFactory? = null,
            trustRegistryFactory: TrustRegistryFactory? = null,
            walletFactory: WalletFactory? = null
        ): TrustWeave {
            return build {
                dispatcher(dispatcher)
                factories(
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
                chainId?.let {
                    anchor {
                        chain(it) { inMemory() }
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
         *     keys {
         *         provider("inMemory")  // Auto-discovered via SPI
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
        /**
         * Build a TrustWeave instance with the given configuration.
         *
         * Registries are automatically created with SPI auto-registration.
         * DID resolver and CredentialService are auto-created from the registry and KMS.
         *
         * @param name Optional name for this TrustWeave instance (default: "default")
         * @param block DSL block for configuring TrustWeave
         * @return A configured TrustWeave instance
         */
        suspend fun build(
            name: String = "default",
            block: TrustWeaveConfig.Builder.() -> Unit
        ): TrustWeave {
            val config = trustWeave(name, block)
            return TrustWeave(config)
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
            return TrustWeave(config)
        }
    }

    /**
     * Close and cleanup resources held by this TrustWeave instance.
     *
     * This method should be called when the TrustWeave instance is no longer needed
     * to ensure proper cleanup of resources like KMS connections, blockchain clients, etc.
     *
     * **Example:**
     * ```kotlin
     * val trustWeave = TrustWeave.build { ... }
     * try {
     *     // Use trustWeave
     * } finally {
     *     trustWeave.close()
     * }
     * ```
     */
    override fun close() {
        logger.debug("Closing TrustWeave instance: ${config.name}")
        
        // Close KMS if it implements Closeable
        try {
            (config.kms as? Closeable)?.close()
        } catch (e: Exception) {
            logger.warn("Error closing KMS: ${e.message}", e)
        }
        
        // Close blockchain clients
        try {
            config.blockchainRegistry.getAllClients().values
                .filterIsInstance<Closeable>()
                .forEach { client ->
                    try {
                        client.close()
                    } catch (e: Exception) {
                        logger.warn("Error closing blockchain client: ${e.message}", e)
                    }
                }
        } catch (e: Exception) {
            logger.warn("Error closing blockchain clients: ${e.message}", e)
        }
        
        // Close KMS service if it implements Closeable
        try {
            (config.kmsService as? Closeable)?.close()
        } catch (e: Exception) {
            logger.warn("Error closing KMS service: ${e.message}", e)
        }
        
        logger.debug("TrustWeave instance closed: ${config.name}")
    }
}

