package org.trustweave.did.registrar.storage

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.trustweave.did.registrar.model.DidRegistrationResponse
import java.sql.Timestamp
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Database-backed implementation of [JobStorage].
 *
 * Stores DID registration job responses in a relational database.
 * Supports PostgreSQL, MySQL, and H2 databases.
 *
 * **Example Usage:**
 * ```kotlin
 * val dataSource = HikariDataSource().apply {
 *     jdbcUrl = "jdbc:postgresql://localhost:5432/trustweave"
 *     username = "user"
 *     password = "password"
 * }
 *
 * val storage = DatabaseJobStorage(dataSource)
 * storage.store("job-123", response)
 * val retrieved = storage.get("job-123")
 * ```
 *
 * **Dependencies:**
 * - Requires a JDBC DataSource implementation (e.g., HikariCP, Tomcat JDBC Pool)
 * - Requires a database driver (PostgreSQL, MySQL, H2, etc.)
 * - These should be provided by the application using this class
 *
 * **Retention:** a job is not served once it is older (since its last write) than [finishedJobTtl]
 * (finished or failed) or [unfinishedJobTtl] (still running), and expired rows are purged in the
 * background of [store] at most once per [cleanupInterval]. [cleanupCompletedJobs] remains available
 * for an explicit, scheduled sweep.
 *
 * @param dataSource JDBC DataSource for database connections
 * @param tableName Name of the table to store jobs (default: "did_registration_jobs"); must be a plain
 *   SQL identifier because it is part of the statements
 * @param finishedJobTtl How long a finished or failed job stays retrievable
 * @param unfinishedJobTtl How long an unfinished job stays tracked at most
 * @param cleanupInterval Minimum time between the purges that [store] triggers
 */
class DatabaseJobStorage
    @JvmOverloads
    constructor(
        private val dataSource: DataSource,
        private val tableName: String = "did_registration_jobs",
        private val finishedJobTtl: Duration = Duration.parse("PT1H"),
        private val unfinishedJobTtl: Duration = Duration.parse("PT24H"),
        private val cleanupInterval: Duration = Duration.parse("PT10M"),
    ) : JobStorage {
        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
            }

        private val lastCleanupMillis = AtomicLong(System.currentTimeMillis())

        init {
            // The table name is spliced into SQL: accept nothing but a plain identifier.
            require(TABLE_NAME.matches(tableName)) { "tableName must be a plain SQL identifier, was: $tableName" }
            // Initialize database schema
            initializeSchema()
        }

        /**
         * Initialize database schema (table for job storage).
         */
        private fun initializeSchema() {
            dataSource.connection.use { conn ->
                // Create jobs table
                // Using TEXT/CLOB for JSON storage to support large responses
                val createTableSql =
                    when {
                        conn.metaData.databaseProductName
                            .lowercase()
                            .contains("postgresql") -> {
                            """
                            CREATE TABLE IF NOT EXISTS $tableName (
                                job_id VARCHAR(255) PRIMARY KEY,
                                response_data TEXT NOT NULL,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                            )
                            """.trimIndent()
                        }
                        conn.metaData.databaseProductName
                            .lowercase()
                            .contains("mysql") -> {
                            """
                            CREATE TABLE IF NOT EXISTS $tableName (
                                job_id VARCHAR(255) PRIMARY KEY,
                                response_data TEXT NOT NULL,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                            )
                            """.trimIndent()
                        }
                        else -> {
                            // H2 and other databases
                            """
                            CREATE TABLE IF NOT EXISTS $tableName (
                                job_id VARCHAR(255) PRIMARY KEY,
                                response_data CLOB NOT NULL,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                            )
                            """.trimIndent()
                        }
                    }

                conn.prepareStatement(createTableSql).execute()

                // Create index on created_at for cleanup queries
                try {
                    val indexName = "idx_${tableName}_created_at"
                    val createIndexSql =
                        when {
                            conn.metaData.databaseProductName
                                .lowercase()
                                .contains("postgresql") -> {
                                "CREATE INDEX IF NOT EXISTS $indexName ON $tableName(created_at)"
                            }
                            conn.metaData.databaseProductName
                                .lowercase()
                                .contains("mysql") -> {
                                "CREATE INDEX IF NOT EXISTS $indexName ON $tableName(created_at)"
                            }
                            else -> {
                                // H2 supports IF NOT EXISTS
                                "CREATE INDEX IF NOT EXISTS $indexName ON $tableName(created_at)"
                            }
                        }
                    conn.prepareStatement(createIndexSql).execute()
                } catch (e: Exception) {
                    // Index might already exist, ignore
                    // Some databases don't support IF NOT EXISTS for indexes
                }
            }
        }

        override fun store(
            jobId: String,
            response: DidRegistrationResponse,
        ) {
            val jsonData = json.encodeToString(response)
            val now = Timestamp.from(java.time.Instant.ofEpochSecond(Clock.System.now().epochSeconds))

            dataSource.connection.use { conn ->
                // Use INSERT ... ON CONFLICT UPDATE (PostgreSQL) or REPLACE INTO (MySQL/H2)
                val sql =
                    when {
                        conn.metaData.databaseProductName
                            .lowercase()
                            .contains("postgresql") -> {
                            """
                            INSERT INTO $tableName (job_id, response_data, created_at, updated_at)
                            VALUES (?, ?, ?, ?)
                            ON CONFLICT (job_id) DO UPDATE SET
                                response_data = EXCLUDED.response_data,
                                updated_at = EXCLUDED.updated_at
                            """.trimIndent()
                        }
                        conn.metaData.databaseProductName
                            .lowercase()
                            .contains("mysql") -> {
                            """
                            INSERT INTO $tableName (job_id, response_data, created_at, updated_at)
                            VALUES (?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE
                                response_data = VALUES(response_data),
                                updated_at = VALUES(updated_at)
                            """.trimIndent()
                        }
                        else -> {
                            // H2 and other databases - use MERGE or REPLACE
                            """
                            MERGE INTO $tableName (job_id, response_data, created_at, updated_at)
                            VALUES (?, ?, ?, ?)
                            """.trimIndent()
                        }
                    }

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, jobId)
                    stmt.setString(2, jsonData)
                    stmt.setTimestamp(3, now)
                    stmt.setTimestamp(4, now)
                    stmt.executeUpdate()
                }
            }
            purgeExpiredIfDue()
        }

        override fun get(jobId: String): DidRegistrationResponse? {
            var expired = false
            var found: DidRegistrationResponse? = null
            dataSource.connection.use { conn ->
                val sql = "SELECT response_data, updated_at FROM $tableName WHERE job_id = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, jobId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            val response = json.decodeFromString<DidRegistrationResponse>(rs.getString("response_data"))
                            if (isExpired(response, rs.getTimestamp("updated_at"))) {
                                expired = true
                            } else {
                                found = response
                            }
                        }
                    }
                }
            }
            if (expired) remove(jobId)
            return found
        }

        override fun remove(jobId: String): Boolean {
            dataSource.connection.use { conn ->
                val sql = "DELETE FROM $tableName WHERE job_id = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, jobId)
                    val rowsAffected = stmt.executeUpdate()
                    return rowsAffected > 0
                }
            }
        }

        override fun exists(jobId: String): Boolean = get(jobId) != null

        private fun isExpired(
            response: DidRegistrationResponse?,
            updatedAt: Timestamp?,
        ): Boolean {
            val written = updatedAt?.time ?: return false
            val ttl = if (response == null || response.isComplete()) finishedJobTtl else unfinishedJobTtl
            return System.currentTimeMillis() - written >= ttl.inWholeMilliseconds
        }

        /** Purges expired rows, at most once per [cleanupInterval]; a failure here never fails a [store]. */
        private fun purgeExpiredIfDue() {
            val now = System.currentTimeMillis()
            val last = lastCleanupMillis.get()
            if (now - last < cleanupInterval.inWholeMilliseconds || !lastCleanupMillis.compareAndSet(last, now)) return
            try {
                val shortest = minOf(finishedJobTtl, unfinishedJobTtl)
                val bound = Timestamp(now - shortest.inWholeMilliseconds)
                purge("updated_at", bound) { response, updatedAt -> isExpired(response, updatedAt) }
            } catch (e: Exception) {
                log.warn("Purging expired DID registration jobs failed: {}", e.toString())
            }
        }

        /**
         * Deletes rows whose [column] is before [bound] and for which [shouldDelete] holds.
         *
         * The decision is made in Kotlin on the parsed job, not in SQL: the state lives inside a JSON
         * document and JSON path syntax differs between PostgreSQL, MySQL and H2. Rows are visited in
         * key order in bounded batches, so memory stays flat and rows that are kept are not revisited.
         *
         * @return the number of rows deleted
         */
        private fun purge(
            column: String,
            bound: Timestamp,
            shouldDelete: (DidRegistrationResponse?, Timestamp) -> Boolean,
        ): Int {
            var deleted = 0
            var after = ""
            while (true) {
                val batch = ArrayList<String>()
                var lastKey: String? = null
                dataSource.connection.use { conn ->
                    val sql =
                        "SELECT job_id, response_data, $column FROM $tableName " +
                            "WHERE $column < ? AND job_id > ? ORDER BY job_id"
                    conn.prepareStatement(sql).use { stmt ->
                        stmt.maxRows = PURGE_BATCH
                        stmt.setTimestamp(1, bound)
                        stmt.setString(2, after)
                        stmt.executeQuery().use { rs ->
                            while (rs.next()) {
                                val key = rs.getString("job_id")
                                lastKey = key
                                val response =
                                    try {
                                        json.decodeFromString<DidRegistrationResponse>(rs.getString("response_data"))
                                    } catch (e: Exception) {
                                        null
                                    }
                                if (shouldDelete(response, rs.getTimestamp(column))) batch.add(key)
                            }
                        }
                    }
                    conn.prepareStatement("DELETE FROM $tableName WHERE job_id = ?").use { stmt ->
                        for (key in batch) {
                            stmt.setString(1, key)
                            deleted += stmt.executeUpdate()
                        }
                    }
                }
                after = lastKey ?: return deleted
            }
        }

        /**
         * Cleans up old completed jobs from the database.
         *
         * @param olderThanDays Remove jobs older than this many days (default: 30)
         * @return Number of jobs removed
         */
        fun cleanupCompletedJobs(olderThanDays: Int = 30): Int {
            require(olderThanDays >= 0) { "olderThanDays must not be negative" }
            val bound = Timestamp(System.currentTimeMillis() - olderThanDays * MILLIS_PER_DAY)
            // Only finished or failed jobs are removed. A row that cannot be parsed is left alone.
            return purge("created_at", bound) { response, _ -> response != null && response.isComplete() }
        }

        /**
         * Gets the count of jobs in storage.
         *
         * @return Total number of jobs
         */
        fun count(): Long {
            dataSource.connection.use { conn ->
                val sql = "SELECT COUNT(*) as count FROM $tableName"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            return rs.getLong("count")
                        }
                    }
                }
            }
            return 0
        }
    }

private val log = LoggerFactory.getLogger(DatabaseJobStorage::class.java)
private val TABLE_NAME = Regex("^[A-Za-z_][A-Za-z0-9_]{0,62}$")
private const val PURGE_BATCH = 500
private const val MILLIS_PER_DAY = 86_400_000L
