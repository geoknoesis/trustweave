package org.trustweave.credential.revocation

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.model.vc.CredentialStatus
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The revocation manager decides whether a credential still counts, and it was entirely untested.
 *
 * These tests are written against the behaviour that matters to a caller — can a revoked
 * credential come back as valid, can one list's state leak into another, does a purpose mismatch
 * silently succeed — rather than against the implementation's internal maps.
 */
class InMemoryCredentialRevocationManagerTest {
    private val issuer = "did:example:issuer"

    private fun manager() = RevocationManagers.default()

    private fun credential(
        id: String,
        status: CredentialStatus?,
    ) = VerifiableCredential(
        id = CredentialId(id),
        type = listOf(CredentialType.fromString("VerifiableCredential")),
        issuer = Issuer.IriIssuer(Iri(issuer)),
        issuanceDate = Clock.System.now(),
        credentialSubject = CredentialSubject(id = Iri("did:example:subject"), claims = emptyMap()),
        credentialStatus = status,
    )

    // ---------------------------------------------------------------- lifecycle

    @Test
    fun `a revoked credential does not come back as valid`() =
        runBlocking<Unit> {
            val subject = manager()
            val list = subject.createStatusList(issuer, StatusPurpose.REVOCATION, 128)
            assertTrue(subject.checkStatusByCredentialId("cred-1", list).isValid)

            assertTrue(subject.revokeCredential("cred-1", list))
            val revoked = subject.checkStatusByCredentialId("cred-1", list)
            assertTrue(revoked.revoked)
            assertFalse(revoked.isValid)

            assertTrue(subject.unrevokeCredential("cred-1", list))
            assertTrue(subject.checkStatusByCredentialId("cred-1", list).isValid)
        }

    @Test
    fun `suspension is tracked separately from revocation`() =
        runBlocking<Unit> {
            val subject = manager()
            val list = subject.createStatusList(issuer, StatusPurpose.SUSPENSION, 128)
            assertTrue(subject.suspendCredential("cred-1", list))
            val status = subject.checkStatusByCredentialId("cred-1", list)
            assertTrue(status.suspended)
            assertFalse(status.revoked, "a suspension must not read as a revocation")
            assertFalse(status.isValid)

            assertTrue(subject.unsuspendCredential("cred-1", list))
            assertTrue(subject.checkStatusByCredentialId("cred-1", list).isValid)
        }

    // ---------------------------------------------------------------- purpose

    @Test
    fun `an operation against the wrong purpose is refused rather than silently applied`() =
        runBlocking<Unit> {
            val subject = manager()
            val revocationList = subject.createStatusList(issuer, StatusPurpose.REVOCATION, 16)
            val suspensionList = subject.createStatusList(issuer, StatusPurpose.SUSPENSION, 16)

            assertFalse(subject.suspendCredential("cred-1", revocationList), "cannot suspend on a revocation list")
            assertFalse(subject.revokeCredential("cred-1", suspensionList), "cannot revoke on a suspension list")
            assertFalse(subject.unsuspendCredential("cred-1", revocationList))
            assertFalse(subject.unrevokeCredential("cred-1", suspensionList))

            assertTrue(subject.checkStatusByCredentialId("cred-1", revocationList).isValid)
            assertTrue(subject.checkStatusByCredentialId("cred-1", suspensionList).isValid)
        }

    @Test
    fun `a batch update ignores the field that does not match the list purpose`() =
        runBlocking<Unit> {
            val subject = manager()
            val list = subject.createStatusList(issuer, StatusPurpose.REVOCATION, 16)
            subject.updateStatusListBatch(
                list,
                listOf(
                    StatusUpdate(index = 0, revoked = true),
                    // A suspension update on a revocation list changes nothing.
                    StatusUpdate(index = 1, suspended = true),
                ),
            )
            assertTrue(subject.checkStatusByIndex(list, 0).revoked)
            assertTrue(subject.checkStatusByIndex(list, 1).isValid)
        }

    @Test
    fun `a batch update on an unknown list is refused`() =
        runBlocking<Unit> {
            assertFailsWith<IllegalArgumentException> {
                manager().updateStatusListBatch(StatusListId("never-created"), listOf(StatusUpdate(0, revoked = true)))
            }
        }

    // ---------------------------------------------------------------- unknown lists

    @Test
    fun `writes against an unknown status list report false rather than throwing`() =
        runBlocking<Unit> {
            // A write that names a list this manager does not hold changed nothing, and says so.
            // Only the *reads* throw, because only a read has to produce a verdict.
            val subject = manager()
            val unknown = StatusListId("never-created")
            assertFalse(subject.revokeCredential("cred-1", unknown))
            assertFalse(subject.suspendCredential("cred-1", unknown))
            assertFalse(subject.unrevokeCredential("cred-1", unknown))
            assertFalse(subject.unsuspendCredential("cred-1", unknown))
            assertNull(subject.getStatusList(unknown))
            assertNull(subject.getStatusListStatistics(unknown))
            assertFalse(subject.deleteStatusList(unknown))
        }

    @Test
    fun `a status list this manager does not hold is refused, not answered`() =
        runBlocking<Unit> {
            // The manager's state is one JVM's heap, so "I have no such list" almost always means
            // the list lives elsewhere — not that the credential is in good standing. Answering
            // "not revoked" passed the credential *around* RevocationFailurePolicy; throwing hands
            // the decision back to the host's configured policy.
            val subject = manager()
            val unknown = StatusListId("never-created")
            assertFailsWith<IllegalStateException> { subject.checkStatusByIndex(unknown, 7) }
            assertFailsWith<IllegalStateException> { subject.checkStatusByCredentialId("cred-1", unknown) }

            val entry =
                CredentialStatus(
                    id = unknown,
                    type = "BitstringStatusListEntry",
                    statusListIndex = "7",
                    statusListCredential = unknown,
                )
            val failure =
                assertFailsWith<IllegalStateException> {
                    subject.checkRevocationStatus(credential("urn:cred:1", entry))
                }
            assertTrue("never-created" in (failure.message ?: ""), failure.message ?: "")
        }

    @Test
    fun `a known list with no index for the credential is a real answer`() =
        runBlocking<Unit> {
            // This is the case that must NOT throw: the manager holds the list and the credential
            // simply has no position on it, so nothing on that list can have revoked it.
            val subject = manager()
            val list = subject.createStatusList(issuer, StatusPurpose.REVOCATION, 16)
            assertTrue(subject.checkStatusByCredentialId("never-assigned", list).isValid)
        }

    @Test
    fun `unrevoking a credential that was never assigned an index is refused`() =
        runBlocking<Unit> {
            val subject = manager()
            val list = subject.createStatusList(issuer, StatusPurpose.REVOCATION, 16)
            assertFalse(subject.unrevokeCredential("never-seen", list))
        }

    // ---------------------------------------------------------------- indices

    @Test
    fun `an index is assigned once and remains stable`() =
        runBlocking<Unit> {
            val subject = manager()
            val list = subject.createStatusList(issuer, StatusPurpose.REVOCATION, 16)
            subject.revokeCredential("cred-1", list)
            val first = assertNotNull(subject.getCredentialIndex("cred-1", list))
            subject.unrevokeCredential("cred-1", list)
            subject.revokeCredential("cred-1", list)
            assertEquals(first, subject.getCredentialIndex("cred-1", list))
        }

    @Test
    fun `two credentials never share an index`() =
        runBlocking<Unit> {
            val subject = manager()
            val list = subject.createStatusList(issuer, StatusPurpose.REVOCATION, 64)
            val assigned = (1..20).map { subject.assignCredentialIndex("cred-$it", list) }
            assertEquals(20, assigned.toSet().size, "assigned indices must be distinct: $assigned")
        }

    @Test
    fun `an explicit index is honoured and a collision is refused`() =
        runBlocking<Unit> {
            val subject = manager()
            val list = subject.createStatusList(issuer, StatusPurpose.REVOCATION, 64)
            assertEquals(42, subject.assignCredentialIndex("cred-1", list, index = 42))
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    subject.assignCredentialIndex("cred-2", list, index = 42)
                }
            assertTrue("42" in (failure.message ?: ""), failure.message ?: "")
        }

    @Test
    fun `automatic assignment skips an index already taken explicitly`() =
        runBlocking<Unit> {
            val subject = manager()
            val list = subject.createStatusList(issuer, StatusPurpose.REVOCATION, 64)
            subject.assignCredentialIndex("explicit", list, index = 0)
            val automatic = subject.assignCredentialIndex("automatic", list)
            assertTrue(automatic != 0, "automatic assignment must not reuse index 0")
            assertEquals("explicit", subject.getCredentialIndex("explicit", list)?.let { "explicit" })
        }

    @Test
    fun `indices are scoped to their status list`() =
        runBlocking<Unit> {
            val subject = manager()
            val first = subject.createStatusList(issuer, StatusPurpose.REVOCATION, 16)
            val second = subject.createStatusList(issuer, StatusPurpose.REVOCATION, 16)
            subject.revokeCredential("cred-1", first)

            assertTrue(subject.checkStatusByCredentialId("cred-1", first).revoked)
            assertTrue(
                subject.checkStatusByCredentialId("cred-1", second).isValid,
                "revoking in one list must not revoke the same credential id in another",
            )
            assertNull(subject.getCredentialIndex("cred-1", second))
        }

    // ---------------------------------------------------------------- credential-driven checks

    @Test
    fun `a credential with no status is not revoked`() =
        runBlocking<Unit> {
            val status = manager().checkRevocationStatus(credential("urn:cred:1", status = null))
            assertTrue(status.isValid)
            assertNull(status.statusListId)
        }

    @Test
    fun `a credential is resolved through its statusListIndex`() =
        runBlocking<Unit> {
            val subject = manager()
            val list = subject.createStatusList(issuer, StatusPurpose.REVOCATION, 128)
            subject.updateStatusListBatch(list, listOf(StatusUpdate(index = 9, revoked = true)))

            val entry =
                CredentialStatus(
                    id = list,
                    type = "BitstringStatusListEntry",
                    statusPurpose = StatusPurpose.REVOCATION,
                    statusListIndex = "9",
                    statusListCredential = list,
                )
            assertTrue(subject.checkRevocationStatus(credential("urn:cred:1", entry)).revoked)

            val other = entry.copy(statusListIndex = "10")
            assertTrue(subject.checkRevocationStatus(credential("urn:cred:2", other)).isValid)
        }

    @Test
    fun `a credential with no index falls back to its own id`() =
        runBlocking<Unit> {
            val subject = manager()
            val list = subject.createStatusList(issuer, StatusPurpose.REVOCATION, 128)
            subject.revokeCredential("urn:cred:1", list)

            val entry =
                CredentialStatus(
                    id = list,
                    type = "BitstringStatusListEntry",
                    statusListIndex = null,
                    statusListCredential = list,
                )
            assertTrue(subject.checkRevocationStatus(credential("urn:cred:1", entry)).revoked)
            assertTrue(subject.checkRevocationStatus(credential("urn:cred:unknown", entry)).isValid)
        }

    @Test
    fun `a non-numeric statusListIndex falls back rather than throwing`() =
        runBlocking<Unit> {
            val subject = manager()
            val list = subject.createStatusList(issuer, StatusPurpose.REVOCATION, 128)
            subject.revokeCredential("urn:cred:1", list)
            val entry =
                CredentialStatus(
                    id = list,
                    type = "BitstringStatusListEntry",
                    statusListIndex = "not-a-number",
                    statusListCredential = list,
                )
            assertTrue(subject.checkRevocationStatus(credential("urn:cred:1", entry)).revoked)
        }

    // ---------------------------------------------------------------- bulk and administration

    @Test
    fun `a bulk revocation revokes every credential it reports true for`() =
        runBlocking<Unit> {
            val subject = manager()
            val list = subject.createStatusList(issuer, StatusPurpose.REVOCATION, 64)
            val ids = (1..5).map { "cred-$it" }
            val results = subject.revokeCredentials(ids, list)
            assertEquals(ids.toSet(), results.filterValues { it }.keys)
            ids.forEach { assertTrue(subject.checkStatusByCredentialId(it, list).revoked, "$it must be revoked") }
        }

    @Test
    fun `a bulk revocation against the wrong purpose reports false for every credential`() =
        runBlocking<Unit> {
            val subject = manager()
            val list = subject.createStatusList(issuer, StatusPurpose.SUSPENSION, 64)
            val results = subject.revokeCredentials(listOf("a", "b"), list)
            assertEquals(mapOf("a" to false, "b" to false), results)
        }

    @Test
    fun `statistics count what is used and what is revoked`() =
        runBlocking<Unit> {
            val subject = manager()
            val list = subject.createStatusList(issuer, StatusPurpose.REVOCATION, 100)
            subject.revokeCredentials(listOf("a", "b", "c"), list)
            subject.assignCredentialIndex("d", list)

            val stats = assertNotNull(subject.getStatusListStatistics(list))
            assertEquals(100, stats.totalCapacity)
            assertEquals(4, stats.usedIndices, "three revoked plus one assigned but not revoked")
            assertEquals(3, stats.revokedCount)
            assertEquals(0, stats.suspendedCount, "a revocation list reports no suspensions")
            assertEquals(96, stats.availableIndices)
            assertEquals(issuer, stats.issuerDid)
        }

    @Test
    fun `listing is filtered by issuer`() =
        runBlocking<Unit> {
            val subject = manager()
            subject.createStatusList(issuer, StatusPurpose.REVOCATION, 8, customId = "mine")
            subject.createStatusList("did:example:other", StatusPurpose.REVOCATION, 8, customId = "theirs")

            assertEquals(2, subject.listStatusLists().size)
            assertEquals(listOf(StatusListId("mine")), subject.listStatusLists(issuer).map { it.id })
            assertEquals(emptyList(), subject.listStatusLists("did:example:nobody"))
        }

    @Test
    fun `a custom id is the list id`() =
        runBlocking<Unit> {
            val subject = manager()
            val list = subject.createStatusList(issuer, StatusPurpose.REVOCATION, 8, customId = "2026-q1")
            assertEquals(StatusListId("2026-q1"), list)
            val metadata = assertNotNull(subject.getStatusList(list))
            assertEquals(8, metadata.size)
            assertEquals(StatusPurpose.REVOCATION, metadata.purpose)
        }

    @Test
    fun `deleting a list forgets its revocations`() =
        runBlocking<Unit> {
            val subject = manager()
            val list = subject.createStatusList(issuer, StatusPurpose.REVOCATION, 16, customId = "temp")
            subject.revokeCredential("cred-1", list)
            assertTrue(subject.checkStatusByCredentialId("cred-1", list).revoked)

            assertTrue(subject.deleteStatusList(list))
            assertFalse(subject.deleteStatusList(list), "a second delete reports nothing was removed")
            assertNull(subject.getStatusList(list))
            assertFailsWith<IllegalStateException>(
                "a deleted list can say nothing about a credential, and must not pretend otherwise",
            ) { subject.checkStatusByCredentialId("cred-1", list) }
        }

    @Test
    fun `expanding a list keeps the revocations it already holds`() =
        runBlocking<Unit> {
            val subject = manager()
            val list = subject.createStatusList(issuer, StatusPurpose.REVOCATION, 16)
            subject.updateStatusListBatch(list, listOf(StatusUpdate(index = 3, revoked = true)))

            subject.expandStatusList(list, additionalSize = 48)

            assertEquals(64, assertNotNull(subject.getStatusList(list)).size)
            assertTrue(subject.checkStatusByIndex(list, 3).revoked, "expansion must not drop existing state")
            assertTrue(subject.checkStatusByIndex(list, 50).isValid)
        }

    @Test
    fun `expanding a suspension list keeps its suspensions`() =
        runBlocking<Unit> {
            val subject = manager()
            val list = subject.createStatusList(issuer, StatusPurpose.SUSPENSION, 16)
            subject.updateStatusListBatch(list, listOf(StatusUpdate(index = 2, suspended = true)))
            subject.expandStatusList(list, additionalSize = 16)
            assertTrue(subject.checkStatusByIndex(list, 2).suspended)
        }

    @Test
    fun `expanding an unknown list is refused`() =
        runBlocking<Unit> {
            assertFailsWith<IllegalArgumentException> {
                manager().expandStatusList(StatusListId("never-created"), additionalSize = 8)
            }
        }

    @Test
    fun `an update bumps the list's lastUpdated`() =
        runBlocking<Unit> {
            val subject = manager()
            val list = subject.createStatusList(issuer, StatusPurpose.REVOCATION, 16)
            val before = assertNotNull(subject.getStatusList(list)).lastUpdated
            subject.revokeCredential("cred-1", list)
            val after = assertNotNull(subject.getStatusList(list)).lastUpdated
            assertTrue(after >= before, "revoking must not move lastUpdated backwards")
        }
}
