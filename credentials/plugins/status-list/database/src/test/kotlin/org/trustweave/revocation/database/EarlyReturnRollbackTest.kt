package org.trustweave.revocation.database

import kotlinx.coroutines.runBlocking
import org.h2.jdbcx.JdbcDataSource
import org.trustweave.credential.model.StatusPurpose
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** A refused update holds the status list's row lock; it must be rolled back, not left to the pool. */
class EarlyReturnRollbackTest {
    private val rollbacks = AtomicInteger()

    private fun counting(delegate: DataSource): DataSource =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(DataSource::class.java)) { _, method, args ->
            try {
                val result = method.invoke(delegate, *(args ?: emptyArray()))
                if (method.name == "getConnection" && result is Connection) {
                    Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Connection::class.java)) { _, m, a ->
                        if (m.name == "rollback") rollbacks.incrementAndGet()
                        try {
                            m.invoke(result, *(a ?: emptyArray()))
                        } catch (e: InvocationTargetException) {
                            throw e.targetException
                        }
                    }
                } else {
                    result
                }
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        } as DataSource

    @Test
    fun `an update refused for purpose mismatch rolls back`() =
        runBlocking<Unit> {
            val source = JdbcDataSource().apply { setURL("jdbc:h2:mem:rollback-${UUID.randomUUID()};DB_CLOSE_DELAY=-1") }
            val manager = DatabaseStatusListManager(counting(source))
            val id = manager.createStatusList("did:key:issuer", StatusPurpose.REVOCATION, 16, null)
            val before = rollbacks.get()
            assertFalse(manager.suspendCredential("c", id), "a revocation list cannot record a suspension")
            assertEquals(before + 1, rollbacks.get(), "the refused update must roll back its transaction")
        }
}
