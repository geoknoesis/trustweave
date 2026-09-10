package org.trustweave.revocation.database

import kotlinx.coroutines.*
import org.h2.jdbcx.JdbcDataSource
import org.trustweave.credential.model.StatusPurpose
import java.lang.reflect.Proxy
import java.lang.reflect.InvocationTargetException
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.test.*

class ReviewReadinessProbeTest {
    @Test
    fun `concurrent revocations must both persist`() = runBlocking<Unit> {
        val raw = JdbcDataSource().apply { setURL("jdbc:h2:mem:review-${UUID.randomUUID()};DB_CLOSE_DELAY=-1") }
        val barrier = CyclicBarrier(2)
        var armed = false
        fun invoke(target: Any, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? =
            try { method.invoke(target, *(args ?: emptyArray())) } catch (e: InvocationTargetException) { throw e.targetException }
        val source = object : DataSource by raw {
            override fun getConnection(): Connection {
                val conn = raw.connection
                return Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, m, a ->
                    val result = invoke(conn, m, a)
                    if (m.name == "prepareStatement" && a?.firstOrNull() == "SELECT status_list_data FROM status_lists WHERE id = ?") {
                        val stmt = result as PreparedStatement
                        Proxy.newProxyInstance(PreparedStatement::class.java.classLoader, arrayOf(PreparedStatement::class.java)) { _, sm, sa ->
                            val value = invoke(stmt, sm, sa)
                            if (armed && sm.name == "executeQuery") barrier.await(10, TimeUnit.SECONDS)
                            value
                        }
                    } else result
                } as Connection
            }
        }
        val first = DatabaseStatusListManager(source)
        val second = DatabaseStatusListManager(source)
        val id = first.createStatusList("did:key:issuer", StatusPurpose.REVOCATION, 16, null)
        first.assignCredentialIndex("a", id)
        first.assignCredentialIndex("b", id)
        armed = true
        listOf(async(Dispatchers.IO) { first.revokeCredential("a", id) }, async(Dispatchers.IO) { second.revokeCredential("b", id) }).awaitAll()
        armed = false
        val a = first.checkStatusByCredentialId("a", id).revoked
        val b = first.checkStatusByCredentialId("b", id).revoked
        assertTrue(a && b, "Both completed revocations must persist; a=$a b=$b")
    }

    @Test
    fun `expansion uses declared size`() = runBlocking<Unit> {
        val source = JdbcDataSource().apply { setURL("jdbc:h2:mem:expand-${UUID.randomUUID()};DB_CLOSE_DELAY=-1") }
        val manager = DatabaseStatusListManager(source)
        val id = manager.createStatusList("did:key:issuer", StatusPurpose.REVOCATION, 16, null)
        manager.expandStatusList(id, 8)
        assertEquals(24, manager.getStatusList(id)!!.size)
    }
}
