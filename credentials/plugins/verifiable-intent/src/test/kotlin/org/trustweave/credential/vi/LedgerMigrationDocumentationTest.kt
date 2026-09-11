package org.trustweave.credential.vi

import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.trustweave.credential.vi.verification.PostgresIntentLedger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LedgerMigrationDocumentationTest {
    @Test
    fun `documented schema upgrade is idempotent and preserves legacy reservations`() {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").use { postgres ->
            postgres.start()
            val source =
                PGSimpleDataSource().apply {
                    setURL(postgres.jdbcUrl)
                    user = postgres.username
                    password = postgres.password
                }
            // This fixture represents the earlier schema; run only in this disposable database.
            source.connection.use { connection ->
                connection.createStatement().use { sql ->
                    sql.execute(
                        "CREATE TABLE vi_budget_accounts(scope VARCHAR(43) PRIMARY KEY, currency VARCHAR(3) NOT NULL, maximum BIGINT NOT NULL, spent BIGINT NOT NULL)",
                    )
                    sql.execute(
                        "CREATE TABLE vi_budget_reservations(scope VARCHAR(43) REFERENCES vi_budget_accounts(scope), transaction_hash VARCHAR(43), challenge_hash VARCHAR(43) UNIQUE, amount BIGINT NOT NULL, PRIMARY KEY(scope,transaction_hash))",
                    )
                    sql.execute("INSERT INTO vi_budget_accounts VALUES ('legacy','USD',100,5)")
                    sql.execute("INSERT INTO vi_budget_reservations VALUES ('legacy','transaction','challenge',5)")
                }
            }
            val ledger = PostgresIntentLedger(source)
            repeat(2) {
                // Deployment identity requires migration privileges; runtime identity needs only DML.
                ledger.initializeSchema()
                assertEquals(1L, ledger.healthSnapshot().pending)
                assertNull(ledger.healthSnapshot().oldestPendingEpochSeconds)
            }
            source.connection.use { connection ->
                connection.createStatement().use { sql ->
                    sql
                        .executeQuery(
                            "SELECT a.spent,r.amount,r.settlement_state,r.created_at FROM vi_budget_accounts a JOIN vi_budget_reservations r ON a.scope=r.scope",
                        ).use { row ->
                            assertTrue(row.next())
                            assertEquals(5L, row.getLong("spent"))
                            assertEquals(5L, row.getLong("amount"))
                            assertEquals("RESERVED", row.getString("settlement_state"))
                            assertNull(row.getTimestamp("created_at"))
                        }
                }
            }
        }
    }
}
