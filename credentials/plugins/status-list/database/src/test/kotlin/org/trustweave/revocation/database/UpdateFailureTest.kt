package org.trustweave.revocation.database

import kotlinx.coroutines.runBlocking
import org.h2.jdbcx.JdbcDataSource
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.StatusPurpose
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateFailureTest {
    private fun source() = JdbcDataSource().apply { setURL("jdbc:h2:mem:updfail-${UUID.randomUUID()};DB_CLOSE_DELAY=-1") }

    /** A DataSource whose connections fail the status-list UPDATE with an [SQLException] while [failing] is set. */
    private class FlakyDataSource(
        private val delegate: DataSource,
    ) : DataSource by delegate {
        @Volatile var failing = false

        override fun getConnection(): Connection {
            val real = delegate.connection
            return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
                if (failing && method.name == "prepareStatement" && (args?.get(0) as? String)?.contains("UPDATE status_lists") == true) {
                    throw SQLException("simulated connection loss")
                }
                try {
                    method.invoke(real, *(args ?: emptyArray()))
                } catch (e: InvocationTargetException) {
                    throw e.targetException
                }
            } as Connection
        }
    }

    @Test
    fun `a database error is surfaced, not reported as not applied`() =
        runBlocking<Unit> {
            val flaky = FlakyDataSource(source())
            val manager = DatabaseStatusListManager(flaky)
            val id = manager.createStatusList("did:key:issuer", StatusPurpose.REVOCATION, 16, null)
            manager.assignCredentialIndex("a", id)
            flaky.failing = true
            val e = assertFailsWith<TrustWeaveException.InvalidState> { manager.revokeCredential("a", id) }
            assertTrue(e.cause is SQLException)
            flaky.failing = false
            assertFalse(manager.checkStatusByIndex(id, 0).revoked, "failed update must have rolled back")
            assertTrue(manager.revokeCredential("a", id))
        }

    @Test
    fun `an unknown status list or a purpose mismatch still returns false`() =
        runBlocking<Unit> {
            val manager = DatabaseStatusListManager(source())
            assertFalse(manager.revokeCredential("a", StatusListId("urn:uuid:does-not-exist")))
            val suspension = manager.createStatusList("did:key:issuer", StatusPurpose.SUSPENSION, 16, null)
            assertFalse(manager.revokeCredential("a", suspension))
        }
}
