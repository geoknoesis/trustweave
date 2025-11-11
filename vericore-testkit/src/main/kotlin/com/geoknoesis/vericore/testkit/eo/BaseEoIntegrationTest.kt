package com.geoknoesis.vericore.testkit.eo

import com.geoknoesis.vericore.anchor.BlockchainAnchorClient
import com.geoknoesis.vericore.anchor.BlockchainAnchorRegistry
import com.geoknoesis.vericore.credential.CredentialServiceRegistry
import com.geoknoesis.vericore.did.DidMethod
import com.geoknoesis.vericore.did.DidMethodRegistry
import com.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import com.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking

/**
 * Base class for EO integration tests using TestContainers.
 * 
 * Provides common setup and teardown for EO integration tests.
 * Subclasses should implement the blockchain-specific anchor client creation.
 * 
 * Example usage:
 * ```kotlin
 * class MyBlockchainEoIntegrationTest : BaseEoIntegrationTest() {
 *     override fun createAnchorClient(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient {
 *         return MyBlockchainAnchorClient(chainId, options)
 *     }
 *     
 *     @Test
 *     fun `test EO scenario`() = runBlocking {
 *         val result = runEoTestScenario(chainId, options)
 *         assertTrue(result.verificationResult.valid)
 *     }
 * }
 * ```
 */
abstract class BaseEoIntegrationTest {

    protected val didRegistry: DidMethodRegistry = DidMethodRegistry()
    protected val blockchainRegistry: BlockchainAnchorRegistry = BlockchainAnchorRegistry()
    protected val credentialRegistry: CredentialServiceRegistry = CredentialServiceRegistry.create()

    /**
     * Creates a blockchain anchor client for the test.
     * Subclasses must implement this to provide their specific blockchain client.
     * 
     * @param chainId The chain ID
     * @param options Configuration options (e.g., RPC URL, private key from TestContainers)
     * @return A configured BlockchainAnchorClient
     */
    abstract fun createAnchorClient(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient

    /**
     * Gets the chain ID to use for testing.
     * Override if you need a different chain ID.
     */
    open fun getChainId(): String = "testnet:local"

    /**
     * Gets configuration options for the anchor client.
     * Override to provide TestContainers-specific options (RPC URL, private key, etc.).
     */
    open fun getAnchorClientOptions(): Map<String, Any?> = emptyMap()

    /**
     * Sets up DID method for testing.
     * Override if you need a different DID method.
     */
    open fun setupDidMethod(): DidMethod {
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        didRegistry.register(didMethod)
        return didMethod
    }

    /**
     * Cleans up registries after each test.
     * Override if you need additional cleanup.
     */
    open fun cleanup() {
        didRegistry.clear()
        blockchainRegistry.clear()
        credentialRegistry.clear()
    }

    /**
     * Runs a complete EO test scenario.
     * 
     * @param chainId Optional chain ID (uses getChainId() if not provided)
     * @param options Optional anchor client options (uses getAnchorClientOptions() if not provided)
     * @param datasetId Optional dataset ID
     * @param metadataTitle Optional metadata title
     * @param metadataDescription Optional metadata description
     * @return EoTestResult containing anchor result and verification result
     */
    fun runEoTestScenario(
        chainId: String? = null,
        options: Map<String, Any?>? = null,
        datasetId: String = "eo-dataset-test",
        metadataTitle: String = "Test EO Dataset",
        metadataDescription: String = "A test Earth Observation dataset for integrity verification"
    ): EoTestResult = runBlocking {
        // Setup DID method
        val didMethod = setupDidMethod()
        
        // Create issuer DID
        val issuerDoc = didMethod.createDid()
        val issuerDid = issuerDoc.id

        // Create anchor client
        val testChainId = chainId ?: getChainId()
        val testOptions = options ?: getAnchorClientOptions()
        val anchorClient = createAnchorClient(testChainId, testOptions)
        blockchainRegistry.register(testChainId, anchorClient)

        // Run complete EO scenario
        EoTestIntegration.runCompleteScenario(
            issuerDid = issuerDid,
            anchorClient = anchorClient,
            chainId = testChainId,
            datasetId = datasetId,
            metadataTitle = metadataTitle,
            metadataDescription = metadataDescription,
            blockchainRegistry = blockchainRegistry
        )
    }
}

