package io.geoknoesis.vericore.credential.revocation

import io.geoknoesis.vericore.credential.models.CredentialStatus
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for InMemoryStatusListManager API.
 */
class InMemoryStatusListManagerTest {

    private lateinit var manager: InMemoryStatusListManager
    private val issuerDid = "did:key:issuer"

    @BeforeEach
    fun setup() {
        manager = InMemoryStatusListManager()
    }

    @AfterEach
    fun cleanup() {
        // Manager doesn't have clear method, but tests are isolated
    }

    @Test
    fun `test create status list for revocation`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION,
            size = 1000
        )
        
        assertNotNull(statusList)
        assertEquals(issuerDid, statusList.issuer)
        assertTrue(statusList.type.contains("StatusList2021Credential"))
        assertEquals("revocation", statusList.credentialSubject.statusPurpose)
        assertNotNull(statusList.credentialSubject.encodedList)
    }

    @Test
    fun `test create status list for suspension`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.SUSPENSION,
            size = 1000
        )
        
        assertEquals("suspension", statusList.credentialSubject.statusPurpose)
    }

    @Test
    fun `test create status list with custom size`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION,
            size = 5000
        )
        
        assertNotNull(statusList)
    }

    @Test
    fun `test revoke credential`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        
        val revoked = manager.revokeCredential("cred-123", statusList.id)
        
        assertTrue(revoked)
    }

    @Test
    fun `test revoke credential fails when status list not found`() = runBlocking {
        val revoked = manager.revokeCredential("cred-123", "nonexistent-status-list")
        
        assertFalse(revoked)
    }

    @Test
    fun `test suspend credential`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.SUSPENSION
        )
        
        val suspended = manager.suspendCredential("cred-123", statusList.id)
        
        assertTrue(suspended)
    }

    @Test
    fun `test check revocation status for non-revoked credential`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        
        val credential = createTestCredential(
            id = "cred-123",
            credentialStatus = CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry",
                statusListCredential = statusList.id
            )
        )
        
        val status = manager.checkRevocationStatus(credential)
        
        assertFalse(status.revoked)
        assertFalse(status.suspended)
    }

    @Test
    fun `test check revocation status for revoked credential`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        
        manager.revokeCredential("cred-123", statusList.id)
        
        val credential = createTestCredential(
            id = "cred-123",
            credentialStatus = CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry",
                statusListCredential = statusList.id
            )
        )
        
        val status = manager.checkRevocationStatus(credential)
        
        assertTrue(status.revoked)
        assertFalse(status.suspended)
    }

    @Test
    fun `test check revocation status for suspended credential`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.SUSPENSION
        )
        
        manager.suspendCredential("cred-123", statusList.id)
        
        val credential = createTestCredential(
            id = "cred-123",
            credentialStatus = CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry",
                statusListCredential = statusList.id
            )
        )
        
        val status = manager.checkRevocationStatus(credential)
        
        assertFalse(status.revoked)
        assertTrue(status.suspended)
    }

    @Test
    fun `test check revocation status for credential without status`() = runBlocking {
        val credential = createTestCredential()
        
        val status = manager.checkRevocationStatus(credential)
        
        assertFalse(status.revoked)
        assertFalse(status.suspended)
    }

    @Test
    fun `test update status list`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        
        val updated = manager.updateStatusList(statusList.id, listOf(0, 1, 2))
        
        assertNotNull(updated)
        assertNotEquals(statusList.credentialSubject.encodedList, updated.credentialSubject.encodedList)
    }

    @Test
    fun `test update status list fails when not found`() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            manager.updateStatusList("nonexistent", listOf(0, 1))
        }
    }

    @Test
    fun `test get status list`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        
        val retrieved = manager.getStatusList(statusList.id)
        
        assertNotNull(retrieved)
        assertEquals(statusList.id, retrieved?.id)
    }

    @Test
    fun `test get status list returns null when not found`() = runBlocking {
        assertNull(manager.getStatusList("nonexistent"))
    }

    @Test
    fun `test revoke multiple credentials`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        
        assertTrue(manager.revokeCredential("cred-1", statusList.id))
        assertTrue(manager.revokeCredential("cred-2", statusList.id))
        assertTrue(manager.revokeCredential("cred-3", statusList.id))
        
        val credential1 = createTestCredential(
            id = "cred-1",
            credentialStatus = CredentialStatus(
                id = "status-1",
                type = "StatusList2021Entry",
                statusListCredential = statusList.id
            )
        )
        val credential2 = createTestCredential(
            id = "cred-2",
            credentialStatus = CredentialStatus(
                id = "status-2",
                type = "StatusList2021Entry",
                statusListCredential = statusList.id
            )
        )
        
        assertTrue(manager.checkRevocationStatus(credential1).revoked)
        assertTrue(manager.checkRevocationStatus(credential2).revoked)
    }

    @Test
    fun `test check revocation status uses credential ID from status`() = runBlocking {
        val statusList = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        
        manager.revokeCredential("cred-123", statusList.id)
        
        val credential = createTestCredential(
            id = "cred-123",
            credentialStatus = CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry",
                statusListCredential = statusList.id,
                statusListIndex = "0"
            )
        )
        
        val status = manager.checkRevocationStatus(credential)
        
        assertTrue(status.revoked)
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

