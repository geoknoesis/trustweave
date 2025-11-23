package com.trustweave.trust.dsl

import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.credential.CredentialServiceRegistry
import com.trustweave.did.resolution.DidResolver
import com.trustweave.credential.issuer.CredentialIssuer
import com.trustweave.credential.proof.Ed25519ProofGenerator
import com.trustweave.credential.proof.ProofGenerator
import com.trustweave.credential.proof.ProofGeneratorRegistry
import com.trustweave.did.DidMethod
import com.trustweave.did.DidMethodRegistry
import com.trustweave.anchor.services.BlockchainAnchorClientFactory
import com.trustweave.trust.services.TrustRegistryFactory
import com.trustweave.revocation.services.StatusListManagerFactory
import com.trustweave.did.services.DidDocumentAccess
import com.trustweave.did.services.VerificationMethodAccess
import com.trustweave.did.services.DidMethodService
import com.trustweave.kms.services.KmsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

data class TrustLayerRegistries(
    val didRegistry: DidMethodRegistry = DidMethodRegistry(),
    val blockchainRegistry: BlockchainAnchorRegistry = BlockchainAnchorRegistry(),
    val credentialRegistry: CredentialServiceRegistry = CredentialServiceRegistry.create(),
    val proofRegistry: ProofGeneratorRegistry = ProofGeneratorRegistry()
)

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
    val kms: Any,
    val registries: TrustLayerRegistries,
    val credentialConfig: CredentialConfig,
    val issuer: CredentialIssuer,
    val didResolver: DidResolver? = null,
    val statusListManager: Any? = null,
    val trustRegistry: Any? = null,
    val walletFactory: WalletFactory? = null,
    val didDocumentAccess: DidDocumentAccess? = null,
    val verificationMethodAccess: VerificationMethodAccess? = null,
    val didMethodService: DidMethodService? = null,
    val kmsService: KmsService? = null
) {
    val didMethods: Map<String, Any>
        get() = registries.didRegistry.getAllMethods()

    val anchorClients: Map<String, Any>
        get() = registries.blockchainRegistry.getAllClients()
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
    class Builder(
        private val name: String = "default",
        private val registries: TrustLayerRegistries = TrustLayerRegistries()
    ) {
        private var kms: Any? = null // KeyManagementService - using Any to avoid dependency
        private var kmsProvider: String? = null
        private var kmsAlgorithm: String = "Ed25519"
        private var kmsSigner: (suspend (ByteArray, String) -> ByteArray)? = null // Direct signer function
        private val didMethodConfigs = mutableMapOf<String, DidMethodConfig>()
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
            for ((methodName, config) in didMethodConfigs) {
                val resolvedMethod = resolveDidMethod(methodName, config, resolvedKms) as? DidMethod
                    ?: throw IllegalStateException("Invalid DID method returned for '$methodName'")
                registries.didRegistry.register(resolvedMethod)
            }
            
            for ((chainId, config) in anchorConfigs) {
                val client = resolveAnchorClient(chainId, config) as? BlockchainAnchorClient
                    ?: throw IllegalStateException("Invalid blockchain anchor client returned for '$chainId'")
                registries.blockchainRegistry.register(chainId, client)
            }
            
            // Create proof generator
            val proofGenerator = createProofGenerator(defaultProofType, resolvedKms, finalSigner)
            registries.proofRegistry.register(proofGenerator)
            
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
            
            val didResolver = DidResolver { did ->
                runCatching { registries.didRegistry.resolve(did) }
                    .getOrNull()
            }

            val issuer = CredentialIssuer(
                proofGenerator = proofGenerator,
                resolveDid = { did ->
                    val resolution = didResolver.resolve(did)
                    resolution?.document != null
                },
                proofRegistry = registries.proofRegistry
            )
            
            // Resolve wallet factory
            val resolvedWalletFactory = walletFactory ?: getDefaultWalletFactory()
            
            // Create service adapters (no reflection, direct instantiation)
            val resolvedDidDocumentAccess = com.trustweave.did.services.DidDocumentAccessAdapter()
            val resolvedVerificationMethodAccess = com.trustweave.did.services.VerificationMethodAccessAdapter()
            val resolvedDidMethodService = com.trustweave.did.services.DidMethodServiceAdapter()
            val resolvedKmsService = com.trustweave.testkit.services.TestkitKmsService()
            
            val registriesSnapshot = TrustLayerRegistries(
                didRegistry = registries.didRegistry.snapshot(),
                blockchainRegistry = registries.blockchainRegistry.snapshot(),
                credentialRegistry = registries.credentialRegistry.snapshot()
            )
            
            return TrustLayerConfig(
                name = name,
                kms = resolvedKms,
                registries = registriesSnapshot,
                credentialConfig = CredentialConfig(
                    defaultProofType = defaultProofType,
                    autoAnchor = autoAnchor,
                    defaultChain = defaultChain
                ),
                issuer = issuer,
                didResolver = didResolver,
                statusListManager = resolvedStatusListManager,
                trustRegistry = resolvedTrustRegistry,
                walletFactory = resolvedWalletFactory,
                didDocumentAccess = resolvedDidDocumentAccess,
                verificationMethodAccess = resolvedVerificationMethodAccess,
                didMethodService = resolvedDidMethodService,
                kmsService = resolvedKmsService
            )
        }
        
        private suspend fun resolveKms(providerName: String, algorithm: String): Pair<Any, (suspend (ByteArray, String) -> ByteArray)?> {
            val factory = kmsFactory ?: getDefaultKmsFactory()
            return factory.createFromProvider(providerName, algorithm)
        }
        
        private fun getDefaultKmsFactory(): KmsFactory {
            // Try to get factory from testkit
            try {
                val factoryClass = Class.forName("com.trustweave.testkit.services.TestkitKmsFactory")
                val factory = factoryClass.getDeclaredConstructor().newInstance() as? KmsFactory
                if (factory != null) return factory
            } catch (e: Exception) {
                // Testkit not available, fall through
            }
            
            // Fallback: throw error asking user to provide factory
            throw IllegalStateException(
                "KMS factory not available. " +
                "Provide a factory via Builder.factories() or ensure TrustWeave-testkit is on classpath."
            )
        }
        
        private suspend fun resolveDidMethod(
            methodName: String,
            config: DidMethodConfig,
            kms: Any
        ): DidMethod {
            requireNotNull(kms) { "KMS cannot be null when resolving DID method: $methodName" }
            val factory = didMethodFactory ?: getDefaultDidMethodFactory()
            val method = factory.create(methodName, config.toOptions(kms), kms) as? DidMethod
            return method ?: throw IllegalStateException(
                "DID method '$methodName' not found. " +
                "Ensure appropriate DID method provider is on classpath."
            )
        }
        
        private fun getDefaultDidMethodFactory(): DidMethodFactory {
            // Try to get factory from testkit
            try {
                val factoryClass = Class.forName("com.trustweave.testkit.services.TestkitDidMethodFactory")
                val factory = factoryClass.getDeclaredConstructor().newInstance() as? DidMethodFactory
                if (factory != null) return factory
            } catch (e: Exception) {
                // Testkit not available, fall through
            }
            
            throw IllegalStateException(
                "DID method factory not available. " +
                "Provide a factory via Builder.factories() or ensure TrustWeave-testkit is on classpath."
            )
        }
        
        private suspend fun resolveAnchorClient(
            chainId: String,
            config: AnchorConfig
        ): BlockchainAnchorClient {
            val factory = anchorClientFactory ?: getDefaultAnchorClientFactory()
            val client = factory.create(chainId, config.provider, config.options)
            return client as? BlockchainAnchorClient ?: throw IllegalStateException(
                "Invalid blockchain anchor client implementation returned for $chainId"
            )
        }
        
        private fun getDefaultAnchorClientFactory(): BlockchainAnchorClientFactory {
            // Try to get factory from testkit
            try {
                val factoryClass = Class.forName("com.trustweave.testkit.services.TestkitBlockchainAnchorClientFactory")
                val factory = factoryClass.getDeclaredConstructor().newInstance() as? BlockchainAnchorClientFactory
                if (factory != null) return factory
            } catch (e: Exception) {
                // Testkit not available, fall through
            }
            
            throw IllegalStateException(
                "BlockchainAnchorClient factory not available. " +
                "Provide a factory via Builder.factories() or ensure TrustWeave-testkit is on classpath."
            )
        }
        
        private suspend fun resolveStatusListManager(providerName: String): Any {
            val factory = statusListManagerFactory ?: getDefaultStatusListManagerFactory()
            return factory.create(providerName)
        }
        
        private fun getDefaultStatusListManagerFactory(): StatusListManagerFactory {
            // Try to get factory from testkit
            try {
                val factoryClass = Class.forName("com.trustweave.testkit.services.TestkitStatusListManagerFactory")
                val factory = factoryClass.getDeclaredConstructor().newInstance() as? StatusListManagerFactory
                if (factory != null) return factory
            } catch (e: Exception) {
                // Testkit not available, fall through
            }
            
            // Fallback: try to instantiate InMemoryStatusListManager directly (it's in TrustWeave-core)
            try {
                val managerClass = Class.forName("com.trustweave.credential.revocation.InMemoryStatusListManager")
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
                "Provide a factory via Builder.factories() or ensure TrustWeave-testkit is on classpath."
            )
        }
        
        private suspend fun resolveTrustRegistry(providerName: String): Any {
            val factory = trustRegistryFactory ?: getDefaultTrustRegistryFactory()
            return factory.create(providerName)
        }
        
        private fun getDefaultTrustRegistryFactory(): TrustRegistryFactory {
            // Try to get factory from testkit
            try {
                val factoryClass = Class.forName("com.trustweave.testkit.services.TestkitTrustRegistryFactory")
                val factory = factoryClass.getDeclaredConstructor().newInstance() as? TrustRegistryFactory
                if (factory != null) return factory
            } catch (e: Exception) {
                // Testkit not available, fall through
            }
            
            throw IllegalStateException(
                "TrustRegistry factory not available. " +
                "Provide a factory via Builder.factories() or ensure TrustWeave-testkit is on classpath."
            )
        }
        
        private fun getDefaultWalletFactory(): WalletFactory? {
            // Try to get factory from testkit
            try {
                val factoryClass = Class.forName("com.trustweave.testkit.services.TestkitWalletFactory")
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
        val algorithm: com.trustweave.did.DidCreationOptions.KeyAlgorithm? = null,
        val domain: String? = null,
        val additionalProperties: Map<String, Any?> = emptyMap()
    ) {
        fun toOptions(kms: Any): com.trustweave.did.DidCreationOptions {
            val resolvedAlgorithm = algorithm ?: com.trustweave.did.DidCreationOptions.KeyAlgorithm.ED25519
            val props = buildMap<String, Any?> {
                putAll(additionalProperties)
                put("kms", kms)
                domain?.let { put("domain", it) }
            }
            return com.trustweave.did.DidCreationOptions(
                algorithm = resolvedAlgorithm,
                purposes = listOf(com.trustweave.did.DidCreationOptions.KeyPurpose.AUTHENTICATION),
                additionalProperties = props
            )
        }
    }
    
    /**
     * DID method configuration builder.
     */
    class DidMethodConfigBuilder {
        private var algorithm: com.trustweave.did.DidCreationOptions.KeyAlgorithm? = null
        private var domain: String? = null
        private val options = mutableMapOf<String, Any?>()
        
        fun algorithm(name: String) {
            algorithm = com.trustweave.did.DidCreationOptions.KeyAlgorithm.fromName(name)
        }
        
        fun algorithm(value: com.trustweave.did.DidCreationOptions.KeyAlgorithm) {
            algorithm = value
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
        var defaultFormat: com.trustweave.core.SchemaFormat = com.trustweave.core.SchemaFormat.JSON_SCHEMA
        
        fun autoValidate(enabled: Boolean) {
            autoValidate = enabled
        }
        
        fun defaultFormat(format: com.trustweave.core.SchemaFormat) {
            defaultFormat = format
        }
    }
}

/**
 * DSL function to create a trust layer configuration.
 */
suspend fun trustLayer(
    name: String = "default",
    registries: TrustLayerRegistries = TrustLayerRegistries(),
    block: TrustLayerConfig.Builder.() -> Unit
): TrustLayerConfig {
    val builder = TrustLayerConfig.Builder(name, registries)
    builder.block()
    return builder.build()
}

