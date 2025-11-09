package io.geoknoesis.vericore.credential.wallet

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.serialization.json.*
import kotlin.test.*

/**
 * Additional edge case tests for CredentialQueryBuilder.
 */
class CredentialQueryBuilderPredicateTest {

    @Test
    fun `test toPredicate method`() {
        val builder = CredentialQueryBuilder().apply {
            byIssuer("did:example:issuer")
            byType("TestCredential")
        }

        val predicate = builder.toPredicate()

        val matchingCredential = createTestCredential(
            id = "cred-1",
            issuer = "did:example:issuer",
            types = listOf("VerifiableCredential", "TestCredential")
        )
        val nonMatchingCredential = createTestCredential(
            id = "cred-2",
            issuer = "did:example:other",
            types = listOf("VerifiableCredential", "TestCredential")
        )

        assertTrue(predicate(matchingCredential))
        assertFalse(predicate(nonMatchingCredential))
    }

    @Test
    fun `test createPredicate method`() {
        val builder = CredentialQueryBuilder().apply {
            byIssuer("did:example:issuer")
            notExpired()
        }

        val predicate = builder.createPredicate()

        val futureDate = java.time.Instant.now().plusSeconds(86400).toString()
        val matchingCredential = createTestCredential(
            id = "cred-1",
            issuer = "did:example:issuer",
            expirationDate = futureDate
        )
        val nonMatchingCredential = createTestCredential(
            id = "cred-2",
            issuer = "did:example:other",
            expirationDate = futureDate
        )

        assertTrue(predicate(matchingCredential))
        assertFalse(predicate(nonMatchingCredential))
    }

    @Test
    fun `test empty query builder predicate`() {
        val builder = CredentialQueryBuilder()
        val predicate = builder.toPredicate()

        val credential = createTestCredential()
        // Empty predicate should match all credentials
        assertTrue(predicate(credential))
    }

    @Test
    fun `test predicate with multiple filters`() {
        val builder = CredentialQueryBuilder().apply {
            byIssuer("did:example:issuer")
            byType("TestCredential")
            bySubject("did:example:subject")
            notExpired()
            notRevoked()
        }

        val predicate = builder.toPredicate()

        val futureDate = java.time.Instant.now().plusSeconds(86400).toString()
        val matchingCredential = createTestCredential(
            id = "cred-1",
            issuer = "did:example:issuer",
            types = listOf("VerifiableCredential", "TestCredential"),
            subjectId = "did:example:subject",
            expirationDate = futureDate,
            revoked = false
        )

        assertTrue(predicate(matchingCredential))
    }

    @Test
    fun `test predicate with expired filter`() {
        val builder = CredentialQueryBuilder().apply {
            expired()
        }

        val predicate = builder.toPredicate()

        val pastDate = java.time.Instant.now().minusSeconds(86400).toString()
        val expiredCredential = createTestCredential(
            id = "cred-1",
            expirationDate = pastDate
        )
        val futureDate = java.time.Instant.now().plusSeconds(86400).toString()
        val validCredential = createTestCredential(
            id = "cred-2",
            expirationDate = futureDate
        )

        assertTrue(predicate(expiredCredential))
        assertFalse(predicate(validCredential))
    }

    @Test
    fun `test predicate with revoked filter`() {
        val builder = CredentialQueryBuilder().apply {
            revoked()
        }

        val predicate = builder.toPredicate()

        val revokedCredential = createTestCredential(
            id = "cred-1",
            revoked = true
        )
        val validCredential = createTestCredential(
            id = "cred-2",
            revoked = false
        )

        assertTrue(predicate(revokedCredential))
        assertFalse(predicate(validCredential))
    }

    @Test
    fun `test predicate with valid filter`() {
        val builder = CredentialQueryBuilder().apply {
            valid()
        }

        val predicate = builder.toPredicate()

        val futureDate = java.time.Instant.now().plusSeconds(86400).toString()
        val validCredential = createTestCredential(
            id = "cred-1",
            expirationDate = futureDate,
            revoked = false,
            hasProof = true
        )
        val invalidCredential = createTestCredential(
            id = "cred-2",
            expirationDate = futureDate,
            revoked = false,
            hasProof = false
        )

        assertTrue(predicate(validCredential))
        assertFalse(predicate(invalidCredential))
    }

    @Test
    fun `test predicate with invalid expiration date`() {
        val builder = CredentialQueryBuilder().apply {
            notExpired()
        }

        val predicate = builder.toPredicate()

        // Credential with invalid expiration date format
        val credentialWithInvalidDate = VerifiableCredential(
            id = "cred-1",
            type = listOf("VerifiableCredential"),
            issuer = "did:example:issuer",
            issuanceDate = java.time.Instant.now().toString(),
            expirationDate = "invalid-date-format",
            credentialSubject = buildJsonObject {
                put("id", "did:example:subject")
            }
        )

        // Should treat invalid date as not expired (defaults to true)
        assertTrue(predicate(credentialWithInvalidDate))
    }

    @Test
    fun `test predicate with byTypes filter`() {
        val builder = CredentialQueryBuilder().apply {
            byTypes("TypeA", "TypeB")
        }

        val predicate = builder.toPredicate()

        val credentialWithTypeA = createTestCredential(
            id = "cred-1",
            types = listOf("VerifiableCredential", "TypeA")
        )
        val credentialWithTypeB = createTestCredential(
            id = "cred-2",
            types = listOf("VerifiableCredential", "TypeB")
        )
        val credentialWithTypeC = createTestCredential(
            id = "cred-3",
            types = listOf("VerifiableCredential", "TypeC")
        )

        assertTrue(predicate(credentialWithTypeA))
        assertTrue(predicate(credentialWithTypeB))
        assertFalse(predicate(credentialWithTypeC))
    }

    private fun createTestCredential(
        id: String = "cred-${System.currentTimeMillis()}",
        issuer: String = "did:example:issuer",
        types: List<String> = listOf("VerifiableCredential", "TestCredential"),
        subjectId: String = "did:example:subject",
        expirationDate: String? = null,
        revoked: Boolean = false,
        hasProof: Boolean = true
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuer,
            issuanceDate = java.time.Instant.now().toString(),
            expirationDate = expirationDate,
            credentialSubject = buildJsonObject {
                put("id", subjectId)
                put("name", "Test Subject")
            },
            credentialStatus = if (revoked) {
                io.geoknoesis.vericore.credential.models.CredentialStatus(
                    id = "https://example.com/status/1",
                    type = "StatusList2021Entry",
                    statusListIndex = "1"
                )
            } else null,
            proof = if (hasProof) {
                io.geoknoesis.vericore.credential.models.Proof(
                    type = "Ed25519Signature2020",
                    created = java.time.Instant.now().toString(),
                    verificationMethod = "did:example:issuer#key-1",
                    proofPurpose = "assertionMethod",
                    proofValue = "zSignature"
                )
            } else null
        )
    }
}


