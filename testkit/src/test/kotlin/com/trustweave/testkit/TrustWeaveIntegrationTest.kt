package com.trustweave.testkit

import com.trustweave.anchor.AnchorResult
import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.json.DigestUtils
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Integration tests demonstrating how TrustWeave modules work together.
 */
class TrustWeaveIntegrationTest {

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
        
        // Step 1: Create a DID
        val didDocument = didMethod.createDid()
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

        val payload = Json.encodeToJsonElement(VerifiableCredentialDigest.serializer(), digestObj)
        val anchorResult = anchorClient.writePayload(payload)

        assertNotNull(anchorResult.ref)
        println("Anchored at: ${anchorResult.ref.txHash}")

        // Step 5: Read back the anchored data
        val retrievedPayload = anchorClient.readPayload(anchorResult.ref).payload
        val retrieved = Json.decodeFromJsonElement(VerifiableCredentialDigest.serializer(), retrievedPayload)

        assertEquals(digestObj.vcId, retrieved.vcId)
        assertEquals(digestObj.vcDigest, retrieved.vcDigest)
    }

    @Test
    fun integrationTest_RoundTripAnchorTypedAndReadTyped() = runBlocking {
        val client = InMemoryBlockchainAnchorClient("test:chain")

        val original = VerifiableCredentialDigest("vc-999", "uXYZ789")
        
        val anchored = client.writePayload(
            Json.encodeToJsonElement(VerifiableCredentialDigest.serializer(), original)
        )

        val retrieved = Json.decodeFromJsonElement(
            VerifiableCredentialDigest.serializer(),
            client.readPayload(anchored.ref).payload
        )

        assertEquals(original, retrieved)
    }
}

