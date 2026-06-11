package org.trustweave.trust.dsl

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.anchor.spi.BlockchainAnchorClientProvider
import org.trustweave.credential.CredentialService
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.did.DidMethod
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.registry.DidMethodAutoRegisterFailure
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.KeyManagementServices
import org.trustweave.kms.results.SignResult
import org.trustweave.trust.TrustRegistry
import org.trustweave.trust.domain.TrustedDomainManager
import org.trustweave.trust.domain.treasury.ChainAccount
import org.trustweave.trust.domain.treasury.InMemoryChainAccount
import org.trustweave.trust.domain.treasury.InMemoryDomainTreasury
import org.trustweave.core.exception.ConfigException
import org.trustweave.trust.dsl.builders.AnchorConfig
import org.trustweave.trust.dsl.builders.DidMethodConfig
import org.trustweave.trust.dsl.builders.DomainConfig
import org.trustweave.trust.services.DefaultKmsService
import org.trustweave.trust.services.DefaultTrustRegistryFactory
import org.trustweave.trust.services.TrustRegistryFactory
import org.trustweave.anchor.BlockchainAnchorRegistry
import org.slf4j.LoggerFactory
import java.util.ServiceConfigurationError
import java.util.ServiceLoader

/**
 * Factory responsible for resolving and wiring all TrustWeave infrastructure components.
 *
 * Extracted from [TrustWeaveConfig.Builder] to give it a single, focused responsibility:
 * translating builder state into a fully-initialized [TrustWeaveConfig]. All SPI
 * auto-discovery, KMS setup, credential service creation, and registry wiring live here.
 */
internal object TrustWeaveFactory {
    private val logger = LoggerFactory.getLogger(TrustWeaveFactory::class.java)

    /**
     * Build a [TrustWeaveConfig] from the provided builder state.
     */
    suspend fun build(state: TrustWeaveConfig.BuilderState): TrustWeaveConfig {
        val (resolvedKms, resolvedSigner) = if (state.kms != null) {
            val kmsRef = requireNotNull(state.kms)
            val signer = state.kmsSigner ?: createSignerFromKms(kmsRef, extractKeyId = true)
            Pair(kmsRef, signer)
        } else {
            resolveKms(state.kmsProvider ?: "inMemory", state.kmsAlgorithm)
        }

        val nonNullKms = requireNotNull(resolvedKms) {
            "KMS cannot be null. Provide a custom KMS or ensure a provider is configured."
        }
        val finalSigner = resolvedSigner ?: createSignerFromKms(nonNullKms, extractKeyId = false)

        val didRegistry = state.didRegistry

        // Auto-register DID methods from SPI
        val autoRegisterResult = DidMethodRegistry.autoRegister(nonNullKms)
        if (autoRegisterResult.hasFailures) {
            for (failure in autoRegisterResult.failures) {
                logDidMethodAutoRegisterFailure(failure)
            }
        }
        for ((methodName, method) in autoRegisterResult.registry.getAllMethods()) {
            if (methodName !in didRegistry) {
                didRegistry.register(method)
            }
        }

        // Register configured DID methods (may override auto-registered ones)
        for ((methodName, config) in state.didMethodConfigs) {
            val resolvedMethod = resolveDidMethod(methodName, config, nonNullKms, didRegistry)
            didRegistry.register(resolvedMethod)
        }

        var defaultDidMethod = state.defaultDidMethod
        if (state.didMethodConfigs.isEmpty() && didRegistry.getAllMethodNames().isEmpty()) {
            val defaultMethod = resolveDidMethod(
                "key",
                DidMethodConfig(algorithm = KeyAlgorithm.ED25519),
                nonNullKms,
                didRegistry
            )
            didRegistry.register(defaultMethod)
            defaultDidMethod = "key"
        }

        val blockchainRegistry = state.blockchainRegistry
        for ((chainId, config) in state.anchorConfigs) {
            val client = resolveAnchorClient(chainId, config)
            blockchainRegistry.register(chainId, client)
        }

        val resolvedRevocationManager = state.revocationProvider?.let {
            resolveRevocationManager(it)
        }

        val resolvedTrustRegistry = state.trustProvider?.let {
            resolveTrustRegistry(it, state.trustRegistryFactory)
        }

        val didResolver = DidResolver { did -> didRegistry.resolve(did.value) }

        val resolvedCredentialService =
            resolveCredentialService(state, didResolver, finalSigner, nonNullKms)

        val snapshotRegistry = blockchainRegistry.snapshot()
        val trustedDomainManager = state.domainConfig?.let {
            buildTrustedDomainManager(it, snapshotRegistry, nonNullKms)
        }

        return TrustWeaveConfig(
            name = state.name,
            kms = nonNullKms,
            didRegistry = didRegistry,
            blockchainRegistry = snapshotRegistry,
            credentialConfig = TrustWeaveConfig.CredentialConfig(
                defaultProofType = state.defaultProofType,
                autoAnchor = state.autoAnchor,
                defaultChain = state.defaultChain
            ),
            credentialService = resolvedCredentialService,
            didResolver = didResolver,
            revocationManager = resolvedRevocationManager,
            trustRegistry = resolvedTrustRegistry,
            walletFactory = state.walletFactory,
            // Derive the KmsService adapter from the same KMS the keys { } block resolved,
            // so facade operations that need it (e.g. rotateKey) work without extra setup.
            kmsService = DefaultKmsService(),
            defaultDidMethod = defaultDidMethod,
            ioDispatcher = state.ioDispatcher,
            smartContractService = state.smartContractService,
            trustedDomainManager = trustedDomainManager,
            // Ownership drives TrustWeave.close(): only components this factory created
            // are closed by the facade; caller-injected ones remain caller-owned.
            ownership = ComponentOwnership(
                ownsKms = state.kms == null,
                ownsCredentialService = state.credentialService == null &&
                    resolvedCredentialService != null,
                ownsRevocationManager = resolvedRevocationManager != null,
                ownsTrustRegistry = resolvedTrustRegistry != null,
                // Every method present at this point was created during this build
                // (SPI auto-registration or did { method(...) } resolution); methods the
                // caller registers later via getDidRegistry() are not snapshotted here.
                ownedDidMethods = didRegistry.getAllMethods().values.toList(),
            ),
        )
    }

    private fun buildTrustedDomainManager(
        config: DomainConfig,
        registry: BlockchainAnchorRegistry,
        kms: KeyManagementService,
    ): TrustedDomainManager {
        val accounts: Map<String, ChainAccount> = config.accounts.associate { acc ->
            val client = registry.get(acc.chainId)
                ?: throw IllegalStateException(
                    "domain { chainAccount(\"${acc.chainId}\") } references a chain " +
                        "that is not registered. Configure anchor { chain(\"${acc.chainId}\") { ... } } first.",
                )
            acc.chainId to InMemoryChainAccount(
                chainId = acc.chainId,
                address = acc.address,
                keyRef = acc.keyRef,
                anchorClient = client,
                kms = kms,
            )
        }
        val treasury = InMemoryDomainTreasury(
            domainId = config.domainId,
            accounts = accounts,
            spendPolicy = config.spendPolicy,
        )
        return TrustedDomainManager(
            domainId = config.domainId,
            payerDid = config.payerDid,
            treasury = treasury,
            registry = registry,
        )
    }

    private fun resolveCredentialService(
        state: TrustWeaveConfig.BuilderState,
        didResolver: DidResolver,
        finalSigner: suspend (ByteArray, String) -> ByteArray,
        nonNullKms: KeyManagementService,
    ): CredentialService? =
        state.credentialService ?: if (state.kmsSigner != null) {
            org.trustweave.credential.credentialService(
                didResolver = didResolver,
                signer = finalSigner,
            )
        } else {
            org.trustweave.credential.CredentialServices.createCredentialService(
                kms = nonNullKms,
                didResolver = didResolver,
            )
        }

    private suspend fun resolveKms(
        providerName: String,
        algorithm: String
    ): Pair<KeyManagementService, (suspend (ByteArray, String) -> ByteArray)?> {
        val kms = try {
            KeyManagementServices.create(providerName, mapOf("algorithm" to algorithm))
        } catch (e: IllegalArgumentException) {
            throw ConfigException.UnsupportedValue(
                field = "keys.provider",
                value = providerName,
                reason = "KMS provider not found. " +
                    "Available providers: ${KeyManagementServices.availableProviders()}. " +
                    "Ensure the provider is on the classpath.",
                cause = e
            )
        }
        val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
            when (val result = kms.sign(org.trustweave.core.identifiers.KeyId(keyId), data)) {
                is SignResult.Success -> result.signature
                is SignResult.Failure.KeyNotFound ->
                    throw IllegalStateException("Signing failed: Key not found: ${result.keyId.value}")
                is SignResult.Failure.UnsupportedAlgorithm ->
                    throw IllegalStateException("Signing failed: Unsupported algorithm: ${result.reason ?: "Algorithm mismatch"}")
                is SignResult.Failure.Error ->
                    throw IllegalStateException("Signing failed: ${result.reason}")
            }
        }
        return Pair(kms, signer)
    }

    private suspend fun resolveDidMethod(
        methodName: String,
        config: DidMethodConfig,
        kms: KeyManagementService,
        didRegistry: DidMethodRegistry
    ): DidMethod {
        val existing = didRegistry[methodName]
        if (existing != null) return existing

        for (provider in loadProvidersIsolated(DidMethodProvider::class.java)) {
            val method = try {
                if (methodName in provider.supportedMethods && provider.hasRequiredEnvironmentVariables()) {
                    provider.create(methodName, config.toOptions(kms))
                } else {
                    null
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // One broken provider must not prevent the remaining providers from being
                // tried (mirrors DidMethodRegistry.autoRegister's collecting behavior).
                logger.warn(
                    "DidMethodProvider {} failed for method '{}': {} — skipping",
                    provider::class.java.name, methodName, e.message ?: e::class.java.simpleName, e
                )
                null
            }
            if (method != null) return method
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
        val providerName = config.provider ?: chainId.substringBefore(":")

        var sawMatchingProvider = false
        for (provider in loadProvidersIsolated(BlockchainAnchorClientProvider::class.java)) {
            val client = try {
                val matches = provider.name == providerName &&
                    (chainId in provider.supportedChains || provider.supportedChains.isEmpty()) &&
                    provider.hasRequiredEnvironmentVariables()
                if (matches) {
                    sawMatchingProvider = true
                    provider.create(chainId, config.options)
                } else {
                    null
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Skip broken providers so one bad SPI registration is not fatal.
                logger.warn(
                    "BlockchainAnchorClientProvider {} failed for chain '{}': {} — skipping",
                    provider::class.java.name, chainId, e.message ?: e::class.java.simpleName, e
                )
                null
            }
            if (client != null) return client
        }

        if (sawMatchingProvider) {
            throw IllegalStateException(
                "Provider '$providerName' does not support chain '$chainId' " +
                "(create returned null or failed for every matching provider)."
            )
        }
        throw IllegalStateException(
            "Anchor provider '$providerName' not found for chain '$chainId'. " +
            "Ensure the provider is on the classpath."
        )
    }

    /**
     * Loads SPI providers with per-provider error isolation: a provider whose class cannot
     * be loaded/instantiated ([ServiceConfigurationError]) is logged and skipped instead of
     * aborting discovery. If the underlying iteration itself becomes poisoned (e.g. a
     * malformed `META-INF/services` file makes `hasNext` fail), the providers discovered so
     * far are returned rather than failing the whole build.
     */
    private fun <T : Any> loadProvidersIsolated(serviceClass: Class<T>): List<T> {
        val providers = mutableListOf<T>()
        val iterator = ServiceLoader.load(serviceClass).iterator()
        while (true) {
            val hasNext = try {
                iterator.hasNext()
            } catch (e: ServiceConfigurationError) {
                logger.warn(
                    "SPI discovery for {} aborted after {} provider(s): {}",
                    serviceClass.simpleName, providers.size, e.message, e
                )
                false
            }
            if (!hasNext) break
            try {
                providers += iterator.next()
            } catch (e: ServiceConfigurationError) {
                logger.warn(
                    "Skipping broken {} SPI provider: {}",
                    serviceClass.simpleName, e.message, e
                )
            }
        }
        return providers
    }

    private fun resolveRevocationManager(
        providerName: String
    ): CredentialRevocationManager = when (providerName) {
        "inMemory", "in-memory", "default" ->
            org.trustweave.credential.revocation.RevocationManagers.default()
        else -> throw ConfigException.UnsupportedValue(
            field = "revocation.provider",
            value = providerName,
            reason = "Named revocation providers are not yet supported. " +
                "Use \"inMemory\" for the default in-memory manager, or wire a custom " +
                "CredentialRevocationManager programmatically."
        )
    }

    private suspend fun resolveTrustRegistry(
        providerName: String,
        factory: TrustRegistryFactory?
    ): TrustRegistry {
        // No factory supplied: fall back to the built-in in-memory registry for the
        // in-memory provider names so `TrustWeave.quickStart()` works out of the box.
        val f = factory ?: when (providerName) {
            in DefaultTrustRegistryFactory.IN_MEMORY_PROVIDERS -> DefaultTrustRegistryFactory
            else -> throw IllegalStateException(
                "TrustRegistry factory is required for provider '$providerName'. " +
                    "Provide it via Builder.factories(trustRegistryFactory = ...)"
            )
        }
        return f.create(providerName)
    }

    internal fun createSignerFromKms(
        kms: KeyManagementService,
        extractKeyId: Boolean = false
    ): suspend (ByteArray, String) -> ByteArray = { data: ByteArray, keyId: String ->
        val actualKeyId = if (extractKeyId && keyId.contains("#")) {
            keyId.substringAfter("#")
        } else {
            keyId
        }
        when (val result = kms.sign(org.trustweave.core.identifiers.KeyId(actualKeyId), data)) {
            is SignResult.Success -> result.signature
            is SignResult.Failure -> {
                val reason = when (result) {
                    is SignResult.Failure.KeyNotFound -> result.reason ?: "Key not found: ${result.keyId.value}"
                    is SignResult.Failure.UnsupportedAlgorithm -> result.reason ?: "Unsupported algorithm"
                    is SignResult.Failure.Error -> result.reason
                }
                throw IllegalStateException("Signing failed: $reason")
            }
        }
    }

    private fun logDidMethodAutoRegisterFailure(failure: DidMethodAutoRegisterFailure) {
        val msg = "DidMethod SPI auto-register [{}]: {}"
        val phase = failure.phase
        val text = failure.message
        val cause = failure.cause
        if (phase == "environment") {
            if (logger.isDebugEnabled) {
                if (cause != null) {
                    logger.debug(msg, phase, text, cause)
                } else {
                    logger.debug(msg, phase, text)
                }
            }
        } else {
            if (cause != null) {
                logger.warn(msg, phase, text, cause)
            } else {
                logger.warn(msg, phase, text)
            }
        }
    }
}
