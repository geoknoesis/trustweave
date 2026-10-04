package org.trustweave.credential.didcomm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.sql.SQLException
import java.sql.SQLIntegrityConstraintViolationException
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource

/**
 * Durable, replica-shared [DidCommReplayStore] backed by a JDBC table.
 *
 * Recording is a single atomic insert against the primary key: of any number of concurrent
 * callers (in this process or in other replicas sharing the database) presenting the same message
 * id, exactly one gets `true`. An id whose retention has passed is replaced rather than treated as
 * a replay, and expired rows are purged opportunistically at most every [cleanupIntervalSeconds].
 *
 * The key stored is the lowercase-hex SHA-256 of the UTF-8 message id (64 characters), not the id
 * itself. A raw id in a `VARCHAR(255)` primary key would be compared under the column's collation:
 * MySQL's default collations are case-insensitive (and accent-insensitive), so two distinct ids
 * differing only in case would collide and a legitimate message would be refused as a replay, and an
 * id longer than 255 characters could not be stored at all. A fixed-length hex digest is
 * collation-independent. Rows written by earlier versions held raw ids and no longer match; they only
 * protected ids for their retention window, which they will simply age out of.
 *
 * Uses only portable SQL (`CREATE TABLE IF NOT EXISTS`, plain `INSERT`/`DELETE`), so it runs on
 * PostgreSQL, MySQL and H2. The table is created on construction.
 *
 * @param tableName Table to use; must be a plain SQL identifier.
 * @param cleanupIntervalSeconds Minimum spacing between expired-row purges by this instance.
 */
class DatabaseDidCommReplayStore
    @JvmOverloads
    constructor(
        private val dataSource: DataSource,
        private val tableName: String = DEFAULT_TABLE_NAME,
        private val cleanupIntervalSeconds: Long = DEFAULT_CLEANUP_INTERVAL_SECONDS,
    ) : DidCommReplayStore {
        init {
            require(IDENTIFIER.matches(tableName)) { "tableName must be a plain SQL identifier: '$tableName'" }
            require(cleanupIntervalSeconds > 0) { "cleanupIntervalSeconds must be positive" }
            dataSource.connection.use { conn ->
                conn
                    .createStatement()
                    .use {
                        it.execute(
                            "CREATE TABLE IF NOT EXISTS $tableName (" +
                                "message_id VARCHAR(255) PRIMARY KEY, " +
                                "retain_until BIGINT NOT NULL)",
                        )
                    }
                // IF NOT EXISTS on an index is not portable to MySQL; the primary key already
                // serves the replay lookup, and purges scan retain_until rarely.
            }
        }

        private val lastCleanup = AtomicLong(0)

        override suspend fun recordIfAbsent(
            messageId: String,
            retainUntilEpochSeconds: Long,
            nowEpochSeconds: Long,
        ): Boolean =
            withContext(Dispatchers.IO) {
                val recorded = insertIfAbsent(keyOf(messageId), retainUntilEpochSeconds, nowEpochSeconds)
                purgeExpiredIfDue(nowEpochSeconds)
                recorded
            }

        private fun keyOf(messageId: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(messageId.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        private fun insertIfAbsent(
            messageId: String,
            retainUntil: Long,
            now: Long,
        ): Boolean =
            dataSource.connection.use { conn ->
                val originalAutoCommit = conn.autoCommit
                conn.autoCommit = false
                try {
                    // An expired record of the same id no longer protects anything; clear it so the
                    // id can be accepted again. A live record is untouched (retain_until > now).
                    conn.prepareStatement("DELETE FROM $tableName WHERE message_id = ? AND retain_until <= ?").use {
                        it.setString(1, messageId)
                        it.setLong(2, now)
                        it.executeUpdate()
                    }
                    try {
                        conn.prepareStatement("INSERT INTO $tableName (message_id, retain_until) VALUES (?, ?)").use {
                            it.setString(1, messageId)
                            it.setLong(2, retainUntil)
                            it.executeUpdate()
                        }
                    } catch (e: SQLException) {
                        if (isUniqueViolation(e)) {
                            conn.rollback()
                            return@use false
                        }
                        throw e
                    }
                    conn.commit()
                    true
                } catch (e: CancellationException) {
                    conn.rollback()
                    throw e
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    // The connection goes back to the pool: leaving it in manual-commit mode would
                    // make the next borrower's writes silently uncommitted.
                    runCatching { conn.autoCommit = originalAutoCommit }
                }
            }

        private fun purgeExpiredIfDue(now: Long) {
            val last = lastCleanup.get()
            if (now - last < cleanupIntervalSeconds || !lastCleanup.compareAndSet(last, now)) return
            dataSource.connection.use { conn ->
                conn.prepareStatement("DELETE FROM $tableName WHERE retain_until <= ?").use {
                    it.setLong(1, now)
                    it.executeUpdate()
                }
            }
        }

        private fun isUniqueViolation(e: SQLException): Boolean =
            e is SQLIntegrityConstraintViolationException || e.sqlState?.startsWith("23") == true

        companion object {
            const val DEFAULT_TABLE_NAME: String = "didcomm_replay_ids"
            const val DEFAULT_CLEANUP_INTERVAL_SECONDS: Long = 60

            private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]{0,62}")
        }
    }
