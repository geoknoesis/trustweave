package org.trustweave.credential.avpauth.state

import kotlinx.coroutines.test.runTest
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.sql.Connection
import java.sql.SQLException
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A pooled connection must go back as it was found. Uses a stub connection that fails its first
 * statement, so no database is needed; the guarantees against a real server are the Testcontainers
 * suite's job.
 */
class PostgresConnectionStateTest {
    private class State {
        var autoCommit = true
        var isolation = Connection.TRANSACTION_REPEATABLE_READ
        var closed = false
    }

    private fun source(state: State): DataSource {
        val connection =
            Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
                when (method.name) {
                    "getAutoCommit" -> state.autoCommit
                    "setAutoCommit" -> state.autoCommit = args[0] as Boolean
                    "getTransactionIsolation" -> state.isolation
                    "setTransactionIsolation" -> state.isolation = args[0] as Int
                    "close" -> state.closed = true
                    "rollback" -> Unit
                    "createStatement", "prepareStatement" -> throw SQLException("boom", "08006")
                    else -> throw UnsupportedOperationException(method.name)
                }
            } as Connection
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(DataSource::class.java)) { _, method, _ ->
            if (method.name == "getConnection") connection else throw UnsupportedOperationException(method.name)
        } as DataSource
    }

    @Test
    fun `admit restores autoCommit and isolation when the transaction fails`() =
        runTest {
            val state = State()
            val request =
                AdmissionRequest(
                    "c",
                    "n",
                    "a",
                    "p",
                    BigDecimal("1"),
                    null,
                    Instant.parse("2026-03-25T21:30:30Z"),
                    Instant.parse("2026-03-25T21:40:30Z"),
                )
            val admission = PostgresAuthorizationStore(source(state)).admit(request)
            assertEquals(StatefulRejection.STORE_UNAVAILABLE, (admission as Admission.Refused).rejection)
            assertTrue(state.autoCommit, "autoCommit must be restored")
            assertEquals(Connection.TRANSACTION_REPEATABLE_READ, state.isolation, "isolation must be restored")
            assertTrue(state.closed)
        }

    @Test
    fun `initializeSchema restores autoCommit when it fails`() {
        val state = State()
        try {
            PostgresAuthorizationStore(source(state)).initializeSchema()
        } catch (expected: SQLException) {
            // expected
        } catch (expected: InvocationTargetException) {
            // expected
        }
        assertTrue(state.autoCommit)
        assertEquals(Connection.TRANSACTION_REPEATABLE_READ, state.isolation)
    }
}
