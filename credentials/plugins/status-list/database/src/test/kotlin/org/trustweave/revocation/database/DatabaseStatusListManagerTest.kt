package org.trustweave.revocation.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.h2.jdbcx.JdbcDataSource
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.StatusPurpose
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseStatusListManagerTest {
    @Test
    fun `revocation persists and unknown or out of range status never reports active`() =
        runBlocking<Unit> {
            val source = JdbcDataSource().apply { setURL("jdbc:h2:mem:status-${UUID.randomUUID()};DB_CLOSE_DELAY=-1") }
            val manager = DatabaseStatusListManager(source)
            val id = manager.createStatusList("did:key:issuer", StatusPurpose.REVOCATION, 16, null)
            val index = manager.assignCredentialIndex("credential", id)
            assertFalse(manager.checkStatusByIndex(id, index).revoked)
            assertEquals(index, manager.assignCredentialIndex("credential", id))
            assertFailsWith<IllegalArgumentException> { manager.assignCredentialIndex("other", id, index) }
            assertFailsWith<IllegalArgumentException> { manager.assignCredentialIndex("credential", id, index + 1) }
            assertFailsWith<IllegalArgumentException> { manager.assignCredentialIndex("invalid", id, 16) }

            manager.revokeCredential("credential", id)
            assertTrue(DatabaseStatusListManager(source).checkStatusByCredentialId("credential", id).revoked)
            assertFailsWith<IllegalArgumentException> { manager.checkStatusByIndex(StatusListId("missing"), 0) }
            assertFailsWith<IllegalArgumentException> { manager.checkStatusByCredentialId("unknown", id) }
            for (slot in 1 until 16) manager.assignCredentialIndex("credential-$slot", id)
            assertFailsWith<IllegalArgumentException> { manager.assignCredentialIndex("overflow", id) }
            for (invalid in listOf(-1, 16, Int.MAX_VALUE)) {
                assertFailsWith<IllegalArgumentException> { manager.checkStatusByIndex(id, invalid) }
            }
        }

    @Test
    fun `concurrent allocation across managers preserves unique and stable indices`() =
        runBlocking<Unit> {
            val source = JdbcDataSource().apply { setURL("jdbc:h2:mem:concurrent-${UUID.randomUUID()};DB_CLOSE_DELAY=-1") }
            val first = DatabaseStatusListManager(source)
            val second = DatabaseStatusListManager(source)
            val id = first.createStatusList("did:key:issuer", StatusPurpose.REVOCATION, 32, null)
            val indices =
                (0 until 32)
                    .map { number ->
                        async(Dispatchers.IO) {
                            val manager = if (number % 2 == 0) first else second
                            manager.assignCredentialIndex("credential-$number", id)
                        }
                    }.awaitAll()
            assertEquals((0 until 32).toSet(), indices.toSet())
            val repeated =
                (0 until 8)
                    .map {
                        async(Dispatchers.IO) { second.assignCredentialIndex("credential-0", id) }
                    }.awaitAll()
            assertTrue(repeated.all { it == indices[0] })
        }
}
