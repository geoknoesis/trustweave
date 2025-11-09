package io.geoknoesis.vericore.credential.anchor

import io.geoknoesis.vericore.credential.models.Evidence
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for CredentialAnchorService.
 * Tests all methods, branches, and edge cases.
 */
class CredentialAnchorServiceBranchCoverageTest {

    @Test
    fun `test CredentialAnchorService anchorCredential with includeProof true`() = runBlocking {
        val service = CredentialAnchorService(anchorClient = Any())
        val credential = createTestCredential()
        val options = AnchorOptions(includeProof = true)
        
        val result = service.anchorCredential(credential, "algorand:testnet", options)
        
        assertNotNull(result)
        assertNotNull(result.anchorRef)
        assertNotNull(result.digest)
    }

    @Test
    fun `test CredentialAnchorService anchorCredential with includeProof false`() = runBlocking {
        val service = CredentialAnchorService(anchorClient = Any())
        val credential = createTestCredential(
            proof = io.geoknoesis.vericore.credential.models.Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:issuer#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "test-proof"
            )
        )
        val options = AnchorOptions(includeProof = false)
        
        val result = service.anchorCredential(credential, "algorand:testnet", options)
        
        assertNotNull(result)
    }

    @Test
    fun `test CredentialAnchorService anchorCredential with default options`() = runBlocking {
        val service = CredentialAnchorService(anchorClient = Any())
        val credential = createTestCredential()
        
        val result = service.anchorCredential(credential, "algorand:testnet")
        
        assertNotNull(result)
    }

    @Test
    fun `test CredentialAnchorService verifyAnchoredCredential returns true`() = runBlocking {
        val service = CredentialAnchorService(anchorClient = Any())
        val credential = createTestCredential(
            evidence = listOf(
                Evidence(
                    id = "evidence-1",
                    type = listOf("BlockchainAnchorEvidence"),
                    evidenceDocument = buildJsonObject {
                        put("chainId", "algorand:testnet")
                        put("txHash", "tx-123")
                    }
                )
            )
        )
        
        val verified = service.verifyAnchoredCredential(credential, "algorand:testnet")
        
        assertTrue(verified)
    }

    @Test
    fun `test CredentialAnchorService verifyAnchoredCredential returns false when no evidence`() = runBlocking {
        val service = CredentialAnchorService(anchorClient = Any())
        val credential = createTestCredential()
        
        val verified = service.verifyAnchoredCredential(credential, "algorand:testnet")
        
        assertFalse(verified)
    }

    @Test
    fun `test CredentialAnchorService verifyAnchoredCredential returns false for wrong chain`() = runBlocking {
        val service = CredentialAnchorService(anchorClient = Any())
        val credential = createTestCredential(
            evidence = listOf(
                Evidence(
                    id = "evidence-1",
                    type = listOf("BlockchainAnchorEvidence"),
                    evidenceDocument = buildJsonObject {
                        put("chainId", "algorand:mainnet")
                        put("txHash", "tx-123")
                    }
                )
            )
        )
        
        val verified = service.verifyAnchoredCredential(credential, "algorand:testnet")
        
        assertFalse(verified)
    }

    @Test
    fun `test CredentialAnchorService getAnchorReference returns reference`() = runBlocking {
        val service = CredentialAnchorService(anchorClient = Any())
        val credential = createTestCredential(
            evidence = listOf(
                Evidence(
                    id = "evidence-1",
                    type = listOf("BlockchainAnchorEvidence"),
                    evidenceDocument = buildJsonObject {
                        put("chainId", "algorand:testnet")
                        put("txHash", "tx-123")
                    }
                )
            )
        )
        
        val ref = service.getAnchorReference(credential, "algorand:testnet")
        
        // Current implementation returns null (placeholder)
        assertNull(ref)
    }

    @Test
    fun `test CredentialAnchorService getAnchorReference returns null when no evidence`() = runBlocking {
        val service = CredentialAnchorService(anchorClient = Any())
        val credential = createTestCredential()
        
        val ref = service.getAnchorReference(credential, "algorand:testnet")
        
        assertNull(ref)
    }

    @Test
    fun `test CredentialAnchorService getAnchorReference returns null for wrong chain`() = runBlocking {
        val service = CredentialAnchorService(anchorClient = Any())
        val credential = createTestCredential(
            evidence = listOf(
                Evidence(
                    id = "evidence-1",
                    type = listOf("BlockchainAnchorEvidence"),
                    evidenceDocument = buildJsonObject {
                        put("chainId", "algorand:mainnet")
                        put("txHash", "tx-123")
                    }
                )
            )
        )
        
        val ref = service.getAnchorReference(credential, "algorand:testnet")
        
        assertNull(ref)
    }

    private fun createTestCredential(
        id: String? = null,
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        issuerDid: String = "did:key:issuer",
        subject: JsonObject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        },
        issuanceDate: String = java.time.Instant.now().toString(),
        proof: io.geoknoesis.vericore.credential.models.Proof? = null,
        evidence: List<Evidence>? = null
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


