package org.trustweave.revocation.token

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.revocation.StatusUpdate
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Concurrency and integrity of the token status list store, against H2 in PostgreSQL mode.
 *
 * H2 honours `SELECT ... FOR UPDATE` row locks (MVCC), so the lost-update and duplicate-index races
 * are reproducible here. What only a real PostgreSQL proves: lock behaviour under its READ COMMITTED
 * re-check semantics and the exact error code of a unique-index violation.
 */
class TokenStatusListConcurrencyTest {
    private lateinit var dataSource: DataSource
    private val issuerDid = "did:key:z6MkTestTokenIssuer"

    private fun manager(
        bits: Int = 1,
        ds: DataSource = dataSource,
    ) = TokenStatusListManager(ds, InMemoryKeyManagementService(), issuerDid, "https://example.com/sl", bits)

    @BeforeTest
    fun setUp() {
        dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = "jdbc:h2:mem:token_conc_${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
                    username = "sa"
                    password = ""
                    maximumPoolSize = 16
                },
            )
    }

    @AfterTest
    fun tearDown() {
        (dataSource as? HikariDataSource)?.close()
    }

    @Test
    fun `concurrent revocations of different credentials all land`() =
        runBlocking<Unit> {
            val m = manager()
            val id = m.createStatusList(issuerDid, StatusPurpose.REVOCATION, size = 256)
            val creds = (0 until 48).map { "cred-$it" }
            coroutineScope { creds.map { c -> async(Dispatchers.IO) { m.revokeCredential(c, id) } }.awaitAll() }
            for (c in creds) {
                assertTrue(m.checkStatusByCredentialId(c, id).revoked, "revocation of $c was lost")
            }
        }

    @Test
    fun `concurrent allocation hands out distinct indices`() =
        runBlocking<Unit> {
            val m = manager()
            val id = m.createStatusList(issuerDid, StatusPurpose.REVOCATION, size = 256)
            val indices =
                coroutineScope {
                    (0 until 48).map { i -> async(Dispatchers.IO) { m.assignCredentialIndex("c-$i", id) } }.awaitAll()
                }
            assertEquals(48, indices.toSet().size, "duplicate indices allocated: ${indices.sorted()}")
        }

    @Test
    fun `the database refuses two credentials on one index`() =
        runBlocking<Unit> {
            val m = manager()
            val id = m.createStatusList(issuerDid, StatusPurpose.REVOCATION, size = 16)
            m.assignCredentialIndex("a", id, 3)
            assertFailsWith<SQLException> {
                dataSource.connection.use { c ->
                    c
                        .prepareStatement(
                            "INSERT INTO token_credential_indices (credential_id, status_list_id, entry_index) VALUES ('b', ?, 3)",
                        ).apply { setString(1, id.toString()) }
                        .executeUpdate()
                }
            }
        }

    @Test
    fun `an explicit index outside the list is refused`() =
        runBlocking<Unit> {
            val m = manager()
            val id = m.createStatusList(issuerDid, StatusPurpose.REVOCATION, size = 8)
            assertFailsWith<IllegalArgumentException> { m.assignCredentialIndex("neg", id, -1) }
            assertFailsWith<IllegalArgumentException> { m.assignCredentialIndex("big", id, 8) }
            assertEquals(7, m.assignCredentialIndex("edge", id, 7))
        }

    @Test
    fun `automatic allocation skips an explicitly taken index`() =
        runBlocking<Unit> {
            val m = manager()
            val id = m.createStatusList(issuerDid, StatusPurpose.REVOCATION, size = 8)
            m.assignCredentialIndex("pinned", id, 0)
            assertEquals(1, m.assignCredentialIndex("auto", id))
        }

    @Test
    fun `allocation beyond capacity is refused`() =
        runBlocking<Unit> {
            val m = manager()
            val id = m.createStatusList(issuerDid, StatusPurpose.REVOCATION, size = 8)
            repeat(8) { m.assignCredentialIndex("c$it", id) }
            assertFailsWith<Exception> { m.assignCredentialIndex("overflow", id) }
        }

    @Test
    fun `a batch with an out-of-range update applies nothing`() =
        runBlocking<Unit> {
            val m = manager()
            val id = m.createStatusList(issuerDid, StatusPurpose.REVOCATION, size = 8)
            assertFailsWith<IllegalArgumentException> {
                m.updateStatusListBatch(id, listOf(StatusUpdate(index = 1, revoked = true), StatusUpdate(index = 99, revoked = true)))
            }
            assertTrue(!m.checkStatusByIndex(id, 1).revoked)
        }

    @Test
    fun `concurrent batch updates and expansion do not lose each other`() =
        runBlocking<Unit> {
            val m = manager()
            val id = m.createStatusList(issuerDid, StatusPurpose.REVOCATION, size = 64)
            coroutineScope {
                val updates =
                    (0 until 32).map { i ->
                        async(Dispatchers.IO) { m.updateStatusListBatch(id, listOf(StatusUpdate(index = i, revoked = true))) }
                    }
                val expand = async(Dispatchers.IO) { m.expandStatusList(id, 64) }
                (updates + expand).awaitAll()
            }
            for (i in 0 until 32) assertTrue(m.checkStatusByIndex(id, i).revoked, "batch update of $i was lost")
            assertEquals(128, m.getStatusList(id)!!.size)
        }

    @Test
    fun `the stored bits per entry wins over the instance setting`() =
        runBlocking<Unit> {
            val twoBit = manager(bits = 2)
            val id = twoBit.createStatusList(issuerDid, StatusPurpose.REVOCATION, size = 8)
            // A differently configured instance on the same database must not misread or corrupt the list.
            val oneBit = manager(bits = 1)
            assertTrue(oneBit.suspendCredential("s", id), "2-bit list must accept a suspension through a 1-bit instance")
            val status = oneBit.checkStatusByCredentialId("s", id)
            assertTrue(status.suspended)
            assertTrue(!status.revoked)
            oneBit.revokeCredential("r", id)
            assertTrue(twoBit.checkStatusByCredentialId("r", id).revoked)
            assertTrue(!twoBit.checkStatusByCredentialId("s", id).revoked)
        }

    @Test
    fun `a token reports the stored bits per entry`() =
        runBlocking<Unit> {
            val twoBit = manager(bits = 2)
            val id = twoBit.createStatusList(issuerDid, StatusPurpose.REVOCATION, size = 8)
            val stats = manager(bits = 1).getStatusListStatistics(id)!!
            assertEquals(8, stats.totalCapacity)
        }
}
