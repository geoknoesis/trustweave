package io.geoknoesis.vericore.credential.revocation

import io.geoknoesis.vericore.credential.models.CredentialStatus
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for StatusListManager interface contract.
 * Tests the interface behavior through InMemoryStatusListManager implementation.
 */
class StatusListManagerTest {

    private lateinit var manager: StatusListManager

    @BeforeEach
    fun setup() {
        manager = InMemoryStatusListManager()
    }

    @Test
    fun `test StatusListManager createStatusList with default size`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.REVOCATION
        )
        
        assertNotNull(statusList)
        assertEquals("did:key:issuer", statusList.issuer)
        assertEquals(StatusPurpose.REVOCATION.name.lowercase(), statusList.credentialSubject.statusPurpose)
    }

    @Test
    fun `test StatusListManager createStatusList with custom size`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.REVOCATION,
            size = 1000
        )
        
        assertNotNull(statusList)
        assertTrue(statusList.credentialSubject.encodedList.isNotBlank())
    }

    @Test
    fun `test StatusListManager createStatusList with SUSPENSION purpose`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.SUSPENSION
        )
        
        assertEquals(StatusPurpose.SUSPENSION.name.lowercase(), statusList.credentialSubject.statusPurpose)
    }

    @Test
    fun `test StatusListManager revokeCredential`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.REVOCATION
        )
        
        val result = manager.revokeCredential("cred-123", statusList.id)
        
        assertTrue(result)
    }

    @Test
    fun `test StatusListManager suspendCredential`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.SUSPENSION
        )
        
        val result = manager.suspendCredential("cred-123", statusList.id)
        
        assertTrue(result)
    }

    @Test
    fun `test StatusListManager checkRevocationStatus with revoked credential`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.REVOCATION
        )
        val credentialId = "https://example.com/credentials/revoked-123"
        manager.revokeCredential(credentialId, statusList.id)
        
        val credential = createTestCredential(
            id = credentialId,
            credentialStatus = CredentialStatus(
                id = statusList.id,
                type = "StatusList2021Entry",
                statusListIndex = "0"
            )
        )
        
        val status = manager.checkRevocationStatus(credential)
        
        assertTrue(status.revoked)
        assertEquals(statusList.id, status.statusListId)
    }

    @Test
    fun `test StatusListManager checkRevocationStatus with non-revoked credential`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.REVOCATION
        )
        
        val credential = createTestCredential(
            credentialStatus = CredentialStatus(
                id = statusList.id,
                type = "StatusList2021Entry",
                statusListIndex = "1"
            )
        )
        
        val status = manager.checkRevocationStatus(credential)
        
        assertFalse(status.revoked)
    }

    @Test
    fun `test StatusListManager checkRevocationStatus with no credential status`() = runBlocking {
        val credential = createTestCredential(credentialStatus = null)
        
        val status = manager.checkRevocationStatus(credential)
        
        assertFalse(status.revoked)
        assertFalse(status.suspended)
    }

    @Test
    fun `test StatusListManager updateStatusList`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.REVOCATION
        )
        
        val updated = manager.updateStatusList(statusList.id, listOf(0, 5, 10))
        
        assertNotNull(updated)
        assertEquals(statusList.id, updated.id)
    }

    @Test
    fun `test StatusListManager getStatusList`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.REVOCATION
        )
        
        val retrieved = manager.getStatusList(statusList.id)
        
        assertNotNull(retrieved)
        assertEquals(statusList.id, retrieved?.id)
    }

    @Test
    fun `test StatusListManager getStatusList returns null for nonexistent`() = runBlocking {
        val result = manager.getStatusList("nonexistent")
        
        assertNull(result)
    }

    @Test
    fun `test StatusListManager revokeCredential returns false for nonexistent status list`() = runBlocking {
        val result = manager.revokeCredential("cred-123", "nonexistent")
        
        assertFalse(result)
    }

    @Test
    fun `test StatusListManager suspendCredential returns false for nonexistent status list`() = runBlocking {
        val result = manager.suspendCredential("cred-123", "nonexistent")
        
        assertFalse(result)
    }

    @Test
    fun `test StatusListManager checkRevocationStatus with suspended credential`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.SUSPENSION
        )
        val credentialId = "https://example.com/credentials/suspended-123"
        manager.suspendCredential(credentialId, statusList.id)
        
        val credential = createTestCredential(
            id = credentialId,
            credentialStatus = CredentialStatus(
                id = statusList.id,
                type = "StatusList2021Entry",
                statusListIndex = "0"
            )
        )
        
        val status = manager.checkRevocationStatus(credential)
        
        assertTrue(status.suspended)
    }

    @Test
    fun `test StatusListManager updateStatusList with empty list`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.REVOCATION
        )
        
        val updated = manager.updateStatusList(statusList.id, emptyList())
        
        assertNotNull(updated)
    }

    @Test
    fun `test StatusListManager updateStatusList throws exception for nonexistent status list`() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            manager.updateStatusList("nonexistent", listOf(0))
        }
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
        credentialStatus: CredentialStatus? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            credentialStatus = credentialStatus
        )
    }
}

