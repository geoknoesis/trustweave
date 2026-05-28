package org.trustweave.trust

import org.trustweave.anchor.services.BlockchainService
import org.trustweave.contract.DefaultSmartContractService
import org.trustweave.contract.SmartContractService
import org.trustweave.trust.context.DidDslContext
import org.trustweave.trust.context.WalletDslContext
import org.trustweave.trust.dsl.*
import org.trustweave.trust.dsl.credential.IssuanceBuilder
import org.trustweave.trust.dsl.credential.RevocationBuilder
import org.trustweave.trust.dsl.credential.VerificationBuilder
import org.trustweave.trust.dsl.KeyRotationBuilder
import org.trustweave.trust.dsl.did.DelegationBuilder
import org.trustweave.trust.dsl.did.DidBuilder
import org.trustweave.trust.dsl.did.DidDocumentBuilder
import org.trustweave.trust.dsl.wallet.WalletBuilder
import org.trustweave.did.DidMethod
import org.trustweave.did.model.DidDocument
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.verifier.DelegationChainResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.identifiers.Did
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.trust.types.DidCreationWithKeyResult
import org.trustweave.trust.types.DidResult
import org.trustweave.trust.types.IssuerIdentity
import org.trustweave.trust.types.VerifierIdentity
import org.trustweave.trust.types.WalletCreationResult
import org.trustweave.trust.types.TrustPath
import org.trustweave.trust.internal.placeholderCredentialForUnconfiguredVerification
import java.io.Closeable
import org.trustweave.trust.services.CredentialIssuanceService
import org.trustweave.trust.services.CredentialVerificationService
import org.trustweave.trust.services.CredentialRevocationService
import org.trustweave.trust.services.DidManagementService
import org.trustweave.trust.services.WalletManagementService
import org.trustweave.trust.services.TrustManagementService
import org.trustweave.wallet.services.WalletFactory
import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.services.KmsService
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.schema.SchemaRegistry
import org.trustweave.credential.model.ProofType
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * TrustWeave - The main facade for trust and identity operations.
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
 * // Quick start (in-memory, did:key)
 * val trustWeave = TrustWeave.quickStart()
 *
 * // Or custom configuration
 * val trustWeave = TrustWeave.build {
 *     keys { provider("inMemory"); algorithm("Ed25519") }
 *     did { method("key") { algorithm("Ed25519") } }
 *     trust { provider("inMemory") }
 * }
 *
 * val issuerDid = trustWeave.createDid().getOrThrowDid()
 *
 * // Issue credentials (key ID auto-extracted via signedBy(issuerDid))
 * val credential = trustWeave.issue {
 *     credential {
 *         type("EducationCredential")
 *         issuer(issuerDid)
 *         subject("did:key:student") { "degree" to "Bachelor of Science" }
 *     }
 *     signedBy(issuerDid)
 * }.getOrThrow()
 *
 * // Verify (simple overload)
 * val result = trustWeave.verify(credential)
 *
 * // Or use DSL for advanced options
 * val result = trustWeave.verify {
 *     credential(credential)
 *     checkRevocation()
 *     validateSchema("https://example.com/schemas/degree.json")
 * }
 * ```
 */
class TrustWeave internal constructor(
    private val config: TrustWeaveConfig
) : DidResolver, Closeable, DidDslContext, WalletDslContext {
    private val logger = LoggerFactory.getLogger(TrustWeave::class.java)
    
    /**
     * The underlying configuration.
     *
     * Provides access to lower-level configuration details if needed.
     * Most operations should be done through the TrustWeave facade methods.
     */
    override val configuration: TrustWeaveConfig
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
    val contracts: SmartContractService by lazy {
        config.smartContractService ?: DefaultSmartContractService(
            credentialService = config.credentialService,
            blockchainRegistry = config.blockchainRegistry
        )
    }

    // ========== Domain Services (thin composer pattern) ==========

    private val issuanceService: CredentialIssuanceService? by lazy {
        config.credentialService?.let { cs ->
            CredentialIssuanceService(
                credentialService = cs,
                revocationManager = config.revocationManager,
                didResolver = config.didResolver,
                defaultProofType = config.credentialConfig.defaultProofType,
                ioDispatcher = config.ioDispatcher
            )
        }
    }

    private val verificationService: CredentialVerificationService? by lazy {
        config.credentialService?.let { cs ->
            CredentialVerificationService(
                credentialService = cs,
                ioDispatcher = config.ioDispatcher
            )
        }
    }

    private val revocationService: CredentialRevocationService by lazy {
        CredentialRevocationService(
            revocationManager = config.revocationManager,
            ioDispatcher = config.ioDispatcher
        )
    }

    internal val didService: DidManagementService by lazy {
        DidManagementService(
            didContext = this,
            didRegistry = config.didRegistry,
            kms = config.kms,
            kmsService = config.kmsService,
            defaultDidMethod = config.defaultDidMethod,
            ioDispatcher = config.ioDispatcher
        )
    }

    internal val walletService: WalletManagementService by lazy {
        WalletManagementService(
            walletContext = this,
            ioDispatcher = config.ioDispatcher
        )
    }

    private val trustService: TrustManagementService? by lazy {
        config.trustRegistry?.let { TrustManagementService(it) }
    }

    // ========== Internal Configuration Access Methods ==========
    
    /**
     * Get a DID method by name.
     */
    override fun getDidMethod(name: String): DidMethod? {
        val method = config.didRegistry.get(name) as? DidMethod
        logger.debug("Getting DID method: name={}, found={}", name, method != null)
        return method
    }

    /**
     * Get a DID resolver for delegation operations.
     * This returns the native DID resolver interface used by did:core.
     */
    override fun getDidResolver(): DidResolver {
        return DidResolver { did ->
            try {
                config.didRegistry.resolve(did.value)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                DidResolutionResult.Failure.ResolutionError(
                    did = did,
                    reason = "Failed to resolve DID: ${e.message}"
                )
            }
        }
    }

    /**
     * Get wallet factory.
     */
    override fun getWalletFactory(): WalletFactory? {
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
     * Configured [org.trustweave.credential.CredentialService], if any.
     */
    internal fun getCredentialService(): org.trustweave.credential.CredentialService? =
        config.credentialService

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
    override fun getDidRegistry(): DidMethodRegistry {
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
     * Returns the configured registry if set, otherwise falls back to the default registry.
     */
    internal fun getSchemaRegistry(): SchemaRegistry? {
        return config.schemaRegistry ?: org.trustweave.credential.schema.SchemaRegistries.default()
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
        return issuanceService?.issue(timeout, block)
            ?: IssuanceResult.Failure.AdapterNotReady(
                format = org.trustweave.credential.format.ProofSuiteId.VC_LD,
                reason = "CredentialService is not available. Configure it in TrustWeave.build { ... }"
            )
    }



    /**
     * Verify a verifiable credential (convenience overload).
     *
     * Use this when verifying a single credential with default options.
     *
     * **Example:**
     * ```kotlin
     * val result = trustWeave.verify(credential)
     * when (result) {
     *     is VerificationResult.Valid -> println("Valid!")
     *     is VerificationResult.Invalid -> println("Invalid: ${result.errors}")
     * }
     * ```
     *
     * @param credential The credential to verify
     * @param checkRevocation Whether to check revocation status (default: true)
     * @param checkExpiration Whether to check expiration (default: true)
     * @param timeout Maximum time to wait (default: 10 seconds)
     */
    suspend fun verify(
        credential: VerifiableCredential,
        checkRevocation: Boolean = true,
        checkExpiration: Boolean = true,
        timeout: Duration = 10.seconds
    ): VerificationResult {
        val service = verificationService ?: return VerificationResult.Invalid.AdapterNotReady(
            credential = credential,
            reason = "CredentialService is not available. Configure it in TrustWeave.build { ... }",
        )
        return service.verify(timeout) {
            this.credential(credential)
            if (checkRevocation) checkRevocation() else skipRevocation()
            if (checkExpiration) checkExpiration() else skipExpiration()
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
        val service = verificationService ?: return VerificationResult.Invalid.AdapterNotReady(
            credential = placeholderCredentialForUnconfiguredVerification(),
            reason = "CredentialService is not available. Configure it in TrustWeave.build { ... }",
        )
        return service.verify(timeout, block)
    }

    // ========== DID Operations ==========

    suspend fun createDid(
        method: String? = null,
        timeout: Duration = 10.seconds,
        block: DidBuilder.() -> Unit = {}
    ): DidCreationResult = didService.createDid(method, timeout, block)

    suspend fun createDidWithKey(
        method: String? = null,
        timeout: Duration = 10.seconds,
        block: DidBuilder.() -> Unit = {}
    ): DidCreationWithKeyResult = didService.createDidWithKey(method, timeout, block)

    suspend fun getKeyId(did: Did): Result<String> = didService.getKeyId(did)

    suspend fun resolveDid(
        did: String,
        timeout: Duration = 30.seconds
    ): DidResolutionResult = didService.resolveDid(did, timeout)

    suspend fun resolveDid(
        did: Did,
        timeout: Duration = 30.seconds
    ): DidResolutionResult = didService.resolveDid(did, timeout)

    suspend fun updateDid(
        timeout: Duration = 30.seconds,
        block: DidDocumentBuilder.() -> Unit
    ): DidResult = didService.updateDid(timeout, block)

    suspend fun delegate(
        timeout: Duration = 30.seconds,
        block: suspend DelegationBuilder.() -> Unit
    ): DelegationChainResult = didService.delegate(timeout, block)

    suspend fun rotateKey(
        timeout: Duration = 30.seconds,
        block: KeyRotationBuilder.() -> Unit
    ): DidResult = didService.rotateKey(timeout, block)

    override suspend fun resolve(did: Did): DidResolutionResult = didService.resolveDid(did)

    // ========== Wallet Operations ==========

    /**
     * Create a wallet using the configured TrustWeave instance.
     *
     * @param block DSL block for configuring the wallet
     * @return Sealed result type with success or detailed failure information
     */
    suspend fun wallet(block: WalletBuilder.() -> Unit): WalletCreationResult =
        walletService.wallet(block)

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
        val service = trustService
            ?: return TrustPath.NotConfigured("Trust registry is not configured. Configure it in TrustWeave.build { trust { ... } }")
        return service.findTrustPath(verifier, issuer, timeout)
    }

    /**
     * Perform trust operations using the trust DSL.
     *
     * Provides a fluent API for managing trust anchors and discovering trust paths.
     * The registry must be configured in the TrustWeaveConfig.
     *
     * **Example**:
     * ```kotlin
     * val notConfigured = trustWeave.trust {
     *     addAnchor("did:key:university") {
     *         credentialTypes("EducationCredential")
     *         description("Trusted university")
     *     }
     *
     *     val isTrusted = isTrusted("did:key:university", "EducationCredential")
     *     val path = findTrustPath(VerifierIdentity(Did("did:key:verifier")), IssuerIdentity(Did("did:key:issuer")))
     * }
     * if (notConfigured != null) {
     *     println("Trust registry not configured: ${notConfigured.reason}")
     * }
     * ```
     *
     * @param block DSL block for trust operations
     * @return [TrustPath.NotConfigured] if the trust registry is not configured, null on success
     */
    suspend fun trust(block: suspend TrustBuilder.() -> Unit): TrustPath.NotConfigured? {
        val service = trustService
            ?: return TrustPath.NotConfigured("Trust registry is not configured. Configure it in trustWeave { trust { provider(\"inMemory\") } }")
        service.trust(block)
        return null
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
    ): Boolean = revocationService.revoke(timeout, block)

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

    /**
     * Build a [TrustWeave] instance with the given configuration.
     * Static factories [TrustWeave.inMemory] and [TrustWeave.from] are in `TrustWeaveFactories.kt`.
     */
    companion object {
        suspend fun build(
            name: String = "default",
            block: TrustWeaveConfig.Builder.() -> Unit
        ): TrustWeave {
            val config = trustWeave(name, block)
            return TrustWeave(config)
        }
    }
}

