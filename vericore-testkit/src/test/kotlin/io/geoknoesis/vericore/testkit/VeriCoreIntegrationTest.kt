package io.geoknoesis.vericore.testkit

import io.geoknoesis.vericore.anchor.*
import io.geoknoesis.vericore.did.*
import io.geoknoesis.vericore.json.DigestUtils
import io.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Integration tests demonstrating how VeriCore modules work together.
 */
class VeriCoreIntegrationTest {

    @Serializable
    data class VerifiableCredentialDigest(
        val vcId: String,
        val vcDigest: String
    )

    @Test
    fun integrationTest_DIDCreationDigestComputationAndAnchoring() = runBlocking {
        // Setup: Create KMS, DID method, and blockchain client
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val anchorClient = InMemoryBlockchainAnchorClient("algorand:mainnet", "app-123")
        
        // Register the DID method and blockchain client
        DidRegistry.register(didMethod)
        BlockchainRegistry.register("algorand:mainnet", anchorClient)

        // Step 1: Create a DID
        val didDocument = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
        assertNotNull(didDocument.id)
        println("Created DID: ${didDocument.id}")

        // Step 2: Create a verifiable credential digest
        val vcPayload = buildJsonObject {
            put("vcId", "vc-12345")
            put("issuer", didDocument.id)
            put("credentialSubject", buildJsonObject {
                put("id", "subject-123")
            })
        }

        // Step 3: Compute digest
        val digest = DigestUtils.sha256DigestMultibase(vcPayload)
        assertNotNull(digest)
        assert(digest.startsWith("u"))
        println("Computed digest: $digest")

        // Step 4: Create digest object and anchor it
        val digestObj = VerifiableCredentialDigest(
            vcId = "vc-12345",
            vcDigest = digest
        )

        val anchorResult = anchorTyped(
            value = digestObj,
            serializer = VerifiableCredentialDigest.serializer(),
            targetChainId = "algorand:mainnet"
        )

        assertNotNull(anchorResult.ref)
        println("Anchored at: ${anchorResult.ref.txHash}")

        // Step 5: Read back the anchored data
        val retrieved = readTyped<VerifiableCredentialDigest>(
            ref = anchorResult.ref,
            serializer = VerifiableCredentialDigest.serializer()
        )

        assertEquals(digestObj.vcId, retrieved.vcId)
        assertEquals(digestObj.vcDigest, retrieved.vcDigest)

        // Cleanup
        DidRegistry.clear()
        BlockchainRegistry.clear()
    }

    @Test
    fun integrationTest_RoundTripAnchorTypedAndReadTyped() = runBlocking {
        val client = InMemoryBlockchainAnchorClient("test:chain")
        BlockchainRegistry.register("test:chain", client)

        val original = VerifiableCredentialDigest("vc-999", "uXYZ789")
        
        val anchored = anchorTyped(
            value = original,
            serializer = VerifiableCredentialDigest.serializer(),
            targetChainId = "test:chain"
        )

        val retrieved = readTyped<VerifiableCredentialDigest>(
            ref = anchored.ref,
            serializer = VerifiableCredentialDigest.serializer()
        )

        assertEquals(original, retrieved)
        
        BlockchainRegistry.clear()
    }
}

