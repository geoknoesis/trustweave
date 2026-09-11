package org.trustweave.credential.vi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.trustweave.credential.vi.crypto.sha256B64Url
import org.trustweave.credential.vi.verification.BudgetReservation
import org.trustweave.credential.vi.verification.PostgresIntentLedger
import java.sql.SQLException

class PostgresIntentLedgerTest {
    private fun hash(value: String) = sha256B64Url(value.toByteArray())

    @Test
    fun `concurrent authorization restart replay and rollback are durable`() =
        runBlocking<Unit> {
            PostgreSQLContainer<Nothing>("postgres:16-alpine").use { container ->
                container.start()
                val source =
                    PGSimpleDataSource().apply {
                        setURL(container.jdbcUrl)
                        user = container.username
                        password = container.password
                    }
                // Upgrade the preceding ledger schema with an existing reservation; its age is unknown.
                source.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute(
                            "CREATE TABLE vi_budget_accounts(scope VARCHAR(43) PRIMARY KEY, currency VARCHAR(3) NOT NULL, " +
                                "maximum BIGINT NOT NULL, spent BIGINT NOT NULL)",
                        )
                        statement.execute(
                            "CREATE TABLE vi_budget_reservations(scope VARCHAR(43) REFERENCES vi_budget_accounts(scope), " +
                                "transaction_hash VARCHAR(43), challenge_hash VARCHAR(43) UNIQUE, amount BIGINT NOT NULL, " +
                                "PRIMARY KEY(scope,transaction_hash))",
                        )
                        statement.execute("INSERT INTO vi_budget_accounts VALUES ('${hash("legacy")}', 'USD', 100, 5)")
                        statement.execute(
                            "INSERT INTO vi_budget_reservations VALUES " +
                                "('${hash("legacy")}', '${hash("legacy")}', '${hash("legacy")}', 5)",
                        )
                    }
                }
                val first = PostgresIntentLedger(source)
                first.initializeSchema()
                assertEquals(1, first.healthSnapshot().pending)
                assertEquals(null, first.healthSnapshot().oldestPendingEpochSeconds)
                val second = PostgresIntentLedger(source)
                val results =
                    (0 until 32)
                        .map { n ->
                            async(Dispatchers.IO) {
                                (if (n % 2 == 0) first else second).reserve(
                                    hash("scope"),
                                    hash("txn-$n"),
                                    hash("nonce-$n"),
                                    BudgetReservation("USD", 100, 10, 100),
                                )
                            }
                        }.awaitAll()
                assertEquals(10, results.count { it })

                fun spent(scope: String): Long =
                    source.connection.use { connection ->
                        connection.prepareStatement("SELECT spent FROM vi_budget_accounts WHERE scope=?").use { statement ->
                            statement.setString(1, hash(scope))
                            statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else 0 }
                        }
                    }
                assertEquals(100, spent("scope"))
                val restarted = PostgresIntentLedger(source)
                assertFalse(restarted.reserve(hash("scope"), hash("new"), hash("new"), BudgetReservation("USD", 100, 1)))
                assertTrue(first.reserve(hash("replay"), hash("same"), hash("once"), BudgetReservation("USD", 100, 10, 100)))
                assertFalse(second.reserve(hash("replay"), hash("same"), hash("twice"), BudgetReservation("USD", 100, 10, 100)))
                assertFalse(second.reserve(hash("replay"), hash("other"), hash("once"), BudgetReservation("USD", 100, 10, 100)))
                assertFalse(second.reserve(hash("another-scope"), hash("other"), hash("once"), BudgetReservation("USD", 100, 10, 100)))
                assertEquals(10, spent("replay"))
                assertEquals(0, spent("another-scope"))
                source.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute(
                            """
                            CREATE FUNCTION reject_budget() RETURNS trigger AS $$
                            BEGIN RAISE EXCEPTION 'injected failure'; END; $$ LANGUAGE plpgsql
                            """.trimIndent(),
                        )
                        statement.execute(
                            "CREATE TRIGGER reject_budget BEFORE UPDATE ON vi_budget_accounts " +
                                "FOR EACH ROW EXECUTE FUNCTION reject_budget()",
                        )
                    }
                }
                assertThrows(SQLException::class.java) {
                    first.reserve(hash("rollback"), hash("retry"), hash("retry"), BudgetReservation("USD", 100, 20))
                }
                assertEquals(0, spent("rollback"))
                source.connection.use {
                    it.createStatement().use { statement ->
                        statement.execute("DROP TRIGGER reject_budget ON vi_budget_accounts")
                    }
                }
                assertTrue(restarted.reserve(hash("rollback"), hash("retry"), hash("retry"), BudgetReservation("USD", 100, 20)))
                assertEquals(20, spent("rollback"))
                assertTrue(
                    first.reserve(hash("overflow"), hash("max"), hash("max"), BudgetReservation("USD", Long.MAX_VALUE, Long.MAX_VALUE)),
                )
                assertFalse(first.reserve(hash("overflow"), hash("max2"), hash("max2"), BudgetReservation("USD", Long.MAX_VALUE, 1)))
                val uncertainSource =
                    object : javax.sql.DataSource by source {
                        override fun getConnection(): java.sql.Connection {
                            val actual = source.connection
                            return java.lang.reflect.Proxy.newProxyInstance(
                                java.sql.Connection::class.java.classLoader,
                                arrayOf(java.sql.Connection::class.java),
                            ) { _, method, arguments ->
                                if (method.name == "commit") {
                                    actual.commit()
                                    throw SQLException("connection lost after commit")
                                }
                                method.invoke(actual, *(arguments ?: emptyArray()))
                            } as java.sql.Connection
                        }
                    }
                assertThrows(SQLException::class.java) {
                    PostgresIntentLedger(uncertainSource).reserve(
                        hash("uncertain"),
                        hash("uncertain"),
                        hash("uncertain"),
                        BudgetReservation("USD", 100, 20),
                    )
                }
                assertEquals(20, spent("uncertain"))
                assertFalse(first.reserve(hash("uncertain"), hash("uncertain"), hash("uncertain"), BudgetReservation("USD", 100, 20)))
                val occurrenceResults =
                    (0 until 16)
                        .map { n ->
                            async(Dispatchers.IO) {
                                n to
                                    first.reserve(
                                        hash("occurrences"),
                                        hash("occurrence-$n"),
                                        hash("occurrence-$n"),
                                        BudgetReservation("USD", 1000, 1, 2),
                                    )
                            }
                        }.awaitAll()
                assertEquals(2, occurrenceResults.count { it.second })
                val occurrence = occurrenceResults.first { it.second }.first
                assertTrue(
                    first.reconcile(
                        "occurrences",
                        "occurrence-$occurrence",
                        org.trustweave.credential.vi.verification.SettlementOutcome.RELEASED,
                        "journal:occurrence",
                    ),
                )
                assertFalse(first.reserve(hash("occurrences"), hash("extra"), hash("extra"), BudgetReservation("USD", 1000, 1, 2)))
                assertTrue(
                    first.reserve(
                        hash("reconcile-failure"),
                        hash("payment"),
                        hash("settlement-failure"),
                        BudgetReservation("USD", 100, 10),
                    ),
                )
                source.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute(
                            """
                            CREATE FUNCTION reject_settlement() RETURNS trigger AS $$
                            BEGIN RAISE EXCEPTION 'injected settlement failure'; END; $$ LANGUAGE plpgsql
                            """.trimIndent(),
                        )
                        statement.execute(
                            "CREATE TRIGGER reject_settlement BEFORE UPDATE ON vi_budget_reservations " +
                                "FOR EACH ROW EXECUTE FUNCTION reject_settlement()",
                        )
                    }
                }
                assertThrows(SQLException::class.java) {
                    first.reconcile(
                        "reconcile-failure",
                        "payment",
                        org.trustweave.credential.vi.verification.SettlementOutcome.RELEASED,
                        "journal:failure",
                    )
                }
                assertEquals(10, spent("reconcile-failure"))
                source.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute("DROP TRIGGER reject_settlement ON vi_budget_reservations")
                    }
                }
                assertTrue(
                    first.reconcile(
                        "reconcile-failure",
                        "payment",
                        org.trustweave.credential.vi.verification.SettlementOutcome.RELEASED,
                        "journal:failure",
                    ),
                )
                assertEquals(0, spent("reconcile-failure"))
                assertFalse(first.reserve(hash("reconcile-failure"), hash("second"), hash("second"), BudgetReservation("USD", 100, 10)))
            }
        }
}
