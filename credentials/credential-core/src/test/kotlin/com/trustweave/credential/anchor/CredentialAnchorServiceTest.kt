package com.trustweave.credential.anchor

import com.trustweave.credential.model.vc.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock

/**
 * Comprehensive tests for CredentialAnchorService API.
 */
class CredentialAnchorServiceTest {

    private val anchorClient = Any() // Mock anchor client
    private val service = CredentialAnchorService(anchorClient)

    @Test
    fun `test anchor credential with proof`() = runBlocking {
        val credential = createTestCredential(
            proof = com.trustweave.credential.models.Proof(
                type = "Ed25519Signature2020",
                created = Clock.System.now().toString(),
                verificationMethod = "did:key:issuer#key-1",
                proofPurpose = "assertionMethod"
            )
        )

        val result = service.anchorCredential(
            credential = credential,
            chainId = "algorand:testnet",
            options = AnchorOptions(includeProof = true)
        )

        assertNotNull(result)
        assertEquals(credential, result.credential)
        assertNotNull(result.digest)
        assertNotNull(result.anchorRef)
    }

    @Test
    fun `test anchor credential without proof`() = runBlocking {
        val credential = createTestCredential()

        val result = service.anchorCredential(
            credential = credential,
            chainId = "algorand:testnet",
            options = AnchorOptions(includeProof = false)
        )

        assertNotNull(result)
        assertEquals(credential, result.credential)
    }

    @Test
    fun `test verify anchored credential with evidence`() = runBlocking {
        val credential = createTestCredential(
            evidence = listOf(
                com.trustweave.credential.models.Evidence(
                    id = "evidence-1",
                    type = listOf("BlockchainAnchorEvidence"),
                    evidenceDocument = buildJsonObject {
                        put("chainId", "algorand:testnet")
                        put("txHash", "abc123")
                    }
                )
            )
        )

        val verified = service.verifyAnchoredCredential(credential, "algorand:testnet")

        assertTrue(verified)
    }

    @Test
    fun `test verify anchored credential without evidence`() = runBlocking {
        val credential = createTestCredential()

        val verified = service.verifyAnchoredCredential(credential, "algorand:testnet")

        assertFalse(verified)
    }

    @Test
    fun `test verify anchored credential with wrong chain ID`() = runBlocking {
        val credential = createTestCredential(
            evidence = listOf(
                com.trustweave.credential.models.Evidence(
                    id = "evidence-1",
                    type = listOf("BlockchainAnchorEvidence"),
                    evidenceDocument = buildJsonObject {
                        put("chainId", "algorand:testnet")
                    }
                )
            )
        )

        val verified = service.verifyAnchoredCredential(credential, "eip155:1")

        assertFalse(verified)
    }

    @Test
    fun `test get anchor reference with evidence`() = runBlocking {
        val credential = createTestCredential(
            evidence = listOf(
                com.trustweave.credential.models.Evidence(
                    id = "evidence-1",
                    type = listOf("BlockchainAnchorEvidence"),
                    evidenceDocument = buildJsonObject {
                        put("chainId", "algorand:testnet")
                        put("txHash", "abc123")
                    }
                )
            )
        )

        val ref = service.getAnchorReference(credential, "algorand:testnet")

        // Placeholder implementation returns null
        // This test verifies the method executes without error
        assertNotNull(ref) // Will be null in placeholder, but method should execute
    }

    @Test
    fun `test get anchor reference without evidence`() = runBlocking {
        val credential = createTestCredential()

        val ref = service.getAnchorReference(credential, "algorand:testnet")

        assertNull(ref)
    }

    private fun createTestCredential(
        id: String? = "https://example.com/credentials/1",
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        issuerDid: String = "did:key:issuer",
        subject: JsonObject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        },
        issuanceDate: String = Clock.System.now().toString(),
        proof: com.trustweave.credential.models.Proof? = null,
        evidence: List<com.trustweave.credential.models.Evidence>? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            proof = proof,
            evidence = evidence
        )
    }
}

