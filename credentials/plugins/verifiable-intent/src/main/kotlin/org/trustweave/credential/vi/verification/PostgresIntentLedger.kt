package org.trustweave.credential.vi.verification

import java.sql.Connection
import javax.sql.DataSource

/**
 * Durable, conservative budget reservations for a single signed L2 payment mandate.
 * All verifier instances authorizing the same mandates must share this database.
 * Run [initializeSchema] during deployment with migration privileges; runtime needs only DML.
 * A successful reservation is never automatically refunded, even if downstream execution fails.
 * Execute payments with the signed transaction ID as an idempotency key. Never retry an
 * uncertain database commit as a fresh authorization. Retain records for the mandate lifetime.
 * Configure connection/network timeouts on the DataSource; SQL statements have a 10-second timeout.
 * Transactions require synchronous WAL acknowledgement (and retain remote_apply when configured).
 * This cannot compensate for fsync being disabled or missing synchronous replicas at the server.
 */
public class PostgresIntentLedger
    @JvmOverloads
    constructor(
        private val source: DataSource,
        public val diagnostics: IntentLedgerDiagnostics = IntentLedgerDiagnostics(),
    ) {
        public fun initializeSchema(): Unit = diagnostics.observe(IntentLedgerDiagnostics.Operation.MIGRATE) { initializeSchemaImpl() }

        private fun initializeSchemaImpl(): Unit =
            source.connection.use { connection ->
                connection.autoCommit = false
                try {
                    requireDurableCommit(connection)
                    connection.createStatement().use { statement ->
                        statement.queryTimeout = 10
                        statement.execute(
                            """
                            CREATE TABLE IF NOT EXISTS vi_budget_accounts (
                                scope VARCHAR(43) PRIMARY KEY,
                                currency VARCHAR(3) NOT NULL,
                                maximum BIGINT NOT NULL CHECK (maximum >= 0),
                                spent BIGINT NOT NULL CHECK (spent >= 0 AND spent <= maximum)
                            )
                            """.trimIndent(),
                        )
                        statement.execute(
                            """
                            CREATE TABLE IF NOT EXISTS vi_budget_reservations (
                                scope VARCHAR(43) NOT NULL REFERENCES vi_budget_accounts(scope),
                                transaction_hash VARCHAR(43) NOT NULL,
                                challenge_hash VARCHAR(43) NOT NULL UNIQUE,
                                amount BIGINT NOT NULL CHECK (amount >= 0),
                                PRIMARY KEY (scope, transaction_hash)
                            )
                            """.trimIndent(),
                        )
                        // Lock accounts before reservations, matching writers; drain writers before deployment.
                        statement.execute("LOCK TABLE vi_budget_accounts, vi_budget_reservations IN ACCESS EXCLUSIVE MODE")
                        statement.execute(
                            "ALTER TABLE vi_budget_reservations ADD COLUMN IF NOT EXISTS " +
                                "settlement_state VARCHAR(16) NOT NULL DEFAULT 'RESERVED'",
                        )
                        statement.execute("ALTER TABLE vi_budget_reservations ADD COLUMN IF NOT EXISTS evidence_hash VARCHAR(43)")
                        statement.execute(
                            "ALTER TABLE vi_budget_reservations ADD COLUMN IF NOT EXISTS " +
                                "created_at TIMESTAMPTZ",
                        )
                        statement.execute("ALTER TABLE vi_budget_reservations ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP")
                        statement.execute(
                            "ALTER TABLE vi_budget_accounts ADD COLUMN IF NOT EXISTS " +
                                "occurrence_count BIGINT NOT NULL DEFAULT 0 CHECK (occurrence_count >= 0)",
                        )
                        statement.execute(
                            """
                            UPDATE vi_budget_accounts a
                            SET occurrence_count = greatest(a.occurrence_count, r.total)
                            FROM (SELECT scope, count(*) AS total FROM vi_budget_reservations GROUP BY scope) r
                            WHERE a.scope = r.scope AND a.occurrence_count < r.total
                            """.trimIndent(),
                        )
                        // An AFTER INSERT trigger runs only for actual inserts, never ON CONFLICT no-ops.
                        // It shares the reservation transaction and also covers preceding SDK writers.
                        statement.execute(
                            """
                            CREATE OR REPLACE FUNCTION vi_count_occurrence() RETURNS trigger AS ${'$'}${'$'}
                            DECLARE affected BIGINT;
                            BEGIN
                                EXECUTE format('UPDATE %I.vi_budget_accounts SET occurrence_count=occurrence_count+1 WHERE scope=${'$'}1',
                                               TG_TABLE_SCHEMA) USING NEW.scope;
                                GET DIAGNOSTICS affected = ROW_COUNT;
                                IF affected <> 1 THEN
                                    RAISE EXCEPTION 'Budget account unavailable for occurrence accounting';
                                END IF;
                                RETURN NEW;
                            END; ${'$'}${'$'} LANGUAGE plpgsql
                            """.trimIndent(),
                        )
                        statement.execute("DROP TRIGGER IF EXISTS vi_count_occurrence ON vi_budget_reservations")
                        statement.execute(
                            "CREATE TRIGGER vi_count_occurrence AFTER INSERT ON vi_budget_reservations " +
                                "FOR EACH ROW EXECUTE FUNCTION vi_count_occurrence()",
                        )
                    }
                    connection.commit()
                } catch (failure: Throwable) {
                    try {
                        connection.rollback()
                    } catch (rollback: Throwable) {
                        failure.addSuppressed(rollback)
                    }
                    throw failure
                }
            }

        /**
         * Privileged host reconciliation after authenticating an authoritative payment-journal outcome.
         * Never expose this method directly to presenters or accept their claimed payment outcomes.
         * [evidenceReference] identifies the durable external journal record; only its hash is stored.
         * RELEASED requires confirmed non-execution, never a timeout or an unknown provider response.
         * Repeats with identical evidence are idempotent. Contradictory outcomes/evidence return false.
         * Challenge and occurrence consumption survive release. Downstream payment cannot then execute.
         */
        public fun reconcile(
            l2: String,
            transactionId: String,
            outcome: SettlementOutcome,
            evidenceReference: String,
        ): Boolean = reconcileHashes(hash(l2.substringBefore('~')), hash(transactionId), outcome, evidenceReference)

        internal fun reconcileHashes(
            scope: String,
            transaction: String,
            outcome: SettlementOutcome,
            evidenceReference: String,
        ): Boolean =
            diagnostics.observe(IntentLedgerDiagnostics.Operation.RECONCILE) {
                reconcileHashesImpl(scope, transaction, outcome, evidenceReference)
            }

        private fun reconcileHashesImpl(
            scope: String,
            transaction: String,
            outcome: SettlementOutcome,
            evidenceReference: String,
        ): Boolean {
            require(evidenceReference.isNotBlank() && evidenceReference.length <= 1024) { "Durable settlement evidence reference required" }
            val evidence = hash(evidenceReference)
            return source.connection.use { connection ->
                connection.autoCommit = false
                connection.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
                try {
                    requireDurableCommit(connection)
                    val account =
                        connection.prepareStatement("SELECT scope FROM vi_budget_accounts WHERE scope=? FOR UPDATE").use { statement ->
                            statement.queryTimeout = 10
                            statement.setString(1, scope)
                            statement.executeQuery().use { it.next() }
                        }
                    if (!account) {
                        connection.rollback()
                        return@use false
                    }
                    val record =
                        connection
                            .prepareStatement(
                                "SELECT settlement_state,evidence_hash,amount FROM vi_budget_reservations " +
                                    "WHERE scope=? AND transaction_hash=? FOR UPDATE",
                            ).use { statement ->
                                statement.queryTimeout = 10
                                statement.setString(1, scope)
                                statement.setString(2, transaction)
                                statement.executeQuery().use { rows ->
                                    if (rows.next()) Triple(rows.getString(1), rows.getString(2), rows.getLong(3)) else null
                                }
                            }
                    if (record == null) {
                        connection.rollback()
                        return@use false
                    }
                    if (record.first != "RESERVED") {
                        connection.rollback()
                        return@use record.first == outcome.name && record.second == evidence
                    }
                    if (outcome == SettlementOutcome.RELEASED) {
                        connection.prepareStatement("UPDATE vi_budget_accounts SET spent=spent-? WHERE scope=?").use { statement ->
                            statement.queryTimeout = 10
                            statement.setLong(1, record.third)
                            statement.setString(2, scope)
                            check(statement.executeUpdate() == 1)
                        }
                    }
                    connection
                        .prepareStatement(
                            "UPDATE vi_budget_reservations SET settlement_state=?,evidence_hash=? WHERE scope=? AND transaction_hash=?",
                        ).use { statement ->
                            statement.queryTimeout = 10
                            statement.setString(1, outcome.name)
                            statement.setString(2, evidence)
                            statement.setString(3, scope)
                            statement.setString(4, transaction)
                            check(statement.executeUpdate() == 1)
                        }
                    connection.commit()
                    true
                } catch (failure: Throwable) {
                    try {
                        connection.rollback()
                    } catch (rollback: Throwable) {
                        failure.addSuppressed(rollback)
                    }
                    throw failure
                }
            }
        }

        /** Bounded aggregate fields for host metrics; never expose raw mandates or transaction identifiers. */
        public fun healthSnapshot(): IntentLedgerHealth =
            diagnostics.observe(IntentLedgerDiagnostics.Operation.HEALTH) { healthSnapshotImpl() }

        private fun healthSnapshotImpl(): IntentLedgerHealth =
            source.connection.use { connection ->
                connection
                    .prepareStatement(
                        """
                        SELECT count(*) FILTER (WHERE settlement_state='RESERVED'),
                               count(*) FILTER (WHERE settlement_state='SETTLED'),
                               count(*) FILTER (WHERE settlement_state='RELEASED'),
                               CASE WHEN count(*) FILTER (WHERE settlement_state='RESERVED' AND created_at IS NULL) > 0
                                 THEN NULL ELSE min(created_at) FILTER (WHERE settlement_state='RESERVED') END
                        FROM vi_budget_reservations
                        """.trimIndent(),
                    ).use { statement ->
                        statement.queryTimeout = 10
                        statement.executeQuery().use { rows ->
                            check(rows.next())
                            IntentLedgerHealth(
                                rows.getLong(1),
                                rows.getLong(2),
                                rows.getLong(3),
                                rows.getTimestamp(4)?.toInstant()?.epochSecond,
                            )
                        }
                    }
            }

        private fun hash(value: String): String =
            org.trustweave.credential.vi.crypto
                .sha256B64Url(value.toByteArray(Charsets.UTF_8))

        private fun requireDurableCommit(connection: Connection) {
            connection.createStatement().use { statement ->
                statement.queryTimeout = 10
                statement.execute(
                    "SELECT set_config('synchronous_commit', " +
                        "CASE WHEN current_setting('synchronous_commit')='remote_apply' THEN 'remote_apply' ELSE 'on' END, true)",
                )
            }
        }

        internal fun reserve(
            scope: String,
            transaction: String,
            challenge: String,
            budget: BudgetReservation,
        ): Boolean = diagnostics.observe(IntentLedgerDiagnostics.Operation.RESERVE) { reserveImpl(scope, transaction, challenge, budget) }

        private fun reserveImpl(
            scope: String,
            transaction: String,
            challenge: String,
            budget: BudgetReservation,
        ): Boolean {
            require(listOf(scope, transaction, challenge).all { it.matches(Regex("[A-Za-z0-9_-]{43}")) })
            require(
                budget.maximumOccurrences > 0 && budget.amount >= 0 && budget.maximum >= 0 && budget.currency.matches(Regex("[A-Z]{3}")),
            )
            return source.connection.use { connection ->
                connection.autoCommit = false
                connection.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
                try {
                    requireDurableCommit(connection)
                    connection
                        .prepareStatement(
                            "INSERT INTO vi_budget_accounts(scope,currency,maximum,spent) VALUES (?,?,?,0) ON CONFLICT DO NOTHING",
                        ).use { statement ->
                            statement.queryTimeout = 10
                            statement.setString(1, scope)
                            statement.setString(2, budget.currency)
                            statement.setLong(3, budget.maximum)
                            statement.executeUpdate()
                        }
                    val account =
                        connection
                            .prepareStatement(
                                "SELECT currency,maximum,spent,occurrence_count FROM vi_budget_accounts WHERE scope=? FOR UPDATE",
                            ).use { statement ->
                                statement.queryTimeout = 10
                                statement.setString(1, scope)
                                statement.executeQuery().use { rows ->
                                    check(rows.next())
                                    check(rows.getString(1) == budget.currency && rows.getLong(2) == budget.maximum) {
                                        "Budget policy differs from stored mandate"
                                    }
                                    (rows.getLong(2) - rows.getLong(3)) to rows.getLong(4)
                                }
                            }
                    if (budget.amount > account.first || account.second >= budget.maximumOccurrences) {
                        connection.rollback()
                        return@use false
                    }
                    val inserted =
                        connection
                            .prepareStatement(
                                """
                                INSERT INTO vi_budget_reservations(scope,transaction_hash,challenge_hash,amount)
                                VALUES (?,?,?,?) ON CONFLICT DO NOTHING
                                """.trimIndent(),
                            ).use { statement ->
                                statement.queryTimeout = 10
                                statement.setString(1, scope)
                                statement.setString(2, transaction)
                                statement.setString(3, challenge)
                                statement.setLong(4, budget.amount)
                                statement.executeUpdate()
                            }
                    if (inserted != 1) {
                        connection.rollback()
                        return@use false
                    }
                    connection.prepareStatement("UPDATE vi_budget_accounts SET spent=spent+? WHERE scope=?").use { statement ->
                        statement.queryTimeout = 10
                        statement.setLong(1, budget.amount)
                        statement.setString(2, scope)
                        check(statement.executeUpdate() == 1)
                    }
                    connection.commit()
                    true
                } catch (failure: Throwable) {
                    try {
                        connection.rollback()
                    } catch (rollback: Throwable) {
                        failure.addSuppressed(rollback)
                    }
                    throw failure
                }
            }
        }
    }

internal data class BudgetReservation(
    val currency: String,
    val maximum: Long,
    val amount: Long,
    val maximumOccurrences: Long = 1,
    val merchantRecurrence: kotlinx.serialization.json.JsonObject? = null,
)

/** Terminal journal outcomes; UNKNOWN intentionally cannot release reserved authority. */
public enum class SettlementOutcome { SETTLED, RELEASED }

public data class IntentLedgerHealth(
    val pending: Long,
    val settled: Long,
    val released: Long,
    val oldestPendingEpochSeconds: Long?,
)
