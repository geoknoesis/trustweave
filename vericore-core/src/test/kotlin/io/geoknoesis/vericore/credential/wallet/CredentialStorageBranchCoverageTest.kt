package io.geoknoesis.vericore.credential.wallet

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for CredentialStorage interface and CredentialQueryBuilder.
 */
class CredentialStorageBranchCoverageTest {

    @Test
    fun `test CredentialQueryBuilder byIssuer filter matches`() {
        val builder = CredentialQueryBuilder()
        builder.byIssuer("did:key:issuer1")
        
        val credential = createTestCredential(issuerDid = "did:key:issuer1")
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder byIssuer filter does not match`() {
        val builder = CredentialQueryBuilder()
        builder.byIssuer("did:key:issuer1")
        
        val credential = createTestCredential(issuerDid = "did:key:issuer2")
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder byType filter matches`() {
        val builder = CredentialQueryBuilder()
        builder.byType("PersonCredential")
        
        val credential = createTestCredential(types = listOf("VerifiableCredential", "PersonCredential"))
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder byType filter does not match`() {
        val builder = CredentialQueryBuilder()
        builder.byType("PersonCredential")
        
        val credential = createTestCredential(types = listOf("VerifiableCredential", "DegreeCredential"))
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder byTypes filter matches one`() {
        val builder = CredentialQueryBuilder()
        builder.byTypes("PersonCredential", "DegreeCredential")
        
        val credential = createTestCredential(types = listOf("VerifiableCredential", "PersonCredential"))
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder byTypes filter matches none`() {
        val builder = CredentialQueryBuilder()
        builder.byTypes("PersonCredential", "DegreeCredential")
        
        val credential = createTestCredential(types = listOf("VerifiableCredential", "OtherCredential"))
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder bySubject filter matches`() {
        val builder = CredentialQueryBuilder()
        builder.bySubject("did:key:subject1")
        
        val credential = createTestCredential(
            subject = buildJsonObject {
                put("id", "did:key:subject1")
                put("name", "John")
            }
        )
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder bySubject filter does not match`() {
        val builder = CredentialQueryBuilder()
        builder.bySubject("did:key:subject1")
        
        val credential = createTestCredential(
            subject = buildJsonObject {
                put("id", "did:key:subject2")
                put("name", "John")
            }
        )
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder bySubject filter with missing id`() {
        val builder = CredentialQueryBuilder()
        builder.bySubject("did:key:subject1")
        
        val credential = createTestCredential(
            subject = buildJsonObject {
                put("name", "John")
                // No "id" field
            }
        )
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder notExpired with valid expiration`() {
        val builder = CredentialQueryBuilder()
        builder.notExpired()
        
        val futureDate = java.time.Instant.now().plusSeconds(86400).toString()
        val credential = createTestCredential(expirationDate = futureDate)
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder notExpired with expired credential`() {
        val builder = CredentialQueryBuilder()
        builder.notExpired()
        
        val pastDate = java.time.Instant.now().minusSeconds(86400).toString()
        val credential = createTestCredential(expirationDate = pastDate)
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder notExpired with null expiration`() {
        val builder = CredentialQueryBuilder()
        builder.notExpired()
        
        val credential = createTestCredential(expirationDate = null)
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder notExpired with invalid date format`() {
        val builder = CredentialQueryBuilder()
        builder.notExpired()
        
        val credential = createTestCredential(expirationDate = "invalid-date")
        val predicate = builder.toPredicate()
        
        // Should return true for invalid dates (assumes not expired)
        assertTrue(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder expired with expired credential`() {
        val builder = CredentialQueryBuilder()
        builder.expired()
        
        val pastDate = java.time.Instant.now().minusSeconds(86400).toString()
        val credential = createTestCredential(expirationDate = pastDate)
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder expired with valid expiration`() {
        val builder = CredentialQueryBuilder()
        builder.expired()
        
        val futureDate = java.time.Instant.now().plusSeconds(86400).toString()
        val credential = createTestCredential(expirationDate = futureDate)
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder expired with null expiration`() {
        val builder = CredentialQueryBuilder()
        builder.expired()
        
        val credential = createTestCredential(expirationDate = null)
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder expired with invalid date format`() {
        val builder = CredentialQueryBuilder()
        builder.expired()
        
        val credential = createTestCredential(expirationDate = "invalid-date")
        val predicate = builder.toPredicate()
        
        // Should return false for invalid dates
        assertFalse(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder notRevoked with no status`() {
        val builder = CredentialQueryBuilder()
        builder.notRevoked()
        
        val credential = createTestCredential(credentialStatus = null)
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder notRevoked with status`() {
        val builder = CredentialQueryBuilder()
        builder.notRevoked()
        
        val credential = createTestCredential(
            credentialStatus = io.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "status-list-1",
                type = "StatusList2021Entry",
                statusListIndex = "0"
            )
        )
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder revoked with status`() {
        val builder = CredentialQueryBuilder()
        builder.revoked()
        
        val credential = createTestCredential(
            credentialStatus = io.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "status-list-1",
                type = "StatusList2021Entry",
                statusListIndex = "0"
            )
        )
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder revoked with no status`() {
        val builder = CredentialQueryBuilder()
        builder.revoked()
        
        val credential = createTestCredential(credentialStatus = null)
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder valid with all conditions met`() {
        val builder = CredentialQueryBuilder()
        builder.valid()
        
        val futureDate = java.time.Instant.now().plusSeconds(86400).toString()
        val credential = createTestCredential(
            expirationDate = futureDate,
            credentialStatus = null,
            proof = io.geoknoesis.vericore.credential.models.Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:issuer#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "test-proof"
            )
        )
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder valid without proof`() {
        val builder = CredentialQueryBuilder()
        builder.valid()
        
        val futureDate = java.time.Instant.now().plusSeconds(86400).toString()
        val credential = createTestCredential(
            expirationDate = futureDate,
            credentialStatus = null,
            proof = null
        )
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder valid with expired credential`() {
        val builder = CredentialQueryBuilder()
        builder.valid()
        
        val pastDate = java.time.Instant.now().minusSeconds(86400).toString()
        val credential = createTestCredential(
            expirationDate = pastDate,
            credentialStatus = null,
            proof = io.geoknoesis.vericore.credential.models.Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:issuer#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "test-proof"
            )
        )
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder valid with revoked credential`() {
        val builder = CredentialQueryBuilder()
        builder.valid()
        
        val futureDate = java.time.Instant.now().plusSeconds(86400).toString()
        val credential = createTestCredential(
            expirationDate = futureDate,
            credentialStatus = io.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "status-list-1",
                type = "StatusList2021Entry",
                statusListIndex = "0"
            ),
            proof = io.geoknoesis.vericore.credential.models.Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:issuer#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "test-proof"
            )
        )
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder valid with null expiration`() {
        val builder = CredentialQueryBuilder()
        builder.valid()
        
        val credential = createTestCredential(
            expirationDate = null,
            credentialStatus = null,
            proof = io.geoknoesis.vericore.credential.models.Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:issuer#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "test-proof"
            )
        )
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder valid with invalid expiration date`() {
        val builder = CredentialQueryBuilder()
        builder.valid()
        
        val credential = createTestCredential(
            expirationDate = "invalid-date",
            credentialStatus = null,
            proof = io.geoknoesis.vericore.credential.models.Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:issuer#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "test-proof"
            )
        )
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder multiple filters all match`() {
        val builder = CredentialQueryBuilder()
        builder.byIssuer("did:key:issuer1")
        builder.byType("PersonCredential")
        builder.notExpired()
        
        val futureDate = java.time.Instant.now().plusSeconds(86400).toString()
        val credential = createTestCredential(
            issuerDid = "did:key:issuer1",
            types = listOf("VerifiableCredential", "PersonCredential"),
            expirationDate = futureDate
        )
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder multiple filters one fails`() {
        val builder = CredentialQueryBuilder()
        builder.byIssuer("did:key:issuer1")
        builder.byType("PersonCredential")
        builder.notExpired()
        
        val futureDate = java.time.Instant.now().plusSeconds(86400).toString()
        val credential = createTestCredential(
            issuerDid = "did:key:issuer1",
            types = listOf("VerifiableCredential", "OtherCredential"), // Wrong type
            expirationDate = futureDate
        )
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder empty filters matches all`() {
        val builder = CredentialQueryBuilder()
        
        val credential = createTestCredential()
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential))
    }

    @Test
    fun `test CredentialQueryBuilder createPredicate same as toPredicate`() {
        val builder = CredentialQueryBuilder()
        builder.byIssuer("did:key:issuer1")
        
        val credential = createTestCredential(issuerDid = "did:key:issuer1")
        val predicate1 = builder.toPredicate()
        val predicate2 = builder.createPredicate()
        
        assertEquals(predicate1(credential), predicate2(credential))
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
        credentialStatus: io.geoknoesis.vericore.credential.models.CredentialStatus? = null,
        proof: io.geoknoesis.vericore.credential.models.Proof? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            expirationDate = expirationDate,
            credentialStatus = credentialStatus,
            proof = proof
        )
    }
}

