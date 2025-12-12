package com.trustweave.credential.revocation

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
import kotlinx.datetime.Instant

/**
 * Comprehensive tests for BlockchainRevocationRegistry.
 */
class BlockchainRevocationRegistryTest {

    @Test
    fun `test anchorRevocationList`() = runBlocking {
        val statusListManager = object : CredentialRevocationManager {
            override suspend fun createStatusList(issuerDid: String, purpose: StatusPurpose, size: Int, customId: String?) = StatusListId("test-list")
            override suspend fun revokeCredential(credentialId: String, statusListId: StatusListId) = false
            override suspend fun suspendCredential(credentialId: String, statusListId: StatusListId) = false
            override suspend fun unrevokeCredential(credentialId: String, statusListId: StatusListId) = false
            override suspend fun unsuspendCredential(credentialId: String, statusListId: StatusListId) = false
            override suspend fun checkRevocationStatus(credential: VerifiableCredential) = RevocationStatus(revoked = false)
            override suspend fun checkStatusByIndex(statusListId: StatusListId, index: Int) = RevocationStatus(revoked = false)
            override suspend fun checkStatusByCredentialId(credentialId: String, statusListId: StatusListId) = RevocationStatus(revoked = false)
            override suspend fun getCredentialIndex(credentialId: String, statusListId: StatusListId) = null
            override suspend fun assignCredentialIndex(credentialId: String, statusListId: StatusListId, index: Int?) = 0
            override suspend fun revokeCredentials(credentialIds: List<String>, statusListId: StatusListId) = emptyMap<String, Boolean>()
            override suspend fun updateStatusListBatch(statusListId: StatusListId, updates: List<StatusUpdate>) {}
            override suspend fun getStatusListStatistics(statusListId: StatusListId) = null
            override suspend fun getStatusList(statusListId: StatusListId) = null
            override suspend fun listStatusLists(issuerDid: String?) = emptyList<StatusListMetadata>()
            override suspend fun deleteStatusList(statusListId: StatusListId) = false
            override suspend fun expandStatusList(statusListId: StatusListId, additionalSize: Int) {}
        }

        val registry = BlockchainRevocationRegistry(
            anchorClient = null,
            statusListManager = statusListManager
        )

        val statusListId = StatusListId("https://example.com/status-list/1")
        val claims = mapOf(
            "type" to JsonPrimitive("StatusList2021"),
            "statusPurpose" to JsonPrimitive("revocation"),
            "encodedList" to JsonPrimitive("H4sIAAAAAAAAA+3BMQEAAADCoPVPbQwfoAAAAAAAAAAAAAAAAAAAAIC3AYbSVKsAQAAA")
        )
        val statusList = VerifiableCredential(
            id = CredentialId(statusListId.value),
            type = listOf(CredentialType.fromString("VerifiableCredential"), CredentialType.fromString("StatusList2021Credential")),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromIri(statusListId.value, claims = claims),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z")
        )

        val anchorRef = registry.anchorRevocationList(statusList, "algorand:testnet")

        // Placeholder implementation returns status list ID
        assertEquals(statusList.id?.value, anchorRef)
    }

    @Test
    fun `test checkRevocationOnChain delegates to statusListManager`() = runBlocking {
        val expectedStatus = RevocationStatus(revoked = false)

        val statusListManager = object : CredentialRevocationManager {
            override suspend fun createStatusList(issuerDid: String, purpose: StatusPurpose, size: Int, customId: String?) = StatusListId("test-list")
            override suspend fun revokeCredential(credentialId: String, statusListId: StatusListId) = false
            override suspend fun suspendCredential(credentialId: String, statusListId: StatusListId) = false
            override suspend fun unrevokeCredential(credentialId: String, statusListId: StatusListId) = false
            override suspend fun unsuspendCredential(credentialId: String, statusListId: StatusListId) = false
            override suspend fun checkRevocationStatus(credential: VerifiableCredential) = expectedStatus
            override suspend fun checkStatusByIndex(statusListId: StatusListId, index: Int) = RevocationStatus(revoked = false)
            override suspend fun checkStatusByCredentialId(credentialId: String, statusListId: StatusListId) = RevocationStatus(revoked = false)
            override suspend fun getCredentialIndex(credentialId: String, statusListId: StatusListId) = null
            override suspend fun assignCredentialIndex(credentialId: String, statusListId: StatusListId, index: Int?) = 0
            override suspend fun revokeCredentials(credentialIds: List<String>, statusListId: StatusListId) = emptyMap<String, Boolean>()
            override suspend fun updateStatusListBatch(statusListId: StatusListId, updates: List<StatusUpdate>) {}
            override suspend fun getStatusListStatistics(statusListId: StatusListId) = null
            override suspend fun getStatusList(statusListId: StatusListId) = null
            override suspend fun listStatusLists(issuerDid: String?) = emptyList<StatusListMetadata>()
            override suspend fun deleteStatusList(statusListId: StatusListId) = false
            override suspend fun expandStatusList(statusListId: StatusListId, additionalSize: Int) {}
        }

        val registry = BlockchainRevocationRegistry(
            anchorClient = null,
            statusListManager = statusListManager
        )

        val credential = VerifiableCredential(
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject")),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z")
        )

        val status = registry.checkRevocationOnChain(credential, "algorand:testnet")

        assertEquals(expectedStatus, status)
    }
}



