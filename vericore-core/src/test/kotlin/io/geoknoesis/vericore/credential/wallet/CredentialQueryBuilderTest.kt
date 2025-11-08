package io.geoknoesis.vericore.credential.wallet

import io.geoknoesis.vericore.credential.models.CredentialStatus
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for CredentialQueryBuilder API.
 */
class CredentialQueryBuilderTest {

    @Test
    fun `test query by issuer`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.byIssuer("did:key:issuer1")
        
        val predicate = builder.toPredicate()
        
        val credential1 = createTestCredential(issuerDid = "did:key:issuer1")
        val credential2 = createTestCredential(issuerDid = "did:key:issuer2")
        
        assertTrue(predicate(credential1))
        assertFalse(predicate(credential2))
    }

    @Test
    fun `test query by type`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.byType("PersonCredential")
        
        val predicate = builder.toPredicate()
        
        val credential1 = createTestCredential(types = listOf("VerifiableCredential", "PersonCredential"))
        val credential2 = createTestCredential(types = listOf("VerifiableCredential", "DegreeCredential"))
        
        assertTrue(predicate(credential1))
        assertFalse(predicate(credential2))
    }

    @Test
    fun `test query by multiple types`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.byTypes("PersonCredential", "DegreeCredential")
        
        val predicate = builder.toPredicate()
        
        val credential1 = createTestCredential(types = listOf("VerifiableCredential", "PersonCredential"))
        val credential2 = createTestCredential(types = listOf("VerifiableCredential", "DegreeCredential"))
        val credential3 = createTestCredential(types = listOf("VerifiableCredential", "OtherCredential"))
        
        assertTrue(predicate(credential1))
        assertTrue(predicate(credential2))
        assertFalse(predicate(credential3))
    }

    @Test
    fun `test query by subject`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.bySubject("did:key:subject1")
        
        val predicate = builder.toPredicate()
        
        val credential1 = createTestCredential(
            subject = buildJsonObject { put("id", "did:key:subject1") }
        )
        val credential2 = createTestCredential(
            subject = buildJsonObject { put("id", "did:key:subject2") }
        )
        
        assertTrue(predicate(credential1))
        assertFalse(predicate(credential2))
    }

    @Test
    fun `test query not expired`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.notExpired()
        
        val predicate = builder.toPredicate()
        
        val futureDate = java.time.Instant.now().plusSeconds(86400).toString()
        val pastDate = java.time.Instant.now().minusSeconds(86400).toString()
        
        val credential1 = createTestCredential(expirationDate = futureDate)
        val credential2 = createTestCredential(expirationDate = pastDate)
        val credential3 = createTestCredential(expirationDate = null)
        
        assertTrue(predicate(credential1))
        assertFalse(predicate(credential2))
        assertTrue(predicate(credential3)) // No expiration means not expired
    }

    @Test
    fun `test query expired`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.expired()
        
        val predicate = builder.toPredicate()
        
        val futureDate = java.time.Instant.now().plusSeconds(86400).toString()
        val pastDate = java.time.Instant.now().minusSeconds(86400).toString()
        
        val credential1 = createTestCredential(expirationDate = futureDate)
        val credential2 = createTestCredential(expirationDate = pastDate)
        val credential3 = createTestCredential(expirationDate = null)
        
        assertFalse(predicate(credential1))
        assertTrue(predicate(credential2))
        assertFalse(predicate(credential3)) // No expiration means not expired
    }

    @Test
    fun `test query not expired with invalid date format`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.notExpired()
        
        val predicate = builder.toPredicate()
        
        val credential = createTestCredential(expirationDate = "invalid-date")
        
        assertTrue(predicate(credential)) // Should default to true for invalid format
    }

    @Test
    fun `test query not revoked`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.notRevoked()
        
        val predicate = builder.toPredicate()
        
        val credential1 = createTestCredential(credentialStatus = null)
        val credential2 = createTestCredential(
            credentialStatus = CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry"
            )
        )
        
        assertTrue(predicate(credential1))
        assertFalse(predicate(credential2)) // Has status means revoked (for now)
    }

    @Test
    fun `test query revoked`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.revoked()
        
        val predicate = builder.toPredicate()
        
        val credential1 = createTestCredential(credentialStatus = null)
        val credential2 = createTestCredential(
            credentialStatus = CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry"
            )
        )
        
        assertFalse(predicate(credential1))
        assertTrue(predicate(credential2))
    }

    @Test
    fun `test query valid credentials`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.valid()
        
        val predicate = builder.toPredicate()
        
        val futureDate = java.time.Instant.now().plusSeconds(86400).toString()
        val pastDate = java.time.Instant.now().minusSeconds(86400).toString()
        
        val validCredential = createTestCredential(
            expirationDate = futureDate,
            proof = io.geoknoesis.vericore.credential.models.Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:issuer#key-1",
                proofPurpose = "assertionMethod"
            )
        )
        val expiredCredential = createTestCredential(expirationDate = pastDate)
        val revokedCredential = createTestCredential(
            credentialStatus = CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry"
            )
        )
        val noProofCredential = createTestCredential(proof = null)
        
        assertTrue(predicate(validCredential))
        assertFalse(predicate(expiredCredential))
        assertFalse(predicate(revokedCredential))
        assertFalse(predicate(noProofCredential))
    }

    @Test
    fun `test query with multiple filters`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.byIssuer("did:key:issuer1")
        builder.byType("PersonCredential")
        builder.notExpired()
        
        val predicate = builder.toPredicate()
        
        val futureDate = java.time.Instant.now().plusSeconds(86400).toString()
        
        val matchingCredential = createTestCredential(
            issuerDid = "did:key:issuer1",
            types = listOf("VerifiableCredential", "PersonCredential"),
            expirationDate = futureDate
        )
        val wrongIssuer = createTestCredential(
            issuerDid = "did:key:issuer2",
            types = listOf("VerifiableCredential", "PersonCredential"),
            expirationDate = futureDate
        )
        val wrongType = createTestCredential(
            issuerDid = "did:key:issuer1",
            types = listOf("VerifiableCredential", "DegreeCredential"),
            expirationDate = futureDate
        )
        val expired = createTestCredential(
            issuerDid = "did:key:issuer1",
            types = listOf("VerifiableCredential", "PersonCredential"),
            expirationDate = java.time.Instant.now().minusSeconds(86400).toString()
        )
        
        assertTrue(predicate(matchingCredential))
        assertFalse(predicate(wrongIssuer))
        assertFalse(predicate(wrongType))
        assertFalse(predicate(expired))
    }

    @Test
    fun `test createPredicate method`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.byIssuer("did:key:issuer1")
        
        val predicate1 = builder.toPredicate()
        val predicate2 = builder.createPredicate()
        
        val credential = createTestCredential(issuerDid = "did:key:issuer1")
        
        assertTrue(predicate1(credential))
        assertTrue(predicate2(credential))
    }

    @Test
    fun `test empty query matches all`() = runBlocking {
        val builder = CredentialQueryBuilder()
        val predicate = builder.toPredicate()
        
        val credential1 = createTestCredential()
        val credential2 = createTestCredential(issuerDid = "did:key:other")
        
        assertTrue(predicate(credential1))
        assertTrue(predicate(credential2))
    }

    private fun createTestCredential(
        id: String? = "https://example.com/credentials/1",
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        issuerDid: String = "did:key:issuer",
        subject: JsonObject = buildJsonObject {
            put("id", "did:key:subject")
                put("name", "John Doe")
        },
        issuanceDate: String = java.time.Instant.now().toString(),
        expirationDate: String? = null,
        proof: io.geoknoesis.vericore.credential.models.Proof? = io.geoknoesis.vericore.credential.models.Proof(
            type = "Ed25519Signature2020",
            created = java.time.Instant.now().toString(),
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "assertionMethod"
        ),
        credentialStatus: CredentialStatus? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            expirationDate = expirationDate,
            proof = proof,
            credentialStatus = credentialStatus
        )
    }
}

