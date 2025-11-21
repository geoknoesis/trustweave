package com.geoknoesis.vericore.testkit

import com.geoknoesis.vericore.anchor.BlockchainAnchorClient
import com.geoknoesis.vericore.anchor.BlockchainAnchorRegistry
import com.geoknoesis.vericore.credential.CredentialServiceRegistry
import com.geoknoesis.vericore.did.DidCreationOptions
import com.geoknoesis.vericore.did.DidCreationOptions.KeyAlgorithm
import com.geoknoesis.vericore.did.DidDocument
import com.geoknoesis.vericore.did.DidMethod
import com.geoknoesis.vericore.did.DidMethodRegistry
import com.geoknoesis.vericore.did.didCreationOptions
import com.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import com.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import com.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Comprehensive test fixture builder for VeriCore tests.
 * 
 * Provides a fluent API for setting up complete test environments with:
 * - Key Management Service (KMS)
 * - DID Method
 * - Blockchain Anchor Client
 * - Automatic registry registration
 * 
 * **Example Usage**:
 * ```
 * val fixture = VeriCoreTestFixture.builder()
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
 * VeriCoreTestFixture.builder().build().use { fixture ->
 *     // Test code
 * }
 * ```
 */
class VeriCoreTestFixture private constructor(
    private val kms: com.geoknoesis.vericore.kms.KeyManagementService,
    private val didMethod: DidMethod,
    private val didRegistry: DidMethodRegistry,
    private val blockchainRegistry: BlockchainAnchorRegistry,
    private val credentialRegistry: CredentialServiceRegistry,
    private val blockchainClients: Map<String, BlockchainAnchorClient>
) : AutoCloseable {
    
    /**
     * Gets the Key Management Service.
     */
    fun getKms(): com.geoknoesis.vericore.kms.KeyManagementService = kms
    
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

    fun getCredentialRegistry(): CredentialServiceRegistry = credentialRegistry
    
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
        val subjectId = id ?: createIssuerDid().id
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
        credentialRegistry.clear()
    }
    
    /**
     * Builder for creating test fixtures.
     */
    class Builder {
        private var kms: com.geoknoesis.vericore.kms.KeyManagementService? = null
        private var didMethod: DidMethod? = null
        private val blockchainClients = mutableMapOf<String, BlockchainAnchorClient>()
        private var didRegistry: DidMethodRegistry? = null
        private var blockchainRegistry: BlockchainAnchorRegistry? = null
        private var credentialRegistry: CredentialServiceRegistry? = null
        
        /**
         * Sets the Key Management Service.
         * If not set, defaults to InMemoryKeyManagementService.
         */
        fun withKms(kms: com.geoknoesis.vericore.kms.KeyManagementService): Builder {
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
         * @param config Optional configuration map for the plugin
         * @return This builder
         */
        fun withDidMethodPlugin(methodName: String, config: Map<String, Any> = emptyMap()): Builder {
            val kmsInstance = kms ?: InMemoryKeyManagementService()
            
            // Try to load via SPI first
            val didMethod = try {
                loadDidMethodViaSpi(methodName, kmsInstance, config)
            } catch (e: Exception) {
                // Fall back to mock if SPI fails
                null
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
                // Fall back to in-memory if SPI fails
                InMemoryKeyManagementService()
            }
            
            this.kms = kmsInstance
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
                loadChainClientViaSpi(chainId, config) ?: InMemoryBlockchainAnchorClient(chainId)
            } catch (e: Exception) {
                // Fall back to in-memory if SPI fails
                InMemoryBlockchainAnchorClient(chainId)
            }
            
            blockchainClients[chainId] = client
            return this
        }
        
        /**
         * Helper to load DID method via SPI.
         */
        private fun loadDidMethodViaSpi(
            methodName: String,
            kms: com.geoknoesis.vericore.kms.KeyManagementService,
            config: Map<String, Any>
        ): DidMethod? {
            return try {
                val providerClass = Class.forName("com.geoknoesis.vericore.did.spi.DidMethodProvider")
                val serviceLoader = java.util.ServiceLoader.load(providerClass)
                
                for (provider in serviceLoader) {
                    val createMethod = providerClass.getMethod("create", String::class.java, 
                        com.geoknoesis.vericore.did.DidCreationOptions::class.java)
                    val options = com.geoknoesis.vericore.did.DidCreationOptions()
                    val method = createMethod.invoke(provider, methodName, options) as? DidMethod
                    if (method != null) {
                        return method
                    }
                }
                null
            } catch (e: Exception) {
                null
            }
        }
        
        /**
         * Helper to load KMS via SPI.
         */
        private fun loadKmsViaSpi(
            providerName: String,
            config: Map<String, Any>
        ): com.geoknoesis.vericore.kms.KeyManagementService? {
            return try {
                val providerClass = Class.forName("com.geoknoesis.vericore.kms.spi.KeyManagementServiceProvider")
                val serviceLoader = java.util.ServiceLoader.load(providerClass)
                
                for (provider in serviceLoader) {
                    val nameMethod = providerClass.getMethod("getName")
                    val providerNameValue = nameMethod.invoke(provider) as? String
                    if (providerNameValue == providerName) {
                        val createMethod = providerClass.getMethod("create", Map::class.java)
                        return createMethod.invoke(provider, config) as? com.geoknoesis.vericore.kms.KeyManagementService
                    }
                }
                null
            } catch (e: Exception) {
                null
            }
        }
        
        /**
         * Helper to load chain client via SPI.
         */
        private fun loadChainClientViaSpi(
            chainId: String,
            config: Map<String, Any>
        ): BlockchainAnchorClient? {
            return try {
                val providerClass = Class.forName("com.geoknoesis.vericore.anchor.spi.BlockchainAnchorClientProvider")
                val serviceLoader = java.util.ServiceLoader.load(providerClass)
                
                for (provider in serviceLoader) {
                    val createMethod = providerClass.getMethod("create", String::class.java, Map::class.java)
                    val client = createMethod.invoke(provider, chainId, config) as? BlockchainAnchorClient
                    if (client != null) {
                        return client
                    }
                }
                null
            } catch (e: Exception) {
                null
            }
        }
        
        /**
         * Builds the test fixture and registers all components.
         * 
         * @return The configured test fixture
         */
        fun build(): VeriCoreTestFixture {
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
            val credentialRegistryInstance = credentialRegistry ?: CredentialServiceRegistry.create()
            
            return VeriCoreTestFixture(
                kms = kmsInstance,
                didMethod = didMethodInstance,
                didRegistry = didRegistryInstance,
                blockchainRegistry = blockchainRegistryInstance,
                credentialRegistry = credentialRegistryInstance,
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
        fun minimal(): VeriCoreTestFixture {
            return builder()
                .withInMemoryBlockchainClient("algorand:testnet")
                .build()
        }
    }
}

