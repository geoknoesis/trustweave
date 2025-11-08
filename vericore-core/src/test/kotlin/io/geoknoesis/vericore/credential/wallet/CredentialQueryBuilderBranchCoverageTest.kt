package io.geoknoesis.vericore.credential.wallet

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for CredentialQueryBuilder.
 * Tests all conditional branches in filter methods.
 */
class CredentialQueryBuilderBranchCoverageTest {

    // ========== notExpired() Branch Coverage ==========

    @Test
    fun `test branch notExpired with null expiration date`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.notExpired()
        
        val credential = createTestCredential(expirationDate = null)
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential)) // null expiration means not expired
    }

    @Test
    fun `test branch notExpired with valid future expiration`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.notExpired()
        
        val credential = createTestCredential(
            expirationDate = java.time.Instant.now().plusSeconds(86400).toString()
        )
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential))
    }

    @Test
    fun `test branch notExpired with past expiration`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.notExpired()
        
        val credential = createTestCredential(
            expirationDate = java.time.Instant.now().minusSeconds(86400).toString()
        )
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test branch notExpired with invalid date format`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.notExpired()
        
        val credential = createTestCredential(expirationDate = "invalid-date")
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential)) // Invalid format defaults to true
    }

    @Test
    fun `test branch notExpired with exactly current time`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.notExpired()
        
        val now = java.time.Instant.now()
        val credential = createTestCredential(expirationDate = now.toString())
        val predicate = builder.toPredicate()
        
        // Should be false (now is not before expiration)
        assertFalse(predicate(credential))
    }

    // ========== expired() Branch Coverage ==========

    @Test
    fun `test branch expired with null expiration date`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.expired()
        
        val credential = createTestCredential(expirationDate = null)
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential)) // null expiration means not expired
    }

    @Test
    fun `test branch expired with valid future expiration`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.expired()
        
        val credential = createTestCredential(
            expirationDate = java.time.Instant.now().plusSeconds(86400).toString()
        )
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test branch expired with past expiration`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.expired()
        
        val credential = createTestCredential(
            expirationDate = java.time.Instant.now().minusSeconds(86400).toString()
        )
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential))
    }

    @Test
    fun `test branch expired with invalid date format`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.expired()
        
        val credential = createTestCredential(expirationDate = "invalid-date")
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential)) // Invalid format defaults to false
    }

    // ========== valid() Branch Coverage ==========

    @Test
    fun `test branch valid with all conditions met`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.valid()
        
        val credential = createTestCredential(
            expirationDate = java.time.Instant.now().plusSeconds(86400).toString(),
            credentialStatus = null
        )
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential))
    }

    @Test
    fun `test branch valid with null proof`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.valid()
        
        val credential = createTestCredential(proof = null)
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test branch valid with expired credential`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.valid()
        
        val credential = createTestCredential(
            expirationDate = java.time.Instant.now().minusSeconds(86400).toString(),
            credentialStatus = null
        )
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test branch valid with revoked credential`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.valid()
        
        val credential = createTestCredential(
            credentialStatus = io.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry"
            )
        )
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test branch valid with null expiration date`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.valid()
        
        val credential = createTestCredential(
            expirationDate = null,
            credentialStatus = null
        )
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential)) // null expiration means valid
    }

    @Test
    fun `test branch valid with invalid expiration date format`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.valid()
        
        val credential = createTestCredential(
            expirationDate = "invalid-date",
            credentialStatus = null
        )
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential)) // Invalid format defaults to false in valid()
    }

    // ========== bySubject() Branch Coverage ==========

    @Test
    fun `test branch bySubject with matching subject ID`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.bySubject("did:key:subject")
        
        val credential = createTestCredential(
            subject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", "John Doe")
            }
        )
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential))
    }

    @Test
    fun `test branch bySubject with non-matching subject ID`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.bySubject("did:key:different")
        
        val credential = createTestCredential(
            subject = buildJsonObject {
                put("id", "did:key:subject")
            }
        )
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test branch bySubject with missing id field`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.bySubject("did:key:subject")
        
        val credential = createTestCredential(
            subject = buildJsonObject {
                put("name", "John Doe")
                // No id field
            }
        )
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test branch bySubject with id as non-primitive`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.bySubject("did:key:subject")
        
        val credential = createTestCredential(
            subject = buildJsonObject {
                put("id", buildJsonObject { put("value", "did:key:subject") })
            }
        )
        val predicate = builder.toPredicate()
        
        // When id is not a primitive (JsonObject), calling jsonPrimitive throws IllegalArgumentException
        // This tests the error handling branch in the filter
        try {
            val result = predicate(credential)
            // If no exception, the result should be false (null == string is false)
            assertFalse(result)
        } catch (e: IllegalArgumentException) {
            // Exception is expected when jsonPrimitive is called on JsonObject
            assertTrue(e.message?.contains("JsonPrimitive") == true || e.message?.contains("not a") == true)
        }
    }

    // ========== byTypes() Branch Coverage ==========

    @Test
    fun `test branch byTypes with matching type`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.byTypes("PersonCredential", "EmailCredential")
        
        val credential = createTestCredential(
            types = listOf("VerifiableCredential", "PersonCredential")
        )
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential))
    }

    @Test
    fun `test branch byTypes with no matching type`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.byTypes("EmailCredential", "PhoneCredential")
        
        val credential = createTestCredential(
            types = listOf("VerifiableCredential", "PersonCredential")
        )
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test branch byTypes with empty types list`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.byTypes("PersonCredential")
        
        val credential = createTestCredential(
            types = emptyList()
        )
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    // ========== Combined Filter Branches ==========

    @Test
    fun `test branch multiple filters all pass`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.byIssuer("did:key:issuer")
        builder.byType("PersonCredential")
        builder.notExpired()
        builder.notRevoked()
        
        val credential = createTestCredential(
            expirationDate = java.time.Instant.now().plusSeconds(86400).toString()
        )
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential))
    }

    @Test
    fun `test branch multiple filters one fails`() = runBlocking {
        val builder = CredentialQueryBuilder()
        builder.byIssuer("did:key:issuer")
        builder.byType("PersonCredential")
        builder.notExpired()
        
        val credential = createTestCredential(
            expirationDate = java.time.Instant.now().minusSeconds(86400).toString()
        )
        val predicate = builder.toPredicate()
        
        assertFalse(predicate(credential))
    }

    @Test
    fun `test branch empty filters list`() = runBlocking {
        val builder = CredentialQueryBuilder()
        
        val credential = createTestCredential()
        val predicate = builder.toPredicate()
        
        assertTrue(predicate(credential)) // Empty filters means all pass
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
        credentialStatus: io.geoknoesis.vericore.credential.models.CredentialStatus? = null
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

