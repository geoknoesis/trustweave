package org.trustweave.credential.vi

import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.trustweave.credential.vi.crypto.sha256B64Url
import org.trustweave.credential.vi.verification.BudgetReservation
import org.trustweave.credential.vi.verification.PostgresIntentLedger
import org.trustweave.credential.vi.verification.SettlementOutcome
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LedgerIntegrityTest {
    private fun hash(value: String) = sha256B64Url(value.toByteArray())

    @Test
    fun `internally consistent rollback is rejected against external checkpoint`() {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").use { postgres ->
            postgres.start()
            val source =
                PGSimpleDataSource().apply {
                    setURL(postgres.jdbcUrl)
                    user = postgres.username
                    password = postgres.password
                }
            val ledger = PostgresIntentLedger(source)
            ledger.initializeSchema()
            assertTrue(ledger.reserve(hash("mandate"), hash("first"), hash("first-challenge"), BudgetReservation("USD", 100, 5, 10)))
            val oldCheckpoint = ledger.verifyIntegrity()
            assertTrue(ledger.reserve(hash("mandate"), hash("second"), hash("second-challenge"), BudgetReservation("USD", 100, 5, 10)))
            val trustedCheckpoint = ledger.verifyIntegrity()
            // Model a consistent old snapshot, not a partial-write corruption.
            source.connection.use { connection ->
                connection.autoCommit = false
                connection.createStatement().use { sql ->
                    sql.execute("DELETE FROM vi_budget_reservations WHERE transaction_hash='${hash("second")}'")
                    sql.execute("UPDATE vi_budget_accounts SET spent=5,occurrence_count=1")
                }
                connection.commit()
            }
            assertEquals(oldCheckpoint, ledger.verifyIntegrity())
            assertFailsWith<IllegalStateException> { ledger.verifyIntegrity(trustedCheckpoint) }
        }
    }

    @Test
    fun `filtered audit role fails instead of authenticating partial data`() {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").use { postgres ->
            postgres.start()
            val source =
                PGSimpleDataSource().apply {
                    setURL(postgres.jdbcUrl)
                    user = postgres.username
                    password = postgres.password
                }
            val ledger = PostgresIntentLedger(source)
            ledger.initializeSchema()
            source.connection.use { connection ->
                connection.createStatement().use { sql ->
                    sql.execute("CREATE ROLE partial_auditor LOGIN PASSWORD 'fixture-only'")
                    sql.execute("GRANT SELECT ON vi_budget_accounts,vi_budget_reservations TO partial_auditor")
                    sql.execute("ALTER TABLE vi_budget_accounts ENABLE ROW LEVEL SECURITY")
                    sql.execute("CREATE POLICY hidden_accounts ON vi_budget_accounts FOR SELECT TO partial_auditor USING (false)")
                }
            }
            val filtered =
                PGSimpleDataSource().apply {
                    setURL(postgres.jdbcUrl)
                    user = "partial_auditor"
                    password = "fixture-only"
                }
            assertFailsWith<java.sql.SQLException> { PostgresIntentLedger(filtered).verifyIntegrity() }
            ledger.verifyIntegrity()
        }
    }

    @Test
    fun `checkpoint detects stale data and includes terminal evidence without exposing identifiers`() {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").use { postgres ->
            postgres.start()
            val source =
                PGSimpleDataSource().apply {
                    setURL(postgres.jdbcUrl)
                    user = postgres.username
                    password = postgres.password
                }
            val ledger = PostgresIntentLedger(source)
            ledger.initializeSchema()
            val empty = ledger.verifyIntegrity()
            assertTrue(ledger.reserve(hash("mandate"), hash("transaction"), hash("challenge"), BudgetReservation("USD", 100, 5, 10)))
            val pending = ledger.verifyIntegrity()
            assertNotEquals(empty, pending)
            assertEquals(pending, ledger.verifyIntegrity(pending))
            assertFailsWith<IllegalStateException> { ledger.verifyIntegrity(empty) }
            assertTrue(ledger.reconcileHashes(hash("mandate"), hash("transaction"), SettlementOutcome.SETTLED, "journal-1"))
            val settled = ledger.verifyIntegrity()
            assertNotEquals(pending, settled)
            assertEquals(settled, PostgresIntentLedger(source).verifyIntegrity(settled))
            source.connection.use { connection ->
                connection.createStatement().use { sql ->
                    sql.execute("UPDATE vi_budget_reservations SET evidence_hash='${hash("altered")}'")
                }
            }
            assertFailsWith<IllegalStateException> { ledger.verifyIntegrity(settled) }
            assertFailsWith<IllegalArgumentException> { ledger.verifyIntegrity("untrusted-format") }
        }
    }

    @Test
    fun `audit rejects inconsistent balances consumption states and evidence without repairing data`() {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").use { postgres ->
            postgres.start()
            val source =
                PGSimpleDataSource().apply {
                    setURL(postgres.jdbcUrl)
                    user = postgres.username
                    password = postgres.password
                }
            val ledger = PostgresIntentLedger(source)
            ledger.initializeSchema()
            assertTrue(ledger.reserve(hash("mandate"), hash("transaction"), hash("challenge"), BudgetReservation("USD", 100, 5, 10)))
            val checkpoint = ledger.verifyIntegrity()
            val corruptions =
                listOf(
                    "UPDATE vi_budget_accounts SET spent=0" to "UPDATE vi_budget_accounts SET spent=5",
                    "UPDATE vi_budget_accounts SET occurrence_count=0" to "UPDATE vi_budget_accounts SET occurrence_count=1",
                    "UPDATE vi_budget_accounts SET currency='usd'" to "UPDATE vi_budget_accounts SET currency='USD'",
                    "UPDATE vi_budget_reservations SET settlement_state='UNKNOWN'" to
                        "UPDATE vi_budget_reservations SET settlement_state='RESERVED'",
                    "UPDATE vi_budget_reservations SET settlement_state='SETTLED'" to
                        "UPDATE vi_budget_reservations SET settlement_state='RESERVED'",
                    "UPDATE vi_budget_reservations SET evidence_hash='bad'" to "UPDATE vi_budget_reservations SET evidence_hash=NULL",
                )
            for ((corrupt, repair) in corruptions) {
                source.connection.use { it.createStatement().use { sql -> sql.execute(corrupt) } }
                assertFailsWith<IllegalStateException> { ledger.verifyIntegrity() }
                // Only the privileged test fixture repairs corruption; the audit is read-only.
                source.connection.use { it.createStatement().use { sql -> sql.execute(repair) } }
                assertEquals(checkpoint, ledger.verifyIntegrity(checkpoint))
            }
            assertTrue(ledger.reconcileHashes(hash("mandate"), hash("transaction"), SettlementOutcome.RELEASED, "confirmed-fenced"))
            ledger.verifyIntegrity()
            ledger.initializeSchema()
            ledger.verifyIntegrity()
        }
    }
}
