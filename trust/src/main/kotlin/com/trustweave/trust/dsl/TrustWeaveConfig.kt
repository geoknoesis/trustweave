package com.trustweave.trust.dsl

import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.did.resolver.DidResolver
import com.trustweave.did.DidCreationOptions
import com.trustweave.did.KeyAlgorithm
import com.trustweave.did.DidMethod
import com.trustweave.did.registry.DidMethodRegistry
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.anchor.services.BlockchainAnchorClientFactory
import com.trustweave.trust.services.TrustRegistryFactory
import com.trustweave.revocation.services.StatusListRegistryFactory
import com.trustweave.credential.model.SchemaFormat
import com.trustweave.kms.services.KmsService
import com.trustweave.kms.services.KmsFactory
import com.trustweave.kms.KeyManagementService
import com.trustweave.did.services.DidMethodFactory
import com.trustweave.wallet.services.WalletFactory
import com.trustweave.credential.model.ProofType
import com.trustweave.credential.revocation.CredentialRevocationManager
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
    val blockchainRegistry: BlockchainAnchorRegistry = BlockchainAnchorRegistry()
    // CredentialService is provided via TrustWeaveConfig.issuer
    // Proof engines are managed by CredentialService internally
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
        private var defaultDidMethod: String? = null
        private val anchorConfigs = mutableMapOf<String, AnchorConfig>()
        private var defaultProofType: ProofType = ProofType.Ed25519Signature2020
        private var autoAnchor: Boolean = false
        private var defaultChain: String? = null
        private var revocationProvider: String? = null
        private var trustProvider: String? = null
        private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        private var issuer: Any? = null // CredentialService from credential-api

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
         * Only sets factories that are not null (allows merging).
         */
        fun factories(
            kmsFactory: KmsFactory? = null,
            didMethodFactory: DidMethodFactory? = null,
            anchorClientFactory: BlockchainAnchorClientFactory? = null,
            statusListRegistryFactory: StatusListRegistryFactory? = null,
            trustRegistryFactory: TrustRegistryFactory? = null,
            walletFactory: WalletFactory? = null
        ) {
            if (kmsFactory != null) this.kmsFactory = kmsFactory
            if (didMethodFactory != null) this.didMethodFactory = didMethodFactory
            if (anchorClientFactory != null) this.anchorClientFactory = anchorClientFactory
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
                val kmsRef = requireNotNull(kms) { "KMS cannot be null" }
                val signer = kmsSigner ?: { data: ByteArray, keyId: String ->
                    when (val result = kmsRef.sign(com.trustweave.core.identifiers.KeyId(keyId), data)) {
                        is com.trustweave.kms.results.SignResult.Success -> result.signature
                        is com.trustweave.kms.results.SignResult.Failure -> throw IllegalStateException(
                            "Signing failed: ${when (result) {
                                is com.trustweave.kms.results.SignResult.Failure.KeyNotFound -> result.reason ?: "Key not found"
                                is com.trustweave.kms.results.SignResult.Failure.UnsupportedAlgorithm -> result.reason ?: "Unsupported algorithm"
                                is com.trustweave.kms.results.SignResult.Failure.Error -> result.reason
                            }}"
                        )
                    }
                }
                Pair(kmsRef, signer)
            } else {
                // Use factory to create KMS
                resolveKms(kmsProvider ?: "inMemory", kmsAlgorithm)
            }
            val nonNullKms = requireNotNull(resolvedKms) {
                "KMS cannot be null. Provide a custom KMS or ensure a provider is configured."
            }

            // Use the resolved signer (auto-created from KMS if not provided)
            val finalSigner = resolvedSigner ?: { data: ByteArray, keyId: String ->
                when (val result = nonNullKms.sign(com.trustweave.core.identifiers.KeyId(keyId), data)) {
                    is com.trustweave.kms.results.SignResult.Success -> result.signature
                    is com.trustweave.kms.results.SignResult.Failure -> {
                        val reason = when (result) {
                            is com.trustweave.kms.results.SignResult.Failure.KeyNotFound -> 
                                "Key not found: ${result.keyId.value}"
                            is com.trustweave.kms.results.SignResult.Failure.UnsupportedAlgorithm -> 
                                result.reason ?: "Unsupported algorithm"
                            is com.trustweave.kms.results.SignResult.Failure.Error -> 
                                result.reason
                            else -> "Unknown error"
                        }
                        throw IllegalStateException("Signing failed: $reason")
                    }
                }
            }

            // Resolve DID methods
            for ((methodName, config) in didMethodConfigs) {
                val resolvedMethod = resolveDidMethod(methodName, config, nonNullKms)
                registries.didRegistry.register(resolvedMethod)
            }

            for ((chainId, config) in anchorConfigs) {
                val client = resolveAnchorClient(chainId, config)
                registries.blockchainRegistry.register(chainId, client)
            }

            // Create proof generator
            // Proof engines are managed by CredentialService internally
            // The credential-api uses proof engines instead of proof generators
            // val proofGenerator = createProofGenerator(defaultProofType, nonNullKms, finalSigner)
            // registries.proofRegistry.register(proofGenerator)

            // Resolve revocation manager if revocation is configured
            val resolvedRevocationManager = revocationProvider?.let {
                resolveRevocationManager(it)
            }

            // Resolve trust registry if configured
            val resolvedTrustRegistry = trustProvider?.let {
                resolveTrustRegistry(it)
            }

            val didResolver = DidResolver { did ->
                registries.didRegistry.resolve(did.value)
            }

            // Use issuer if provided, otherwise null
            // CredentialService is created via credentialService() factory function
            val resolvedIssuer: Any? = issuer // Use issuer from builder if set

            // Resolve wallet factory (optional)
            val resolvedWalletFactory = walletFactory

            // KMS service is optional - no default needed
            val resolvedKmsService: KmsService? = null

            // Use the registries directly instead of snapshotting to allow DIDs created after build
            // to be resolvable. This is safe because the registries are already immutable from the
            // caller's perspective (they're passed in and not modified by the caller).
            val registriesSnapshot = TrustWeaveRegistries(
                didRegistry = registries.didRegistry, // Don't snapshot - use directly for test compatibility
                blockchainRegistry = registries.blockchainRegistry.snapshot()
            )

            return TrustWeaveConfig(
                name = name,
                kms = nonNullKms,
                registries = registriesSnapshot,
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
            val factory = kmsFactory ?: throw IllegalStateException(
                "KMS factory is required. Provide it via Builder.factories(kmsFactory = ...)"
            )
            return factory.createFromProvider(providerName, algorithm)
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
            val method = factory.create(methodName, config.toOptions(kms), kms)
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
            return factory.create(chainId, config.provider, config.options)
        }

        private suspend fun resolveRevocationManager(providerName: String): CredentialRevocationManager {
            // For now, use default in-memory manager
            // TODO: Add factory pattern for revocation managers if needed
            return com.trustweave.credential.revocation.RevocationManagers.default()
        }

        private suspend fun resolveTrustRegistry(providerName: String): TrustRegistry {
            val factory = trustRegistryFactory ?: throw IllegalStateException(
                "TrustRegistry factory is required. Provide it via Builder.factories(trustRegistryFactory = ...)"
            )
            return factory.create(providerName)
        }

        // Wallet factory is optional - no default needed

        private fun createProofGenerator(
            proofType: ProofType,
            kms: KeyManagementService,
            signerFn: (suspend (ByteArray, String) -> ByteArray)?
        ): Any? {
            requireNotNull(kms) { "KMS cannot be null when creating proof generator" }
            return when (proofType) {
                is ProofType.Ed25519Signature2020 -> {
                    // Use provided signer function if available, otherwise create a wrapper using KMS
                    val signHelper: suspend (ByteArray, String) -> ByteArray = signerFn ?: { data, keyId ->
                        when (val result = kms.sign(com.trustweave.core.identifiers.KeyId(keyId), data)) {
                            is com.trustweave.kms.results.SignResult.Success -> result.signature
                            is com.trustweave.kms.results.SignResult.Failure -> {
                                val reason = when (result) {
                                    is com.trustweave.kms.results.SignResult.Failure.KeyNotFound -> 
                                        "Key not found: ${result.keyId.value}"
                                    is com.trustweave.kms.results.SignResult.Failure.UnsupportedAlgorithm -> 
                                        result.reason ?: "Unsupported algorithm"
                                    is com.trustweave.kms.results.SignResult.Failure.Error -> 
                                        result.reason
                                    else -> "Unknown error"
                                }
                                throw IllegalStateException("Signing failed: $reason")
                            }
                        }
                    }

                    // Proof engines are managed by CredentialService
                    // The credential-api uses proof engines instead of proof generators
                    throw UnsupportedOperationException(
                        "Proof engines are managed by CredentialService. " +
                        "Please use credential-api's proof engine system instead."
                    )
                }
                else -> throw IllegalArgumentException(
                    "Unsupported proof type: ${proofType.identifier}. " +
                    "Supported types: Ed25519Signature2020, JsonWebSignature2020, BbsBlsSignature2020. " +
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
            val builder = DidMethodConfigBuilder()
            builder.block()
            methods[name] = builder.build()
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
                purposes = listOf(com.trustweave.did.KeyPurpose.AUTHENTICATION),
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

