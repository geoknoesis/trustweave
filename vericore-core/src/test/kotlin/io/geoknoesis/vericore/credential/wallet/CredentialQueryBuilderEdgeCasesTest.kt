package io.geoknoesis.vericore.credential.wallet

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.time.Instant

/**
 * Additional edge case tests for CredentialQueryBuilder.
 */
class CredentialQueryBuilderEdgeCasesTest {

    @Test
    fun `test query builder with multiple types`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.byTypes("PersonCredential", "EducationCredential", "ProfessionalCredential")
        
        val predicate = builder.createPredicate()
        
        val cred1 = createTestCredential(type = listOf("VerifiableCredential", "PersonCredential"))
        val cred2 = createTestCredential(type = listOf("VerifiableCredential", "EducationCredential"))
        val cred3 = createTestCredential(type = listOf("VerifiableCredential", "OtherCredential"))
        
        assertTrue(predicate(cred1))
        assertTrue(predicate(cred2))
        assertFalse(predicate(cred3))
    }

    @Test
    fun `test query builder with expired credential`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.expired()
        
        val predicate = builder.createPredicate()
        
        val expiredCred = createTestCredential(
            expirationDate = Instant.now().minusSeconds(86400).toString() // Yesterday
        )
        val validCred = createTestCredential(
            expirationDate = Instant.now().plusSeconds(86400).toString() // Tomorrow
        )
        val noExpirationCred = createTestCredential(expirationDate = null)
        
        assertTrue(predicate(expiredCred))
        assertFalse(predicate(validCred))
        assertFalse(predicate(noExpirationCred))
    }

    @Test
    fun `test query builder with invalid expiration date format`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.notExpired()
        
        val predicate = builder.createPredicate()
        
        val invalidDateCred = createTestCredential(expirationDate = "invalid-date")
        
        // Should treat invalid dates as not expired (safe default)
        assertTrue(predicate(invalidDateCred))
    }

    @Test
    fun `test query builder valid filter requires proof`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.valid()
        
        val predicate = builder.createPredicate()
        
        val credWithProof = createTestCredential(
            proof = io.geoknoesis.vericore.credential.models.Proof(
                type = "Ed25519Signature2020",
                created = "2024-01-01T00:00:00Z",
                verificationMethod = "did:key:issuer#key-1",
                proofPurpose = "assertionMethod"
            )
        )
        val credWithoutProof = createTestCredential(proof = null)
        
        assertTrue(predicate(credWithProof))
        assertFalse(predicate(credWithoutProof))
    }

    @Test
    fun `test query builder complex query`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.byIssuer("did:key:issuer")
        builder.byType("PersonCredential")
        builder.notExpired()
        builder.notRevoked()
        builder.valid()
        
        val predicate = builder.createPredicate()
        
        val matchingCred = createTestCredential(
            issuer = "did:key:issuer",
            type = listOf("VerifiableCredential", "PersonCredential"),
            expirationDate = Instant.now().plusSeconds(86400).toString(),
            proof = io.geoknoesis.vericore.credential.models.Proof(
                type = "Ed25519Signature2020",
                created = "2024-01-01T00:00:00Z",
                verificationMethod = "did:key:issuer#key-1",
                proofPurpose = "assertionMethod"
            )
        )
        
        assertTrue(predicate(matchingCred))
    }

    @Test
    fun `test query builder toPredicate method`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.byIssuer("did:key:issuer")
        
        val predicate = builder.toPredicate()
        
        val cred = createTestCredential(issuer = "did:key:issuer")
        assertTrue(predicate(cred))
    }

    private fun createTestCredential(
        id: String = "cred-${System.currentTimeMillis()}",
        issuer: String = "did:key:issuer",
        type: List<String> = listOf("VerifiableCredential"),
        expirationDate: String? = null,
        proof: io.geoknoesis.vericore.credential.models.Proof? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = type,
            issuer = issuer,
            credentialSubject = buildJsonObject { put("id", "did:key:subject") },
            issuanceDate = Instant.now().toString(),
            expirationDate = expirationDate,
            proof = proof
        )
    }
}



