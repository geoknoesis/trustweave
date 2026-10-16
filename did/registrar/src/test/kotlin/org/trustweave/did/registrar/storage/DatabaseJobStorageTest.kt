package org.trustweave.did.registrar.storage

import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Test
import org.trustweave.did.registrar.model.DidRegistrationResponse
import org.trustweave.did.registrar.model.DidState
import org.trustweave.did.registrar.model.OperationState
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseJobStorageTest {
    private fun dataSource(): DataSource = JdbcDataSource().apply { setURL("jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1") }

    private fun response(state: OperationState) =
        DidRegistrationResponse(jobId = "j", didState = DidState(state = state, did = "did:example:1"))

    private fun present(
        ds: DataSource,
        jobId: String,
    ): Boolean =
        ds.connection.use { conn ->
            conn.prepareStatement("SELECT 1 FROM did_registration_jobs WHERE job_id = ?").use {
                it.setString(1, jobId)
                it.executeQuery().use { rs -> rs.next() }
            }
        }

    private fun age(
        ds: DataSource,
        jobId: String,
        days: Long,
    ) {
        ds.connection.use { conn ->
            conn.prepareStatement("UPDATE did_registration_jobs SET created_at = ?, updated_at = ? WHERE job_id = ?").use {
                val old =
                    Timestamp.from(
                        java.time.Instant
                            .now()
                            .minusSeconds(days * 86_400),
                    )
                it.setTimestamp(1, old)
                it.setTimestamp(2, old)
                it.setString(3, jobId)
                it.executeUpdate()
            }
        }
    }

    @Test
    fun `a stored job round-trips`() {
        val storage = DatabaseJobStorage(dataSource())
        storage.store("a", response(OperationState.WAIT))
        assertEquals(OperationState.WAIT, storage.get("a")?.didState?.state)
        assertTrue(storage.exists("a"))
        assertTrue(storage.remove("a"))
        assertNull(storage.get("a"))
    }

    @Test
    fun `cleanup removes only old finished or failed jobs`() {
        val ds = dataSource()
        val storage = DatabaseJobStorage(ds)
        storage.store("old-finished", response(OperationState.FINISHED))
        storage.store("old-failed", response(OperationState.FAILED))
        storage.store("old-waiting", response(OperationState.WAIT))
        storage.store("new-finished", response(OperationState.FINISHED))
        listOf("old-finished", "old-failed", "old-waiting").forEach { age(ds, it, days = 40) }

        assertEquals(2, storage.cleanupCompletedJobs(olderThanDays = 30))

        assertFalse(present(ds, "old-finished"))
        assertFalse(present(ds, "old-failed"))
        assertTrue(present(ds, "old-waiting"))
        assertTrue(present(ds, "new-finished"))
    }

    @Test
    fun `an expired finished job is not served`() {
        val ds = dataSource()
        val storage = DatabaseJobStorage(ds)
        storage.store("done", response(OperationState.FINISHED))
        age(ds, "done", days = 2)
        assertNull(storage.get("done"))
        assertFalse(storage.exists("done"))
    }

    @Test
    fun `a table name that is not a plain identifier is refused`() {
        assertFailsWith<IllegalArgumentException> { DatabaseJobStorage(dataSource(), "jobs; DROP TABLE x") }
    }
}
