package com.trustweave

import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.anchor.DefaultBlockchainAnchorRegistry
import com.trustweave.core.*
import com.trustweave.credential.CredentialServiceRegistry
import com.trustweave.credential.proof.ProofGeneratorRegistry
import com.trustweave.credential.proof.ProofGenerator
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.did.registry.DidMethodRegistry
import com.trustweave.did.DidMethod
import com.trustweave.did.DidDocument
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.exception.DidException
import com.trustweave.kms.KeyManagementService
import com.trustweave.services.*
import com.trustweave.wallet.services.WalletFactory
import com.trustweave.wallet.services.WalletCreationOptionsBuilder
import com.trustweave.wallet.services.WalletCreationOptions
import com.trustweave.core.plugin.PluginLifecycle
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject

/**
 * Main entry point for TrustWeave library.
 *
 * TrustWeave provides a unified, elegant API for decentralized identity and trust operations.
 * Common operations are available as direct methods for simplicity, while complex operations
 * are organized in focused services.
 *
 * **Quick Start:**
 * ```kotlin
 * val trustweave = TrustWeave.create()
 * val did = trustweave.createDid()
 * val credential = trustweave.issueCredential(
 *     issuer = did.id,
 *     keyId = "key-1",
 *     subject = mapOf("name" to "Alice")
 * )
 * val wallet = trustweave.createWallet(holderDid = did.id)
 * wallet.store(credential)
 * ```
 *
 * **Custom Configuration:**
 * ```kotlin
 * val trustweave = TrustWeave.create {
 *     kms = InMemoryKeyManagementService()
 *     walletFactory = TestkitWalletFactory()
 *
 *     didMethods {
 *         + DidKeyMethod()
 *         + DidWebMethod()
 *     }
 *
 *     blockchains {
 *         "ethereum:mainnet" to ethereumClient
 *         "algorand:testnet" to algorandClient
 *     }
 * }
 * ```
 */
class TrustWeave private constructor(
    private val context: TrustWeaveContext
) {
    /**
     * Blockchain anchoring service.
     *
     * Provides methods for anchoring data to blockchains and reading anchored data.
     * This is a complex service with multiple methods, so it's kept as a separate property.
     */
    val blockchains: BlockchainService = BlockchainService(context)

    /**
     * Smart contract operations service.
     *
     * Provides methods for creating, binding, and executing smart contracts.
     * This is a complex service with multiple methods, so it's kept as a separate property.
     */
    val contracts: ContractService = ContractService(context)

    // ========================================
    // DID Operations (Direct Methods)
    // ========================================

    /**
     * Creates a new DID using the specified method.
     *
     * Simple overload for common case - creates a DID with default method ("key").
     *
     * **Example:**
     * ```kotlin
     * val did = trustweave.createDid()  // Uses default method "key"
     * val did = trustweave.createDid(method = "web")  // Uses did:web method
     * ```
     *
     * @param method DID method name (default: "key")
     * @return The created DID document
     * @throws DidException.DidMethodNotRegistered if method is not registered
     */
    suspend fun createDid(method: String = "key"): DidDocument {
        val availableMethods = context.getAvailableDidMethods()
        if (method !in availableMethods) {
            throw DidException.DidMethodNotRegistered(
                method = method,
                availableMethods = availableMethods
            )
        }

        val didMethod = context.getDidMethod(method)
            ?: throw DidException.DidMethodNotRegistered(
                method = method,
                availableMethods = availableMethods
            )

        return didMethod.createDid(com.trustweave.did.DidCreationOptions())
    }

    /**
     * Creates a DID using a fluent builder for advanced configuration.
     *
     * **Example:**
     * ```kotlin
     * val did = trustweave.createDid(method = "key") {
     *     algorithm = KeyAlgorithm.ED25519
     *     purpose(KeyPurpose.AUTHENTICATION)
     * }
     * ```
     *
     * @param method DID method name (default: "key")
     * @param configure Configuration block for DID creation
     * @return The created DID document
     */
    suspend fun createDid(
        method: String = "key",
        configure: com.trustweave.did.DidCreationOptionsBuilder.() -> Unit
    ): DidDocument {
        val options = com.trustweave.did.didCreationOptions(configure)
        return createDid(method, options)
    }

    /**
     * Internal helper for creating DID with options.
     */
    private suspend fun createDid(method: String, options: com.trustweave.did.DidCreationOptions): DidDocument {
        val availableMethods = context.getAvailableDidMethods()
        if (method !in availableMethods) {
            throw DidException.DidMethodNotRegistered(
                method = method,
                availableMethods = availableMethods
            )
        }

        val didMethod = context.getDidMethod(method)
            ?: throw DidException.DidMethodNotRegistered(
                method = method,
                availableMethods = availableMethods
            )

        return didMethod.createDid(options)
    }

    /**
     * Resolves a DID to its document.
     *
     * Returns a sealed result type for exhaustive error handling.
     *
     * **Example:**
     * ```kotlin
     * when (val result = trustweave.resolveDid("did:key:z6Mk...")) {
     *     is DidResolutionResult.Success -> {
     *         println("Resolved: ${result.document.id}")
     *     }
     *     is DidResolutionResult.Failure.NotFound -> {
     *         println("DID not found")
     *     }
     *     // ... handle other cases
     * }
     * ```
     *
     * @param did The DID string to resolve
     * @return Resolution result containing the document and metadata
     */
    suspend fun resolveDid(did: String): DidResolutionResult {
        // Validate DID format
        com.trustweave.did.validation.DidValidator.validateFormat(did).let {
            if (!it.isValid()) {
                return DidResolutionResult.Failure.InvalidFormat(
                    did = did,
                    reason = it.errorMessage() ?: "Invalid DID format"
                )
            }
        }

        // Validate method is registered
        val availableMethods = context.getAvailableDidMethods()
        com.trustweave.did.validation.DidValidator.validateMethod(did, availableMethods).let {
            if (!it.isValid()) {
                val methodName = com.trustweave.did.validation.DidValidator.extractMethod(did) ?: "unknown"
                return DidResolutionResult.Failure.MethodNotRegistered(
                    method = methodName,
                    availableMethods = availableMethods
                )
            }
        }

        return context.resolveDid(did)
    }

    // ========================================
    // Credential Operations (Direct Methods)
    // ========================================

    /**
     * Issues a verifiable credential.
     *
     * Simple overload for common case - issues a credential with minimal configuration.
     *
     * **Example:**
     * ```kotlin
     * val credential = trustweave.issueCredential(
     *     issuer = "did:key:issuer",
     *     keyId = "key-1",
     *     subject = mapOf(
     *         "id" to "did:key:subject",
     *         "name" to "Alice"
     *     ),
     *     credentialType = "EducationCredential"
     * )
     * ```
     *
     * @param issuer DID of the credential issuer
     * @param keyId The key ID for signing (can be fragment like "key-1" or full like "did:key:...#key-1")
     * @param subject The credential subject as a map of properties
     * @param credentialType The credential type (default: "VerifiableCredential")
     * @param expirationDate Optional expiration date (ISO 8601)
     * @return The issued credential with proof
     */
    suspend fun issueCredential(
        issuer: String,
        keyId: String,
        subject: Map<String, Any>,
        credentialType: String = "VerifiableCredential",
        expirationDate: String? = null
    ): VerifiableCredential {
        val subjectJson = buildJsonObject {
            subject.forEach { (key, value) ->
                when (value) {
                    is Map<*, *> -> {
                        put(key, buildJsonObject {
                            @Suppress("UNCHECKED_CAST")
                            (value as? Map<String, Any> ?: emptyMap()).forEach { (k, v) ->
                                put(k, v.toString())
                            }
                        })
                    }
                    else -> put(key, value.toString())
                }
            }
        }

        val config = com.trustweave.services.IssuanceConfig(
            proofType = com.trustweave.credential.proof.ProofType.Ed25519Signature2020,
            keyId = keyId,
            issuerDid = issuer
        )

        return issueCredential(
            issuer = issuer,
            subject = subjectJson,
            config = config,
            types = listOf(credentialType),
            expirationDate = expirationDate
        )
    }

    /**
     * Issues a verifiable credential with advanced configuration.
     *
     * **Example:**
     * ```kotlin
     * val credential = trustweave.issueCredential(
     *     issuer = "did:key:issuer",
     *     subject = buildJsonObject { put("name", "Alice") },
     *     config = IssuanceConfig(
     *         proofType = ProofType.Ed25519Signature2020,
     *         keyId = "key-1",
     *         issuerDid = "did:key:issuer"
     *     ),
     *     types = listOf("EducationCredential")
     * )
     * ```
     *
     * @param issuer DID of the credential issuer
     * @param subject The credential subject as JSON
     * @param config Issuance configuration
     * @param types Credential types (VerifiableCredential is added automatically)
     * @param expirationDate Optional expiration date (ISO 8601)
     * @return The issued credential with proof
     */
    suspend fun issueCredential(
        issuer: String,
        subject: kotlinx.serialization.json.JsonElement,
        config: com.trustweave.services.IssuanceConfig,
        types: List<String> = listOf("VerifiableCredential"),
        expirationDate: String? = null
    ): VerifiableCredential {
        val credentialService = com.trustweave.services.CredentialService(context)
        return credentialService.issue(issuer, subject, config, types, expirationDate)
    }

    /**
     * Verifies a verifiable credential.
     *
     * **Example:**
     * ```kotlin
     * val result = trustweave.verifyCredential(credential)
     * if (result.valid) {
     *     println("Credential is valid")
     * } else {
     *     println("Errors: ${result.allErrors}")
     * }
     * ```
     *
     * @param credential The credential to verify
     * @param config Verification configuration (optional)
     * @return Verification result with detailed status
     */
    suspend fun verifyCredential(
        credential: VerifiableCredential,
        config: com.trustweave.services.VerificationConfig = com.trustweave.services.VerificationConfig()
    ): com.trustweave.credential.CredentialVerificationResult {
        val credentialService = com.trustweave.services.CredentialService(context)
        return credentialService.verify(credential, config)
    }

    // ========================================
    // Wallet Operations (Direct Methods)
    // ========================================

    /**
     * Creates a wallet with the specified configuration.
     *
     * **Example:**
     * ```kotlin
     * // Simple usage
     * val wallet = trustweave.createWallet(holderDid = "did:key:holder")
     *
     * // With custom type and options
     * val wallet = trustweave.createWallet(
     *     holderDid = "did:key:holder",
     *     type = WalletType.Database,
     *     options = walletOptions {
     *         label = "My Wallet"
     *         enableOrganization = true
     *     }
     * )
     * ```
     *
     * @param holderDid DID of the wallet holder (required)
     * @param walletId Optional wallet identifier (generated if not provided)
     * @param type Wallet type (default: InMemory)
     * @param options Provider-specific configuration
     * @return The created wallet instance
     * @throws WalletException.WalletCreationFailed if wallet creation fails
     */
    suspend fun createWallet(
        holderDid: String,
        walletId: String? = null,
        type: com.trustweave.wallet.WalletType = com.trustweave.wallet.WalletType.InMemory,
        options: WalletCreationOptions = WalletCreationOptions()
    ): com.trustweave.wallet.Wallet {
        val finalWalletId = walletId ?: java.util.UUID.randomUUID().toString()
        val walletService = com.trustweave.services.WalletService(context)
        return walletService.create(holderDid, finalWalletId, type, options)
    }

    // ========================================
    // Plugin Lifecycle
    // ========================================

    /**
     * Initializes all plugins that implement PluginLifecycle.
     *
     * This method should be called before using any plugins to ensure
     * they are properly initialized.
     */
    suspend fun initialize(config: Map<String, Any?> = emptyMap()) {
        context.getAllPlugins().forEach { plugin ->
            if (plugin is PluginLifecycle) {
                plugin.initialize(config)
            }
        }
    }

    /**
     * Starts all plugins that implement PluginLifecycle.
     *
     * This method should be called after initialize() and before
     * starting operations.
     */
    suspend fun start() {
        context.getAllPlugins().forEach { plugin ->
            if (plugin is PluginLifecycle) {
                plugin.start()
            }
        }
    }

    /**
     * Stops all plugins that implement PluginLifecycle.
     *
     * This method should be called when shutting down to gracefully
     * stop all plugins.
     */
    suspend fun stop() {
        context.getAllPlugins().forEach { plugin ->
            if (plugin is PluginLifecycle) {
                plugin.stop()
            }
        }
    }

    /**
     * Cleans up all plugins that implement PluginLifecycle.
     *
     * This method should be called after stop() to clean up resources.
     */
    suspend fun cleanup() {
        context.getAllPlugins().forEach { plugin ->
            if (plugin is PluginLifecycle) {
                plugin.cleanup()
            }
        }
    }

    // ========================================
    // Factory Methods
    // ========================================

    companion object {
        /**
         * Creates a TrustWeave instance with sensible defaults.
         *
         * Includes:
         * - In-memory key management
         * - did:key method support
         * - No blockchain anchoring (must be configured separately)
         *
         * **Example:**
         * ```kotlin
         * val trustweave = TrustWeave.create()
         * ```
         *
         * @return TrustWeave instance with default configuration
         */
        fun create(): TrustWeave = create(TrustWeaveDefaults.inMemory())

        /**
         * Creates a TrustWeave instance from a configuration object.
         *
         * @param config Fully constructed configuration
         */
        fun create(config: TrustWeaveConfig): TrustWeave {
            return TrustWeave(TrustWeaveContext.fromConfig(config))
        }

        /**
         * Creates a TrustWeave instance with custom configuration.
         * Starts from defaults and applies overrides.
         *
         * **Example - DSL Style (Recommended):**
         * ```kotlin
         * val trustweave = TrustWeave.create {
         *     kms = InMemoryKeyManagementService()
         *     walletFactory = TestkitWalletFactory()
         *
         *     didMethods {
         *         + DidKeyMethod()
         *         + DidWebMethod()
         *     }
         *
 *     blockchains {
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
         * @return Configured TrustWeave instance
         */
        fun create(configure: TrustWeaveConfig.Builder.() -> Unit): TrustWeave {
            val builder = TrustWeaveDefaults.inMemory().toBuilder()
            builder.configure()
            return create(builder.build())
        }
    }
}

/**
 * TrustWeave context holding all registries and services.
 *
 * This class encapsulates all dependencies and configuration,
 * making it thread-safe and testable without global state.
 */
class TrustWeaveContext private constructor(
    val kms: KeyManagementService,
    val walletFactory: WalletFactory,
    val didRegistry: DidMethodRegistry,
    val blockchainRegistry: BlockchainAnchorRegistry,
    val credentialRegistry: CredentialServiceRegistry,
    val proofRegistry: ProofGeneratorRegistry
) {
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

    /**
     * Gets available blockchain chain IDs.
     */
    fun getAvailableChains(): List<String> = blockchainRegistry.getAllChainIds()

    /**
     * Gets blockchain client for a chain ID.
     */
    fun getBlockchainClient(chainId: String): BlockchainAnchorClient? = blockchainRegistry.get(chainId)

    /**
     * Gets available DID method names.
     */
    fun getAvailableDidMethods(): List<String> = didRegistry.getAllMethodNames()

    /**
     * Gets DID method by name.
     */
    fun getDidMethod(method: String): DidMethod? = didRegistry.get(method)

    /**
     * Resolves a DID.
     */
    suspend fun resolveDid(did: String): DidResolutionResult {
        // Extract method name from DID (format: did:method:identifier)
        // e.g., "did:key:z6Mk..." -> "key"
        val methodName = if (did.startsWith("did:")) {
            did.substringAfter("did:").substringBefore(":")
        } else {
            did.substringBefore(":")
        }
        val method = didRegistry.get(methodName)
            ?: throw DidException.DidMethodNotRegistered(
                method = methodName,
                availableMethods = didRegistry.getAllMethodNames()
            )
        return method.resolveDid(did)
    }

    companion object {
        fun fromConfig(config: TrustWeaveConfig): TrustWeaveContext {
            return TrustWeaveContext(
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
 * Strongly typed configuration for [TrustWeave].
 */
data class TrustWeaveConfig(
    val kms: KeyManagementService,
    val walletFactory: WalletFactory,
    val didRegistry: DidMethodRegistry,
    val blockchainRegistry: BlockchainAnchorRegistry = BlockchainAnchorRegistry(),
    val credentialRegistry: CredentialServiceRegistry = CredentialServiceRegistry.create(),
    val proofRegistry: ProofGeneratorRegistry = ProofGeneratorRegistry()
) {
    fun toBuilder(): Builder {
        // For blockchainRegistry, we need to ensure it's mutable so chains can be registered
        // during the configure phase. We'll take a snapshot, but ensure it's a DefaultBlockchainAnchorRegistry
        // instance that can be modified.
        val mutableBlockchainRegistry = when (val reg = blockchainRegistry.snapshot()) {
            is DefaultBlockchainAnchorRegistry -> reg
            else -> {
                // If snapshot returns a different type, create a new mutable registry and copy existing chains
                val newRegistry = DefaultBlockchainAnchorRegistry()
                reg.getAllChainIds().forEach { chainId ->
                    reg.get(chainId)?.let { client ->
                        newRegistry.register(chainId, client)
                    }
                }
                newRegistry
            }
        }

        return Builder(
            _kms = kms,
            _walletFactory = walletFactory,
            didRegistry = didRegistry.snapshot(),
            blockchainRegistry = mutableBlockchainRegistry,
            credentialRegistry = credentialRegistry.snapshot(),
            proofRegistry = proofRegistry.snapshot()
        )
    }

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
         * DSL block for registering blockchain clients.
         *
         * **Example:**
         * ```kotlin
         * blockchains {
         *     "ethereum:mainnet" to ethereumClient
         *     "algorand:testnet" to algorandClient
         * }
         * ```
         */
        fun blockchains(block: BlockchainsBuilder.() -> Unit) {
            val builder = BlockchainsBuilder()
            builder.block()
            builder.clients.forEach { (chainId, client) ->
                blockchainRegistry.register(chainId, client)
            }
        }

        /**
         * Internal builder for blockchains DSL.
         */
        inner class BlockchainsBuilder {
            internal val clients = mutableMapOf<String, BlockchainAnchorClient>()

            /**
             * Register a blockchain client using the `to` operator.
             *
             * **Example:**
             * ```kotlin
             * blockchains {
             *     "algorand:testnet" to algorandClient
             * }
             * ```
             */
            infix fun String.to(client: BlockchainAnchorClient) {
                clients[this@to] = client
            }

            /**
             * Support indexed access for backward compatibility.
             *
             * **Example:**
             * ```kotlin
             * blockchains {
             *     this[chainId] = anchorClient
             * }
             * ```
             */
            operator fun set(chainId: String, client: BlockchainAnchorClient) {
                clients[chainId] = client
            }

            /**
             * Support indexed access getter.
             */
            operator fun get(chainId: String): BlockchainAnchorClient? {
                return clients[chainId]
            }

            /**
             * Support put() method for backward compatibility.
             */
            fun put(chainId: String, client: BlockchainAnchorClient) {
                clients[chainId] = client
            }
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
            operator fun com.trustweave.credential.CredentialService.unaryPlus() {
                registry.register(this)
            }
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
         * Builds the TrustWeaveConfig from this builder.
         */
        fun build(): TrustWeaveConfig {
            val kms = requireNotNull(_kms) { "KMS must be configured" }
            val walletFactory = requireNotNull(_walletFactory) { "WalletFactory must be configured" }
            require(didRegistry.size() > 0) { "At least one DID method must be registered" }

        return TrustWeaveConfig(
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
 *
 * Builder for wallet creation options.
 * Use the [walletOptions] DSL function instead of constructing directly.
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
