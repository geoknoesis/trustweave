package org.trustweave.trust

import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory
import org.trustweave.anchor.AnchorResult
import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.anchor.payment.FeeStrategy
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.anchor.services.BlockchainService
import org.trustweave.contract.DefaultSmartContractService
import org.trustweave.contract.SmartContractService
import org.trustweave.core.plugin.PluginLifecycle
import org.trustweave.credential.model.ProofType
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.schema.SchemaRegistry
import org.trustweave.did.DidMethod
import org.trustweave.did.identifiers.Did
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.verifier.DelegationChainResult
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.services.KmsService
import org.trustweave.trust.context.DidDslContext
import org.trustweave.trust.context.WalletDslContext
import org.trustweave.trust.domain.TrustedDomainManager
import org.trustweave.trust.dsl.KeyRotationBuilder
import org.trustweave.trust.dsl.TrustBuilder
import org.trustweave.trust.dsl.TrustWeaveConfig
import org.trustweave.trust.dsl.credential.IssuanceBuilder
import org.trustweave.trust.dsl.credential.RevocationBuilder
import org.trustweave.trust.dsl.credential.VerificationBuilder
import org.trustweave.trust.dsl.did.DelegationBuilder
import org.trustweave.trust.dsl.did.DidBuilder
import org.trustweave.trust.dsl.did.DidDocumentBuilder
import org.trustweave.trust.dsl.findTrustPath
import org.trustweave.trust.dsl.not
import org.trustweave.trust.dsl.trustWeave
import org.trustweave.trust.dsl.wallet.WalletBuilder
import org.trustweave.trust.internal.TrustWeaveShutdown
import org.trustweave.trust.internal.placeholderCredentialForUnconfiguredVerification
import org.trustweave.trust.services.CredentialIssuanceService
import org.trustweave.trust.services.CredentialRevocationService
import org.trustweave.trust.services.CredentialVerificationService
import org.trustweave.trust.services.DidManagementService
import org.trustweave.trust.services.TrustManagementService
import org.trustweave.trust.services.WalletManagementService
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.trust.types.DidCreationWithKeyResult
import org.trustweave.trust.types.DidResult
import org.trustweave.trust.types.IssuerIdentity
import org.trustweave.trust.types.KeyIdResult
import org.trustweave.trust.types.RevocationResult
import org.trustweave.trust.types.TrustOperationResult
import org.trustweave.trust.types.TrustPath
import org.trustweave.trust.types.VerifierIdentity
import org.trustweave.trust.types.WalletCreationResult
import org.trustweave.wallet.services.WalletFactory
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
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
    private val config: TrustWeaveConfig,
) : DidResolver,
    Closeable,
    DidDslContext,
    WalletDslContext {
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
            blockchainRegistry = config.blockchainRegistry,
        )
    }

    /**
     * Active [TrustedDomainManager] for this facade, or `null` when no
     * `domain { ... }` block was configured.
     *
     * The legacy [blockchains] surface stays unchanged and never routes through
     * the treasury. Callers that want fee caps, sponsor allow-lists, and a
     * settled audit ledger go through this manager (or the
     * [anchorThroughDomain] convenience).
     *
     * **Example:**
     * ```kotlin
     * val tw = TrustWeave.build {
     *     anchor { chain("algorand:testnet") { provider("algorand") } }
     *     domain {
     *         domainId("acme")
     *         payerDid("did:web:acme.example")
     *         chainAccount("algorand:testnet") {
     *             keyRef = KmsKeyRef("in-memory", "alg-key")
     *             address = "ABC...XYZ"
     *         }
     *     }
     * }
     * val ledger = tw.activeDomain?.treasury()?.ledger()
     * ```
     */
    val activeDomain: TrustedDomainManager? get() = config.trustedDomainManager

    /**
     * Anchor [payload] through the active [TrustedDomainManager]. Throws
     * [IllegalStateException] when no `domain { ... }` was configured;
     * otherwise delegates to [TrustedDomainManager.anchor] verbatim.
     */
    suspend fun anchorThroughDomain(
        operationKind: String,
        chainId: String,
        payload: JsonElement,
        feeStrategy: FeeStrategy = FeeStrategy.DomainPays,
        maxFee: TokenAmount? = null,
        mediaType: String = "application/json",
    ): AnchorResult {
        val domain =
            activeDomain
                ?: error(
                    "No active Trusted Domain. Configure one in TrustWeave.build { domain { ... } } " +
                        "before calling anchorThroughDomain.",
                )
        return domain.anchor(
            operationKind = operationKind,
            chainId = chainId,
            payload = payload,
            feeStrategy = feeStrategy,
            maxFee = maxFee,
            mediaType = mediaType,
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
                ioDispatcher = config.ioDispatcher,
                autoAnchor = config.credentialConfig.autoAnchor,
                defaultChain = config.credentialConfig.defaultChain,
                blockchainService = blockchains,
            )
        }
    }

    private val verificationService: CredentialVerificationService? by lazy {
        config.credentialService?.let { cs ->
            CredentialVerificationService(
                credentialService = cs,
                ioDispatcher = config.ioDispatcher,
            )
        }
    }

    private val revocationService: CredentialRevocationService by lazy {
        CredentialRevocationService(
            revocationManager = config.revocationManager,
            ioDispatcher = config.ioDispatcher,
        )
    }

    internal val didService: DidManagementService by lazy {
        DidManagementService(
            didContext = this,
            didRegistry = config.didRegistry,
            kms = config.kms,
            kmsService = config.kmsService,
            defaultDidMethod = config.defaultDidMethod,
            ioDispatcher = config.ioDispatcher,
        )
    }

    internal val walletService: WalletManagementService by lazy {
        WalletManagementService(
            walletContext = this,
            ioDispatcher = config.ioDispatcher,
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
    override fun getDidResolver(): DidResolver =
        DidResolver { did ->
            try {
                config.didRegistry.resolve(did.value)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                DidResolutionResult.Failure.ResolutionError(
                    did = did,
                    reason = "Failed to resolve DID: ${e.message}",
                )
            }
        }

    /**
     * Get wallet factory.
     */
    override fun getWalletFactory(): WalletFactory? = config.walletFactory

    /**
     * Get an anchor client by chain ID.
     *
     * @param chainId The blockchain chain identifier (e.g., "algorand:testnet")
     * @return The blockchain anchor client, or null if not registered
     */
    internal fun getAnchorClient(chainId: String): BlockchainAnchorClient? = config.blockchainRegistry.get(chainId)

    /**
     * Configured [org.trustweave.credential.CredentialService], if any.
     */
    internal fun getCredentialService(): org.trustweave.credential.CredentialService? = config.credentialService

    /**
     * Get the revocation manager.
     */
    internal fun getRevocationManager(): CredentialRevocationManager? = config.revocationManager

    /**
     * Get the default proof type.
     */
    internal fun getDefaultProofType(): ProofType = config.credentialConfig.defaultProofType

    /**
     * Get the DID registry.
     *
     * @return The DID method registry
     */
    override fun getDidRegistry(): DidMethodRegistry = config.didRegistry

    /**
     * Get the trust registry.
     */
    internal fun getTrustRegistry(): TrustRegistry? = config.trustRegistry

    /**
     * Get the KMS service adapter.
     */
    internal fun getKmsService(): KmsService? = config.kmsService

    /**
     * Get the underlying KMS.
     *
     * Public so consumers can, for example, register a real DID method bound to the same KMS that
     * signs credentials (`getDidRegistry().register(KeyDidMethod(getKms()!!))`).
     *
     * The return type stays nullable for source compatibility with existing callers, but the
     * configured KMS is always non-null.
     */
    fun getKms(): KeyManagementService? = config.kms

    /**
     * Get the schema registry.
     * Returns the configured registry if set, otherwise falls back to the default registry.
     */
    private val fallbackSchemaRegistry: SchemaRegistry by lazy {
        org.trustweave.credential.schema.SchemaRegistries
            .default()
    }

    internal fun getSchemaRegistry(): SchemaRegistry = config.schemaRegistry ?: fallbackSchemaRegistry

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
        block: IssuanceBuilder.() -> Unit,
    ): IssuanceResult =
        issuanceService?.issue(timeout, block)
            ?: IssuanceResult.Failure.AdapterNotReady(
                format = org.trustweave.credential.format.ProofSuiteId.VC_LD,
                reason = "CredentialService is not available. Configure it in TrustWeave.build { ... }",
            )

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
        timeout: Duration = 10.seconds,
    ): VerificationResult {
        val service =
            verificationService ?: return VerificationResult.Invalid.AdapterNotReady(
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
        block: VerificationBuilder.() -> Unit,
    ): VerificationResult {
        val service =
            verificationService ?: return VerificationResult.Invalid.AdapterNotReady(
                credential = placeholderCredentialForUnconfiguredVerification(),
                reason = "CredentialService is not available. Configure it in TrustWeave.build { ... }",
            )
        return service.verify(timeout, block)
    }

    // ========== DID Operations ==========

    suspend fun createDid(
        method: String? = null,
        timeout: Duration = 10.seconds,
        block: DidBuilder.() -> Unit = {},
    ): DidCreationResult = didService.createDid(method, timeout, block)

    suspend fun createDidWithKey(
        method: String? = null,
        timeout: Duration = 10.seconds,
        block: DidBuilder.() -> Unit = {},
    ): DidCreationWithKeyResult = didService.createDidWithKey(method, timeout, block)

    /**
     * The ID of the key [did] issues with: its first `assertionMethod`, else its first
     * `authentication` method, else its first verification method not reserved for `keyAgreement`.
     *
     * **Result contract:** returns [kotlin.Result] (not a TrustWeave sealed result) and never
     * throws for domain failures — an unresolvable DID or a document without a usable key is
     * `Result.failure` carrying an [IllegalStateException] with the reason. Coroutine
     * cancellation is rethrown, never captured. Unwrap with `getOrElse` / `fold`.
     */
    @Deprecated(
        message = "Returns kotlin.Result instead of a sealed TrustWeave result; use getKeyIdResult(did).",
        replaceWith = ReplaceWith("getKeyIdResult(did)"),
    )
    suspend fun getKeyId(did: Did): Result<String> = didService.getKeyId(did)

    /**
     * Sealed-result form of [getKeyId]: the ID of the key [did] issues with (first `assertionMethod`,
     * else first `authentication`, else first verification method not reserved for `keyAgreement`).
     * Never throws for domain failures; coroutine cancellation is rethrown.
     */
    suspend fun getKeyIdResult(did: Did): KeyIdResult =
        didService.getKeyId(did).fold(
            onSuccess = { KeyIdResult.Success(it) },
            onFailure = { KeyIdResult.Failure(it.message ?: it::class.simpleName ?: "Key lookup failed", it) },
        )

    suspend fun resolveDid(
        did: String,
        timeout: Duration = 30.seconds,
    ): DidResolutionResult = didService.resolveDid(did, timeout)

    suspend fun resolveDid(
        did: Did,
        timeout: Duration = 30.seconds,
    ): DidResolutionResult = didService.resolveDid(did, timeout)

    suspend fun updateDid(
        timeout: Duration = 30.seconds,
        block: DidDocumentBuilder.() -> Unit,
    ): DidResult = didService.updateDid(timeout, block)

    suspend fun delegate(
        timeout: Duration = 30.seconds,
        block: suspend DelegationBuilder.() -> Unit,
    ): DelegationChainResult = didService.delegate(timeout, block)

    suspend fun rotateKey(
        timeout: Duration = 30.seconds,
        block: KeyRotationBuilder.() -> Unit,
    ): DidResult = didService.rotateKey(timeout, block)

    override suspend fun resolve(did: Did): DidResolutionResult = didService.resolveDid(did)

    // ========== Wallet Operations ==========

    /**
     * Create a wallet using the configured TrustWeave instance.
     *
     * @param block DSL block for configuring the wallet
     * @return Sealed result type with success or detailed failure information
     */
    suspend fun wallet(block: WalletBuilder.() -> Unit): WalletCreationResult = walletService.wallet(block)

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
        timeout: Duration = 10.seconds,
    ): TrustPath {
        val service =
            trustService
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
     * **Result contract:** the only failure reported through the return value is a missing trust
     * registry ([TrustPath.NotConfigured]); [block] is then not run. Anything [block] or the
     * registry throws (for example an invalid anchor DID) propagates as an exception. `null`
     * means [block] ran to completion — it is not a "trusted" verdict; use `isTrusted` /
     * [findTrustPath] inside or outside the block for that.
     *
     * @param block DSL block for trust operations
     * @return [TrustPath.NotConfigured] if the trust registry is not configured, null on success
     */
    @Deprecated(
        message = "Nullable NotConfigured return and propagating exceptions; use trustResult { }.",
        replaceWith = ReplaceWith("trustResult(block)"),
    )
    suspend fun trust(block: suspend TrustBuilder.() -> Unit): TrustPath.NotConfigured? {
        val service =
            trustService
                ?: return TrustPath.NotConfigured(
                    "Trust registry is not configured. Configure it in trustWeave { trust { provider(\"inMemory\") } }",
                )
        service.trust(block)
        return null
    }

    /**
     * Sealed-result form of [trust]: [TrustOperationResult.Completed] when [block] ran,
     * [TrustOperationResult.NotConfigured] without a trust registry (block not run) and
     * [TrustOperationResult.Failure] when [block] or the registry threw. Coroutine cancellation is
     * rethrown.
     */
    suspend fun trustResult(block: suspend TrustBuilder.() -> Unit): TrustOperationResult {
        val service =
            trustService
                ?: return TrustOperationResult.NotConfigured(
                    "Trust registry is not configured. Configure it in trustWeave { trust { provider(\"inMemory\") } }",
                )
        return try {
            service.trust(block)
            TrustOperationResult.Completed
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            TrustOperationResult.Failure(e.message ?: e::class.simpleName ?: "Trust operation failed", e)
        }
    }

    /**
     * Revoke a credential using the configured TrustWeave instance.
     *
     * **Timeout semantics:** a revocation that exceeds [timeout] throws
     * [org.trustweave.core.exception.TrustWeaveException.OperationTimedOut] — the `Boolean`
     * return cannot honestly express a timeout (mapping it to `false` would silently report
     * an unknown outcome as "not revoked"). On timeout, the revocation outcome is unknown;
     * re-check the credential's status before retrying.
     *
     * **Result contract:** `true`/`false` is the revocation manager's answer for a well-formed
     * request (`false`: the manager did not change the entry, e.g. unknown index). Everything
     * else throws: a missing revocation manager or missing `credential(...)` / `statusList(...)`
     * ([IllegalStateException]), manager errors (propagated as thrown), and timeouts (below).
     *
     * @param timeout Maximum time to wait for revocation (default: 10 seconds)
     * @param block DSL block for specifying revocation parameters
     * @return true if revocation succeeded
     * @throws org.trustweave.core.exception.TrustWeaveException.OperationTimedOut if the
     *   operation exceeds [timeout]
     */
    @Deprecated(
        message = "Boolean return with thrown failures; use revokeResult { } for a sealed result.",
        replaceWith = ReplaceWith("revokeResult(timeout, block)"),
    )
    suspend fun revoke(
        timeout: Duration = 10.seconds,
        block: RevocationBuilder.() -> Unit,
    ): Boolean = revocationService.revoke(timeout, block)

    /**
     * Sealed-result form of [revoke]. Missing configuration, incomplete requests and timeouts are
     * variants instead of exceptions; coroutine cancellation is rethrown.
     *
     * @param timeout maximum time to wait; exceeding it yields [RevocationResult.TimedOut]
     */
    suspend fun revokeResult(
        timeout: Duration = 10.seconds,
        block: RevocationBuilder.() -> Unit,
    ): RevocationResult {
        if (configuration.revocationManager == null) {
            return RevocationResult.NotConfigured(
                "CredentialRevocationManager is required for revocation operations.",
            )
        }
        return try {
            if (revocationService.revoke(timeout, block)) RevocationResult.Revoked else RevocationResult.NotRevoked
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: org.trustweave.core.exception.TrustWeaveException.OperationTimedOut) {
            RevocationResult.TimedOut(timeout, e.cause)
        } catch (e: IllegalStateException) {
            RevocationResult.InvalidRequest(e.message ?: "Incomplete revocation request")
        } catch (e: Exception) {
            RevocationResult.Failure(e.message ?: e::class.simpleName ?: "Revocation failed", e)
        }
    }

    /** Guards [close] so repeated calls are no-ops (idempotent close). */
    private val closed = AtomicBoolean(false)

    private val shutdown by lazy { TrustWeaveShutdown(config, logger) }

    /**
     * Close and cleanup resources held by this TrustWeave instance.
     *
     * **Ownership semantics** — only components the facade *created* during
     * `TrustWeave.build { ... }` are closed; components injected by the caller remain
     * caller-owned and are never touched:
     * - **Closed when factory-created:** KMS (when resolved from a `keys { provider(...) }`
     *   name), credential service, revocation manager, trust registry (the instance the
     *   configured `TrustRegistryFactory` created during build), DID method instances
     *   registered during build, blockchain anchor clients resolved from `anchor { ... }`,
     *   the trusted domain manager, and the internal KMS service adapter.
     * - **Never closed (caller-owned):** a KMS supplied via `keys { custom(...) }` /
     *   `customKms(...)`, a credential service supplied via `credentialService(...)`,
     *   the wallet factory (always supplied via `factories(...)`), wallets returned by
     *   `wallet { ... }` (handed to the caller, never retained), and DID methods the
     *   caller registers via [getDidRegistry] after construction.
     *
     * For each owned component, [AutoCloseable.close] is invoked when implemented;
     * otherwise, if the component implements [PluginLifecycle], `stop()` + `cleanup()`
     * are driven.
     *
     * **From a coroutine, call [closeAsync] instead of this method.** [AutoCloseable.close] cannot
     * suspend, so this method BLOCKS the calling thread until the owned components have stopped. To make
     * that safe it runs the shutdown on a dedicated daemon thread (its own `runBlocking` event loop,
     * never nested in the caller's dispatcher) and waits for it with a bounded `join` (60 s); a
     * component that never finishes is logged as an error and abandoned rather than hanging the caller.
     * Even so, never call `close()` on a single-threaded or main dispatcher from a coroutine: a
     * component that needs that very thread to complete cannot, while it waits here. Use [closeAsync]
     * (or `use { }` only from plain, non-coroutine code such as `main` or a shutdown hook).
     *
     * **Error handling:** exceptions thrown by individual components are logged and do
     * not prevent the remaining components from being closed; in keeping with the
     * previous behavior of this method, close() itself never throws. Calling close()
     * more than once is a no-op.
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
        if (!beginClose()) return
        shutdown.closeBlocking()
    }

    /**
     * Suspending form of [close] with the same ownership, error-handling and idempotence
     * semantics: `stop()` + `cleanup()` of [PluginLifecycle] components are awaited instead of
     * blocking the calling thread. Calling either [close] or [closeAsync] more than once (in any
     * combination) closes the components only once.
     */
    suspend fun closeAsync() {
        if (!beginClose()) return
        shutdown.closeOwnedComponents()
    }

    private fun beginClose(): Boolean {
        if (!closed.compareAndSet(false, true)) {
            logger.debug("TrustWeave instance already closed: ${config.name}")
            return false
        }
        return true
    }

    /**
     * Build a [TrustWeave] instance with the given configuration.
     * Static factories [TrustWeave.inMemory] and [TrustWeave.from] are in `TrustWeaveFactories.kt`.
     */
    companion object {
        suspend fun build(
            name: String = "default",
            block: TrustWeaveConfig.Builder.() -> Unit,
        ): TrustWeave {
            val config = trustWeave(name, block)
            return TrustWeave(config)
        }
    }
}
