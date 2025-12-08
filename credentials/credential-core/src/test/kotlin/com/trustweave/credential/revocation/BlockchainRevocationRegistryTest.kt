package com.trustweave.credential.revocation

import com.trustweave.credential.model.vc.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for BlockchainRevocationRegistry.
 */
class BlockchainRevocationRegistryTest {

    @Test
    fun `test anchorRevocationList`() = runBlocking {
        val statusListManager = object : StatusListManager {
            override suspend fun createStatusList(issuerDid: String, purpose: StatusPurpose, size: Int, customId: String?) = TODO()
            override suspend fun revokeCredential(credentialId: String, statusListId: String) = TODO()
            override suspend fun suspendCredential(credentialId: String, statusListId: String) = TODO()
            override suspend fun checkRevocationStatus(credential: VerifiableCredential) = TODO()
            override suspend fun updateStatusList(statusListId: String, revokedIndices: List<Int>) = TODO()
            override suspend fun getStatusList(statusListId: String) = null
            override suspend fun listStatusLists(issuerDid: String?) = emptyList<StatusListCredential>()
            override suspend fun deleteStatusList(statusListId: String) = false
            override suspend fun getStatusListStatistics(statusListId: String) = null
            override suspend fun unrevokeCredential(credentialId: String, statusListId: String) = false
            override suspend fun unsuspendCredential(credentialId: String, statusListId: String) = false
            override suspend fun checkStatusByIndex(statusListId: String, index: Int) = RevocationStatus(revoked = false)
            override suspend fun checkStatusByCredentialId(credentialId: String, statusListId: String) = RevocationStatus(revoked = false)
            override suspend fun getCredentialIndex(credentialId: String, statusListId: String) = null
            override suspend fun assignCredentialIndex(credentialId: String, statusListId: String, index: Int?) = 0
            override suspend fun revokeCredentials(credentialIds: List<String>, statusListId: String) = emptyMap<String, Boolean>()
            override suspend fun updateStatusListBatch(statusListId: String, updates: List<StatusUpdate>) = TODO()
            override suspend fun expandStatusList(statusListId: String, additionalSize: Int) = TODO()
        }

        val registry = BlockchainRevocationRegistry(
            anchorClient = null,
            statusListManager = statusListManager
        )

        val statusList = StatusListCredential(
            id = "https://example.com/status-list/1",
            type = listOf("VerifiableCredential", "StatusList2021Credential"),
            issuer = "did:key:issuer",
            credentialSubject = StatusListSubject(
                id = "https://example.com/status-list/1",
                encodedList = "H4sIAAAAAAAAA+3BMQEAAADCoPVPbQwfoAAAAAAAAAAAAAAAAAAAAIC3AYbSVKsAQAAA"
            ),
            issuanceDate = "2024-01-01T00:00:00Z"
        )

        val anchorRef = registry.anchorRevocationList(statusList, "algorand:testnet")

        // Placeholder implementation returns status list ID
        assertEquals(statusList.id, anchorRef)
    }

    @Test
    fun `test checkRevocationOnChain delegates to statusListManager`() = runBlocking {
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject { put("id", "did:key:subject") },
            issuanceDate = "2024-01-01T00:00:00Z"
        )

        val expectedStatus = RevocationStatus(revoked = false)

        val statusListManager = object : StatusListManager {
            override suspend fun createStatusList(issuerDid: String, purpose: StatusPurpose, size: Int, customId: String?) = TODO()
            override suspend fun revokeCredential(credentialId: String, statusListId: String) = TODO()
            override suspend fun suspendCredential(credentialId: String, statusListId: String) = TODO()
            override suspend fun checkRevocationStatus(credential: VerifiableCredential) = expectedStatus
            override suspend fun updateStatusList(statusListId: String, revokedIndices: List<Int>) = TODO()
            override suspend fun getStatusList(statusListId: String) = null
            override suspend fun listStatusLists(issuerDid: String?) = emptyList<StatusListCredential>()
            override suspend fun deleteStatusList(statusListId: String) = false
            override suspend fun getStatusListStatistics(statusListId: String) = null
            override suspend fun unrevokeCredential(credentialId: String, statusListId: String) = false
            override suspend fun unsuspendCredential(credentialId: String, statusListId: String) = false
            override suspend fun checkStatusByIndex(statusListId: String, index: Int) = RevocationStatus(revoked = false)
            override suspend fun checkStatusByCredentialId(credentialId: String, statusListId: String) = RevocationStatus(revoked = false)
            override suspend fun getCredentialIndex(credentialId: String, statusListId: String) = null
            override suspend fun assignCredentialIndex(credentialId: String, statusListId: String, index: Int?) = 0
            override suspend fun revokeCredentials(credentialIds: List<String>, statusListId: String) = emptyMap<String, Boolean>()
            override suspend fun updateStatusListBatch(statusListId: String, updates: List<StatusUpdate>) = TODO()
            override suspend fun expandStatusList(statusListId: String, additionalSize: Int) = TODO()
        }

        val registry = BlockchainRevocationRegistry(
            anchorClient = null,
            statusListManager = statusListManager
        )

        val status = registry.checkRevocationOnChain(credential, "algorand:testnet")

        assertEquals(expectedStatus, status)
    }
}



