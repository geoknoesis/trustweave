package com.geoknoesis.vericore

import com.geoknoesis.vericore.anchor.BlockchainAnchorRegistry
import com.geoknoesis.vericore.core.*
import com.geoknoesis.vericore.credential.CredentialServiceRegistry
import com.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry
import com.geoknoesis.vericore.did.DidMethodRegistry
import com.geoknoesis.vericore.kms.KeyManagementService
import com.geoknoesis.vericore.services.*
import com.geoknoesis.vericore.spi.services.WalletFactory
import com.geoknoesis.vericore.spi.PluginLifecycle

/**
 * Main entry point for VeriCore library.
 * 
 * VeriCore provides a unified, elegant API for decentralized identity and trust operations.
 * The API is organized into focused services for better discoverability and clarity.
 * 
 * **Quick Start:**
 * ```kotlin
 * val vericore = VeriCore.create()
 * val did = vericore.dids.create()
 * val credential = vericore.credentials.issue(
 *     issuer = did.id,
 *     subject = buildJsonObject { put("name", "Alice") },
 *     config = IssuanceConfig(proofType = ProofType.Ed25519Signature2020, keyId = "key-1")
 * )
 * val wallet = vericore.wallets.create(holderDid = did.id)
 * wallet.store(credential)
 * ```
 * 
 * **Custom Configuration:**
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
 *     blockchains {
 *         "ethereum:mainnet" to ethereumClient
 *         "algorand:testnet" to algorandClient
 *     }
 * }
 * ```
 */
class VeriCore private constructor(
    private val context: VeriCoreContext
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
    suspend fun initialize() {
        context.getAllPlugins().forEach { plugin ->
            if (plugin is PluginLifecycle) {
                plugin.initialize()
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
internal class VeriCoreContext private constructor(
    internal val kms: KeyManagementService,
    internal val walletFactory: WalletFactory,
    internal val didRegistry: DidMethodRegistry,
    internal val blockchainRegistry: BlockchainAnchorRegistry,
    internal val credentialRegistry: CredentialServiceRegistry,
    internal val proofRegistry: ProofGeneratorRegistry
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
        fun blockchains(block: MutableMap<String, BlockchainAnchorClient>.() -> Unit) {
            val clients = mutableMapOf<String, BlockchainAnchorClient>()
            clients.block()
            clients.forEach { (chainId, client) ->
                blockchainRegistry.register(chainId, client)
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
            operator fun CredentialService.unaryPlus() {
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
 * 
 * @internal This class is internal. Use the [walletOptions] DSL function instead.
 */
internal class WalletOptionsBuilder {
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
