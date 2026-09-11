package org.trustweave.revocation.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.trustweave.credential.model.StatusPurpose
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostgresStatusMutationTest {
    @Test
    fun `postgres concurrent revocations and rollback preserve status`() =
        runBlocking<Unit> {
            PostgreSQLContainer<Nothing>("postgres:16-alpine").use { container ->
                container.start()
                val source =
                    PGSimpleDataSource().apply {
                        setURL(container.jdbcUrl)
                        user = container.username
                        password = container.password
                    }
                val first = DatabaseStatusListManager(source)
                val second = DatabaseStatusListManager(source)
                val id = first.createStatusList("did:key:issuer", StatusPurpose.REVOCATION, 32, null)
                (0 until 32).forEach { first.assignCredentialIndex("c-$it", id) }
                (0 until 32)
                    .map { n ->
                        async(Dispatchers.IO) {
                            assertTrue((if (n % 2 == 0) first else second).revokeCredential("c-$n", id))
                        }
                    }.awaitAll()
                (0 until 32).forEach { assertTrue(second.checkStatusByIndex(id, it).revoked) }
                first.expandStatusList(id, 8)
                assertEquals(40, second.getStatusList(id)!!.size)
                val full = first.createStatusList("did:key:issuer", StatusPurpose.REVOCATION, 1, null)
                first.assignCredentialIndex("a", full)
                assertTrue(second.revokeCredentials(listOf("a", "b"), full).values.none { it })
                assertFalse(first.checkStatusByIndex(full, 0).revoked)
                source.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute(
                            """
                            CREATE FUNCTION reject_expansion() RETURNS trigger AS $$
                            BEGIN
                                IF NEW.size <> OLD.size THEN RAISE EXCEPTION 'injected expansion failure'; END IF;
                                RETURN NEW;
                            END; $$ LANGUAGE plpgsql
                            """.trimIndent(),
                        )
                        statement.execute(
                            """
                            CREATE TRIGGER reject_expansion BEFORE UPDATE ON status_lists
                            FOR EACH ROW EXECUTE FUNCTION reject_expansion()
                            """.trimIndent(),
                        )
                    }
                }

                fun encoded(): String =
                    source.connection.use { connection ->
                        connection.prepareStatement("SELECT encoded_list FROM status_lists WHERE id = ?").use { statement ->
                            statement.setString(1, id.toString())
                            statement.executeQuery().use { rows ->
                                check(rows.next())
                                rows.getString(1)
                            }
                        }
                    }
                val before = encoded()
                assertFailsWith<java.sql.SQLException> { first.expandStatusList(id, 64) }
                assertEquals(40, second.getStatusList(id)!!.size)
                assertEquals(before, encoded(), "Failed metadata update must roll back bitmap expansion")
            }
        }
}
