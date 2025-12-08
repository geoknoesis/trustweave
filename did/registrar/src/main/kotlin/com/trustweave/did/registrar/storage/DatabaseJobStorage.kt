package com.trustweave.did.registrar.storage

import com.trustweave.did.registrar.model.DidRegistrationResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Timestamp
import kotlinx.datetime.Clock
import javax.sql.DataSource

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
 * @param dataSource JDBC DataSource for database connections
 * @param tableName Name of the table to store jobs (default: "did_registration_jobs")
 */
class DatabaseJobStorage(
    private val dataSource: DataSource,
    private val tableName: String = "did_registration_jobs"
) : JobStorage {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    init {
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
            val createTableSql = when {
                conn.metaData.databaseProductName.lowercase().contains("postgresql") -> {
                    """
                    CREATE TABLE IF NOT EXISTS $tableName (
                        job_id VARCHAR(255) PRIMARY KEY,
                        response_data TEXT NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """.trimIndent()
                }
                conn.metaData.databaseProductName.lowercase().contains("mysql") -> {
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
                val createIndexSql = when {
                    conn.metaData.databaseProductName.lowercase().contains("postgresql") -> {
                        "CREATE INDEX IF NOT EXISTS $indexName ON $tableName(created_at)"
                    }
                    conn.metaData.databaseProductName.lowercase().contains("mysql") -> {
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

    override fun store(jobId: String, response: DidRegistrationResponse) {
        val jsonData = json.encodeToString(response)
        val now = Timestamp.from(java.time.Instant.ofEpochSecond(Clock.System.now().epochSeconds))

        dataSource.connection.use { conn ->
            // Use INSERT ... ON CONFLICT UPDATE (PostgreSQL) or REPLACE INTO (MySQL/H2)
            val sql = when {
                conn.metaData.databaseProductName.lowercase().contains("postgresql") -> {
                    """
                    INSERT INTO $tableName (job_id, response_data, created_at, updated_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (job_id) DO UPDATE SET
                        response_data = EXCLUDED.response_data,
                        updated_at = EXCLUDED.updated_at
                    """.trimIndent()
                }
                conn.metaData.databaseProductName.lowercase().contains("mysql") -> {
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
    }

    override fun get(jobId: String): DidRegistrationResponse? {
        dataSource.connection.use { conn ->
            val sql = "SELECT response_data FROM $tableName WHERE job_id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, jobId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        val jsonData = rs.getString("response_data")
                        return json.decodeFromString<DidRegistrationResponse>(jsonData)
                    }
                }
            }
        }
        return null
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

    override fun exists(jobId: String): Boolean {
        dataSource.connection.use { conn ->
            val sql = "SELECT 1 FROM $tableName WHERE job_id = ? LIMIT 1"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, jobId)
                stmt.executeQuery().use { rs ->
                    return rs.next()
                }
            }
        }
    }

    /**
     * Cleans up old completed jobs from the database.
     *
     * @param olderThanDays Remove jobs older than this many days (default: 30)
     * @return Number of jobs removed
     */
    fun cleanupCompletedJobs(olderThanDays: Int = 30): Int {
        dataSource.connection.use { conn ->
            val sql = when {
                conn.metaData.databaseProductName.lowercase().contains("postgresql") -> {
                    """
                    DELETE FROM $tableName
                    WHERE response_data::jsonb->>'didState'->>'state' IN ('finished', 'failed')
                    AND created_at < CURRENT_TIMESTAMP - INTERVAL '$olderThanDays days'
                    """.trimIndent()
                }
                conn.metaData.databaseProductName.lowercase().contains("mysql") -> {
                    """
                    DELETE FROM $tableName
                    WHERE JSON_EXTRACT(response_data, '$.didState.state') IN ('finished', 'failed')
                    AND created_at < DATE_SUB(NOW(), INTERVAL $olderThanDays DAY)
                    """.trimIndent()
                }
                else -> {
                    // H2 - simpler approach: delete all old jobs
                    // For production, consider parsing JSON or using a separate state column
                    """
                    DELETE FROM $tableName
                    WHERE created_at < DATEADD('DAY', -$olderThanDays, CURRENT_TIMESTAMP)
                    """.trimIndent()
                }
            }

            conn.prepareStatement(sql).use { stmt ->
                return stmt.executeUpdate()
            }
        }
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

