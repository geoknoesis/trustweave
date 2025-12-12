package com.trustweave.credential.revocation

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.vc.CredentialProof
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.StatusPurpose
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.credential.identifiers.StatusListId
import com.trustweave.credential.revocation.StatusListCredential
import com.trustweave.credential.revocation.RevocationStatus
import com.trustweave.did.identifiers.Did
import kotlinx.datetime.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for revocation models (StatusListCredential, StatusListSubject, RevocationStatus, StatusPurpose).
 */
class RevocationModelsTest {

    @Test
    fun `test StatusListCredential with all fields`() {
        val proof = CredentialProof.LinkedDataProof(
            type = "Ed25519Signature2020",
            created = Instant.parse("2024-01-01T00:00:00Z"),
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "assertionMethod",
            proofValue = "test-proof",
            additionalProperties = emptyMap()
        )

        val statusListId = "https://example.com/status-list/1"
        val statusList = StatusListCredential(
            id = CredentialId("https://example.com/status-list-credential/1"),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("StatusList2021Credential")),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromIri(
                com.trustweave.core.identifiers.Iri(statusListId),
                claims = mapOf(
                    "type" to JsonPrimitive("StatusList2021"),
                    "statusPurpose" to JsonPrimitive("revocation"),
                    "encodedList" to JsonPrimitive("H4sIAAAAAAAAA+3BMQEAAADCoPVPbQwfoAAAAAAAAAAAAAAAAAAAAIC3AYbSVKsAQAAA")
                )
            ),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z"),
            proof = proof
        )

        assertEquals("https://example.com/status-list-credential/1", statusList.id?.value)
        assertEquals(2, statusList.type.size)
        assertEquals("did:key:issuer", statusList.issuer.id.value)
        assertNotNull(statusList.proof)
    }

    @Test
    fun `test StatusListCredential with defaults`() {
        val statusListId = "https://example.com/status-list/1"
        val statusList = StatusListCredential(
            id = CredentialId("https://example.com/status-list-credential/1"),
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromIri(
                com.trustweave.core.identifiers.Iri(statusListId),
                claims = mapOf(
                    "encodedList" to JsonPrimitive("H4sIAAAAAAAAA+3BMQEAAADCoPVPbQwfoAAAAAAAAAAAAAAAAAAAAIC3AYbSVKsAQAAA")
                )
            ),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z")
        )

        assertNull(statusList.proof)
    }

    // StatusListSubject doesn't exist as a separate model - it's just the credentialSubject of a StatusListCredential
    // These tests are commented out as they test a non-existent model
    /*
    @Test
    fun `test StatusListSubject with all fields`() {
        // StatusListSubject is not a separate model
    }

    @Test
    fun `test StatusListSubject with defaults`() {
        // StatusListSubject is not a separate model
    }
    */

    @Test
    fun `test RevocationStatus with all fields`() {
        val status = RevocationStatus(
            revoked = true,
            suspended = false,
            statusListId = StatusListId("https://example.com/status-list/1"),
            reason = "Credential compromised"
        )

        assertTrue(status.revoked)
        assertFalse(status.suspended)
        assertEquals("https://example.com/status-list/1", status.statusListId?.value)
        assertEquals("Credential compromised", status.reason)
    }

    @Test
    fun `test RevocationStatus with defaults`() {
        val status = RevocationStatus(revoked = false)

        assertFalse(status.revoked)
        assertFalse(status.suspended)
        assertNull(status.statusListId)
        assertNull(status.reason)
    }

    @Test
    fun `test StatusPurpose enum values`() {
        assertEquals(StatusPurpose.REVOCATION, StatusPurpose.REVOCATION)
        assertEquals(StatusPurpose.SUSPENSION, StatusPurpose.SUSPENSION)
        assertEquals(2, StatusPurpose.values().size)
    }
}



