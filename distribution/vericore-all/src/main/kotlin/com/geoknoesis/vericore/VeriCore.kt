package com.geoknoesis.vericore

import com.geoknoesis.vericore.anchor.AnchorResult
import com.geoknoesis.vericore.anchor.BlockchainAnchorClient
import com.geoknoesis.vericore.anchor.BlockchainAnchorRegistry
import com.geoknoesis.vericore.core.*
import com.geoknoesis.vericore.core.vericoreCatching
import com.geoknoesis.vericore.core.normalizeKeyId
import com.geoknoesis.vericore.spi.services.WalletFactory
import com.geoknoesis.vericore.spi.services.WalletCreationOptions
import com.geoknoesis.vericore.spi.services.WalletCreationOptionsBuilder
import com.geoknoesis.vericore.credential.CredentialService
import com.geoknoesis.vericore.credential.CredentialServiceRegistry
import com.geoknoesis.vericore.credential.CredentialVerificationOptions
import com.geoknoesis.vericore.credential.CredentialVerificationResult
import com.geoknoesis.vericore.credential.did.CredentialDidResolver
import com.geoknoesis.vericore.did.toCredentialDidResolution
import com.geoknoesis.vericore.credential.issuer.CredentialIssuer
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.verifier.CredentialVerifier
import com.geoknoesis.vericore.credential.wallet.Wallet
import com.geoknoesis.vericore.credential.wallet.WalletProvider
import com.geoknoesis.vericore.credential.proof.Ed25519ProofGenerator
import com.geoknoesis.vericore.credential.proof.ProofGenerator
import com.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry
import com.geoknoesis.vericore.did.Did
import com.geoknoesis.vericore.did.DidCreationOptions
import com.geoknoesis.vericore.did.DidCreationOptionsBuilder
import com.geoknoesis.vericore.did.DidDocument
import com.geoknoesis.vericore.did.DidMethod
import com.geoknoesis.vericore.did.DidResolutionResult
import com.geoknoesis.vericore.did.DidMethodRegistry
import com.geoknoesis.vericore.did.didCreationOptions
import com.geoknoesis.vericore.kms.KeyManagementService
import com.geoknoesis.vericore.spi.PluginLifecycle
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
 *     walletFactory = MyWalletFactory()
 *     
 *     didMethods {
 *         + DidKeyMethod()
 *         + MyDidMethod()
 *     }
 *     
 *     blockchain {
 *         "ethereum:mainnet" to ethereumClient
 *         "algorand:testnet" to algorandClient
 *     }
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
     * // Simple usage
     * val did = vericore.createDid().getOrThrow()  // Uses default "key" method
     * 
     * // With custom method
     * val webDid = vericore.createDid(method = "web").getOrThrow()
     * 
     * // With error handling
     * val result = vericore.createDid()
     * result.fold(
     *     onSuccess = { did -> println("Created: ${did.id}") },
     *     onFailure = { error -> println("Failed: ${error.message}") }
     * )
     * ```
     * 
     * @param method DID method name (default: "key")
     * @param options Method-specific creation options
     * @return Result containing the created DID document
     */
    suspend fun createDid(
        method: String = "key",
        options: DidCreationOptions = DidCreationOptions()
    ): Result<DidDocument> = vericoreCatching {
        // Validate method is registered
        val availableMethods = context.getAvailableDidMethods()
        if (method !in availableMethods) {
            throw VeriCoreError.DidMethodNotRegistered(
                method = method,
                availableMethods = availableMethods
            )
        }
        
        val didMethod = context.getDidMethod(method)
            ?: throw VeriCoreError.DidMethodNotRegistered(
                method = method,
                availableMethods = availableMethods
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
     * // Simple usage
     * val result = vericore.resolveDid("did:key:z6Mkfriq...").getOrThrow()
     * if (result.document != null) {
     *     println("DID resolved: ${result.document.id}")
     * }
     * 
     * // With error handling
     * val result = vericore.resolveDid("did:key:z6Mkfriq...")
     * result.fold(
     *     onSuccess = { resolution ->
     *         if (resolution.document != null) {
     *             println("DID resolved: ${resolution.document.id}")
     *         } else {
     *             println("DID not found")
     *         }
     *     },
     *     onFailure = { error -> println("Resolution failed: ${error.message}") }
     * )
     * ```
     * 
     * @param did The DID string to resolve
     * @return Resolution result containing the document and metadata
     */
    suspend fun resolveDid(did: String): Result<DidResolutionResult> = vericoreCatching {
        // Validate DID format
        DidValidator.validateFormat(did).let {
            if (!it.isValid()) {
                throw VeriCoreError.InvalidDidFormat(
                    did = did,
                    reason = it.errorMessage() ?: "Invalid DID format"
                )
            }
        }
        
        // Validate method is registered
        val availableMethods = context.getAvailableDidMethods()
        DidValidator.validateMethod(did, availableMethods).let {
            if (!it.isValid()) {
                throw VeriCoreError.DidMethodNotRegistered(
                    method = DidValidator.extractMethod(did) ?: "unknown",
                    availableMethods = availableMethods
                )
            }
        }
        
        context.resolveDid(did)
    }
    
    // ========================================
    // Credential Operations
    // ========================================
    
    /**
     * Issues a verifiable credential.
     * 
     * **Example:**
     * ```kotlin
     * // Simple usage
     * val credential = vericore.issueCredential(
     *     issuerDid = "did:key:issuer",
     *     issuerKeyId = "key-1",
     *     credentialSubject = buildJsonObject {
     *         put("id", "did:key:subject")
     *         put("name", "Alice")
     *     },
     *     types = listOf("PersonCredential")
     * ).getOrThrow()
     * 
     * // With error handling
     * val result = vericore.issueCredential(...)
     * result.fold(
     *     onSuccess = { credential -> println("Issued: ${credential.id}") },
     *     onFailure = { error -> println("Issuance failed: ${error.message}") }
     * )
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
    ): Result<VerifiableCredential> = vericoreCatching {
        // Validate issuer DID format
        DidValidator.validateFormat(issuerDid).let {
            if (!it.isValid()) {
                throw VeriCoreError.InvalidDidFormat(
                    did = issuerDid,
                    reason = it.errorMessage() ?: "Invalid issuer DID format"
                )
            }
        }
        
        // Validate issuer DID method is registered
        val availableMethods = context.getAvailableDidMethods()
        DidValidator.validateMethod(issuerDid, availableMethods).let {
            if (!it.isValid()) {
                throw VeriCoreError.DidMethodNotRegistered(
                    method = DidValidator.extractMethod(issuerDid) ?: "unknown",
                    availableMethods = availableMethods
                )
            }
        }
        
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
        
        // Validate credential structure
        CredentialValidator.validateStructure(credential).let {
            if (!it.isValid()) {
                throw VeriCoreError.CredentialInvalid(
                    reason = it.errorMessage() ?: "Invalid credential structure",
                    credentialId = credentialId,
                    field = (it as? ValidationResult.Invalid)?.field
                )
            }
        }

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
    ): Result<CredentialVerificationResult> = vericoreCatching {
        val effectiveResolver = CredentialDidResolver { did ->
            runCatching { context.resolveDid(did) }
                .getOrNull()
                ?.toCredentialDidResolution()
        }
        val verifier = CredentialVerifier(defaultDidResolver = effectiveResolver)
        verifier.verify(credential, options)
    }
    
    // ========================================
    // Wallet Operations
    // ========================================
    
    /**
     * Creates an in-memory wallet for storing credentials.
     * 
     * **Example:**
     * ```kotlin
     * // Simplest usage - just provide holder DID
     * val wallet = vericore.createWallet("did:key:holder").getOrThrow()
     * 
     * // With options builder
     * val wallet = vericore.createWallet("did:key:holder") {
     *     label = "Holder Wallet"
     *     enableOrganization = true
     * }.getOrThrow()
     * 
     * // Full control
     * val wallet = vericore.createWallet(
     *     holderDid = "did:key:holder",
     *     provider = WalletProvider.Database,
     *     options = walletOptions { ... }
     * ).getOrThrow()
     * 
     * wallet.store(credential)
     * val credentials = wallet.query {
     *     byType("PersonCredential")
     *     notExpired()
     * }
     * ```
     * 
     * @param holderDid DID of the wallet holder
     * @return Result containing the new wallet instance
     */
    suspend fun createWallet(holderDid: String): Result<Wallet> =
        createWallet(holderDid, WalletProvider.InMemory)

    /**
     * Creates a wallet with a specific provider.
     * 
     * @param holderDid DID of the wallet holder
     * @param provider Wallet provider (defaults to InMemory)
     * @return Result containing the new wallet instance
     */
    suspend fun createWallet(
        holderDid: String,
        provider: WalletProvider
    ): Result<Wallet> =
        createWallet(holderDid, UUID.randomUUID().toString(), provider)

    /**
     * Creates a wallet with a specific provider and wallet ID.
     * 
     * @param holderDid DID of the wallet holder
     * @param walletId Wallet identifier (generated if not provided)
     * @param provider Wallet provider (defaults to InMemory)
     * @return Result containing the new wallet instance
     */
    suspend fun createWallet(
        holderDid: String,
        walletId: String,
        provider: WalletProvider = WalletProvider.InMemory
    ): Result<Wallet> =
        createWallet(holderDid, walletId, provider, WalletCreationOptions())

    /**
     * Creates a wallet with full configuration options.
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
        options: WalletCreationOptions
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
     * 
     * @param holderDid DID of the wallet holder
     * @param provider Wallet provider (defaults to InMemory)
     * @param configure Options builder block
     * @return Result containing the new wallet instance
     */
    suspend fun createWallet(
        holderDid: String,
        provider: WalletProvider = WalletProvider.InMemory,
        configure: WalletOptionsBuilder.() -> Unit
    ): Result<Wallet> =
        createWallet(
            holderDid = holderDid,
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
     * // Simple usage
     * val result = vericore.anchor(
     *     data = myData,
     *     serializer = MyData.serializer(),
     *     chainId = "algorand:testnet"
     * ).getOrThrow()
     * println("Anchored at: ${result.ref.txHash}")
     * 
     * // With error handling
     * val result = vericore.anchor(...)
     * result.fold(
     *     onSuccess = { anchor -> println("Anchored at: ${anchor.ref.txHash}") },
     *     onFailure = { error -> println("Anchoring failed: ${error.message}") }
     * )
     * ```
     * 
     * @param data The data to anchor
     * @param serializer Kotlinx Serialization serializer for the data type
     * @param chainId Blockchain chain identifier (CAIP-2 format)
     * @return Result containing anchor result with transaction reference
     */
    suspend fun <T : Any> anchor(
        data: T,
        serializer: KSerializer<T>,
        chainId: String
    ): Result<AnchorResult> = vericoreCatching {
        // Validate chain ID format
        ChainIdValidator.validateFormat(chainId).let {
            if (!it.isValid()) {
                throw VeriCoreError.ChainNotRegistered(
                    chainId = chainId,
                    availableChains = context.getAvailableChains()
                )
            }
        }
        
        // Validate chain is registered
        val availableChains = context.getAvailableChains()
        ChainIdValidator.validateRegistered(chainId, availableChains).let {
            if (!it.isValid()) {
                throw VeriCoreError.ChainNotRegistered(
                    chainId = chainId,
                    availableChains = availableChains
                )
            }
        }
        
        val client = context.getBlockchainClient(chainId)
            ?: throw VeriCoreError.ChainNotRegistered(
                chainId = chainId,
                availableChains = availableChains
            )
        
        val json = Json.encodeToJsonElement(serializer, data)
        client.writePayload(json)
    }
    
    /**
     * Reads anchored data from a blockchain.
     * 
     * **Example:**
     * ```kotlin
     * // Simple usage
     * val data = vericore.readAnchor<MyData>(
     *     ref = anchorRef,
     *     serializer = MyData.serializer()
     * ).getOrThrow()
     * 
     * // With error handling
     * val result = vericore.readAnchor<MyData>(...)
     * result.fold(
     *     onSuccess = { data -> println("Read: $data") },
     *     onFailure = { error -> println("Read failed: ${error.message}") }
     * )
     * ```
     * 
     * @param ref The anchor reference
     * @param serializer Kotlinx Serialization serializer for the data type
     * @return Result containing the deserialized data
     */
    suspend fun <T : Any> readAnchor(
        ref: com.geoknoesis.vericore.anchor.AnchorRef,
        serializer: KSerializer<T>
    ): Result<T> = vericoreCatching {
        // Validate chain is registered
        val availableChains = context.getAvailableChains()
        ChainIdValidator.validateRegistered(ref.chainId, availableChains).let {
            if (!it.isValid()) {
                throw VeriCoreError.ChainNotRegistered(
                    chainId = ref.chainId,
                    availableChains = availableChains
                )
            }
        }
        
        val client = context.getBlockchainClient(ref.chainId)
            ?: throw VeriCoreError.ChainNotRegistered(
                chainId = ref.chainId,
                availableChains = availableChains
            )
        
        val result = client.readPayload(ref)
        Json.decodeFromJsonElement(serializer, result.payload)
    }
    
    // ========================================
    // Context Access
    // ========================================
    
    /**
     * Gets the underlying context for advanced usage.
     * 
     * Use this when you need direct access to registries or services.
     * Most users should use the VeriCore facade methods instead.
     * 
     * @return The VeriCore context
     */
    fun getContext(): VeriCoreContext = context
    
    /**
     * Registers a DID method on this VeriCore instance.
     * 
     * Useful for adding methods after construction.
     * 
     * **Example:**
     * ```kotlin
     * vericore.registerDidMethod(DidWebMethod())
     * ```
     * 
     * @param method The DID method implementation to register
     */
    fun registerDidMethod(method: DidMethod) {
        context.didRegistry.register(method)
    }
    
    /**
     * Registers a blockchain client on this VeriCore instance.
     * 
     * Useful for adding blockchain clients after construction.
     * 
     * **Example:**
     * ```kotlin
     * vericore.registerBlockchainClient("ethereum:mainnet", ethereumClient)
     * ```
     * 
     * @param chainId Chain ID in CAIP-2 format (e.g., "ethereum:mainnet")
     * @param client Blockchain anchor client instance
     */
    fun registerBlockchainClient(chainId: String, client: BlockchainAnchorClient) {
        context.blockchainRegistry.register(chainId, client)
    }
    
    /**
     * Gets available DID method names.
     * 
     * @return List of registered DID method names
     */
    fun getAvailableDidMethods(): List<String> = context.didRegistry.getAllMethodNames()
    
    /**
     * Gets available blockchain chain IDs.
     * 
     * @return List of registered blockchain chain IDs
     */
    fun getAvailableChains(): List<String> = context.blockchainRegistry.getAllChainIds()
    
    // ========================================
    // Plugin Lifecycle Management
    // ========================================
    
    /**
     * Initializes all plugins that implement [PluginLifecycle].
     * 
     * This should be called after creating a VeriCore instance if you need
     * to initialize plugins before use.
     * 
     * **Example:**
     * ```kotlin
     * val vericore = VeriCore.create { ... }
     * vericore.initialize().getOrThrow()
     * ```
     * 
     * @param config Optional configuration map for plugins
     * @return Result indicating success or failure
     */
    suspend fun initialize(config: Map<String, Any?> = emptyMap()): Result<Unit> = vericoreCatching {
        val plugins = context.getAllPlugins()
        val failures = mutableListOf<String>()
        
        plugins.forEach { plugin ->
            if (plugin is PluginLifecycle) {
                val pluginConfig = config[plugin::class.simpleName] as? Map<String, Any?> ?: emptyMap()
                val initialized = plugin.initialize(pluginConfig)
                if (!initialized) {
                    failures.add("Plugin ${plugin::class.simpleName} failed to initialize")
                }
            }
        }
        
        if (failures.isNotEmpty()) {
            throw VeriCoreError.PluginInitializationFailed(
                pluginId = "multiple",
                reason = failures.joinToString("; ")
            )
        }
    }
    
    /**
     * Starts all plugins that implement [PluginLifecycle].
     * 
     * This should be called after [initialize] and before using VeriCore.
     * 
     * **Example:**
     * ```kotlin
     * val vericore = VeriCore.create { ... }
     * vericore.initialize().getOrThrow()
     * vericore.start().getOrThrow()
     * ```
     * 
     * @return Result indicating success or failure
     */
    suspend fun start(): Result<Unit> = vericoreCatching {
        val plugins = context.getAllPlugins()
        val failures = mutableListOf<String>()
        
        plugins.forEach { plugin ->
            if (plugin is PluginLifecycle) {
                val started = plugin.start()
                if (!started) {
                    failures.add("Plugin ${plugin::class.simpleName} failed to start")
                }
            }
        }
        
        if (failures.isNotEmpty()) {
            throw VeriCoreError.PluginInitializationFailed(
                pluginId = "multiple",
                reason = failures.joinToString("; ")
            )
        }
    }
    
    /**
     * Stops all plugins that implement [PluginLifecycle].
     * 
     * This should be called when shutting down VeriCore.
     * Plugins are stopped in reverse order of initialization.
     * 
     * **Example:**
     * ```kotlin
     * val vericore = VeriCore.create { ... }
     * // ... use vericore ...
     * vericore.stop().getOrThrow()
     * ```
     * 
     * @return Result indicating success or failure
     */
    suspend fun stop(): Result<Unit> = vericoreCatching {
        val plugins = context.getAllPlugins().reversed()
        val failures = mutableListOf<String>()
        
        plugins.forEach { plugin ->
            if (plugin is PluginLifecycle) {
                try {
                    val stopped = plugin.stop()
                    if (!stopped) {
                        failures.add("Plugin ${plugin::class.simpleName} failed to stop")
                    }
                } catch (e: Exception) {
                    failures.add("Plugin ${plugin::class.simpleName} error during stop: ${e.message}")
                }
            }
        }
        
        if (failures.isNotEmpty()) {
            throw VeriCoreError.InvalidOperation(
                code = "PLUGIN_STOP_FAILED",
                message = "Some plugins failed to stop: ${failures.joinToString("; ")}",
                context = emptyMap()
            )
        }
    }
    
    /**
     * Cleans up all plugins that implement [PluginLifecycle].
     * 
     * This should be called after [stop] for final cleanup.
     * 
     * **Example:**
     * ```kotlin
     * val vericore = VeriCore.create { ... }
     * // ... use vericore ...
     * vericore.stop().getOrThrow()
     * vericore.cleanup().getOrThrow()
     * ```
     * 
     * @return Result indicating success or failure
     */
    suspend fun cleanup(): Result<Unit> = vericoreCatching {
        val plugins = context.getAllPlugins().reversed()
        
        plugins.forEach { plugin ->
            if (plugin is PluginLifecycle) {
                try {
                    plugin.cleanup()
                } catch (e: Exception) {
                    // Log but don't fail on cleanup errors
                    // This is best effort cleanup
                }
            }
        }
    }
    
    // ========================================
    // Factory Methods
    // ========================================

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
        fun create(): VeriCore = create(VeriCoreDefaults.inMemory())
        
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
         * Starts from defaults and applies overrides.
         *
         * **Example - DSL Style (Recommended):**
         * ```kotlin
         * val vericore = VeriCore.create {
         *     kms = InMemoryKeyManagementService()
         *     walletFactory = TestkitWalletFactory()
         *     
         *     didMethods {
         *         + DidKeyMethod()
         *         + DidWebMethod()
         *     }
         *     
         *     blockchain {
         *         "ethereum:mainnet" to ethereumClient
         *         "algorand:testnet" to algorandClient
         *     }
         *     
         *     credentialServices {
         *         + MyCredentialService()
         *     }
         * }
         * ```
         *
         * @param configure Configuration block
         * @return Configured VeriCore instance
         */
        fun create(configure: VeriCoreConfig.Builder.() -> Unit): VeriCore {
            val builder = VeriCoreDefaults.inMemory().toBuilder()
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
     * 
     * @param methodName The DID method name (e.g., "key", "web", "ion")
     * @return The DID method if registered, null otherwise
     */
    fun getDidMethod(methodName: String): DidMethod? = didRegistry.get(methodName)
    
    /**
     * Gets available DID method names.
     * 
     * @return List of registered DID method names
     */
    fun getAvailableDidMethods(): List<String> = didRegistry.getAllMethodNames()
    
    /**
     * Gets a blockchain client by chain ID.
     * 
     * @param chainId Chain ID in CAIP-2 format (e.g., "ethereum:mainnet")
     * @return The blockchain client if registered, null otherwise
     */
    fun getBlockchainClient(chainId: String): BlockchainAnchorClient? =
        blockchainRegistry.get(chainId)
    
    /**
     * Gets available blockchain chain IDs.
     * 
     * @return List of registered blockchain chain IDs
     */
    fun getAvailableChains(): List<String> = blockchainRegistry.getAllChainIds()
    
    /**
     * Resolves a DID using registered methods.
     * 
     * **Note:** This is a lower-level method that returns `DidResolutionResult` directly.
     * For error handling with `Result<T>`, use [VeriCore.resolveDid] instead.
     * This method may throw exceptions if resolution fails.
     * 
     * @param did The DID string to resolve
     * @return Resolution result containing the document and metadata
     */
    suspend fun resolveDid(did: String): DidResolutionResult {
        return didRegistry.resolve(did)
    }

    /**
     * Gets the DID method registry.
     * 
     * @InternalApi This method exposes internal implementation details.
     * Use [getDidMethod] and [getAvailableDidMethods] for public operations.
     */
    @PublishedApi
    internal fun didMethodRegistry(): DidMethodRegistry = didRegistry

    /**
     * Gets the blockchain anchor registry.
     * 
     * @InternalApi This method exposes internal implementation details.
     * Use [getBlockchainClient] and [getAvailableChains] for public operations.
     */
    @PublishedApi
    internal fun blockchainRegistry(): BlockchainAnchorRegistry = blockchainRegistry

    /**
     * Gets the credential service registry.
     * 
     * @InternalApi This method exposes internal implementation details.
     * Access through VeriCore facade methods instead.
     */
    @PublishedApi
    internal fun credentialRegistry(): CredentialServiceRegistry = credentialRegistry

    /**
     * Gets the proof generator registry.
     * 
     * @InternalApi This method exposes internal implementation details.
     * Access through VeriCore facade methods instead.
     */
    @PublishedApi
    internal fun proofRegistry(): ProofGeneratorRegistry = proofRegistry
    
    /**
     * Gets all plugins that implement PluginLifecycle from all registries.
     * 
     * @return List of all plugins that implement PluginLifecycle
     */
    fun getAllPlugins(): List<Any> {
        val plugins = mutableListOf<Any>()
        
        // Add KMS if it implements PluginLifecycle
        if (kms is PluginLifecycle) {
            plugins.add(kms)
        }
        
        // Add wallet factory if it implements PluginLifecycle
        if (walletFactory is PluginLifecycle) {
            plugins.add(walletFactory)
        }
        
        // Add DID methods
        didRegistry.getAllMethodNames().forEach { methodName ->
            didRegistry.get(methodName)?.let { method ->
                if (method is PluginLifecycle) {
                    plugins.add(method)
                }
            }
        }
        
        // Add blockchain clients
        blockchainRegistry.getAllChainIds().forEach { chainId ->
            blockchainRegistry.get(chainId)?.let { client ->
                if (client is PluginLifecycle) {
                    plugins.add(client)
                }
            }
        }
        
        // Add credential services
        credentialRegistry.getAll().values.forEach { service ->
            if (service is PluginLifecycle) {
                plugins.add(service)
            }
        }
        
        // Add proof generators
        proofRegistry.getRegisteredTypes().forEach { proofType ->
            proofRegistry.get(proofType)?.let { generator ->
                if (generator is PluginLifecycle) {
                    plugins.add(generator)
                }
            }
        }
        
        return plugins
    }
    
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
        _kms = kms,
        _walletFactory = walletFactory,
        didRegistry = didRegistry.snapshot(),
        blockchainRegistry = blockchainRegistry.snapshot(),
        credentialRegistry = credentialRegistry.snapshot(),
        proofRegistry = proofRegistry.snapshot()
    )
    
    class Builder internal constructor(
        private var _kms: KeyManagementService?,
        private var _walletFactory: WalletFactory?,
        private val didRegistry: DidMethodRegistry,
        private val blockchainRegistry: BlockchainAnchorRegistry,
        private val credentialRegistry: CredentialServiceRegistry,
        private val proofRegistry: ProofGeneratorRegistry
    ) {
        /**
         * Sets the Key Management Service.
         * 
         * **Example:**
         * ```kotlin
         * kms = InMemoryKeyManagementService()
         * ```
         */
        var kms: KeyManagementService?
            get() = _kms
            set(value) {
                _kms = value
            }
        
        /**
         * Sets the Wallet Factory.
         * 
         * **Example:**
         * ```kotlin
         * walletFactory = TestkitWalletFactory()
         * ```
         */
        var walletFactory: WalletFactory?
            get() = _walletFactory
            set(value) {
                _walletFactory = value
            }
        
        /**
         * DSL block for registering DID methods.
         * 
         * **Example:**
         * ```kotlin
         * didMethods {
         *     + DidKeyMethod()
         *     + DidWebMethod()
         * }
         * ```
         */
        fun didMethods(block: DidMethodsBuilder.() -> Unit) {
            val builder = DidMethodsBuilder(didRegistry)
            builder.block()
        }
        
        /**
         * Internal builder for DID methods DSL.
         */
        inner class DidMethodsBuilder(
            private val registry: DidMethodRegistry
        ) {
            operator fun DidMethod.unaryPlus() {
                registry.register(this)
            }
        }
        
        /**
         * Registers a single DID method (backward compatibility).
         */
        fun registerDidMethod(method: DidMethod) {
            didRegistry.register(method)
        }
        
        /**
         * DSL block for registering blockchain clients.
         * 
         * **Example:**
         * ```kotlin
         * blockchain {
         *     "ethereum:mainnet" to ethereumClient
         *     "algorand:testnet" to algorandClient
         * }
         * ```
         */
        fun blockchain(block: MutableMap<String, BlockchainAnchorClient>.() -> Unit) {
            val clients = mutableMapOf<String, BlockchainAnchorClient>()
            clients.block()
            clients.forEach { (chainId, client) ->
                blockchainRegistry.register(chainId, client)
            }
        }
        
        /**
         * Registers a single blockchain client (backward compatibility).
         */
        fun registerBlockchainClient(chainId: String, client: BlockchainAnchorClient) {
            blockchainRegistry.register(chainId, client)
        }
        
        /**
         * DSL block for registering credential services.
         * 
         * **Example:**
         * ```kotlin
         * credentialServices {
         *     + MyCredentialService()
         * }
         * ```
         */
        fun credentialServices(block: CredentialServicesBuilder.() -> Unit) {
            val builder = CredentialServicesBuilder(credentialRegistry)
            builder.block()
        }
        
        /**
         * Internal builder for credential services DSL.
         */
        inner class CredentialServicesBuilder(
            private val registry: CredentialServiceRegistry
        ) {
            operator fun CredentialService.unaryPlus() {
                registry.register(this)
            }
        }
        
        /**
         * Registers a single credential service (backward compatibility).
         */
        fun registerCredentialService(service: CredentialService) {
            credentialRegistry.register(service)
        }
        
        /**
         * Unregisters a credential service.
         */
        fun unregisterCredentialService(providerName: String) {
            credentialRegistry.unregister(providerName)
        }

        /**
         * DSL block for registering proof generators.
         * 
         * **Example:**
         * ```kotlin
         * proofGenerators {
         *     + Ed25519ProofGenerator(signer)
         *     + BbsProofGenerator(signer)
         * }
         * ```
         */
        fun proofGenerators(block: ProofGeneratorsBuilder.() -> Unit) {
            val builder = ProofGeneratorsBuilder(proofRegistry)
            builder.block()
        }
        
        /**
         * Internal builder for proof generators DSL.
         */
        inner class ProofGeneratorsBuilder(
            private val registry: ProofGeneratorRegistry
        ) {
            operator fun ProofGenerator.unaryPlus() {
                registry.register(this)
            }
        }
        
        /**
         * Registers a single proof generator (backward compatibility).
         */
        fun registerProofGenerator(generator: ProofGenerator) {
            proofRegistry.register(generator)
        }

        /**
         * Unregisters a proof generator.
         */
        fun unregisterProofGenerator(proofType: String) {
            proofRegistry.unregister(proofType)
        }
        
        /**
         * Removes a blockchain client.
         */
        fun removeBlockchainClient(chainId: String) {
            blockchainRegistry.unregister(chainId)
        }
        
        /**
         * Removes a DID method.
         */
        fun removeDidMethod(methodName: String) {
            didRegistry.unregister(methodName)
        }
        
        /**
         * Builds the VeriCoreConfig from this builder.
         */
        fun build(): VeriCoreConfig {
            val kms = requireNotNull(_kms) { "KMS must be configured" }
            val walletFactory = requireNotNull(_walletFactory) { "WalletFactory must be configured" }
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
    private val delegate = WalletCreationOptionsBuilder()

    var label: String?
        get() = delegate.label
        set(value) {
            delegate.label = value
        }

    var storagePath: String?
        get() = delegate.storagePath
        set(value) {
            delegate.storagePath = value
        }

    var encryptionKey: String?
        get() = delegate.encryptionKey
        set(value) {
            delegate.encryptionKey = value
        }

    var enableOrganization: Boolean
        get() = delegate.enableOrganization
        set(value) {
            delegate.enableOrganization = value
        }

    var enablePresentation: Boolean
        get() = delegate.enablePresentation
        set(value) {
            delegate.enablePresentation = value
        }

    fun property(key: String, value: Any?) {
        delegate.property(key, value)
    }

    internal fun build(): WalletCreationOptions = delegate.build()
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
fun walletOptions(block: WalletOptionsBuilder.() -> Unit): WalletCreationOptions {
    val builder = WalletOptionsBuilder()
    builder.block()
    return builder.build()
}

