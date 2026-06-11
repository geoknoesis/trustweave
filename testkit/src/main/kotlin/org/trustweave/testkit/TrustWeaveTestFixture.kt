package org.trustweave.testkit

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.anchor.BlockchainAnchorRegistry
import org.trustweave.anchor.spi.BlockchainAnchorClientProvider
import org.trustweave.did.model.DidDocument
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.DidMethod
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.didCreationOptions
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.kms.spi.KeyManagementServiceProvider
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.util.ServiceLoader

/**
 * Comprehensive test fixture builder for TrustWeave tests.
 *
 * Provides a fluent API for setting up complete test environments with:
 * - Key Management Service (KMS)
 * - DID Method
 * - Blockchain Anchor Client
 * - Automatic registry registration
 *
 * **Example Usage**:
 * ```
 * val fixture = TrustWeaveTestFixture.builder()
 *     .withKms(InMemoryKeyManagementService())
 *     .withDidMethod("key")
 *     .withBlockchainClient("algorand:testnet", InMemoryBlockchainAnchorClient("algorand:testnet"))
 *     .build()
 *
 * val issuerDoc = fixture.createIssuerDid()
 * val anchorClient = fixture.getBlockchainClient("algorand:testnet")
 * ```
 *
 * **Automatic Cleanup**: Use `use {}` pattern for automatic cleanup:
 * ```
 * TrustWeaveTestFixture.builder().build().use { fixture ->
 *     // Test code
 * }
 * ```
 */
import org.trustweave.kms.KeyManagementService

private val logger = LoggerFactory.getLogger(TrustWeaveTestFixture::class.java)

class TrustWeaveTestFixture private constructor(
    private val kms: KeyManagementService,
    private val didMethod: DidMethod,
    private val didRegistry: DidMethodRegistry,
    private val blockchainRegistry: BlockchainAnchorRegistry,
    private val blockchainClients: Map<String, BlockchainAnchorClient>
) : AutoCloseable {
    // CredentialService is obtained from TrustWeave or CredentialService directly; fixture provides KMS, DID, and anchor setup.

    /**
     * Gets the Key Management Service.
     */
    fun getKms(): KeyManagementService = kms

    /**
     * Gets the DID Method.
     */
    fun getDidMethod(): DidMethod = didMethod

    /**
     * Exposes the DID registry used by the fixture.
     */
    fun getDidRegistry(): DidMethodRegistry = didRegistry

    /**
     * Gets a blockchain anchor client by chain ID.
     *
     * @param chainId The chain ID
     * @return The blockchain anchor client, or null if not registered
     */
    fun getBlockchainClient(chainId: String): BlockchainAnchorClient? {
        return blockchainClients[chainId]
    }

    fun getBlockchainRegistry(): BlockchainAnchorRegistry = blockchainRegistry

    /**
     * Gets all registered blockchain clients.
     */
    fun getAllBlockchainClients(): Map<String, BlockchainAnchorClient> = blockchainClients

    /**
     * Creates a new issuer DID with default options.
     *
     * @param algorithm The key algorithm (default: "Ed25519")
     * @return The created DID document
     */
    suspend fun createIssuerDid(algorithm: String = "Ed25519"): DidDocument {
        val keyAlgorithm = KeyAlgorithm.fromName(algorithm) ?: KeyAlgorithm.ED25519
        return didMethod.createDid(
            didCreationOptions {
                this.algorithm = keyAlgorithm
            }
        )
    }

    /**
     * Creates a test credential subject with default values.
     *
     * @param id Optional subject ID (defaults to a test DID)
     * @param additionalClaims Additional claims to add
     * @return JSON object representing the credential subject
     */
    suspend fun createTestCredentialSubject(
        id: String? = null,
        additionalClaims: Map<String, Any> = emptyMap()
    ): JsonObject {
        val subjectId = id ?: createIssuerDid().id.value
        return buildJsonObject {
            put("id", subjectId)
            put("name", "Test Subject")
            additionalClaims.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, value)
                    is Number -> put(key, value)
                    is Boolean -> put(key, value)
                    else -> put(key, value.toString())
                }
            }
        }
    }

    /**
     * Cleans up all registries (for testing).
     */
    override fun close() {
        didRegistry.clear()
        blockchainRegistry.clear()
    }

    /**
     * Builder for creating test fixtures.
     */
    class Builder {
        private var kms: KeyManagementService? = null
        private var didMethod: DidMethod? = null
        private val blockchainClients = mutableMapOf<String, BlockchainAnchorClient>()
        private var didRegistry: DidMethodRegistry? = null
        private var blockchainRegistry: BlockchainAnchorRegistry? = null

        /**
         * Sets the Key Management Service.
         * If not set, defaults to InMemoryKeyManagementService.
         */
        fun withKms(kms: KeyManagementService): Builder {
            this.kms = kms
            return this
        }

        /**
         * Sets the DID method by name.
         * If not set, defaults to "key" (DidKeyMockMethod).
         *
         * @param methodName The DID method name (e.g., "key", "web")
         */
        fun withDidMethod(methodName: String = "key"): Builder {
            val kmsInstance = kms ?: InMemoryKeyManagementService()
            this.didMethod = when (methodName) {
                "key" -> DidKeyMockMethod(kmsInstance)
                else -> throw IllegalArgumentException("Unsupported DID method: $methodName. Use 'key' for testing.")
            }
            return this
        }

        /**
         * Sets a custom DID method.
         */
        fun withDidMethod(method: DidMethod): Builder {
            this.didMethod = method
            return this
        }

        /**
         * Registers a blockchain anchor client.
         *
         * @param chainId The chain ID
         * @param client The blockchain anchor client
         */
        fun withBlockchainClient(chainId: String, client: BlockchainAnchorClient): Builder {
            blockchainClients[chainId] = client
            return this
        }

        /**
         * Registers an in-memory blockchain anchor client.
         *
         * @param chainId The chain ID
         * @param contract Optional contract address/app ID
         */
        fun withInMemoryBlockchainClient(chainId: String, contract: String? = null): Builder {
            blockchainClients[chainId] = InMemoryBlockchainAnchorClient(chainId, contract)
            return this
        }

        /**
         * Sets a DID method plugin by name.
         * Attempts to load the plugin via SPI if available, otherwise uses mock.
         *
         * @param methodName The DID method name (e.g., "key", "web", "ion")
         * @param config Optional configuration map for the plugin (currently unused by SPI loading)
         * @return This builder
         */
        fun withDidMethodPlugin(methodName: String, config: Map<String, Any> = emptyMap()): Builder {
            val kmsInstance = kms ?: InMemoryKeyManagementService()

            // Try to load via SPI first; fall back to the in-memory mock with a clear warning.
            val didMethod = try {
                loadDidMethodViaSpi(methodName)
            } catch (e: Exception) {
                logger.warn(
                    "SPI loading of DID method '{}' failed ({}); falling back to in-memory test double",
                    methodName, e.toString(), e
                )
                null
            }
            if (didMethod == null) {
                logger.warn(
                    "No SPI DidMethodProvider available for DID method '{}'; falling back to in-memory test double",
                    methodName
                )
            }

            this.didMethod = didMethod ?: when (methodName) {
                "key" -> DidKeyMockMethod(kmsInstance)
                else -> throw IllegalArgumentException(
                    "DID method '$methodName' not available. " +
                    "Add the plugin dependency or use 'key' for testing."
                )
            }
            return this
        }

        /**
         * Sets a KMS plugin by provider name.
         * Attempts to load the plugin via SPI if available, otherwise uses in-memory.
         *
         * @param providerName The KMS provider name (e.g., "aws", "azure", "google")
         * @param config Configuration map for the KMS plugin
         * @return This builder
         */
        fun withKmsPlugin(providerName: String, config: Map<String, Any>): Builder {
            val kmsInstance = try {
                loadKmsViaSpi(providerName, config)
            } catch (e: Exception) {
                logger.warn(
                    "SPI loading of KMS provider '{}' failed ({}); falling back to InMemoryKeyManagementService",
                    providerName, e.toString(), e
                )
                null
            }
            if (kmsInstance == null) {
                logger.warn(
                    "No SPI KeyManagementServiceProvider named '{}' available; falling back to InMemoryKeyManagementService",
                    providerName
                )
            }

            this.kms = kmsInstance ?: InMemoryKeyManagementService()
            return this
        }

        /**
         * Sets a chain plugin by chain ID.
         * Attempts to load the plugin via SPI if available, otherwise uses in-memory.
         *
         * @param chainId The chain ID (e.g., "eip155:1", "algorand:testnet")
         * @param config Configuration map for the chain plugin
         * @return This builder
         */
        fun withChainPlugin(chainId: String, config: Map<String, Any> = emptyMap()): Builder {
            val client = try {
                loadChainClientViaSpi(chainId, config)
            } catch (e: Exception) {
                logger.warn(
                    "SPI loading of anchor client for chain '{}' failed ({}); falling back to InMemoryBlockchainAnchorClient",
                    chainId, e.toString(), e
                )
                null
            }
            if (client == null) {
                logger.warn(
                    "No SPI BlockchainAnchorClientProvider available for chain '{}'; falling back to InMemoryBlockchainAnchorClient",
                    chainId
                )
            }

            blockchainClients[chainId] = client ?: InMemoryBlockchainAnchorClient(chainId)
            return this
        }

        /**
         * Loads a DID method via typed [ServiceLoader] lookup of [DidMethodProvider].
         *
         * @return The first matching DID method, or null if no provider supports [methodName]
         */
        private fun loadDidMethodViaSpi(methodName: String): DidMethod? {
            val providers = ServiceLoader.load(DidMethodProvider::class.java)
            for (provider in providers) {
                if (methodName in provider.supportedMethods && provider.hasRequiredEnvironmentVariables()) {
                    val method = provider.create(methodName, DidCreationOptions())
                    if (method != null) {
                        return method
                    }
                }
            }
            return null
        }

        /**
         * Loads a KMS via typed [ServiceLoader] lookup of [KeyManagementServiceProvider].
         *
         * @return The KMS from the provider named [providerName], or null if not found
         */
        private fun loadKmsViaSpi(
            providerName: String,
            config: Map<String, Any>
        ): KeyManagementService? {
            val providers = ServiceLoader.load(KeyManagementServiceProvider::class.java)
            // Guard on required environment variables like the DID/chain loaders do:
            // cloud KMS clients often construct lazily and would otherwise be handed
            // back broken (no credentials) without triggering the fallback path.
            return providers
                .firstOrNull { it.name == providerName && it.hasRequiredEnvironmentVariables() }
                ?.create(config)
        }

        /**
         * Loads a blockchain anchor client via typed [ServiceLoader] lookup of
         * [BlockchainAnchorClientProvider].
         *
         * @return The first client created for [chainId], or null if no provider supports it
         */
        private fun loadChainClientViaSpi(
            chainId: String,
            config: Map<String, Any>
        ): BlockchainAnchorClient? {
            val providers = ServiceLoader.load(BlockchainAnchorClientProvider::class.java)
            for (provider in providers) {
                if (!provider.hasRequiredEnvironmentVariables()) {
                    continue
                }
                val client = provider.create(chainId, config)
                if (client != null) {
                    return client
                }
            }
            return null
        }

        /**
         * Builds the test fixture and registers all components.
         *
         * @return The configured test fixture
         */
        fun build(): TrustWeaveTestFixture {
            val kmsInstance = kms ?: InMemoryKeyManagementService()
            val didMethodInstance = didMethod ?: DidKeyMockMethod(kmsInstance)
            val didRegistryInstance = (didRegistry ?: DidMethodRegistry()).also {
                it.register(didMethodInstance)
            }
            val blockchainRegistryInstance = (blockchainRegistry ?: BlockchainAnchorRegistry()).also { registry ->
                blockchainClients.forEach { (chainId, client) ->
                    registry.register(chainId, client)
                }
            }
            return TrustWeaveTestFixture(
                kms = kmsInstance,
                didMethod = didMethodInstance,
                didRegistry = didRegistryInstance,
                blockchainRegistry = blockchainRegistryInstance,
                blockchainClients = blockchainClients.toMap()
            )
        }
    }

    companion object {
        /**
         * Creates a new builder for test fixtures.
         */
        @JvmStatic
        fun builder(): Builder = Builder()

        /**
         * Creates a minimal test fixture with defaults.
         *
         * - InMemoryKeyManagementService
         * - DidKeyMockMethod
         * - InMemoryBlockchainAnchorClient for "algorand:testnet"
         */
        @JvmStatic
        fun minimal(): TrustWeaveTestFixture {
            return builder()
                .withInMemoryBlockchainClient("algorand:testnet")
                .build()
        }
    }
}

