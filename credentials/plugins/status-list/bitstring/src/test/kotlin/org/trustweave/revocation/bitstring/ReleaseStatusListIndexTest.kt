package org.trustweave.revocation.bitstring

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.revocation.StatusUpdate
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `releaseStatusListIndex`: only a never-issued, never-touched index may be handed back. */
class ReleaseStatusListIndexTest {
    private val dataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:release_idx_${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
                username = "sa"
                password = ""
                maximumPoolSize = 5
            },
        )
    private val manager = BitstringStatusListManager(dataSource, InMemoryKeyManagementService(), "did:example:issuer")

    @AfterTest
    fun tearDown() = dataSource.close()

    @Test
    fun `a never-issued index is released and reused by the next allocation`() =
        runBlocking<Unit> {
            val list = manager.createStatusList("did:example:issuer", StatusPurpose.REVOCATION)
            val a = manager.assignCredentialIndex("cred-a", list, null)
            val b = manager.assignCredentialIndex("cred-b", list, null)
            assertEquals(0, a)
            assertEquals(1, b)

            assertTrue(manager.releaseStatusListIndex(list, a))
            assertEquals(null, manager.getCredentialIndex("cred-a", list))

            // The freed slot is handed out again before the counter advances.
            assertEquals(0, manager.assignCredentialIndex("cred-c", list, null))
            assertEquals(2, manager.assignCredentialIndex("cred-d", list, null))
        }

    @Test
    fun `release is idempotent and refuses unknown lists, unallocated and out-of-range indices`() =
        runBlocking<Unit> {
            val list = manager.createStatusList("did:example:issuer", StatusPurpose.REVOCATION)
            val idx = manager.assignCredentialIndex("cred-a", list, null)
            assertTrue(manager.releaseStatusListIndex(list, idx))
            assertFalse(manager.releaseStatusListIndex(list, idx), "second release must be a no-op")
            assertFalse(manager.releaseStatusListIndex(list, 500), "never allocated")
            assertFalse(manager.releaseStatusListIndex(list, -1))
            assertFalse(manager.releaseStatusListIndex(list, Int.MAX_VALUE))
            assertFalse(
                manager.releaseStatusListIndex(
                    org.trustweave.credential.identifiers
                        .StatusListId("no-such-list"),
                    0,
                ),
            )
        }

    @Test
    fun `an index whose bit is set is never released`() =
        runBlocking<Unit> {
            val list = manager.createStatusList("did:example:issuer", StatusPurpose.REVOCATION)
            val idx = manager.assignCredentialIndex("cred-a", list, null)
            manager.revokeCredential("cred-a", list)
            assertFalse(manager.releaseStatusListIndex(list, idx))
            assertTrue(manager.checkStatusByIndex(list, idx).revoked)
        }

    @Test
    fun `an index that was revoked and then restored is still never released`() =
        runBlocking<Unit> {
            val list = manager.createStatusList("did:example:issuer", StatusPurpose.REVOCATION)
            val idx = manager.assignCredentialIndex("cred-a", list, null)
            manager.revokeCredential("cred-a", list)
            manager.unrevokeCredential("cred-a", list)
            assertFalse(manager.checkStatusByIndex(list, idx).revoked)
            // The bit is clear again, but the credential demonstrably existed: no reuse.
            assertFalse(manager.releaseStatusListIndex(list, idx))
            assertEquals(1, manager.assignCredentialIndex("cred-b", list, null))
        }

    @Test
    fun `a batch update marks the index as used too`() =
        runBlocking<Unit> {
            val list = manager.createStatusList("did:example:issuer", StatusPurpose.REVOCATION)
            val idx = manager.assignCredentialIndex("cred-a", list, null)
            manager.updateStatusListBatch(list, listOf(StatusUpdate(index = idx, revoked = true)))
            manager.updateStatusListBatch(list, listOf(StatusUpdate(index = idx, revoked = false)))
            assertFalse(manager.releaseStatusListIndex(list, idx))
        }

    @Test
    fun `bulk revocation marks the index as used`() =
        runBlocking<Unit> {
            val list = manager.createStatusList("did:example:issuer", StatusPurpose.REVOCATION)
            val idx = manager.assignCredentialIndex("cred-a", list, null)
            manager.revokeCredentials(listOf("cred-a"), list)
            manager.unrevokeCredential("cred-a", list)
            assertFalse(manager.releaseStatusListIndex(list, idx))
        }

    @Test
    fun `an explicitly assigned index drops out of the free list`() =
        runBlocking<Unit> {
            val list = manager.createStatusList("did:example:issuer", StatusPurpose.REVOCATION)
            val idx = manager.assignCredentialIndex("cred-a", list, null)
            assertTrue(manager.releaseStatusListIndex(list, idx))
            manager.assignCredentialIndex("cred-explicit", list, idx)
            // The explicit holder keeps the slot; the next automatic allocation must not collide.
            assertEquals(1, manager.assignCredentialIndex("cred-b", list, null))
            assertEquals(idx, manager.getCredentialIndex("cred-explicit", list))
        }
}
