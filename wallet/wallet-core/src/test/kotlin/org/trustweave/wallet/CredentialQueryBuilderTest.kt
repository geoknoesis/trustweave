package org.trustweave.wallet

import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.vc.CredentialStatus
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class CredentialQueryBuilderTest {

    private val issuerDid = "did:key:z6MkTestIssuer"
    private val subjectDid = "did:key:z6MkTestSubject"

    private fun credential(
        issuer: String = issuerDid,
        subject: String = subjectDid,
        type: String = "VerifiableCredential",
        expired: Boolean = false,
        revoked: Boolean = false
    ): VerifiableCredential {
        val now = Clock.System.now()
        val expiration: Instant? = when {
            expired -> now - 1.days
            else -> null
        }
        return VerifiableCredential(
            id = CredentialId("vc:test:${System.nanoTime()}"),
            type = listOf(CredentialType.Custom(type)),
            issuer = Issuer.fromDid(Did(issuer)),
            credentialSubject = CredentialSubject.fromIri(subject),
            issuanceDate = now - 1.days,
            expirationDate = expiration,
            credentialStatus = if (revoked) CredentialStatus(
                id = StatusListId("https://example.com/status/1"),
                type = "StatusList2021Entry"
            ) else null,
            proof = null
        )
    }

    @Test
    fun `empty query matches all credentials`() {
        val builder = CredentialQueryBuilder()
        val predicate = builder.toPredicate()

        assertTrue(predicate(credential()))
        assertTrue(predicate(credential(expired = true)))
    }

    @Test
    fun `byIssuer filters by issuer DID`() {
        val predicate = CredentialQueryBuilder().apply {
            byIssuer(issuerDid)
        }.toPredicate()

        assertTrue(predicate(credential(issuer = issuerDid)))
        assertFalse(predicate(credential(issuer = "did:key:z6MkOtherIssuer")))
    }

    @Test
    fun `byType filters by credential type`() {
        val predicate = CredentialQueryBuilder().apply {
            byType("EducationCredential")
        }.toPredicate()

        assertTrue(predicate(credential(type = "EducationCredential")))
        assertFalse(predicate(credential(type = "EmploymentCredential")))
    }

    @Test
    fun `notExpired excludes expired credentials`() {
        val predicate = CredentialQueryBuilder().apply {
            notExpired()
        }.toPredicate()

        assertTrue(predicate(credential(expired = false)))
        assertFalse(predicate(credential(expired = true)))
    }

    @Test
    fun `expired includes only expired credentials`() {
        val predicate = CredentialQueryBuilder().apply {
            expired()
        }.toPredicate()

        assertFalse(predicate(credential(expired = false)))
        assertTrue(predicate(credential(expired = true)))
    }

    @Test
    fun `notRevoked excludes credentials with credentialStatus`() {
        val predicate = CredentialQueryBuilder().apply {
            notRevoked()
        }.toPredicate()

        assertTrue(predicate(credential(revoked = false)))
        assertFalse(predicate(credential(revoked = true)))
    }

    @Test
    fun `combined predicates are ANDed`() {
        val predicate = CredentialQueryBuilder().apply {
            byIssuer(issuerDid)
            byType("VerifiableCredential")
            notExpired()
        }.toPredicate()

        assertTrue(predicate(credential(issuer = issuerDid, type = "VerifiableCredential", expired = false)))
        assertFalse(predicate(credential(issuer = issuerDid, type = "VerifiableCredential", expired = true)))
        assertFalse(predicate(credential(issuer = "did:key:z6MkOther", type = "VerifiableCredential", expired = false)))
    }

    @Test
    fun `byTag adds to requestedTags`() {
        val builder = CredentialQueryBuilder().apply {
            byTag("important")
            byTag("verified")
        }
        assertTrue("important" in builder.requestedTags)
        assertTrue("verified" in builder.requestedTags)
    }

    @Test
    fun `byCollection adds to requestedCollections`() {
        val builder = CredentialQueryBuilder().apply {
            byCollection("col-1")
            byCollection("col-2")
        }
        assertTrue("col-1" in builder.requestedCollections)
        assertTrue("col-2" in builder.requestedCollections)
    }
}
