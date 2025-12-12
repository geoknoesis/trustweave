package com.trustweave.credential.revocation

import com.trustweave.credential.model.vc.CredentialStatus
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.StatusPurpose
import com.trustweave.credential.identifiers.StatusListId
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.credential.revocation.RevocationManagers
import com.trustweave.did.identifiers.Did
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Tests for CredentialRevocationManager interface contract.
 * Tests the interface behavior through InMemoryCredentialRevocationManager implementation.
 */
class StatusListManagerTest {

    private lateinit var manager: CredentialRevocationManager

    @BeforeEach
    fun setup() {
        manager = RevocationManagers.default()
    }

    @Test
    fun `test CredentialRevocationManager createStatusList with default size`() = runBlocking {
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
        val statusListId = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.SUSPENSION
        )

        val metadata = manager.getStatusList(statusListId)
        assertEquals(StatusPurpose.SUSPENSION, metadata?.purpose)
    }

    @Test
    fun `test CredentialRevocationManager revokeCredential`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.REVOCATION
        )

        val result = manager.revokeCredential("cred-123", statusListId)

        assertTrue(result)
    }

    @Test
    fun `test CredentialRevocationManager suspendCredential`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.SUSPENSION
        )

        val result = manager.suspendCredential("cred-123", statusListId)

        assertTrue(result)
    }

    @Test
    fun `test CredentialRevocationManager checkRevocationStatus with revoked credential`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.REVOCATION
        )
        val credentialId = "https://example.com/credentials/revoked-123"
        manager.revokeCredential(credentialId, statusListId)

        val credential = createTestCredential(
            id = credentialId,
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
    fun `test CredentialRevocationManager checkRevocationStatus with non-revoked credential`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.REVOCATION
        )

        val credential = createTestCredential(
            credentialStatus = CredentialStatus(
                id = statusListId,
                type = "StatusList2021Entry",
                statusListIndex = "1"
            )
        )

        val status = manager.checkRevocationStatus(credential)

        assertFalse(status.revoked)
    }

    @Test
    fun `test CredentialRevocationManager checkRevocationStatus with no credential status`() = runBlocking {
        val credential = createTestCredential(credentialStatus = null)

        val status = manager.checkRevocationStatus(credential)

        assertFalse(status.revoked)
        assertFalse(status.suspended)
    }

    @Test
    fun `test CredentialRevocationManager updateStatusListBatch`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.REVOCATION
        )

        manager.updateStatusListBatch(
            statusListId = statusListId,
            updates = listOf(
                com.trustweave.credential.revocation.StatusUpdate(0, revoked = true),
                com.trustweave.credential.revocation.StatusUpdate(5, revoked = true),
                com.trustweave.credential.revocation.StatusUpdate(10, revoked = true)
            )
        )

        // updateStatusListBatch returns Unit, so we just verify it doesn't throw
        assertTrue(true)
    }

    @Test
    fun `test CredentialRevocationManager getStatusList`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.REVOCATION
        )

        val retrieved = manager.getStatusList(statusListId)

        assertNotNull(retrieved)
        assertEquals(statusListId, retrieved?.id)
    }

    @Test
    fun `test CredentialRevocationManager getStatusList returns null for nonexistent`() = runBlocking {
        val result = manager.getStatusList(StatusListId("nonexistent"))

        assertNull(result)
    }

    @Test
    fun `test CredentialRevocationManager revokeCredential returns false for nonexistent status list`() = runBlocking {
        val result = manager.revokeCredential("cred-123", StatusListId("nonexistent"))

        assertFalse(result)
    }

    @Test
    fun `test CredentialRevocationManager suspendCredential returns false for nonexistent status list`() = runBlocking {
        val result = manager.suspendCredential("cred-123", StatusListId("nonexistent"))

        assertFalse(result)
    }

    @Test
    fun `test CredentialRevocationManager checkRevocationStatus with suspended credential`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.SUSPENSION
        )
        val credentialId = "https://example.com/credentials/suspended-123"
        manager.suspendCredential(credentialId, statusListId)

        val credential = createTestCredential(
            id = credentialId,
            credentialStatus = CredentialStatus(
                id = statusListId,
                type = "StatusList2021Entry",
                statusListIndex = "0"
            )
        )

        val status = manager.checkRevocationStatus(credential)

        assertTrue(status.suspended)
    }

    @Test
    fun `test CredentialRevocationManager updateStatusListBatch with empty list`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = "did:key:issuer",
            purpose = StatusPurpose.REVOCATION
        )

        manager.updateStatusListBatch(statusListId, emptyList())

        // updateStatusListBatch returns Unit, so we just verify it doesn't throw
        assertTrue(true)
    }

    @Test
    fun `test CredentialRevocationManager updateStatusListBatch throws exception for nonexistent status list`() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            manager.updateStatusListBatch(
                statusListId = StatusListId("nonexistent"),
                updates = listOf(com.trustweave.credential.revocation.StatusUpdate(0, revoked = true))
            )
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
        issuanceDate: Instant = Clock.System.now(),
        credentialStatus: CredentialStatus? = null
    ): VerifiableCredential {
        val subjectClaims = subject.entries.associate { entry ->
            entry.key to entry.value
        }.filterKeys { it != "id" }
        val subjectDid = Did(subject["id"]?.jsonPrimitive?.content ?: "did:key:subject")
        
        return VerifiableCredential(
            id = id?.let { CredentialId(it) },
            type = types.map { CredentialType.fromString(it) },
            issuer = Issuer.fromDid(Did(issuerDid)),
            credentialSubject = CredentialSubject.fromDid(subjectDid, claims = subjectClaims),
            issuanceDate = issuanceDate,
            credentialStatus = credentialStatus
        )
    }
}

