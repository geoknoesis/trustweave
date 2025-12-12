package com.trustweave.credential.revocation

import com.trustweave.credential.model.vc.CredentialStatus
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.StatusPurpose
import com.trustweave.credential.identifiers.StatusListId
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.did.identifiers.Did
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Comprehensive tests for InMemoryCredentialRevocationManager API.
 */
class InMemoryStatusListManagerTest {

    private lateinit var manager: CredentialRevocationManager
    private val issuerDid = "did:key:issuer"

    @BeforeEach
    fun setup() {
        manager = RevocationManagers.default()
    }

    @AfterEach
    fun cleanup() {
        // Manager doesn't have clear method, but tests are isolated
    }

    @Test
    fun `test create status list for revocation`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION,
            size = 1000
        )

        assertNotNull(statusListId)
        val metadata = manager.getStatusList(statusListId)
        assertNotNull(metadata)
        assertEquals(issuerDid, metadata?.issuerDid)
        assertEquals(StatusPurpose.REVOCATION, metadata?.purpose)
        assertEquals(1000, metadata?.size)
    }

    @Test
    fun `test create status list for suspension`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.SUSPENSION,
            size = 1000
        )

        val metadata = manager.getStatusList(statusListId)
        assertEquals(StatusPurpose.SUSPENSION, metadata?.purpose)
    }

    @Test
    fun `test create status list with custom size`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION,
            size = 5000
        )

        assertNotNull(statusListId)
        val metadata = manager.getStatusList(statusListId)
        assertEquals(5000, metadata?.size)
    }

    @Test
    fun `test revoke credential`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        manager.assignCredentialIndex("cred-123", statusListId, 0)

        val revoked = manager.revokeCredential("cred-123", statusListId)

        assertTrue(revoked)
    }

    @Test
    fun `test revoke credential fails when status list not found`() = runBlocking {
        val revoked = manager.revokeCredential("cred-123", StatusListId("nonexistent-status-list"))

        assertFalse(revoked)
    }

    @Test
    fun `test suspend credential`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.SUSPENSION
        )
        manager.assignCredentialIndex("cred-123", statusListId, 0)

        val suspended = manager.suspendCredential("cred-123", statusListId)

        assertTrue(suspended)
    }

    @Test
    fun `test check revocation status for non-revoked credential`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )

        val credential = createTestCredential(
            id = "cred-123",
            credentialStatus = CredentialStatus(
                id = statusListId,
                type = "StatusList2021Entry",
                statusListIndex = "0"
            )
        )

        val status = manager.checkRevocationStatus(credential)

        assertFalse(status.revoked)
        assertFalse(status.suspended)
    }

    @Test
    fun `test check revocation status for revoked credential`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        manager.assignCredentialIndex("cred-123", statusListId, 0)
        manager.revokeCredential("cred-123", statusListId)

        val credential = createTestCredential(
            id = "cred-123",
            credentialStatus = CredentialStatus(
                id = statusListId,
                type = "StatusList2021Entry",
                statusListIndex = "0"
            )
        )

        val status = manager.checkRevocationStatus(credential)

        assertTrue(status.revoked)
        assertFalse(status.suspended)
    }

    @Test
    fun `test check revocation status for suspended credential`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.SUSPENSION
        )
        manager.assignCredentialIndex("cred-123", statusListId, 0)
        manager.suspendCredential("cred-123", statusListId)

        val credential = createTestCredential(
            id = "cred-123",
            credentialStatus = CredentialStatus(
                id = statusListId,
                type = "StatusList2021Entry",
                statusListIndex = "0"
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
    fun `test update status list batch`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )

        manager.updateStatusListBatch(statusListId, listOf(
            StatusUpdate(0, revoked = true),
            StatusUpdate(1, revoked = true),
            StatusUpdate(2, revoked = true)
        ))

        val status0 = manager.checkStatusByIndex(statusListId, 0)
        assertTrue(status0.revoked)
    }

    @Test
    fun `test update status list batch fails when not found`() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            manager.updateStatusListBatch(StatusListId("nonexistent"), listOf(StatusUpdate(0, revoked = true)))
        }
    }

    @Test
    fun `test get status list`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )

        val retrieved = manager.getStatusList(statusListId)

        assertNotNull(retrieved)
        assertEquals(statusListId, retrieved?.id)
    }

    @Test
    fun `test get status list returns null when not found`() = runBlocking {
        assertNull(manager.getStatusList(StatusListId("nonexistent")))
    }

    @Test
    fun `test revoke multiple credentials`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        manager.assignCredentialIndex("cred-1", statusListId, 0)
        manager.assignCredentialIndex("cred-2", statusListId, 1)
        manager.assignCredentialIndex("cred-3", statusListId, 2)

        assertTrue(manager.revokeCredential("cred-1", statusListId))
        assertTrue(manager.revokeCredential("cred-2", statusListId))
        assertTrue(manager.revokeCredential("cred-3", statusListId))

        val credential1 = createTestCredential(
            id = "cred-1",
            credentialStatus = CredentialStatus(
                id = statusListId,
                type = "StatusList2021Entry",
                statusListIndex = "0"
            )
        )
        val credential2 = createTestCredential(
            id = "cred-2",
            credentialStatus = CredentialStatus(
                id = statusListId,
                type = "StatusList2021Entry",
                statusListIndex = "1"
            )
        )

        assertTrue(manager.checkRevocationStatus(credential1).revoked)
        assertTrue(manager.checkRevocationStatus(credential2).revoked)
    }

    @Test
    fun `test check revocation status uses credential ID from status`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        manager.assignCredentialIndex("cred-123", statusListId, 0)
        manager.revokeCredential("cred-123", statusListId)

        val credential = createTestCredential(
            id = "cred-123",
            credentialStatus = CredentialStatus(
                id = statusListId,
                type = "StatusList2021Entry",
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
        issuanceDate: Instant = Clock.System.now(),
        credentialStatus: CredentialStatus? = null
    ): VerifiableCredential {
        val subjectId = subject["id"]?.jsonPrimitive?.content ?: "did:key:subject"
        val subjectClaims = subject.filterKeys { it != "id" }
        return VerifiableCredential(
            id = id?.let { CredentialId(it) },
            type = types.map { CredentialType.fromString(it) },
            issuer = Issuer.fromDid(Did(issuerDid)),
            credentialSubject = CredentialSubject.fromDid(Did(subjectId), claims = subjectClaims),
            issuanceDate = issuanceDate,
            credentialStatus = credentialStatus
        )
    }
}

