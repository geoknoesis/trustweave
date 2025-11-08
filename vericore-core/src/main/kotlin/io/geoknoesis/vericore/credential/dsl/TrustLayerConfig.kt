package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.credential.issuer.CredentialIssuer
import io.geoknoesis.vericore.credential.proof.Ed25519ProofGenerator
import io.geoknoesis.vericore.credential.proof.ProofGenerator
import io.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry
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
    val issuer: CredentialIssuer
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
                // Register in DidRegistry using reflection
                try {
                    val didRegistryClass = Class.forName("io.geoknoesis.vericore.did.DidRegistry")
                    val registerMethod = didRegistryClass.getMethod("register", Any::class.java)
                    registerMethod.invoke(null, method)
                } catch (e: Exception) {
                    // DidRegistry not available - this is OK for DSL usage
                }
            }
            
            // Resolve anchor clients
            val resolvedAnchorClients = mutableMapOf<String, Any>()
            for ((chainId, config) in anchorConfigs) {
                val client = resolveAnchorClient(chainId, config)
                resolvedAnchorClients[chainId] = client
                // Register in BlockchainRegistry using reflection
                try {
                    val blockchainRegistryClass = Class.forName("io.geoknoesis.vericore.anchor.BlockchainRegistry")
                    val registerMethod = blockchainRegistryClass.getMethod("register", String::class.java, Any::class.java)
                    registerMethod.invoke(null, chainId, client)
                } catch (e: Exception) {
                    // BlockchainRegistry not available - this is OK for DSL usage
                }
            }
            
            // Create proof generator
            val proofGenerator = createProofGenerator(defaultProofType, resolvedKms, finalSigner)
            ProofGeneratorRegistry.register(proofGenerator)
            
            // Create credential issuer
            val issuer = CredentialIssuer(
                proofGenerator = proofGenerator,
                resolveDid = { did -> 
                    try {
                        val didRegistryClass = Class.forName("io.geoknoesis.vericore.did.DidRegistry")
                        val resolveMethod = didRegistryClass.getMethod("resolve", String::class.java)
                    val result = resolveMethod.invoke(null, did) as? Any
                    if (result != null) {
                        try {
                            val getDocumentMethod = result.javaClass.getMethod("getDocument")
                            val document = getDocumentMethod.invoke(result) as? Any
                            document != null
                        } catch (e: NoSuchMethodException) {
                            try {
                                val documentField = result.javaClass.getDeclaredField("document")
                                documentField.isAccessible = true
                                val document = documentField.get(result) as? Any
                                document != null
                            } catch (e2: Exception) {
                                false
                            }
                        }
                    } else {
                        false
                    }
                    } catch (e: Exception) {
                        true // Assume valid if registry not available
                    }
                }
            )
            
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
                issuer = issuer
            )
        }
        
        private suspend fun resolveKms(providerName: String, algorithm: String): Pair<Any, (suspend (ByteArray, String) -> ByteArray)?> {
            // Check for inMemory first (testkit)
            if (providerName == "inMemory") {
                try {
                    val kmsClass = Class.forName("io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService")
                    val kmsInstance = kmsClass.getDeclaredConstructor().newInstance()
                    // Store reference to avoid closure issues
                    val kmsRef = kmsInstance
                    // Create a signer function for the resolved KMS
                    val signerFn: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
                        // Call the suspend sign method directly via a simple adapter
                        // Since we resolved the KMS, we know it's InMemoryKeyManagementService
                        // We'll use a simple reflection call just for this case
                        try {
                            val signMethod = kmsClass.getDeclaredMethod(
                                "sign",
                                String::class.java,
                                ByteArray::class.java,
                                String::class.java,
                                kotlin.coroutines.Continuation::class.java
                            )
                            kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<ByteArray> { cont ->
                                try {
                                    val result = signMethod.invoke(kmsRef, keyId, data, null, cont)
                                    if (result === kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) {
                                        kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                                    } else {
                                        cont.resumeWith(Result.success(result as ByteArray))
                                        kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                                    }
                                } catch (e: Exception) {
                                    cont.resumeWith(Result.failure(e))
                                    kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                                }
                            } ?: throw IllegalStateException("Sign operation failed")
                        } catch (e: NoSuchMethodException) {
                            // Try without algorithm parameter
                            val signMethod = kmsClass.getDeclaredMethod(
                                "sign",
                                String::class.java,
                                ByteArray::class.java,
                                kotlin.coroutines.Continuation::class.java
                            )
                            kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<ByteArray> { cont ->
                                try {
                                    val result = signMethod.invoke(kmsRef, keyId, data, cont)
                                    if (result === kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) {
                                        kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                                    } else {
                                        cont.resumeWith(Result.success(result as ByteArray))
                                        kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                                    }
                                } catch (e: Exception) {
                                    cont.resumeWith(Result.failure(e))
                                    kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                                }
                            } ?: throw IllegalStateException("Sign operation failed")
                        }
                    }
                    return Pair(kmsInstance, signerFn)
                } catch (e: Exception) {
                    throw IllegalStateException(
                        "InMemoryKeyManagementService not found. " +
                        "Ensure vericore-testkit is on classpath."
                    )
                }
            }
            
            // Try to discover via SPI
            try {
                val providerClass = Class.forName("io.geoknoesis.vericore.kms.spi.KeyManagementServiceProvider")
                val serviceLoader = java.util.ServiceLoader.load(providerClass)
                val provider = serviceLoader.find { 
                    val nameMethod = it.javaClass.getMethod("getName")
                    nameMethod.invoke(it) == providerName
                }
                
                if (provider != null) {
                    val createMethod = provider.javaClass.getMethod("create", Map::class.java)
                    val kms = createMethod.invoke(provider, mapOf("algorithm" to algorithm))
                    // For SPI providers, we can't easily create a signer without reflection
                    // So return null and require the user to provide one
                    return Pair(kms ?: throw IllegalStateException("Failed to create KMS for provider: $providerName"), null)
                }
            } catch (e: ClassNotFoundException) {
                // SPI classes not available
            }
            
            throw IllegalStateException(
                "KMS provider '$providerName' not found. " +
                "Ensure vericore-$providerName is on classpath or use 'inMemory' for testing."
            )
        }
        
        private suspend fun resolveDidMethod(
            methodName: String,
            config: DidMethodConfig,
            kms: Any
        ): Any {
            requireNotNull(kms) { "KMS cannot be null when resolving DID method: $methodName" }
            // Try to discover via SPI
            try {
                val providerClass = Class.forName("io.geoknoesis.vericore.did.spi.DidMethodProvider")
                val serviceLoader = java.util.ServiceLoader.load(providerClass)
                
                // Try waltid provider first
                val waltIdProvider = serviceLoader.find {
                    val nameMethod = it.javaClass.getMethod("getName")
                    nameMethod.invoke(it) == "waltid"
                }
                if (waltIdProvider != null) {
                    val supportedMethodsMethod = waltIdProvider.javaClass.getMethod("getSupportedMethods")
                    val supportedMethods = supportedMethodsMethod.invoke(waltIdProvider) as? List<*>
                    if (supportedMethods != null && methodName in supportedMethods) {
                        val createMethod = waltIdProvider.javaClass.getMethod("create", String::class.java, Map::class.java)
                        val method = createMethod.invoke(waltIdProvider, methodName, config.toOptions(kms))
                        if (method != null) return method
                    }
                }
                
                // Try godiddy provider
                val godiddyProvider = serviceLoader.find {
                    val nameMethod = it.javaClass.getMethod("getName")
                    nameMethod.invoke(it) == "godiddy"
                }
                if (godiddyProvider != null) {
                    val supportedMethodsMethod = godiddyProvider.javaClass.getMethod("getSupportedMethods")
                    val supportedMethods = supportedMethodsMethod.invoke(godiddyProvider) as? List<*>
                    if (supportedMethods != null && methodName in supportedMethods) {
                        val createMethod = godiddyProvider.javaClass.getMethod("create", String::class.java, Map::class.java)
                        val method = createMethod.invoke(godiddyProvider, methodName, config.toOptions(kms))
                        if (method != null) return method
                    }
                }
            } catch (e: ClassNotFoundException) {
                // SPI classes not available
            }
            
            // Fallback to testkit for "key" method
            if (methodName == "key") {
                try {
                    val didMethodClass = Class.forName("io.geoknoesis.vericore.testkit.did.DidKeyMockMethod")
                    // Find KeyManagementService interface
                    val kmsInterface = try {
                        Class.forName("io.geoknoesis.vericore.kms.KeyManagementService")
                    } catch (e: ClassNotFoundException) {
                        throw IllegalStateException(
                            "KeyManagementService interface not found. " +
                            "Ensure vericore-kms is on classpath."
                        )
                    }
                    // Get constructor that takes KeyManagementService
                    val constructor = didMethodClass.getDeclaredConstructor(kmsInterface)
                    constructor.isAccessible = true
                    return constructor.newInstance(kms)
                } catch (e: Exception) {
                    throw IllegalStateException(
                        "DidKeyMockMethod not found or cannot be instantiated: ${e.message}. " +
                        "Ensure vericore-testkit is on classpath. " +
                        "Original error: ${e.javaClass.simpleName}: ${e.message}",
                        e
                    )
                }
            }
            
            throw IllegalStateException(
                "DID method '$methodName' not found. " +
                "Ensure appropriate DID method provider is on classpath."
            )
        }
        
        private suspend fun resolveAnchorClient(
            chainId: String,
            config: AnchorConfig
        ): Any {
            // Check for inMemory first (testkit)
            if (config.provider == "inMemory") {
                try {
                    val clientClass = Class.forName("io.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient")
                    val contract = config.options["contract"] as? String
                    // Try to find constructor - Kotlin default parameters create multiple constructors
                    val constructors = clientClass.declaredConstructors
                    val constructor = constructors.find { 
                        val params = it.parameterTypes
                        when {
                            contract != null -> params.size == 2 && params[0] == String::class.java && params[1] == String::class.java
                            else -> params.size == 1 && params[0] == String::class.java || 
                                    (params.size == 2 && params[0] == String::class.java && params[1] == String::class.java)
                        }
                    } ?: throw NoSuchMethodException("No suitable constructor found for InMemoryBlockchainAnchorClient")
                    
                    constructor.isAccessible = true
                    return if (contract != null) {
                        constructor.newInstance(chainId, contract)
                    } else {
                        // Try single parameter first, then two parameters with null
                        try {
                            constructor.newInstance(chainId)
                        } catch (e: Exception) {
                            constructor.newInstance(chainId, null)
                        }
                    }
                } catch (e: Exception) {
                    throw IllegalStateException(
                        "InMemoryBlockchainAnchorClient not found or cannot be instantiated: ${e.message}. " +
                        "Ensure vericore-testkit is on classpath. " +
                        "Original error: ${e.javaClass.simpleName}: ${e.message}",
                        e
                    )
                }
            }
            
            // Try to discover via SPI
            try {
                val providerClass = Class.forName("io.geoknoesis.vericore.anchor.spi.BlockchainAnchorClientProvider")
                val serviceLoader = java.util.ServiceLoader.load(providerClass)
                val provider = serviceLoader.find {
                    val nameMethod = it.javaClass.getMethod("getName")
                    nameMethod.invoke(it) == config.provider
                }
                
                if (provider != null) {
                    val createMethod = provider.javaClass.getMethod("create", String::class.java, Map::class.java)
                    val client = createMethod.invoke(provider, chainId, config.options)
                    return client ?: throw IllegalStateException("Failed to create anchor client for chain: $chainId")
                }
            } catch (e: ClassNotFoundException) {
                // SPI classes not available
            }
            
            throw IllegalStateException(
                "Anchor provider '${config.provider}' not found for chain '$chainId'. " +
                "Ensure vericore-${config.provider} is on classpath or use 'inMemory' for testing."
            )
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
}

/**
 * DSL function to create a trust layer configuration.
 */
suspend fun trustLayer(name: String = "default", block: TrustLayerConfig.Builder.() -> Unit): TrustLayerConfig {
    val builder = TrustLayerConfig.Builder(name)
    builder.block()
    return builder.build()
}

