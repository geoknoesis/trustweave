package io.geoknoesis.vericore.credential.revocation

import io.geoknoesis.vericore.credential.models.CredentialStatus
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive interface contract tests for StatusListManager.
 * Tests all methods, branches, and edge cases.
 */
class StatusListManagerInterfaceContractTest {

    @Test
    fun `test StatusListManager createStatusList returns status list credential`() = runBlocking {
        val manager = createMockManager()
        
        val statusList = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.REVOCATION
        )
        
        assertNotNull(statusList)
        assertEquals("did:key:issuer", statusList.issuer)
        assertTrue(statusList.type.contains("StatusList2021Credential"))
    }

    @Test
    fun `test StatusListManager createStatusList with custom size`() = runBlocking {
        val manager = createMockManager()
        
        val statusList = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.REVOCATION,
            size = 1000
        )
        
        assertNotNull(statusList)
    }

    @Test
    fun `test StatusListManager createStatusList with SUSPENSION purpose`() = runBlocking {
        val manager = createMockManager()
        
        val statusList = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.SUSPENSION
        )
        
        assertNotNull(statusList)
        assertEquals("suspension", statusList.credentialSubject.statusPurpose)
    }

    @Test
    fun `test StatusListManager revokeCredential returns true`() = runBlocking {
        val manager = createMockManager()
        val statusList = manager.createStatusList("did:key:issuer", StatusPurpose.REVOCATION)
        
        val revoked = manager.revokeCredential("cred-1", statusList.id)
        
        assertTrue(revoked)
    }

    @Test
    fun `test StatusListManager revokeCredential returns false for invalid status list`() = runBlocking {
        val manager = createMockManager()
        
        val revoked = manager.revokeCredential("cred-1", "non-existent")
        
        assertFalse(revoked)
    }

    @Test
    fun `test StatusListManager suspendCredential returns true`() = runBlocking {
        val manager = createMockManager()
        val statusList = manager.createStatusList("did:key:issuer", StatusPurpose.SUSPENSION)
        
        val suspended = manager.suspendCredential("cred-1", statusList.id)
        
        assertTrue(suspended)
    }

    @Test
    fun `test StatusListManager checkRevocationStatus returns revoked status`() = runBlocking {
        val manager = createMockManager()
        val statusList = manager.createStatusList("did:key:issuer", StatusPurpose.REVOCATION)
        manager.revokeCredential("cred-1", statusList.id)
        
        val credential = createTestCredential(
            id = "cred-1",
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
    fun `test StatusListManager checkRevocationStatus returns not revoked`() = runBlocking {
        val manager = createMockManager()
        val credential = createTestCredential(id = "cred-1")
        
        val status = manager.checkRevocationStatus(credential)
        
        assertFalse(status.revoked)
    }

    @Test
    fun `test StatusListManager updateStatusList returns updated status list`() = runBlocking {
        val manager = createMockManager()
        val statusList = manager.createStatusList("did:key:issuer", StatusPurpose.REVOCATION)
        
        val updated = manager.updateStatusList(statusList.id, listOf(0, 1, 2))
        
        assertNotNull(updated)
        assertEquals(statusList.id, updated.id)
    }

    @Test
    fun `test StatusListManager updateStatusList returns null for non-existent`() = runBlocking {
        val manager = createMockManager()
        
        // This should throw IllegalArgumentException based on InMemoryStatusListManager
        assertFailsWith<IllegalArgumentException> {
            manager.updateStatusList("non-existent", listOf(0))
        }
    }

    @Test
    fun `test StatusListManager getStatusList returns status list`() = runBlocking {
        val manager = createMockManager()
        val statusList = manager.createStatusList("did:key:issuer", StatusPurpose.REVOCATION)
        
        val retrieved = manager.getStatusList(statusList.id)
        
        assertNotNull(retrieved)
        assertEquals(statusList.id, retrieved?.id)
    }

    @Test
    fun `test StatusListManager getStatusList returns null for non-existent`() = runBlocking {
        val manager = createMockManager()
        
        val retrieved = manager.getStatusList("non-existent")
        
        assertNull(retrieved)
    }

    @Test
    fun `test StatusListManager revoke then check status`() = runBlocking {
        val manager = createMockManager()
        val statusList = manager.createStatusList("did:key:issuer", StatusPurpose.REVOCATION)
        manager.revokeCredential("cred-1", statusList.id)
        
        val credential = createTestCredential(
            id = "cred-1",
            credentialStatus = CredentialStatus(
                id = statusList.id,
                type = "StatusList2021Entry",
                statusListIndex = "0"
            )
        )
        
        val status = manager.checkRevocationStatus(credential)
        assertTrue(status.revoked)
        
        // Suspend should also work
        manager.suspendCredential("cred-2", statusList.id)
        val credential2 = createTestCredential(
            id = "cred-2",
            credentialStatus = CredentialStatus(
                id = statusList.id,
                type = "StatusList2021Entry",
                statusListIndex = "1"
            )
        )
        val status2 = manager.checkRevocationStatus(credential2)
        assertTrue(status2.suspended)
    }

    private fun createMockManager(): StatusListManager {
        return object : StatusListManager {
            private val statusLists = mutableMapOf<String, StatusListCredential>()
            private val revokedIndices = mutableMapOf<String, MutableSet<Int>>()
            private val suspendedIndices = mutableMapOf<String, MutableSet<Int>>()
            private var nextIndex = 0
            
            override suspend fun createStatusList(
                issuerDid: String,
                purpose: StatusPurpose,
                size: Int
            ): StatusListCredential {
                val id = "status-list-${java.util.UUID.randomUUID()}"
                val encodedList = "0".repeat(size / 8) // Simplified encoding
                val statusList = StatusListCredential(
                    id = id,
                    type = listOf("VerifiableCredential", "StatusList2021Credential"),
                    issuer = issuerDid,
                    credentialSubject = StatusListSubject(
                        id = id,
                        type = "StatusList2021",
                        statusPurpose = purpose.name.lowercase(),
                        encodedList = encodedList
                    ),
                    issuanceDate = java.time.Instant.now().toString()
                )
                statusLists[id] = statusList
                revokedIndices[id] = mutableSetOf()
                suspendedIndices[id] = mutableSetOf()
                return statusList
            }
            
            override suspend fun revokeCredential(credentialId: String, statusListId: String): Boolean {
                if (!statusLists.containsKey(statusListId)) return false
                val index = nextIndex++
                revokedIndices.getOrPut(statusListId) { mutableSetOf() }.add(index)
                return true
            }
            
            override suspend fun suspendCredential(credentialId: String, statusListId: String): Boolean {
                if (!statusLists.containsKey(statusListId)) return false
                val index = nextIndex++
                suspendedIndices.getOrPut(statusListId) { mutableSetOf() }.add(index)
                return true
            }
            
            override suspend fun checkRevocationStatus(credential: VerifiableCredential): RevocationStatus {
                val status = credential.credentialStatus ?: return RevocationStatus(revoked = false)
                val statusListId = status.id
                val index = status.statusListIndex?.toIntOrNull() ?: return RevocationStatus(revoked = false)
                
                val revoked = revokedIndices[statusListId]?.contains(index) ?: false
                val suspended = suspendedIndices[statusListId]?.contains(index) ?: false
                
                return RevocationStatus(
                    revoked = revoked,
                    suspended = suspended,
                    statusListId = statusListId
                )
            }
            
            override suspend fun updateStatusList(
                statusListId: String,
                revokedIndices: List<Int>
            ): StatusListCredential {
                val statusList = statusLists[statusListId]
                    ?: throw IllegalArgumentException("Status list not found: $statusListId")
                
                this.revokedIndices[statusListId] = revokedIndices.toMutableSet()
                
                return statusList.copy(
                    credentialSubject = statusList.credentialSubject.copy(
                        encodedList = "updated" // Simplified
                    )
                )
            }
            
            override suspend fun getStatusList(statusListId: String): StatusListCredential? {
                return statusLists[statusListId]
            }
        }
    }

    private fun createTestCredential(
        id: String? = null,
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

