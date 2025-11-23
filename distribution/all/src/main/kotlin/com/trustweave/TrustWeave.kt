package com.trustweave

import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.anchor.DefaultBlockchainAnchorRegistry
import com.trustweave.core.*
import com.trustweave.credential.CredentialServiceRegistry
import com.trustweave.credential.proof.ProofGeneratorRegistry
import com.trustweave.credential.proof.ProofGenerator
import com.trustweave.did.DidMethodRegistry
import com.trustweave.did.DidMethod
import com.trustweave.did.DidResolutionResult
import com.trustweave.kms.KeyManagementService
import com.trustweave.services.*
import com.trustweave.wallet.services.WalletFactory
import com.trustweave.wallet.services.WalletCreationOptionsBuilder
import com.trustweave.wallet.services.WalletCreationOptions
import com.trustweave.core.PluginLifecycle

/**
 * Main entry point for TrustWeave library.
 * 
 * TrustWeave provides a unified, elegant API for decentralized identity and trust operations.
 * The API is organized into focused services for better discoverability and clarity.
 * 
 * **Quick Start:**
 * ```kotlin
 * val trustweave = TrustWeave.create()
 * val did = trustweave.dids.create()
 * val credential = trustweave.credentials.issue(
 *     issuer = did.id,
 *     subject = buildJsonObject { put("name", "Alice") },
 *     config = IssuanceConfig(proofType = ProofType.Ed25519Signature2020, keyId = "key-1")
 * )
 * val wallet = trustweave.wallets.create(holderDid = did.id)
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
     * DID operations service.
     * 
     * Provides methods for creating, resolving, updating, and deactivating DIDs.
     */
    val dids: DidService = DidService(context)
    
    /**
     * Credential operations service.
     * 
     * Provides methods for issuing and verifying verifiable credentials.
     */
    val credentials: CredentialService = CredentialService(context)
    
    /**
     * Wallet operations service.
     * 
     * Provides methods for creating wallets. All other wallet operations
     * should be performed directly on the wallet instance.
     */
    val wallets: WalletService = WalletService(context)
    
    /**
     * Blockchain anchoring service.
     * 
     * Provides methods for anchoring data to blockchains and reading anchored data.
     */
    val blockchains: BlockchainService = BlockchainService(context)
    
    /**
     * Smart contract operations service.
     * 
     * Provides methods for creating, binding, and executing smart contracts.
     */
    val contracts: ContractService = ContractService(context)
    
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
            ?: throw TrustWeaveError.DidMethodNotRegistered(
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