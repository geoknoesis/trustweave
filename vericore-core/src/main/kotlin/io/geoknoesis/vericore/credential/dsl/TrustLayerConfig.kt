package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.credential.did.CredentialDidResolver
import io.geoknoesis.vericore.credential.did.asCredentialDidResolution
import io.geoknoesis.vericore.credential.issuer.CredentialIssuer
import io.geoknoesis.vericore.credential.proof.Ed25519ProofGenerator
import io.geoknoesis.vericore.credential.proof.ProofGenerator
import io.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry
import io.geoknoesis.vericore.spi.services.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

/**
 * Unified Trust Layer Configuration.
 * 
 * Centralizes configuration for anchor layer (blockchain), credential keys (KMS), 
 * and DID methods into a single configuration point. This makes it easier to 
 * configure different trust layer systems and provides context for DSL operations.
 * 
 * **Example Usage**:
 * ```kotlin
 * val trustLayer = trustLayer {
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
 *     anchor {
 *         chain("algorand:testnet") {
 *             provider("algorand")
 *         }
 *     }
 *     
 *     credentials {
 *         defaultProofType("Ed25519Signature2020")
 *         autoAnchor(true)
 *         defaultChain("algorand:testnet")
 *     }
 * }
 * ```
 */
class TrustLayerConfig private constructor(
    val name: String,
    val kms: Any, // KeyManagementService - using Any to avoid dependency
    val didMethods: Map<String, Any>, // DidMethod - using Any to avoid dependency
    val anchorClients: Map<String, Any>, // BlockchainAnchorClient - using Any to avoid dependency
    val credentialConfig: CredentialConfig,
    val issuer: CredentialIssuer,
    val didResolver: io.geoknoesis.vericore.credential.did.CredentialDidResolver? = null,
    val statusListManager: Any? = null, // StatusListManager - using Any to avoid dependency
    val trustRegistry: Any? = null, // TrustRegistry - using Any to avoid dependency
    val walletFactory: WalletFactory? = null // Wallet factory for creating wallet instances
) {
    /**
     * Credential configuration within trust layer.
     */
    data class CredentialConfig(
        val defaultProofType: String = "Ed25519Signature2020",
        val autoAnchor: Boolean = false,
        val defaultChain: String? = null
    )
    
    /**
     * Builder for trust layer configuration.
     */
    class Builder(private val name: String = "default") {
        private var kms: Any? = null // KeyManagementService - using Any to avoid dependency
        private var kmsProvider: String? = null
        private var kmsAlgorithm: String = "Ed25519"
        private var kmsSigner: (suspend (ByteArray, String) -> ByteArray)? = null // Direct signer function
        private val didMethods = mutableMapOf<String, Any>() // DidMethod - using Any to avoid dependency
        private val didMethodConfigs = mutableMapOf<String, DidMethodConfig>()
        private val anchorClients = mutableMapOf<String, Any>() // BlockchainAnchorClient - using Any to avoid dependency
        private val anchorConfigs = mutableMapOf<String, AnchorConfig>()
        private var defaultProofType: String = "Ed25519Signature2020"
        private var autoAnchor: Boolean = false
        private var defaultChain: String? = null
        private var revocationProvider: String? = null
        private var trustProvider: String? = null
        
        // Factory instances (optional - will attempt reflection-based adapters if not provided)
        private var kmsFactory: KmsFactory? = null
        private var didMethodFactory: DidMethodFactory? = null
        private var anchorClientFactory: BlockchainAnchorClientFactory? = null
        private var statusListManagerFactory: StatusListManagerFactory? = null
        private var trustRegistryFactory: TrustRegistryFactory? = null
        private var walletFactory: WalletFactory? = null
        
        /**
         * Set factory instances for dependency resolution.
         * If not provided, will attempt to load default adapters reflectively.
         */
        fun factories(
            kmsFactory: KmsFactory? = null,
            didMethodFactory: DidMethodFactory? = null,
            anchorClientFactory: BlockchainAnchorClientFactory? = null,
            statusListManagerFactory: StatusListManagerFactory? = null,
            trustRegistryFactory: TrustRegistryFactory? = null,
            walletFactory: WalletFactory? = null
        ) {
            this.kmsFactory = kmsFactory
            this.didMethodFactory = didMethodFactory
            this.anchorClientFactory = anchorClientFactory
            this.statusListManagerFactory = statusListManagerFactory
            this.trustRegistryFactory = trustRegistryFactory
            this.walletFactory = walletFactory
        }
        
        /**
         * Configure key management service.
         */
        fun keys(block: KeysBuilder.() -> Unit) {
            val builder = KeysBuilder()
            builder.block()
            kmsProvider = builder.provider
            kmsAlgorithm = builder.algorithm ?: "Ed25519"
            kms = builder.kms
            // Store signer function if provided
            if (builder.signer != null) {
                // Store signer in a way that can be accessed later
                kmsSigner = builder.signer
            }
        }
        
        /**
         * Set custom KMS directly.
         */
        fun customKms(kms: Any) {
            this.kms = kms
        }
        
        /**
         * Configure DID methods.
         */
        fun did(block: DidBuilder.() -> Unit) {
            val builder = DidBuilder()
            builder.block()
            didMethodConfigs.putAll(builder.methods)
        }
        
        /**
         * Configure anchor layer (blockchain).
         */
        fun anchor(block: AnchorBuilder.() -> Unit) {
            val builder = AnchorBuilder()
            builder.block()
            anchorConfigs.putAll(builder.chains)
        }
        
        /**
         * Configure credential services.
         */
        fun credentials(block: CredentialsBuilder.() -> Unit) {
            val builder = CredentialsBuilder()
            builder.block()
            defaultProofType = builder.defaultProofType ?: defaultProofType
            autoAnchor = builder.autoAnchor ?: autoAnchor
            defaultChain = builder.defaultChain
        }
        
        /**
         * Configure revocation services.
         */
        fun revocation(block: RevocationConfigBuilder.() -> Unit) {
            val builder = RevocationConfigBuilder()
            builder.block()
            revocationProvider = builder.provider
        }
        
        /**
         * Configure schema services.
         */
        fun schemas(block: SchemaConfigBuilder.() -> Unit) {
            val builder = SchemaConfigBuilder()
            builder.block()
            // Store schema config for later use
        }
        
        /**
         * Configure trust registry.
         */
        fun trust(block: TrustConfigBuilder.() -> Unit) {
            val builder = TrustConfigBuilder()
            builder.block()
            trustProvider = builder.provider
        }
        
        /**
         * Build the trust layer configuration.
         */
        suspend fun build(): TrustLayerConfig {
            // Resolve KMS
            val (resolvedKms, resolvedSigner) = if (kms != null) {
                Pair(kms!!, kmsSigner)
            } else {
                resolveKms(kmsProvider ?: "inMemory", kmsAlgorithm)
            }
            requireNotNull(resolvedKms) {
                "KMS cannot be null. Provide a custom KMS or ensure a provider is configured."
            }
            
            // Use resolved signer if available, otherwise use the one provided via DSL
            val finalSigner = kmsSigner ?: resolvedSigner
            
            // Resolve DID methods
            val resolvedDidMethods = mutableMapOf<String, Any>()
            for ((methodName, config) in didMethodConfigs) {
                val method = resolveDidMethod(methodName, config, resolvedKms)
                resolvedDidMethods[methodName] = method
                // Register in DidRegistry using service
                val didRegistryService = AdapterLoader.didRegistryService()
                if (didRegistryService != null) {
                    didRegistryService.register(method)
                } else {
                    // Fallback to reflection if service not available
                    try {
                        val didRegistryClass = Class.forName("io.geoknoesis.vericore.did.DidRegistry")
                        val registerMethod = didRegistryClass.getMethod("register", Any::class.java)
                        registerMethod.invoke(null, method)
                    } catch (e: Exception) {
                        // DidRegistry not available - this is OK for DSL usage
                    }
                }
            }
            
            // Resolve anchor clients
            val resolvedAnchorClients = mutableMapOf<String, Any>()
            for ((chainId, config) in anchorConfigs) {
                val client = resolveAnchorClient(chainId, config)
                resolvedAnchorClients[chainId] = client
                // Register in BlockchainRegistry using service
                val blockchainRegistryService = AdapterLoader.blockchainRegistryService()
                if (blockchainRegistryService != null) {
                    try {
                        val registerMethod = blockchainRegistryService::class.java.getMethod("register", String::class.java, Any::class.java)
                        registerMethod.isAccessible = true
                        registerMethod.invoke(blockchainRegistryService, chainId, client)
                    } catch (e: Exception) {
                        // Adapter present but failed to register - fall back to reflection
                        try {
                            val blockchainRegistryClass = Class.forName("io.geoknoesis.vericore.anchor.BlockchainRegistry")
                            val registerMethod = blockchainRegistryClass.getMethod("register", String::class.java, Any::class.java)
                            registerMethod.invoke(null, chainId, client)
                        } catch (_: Exception) {
                            // Ignored - registry not available is acceptable for DSL usage
                        }
                    }
                } else {
                    // Fallback to reflection if service not available
                    try {
                        val blockchainRegistryClass = Class.forName("io.geoknoesis.vericore.anchor.BlockchainRegistry")
                        val registerMethod = blockchainRegistryClass.getMethod("register", String::class.java, Any::class.java)
                        registerMethod.invoke(null, chainId, client)
                    } catch (e: Exception) {
                        // BlockchainRegistry not available - this is OK for DSL usage
                    }
                }
            }
            
            // Create proof generator
            val proofGenerator = createProofGenerator(defaultProofType, resolvedKms, finalSigner)
            ProofGeneratorRegistry.register(proofGenerator)
            
            // Resolve status list manager if revocation is configured
            val resolvedStatusListManager = if (revocationProvider != null) {
                resolveStatusListManager(revocationProvider!!)
            } else {
                null
            }
            
            // Resolve trust registry if configured
            val resolvedTrustRegistry = if (trustProvider != null) {
                resolveTrustRegistry(trustProvider!!)
            } else {
                null
            }
            
            val credentialDidResolver = CredentialDidResolver { did ->
                val service = AdapterLoader.didRegistryService() ?: return@CredentialDidResolver null
                runCatching { service.resolve(did) }.getOrNull()?.asCredentialDidResolution()
            }

            val issuer = CredentialIssuer(
                proofGenerator = proofGenerator,
                resolveDid = { did ->
                    val resolution = credentialDidResolver.resolve(did)
                    resolution?.isResolvable ?: true
                }
            )
            
            // Resolve wallet factory
            val resolvedWalletFactory = walletFactory ?: getDefaultWalletFactory()
            
            return TrustLayerConfig(
                name = name,
                kms = resolvedKms,
                didMethods = resolvedDidMethods,
                anchorClients = resolvedAnchorClients,
                credentialConfig = CredentialConfig(
                    defaultProofType = defaultProofType,
                    autoAnchor = autoAnchor,
                    defaultChain = defaultChain
                ),
                issuer = issuer,
                didResolver = credentialDidResolver,
                statusListManager = resolvedStatusListManager,
                trustRegistry = resolvedTrustRegistry,
                walletFactory = resolvedWalletFactory
            )
        }
        
        private suspend fun resolveKms(providerName: String, algorithm: String): Pair<Any, (suspend (ByteArray, String) -> ByteArray)?> {
            val factory = kmsFactory ?: getDefaultKmsFactory()
            return factory.createFromProvider(providerName, algorithm)
        }
        
        private fun getDefaultKmsFactory(): KmsFactory {
            // Try to get factory from testkit
            try {
                val factoryClass = Class.forName("io.geoknoesis.vericore.testkit.services.TestkitKmsFactory")
                val factory = factoryClass.getDeclaredConstructor().newInstance() as? KmsFactory
                if (factory != null) return factory
            } catch (e: Exception) {
                // Testkit not available, fall through
            }
            
            // Fallback: throw error asking user to provide factory
            throw IllegalStateException(
                "KMS factory not available. " +
                "Provide a factory via Builder.factories() or ensure vericore-testkit is on classpath."
            )
        }
        
        private suspend fun resolveDidMethod(
            methodName: String,
            config: DidMethodConfig,
            kms: Any
        ): Any {
            requireNotNull(kms) { "KMS cannot be null when resolving DID method: $methodName" }
            val factory = didMethodFactory ?: getDefaultDidMethodFactory()
            val method = factory.create(methodName, config, kms)
            return method ?: throw IllegalStateException(
                "DID method '$methodName' not found. " +
                "Ensure appropriate DID method provider is on classpath."
            )
        }
        
        private fun getDefaultDidMethodFactory(): DidMethodFactory {
            // Try to get factory from testkit
            try {
                val factoryClass = Class.forName("io.geoknoesis.vericore.testkit.services.TestkitDidMethodFactory")
                val factory = factoryClass.getDeclaredConstructor().newInstance() as? DidMethodFactory
                if (factory != null) return factory
            } catch (e: Exception) {
                // Testkit not available, fall through
            }
            
            throw IllegalStateException(
                "DID method factory not available. " +
                "Provide a factory via Builder.factories() or ensure vericore-testkit is on classpath."
            )
        }
        
        private suspend fun resolveAnchorClient(
            chainId: String,
            config: AnchorConfig
        ): Any {
            val factory = anchorClientFactory ?: getDefaultAnchorClientFactory()
            return factory.create(chainId, config.provider, config.options)
        }
        
        private fun getDefaultAnchorClientFactory(): BlockchainAnchorClientFactory {
            // Try to get factory from testkit
            try {
                val factoryClass = Class.forName("io.geoknoesis.vericore.testkit.services.TestkitBlockchainAnchorClientFactory")
                val factory = factoryClass.getDeclaredConstructor().newInstance() as? BlockchainAnchorClientFactory
                if (factory != null) return factory
            } catch (e: Exception) {
                // Testkit not available, fall through
            }
            
            throw IllegalStateException(
                "BlockchainAnchorClient factory not available. " +
                "Provide a factory via Builder.factories() or ensure vericore-testkit is on classpath."
            )
        }
        
        private suspend fun resolveStatusListManager(providerName: String): Any {
            val factory = statusListManagerFactory ?: getDefaultStatusListManagerFactory()
            return factory.create(providerName)
        }
        
        private fun getDefaultStatusListManagerFactory(): StatusListManagerFactory {
            // Try to get factory from testkit
            try {
                val factoryClass = Class.forName("io.geoknoesis.vericore.testkit.services.TestkitStatusListManagerFactory")
                val factory = factoryClass.getDeclaredConstructor().newInstance() as? StatusListManagerFactory
                if (factory != null) return factory
            } catch (e: Exception) {
                // Testkit not available, fall through
            }
            
            // Fallback: try to instantiate InMemoryStatusListManager directly (it's in vericore-core)
            try {
                val managerClass = Class.forName("io.geoknoesis.vericore.credential.revocation.InMemoryStatusListManager")
                // Return a factory that creates instances
                return object : StatusListManagerFactory {
                    override suspend fun create(providerName: String): Any {
                        if (providerName == "inMemory") {
                            return managerClass.getDeclaredConstructor().newInstance()
                        }
                        throw IllegalStateException("Only 'inMemory' provider supported")
                    }
                }
            } catch (e: Exception) {
                // Fall through
            }
            
            throw IllegalStateException(
                "StatusListManager factory not available. " +
                "Provide a factory via Builder.factories() or ensure vericore-testkit is on classpath."
            )
        }
        
        private suspend fun resolveTrustRegistry(providerName: String): Any {
            val factory = trustRegistryFactory ?: getDefaultTrustRegistryFactory()
            return factory.create(providerName)
        }
        
        private fun getDefaultTrustRegistryFactory(): TrustRegistryFactory {
            // Try to get factory from testkit
            try {
                val factoryClass = Class.forName("io.geoknoesis.vericore.testkit.services.TestkitTrustRegistryFactory")
                val factory = factoryClass.getDeclaredConstructor().newInstance() as? TrustRegistryFactory
                if (factory != null) return factory
            } catch (e: Exception) {
                // Testkit not available, fall through
            }
            
            throw IllegalStateException(
                "TrustRegistry factory not available. " +
                "Provide a factory via Builder.factories() or ensure vericore-testkit is on classpath."
            )
        }
        
        private fun getDefaultWalletFactory(): WalletFactory? {
            // Try to get factory from testkit
            try {
                val factoryClass = Class.forName("io.geoknoesis.vericore.testkit.services.TestkitWalletFactory")
                val factory = factoryClass.getDeclaredConstructor().newInstance() as? WalletFactory
                if (factory != null) return factory
            } catch (e: Exception) {
                // Testkit not available, that's OK - wallet is optional
            }
            
            // Wallet factory is optional, return null if not available
            return null
        }
        
        private fun createProofGenerator(
            proofType: String,
            kms: Any,
            signerFn: (suspend (ByteArray, String) -> ByteArray)?
        ): ProofGenerator {
            requireNotNull(kms) { "KMS cannot be null when creating proof generator" }
            return when (proofType) {
                "Ed25519Signature2020" -> {
                    // Use provided signer function if available, otherwise create a simple wrapper
                    val signHelper: suspend (ByteArray, String) -> ByteArray = signerFn ?: { data, keyId ->
                        // If no signer function provided, we need to call KMS sign method
                        // Since we're avoiding reflection, we'll require the signer to be provided
                        throw IllegalStateException(
                            "Signer function must be provided when using custom KMS. " +
                            "Use keys { signer { data, keyId -> kms.sign(keyId, data) } }"
                        )
                    }
                    
                    Ed25519ProofGenerator(
                        signer = signHelper,
                        getPublicKeyId = { keyId -> keyId }
                    )
                }
                else -> throw IllegalArgumentException("Unsupported proof type: $proofType")
            }
        }
    }
    
    /**
     * Keys configuration builder.
     */
    class KeysBuilder {
        var provider: String? = null
        var algorithm: String? = null
        var kms: Any? = null // KeyManagementService - using Any to avoid dependency
        var signer: (suspend (ByteArray, String) -> ByteArray)? = null // Direct signer function
        
        fun provider(name: String) {
            provider = name
        }
        
        fun algorithm(name: String) {
            algorithm = name
        }
        
        fun custom(kms: Any) {
            this.kms = kms
        }
        
        /**
         * Set a direct signer function to avoid reflection.
         * This is the preferred way when you have access to the KMS instance.
         */
        fun signer(signerFn: suspend (ByteArray, String) -> ByteArray) {
            this.signer = signerFn
        }
    }
    
    /**
     * DID methods configuration builder.
     */
    class DidBuilder {
        val methods = mutableMapOf<String, DidMethodConfig>()
        
        fun method(name: String, block: DidMethodConfigBuilder.() -> Unit) {
            val builder = DidMethodConfigBuilder()
            builder.block()
            methods[name] = builder.build()
        }
    }
    
    /**
     * DID method configuration.
     */
    data class DidMethodConfig(
        val algorithm: String? = null,
        val domain: String? = null,
        val options: Map<String, Any?> = emptyMap()
    ) {
        fun toOptions(kms: Any): Map<String, Any?> {
            return options + mapOf("kms" to kms) + 
                (algorithm?.let { mapOf("algorithm" to it) } ?: emptyMap()) +
                (domain?.let { mapOf("domain" to it) } ?: emptyMap())
        }
    }
    
    /**
     * DID method configuration builder.
     */
    class DidMethodConfigBuilder {
        private var algorithm: String? = null
        private var domain: String? = null
        private val options = mutableMapOf<String, Any?>()
        
        fun algorithm(name: String) {
            algorithm = name
        }
        
        fun domain(name: String) {
            domain = name
        }
        
        fun option(key: String, value: Any?) {
            options[key] = value
        }
        
        fun build(): DidMethodConfig {
            return DidMethodConfig(algorithm, domain, options)
        }
    }
    
    /**
     * Anchor layer configuration builder.
     */
    class AnchorBuilder {
        val chains = mutableMapOf<String, AnchorConfig>()
        
        fun chain(chainId: String, block: AnchorConfigBuilder.() -> Unit) {
            val builder = AnchorConfigBuilder()
            builder.block()
            chains[chainId] = builder.build()
        }
    }
    
    /**
     * Anchor configuration.
     */
    data class AnchorConfig(
        val provider: String,
        val options: Map<String, Any?> = emptyMap()
    )
    
    /**
     * Anchor configuration builder.
     */
    class AnchorConfigBuilder {
        private var provider: String? = null
        private val options = mutableMapOf<String, Any?>()
        
        fun provider(name: String) {
            provider = name
        }
        
        /**
         * Use in-memory anchor client (for testing).
         */
        fun inMemory(contract: String? = null) {
            provider = "inMemory"
            if (contract != null) {
                options["contract"] = contract
            }
        }
        
        fun options(block: OptionsBuilder.() -> Unit) {
            val builder = OptionsBuilder()
            builder.block()
            options.putAll(builder.options)
        }
        
        fun build(): AnchorConfig {
            return AnchorConfig(
                provider = provider ?: throw IllegalStateException("Anchor provider is required"),
                options = options
            )
        }
    }
    
    /**
     * Options builder for anchor configuration.
     */
    class OptionsBuilder {
        val options = mutableMapOf<String, Any?>()
        
        infix fun String.to(value: Any?) {
            options[this] = value
        }
    }
    
    /**
     * Credentials configuration builder.
     */
    class CredentialsBuilder {
        var defaultProofType: String? = null
        var autoAnchor: Boolean? = null
        var defaultChain: String? = null
        
        fun defaultProofType(type: String) {
            defaultProofType = type
        }
        
        fun autoAnchor(enabled: Boolean) {
            autoAnchor = enabled
        }
        
        fun defaultChain(chainId: String) {
            defaultChain = chainId
        }
    }
    
    /**
     * Revocation configuration builder.
     */
    class RevocationConfigBuilder {
        var provider: String = "inMemory"
        
        fun provider(name: String) {
            provider = name
        }
    }
    
    /**
     * Trust registry configuration builder.
     */
    class TrustConfigBuilder {
        var provider: String = "inMemory"
        
        fun provider(name: String) {
            provider = name
        }
    }
    
    /**
     * Schema configuration builder.
     */
    class SchemaConfigBuilder {
        var autoValidate: Boolean = false
        var defaultFormat: io.geoknoesis.vericore.spi.SchemaFormat = io.geoknoesis.vericore.spi.SchemaFormat.JSON_SCHEMA
        
        fun autoValidate(enabled: Boolean) {
            autoValidate = enabled
        }
        
        fun defaultFormat(format: io.geoknoesis.vericore.spi.SchemaFormat) {
            defaultFormat = format
        }
    }
}

/**
 * DSL function to create a trust layer configuration.
 */
suspend fun trustLayer(name: String = "default", block: TrustLayerConfig.Builder.() -> Unit): TrustLayerConfig {
    val builder = TrustLayerConfig.Builder(name)
    builder.block()
    return builder.build()
}

