package org.trustweave.revocation.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.h2.jdbcx.JdbcDataSource
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.revocation.StatusUpdate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusMutationTest {
    private fun source() = JdbcDataSource().apply { setURL("jdbc:h2:mem:mutation-${UUID.randomUUID()};DB_CLOSE_DELAY=-1") }

    @Test
    fun `concurrent single and batch revocations survive across managers`() =
        runBlocking<Unit> {
            val source = source()
            val managers = listOf(DatabaseStatusListManager(source), DatabaseStatusListManager(source))
            repeat(8) {
                val id = managers[0].createStatusList("did:key:issuer", StatusPurpose.REVOCATION, 32, null)
                (0 until 32).forEach { managers[0].assignCredentialIndex("credential-$it", id) }
                (0 until 32)
                    .map { n ->
                        async(Dispatchers.IO) {
                            if (n % 2 == 0) {
                                assertTrue(managers[0].revokeCredential("credential-$n", id))
                            } else {
                                assertTrue(managers[1].revokeCredentials(listOf("credential-$n"), id).values.all { it })
                            }
                        }
                    }.awaitAll()
                (0 until 32).forEach { assertTrue(managers[0].checkStatusByIndex(id, it).revoked, "Lost index $it") }
            }
        }

    @Test
    fun `expansion preserves exact size and rejects invalid growth`() =
        runBlocking<Unit> {
            val manager = DatabaseStatusListManager(source())
            val id = manager.createStatusList("did:key:issuer", StatusPurpose.REVOCATION, 16, null)
            manager.assignCredentialIndex("a", id, 15)
            manager.revokeCredential("a", id)
            manager.expandStatusList(id, 8)
            assertEquals(24, manager.getStatusList(id)!!.size)
            assertTrue(manager.checkStatusByIndex(id, 15).revoked)
            for (growth in listOf(0, -1, Int.MAX_VALUE)) assertFailsWith<IllegalArgumentException> { manager.expandStatusList(id, growth) }
            assertEquals(24, manager.getStatusList(id)!!.size)
        }

    @Test
    fun `invalid batches and full-list allocation roll back completely`() =
        runBlocking<Unit> {
            val manager = DatabaseStatusListManager(source())
            val id = manager.createStatusList("did:key:issuer", StatusPurpose.REVOCATION, 2, null)
            manager.assignCredentialIndex("a", id, 0)
            for (index in listOf(-1, 2, Int.MAX_VALUE)) {
                assertFailsWith<IllegalArgumentException> {
                    manager.updateStatusListBatch(id, listOf(StatusUpdate(0, revoked = true), StatusUpdate(index, revoked = true)))
                }
                assertFalse(manager.checkStatusByIndex(id, 0).revoked)
            }
            assertTrue(manager.revokeCredentials(listOf("a", "b", "c"), id).values.none { it })
            assertFalse(manager.checkStatusByIndex(id, 0).revoked)
            assertEquals(1, manager.assignCredentialIndex("replacement", id))
        }
}
