package io.geoknoesis.vericore.testkit

import io.geoknoesis.vericore.anchor.BlockchainAnchorClient
import io.geoknoesis.vericore.anchor.BlockchainRegistry
import io.geoknoesis.vericore.did.DidDocument
import io.geoknoesis.vericore.did.DidMethod
import io.geoknoesis.vericore.did.DidRegistry
import io.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService

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
    private val kms: io.geoknoesis.vericore.kms.KeyManagementService,
    private val didMethod: DidMethod,
    private val blockchainClients: Map<String, BlockchainAnchorClient>
) : AutoCloseable {
    
    /**
     * Gets the Key Management Service.
     */
    fun getKms(): io.geoknoesis.vericore.kms.KeyManagementService = kms
    
    /**
     * Gets the DID Method.
     */
    fun getDidMethod(): DidMethod = didMethod
    
    /**
     * Gets a blockchain anchor client by chain ID.
     * 
     * @param chainId The chain ID
     * @return The blockchain anchor client, or null if not registered
     */
    fun getBlockchainClient(chainId: String): BlockchainAnchorClient? {
        return blockchainClients[chainId]
    }
    
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
        return didMethod.createDid(mapOf("algorithm" to algorithm))
    }
    
    /**
     * Cleans up all registries (for testing).
     */
    override fun close() {
        DidRegistry.clear()
        BlockchainRegistry.clear()
    }
    
    /**
     * Builder for creating test fixtures.
     */
    class Builder {
        private var kms: io.geoknoesis.vericore.kms.KeyManagementService? = null
        private var didMethod: DidMethod? = null
        private val blockchainClients = mutableMapOf<String, BlockchainAnchorClient>()
        
        /**
         * Sets the Key Management Service.
         * If not set, defaults to InMemoryKeyManagementService.
         */
        fun withKms(kms: io.geoknoesis.vericore.kms.KeyManagementService): Builder {
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
         * Builds the test fixture and registers all components.
         * 
         * @return The configured test fixture
         */
        fun build(): VeriCoreTestFixture {
            val kmsInstance = kms ?: InMemoryKeyManagementService()
            val didMethodInstance = didMethod ?: DidKeyMockMethod(kmsInstance)
            
            // Register DID method
            DidRegistry.register(didMethodInstance)
            
            // Register blockchain clients
            blockchainClients.forEach { (chainId, client) ->
                BlockchainRegistry.register(chainId, client)
            }
            
            return VeriCoreTestFixture(kmsInstance, didMethodInstance, blockchainClients.toMap())
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

