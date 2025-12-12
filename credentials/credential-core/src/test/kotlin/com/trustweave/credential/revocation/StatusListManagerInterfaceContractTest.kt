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
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Comprehensive interface contract tests for CredentialRevocationManager.
 * Tests all methods, branches, and edge cases.
 */
class StatusListManagerInterfaceContractTest {

    @Test
    fun `test CredentialRevocationManager createStatusList returns status list ID`() = runBlocking {
        val manager = createMockManager()

        val statusListId = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.REVOCATION
        )

        assertNotNull(statusListId)
        val metadata = manager.getStatusList(statusListId)
        assertNotNull(metadata)
        assertEquals("did:key:issuer", metadata?.issuerDid)
        assertEquals(StatusPurpose.REVOCATION, metadata?.purpose)
    }

    @Test
    fun `test CredentialRevocationManager createStatusList with custom size`() = runBlocking {
        val manager = createMockManager()

        val statusListId = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.REVOCATION,
            size = 1000
        )

        assertNotNull(statusListId)
        val metadata = manager.getStatusList(statusListId)
        assertNotNull(metadata)
        assertEquals(1000, metadata?.size)
    }

    @Test
    fun `test CredentialRevocationManager createStatusList with SUSPENSION purpose`() = runBlocking {
        val manager = createMockManager()

        val statusListId = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.SUSPENSION
        )

        assertNotNull(statusListId)
        val metadata = manager.getStatusList(statusListId)
        assertNotNull(metadata)
        assertEquals(StatusPurpose.SUSPENSION, metadata?.purpose)
    }

    @Test
    fun `test CredentialRevocationManager revokeCredential returns true`() = runBlocking {
        val manager = createMockManager()
        val statusListId = manager.createStatusList("did:key:issuer", StatusPurpose.REVOCATION)
        manager.assignCredentialIndex("cred-1", statusListId, 0)

        val revoked = manager.revokeCredential("cred-1", statusListId)

        assertTrue(revoked)
    }

    @Test
    fun `test CredentialRevocationManager revokeCredential returns false for invalid status list`() = runBlocking {
        val manager = createMockManager()

        val revoked = manager.revokeCredential("cred-1", StatusListId("non-existent"))

        assertFalse(revoked)
    }

    @Test
    fun `test CredentialRevocationManager suspendCredential returns true`() = runBlocking {
        val manager = createMockManager()
        val statusListId = manager.createStatusList("did:key:issuer", StatusPurpose.SUSPENSION)
        manager.assignCredentialIndex("cred-1", statusListId, 0)

        val suspended = manager.suspendCredential("cred-1", statusListId)

        assertTrue(suspended)
    }

    @Test
    fun `test CredentialRevocationManager checkRevocationStatus returns revoked status`() = runBlocking {
        val manager = createMockManager()
        val statusListId = manager.createStatusList("did:key:issuer", StatusPurpose.REVOCATION)
        manager.assignCredentialIndex("cred-1", statusListId, 0)
        manager.revokeCredential("cred-1", statusListId)

        val credential = createTestCredential(
            id = "cred-1",
            credentialStatus = CredentialStatus(
                id = statusListId,
                type = "StatusList2021Entry",
                statusListIndex = "0"
            )
        )

        val status = manager.checkRevocationStatus(credential)

        assertTrue(status.revoked)
        assertEquals(statusListId, status.statusListId)
    }

    @Test
    fun `test CredentialRevocationManager checkRevocationStatus returns not revoked`() = runBlocking {
        val manager = createMockManager()
        val credential = createTestCredential(id = "cred-1")

        val status = manager.checkRevocationStatus(credential)

        assertFalse(status.revoked)
    }

    @Test
    fun `test CredentialRevocationManager updateStatusListBatch updates status list`() = runBlocking {
        val manager = createMockManager()
        val statusListId = manager.createStatusList("did:key:issuer", StatusPurpose.REVOCATION)

        manager.updateStatusListBatch(statusListId, listOf(
            StatusUpdate(0, revoked = true),
            StatusUpdate(1, revoked = true),
            StatusUpdate(2, revoked = true)
        ))

        val status0 = manager.checkStatusByIndex(statusListId, 0)
        assertTrue(status0.revoked)
    }

    @Test
    fun `test CredentialRevocationManager updateStatusListBatch throws for non-existent`() = runBlocking {
        val manager = createMockManager()

        // This should throw IllegalArgumentException
        assertFailsWith<IllegalArgumentException> {
            manager.updateStatusListBatch(StatusListId("non-existent"), listOf(StatusUpdate(0, revoked = true)))
        }
    }

    @Test
    fun `test CredentialRevocationManager getStatusList returns status list metadata`() = runBlocking {
        val manager = createMockManager()
        val statusListId = manager.createStatusList("did:key:issuer", StatusPurpose.REVOCATION)

        val retrieved = manager.getStatusList(statusListId)

        assertNotNull(retrieved)
        assertEquals(statusListId, retrieved?.id)
        assertEquals("did:key:issuer", retrieved?.issuerDid)
    }

    @Test
    fun `test CredentialRevocationManager getStatusList returns null for non-existent`() = runBlocking {
        val manager = createMockManager()

        val retrieved = manager.getStatusList(StatusListId("non-existent"))

        assertNull(retrieved)
    }

    @Test
    fun `test CredentialRevocationManager revoke then check status`() = runBlocking {
        val manager = createMockManager()
        val statusListId = manager.createStatusList("did:key:issuer", StatusPurpose.REVOCATION)
        manager.assignCredentialIndex("cred-1", statusListId, 0)
        manager.revokeCredential("cred-1", statusListId)

        val credential = createTestCredential(
            id = "cred-1",
            credentialStatus = CredentialStatus(
                id = statusListId,
                type = "StatusList2021Entry",
                statusListIndex = "0"
            )
        )

        val status = manager.checkRevocationStatus(credential)
        assertTrue(status.revoked)

        // Suspend should also work
        val statusListId2 = manager.createStatusList("did:key:issuer", StatusPurpose.SUSPENSION)
        manager.assignCredentialIndex("cred-2", statusListId2, 0)
        manager.suspendCredential("cred-2", statusListId2)
        val credential2 = createTestCredential(
            id = "cred-2",
            credentialStatus = CredentialStatus(
                id = statusListId2,
                type = "StatusList2021Entry",
                statusListIndex = "0"
            )
        )
        val status2 = manager.checkRevocationStatus(credential2)
        assertTrue(status2.suspended)
    }

    private fun createMockManager(): CredentialRevocationManager {
        return RevocationManagers.default()
    }

    private fun createTestCredential(
        id: String? = null,
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



