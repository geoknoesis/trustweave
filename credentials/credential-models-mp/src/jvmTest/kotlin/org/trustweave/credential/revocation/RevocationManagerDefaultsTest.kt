package org.trustweave.credential.revocation

import kotlinx.coroutines.test.runTest
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.RevocationFailurePolicy
import org.trustweave.credential.requests.VerificationOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** JVM-side contract checks for the interface defaults and option semantics shared by all managers. */
class RevocationManagerDefaultsTest {
    /** A manager that implements only what the defaults under test call; everything else is a loud failure. */
    private class MinimalManager(
        private val existing: List<StatusListMetadata> = emptyList(),
    ) : CredentialRevocationManager {
        val created = mutableListOf<Triple<String, StatusPurpose, String?>>()

        override suspend fun listStatusLists(issuerDid: String?) = existing.filter { issuerDid == null || it.issuerDid == issuerDid }

        override suspend fun createStatusList(
            issuerDid: String,
            purpose: StatusPurpose,
            size: Int,
            customId: String?,
        ): StatusListId {
            created += Triple(issuerDid, purpose, customId)
            return StatusListId("https://example.com/lists/new-${created.size}")
        }

        private fun nope(): Nothing = throw UnsupportedOperationException("not used by these tests")

        override suspend fun revokeCredential(
            credentialId: String,
            statusListId: StatusListId,
        ) = nope()

        override suspend fun suspendCredential(
            credentialId: String,
            statusListId: StatusListId,
        ) = nope()

        override suspend fun unrevokeCredential(
            credentialId: String,
            statusListId: StatusListId,
        ) = nope()

        override suspend fun unsuspendCredential(
            credentialId: String,
            statusListId: StatusListId,
        ) = nope()

        override suspend fun checkRevocationStatus(credential: VerifiableCredential) = nope()

        override suspend fun checkStatusByIndex(
            statusListId: StatusListId,
            index: Int,
        ) = nope()

        override suspend fun checkStatusByCredentialId(
            credentialId: String,
            statusListId: StatusListId,
        ) = nope()

        override suspend fun getCredentialIndex(
            credentialId: String,
            statusListId: StatusListId,
        ) = nope()

        override suspend fun assignCredentialIndex(
            credentialId: String,
            statusListId: StatusListId,
            index: Int?,
        ) = nope()

        override suspend fun revokeCredentials(
            credentialIds: List<String>,
            statusListId: StatusListId,
        ) = nope()

        override suspend fun updateStatusListBatch(
            statusListId: StatusListId,
            updates: List<StatusUpdate>,
        ) = nope()

        override suspend fun getStatusListStatistics(statusListId: StatusListId) = nope()

        override suspend fun getStatusList(statusListId: StatusListId) = nope()

        override suspend fun deleteStatusList(statusListId: StatusListId) = nope()

        override suspend fun expandStatusList(
            statusListId: StatusListId,
            additionalSize: Int,
        ) = nope()
    }

    private fun meta(
        id: String,
        issuer: String,
        purpose: StatusPurpose,
        updated: Long,
    ) = StatusListMetadata(StatusListId(id), issuer, purpose, 131072, Instant.fromEpochSeconds(0), Instant.fromEpochSeconds(updated))

    @Test
    fun `the default release releases nothing, so callers must treat false as still allocated`() =
        runTest {
            assertFalse(MinimalManager().releaseStatusListIndex(StatusListId("https://example.com/lists/1"), 0))
        }

    @Test
    fun `findOrCreate reuses the most recently updated list of the same issuer and purpose`() =
        runTest {
            val manager =
                MinimalManager(
                    listOf(
                        meta("https://example.com/lists/old", "did:example:i", StatusPurpose.REVOCATION, 10),
                        meta("https://example.com/lists/newer", "did:example:i", StatusPurpose.REVOCATION, 20),
                        meta("https://example.com/lists/other-purpose", "did:example:i", StatusPurpose.SUSPENSION, 30),
                        meta("https://example.com/lists/other-issuer", "did:example:j", StatusPurpose.REVOCATION, 40),
                    ),
                )
            assertEquals(
                "https://example.com/lists/newer",
                manager.findOrCreateStatusList("did:example:i", StatusPurpose.REVOCATION).value,
            )
            assertTrue(manager.created.isEmpty())
        }

    @Test
    fun `findOrCreate creates a list only when none matches`() =
        runTest {
            val manager = MinimalManager(listOf(meta("https://example.com/lists/a", "did:example:i", StatusPurpose.SUSPENSION, 1)))
            manager.findOrCreateStatusList("did:example:i", StatusPurpose.REVOCATION)
            assertEquals(listOf(Triple<String, StatusPurpose, String?>("did:example:i", StatusPurpose.REVOCATION, null)), manager.created)
        }

    // ------------------------------------------------------------------ options + identifiers

    @Test
    fun `verification options default to the strict settings`() {
        val defaults = VerificationOptions()
        assertTrue(defaults.checkRevocation && defaults.checkExpiration && defaults.checkNotBefore)
        assertEquals(RevocationFailurePolicy.FAIL_CLOSED, defaults.revocationFailurePolicy)
        assertEquals(5.minutes, defaults.clockSkewTolerance)
        assertTrue(defaults.verifyPresentationProof)
        assertFalse(defaults.shouldVerifyChallenge)
        assertFalse(defaults.shouldVerifyDomain)
        assertNull(defaults.expectedChallenge)
    }

    @Test
    fun `an expected challenge or domain switches its check on even without the flag`() {
        assertTrue(VerificationOptions(expectedChallenge = "n").shouldVerifyChallenge)
        assertTrue(VerificationOptions(expectedDomain = "d").shouldVerifyDomain)
        assertTrue(VerificationOptions(verifyChallenge = true).shouldVerifyChallenge)
        assertTrue(VerificationOptions(verifyDomain = true).shouldVerifyDomain)
        // challenge and domain are independent
        assertFalse(VerificationOptions(expectedChallenge = "n").shouldVerifyDomain)
        assertFalse(VerificationOptions(expectedDomain = "d").shouldVerifyChallenge)
    }

    @Test
    fun `copying options keeps the derived check flags consistent`() {
        val copy = VerificationOptions().copy(expectedChallenge = "n", checkRevocation = false)
        assertTrue(copy.shouldVerifyChallenge)
        assertFalse(copy.checkRevocation)
    }

    @Test
    fun `identifiers must be valid IRIs`() {
        assertEquals("https://example.com/lists/1", StatusListId("https://example.com/lists/1").value)
        assertEquals("urn:uuid:1234", CredentialId("urn:uuid:1234").value)
        assertFailsWith<IllegalArgumentException> { StatusListId("not an iri") }
        assertFailsWith<IllegalArgumentException> { CredentialId("") }
    }

    @Test
    fun `status purpose has a lowercase wire form`() {
        assertEquals("revocation", StatusPurpose.REVOCATION.stringValue)
        assertEquals("suspension", StatusPurpose.SUSPENSION.stringValue)
    }
}
