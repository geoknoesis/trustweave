package org.trustweave.credential.avpauth.state

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import javax.sql.DataSource

/**
 * Replay prevention, single-use enforcement and daily spend caps in a shared database.
 *
 * Every instance authorizing the same credentials must point at the same database. That is the
 * whole point: the guarantees this store makes are exactly as wide as the database behind it, and
 * two instances with two databases enforce two separate sets of limits.
 *
 * ## How one admission is decided
 *
 * One transaction, at READ COMMITTED, with synchronous commit required:
 *
 * 1. Insert the nonce. A unique-key conflict is a replay.
 * 2. Insert the authorization id. A conflict is a second consumption.
 * 3. Lock the payer's row for today and compare prior spend plus this amount against the limit.
 *
 * Concurrency falls out of the database rather than a lock held in this process: a second
 * transaction presenting the same nonce blocks on the unique index until the first resolves, and
 * then sees the conflict. A refusal rolls the whole transaction back, so a refused authorization
 * records nothing — its nonce stays unconsumed and its budget untouched.
 *
 * ## Operating it
 *
 * Run [initializeSchema] at deployment with migration privileges; runtime needs only DML. Run
 * [purgeExpired] on a schedule — records are retained until the authorization they describe can
 * no longer be validly presented, and nothing drops them before then, but nothing drops them
 * after then either unless the sweep runs.
 *
 * Statements carry a ten-second timeout. Transactions require synchronous WAL acknowledgement,
 * which cannot compensate for `fsync` being disabled or synchronous replicas being absent at the
 * server. Configure connection and network timeouts on the [DataSource].
 *
 * A failed transaction is refused as [StatefulRejection.STORE_UNAVAILABLE] and **never retried as
 * a fresh authorization** — retrying an uncertain commit is how a single-use authorization gets
 * used twice.
 *
 * @param retentionCeiling the furthest out a record will be retained. An authorization that would
 *   need to be remembered longer is refused rather than half-remembered.
 */
class PostgresAuthorizationStore
    @JvmOverloads
    constructor(
        private val source: DataSource,
        private val retentionCeiling: Duration = Duration.ofDays(90),
    ) : AuthorizationStore {
        init {
            require(!retentionCeiling.isNegative && !retentionCeiling.isZero) { "retentionCeiling must be positive" }
        }

        /** Creates the three tables if they are absent. Requires migration privileges. */
        fun initializeSchema() {
            source.connection.use { connection ->
                connection.autoCommit = false
                try {
                    requireDurableCommit(connection)
                    connection.createStatement().use { statement ->
                        statement.queryTimeout = TIMEOUT_SECONDS
                        statement.execute(
                            """
                            CREATE TABLE IF NOT EXISTS avp_nonces (
                                credential_id TEXT NOT NULL,
                                nonce TEXT NOT NULL,
                                retain_until TIMESTAMPTZ NOT NULL,
                                PRIMARY KEY (credential_id, nonce)
                            )
                            """.trimIndent(),
                        )
                        statement.execute(
                            """
                            CREATE TABLE IF NOT EXISTS avp_consumptions (
                                authorization_id TEXT PRIMARY KEY,
                                retain_until TIMESTAMPTZ NOT NULL
                            )
                            """.trimIndent(),
                        )
                        statement.execute(
                            """
                            CREATE TABLE IF NOT EXISTS avp_daily_spend (
                                payer TEXT NOT NULL,
                                credential_id TEXT NOT NULL,
                                spend_date DATE NOT NULL,
                                spent NUMERIC(38,9) NOT NULL CHECK (spent >= 0),
                                PRIMARY KEY (payer, credential_id, spend_date)
                            )
                            """.trimIndent(),
                        )
                        statement.execute("CREATE INDEX IF NOT EXISTS avp_nonces_retain_until ON avp_nonces (retain_until)")
                        statement.execute(
                            "CREATE INDEX IF NOT EXISTS avp_consumptions_retain_until ON avp_consumptions (retain_until)",
                        )
                        statement.execute("CREATE INDEX IF NOT EXISTS avp_daily_spend_date ON avp_daily_spend (spend_date)")
                    }
                    connection.commit()
                } catch (failure: Throwable) {
                    rollbackQuietly(connection, failure)
                    throw failure
                }
            }
        }

        override suspend fun admit(request: AdmissionRequest): Admission {
            if (request.retainUntil.isAfter(request.at.plus(retentionCeiling))) {
                return Admission.Refused(
                    StatefulRejection.UNBOUNDED_LIFETIME,
                    "authorization stays presentable past this store's ${retentionCeiling.toDays()}-day retention ceiling",
                )
            }
            return withContext(Dispatchers.IO) {
                try {
                    admitInTransaction(request)
                } catch (unavailable: SQLException) {
                    // The decision could not be made. Refuse it; do not retry it as a new
                    // authorization, because the commit may well have succeeded.
                    Admission.Refused(
                        StatefulRejection.STORE_UNAVAILABLE,
                        "authorization state could not be committed: ${unavailable.sqlState ?: "unknown"}",
                    )
                }
            }
        }

        private fun admitInTransaction(request: AdmissionRequest): Admission =
            source.connection.use { connection ->
                connection.autoCommit = false
                connection.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
                try {
                    requireDurableCommit(connection)
                    val retainUntil = Timestamp.from(request.retainUntil)

                    val nonceInserted =
                        connection
                            .prepareStatement(
                                "INSERT INTO avp_nonces(credential_id,nonce,retain_until) VALUES (?,?,?) ON CONFLICT DO NOTHING",
                            ).use { statement ->
                                statement.queryTimeout = TIMEOUT_SECONDS
                                statement.setString(1, request.credentialId)
                                statement.setString(2, request.nonce)
                                statement.setTimestamp(3, retainUntil)
                                statement.executeUpdate()
                            }
                    if (nonceInserted != 1) {
                        connection.rollback()
                        return@use Admission.Refused(StatefulRejection.NONCE_REUSE, "nonce already presented")
                    }

                    val consumed =
                        connection
                            .prepareStatement(
                                "INSERT INTO avp_consumptions(authorization_id,retain_until) VALUES (?,?) ON CONFLICT DO NOTHING",
                            ).use { statement ->
                                statement.queryTimeout = TIMEOUT_SECONDS
                                statement.setString(1, request.authorizationId)
                                statement.setTimestamp(2, retainUntil)
                                statement.executeUpdate()
                            }
                    if (consumed != 1) {
                        connection.rollback()
                        return@use Admission.Refused(StatefulRejection.DOUBLE_SPEND, "authorization already consumed")
                    }

                    val day = java.sql.Date.valueOf(request.at.atZone(ZoneOffset.UTC).toLocalDate())
                    connection
                        .prepareStatement(
                            "INSERT INTO avp_daily_spend(payer,credential_id,spend_date,spent) VALUES (?,?,?,0) ON CONFLICT DO NOTHING",
                        ).use { statement ->
                            statement.queryTimeout = TIMEOUT_SECONDS
                            statement.setString(1, request.payer)
                            statement.setString(2, request.credentialId)
                            statement.setDate(3, day)
                            statement.executeUpdate()
                        }
                    val prior =
                        connection
                            .prepareStatement(
                                "SELECT spent FROM avp_daily_spend WHERE payer=? AND credential_id=? AND spend_date=? FOR UPDATE",
                            ).use { statement ->
                                statement.queryTimeout = TIMEOUT_SECONDS
                                statement.setString(1, request.payer)
                                statement.setString(2, request.credentialId)
                                statement.setDate(3, day)
                                statement.executeQuery().use { rows ->
                                    check(rows.next()) { "daily spend row vanished inside its own transaction" }
                                    rows.getBigDecimal(1) ?: BigDecimal.ZERO
                                }
                            }
                    val limit = request.dailyLimit
                    if (limit != null && prior.add(request.amount) > limit) {
                        connection.rollback()
                        return@use Admission.Refused(StatefulRejection.DAILY_LIMIT_EXCEEDED, "daily limit $limit exceeded")
                    }
                    connection
                        .prepareStatement(
                            "UPDATE avp_daily_spend SET spent=spent+? WHERE payer=? AND credential_id=? AND spend_date=?",
                        ).use { statement ->
                            statement.queryTimeout = TIMEOUT_SECONDS
                            statement.setBigDecimal(1, request.amount)
                            statement.setString(2, request.payer)
                            statement.setString(3, request.credentialId)
                            statement.setDate(4, day)
                            check(statement.executeUpdate() == 1) { "daily spend row vanished inside its own transaction" }
                        }
                    connection.commit()
                    Admission.Admitted
                } catch (failure: Throwable) {
                    rollbackQuietly(connection, failure)
                    throw failure
                }
            }

        /**
         * Drops records past their retention, and daily rows for days already over.
         *
         * Safe to run against a live deployment: nothing it removes can change a decision, because
         * a record is only past retention once its authorization can no longer be presented.
         */
        override suspend fun purgeExpired(now: Instant): Int =
            withContext(Dispatchers.IO) {
                source.connection.use { connection ->
                    connection.autoCommit = true
                    var removed = 0
                    connection.prepareStatement("DELETE FROM avp_nonces WHERE retain_until <= ?").use { statement ->
                        statement.queryTimeout = TIMEOUT_SECONDS
                        statement.setTimestamp(1, Timestamp.from(now))
                        removed += statement.executeUpdate()
                    }
                    connection.prepareStatement("DELETE FROM avp_consumptions WHERE retain_until <= ?").use { statement ->
                        statement.queryTimeout = TIMEOUT_SECONDS
                        statement.setTimestamp(1, Timestamp.from(now))
                        removed += statement.executeUpdate()
                    }
                    connection.prepareStatement("DELETE FROM avp_daily_spend WHERE spend_date < ?").use { statement ->
                        statement.queryTimeout = TIMEOUT_SECONDS
                        statement.setDate(1, java.sql.Date.valueOf(now.atZone(ZoneOffset.UTC).toLocalDate()))
                        removed += statement.executeUpdate()
                    }
                    removed
                }
            }

        private fun requireDurableCommit(connection: Connection) {
            connection.createStatement().use { statement ->
                statement.queryTimeout = TIMEOUT_SECONDS
                statement.execute(
                    "SELECT set_config('synchronous_commit', " +
                        "CASE WHEN current_setting('synchronous_commit')='remote_apply' THEN 'remote_apply' ELSE 'on' END, true)",
                )
            }
        }

        private fun rollbackQuietly(
            connection: Connection,
            failure: Throwable,
        ) {
            try {
                connection.rollback()
            } catch (rollback: SQLException) {
                failure.addSuppressed(rollback)
            }
        }

        private companion object {
            const val TIMEOUT_SECONDS = 10
        }
    }
