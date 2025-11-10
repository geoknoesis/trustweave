package io.geoknoesis.vericore.credential.revocation

import io.geoknoesis.vericore.credential.models.VerifiableCredential
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
        val proof = io.geoknoesis.vericore.credential.models.Proof(
            type = "Ed25519Signature2020",
            created = "2024-01-01T00:00:00Z",
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "assertionMethod"
        )
        
        val subject = StatusListSubject(
            id = "https://example.com/status-list/1",
            type = "StatusList2021",
            statusPurpose = "revocation",
            encodedList = "H4sIAAAAAAAAA+3BMQEAAADCoPVPbQwfoAAAAAAAAAAAAAAAAAAAAIC3AYbSVKsAQAAA"
        )
        
        val statusList = StatusListCredential(
            id = "https://example.com/status-list-credential/1",
            type = listOf("VerifiableCredential", "StatusList2021Credential"),
            issuer = "did:key:issuer",
            credentialSubject = subject,
            issuanceDate = "2024-01-01T00:00:00Z",
            proof = proof
        )
        
        assertEquals("https://example.com/status-list-credential/1", statusList.id)
        assertEquals(2, statusList.type.size)
        assertEquals("did:key:issuer", statusList.issuer)
        assertNotNull(statusList.proof)
    }

    @Test
    fun `test StatusListCredential with defaults`() {
        val subject = StatusListSubject(
            id = "https://example.com/status-list/1",
            encodedList = "H4sIAAAAAAAAA+3BMQEAAADCoPVPbQwfoAAAAAAAAAAAAAAAAAAAAIC3AYbSVKsAQAAA"
        )
        
        val statusList = StatusListCredential(
            id = "https://example.com/status-list-credential/1",
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = subject,
            issuanceDate = "2024-01-01T00:00:00Z"
        )
        
        assertNull(statusList.proof)
    }

    @Test
    fun `test StatusListSubject with all fields`() {
        val subject = StatusListSubject(
            id = "https://example.com/status-list/1",
            type = "StatusList2021",
            statusPurpose = "suspension",
            encodedList = "H4sIAAAAAAAAA+3BMQEAAADCoPVPbQwfoAAAAAAAAAAAAAAAAAAAAIC3AYbSVKsAQAAA"
        )
        
        assertEquals("https://example.com/status-list/1", subject.id)
        assertEquals("StatusList2021", subject.type)
        assertEquals("suspension", subject.statusPurpose)
        assertNotNull(subject.encodedList)
    }

    @Test
    fun `test StatusListSubject with defaults`() {
        val subject = StatusListSubject(
            id = "https://example.com/status-list/1",
            encodedList = "H4sIAAAAAAAAA+3BMQEAAADCoPVPbQwfoAAAAAAAAAAAAAAAAAAAAIC3AYbSVKsAQAAA"
        )
        
        assertEquals("StatusList2021", subject.type)
        assertEquals("revocation", subject.statusPurpose)
    }

    @Test
    fun `test RevocationStatus with all fields`() {
        val status = RevocationStatus(
            revoked = true,
            suspended = false,
            statusListId = "https://example.com/status-list/1",
            reason = "Credential compromised"
        )
        
        assertTrue(status.revoked)
        assertFalse(status.suspended)
        assertEquals("https://example.com/status-list/1", status.statusListId)
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
        assertEquals(StatusPurpose.REVOCATION, StatusPurpose.valueOf("REVOCATION"))
        assertEquals(StatusPurpose.SUSPENSION, StatusPurpose.valueOf("SUSPENSION"))
        assertEquals(2, StatusPurpose.values().size)
    }
}



