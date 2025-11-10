package io.geoknoesis.vericore

import io.geoknoesis.vericore.anchor.AnchorResult
import io.geoknoesis.vericore.anchor.BlockchainAnchorClient
import io.geoknoesis.vericore.anchor.BlockchainAnchorRegistry
import io.geoknoesis.vericore.spi.services.WalletFactory
import io.geoknoesis.vericore.credential.CredentialService
import io.geoknoesis.vericore.credential.CredentialServiceRegistry
import io.geoknoesis.vericore.credential.CredentialVerificationOptions
import io.geoknoesis.vericore.credential.CredentialVerificationResult
import io.geoknoesis.vericore.credential.did.CredentialDidResolver
import io.geoknoesis.vericore.did.toCredentialDidResolution
import io.geoknoesis.vericore.credential.issuer.CredentialIssuer
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.verifier.CredentialVerifier
import io.geoknoesis.vericore.credential.wallet.Wallet
import io.geoknoesis.vericore.credential.wallet.WalletProvider
import io.geoknoesis.vericore.credential.proof.Ed25519ProofGenerator
import io.geoknoesis.vericore.credential.proof.ProofGenerator
import io.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry
import io.geoknoesis.vericore.did.Did
import io.geoknoesis.vericore.did.DidCreationOptions
import io.geoknoesis.vericore.did.DidCreationOptionsBuilder
import io.geoknoesis.vericore.did.DidDocument
import io.geoknoesis.vericore.did.DidMethod
import io.geoknoesis.vericore.did.DidResolutionResult
import io.geoknoesis.vericore.did.DidMethodRegistry
import io.geoknoesis.vericore.did.didCreationOptions
import io.geoknoesis.vericore.kms.KeyManagementService
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Main entry point for VeriCore library.
 * 
 * VeriCore provides a unified, elegant API for decentralized identity and trust operations.
 * This facade simplifies common tasks while still allowing access to advanced features.
 * 
 * **Quick Start:**
 * ```kotlin
 * val vericore = VeriCore.create()
     * val did = vericore.createDid().getOrThrow()
     * val credential = vericore.issueCredential(did, keyId) { ... }.getOrThrow()
     * val valid = vericore.verifyCredential(credential).getOrThrow()
 * ```
 * 
 * **Custom Configuration:**
 * ```kotlin
 * val vericore = VeriCore.create {
 *     kms = MyCustomKms()
 *     registerDidMethod(MyDidMethod())
 *     registerBlockchainClient("ethereum:mainnet", myClient)
 * }
 * ```
 * 
 * @see VeriCoreContext for advanced configuration
 */
class VeriCore private constructor(
    private val context: VeriCoreContext
) {
    
    // ========================================
    // DID Operations
    // ========================================
    
    /**
     * Creates a new DID using the default or specified method.
     * 
     * **Example:**
     * ```kotlin
     * val did = vericore.createDid().getOrThrow()  // Uses default "key" method
     * val webDid = vericore.createDid(method = "web").getOrThrow()
     * ```
     * 
     * @param method DID method name (default: "key")
     * @param options Method-specific creation options
     * @return Result containing the created DID document
     * @throws IllegalArgumentException if the method is not registered
     */
    suspend fun createDid(
        method: String = "key",
        options: DidCreationOptions = DidCreationOptions()
    ): Result<DidDocument> = runCatching {
        val didMethod = context.getDidMethod(method)
            ?: throw IllegalArgumentException(
                "DID method '$method' not registered. Available methods: ${context.getAvailableDidMethods()}"
            )
        didMethod.createDid(options)
    }

    /**
     * Creates a DID using a fluent builder for [DidCreationOptions].
     *
     * **Example:**
     * ```kotlin
     * val did = vericore.createDid("key") {
     *     algorithm = DidCreationOptions.KeyAlgorithm.ED25519
     *     purpose(DidCreationOptions.KeyPurpose.AUTHENTICATION)
     * }.getOrThrow()
     * ```
     */
    suspend fun createDid(
        method: String = "key",
        configure: DidCreationOptionsBuilder.() -> Unit
    ): Result<DidDocument> = createDid(method, didCreationOptions(configure))

    /**
     * Resolves a DID to its document.
     * 
     * **Example:**
     * ```kotlin
     * val result = vericore.resolveDid("did:key:z6Mkfriq...").getOrThrow()
     * if (result.document != null) {
     *     println("DID resolved: ${result.document.id}")
     * }
     * ```
     * 
     * @param did The DID string to resolve
     * @return Resolution result containing the document and metadata
     * @throws IllegalArgumentException if the DID method is not registered
     */
    suspend fun resolveDid(did: String): Result<DidResolutionResult> =
        runCatching { context.resolveDid(did) }
    
    // ========================================
    // Credential Operations
    // ========================================
    
    /**
     * Issues a verifiable credential.
     * 
     * **Example:**
     * ```kotlin
     * val credential = vericore.issueCredential(
     *     issuerDid = "did:key:issuer",
     *     issuerKeyId = "key-1",
     *     credentialSubject = buildJsonObject {
     *         put("id", "did:key:subject")
     *         put("name", "Alice")
     *     },
     *     types = listOf("PersonCredential")
     * ).getOrThrow()
     * ```
     * 
     * @param issuerDid DID of the credential issuer
     * @param issuerKeyId Key ID for signing
     * @param credentialSubject The credential subject as JSON
     * @param types Credential types (VerifiableCredential is added automatically)
     * @param expirationDate Optional expiration date (ISO 8601)
     * @return Result containing the issued credential with proof
     */
    suspend fun issueCredential(
        issuerDid: String,
        issuerKeyId: String,
        credentialSubject: kotlinx.serialization.json.JsonElement,
        types: List<String> = listOf("VerifiableCredential"),
        expirationDate: String? = null
    ): Result<VerifiableCredential> = runCatching {
        val normalizedKeyId = normalizeKeyId(issuerKeyId)
        val credentialId = "urn:uuid:${UUID.randomUUID()}"
        val credential = VerifiableCredential(
            id = credentialId,
            type = if ("VerifiableCredential" in types) types else listOf("VerifiableCredential") + types,
            issuer = issuerDid,
            credentialSubject = credentialSubject,
            issuanceDate = java.time.Instant.now().toString(),
            expirationDate = expirationDate
        )

        val proofGenerator = Ed25519ProofGenerator(
            signer = { data, keyId -> context.kms.sign(normalizeKeyId(keyId), data) }
        )

        val issuer = CredentialIssuer(proofGenerator)
        issuer.issue(credential, issuerDid, normalizedKeyId)
    }
    
    /**
     * Verifies a verifiable credential.
     * 
     * **Example:**
     * ```kotlin
     * val result = vericore.verifyCredential(credential).getOrThrow()
     * if (result.valid) {
     *     println("Credential is valid")
     * } else {
     *     println("Errors: ${result.errors}")
     * }
     * ```
     * 
     * @param credential The credential to verify
     * @param options Verification options (e.g., check revocation, expiration)
     * @return Result containing verification details
     */
    suspend fun verifyCredential(
        credential: VerifiableCredential,
        options: CredentialVerificationOptions = CredentialVerificationOptions()
    ): Result<CredentialVerificationResult> = runCatching {
        val effectiveResolver = options.didResolver ?: CredentialDidResolver { did ->
            runCatching { context.resolveDid(did) }
                .getOrNull()
                ?.toCredentialDidResolution()
        }
        val verifier = CredentialVerifier(defaultDidResolver = effectiveResolver)
        @Suppress("DEPRECATION")
        val adjustedOptions = if (options.didResolver == null) {
            options.copy(didResolver = effectiveResolver)
        } else {
            options
        }
        verifier.verify(credential, adjustedOptions)
    }
    
    // ========================================
    // Wallet Operations
    // ========================================
    
    /**
     * Creates an in-memory wallet for storing credentials.
     * 
     * **Example:**
     * ```kotlin
     * val wallet = vericore.createWallet("did:key:holder").getOrThrow()
     * wallet.store(credential)
     * val credentials = wallet.query {
     *     byType("PersonCredential")
     *     notExpired()
     * }
     * ```
     * 
     * @param holderDid DID of the wallet holder
     * @param walletId Optional wallet identifier (generated if not provided)
     * @param provider Strongly typed wallet provider identifier (defaults to in-memory)
     * @param options Provider-specific configuration passed to the underlying [WalletFactory]
     * @return Result containing the new wallet instance
     */
    suspend fun createWallet(
        holderDid: String,
        walletId: String = UUID.randomUUID().toString(),
        provider: WalletProvider = WalletProvider.InMemory,
        options: Map<String, Any?> = emptyMap()
    ): Result<Wallet> = runCatching {
        val wallet = context.walletFactory.create(
            providerName = provider.id,
            walletId = walletId,
            holderDid = holderDid,
            options = options
        )

        wallet as? Wallet ?: throw IllegalStateException(
            "WalletFactory returned unsupported instance: ${wallet?.let { it::class.qualifiedName }}"
        )
    }

    /**
     * Creates a wallet using a fluent options builder.
     *
     * **Example:**
     * ```kotlin
     * val wallet = vericore.createWallet("did:key:holder") {
     *     label = "Holder Wallet"
     *     storagePath = "/wallets/holder"
     *     property("autoUnlock", true)
     * }.getOrThrow()
     * ```
     */
    suspend fun createWallet(
        holderDid: String,
        walletId: String = UUID.randomUUID().toString(),
        provider: WalletProvider = WalletProvider.InMemory,
        configure: WalletOptionsBuilder.() -> Unit
    ): Result<Wallet> =
        createWallet(
            holderDid = holderDid,
            walletId = walletId,
            provider = provider,
            options = walletOptions(configure)
        )
    
    // ========================================
    // Blockchain Anchoring
    // ========================================
    
    /**
     * Anchors data to a blockchain.
     * 
     * **Example:**
     * ```kotlin
     * val result = vericore.anchor(
     *     data = myData,
     *     serializer = MyData.serializer(),
     *     chainId = "algorand:testnet"
     * )
     * println("Anchored at: ${result.ref.txHash}")
     * ```
     * 
     * @param data The data to anchor
     * @param serializer Kotlinx Serialization serializer for the data type
     * @param chainId Blockchain chain identifier (CAIP-2 format)
     * @return Anchor result with transaction reference
     * @throws IllegalArgumentException if no client is registered for the chain
     */
    suspend fun <T : Any> anchor(
        data: T,
        serializer: KSerializer<T>,
        chainId: String
    ): AnchorResult {
        val client = context.getBlockchainClient(chainId)
            ?: throw IllegalArgumentException(
                "No blockchain client registered for chain: $chainId. " +
                "Available chains: ${context.getAvailableChains()}"
            )
        
        val json = Json.encodeToJsonElement(serializer, data)
        return client.writePayload(json)
    }
    
    /**
     * Reads anchored data from a blockchain.
     * 
     * **Example:**
     * ```kotlin
     * val data = vericore.readAnchor<MyData>(
     *     ref = anchorRef,
     *     serializer = MyData.serializer()
     * )
     * ```
     * 
     * @param ref The anchor reference
     * @param serializer Kotlinx Serialization serializer for the data type
     * @return The deserialized data
     */
    suspend fun <T : Any> readAnchor(
        ref: io.geoknoesis.vericore.anchor.AnchorRef,
        serializer: KSerializer<T>
    ): T {
        val client = context.getBlockchainClient(ref.chainId)
            ?: throw IllegalArgumentException("No blockchain client registered for chain: ${ref.chainId}")
        
        val result = client.readPayload(ref)
        return Json.decodeFromJsonElement(serializer, result.payload)
    }
    
    // ========================================
    // Context Access
    // ========================================
    
    /**
     * Gets the underlying context for advanced usage.
     * 
     * Use this when you need direct access to registries or services.
     * 
     * @return The VeriCore context
     */
    fun getContext(): VeriCoreContext = context
    
    /**
     * Registers a DID method on this VeriCore instance.
     *
     * Useful for adding methods after construction.
     */
    fun registerDidMethod(method: DidMethod) {
        context.didRegistry.register(method)
    }
    
    /**
     * Registers a blockchain client on this VeriCore instance.
     */
    fun registerBlockchainClient(chainId: String, client: BlockchainAnchorClient) {
        context.blockchainRegistry.register(chainId, client)
    }
    
    /**
     * Lists registered DID method names.
     */
    fun listDidMethods(): List<String> = context.didRegistry.getAllMethodNames()
    
    /**
     * Lists registered blockchain chain IDs.
     */
    fun listBlockchainChains(): List<String> = context.blockchainRegistry.getAllChainIds()
    
    // ========================================
    // Factory Methods
    // ========================================
    
    private fun normalizeKeyId(keyId: String): String {
        return keyId.substringAfter('#', keyId)
    }

    companion object {
        /**
         * Creates a VeriCore instance with sensible defaults.
         * 
         * Includes:
         * - In-memory key management
         * - did:key method support
         * - No blockchain anchoring (must be configured separately)
         * 
         * **Example:**
         * ```kotlin
         * val vericore = VeriCore.create()
         * ```
         * 
         * @return VeriCore instance with default configuration
         */
        fun create(): VeriCore = create(VeriCoreDefaults.inMemoryTest())
        
        /**
         * Creates a VeriCore instance from a configuration object.
         *
         * @param config Fully constructed configuration
         */
        fun create(config: VeriCoreConfig): VeriCore {
            return VeriCore(VeriCoreContext.fromConfig(config))
        }
        
        /**
         * Creates a VeriCore instance with custom configuration.
         * Starts from [VeriCoreConfig.default] and applies overrides.
         *
         * **Example:**
         * ```kotlin
         * val vericore = VeriCore.create {
         *     kms(InMemoryKeyManagementService())
         *     registerDidMethod(DidWebMethod())
         *     registerBlockchainClient("ethereum:mainnet", ethereumClient)
         * }
         * ```
         *
         * @param configure Configuration block
         * @return Configured VeriCore instance
         */
        fun create(configure: VeriCoreConfig.Builder.() -> Unit): VeriCore {
            val builder = VeriCoreDefaults.inMemoryTest().toBuilder()
            builder.configure()
            return create(builder.build())
        }
    }
}

/**
 * VeriCore context holding all registries and services.
 * 
 * This class encapsulates all dependencies and configuration,
 * making it thread-safe and testable without global state.
 */
class VeriCoreContext private constructor(
    internal val kms: KeyManagementService,
    internal val walletFactory: WalletFactory,
    internal val didRegistry: DidMethodRegistry,
    internal val blockchainRegistry: BlockchainAnchorRegistry,
    internal val credentialRegistry: CredentialServiceRegistry,
    internal val proofRegistry: ProofGeneratorRegistry
) {
    
    /**
     * Gets a DID method by name.
     */
    fun getDidMethod(methodName: String): DidMethod? = didRegistry.get(methodName)
    
    /**
     * Gets available DID method names.
     */
    fun getAvailableDidMethods(): List<String> = didRegistry.getAllMethodNames()
    
    /**
     * Gets a blockchain client by chain ID.
     */
    fun getBlockchainClient(chainId: String): BlockchainAnchorClient? =
        blockchainRegistry.get(chainId)
    
    /**
     * Gets available blockchain chain IDs.
     */
    fun getAvailableChains(): List<String> = blockchainRegistry.getAllChainIds()
    
    /**
     * Resolves a DID using registered methods.
     */
    suspend fun resolveDid(did: String): DidResolutionResult {
        return didRegistry.resolve(did)
    }

    fun didMethodRegistry(): DidMethodRegistry = didRegistry

    fun blockchainRegistry(): BlockchainAnchorRegistry = blockchainRegistry

    fun credentialRegistry(): CredentialServiceRegistry = credentialRegistry

    fun proofRegistry(): ProofGeneratorRegistry = proofRegistry
    
    companion object {
        fun fromConfig(config: VeriCoreConfig): VeriCoreContext {
            return VeriCoreContext(
                kms = config.kms,
                walletFactory = config.walletFactory,
                didRegistry = config.didRegistry,
                blockchainRegistry = config.blockchainRegistry,
                credentialRegistry = config.credentialRegistry,
                proofRegistry = config.proofRegistry
            )
        }
    }
}

/**
 * Strongly typed configuration for [VeriCore].
 */
data class VeriCoreConfig(
    val kms: KeyManagementService,
    val walletFactory: WalletFactory,
    val didRegistry: DidMethodRegistry,
    val blockchainRegistry: BlockchainAnchorRegistry = BlockchainAnchorRegistry(),
    val credentialRegistry: CredentialServiceRegistry = CredentialServiceRegistry.create(),
    val proofRegistry: ProofGeneratorRegistry = ProofGeneratorRegistry()
) {
    fun toBuilder(): Builder = Builder(
        kms = kms,
        walletFactory = walletFactory,
        didRegistry = didRegistry.snapshot(),
        blockchainRegistry = blockchainRegistry.snapshot(),
        credentialRegistry = credentialRegistry.snapshot(),
        proofRegistry = proofRegistry.snapshot()
    )
    
    class Builder internal constructor(
        private var kms: KeyManagementService?,
        private var walletFactory: WalletFactory?,
        private val didRegistry: DidMethodRegistry,
        private val blockchainRegistry: BlockchainAnchorRegistry,
        private val credentialRegistry: CredentialServiceRegistry,
        private val proofRegistry: ProofGeneratorRegistry
    ) {
        fun kms(kms: KeyManagementService) {
            this.kms = kms
        }
        
        fun walletFactory(walletFactory: WalletFactory) {
            this.walletFactory = walletFactory
        }
        
        fun registerDidMethod(method: DidMethod) {
            didRegistry.register(method)
        }
        
        fun registerBlockchainClient(chainId: String, client: BlockchainAnchorClient) {
            blockchainRegistry.register(chainId, client)
        }
        
        fun registerCredentialService(service: CredentialService) {
            credentialRegistry.register(service)
        }
        
        fun unregisterCredentialService(providerName: String) {
            credentialRegistry.unregister(providerName)
        }

        fun registerProofGenerator(generator: ProofGenerator) {
            proofRegistry.register(generator)
        }

        fun unregisterProofGenerator(proofType: String) {
            proofRegistry.unregister(proofType)
        }
        
        fun removeBlockchainClient(chainId: String) {
            blockchainRegistry.unregister(chainId)
        }
        
        fun removeDidMethod(methodName: String) {
            didRegistry.unregister(methodName)
        }
        
        fun build(): VeriCoreConfig {
            val kms = requireNotNull(this.kms) { "KMS must be configured" }
            val walletFactory = requireNotNull(this.walletFactory) { "WalletFactory must be configured" }
            require(didRegistry.size() > 0) { "At least one DID method must be registered" }
            
            return VeriCoreConfig(
                kms = kms,
                walletFactory = walletFactory,
                didRegistry = didRegistry.snapshot(),
                blockchainRegistry = blockchainRegistry.snapshot(),
                credentialRegistry = credentialRegistry.snapshot(),
                proofRegistry = proofRegistry.snapshot()
            )
        }
    }
    
    companion object
}


/**
 * Builder for constructing wallet creation options in a type-safe manner.
 */
class WalletOptionsBuilder {
    /**
     * Optional human-readable label associated with the wallet.
     */
    var label: String? = null

    /**
     * Optional storage path used by file-based wallet providers.
     */
    var storagePath: String? = null

    /**
     * Optional encryption key or password material used by secure wallets.
     */
    var encryptionKey: String? = null

    private val customProperties = mutableMapOf<String, Any?>()

    /**
     * Adds a provider-specific option that doesn't have a dedicated property.
     */
    fun property(key: String, value: Any?) {
        customProperties[key] = value
    }

    internal fun build(): Map<String, Any?> {
        val options = mutableMapOf<String, Any?>()
        label?.let { options["label"] = it }
        storagePath?.let { options["storagePath"] = it }
        encryptionKey?.let { options["encryptionKey"] = it }
        options.putAll(customProperties)
        return options
    }
}

/**
 * Convenience builder for wallet creation options.
 *
 * **Example:**
 * ```kotlin
 * val options = walletOptions {
 *     label = "Holder Wallet"
 *     storagePath = "/wallets/holders/alice"
 *     property("autoUnlock", true)
 * }
 * ```
 */
fun walletOptions(block: WalletOptionsBuilder.() -> Unit): Map<String, Any?> {
    val builder = WalletOptionsBuilder()
    builder.block()
    return builder.build()
}

