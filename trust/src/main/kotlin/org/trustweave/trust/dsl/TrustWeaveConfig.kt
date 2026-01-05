package org.trustweave.trust.dsl

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.anchor.BlockchainAnchorRegistry
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.DidMethod
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.anchor.spi.BlockchainAnchorClientProvider
import org.trustweave.trust.services.TrustRegistryFactory
import org.trustweave.revocation.services.StatusListRegistryFactory
import org.trustweave.credential.model.SchemaFormat
import org.trustweave.kms.services.KmsService
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.KeyManagementServices
import org.trustweave.kms.results.SignResult
import org.trustweave.core.identifiers.KeyId
import java.util.ServiceLoader
import org.trustweave.wallet.services.WalletFactory
import org.trustweave.credential.model.ProofType
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.trust.TrustRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

// TrustWeaveRegistries removed - using direct properties instead

/**
 * Unified TrustWeave Configuration.
 *
 * Centralizes configuration for anchor layer (blockchain), credential keys (KMS),
 * and DID methods into a single configuration point. This makes it easier to
 * configure TrustWeave and provides context for DSL operations.
 *
 * **Example Usage**:
 * ```kotlin
 * val trustWeave = trustWeave {
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
class TrustWeaveConfig private constructor(
    val name: String,
    val kms: KeyManagementService,
    val didRegistry: DidMethodRegistry,
    val blockchainRegistry: BlockchainAnchorRegistry,
    val credentialConfig: CredentialConfig,
    val issuer: Any?, // CredentialService from credential-api
    val didResolver: DidResolver? = null,
    val revocationManager: CredentialRevocationManager? = null,
    val trustRegistry: TrustRegistry? = null,
    val walletFactory: WalletFactory? = null,
    val kmsService: KmsService? = null,
    /**
     * Default DID method for createDid() calls.
     * Falls back to first registered method if not explicitly set.
     */
    val defaultDidMethod: String? = null,
    /**
     * Coroutine dispatcher for I/O-bound operations.
     * 
     * Defaults to [Dispatchers.IO] for production use.
     * Can be customized for testing (e.g., use [Dispatchers.Unconfined] or a test dispatcher).
     * 
     * **Example for testing:**
     * ```kotlin
     * val testDispatcher = Dispatchers.Unconfined
     * val trustWeave = trustWeave {
     *     dispatcher(testDispatcher)
     *     // ... rest of config
     * }
     * ```
     */
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /**
     * All registered DID methods, keyed by method name.
     *
     * **Example:**
     * ```kotlin
     * val keyMethod = config.didMethods["key"]
     * val webMethod = config.didMethods["web"]
     * ```
     */
    val didMethods: Map<String, DidMethod>
        get() = didRegistry.getAllMethods()

    /**
     * All registered blockchain anchor clients, keyed by chain ID.
     *
     * **Example:**
     * ```kotlin
     * val algorandClient = config.anchorClients["algorand:testnet"]
     * val polygonClient = config.anchorClients["polygon:mainnet"]
     * ```
     */
    val anchorClients: Map<String, BlockchainAnchorClient>
        get() = blockchainRegistry.getAllClients()
    /**
     * Credential configuration within TrustWeave.
     */
    data class CredentialConfig(
        val defaultProofType: ProofType = ProofType.Ed25519Signature2020,
        val autoAnchor: Boolean = false,
        val defaultChain: String? = null
    ) {
    }

    /**
     * Builder for TrustWeave configuration.
     */
    class Builder(
        private val name: String = "default"
    ) {
        private val didRegistry = DidMethodRegistry()
        private val blockchainRegistry = BlockchainAnchorRegistry()
        private var kms: KeyManagementService? = null
        private var kmsProvider: String? = null
        private var kmsAlgorithm: String = "Ed25519"
        private var kmsSigner: (suspend (ByteArray, String) -> ByteArray)? = null // Direct signer function
        private val didMethodConfigs = mutableMapOf<String, DidMethodConfig>()
        private var defaultDidMethod: String? = null
        private val anchorConfigs = mutableMapOf<String, AnchorConfig>()
        private var defaultProofType: ProofType = ProofType.Ed25519Signature2020
        private var autoAnchor: Boolean = false
        private var defaultChain: String? = null
        private var revocationProvider: String? = null
        private var trustProvider: String? = null
        private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        private var issuer: Any? = null // CredentialService from credential-api

        // Factory instances (only for services without SPI equivalents)
        private var statusListRegistryFactory: StatusListRegistryFactory? = null
        private var trustRegistryFactory: TrustRegistryFactory? = null
        private var walletFactory: WalletFactory? = null

        /**
         * Set factory instances for services that don't have SPI equivalents.
         * 
         * Note: KMS, DID methods, and Anchor clients are auto-discovered via SPI.
         * Only factories for Wallet, TrustRegistry, and StatusListRegistry are needed.
         * 
         * Only sets factories that are not null (allows merging).
         */
        fun factories(
            statusListRegistryFactory: StatusListRegistryFactory? = null,
            trustRegistryFactory: TrustRegistryFactory? = null,
            walletFactory: WalletFactory? = null
        ) {
            if (statusListRegistryFactory != null) this.statusListRegistryFactory = statusListRegistryFactory
            if (trustRegistryFactory != null) this.trustRegistryFactory = trustRegistryFactory
            if (walletFactory != null) this.walletFactory = walletFactory
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
            // Store signer function if provided (required when using custom KMS)
            builder.signer?.let { kmsSigner = it }
        }

        /**
         * Set custom KMS directly.
         */
        fun customKms(kms: KeyManagementService) {
            this.kms = kms
        }

        /**
         * Configure DID methods.
         */
        fun did(block: DidBuilder.() -> Unit) {
            val builder = DidBuilder()
            builder.block()
            didMethodConfigs.putAll(builder.methods)
            defaultDidMethod = builder.defaultMethod
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
         * Set the credential issuer/service.
         * This is used by presentation and verification builders.
         */
        fun issuer(issuer: Any?) {
            this.issuer = issuer
        }

        /**
         * Configure revocation services.
         * 
         * Simplified syntax: `revocation("inMemory")` or `revocation { provider("inMemory") }`
         */
        fun revocation(provider: String) {
            revocationProvider = provider
        }
        
        /**
         * Configure revocation services (DSL syntax for backward compatibility).
         */
        fun revocation(block: RevocationConfigBuilder.() -> Unit) {
            revocationProvider = RevocationConfigBuilder().apply(block).provider
        }

        /**
         * Configure schema services.
         * 
         * Note: Schema configuration is currently not used. Reserved for future schema validation features.
         */
        fun schemas(
            autoValidate: Boolean = false,
            defaultFormat: SchemaFormat = SchemaFormat.JSON_SCHEMA
        ) {
            // Reserved for future use
        }
        
        /**
         * Configure schema services (DSL syntax for backward compatibility).
         */
        fun schemas(block: SchemaConfigBuilder.() -> Unit) {
            SchemaConfigBuilder().apply(block)
            // Reserved for future use
        }

        /**
         * Configure trust registry.
         * 
         * Simplified syntax: `trust("inMemory")` or `trust { provider("inMemory") }`
         */
        fun trust(provider: String) {
            trustProvider = provider
        }
        
        /**
         * Configure trust registry (DSL syntax for backward compatibility).
         */
        fun trust(block: TrustConfigBuilder.() -> Unit) {
            trustProvider = TrustConfigBuilder().apply(block).provider
        }

        /**
         * Configure coroutine dispatcher for I/O-bound operations.
         * 
         * Defaults to [Dispatchers.IO] for production use.
         * Useful for testing with custom dispatchers (e.g., [Dispatchers.Unconfined]).
         * 
         * **Example for testing:**
         * ```kotlin
         * val trustWeave = trustWeave {
         *     dispatcher(Dispatchers.Unconfined) // For deterministic testing
         *     // ... rest of config
         * }
         * ```
         * 
         * @param dispatcher The coroutine dispatcher to use for I/O operations
         */
        fun dispatcher(dispatcher: CoroutineDispatcher) {
            this.ioDispatcher = dispatcher
        }

        /**
         * Build the TrustWeave configuration.
         */
        suspend fun build(): TrustWeaveConfig {
            // Resolve KMS
            val (resolvedKms, resolvedSigner) = if (kms != null) {
                val kmsRef = requireNotNull(kms) { "KMS cannot be null" }
                val signer = kmsSigner ?: createSignerFromKms(kmsRef, extractKeyId = true)
                Pair(kmsRef, signer)
            } else {
                resolveKms(kmsProvider ?: "inMemory", kmsAlgorithm)
            }
            val nonNullKms = requireNotNull(resolvedKms) {
                "KMS cannot be null. Provide a custom KMS or ensure a provider is configured."
            }

            val finalSigner = resolvedSigner ?: createSignerFromKms(nonNullKms, extractKeyId = false)

            // Auto-register DID methods from SPI
            val autoRegisteredRegistry = DidMethodRegistry.autoRegister(nonNullKms)
            for ((methodName, method) in autoRegisteredRegistry.getAllMethods()) {
                if (methodName !in didRegistry) {
                    didRegistry.register(method)
                }
            }

            // Register configured DID methods (may override auto-registered ones)
            for ((methodName, config) in didMethodConfigs) {
                val resolvedMethod = resolveDidMethod(methodName, config, nonNullKms)
                didRegistry.register(resolvedMethod)
            }
            
            // Default: register "key" method if none configured
            if (didMethodConfigs.isEmpty() && didRegistry.getAllMethodNames().isEmpty()) {
                val defaultMethod = resolveDidMethod("key", DidMethodConfig(algorithm = KeyAlgorithm.ED25519), nonNullKms)
                didRegistry.register(defaultMethod)
                defaultDidMethod = "key"
            }

            for ((chainId, config) in anchorConfigs) {
                val client = resolveAnchorClient(chainId, config)
                blockchainRegistry.register(chainId, client)
            }

            // Proof engines are managed by CredentialService internally
            // The credential-api uses proof engines instead of proof generators

            // Resolve revocation manager if revocation is configured
            val resolvedRevocationManager = revocationProvider?.let {
                resolveRevocationManager(it)
            }

            // Resolve trust registry if configured
            val resolvedTrustRegistry = trustProvider?.let {
                resolveTrustRegistry(it)
            }

            val didResolver = DidResolver { did ->
                didRegistry.resolve(did.value)
            }

            // Auto-create CredentialService if KMS available and issuer not provided
            // Use custom signer if provided, otherwise CredentialService will create one from KMS
            val resolvedIssuer: Any? = issuer ?: (nonNullKms?.let {
                if (kmsSigner != null) {
                    // Custom signer provided - use credentialService with signer
                    org.trustweave.credential.credentialService(
                        didResolver = didResolver,
                        signer = finalSigner
                    )
                } else {
                    // No custom signer - use createCredentialService which creates signer from KMS
                    org.trustweave.credential.CredentialServices.createCredentialService(
                        kms = it,
                        didResolver = didResolver
                    )
                }
            })

            // Resolve wallet factory (optional)
            val resolvedWalletFactory = walletFactory

            // KMS service is optional - no default needed
            val resolvedKmsService: KmsService? = null

            // Use the registries directly instead of snapshotting to allow DIDs created after build
            // to be resolvable. This is safe because the registries are already immutable from the
            // caller's perspective (they're passed in and not modified by the caller).
            val blockchainRegistrySnapshot = blockchainRegistry.snapshot()

            return TrustWeaveConfig(
                name = name,
                kms = nonNullKms,
                didRegistry = didRegistry, // Don't snapshot - use directly for test compatibility
                blockchainRegistry = blockchainRegistrySnapshot,
                credentialConfig = CredentialConfig(
                    defaultProofType = defaultProofType,
                    autoAnchor = autoAnchor,
                    defaultChain = defaultChain
                ),
                issuer = resolvedIssuer,
                didResolver = didResolver,
                revocationManager = resolvedRevocationManager,
                trustRegistry = resolvedTrustRegistry,
                walletFactory = resolvedWalletFactory,
                kmsService = resolvedKmsService,
                defaultDidMethod = defaultDidMethod,
                ioDispatcher = ioDispatcher
            )
        }

        private suspend fun resolveKms(providerName: String, algorithm: String): Pair<KeyManagementService, (suspend (ByteArray, String) -> ByteArray)?> {
            // Use SPI auto-discovery
            val kms = try {
                KeyManagementServices.create(providerName, mapOf("algorithm" to algorithm))
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException(
                    "KMS provider '$providerName' not found. " +
                    "Available providers: ${KeyManagementServices.availableProviders()}. " +
                    "Ensure the provider is on the classpath."
                )
            }
            
            // Create signer function from KMS
            val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
                when (val result = kms.sign(org.trustweave.core.identifiers.KeyId(keyId), data)) {
                    is org.trustweave.kms.results.SignResult.Success -> result.signature
                    is org.trustweave.kms.results.SignResult.Failure.KeyNotFound -> {
                        throw IllegalStateException("Signing failed: Key not found: ${result.keyId.value}")
                    }
                    is org.trustweave.kms.results.SignResult.Failure.UnsupportedAlgorithm -> {
                        throw IllegalStateException("Signing failed: Unsupported algorithm: ${result.reason ?: "Algorithm mismatch"}")
                    }
                    is org.trustweave.kms.results.SignResult.Failure.Error -> {
                        throw IllegalStateException("Signing failed: ${result.reason}")
                    }
                }
            }
            
            return Pair(kms, signer)
        }

        private suspend fun resolveDidMethod(
            methodName: String,
            config: DidMethodConfig,
            kms: KeyManagementService
        ): DidMethod {
            requireNotNull(kms) { "KMS cannot be null when resolving DID method: $methodName" }
            
            // Check if already registered (from SPI auto-registration)
            val existing = didRegistry[methodName]
            if (existing != null) return existing
            
            // Use SPI discovery
            val providers = java.util.ServiceLoader.load(DidMethodProvider::class.java)
            for (provider in providers) {
                if (methodName in provider.supportedMethods && provider.hasRequiredEnvironmentVariables()) {
                    val method = provider.create(methodName, config.toOptions(kms))
                    if (method != null) {
                        return method
                    }
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
        ): BlockchainAnchorClient {
            // Auto-detect provider from chain ID if not specified
            val providerName = config.provider ?: chainId.substringBefore(":")
            
            // Use SPI auto-discovery
            val providers = ServiceLoader.load(BlockchainAnchorClientProvider::class.java)
            val provider = providers.find { 
                it.name == providerName && 
                (chainId in it.supportedChains || it.supportedChains.isEmpty()) &&
                it.hasRequiredEnvironmentVariables()
            } ?: throw IllegalStateException(
                "Anchor provider '$providerName' not found for chain '$chainId'. " +
                "Available providers: ${providers.map { it.name }}. " +
                "Ensure the provider is on the classpath."
            )
            
            val client = provider.create(chainId, config.options)
                ?: throw IllegalStateException(
                    "Provider '$providerName' does not support chain '$chainId'. " +
                    "Supported chains: ${provider.supportedChains}"
                )
            
            return client
        }

        private suspend fun resolveRevocationManager(providerName: String): CredentialRevocationManager {
            // Use default in-memory manager (factory pattern can be added if needed)
            return org.trustweave.credential.revocation.RevocationManagers.default()
        }

        private suspend fun resolveTrustRegistry(providerName: String): TrustRegistry {
            val factory = trustRegistryFactory ?: throw IllegalStateException(
                "TrustRegistry factory is required. Provide it via Builder.factories(trustRegistryFactory = ...)"
            )
            return factory.create(providerName)
        }

        // Wallet factory is optional - no default needed

        /**
         * Create a signer function from KMS.
         * Handles key ID extraction and error conversion.
         */
        private fun createSignerFromKms(
            kms: KeyManagementService,
            extractKeyId: Boolean = false
        ): suspend (ByteArray, String) -> ByteArray {
            return { data: ByteArray, keyId: String ->
                val actualKeyId = if (extractKeyId && keyId.contains("#")) {
                    keyId.substringAfter("#")
                } else {
                    keyId
                }
                when (val result = kms.sign(org.trustweave.core.identifiers.KeyId(actualKeyId), data)) {
                    is org.trustweave.kms.results.SignResult.Success -> result.signature
                    is org.trustweave.kms.results.SignResult.Failure -> {
                        val reason = when (result) {
                            is org.trustweave.kms.results.SignResult.Failure.KeyNotFound ->
                                result.reason ?: "Key not found: ${result.keyId.value}"
                            is org.trustweave.kms.results.SignResult.Failure.UnsupportedAlgorithm ->
                                result.reason ?: "Unsupported algorithm"
                            is org.trustweave.kms.results.SignResult.Failure.Error ->
                                result.reason
                            else -> "Unknown error"
                        }
                        throw IllegalStateException("Signing failed: $reason")
                    }
                }
            }
        }
    }

    /**
     * Keys configuration builder.
     */
    class KeysBuilder {
        var provider: String? = null
        private var algorithmString: String? = null
        private var algorithmEnum: KeyAlgorithm? = null
        var kms: KeyManagementService? = null
        var signer: (suspend (ByteArray, String) -> ByteArray)? = null // Direct signer function

        /**
         * Get the resolved algorithm string.
         * Prefers enum value if set, otherwise uses string value.
         */
        val algorithm: String?
            get() = algorithmEnum?.algorithmName ?: algorithmString

        fun provider(name: String) {
            provider = name
        }

        /**
         * Set algorithm by string name.
         * 
         * For type safety, prefer using algorithm(value: DidCreationOptions.KeyAlgorithm).
         */
        fun algorithm(name: String) {
            algorithmString = name
            algorithmEnum = null
        }

        /**
         * Set algorithm using type-safe enum.
         * 
         * This is the preferred method for compile-time type safety.
         */
        fun algorithm(value: KeyAlgorithm) {
            algorithmEnum = value
            algorithmString = null
        }

        fun custom(kms: KeyManagementService) {
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
        var defaultMethod: String? = null
            private set

        /**
         * Set the default DID method for createDid() calls.
         * If not set, the first registered method will be used.
         */
        fun default(methodName: String) {
            defaultMethod = methodName
        }

        fun method(name: String, block: DidMethodConfigBuilder.() -> Unit) {
            methods[name] = DidMethodConfigBuilder().apply(block).build()
            // First registered method becomes default if not explicitly set
            if (defaultMethod == null) {
                defaultMethod = name
            }
        }
    }

    /**
     * DID method configuration.
     */
    data class DidMethodConfig(
        val algorithm: KeyAlgorithm? = null,
        val domain: String? = null,
        val additionalProperties: Map<String, Any?> = emptyMap()
    ) {
        fun toOptions(kms: KeyManagementService): DidCreationOptions {
            val resolvedAlgorithm = algorithm ?: KeyAlgorithm.ED25519
            val props = buildMap<String, Any?> {
                putAll(additionalProperties)
                put("kms", kms)
                domain?.let { put("domain", it) }
            }
            return DidCreationOptions(
                algorithm = resolvedAlgorithm,
                purposes = listOf(org.trustweave.did.KeyPurpose.AUTHENTICATION),
                additionalProperties = props
            )
        }
    }

    /**
     * DID method configuration builder.
     */
    class DidMethodConfigBuilder {
        private var algorithm: KeyAlgorithm? = null
        private var domain: String? = null
        private val options = mutableMapOf<String, Any?>()

        fun algorithm(name: String) {
            algorithm = KeyAlgorithm.fromName(name)
        }

        fun algorithm(value: KeyAlgorithm) {
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
            chains[chainId] = AnchorConfigBuilder().apply(block).build()
        }
    }

    /**
     * Anchor configuration.
     */
    data class AnchorConfig(
        val provider: String? = null, // Optional - auto-detected from chain ID if not specified
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
            options.putAll(OptionsBuilder().apply(block).options)
        }

        fun build(): AnchorConfig {
            return AnchorConfig(
                provider = provider, // Auto-detected from chain ID if null
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
     * Revocation configuration builder.
     */
    class RevocationConfigBuilder {
        var provider: String? = null

        fun provider(name: String) {
            provider = name
        }
    }

    /**
     * Schema configuration builder.
     */
    class SchemaConfigBuilder {
        var provider: String? = null

        fun provider(name: String) {
            provider = name
        }
    }

    /**
     * Trust registry configuration builder.
     */
    class TrustConfigBuilder {
        var provider: String? = null

        fun provider(name: String) {
            provider = name
        }
    }

    /**
     * Credentials configuration builder.
     */
    class CredentialsBuilder {
        var defaultProofType: ProofType? = null
        var autoAnchor: Boolean? = null
        var defaultChain: String? = null

        /**
         * Set default proof type.
         */
        fun defaultProofType(type: ProofType) {
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
 * DSL function to create a TrustWeave configuration.
 */
suspend fun trustWeave(
    name: String = "default",
    block: TrustWeaveConfig.Builder.() -> Unit
): TrustWeaveConfig {
    val builder = TrustWeaveConfig.Builder(name)
    builder.block()
    return builder.build()
}

