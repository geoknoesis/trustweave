package com.trustweave.trust.dsl

import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.credential.CredentialServiceRegistry
import com.trustweave.did.resolver.DidResolver
import com.trustweave.credential.issuer.CredentialIssuer
import com.trustweave.credential.proof.Ed25519ProofGenerator
import com.trustweave.credential.proof.ProofGenerator
import com.trustweave.credential.proof.ProofGeneratorRegistry
import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidMethod
import com.trustweave.did.registry.DidMethodRegistry
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.anchor.services.BlockchainAnchorClientFactory
import com.trustweave.trust.services.TrustRegistryFactory
import com.trustweave.revocation.services.StatusListRegistryFactory
import com.trustweave.credential.SchemaFormat
import com.trustweave.kms.services.KmsService
import com.trustweave.kms.services.KmsFactory
import com.trustweave.kms.KeyManagementService
import com.trustweave.did.services.DidMethodFactory
import com.trustweave.wallet.services.WalletFactory
import com.trustweave.trust.types.ProofType
import com.trustweave.credential.revocation.StatusListManager
import com.trustweave.trust.TrustRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

data class TrustWeaveRegistries(
    val didRegistry: DidMethodRegistry = DidMethodRegistry(),
    val blockchainRegistry: BlockchainAnchorRegistry = BlockchainAnchorRegistry(),
    val credentialRegistry: CredentialServiceRegistry = CredentialServiceRegistry.create(),
    val proofRegistry: ProofGeneratorRegistry = ProofGeneratorRegistry()
)

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
    val registries: TrustWeaveRegistries,
    val credentialConfig: CredentialConfig,
    val issuer: CredentialIssuer,
    val didResolver: DidResolver? = null,
    val statusListManager: StatusListManager? = null,
    val trustRegistry: TrustRegistry? = null,
    val walletFactory: WalletFactory? = null,
    val kmsService: KmsService? = null,
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
        get() = registries.didRegistry.getAllMethods()

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
        get() = registries.blockchainRegistry.getAllClients()
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
        private val name: String = "default",
        private val registries: TrustWeaveRegistries = TrustWeaveRegistries()
    ) {
        private var kms: KeyManagementService? = null
        private var kmsProvider: String? = null
        private var kmsAlgorithm: String = "Ed25519"
        private var kmsSigner: (suspend (ByteArray, String) -> ByteArray)? = null // Direct signer function
        private val didMethodConfigs = mutableMapOf<String, DidMethodConfig>()
        private val anchorConfigs = mutableMapOf<String, AnchorConfig>()
        private var defaultProofType: ProofType = ProofType.Ed25519Signature2020
        private var autoAnchor: Boolean = false
        private var defaultChain: String? = null
        private var revocationProvider: String? = null
        private var trustProvider: String? = null
        private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

        // Factory instances (required - no reflection fallback)
        private var kmsFactory: KmsFactory? = null
        private var didMethodFactory: DidMethodFactory? = null
        private var anchorClientFactory: BlockchainAnchorClientFactory? = null
        private var statusListRegistryFactory: StatusListRegistryFactory? = null
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
            statusListRegistryFactory: StatusListRegistryFactory? = null,
            trustRegistryFactory: TrustRegistryFactory? = null,
            walletFactory: WalletFactory? = null
        ) {
            this.kmsFactory = kmsFactory
            this.didMethodFactory = didMethodFactory
            this.anchorClientFactory = anchorClientFactory
            this.statusListRegistryFactory = statusListRegistryFactory
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
            // CRITICAL: When custom() is used, signer MUST be provided
            if (builder.signer != null) {
                kmsSigner = builder.signer
            } else if (builder.kms != null) {
                // Custom KMS provided but no signer - this will cause issues
                // We'll check this in build() and throw a clear error
            }
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
                // Custom KMS provided - create signer if not provided
                val signer = kmsSigner ?: { data: ByteArray, keyId: String ->
                    kms!!.sign(com.trustweave.core.types.KeyId(keyId), data)
                }
                Pair(kms!!, signer)
            } else {
                // Use factory to create KMS
                resolveKms(kmsProvider ?: "inMemory", kmsAlgorithm)
            }
            requireNotNull(resolvedKms) {
                "KMS cannot be null. Provide a custom KMS or ensure a provider is configured."
            }

            // Use the resolved signer (auto-created from KMS if not provided)
            val finalSigner = resolvedSigner ?: { data: ByteArray, keyId: String ->
                resolvedKms.sign(com.trustweave.core.types.KeyId(keyId), data)
            }

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
            val resolvedStatusListManager = revocationProvider?.let {
                resolveStatusListManager(it)
            }

            // Resolve trust registry if configured
            val resolvedTrustRegistry = trustProvider?.let {
                resolveTrustRegistry(it)
            }

            val didResolver = DidResolver { did ->
                runCatching { registries.didRegistry.resolve(did.value) }
                    .getOrNull()
            }

            val issuer = CredentialIssuer(
                proofGenerator = proofGenerator,
                resolveDid = { did ->
                    val resolution = didResolver.resolve(com.trustweave.core.types.Did(did))
                    resolution is DidResolutionResult.Success
                },
                proofRegistry = registries.proofRegistry
            )

            // Resolve wallet factory (optional)
            val resolvedWalletFactory = walletFactory

            // KMS service is optional - no default needed
            val resolvedKmsService: KmsService? = null

            val registriesSnapshot = TrustWeaveRegistries(
                didRegistry = registries.didRegistry.snapshot(),
                blockchainRegistry = registries.blockchainRegistry.snapshot(),
                credentialRegistry = registries.credentialRegistry.snapshot()
            )

            return TrustWeaveConfig(
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
                kmsService = resolvedKmsService,
                ioDispatcher = ioDispatcher
            )
        }

        private suspend fun resolveKms(providerName: String, algorithm: String): Pair<KeyManagementService, (suspend (ByteArray, String) -> ByteArray)?> {
            val factory = kmsFactory ?: throw IllegalStateException(
                "KMS factory is required. Provide it via Builder.factories(kmsFactory = ...)"
            )
            val (kms, signer) = factory.createFromProvider(providerName, algorithm)
            return Pair(kms as KeyManagementService, signer)
        }

        private suspend fun resolveDidMethod(
            methodName: String,
            config: DidMethodConfig,
            kms: KeyManagementService
        ): DidMethod {
            requireNotNull(kms) { "KMS cannot be null when resolving DID method: $methodName" }
            val factory = didMethodFactory ?: throw IllegalStateException(
                "DID method factory is required. Provide it via Builder.factories(didMethodFactory = ...)"
            )
            val method = factory.create(methodName, config.toOptions(kms), kms) as? DidMethod
            return method ?: throw IllegalStateException(
                "DID method '$methodName' not found. " +
                "Ensure appropriate DID method provider is on classpath."
            )
        }

        private suspend fun resolveAnchorClient(
            chainId: String,
            config: AnchorConfig
        ): BlockchainAnchorClient {
            val factory = anchorClientFactory ?: throw IllegalStateException(
                "BlockchainAnchorClient factory is required. Provide it via Builder.factories(anchorClientFactory = ...)"
            )
            val client = factory.create(chainId, config.provider, config.options)
            return client as? BlockchainAnchorClient ?: throw IllegalStateException(
                "Invalid blockchain anchor client implementation returned for $chainId"
            )
        }

        private suspend fun resolveStatusListManager(providerName: String): StatusListManager {
            val factory = statusListRegistryFactory ?: throw IllegalStateException(
                "StatusListRegistry factory is required. Provide it via Builder.factories(statusListRegistryFactory = ...)"
            )
            @Suppress("UNCHECKED_CAST")
            return factory.create(providerName) as StatusListManager
        }

        private suspend fun resolveTrustRegistry(providerName: String): TrustRegistry {
            val factory = trustRegistryFactory ?: throw IllegalStateException(
                "TrustRegistry factory is required. Provide it via Builder.factories(trustRegistryFactory = ...)"
            )
            @Suppress("UNCHECKED_CAST")
            return factory.create(providerName) as TrustRegistry
        }

        // Wallet factory is optional - no default needed

        private fun createProofGenerator(
            proofType: ProofType,
            kms: KeyManagementService,
            signerFn: (suspend (ByteArray, String) -> ByteArray)?
        ): ProofGenerator {
            requireNotNull(kms) { "KMS cannot be null when creating proof generator" }
            return when (proofType) {
                is ProofType.Ed25519Signature2020 -> {
                    // Use provided signer function if available, otherwise create a wrapper using KMS
                    val signHelper: suspend (ByteArray, String) -> ByteArray = signerFn ?: { data, keyId ->
                        kms.sign(com.trustweave.core.types.KeyId(keyId), data)
                    }

                    Ed25519ProofGenerator(
                        signer = signHelper,
                        getPublicKeyId = { keyId -> keyId }
                    )
                }
                else -> throw IllegalArgumentException(
                    "Unsupported proof type: ${proofType.value}. " +
                    "Supported types: ${ProofType.all().joinToString { it.value }}. " +
                    "Currently only Ed25519Signature2020 is supported for proof generation."
                )
            }
        }
    }

    /**
     * Keys configuration builder.
     */
    class KeysBuilder {
        var provider: String? = null
        private var algorithmString: String? = null
        private var algorithmEnum: com.trustweave.did.DidCreationOptions.KeyAlgorithm? = null
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
        fun algorithm(value: com.trustweave.did.DidCreationOptions.KeyAlgorithm) {
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
        val algorithm: DidCreationOptions.KeyAlgorithm? = null,
        val domain: String? = null,
        val additionalProperties: Map<String, Any?> = emptyMap()
    ) {
        fun toOptions(kms: KeyManagementService): DidCreationOptions {
            val resolvedAlgorithm = algorithm ?: DidCreationOptions.KeyAlgorithm.ED25519
            val props = buildMap<String, Any?> {
                putAll(additionalProperties)
                put("kms", kms)
                domain?.let { put("domain", it) }
            }
            return DidCreationOptions(
                algorithm = resolvedAlgorithm,
                purposes = listOf(DidCreationOptions.KeyPurpose.AUTHENTICATION),
                additionalProperties = props
            )
        }
    }

    /**
     * DID method configuration builder.
     */
    class DidMethodConfigBuilder {
        private var algorithm: DidCreationOptions.KeyAlgorithm? = null
        private var domain: String? = null
        private val options = mutableMapOf<String, Any?>()

        fun algorithm(name: String) {
            algorithm = DidCreationOptions.KeyAlgorithm.fromName(name)
        }

        fun algorithm(value: DidCreationOptions.KeyAlgorithm) {
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
        var defaultFormat: SchemaFormat = SchemaFormat.JSON_SCHEMA

        fun autoValidate(enabled: Boolean) {
            autoValidate = enabled
        }

        fun defaultFormat(format: SchemaFormat) {
            defaultFormat = format
        }
    }
}

/**
 * DSL function to create a TrustWeave configuration.
 */
suspend fun trustWeave(
    name: String = "default",
    registries: TrustWeaveRegistries = TrustWeaveRegistries(),
    block: TrustWeaveConfig.Builder.() -> Unit
): TrustWeaveConfig {
    val builder = TrustWeaveConfig.Builder(name, registries)
    builder.block()
    return builder.build()
}

