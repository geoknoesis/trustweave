package com.trustweave.testkit.eo

import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Example EO integration test using in-memory blockchain client.
 *
 * This demonstrates how to use BaseEoIntegrationTest and EoTestIntegration
 * to create reusable EO test scenarios without requiring TestContainers.
 *
 * For TestContainers examples, see the TrustWeave-ganache module tests.
 */
class InMemoryEoTestIntegrationExample : BaseEoIntegrationTest() {

    @AfterEach
    override fun cleanup() {
        super.cleanup()
    }

    override fun createAnchorClient(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient {
        // Use in-memory blockchain client for testing
        return InMemoryBlockchainAnchorClient(chainId)
    }

    override fun getChainId(): String = "testnet:in-memory"

    override fun getAnchorClientOptions(): Map<String, Any?> {
        return emptyMap()
    }

    @Test
    fun `end-to-end EO integrity chain verification with in-memory client`() = runBlocking {
        val result = runEoTestScenario()

        // Verify results
        assertNotNull(result.anchorResult)
        assertNotNull(result.verificationResult)
        assertTrue(result.verificationResult.valid, "Integrity chain verification should pass")

        println("\n=== EO Test Integration Results (In-Memory) ===")
        println("Chain ID: ${result.scenario.chainId}")
        println("Transaction Hash: ${result.anchorResult.ref.txHash}")
        println("VC Digest: ${result.scenario.vcDigest}")
        println("Linkset Digest: ${result.scenario.linksetDigest}")
        println("\nVerification Steps:")
        result.verificationResult.steps.forEachIndexed { index, step ->
            println("  ${index + 1}. ${step.name}: ${if (step.valid) "PASS" else "FAIL"}")
            if (step.digest != null) {
                println("     Digest: ${step.digest}")
            }
        }
        println("================================================\n")
    }

    @Test
    fun `test EO scenario with custom dataset`() = runBlocking {
        val result = runEoTestScenario(
            datasetId = "custom-eo-dataset-123",
            metadataTitle = "Custom EO Dataset",
            metadataDescription = "A custom Earth Observation dataset for testing"
        )

        assertTrue(result.verificationResult.valid)
        assertTrue(result.anchorResult.ref.txHash.isNotEmpty())

        // Verify custom dataset ID is in VC
        val vcSubject = result.scenario.vc["credentialSubject"]?.jsonObject
        assertNotNull(vcSubject)
        assertEquals("custom-eo-dataset-123", vcSubject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test EO scenario step by step`() = runBlocking {
        // Setup DID method
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        didRegistry.register(didMethod)

        // Create issuer DID
        val issuerDoc = didMethod.createDid()
        val issuerDid = issuerDoc.id

        // Create anchor client
        val chainId = getChainId()
        val anchorClient = createAnchorClient(chainId, getAnchorClientOptions())
        blockchainRegistry.register(chainId, anchorClient)

        // Create scenario
        val scenario = EoTestIntegration.createScenario(
            issuerDid = issuerDid,
            anchorClient = anchorClient,
            chainId = chainId,
            datasetId = "step-by-step-dataset",
            metadataTitle = "Step-by-Step EO Dataset"
        )

        // Verify scenario components
        assertNotNull(scenario.artifacts)
        assertEquals(3, scenario.artifacts.size)
        assertTrue(scenario.artifacts.containsKey("metadata-1"))
        assertTrue(scenario.artifacts.containsKey("provenance-1"))
        assertTrue(scenario.artifacts.containsKey("quality-1"))

        // Execute scenario
        val result = EoTestIntegration.executeScenario(scenario, blockchainRegistry)

        // Verify results
        assertTrue(result.verificationResult.valid)
        assertEquals(5, result.verificationResult.steps.size) // VC + Linkset + 3 artifacts
    }
}

