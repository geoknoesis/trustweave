package org.trustweave.registry.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.trustweave.registry.*
import java.sql.Timestamp
import javax.sql.DataSource
import kotlin.time.Clock
import kotlin.time.toKotlinInstant

/**
 * JDBC-backed [TrustRegistry].
 *
 * Follows the [TrustRegistry] contract: registering an existing DID throws
 * [ParticipantAlreadyRegisteredException] (the primary key rejects the insert and the existing
 * row is left untouched), updates are a single `UPDATE ... COALESCE` statement read back in the
 * same transaction, and the revocation reason is stored in the `revocation_reason` column
 * (added to pre-existing tables on start-up).
 *
 * Listings filter, order (`registered_at`, `did`) and page in SQL. Every registration, revocation
 * and activation is also appended, in the same transaction as the change, to
 * `registry_status_history` (see [statusHistory]).
 *
 * Schema migration is idempotent and safe when several instances start at once: a missing column
 * is added with `ADD COLUMN IF NOT EXISTS` where the database supports it, and a "column already
 * exists" error from a concurrent starter is treated as success.
 */
class DatabaseTrustRegistry(
    private val dataSource: DataSource,
) : TrustRegistry {
    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        val SCHEMA_LOCK = Any()
    }

    init {
        initializeSchema()
    }

    private fun initializeSchema() {
        // Serialises start-up inside this JVM (some embedded databases, H2 among them, do not make
        // concurrent DDL on one table safe); other processes rely on the idempotent statements.
        synchronized(SCHEMA_LOCK) { initializeSchemaLocked() }
    }

    private fun initializeSchemaLocked() {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn
                    .prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS registry_issuers (
                            did VARCHAR(512) PRIMARY KEY,
                            name VARCHAR(255) NOT NULL,
                            description TEXT,
                            credential_types TEXT NOT NULL DEFAULT '[]',
                            service_endpoint VARCHAR(1024),
                            status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                            registered_at TIMESTAMP NOT NULL,
                            updated_at TIMESTAMP NOT NULL,
                            metadata TEXT NOT NULL DEFAULT '{}',
                            revocation_reason TEXT
                        )
                        """.trimIndent(),
                    ).execute()
                conn
                    .prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS registry_verifiers (
                            did VARCHAR(512) PRIMARY KEY,
                            name VARCHAR(255) NOT NULL,
                            description TEXT,
                            service_endpoint VARCHAR(1024),
                            status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                            registered_at TIMESTAMP NOT NULL,
                            updated_at TIMESTAMP NOT NULL,
                            metadata TEXT NOT NULL DEFAULT '{}',
                            revocation_reason TEXT
                        )
                        """.trimIndent(),
                    ).execute()
                conn
                    .prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS registry_status_history (
                            id VARCHAR(36) PRIMARY KEY,
                            role VARCHAR(16) NOT NULL,
                            did VARCHAR(512) NOT NULL,
                            old_status VARCHAR(32),
                            new_status VARCHAR(32) NOT NULL,
                            reason TEXT,
                            changed_at TIMESTAMP NOT NULL
                        )
                        """.trimIndent(),
                    ).execute()
                addColumnIfMissing(conn, "registry_issuers", "revocation_reason", "TEXT")
                addColumnIfMissing(conn, "registry_verifiers", "revocation_reason", "TEXT")
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw RuntimeException("Failed to initialise trust registry schema: ${e.message}", e)
            }
        }
    }

    /**
     * Schema migration for tables created before a column existed.
     *
     * Not a check-then-act: the metadata probe is only a fast path. The `ALTER` itself is
     * idempotent (`ADD COLUMN IF NOT EXISTS` on PostgreSQL / H2 / MariaDB), and on databases
     * without that clause (MySQL) a duplicate-column failure from a concurrent starter is swallowed.
     * On PostgreSQL the `ALTER` runs under a savepoint so a swallowed failure cannot abort the
     * surrounding schema transaction.
     */
    private fun addColumnIfMissing(
        conn: java.sql.Connection,
        table: String,
        column: String,
        type: String,
    ) {
        val exists =
            listOf(table, table.uppercase()).any { t ->
                listOf(column, column.uppercase()).any { c ->
                    conn.metaData.getColumns(null, null, t, c).use { it.next() }
                }
            }
        if (exists) return
        val product = conn.metaData.databaseProductName.lowercase()
        val supportsIfNotExists = product.contains("postgres") || product.contains("h2") || product.contains("mariadb")
        val sql =
            if (supportsIfNotExists) {
                "ALTER TABLE $table ADD COLUMN IF NOT EXISTS $column $type"
            } else {
                "ALTER TABLE $table ADD COLUMN $column $type"
            }
        val savepoint = if (supportsIfNotExists && !conn.autoCommit) conn.setSavepoint() else null
        try {
            conn.prepareStatement(sql).use { it.execute() }
        } catch (e: java.sql.SQLException) {
            if (!isDuplicateColumn(e)) throw e
            savepoint?.let { conn.rollback(it) }
        }
    }

    private fun isDuplicateColumn(e: java.sql.SQLException): Boolean =
        e.sqlState == "42701" ||
            // PostgreSQL duplicate_column
            e.sqlState == "42S21" ||
            // MySQL / H2 column already exists
            e.errorCode == 1060 ||
            // MySQL ER_DUP_FIELDNAME
            e.errorCode == 42121 // H2 DUPLICATE_COLUMN_NAME_1

    private fun recordChange(
        conn: java.sql.Connection,
        role: ParticipantRole,
        did: String,
        from: AccreditationStatus?,
        to: AccreditationStatus,
        reason: String?,
        at: java.time.Instant,
    ) {
        conn
            .prepareStatement(
                "INSERT INTO registry_status_history (id,role,did,old_status,new_status,reason,changed_at) VALUES (?,?,?,?,?,?,?)",
            ).use {
                it.setString(
                    1,
                    java.util.UUID
                        .randomUUID()
                        .toString(),
                )
                it.setString(2, role.name)
                it.setString(3, did)
                it.setString(4, from?.name)
                it.setString(5, to.name)
                it.setString(6, reason)
                it.setTimestamp(7, Timestamp.from(at))
                it.executeUpdate()
            }
    }

    override suspend fun statusHistory(did: String): List<StatusChange> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn
                    .prepareStatement(
                        "SELECT role,old_status,new_status,reason,changed_at FROM registry_status_history " +
                            "WHERE did = ? ORDER BY changed_at, id",
                    ).use { st ->
                        st.setString(1, did)
                        st.executeQuery().use { rs ->
                            val out = mutableListOf<StatusChange>()
                            while (rs.next()) {
                                out +=
                                    StatusChange(
                                        did = did,
                                        role = ParticipantRole.valueOf(rs.getString("role")),
                                        from = rs.getString("old_status")?.let { AccreditationStatus.valueOf(it) },
                                        to = AccreditationStatus.valueOf(rs.getString("new_status")),
                                        reason = rs.getString("reason"),
                                        changedAt = rs.getTimestamp("changed_at").toInstant().toKotlinInstant(),
                                    )
                            }
                            out
                        }
                    }
            }
        }

    /** Runs an INSERT, translating a primary-key clash into [ParticipantAlreadyRegisteredException]. */
    private suspend fun insertOrConflict(
        did: String,
        role: String,
        exists: suspend () -> Boolean,
        insert: () -> Unit,
    ) {
        try {
            insert()
        } catch (e: java.sql.SQLException) {
            val integrityViolation = e is java.sql.SQLIntegrityConstraintViolationException || e.sqlState?.startsWith("23") == true
            if (integrityViolation && exists()) throw ParticipantAlreadyRegisteredException(did, role)
            throw e
        }
    }

    override suspend fun registerIssuer(registration: IssuerRegistration): IssuerRecord =
        withContext(Dispatchers.IO) {
            val now = Clock.System.now()
            insertOrConflict(registration.did, "Issuer", { getIssuer(registration.did) != null }) {
                dataSource.connection.use { conn ->
                    inTransaction(conn) {
                        conn
                            .prepareStatement(
                                "INSERT INTO registry_issuers (did,name,description,credential_types,service_endpoint,status,registered_at,updated_at,metadata) VALUES (?,?,?,?,?,?,?,?,?)",
                            ).apply {
                                setString(1, registration.did)
                                setString(2, registration.name)
                                setString(3, registration.description)
                                setString(4, json.encodeToString(registration.credentialTypes))
                                setString(5, registration.serviceEndpoint)
                                setString(6, AccreditationStatus.ACTIVE.name)
                                setTimestamp(7, Timestamp(now.toEpochMilliseconds()))
                                setTimestamp(8, Timestamp(now.toEpochMilliseconds()))
                                setString(9, json.encodeToString(registration.metadata))
                            }.executeUpdate()
                        recordChange(
                            conn,
                            ParticipantRole.ISSUER,
                            registration.did,
                            null,
                            AccreditationStatus.ACTIVE,
                            null,
                            java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()),
                        )
                    }
                }
            }
            IssuerRecord(
                did = registration.did,
                name = registration.name,
                description = registration.description,
                credentialTypes = registration.credentialTypes,
                serviceEndpoint = registration.serviceEndpoint,
                status = AccreditationStatus.ACTIVE,
                registeredAt = now,
                updatedAt = now,
                metadata = registration.metadata,
            )
        }

    override suspend fun getIssuer(did: String): IssuerRecord? =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn
                    .prepareStatement("SELECT * FROM registry_issuers WHERE did = ?")
                    .apply { setString(1, did) }
                    .executeQuery()
                    .let { rs -> if (rs.next()) rowToIssuer(rs) else null }
            }
        }

    override suspend fun listIssuers(filter: RegistryFilter): List<IssuerRecord> = queryIssuers(filter, null, 0)

    override suspend fun listIssuers(
        filter: RegistryFilter,
        limit: Int,
        offset: Int,
    ): List<IssuerRecord> {
        requirePage(limit, offset)
        return queryIssuers(filter, limit, offset)
    }

    private suspend fun queryIssuers(
        filter: RegistryFilter,
        limit: Int?,
        offset: Int,
    ): List<IssuerRecord> =
        withContext(Dispatchers.IO) {
            val where = whereClause(filter, withCredentialType = true)
            selectPage("registry_issuers", where, limit, offset, ::rowToIssuer).filter { r ->
                // The SQL LIKE is a coarse match on the JSON array text; confirm exactly.
                filter.credentialType == null || r.credentialTypes.contains(filter.credentialType)
            }
        }

    private class Where(
        val sql: String,
        val args: List<String>,
    )

    private fun whereClause(
        filter: RegistryFilter,
        withCredentialType: Boolean,
    ): Where {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        filter.status?.let {
            clauses += "status = ?"
            args += it.name
        }
        if (withCredentialType) {
            filter.credentialType?.let {
                clauses += "credential_types LIKE ? ESCAPE '!'"
                args += "%" + escapeLike(json.encodeToString(it)) + "%"
            }
        }
        filter.nameContains?.let {
            clauses += "LOWER(name) LIKE ? ESCAPE '!'"
            args += "%" + escapeLike(it.lowercase(java.util.Locale.ROOT)) + "%"
        }
        return Where(if (clauses.isEmpty()) "" else " WHERE " + clauses.joinToString(" AND "), args)
    }

    /** Escapes LIKE wildcards with `!` (a dialect-neutral escape character). */
    private fun escapeLike(raw: String): String = raw.replace("!", "!!").replace("%", "!%").replace("_", "!_")

    private fun <T> selectPage(
        table: String,
        where: Where,
        limit: Int?,
        offset: Int,
        map: (java.sql.ResultSet) -> T,
    ): List<T> =
        dataSource.connection.use { conn ->
            val paging = if (limit != null) " LIMIT ? OFFSET ?" else ""
            conn.prepareStatement("SELECT * FROM $table${where.sql} ORDER BY registered_at, did$paging").use { st ->
                var i = 1
                where.args.forEach { st.setString(i++, it) }
                if (limit != null) {
                    st.setInt(i++, limit)
                    st.setInt(i, offset)
                }
                st.executeQuery().use { rs ->
                    val out = mutableListOf<T>()
                    while (rs.next()) out.add(map(rs))
                    out
                }
            }
        }

    override suspend fun updateIssuer(
        did: String,
        update: IssuerUpdate,
    ): IssuerRecord =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                inTransaction(conn) {
                    val rows =
                        conn
                            .prepareStatement(
                                "UPDATE registry_issuers SET name=COALESCE(?,name),description=COALESCE(?,description)," +
                                    "credential_types=COALESCE(?,credential_types),service_endpoint=COALESCE(?,service_endpoint)," +
                                    "metadata=COALESCE(?,metadata),updated_at=? WHERE did=?",
                            ).apply {
                                setString(1, update.name)
                                setString(2, update.description)
                                setString(3, update.credentialTypes?.let { json.encodeToString(it) })
                                setString(4, update.serviceEndpoint)
                                setString(5, update.metadata?.let { json.encodeToString(it) })
                                setTimestamp(6, Timestamp(Clock.System.now().toEpochMilliseconds()))
                                setString(7, did)
                            }.executeUpdate()
                    if (rows == 0) throw NoSuchElementException("Issuer not found: $did")
                    conn
                        .prepareStatement("SELECT * FROM registry_issuers WHERE did = ?")
                        .apply { setString(1, did) }
                        .executeQuery()
                        .let { rs ->
                            rs.next()
                            rowToIssuer(rs)
                        }
                }
            }
        }

    override suspend fun revokeIssuer(
        did: String,
        reason: String?,
    ): Boolean =
        withContext(Dispatchers.IO) {
            updateIssuerStatus(did, AccreditationStatus.REVOKED, reason)
        }

    override suspend fun activateIssuer(did: String): Boolean =
        withContext(Dispatchers.IO) {
            updateIssuerStatus(did, AccreditationStatus.ACTIVE, null)
        }

    override suspend fun registerVerifier(registration: VerifierRegistration): VerifierRecord =
        withContext(Dispatchers.IO) {
            val now = Clock.System.now()
            insertOrConflict(registration.did, "Verifier", { getVerifier(registration.did) != null }) {
                dataSource.connection.use { conn ->
                    inTransaction(conn) {
                        conn
                            .prepareStatement(
                                "INSERT INTO registry_verifiers (did,name,description,service_endpoint,status,registered_at,updated_at,metadata) VALUES (?,?,?,?,?,?,?,?)",
                            ).apply {
                                setString(1, registration.did)
                                setString(2, registration.name)
                                setString(3, registration.description)
                                setString(4, registration.serviceEndpoint)
                                setString(5, AccreditationStatus.ACTIVE.name)
                                setTimestamp(6, Timestamp(now.toEpochMilliseconds()))
                                setTimestamp(7, Timestamp(now.toEpochMilliseconds()))
                                setString(8, json.encodeToString(registration.metadata))
                            }.executeUpdate()
                        recordChange(
                            conn,
                            ParticipantRole.VERIFIER,
                            registration.did,
                            null,
                            AccreditationStatus.ACTIVE,
                            null,
                            java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()),
                        )
                    }
                }
            }
            VerifierRecord(
                did = registration.did,
                name = registration.name,
                description = registration.description,
                serviceEndpoint = registration.serviceEndpoint,
                status = AccreditationStatus.ACTIVE,
                registeredAt = now,
                updatedAt = now,
                metadata = registration.metadata,
            )
        }

    override suspend fun getVerifier(did: String): VerifierRecord? =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn
                    .prepareStatement("SELECT * FROM registry_verifiers WHERE did = ?")
                    .apply { setString(1, did) }
                    .executeQuery()
                    .let { rs -> if (rs.next()) rowToVerifier(rs) else null }
            }
        }

    override suspend fun listVerifiers(filter: RegistryFilter): List<VerifierRecord> = queryVerifiers(filter, null, 0)

    override suspend fun listVerifiers(
        filter: RegistryFilter,
        limit: Int,
        offset: Int,
    ): List<VerifierRecord> {
        requirePage(limit, offset)
        return queryVerifiers(filter, limit, offset)
    }

    private suspend fun queryVerifiers(
        filter: RegistryFilter,
        limit: Int?,
        offset: Int,
    ): List<VerifierRecord> =
        withContext(Dispatchers.IO) {
            selectPage("registry_verifiers", whereClause(filter, withCredentialType = false), limit, offset, ::rowToVerifier)
        }

    override suspend fun updateVerifier(
        did: String,
        update: VerifierUpdate,
    ): VerifierRecord =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                inTransaction(conn) {
                    val rows =
                        conn
                            .prepareStatement(
                                "UPDATE registry_verifiers SET name=COALESCE(?,name),description=COALESCE(?,description)," +
                                    "service_endpoint=COALESCE(?,service_endpoint),metadata=COALESCE(?,metadata),updated_at=? WHERE did=?",
                            ).apply {
                                setString(1, update.name)
                                setString(2, update.description)
                                setString(3, update.serviceEndpoint)
                                setString(4, update.metadata?.let { json.encodeToString(it) })
                                setTimestamp(5, Timestamp(Clock.System.now().toEpochMilliseconds()))
                                setString(6, did)
                            }.executeUpdate()
                    if (rows == 0) throw NoSuchElementException("Verifier not found: $did")
                    conn
                        .prepareStatement("SELECT * FROM registry_verifiers WHERE did = ?")
                        .apply { setString(1, did) }
                        .executeQuery()
                        .let { rs ->
                            rs.next()
                            rowToVerifier(rs)
                        }
                }
            }
        }

    private fun <T> inTransaction(
        conn: java.sql.Connection,
        block: () -> T,
    ): T {
        val previous = conn.autoCommit
        conn.autoCommit = false
        try {
            val result = block()
            conn.commit()
            return result
        } catch (e: Throwable) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = previous
        }
    }

    override suspend fun revokeVerifier(
        did: String,
        reason: String?,
    ): Boolean =
        withContext(Dispatchers.IO) {
            updateVerifierStatus(did, AccreditationStatus.REVOKED, reason)
        }

    override suspend fun activateVerifier(did: String): Boolean =
        withContext(Dispatchers.IO) {
            updateVerifierStatus(did, AccreditationStatus.ACTIVE, null)
        }

    override suspend fun getAccreditationStatus(did: String): AccreditationStatus =
        getIssuer(did)?.status ?: getVerifier(did)?.status ?: AccreditationStatus.UNKNOWN

    override suspend fun listCredentialTypes(): List<String> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT credential_types FROM registry_issuers").use { st ->
                    st.executeQuery().use { rs ->
                        val out = mutableListOf<String>()
                        while (rs.next()) out += json.decodeFromString<List<String>>(rs.getString(1))
                        out.distinct().sorted()
                    }
                }
            }
        }

    private fun updateIssuerStatus(
        did: String,
        status: AccreditationStatus,
        reason: String?,
    ): Boolean = updateStatus("registry_issuers", ParticipantRole.ISSUER, did, status, reason)

    private fun updateVerifierStatus(
        did: String,
        status: AccreditationStatus,
        reason: String?,
    ): Boolean = updateStatus("registry_verifiers", ParticipantRole.VERIFIER, did, status, reason)

    /** Reads the old status under a row lock, updates, and appends the history row — one transaction. */
    private fun updateStatus(
        table: String,
        role: ParticipantRole,
        did: String,
        status: AccreditationStatus,
        reason: String?,
    ): Boolean =
        dataSource.connection.use { conn ->
            inTransaction(conn) {
                val previous =
                    conn.prepareStatement("SELECT status FROM $table WHERE did=? FOR UPDATE").use { st ->
                        st.setString(1, did)
                        st.executeQuery().use { rs -> if (rs.next()) AccreditationStatus.valueOf(rs.getString(1)) else null }
                    } ?: return@inTransaction false
                val at = java.time.Instant.ofEpochMilli(Clock.System.now().toEpochMilliseconds())
                conn.prepareStatement("UPDATE $table SET status=?,revocation_reason=?,updated_at=? WHERE did=?").use { st ->
                    st.setString(1, status.name)
                    st.setString(2, reason)
                    st.setTimestamp(3, Timestamp.from(at))
                    st.setString(4, did)
                    st.executeUpdate()
                }
                recordChange(conn, role, did, previous, status, reason, at)
                true
            }
        }

    private fun rowToIssuer(rs: java.sql.ResultSet): IssuerRecord =
        IssuerRecord(
            did = rs.getString("did"),
            name = rs.getString("name"),
            description = rs.getString("description"),
            credentialTypes = json.decodeFromString(rs.getString("credential_types")),
            serviceEndpoint = rs.getString("service_endpoint"),
            status = AccreditationStatus.valueOf(rs.getString("status")),
            registeredAt = rs.getTimestamp("registered_at").toInstant().toKotlinInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant().toKotlinInstant(),
            metadata = json.decodeFromString(rs.getString("metadata")),
            revocationReason = rs.getString("revocation_reason"),
        )

    private fun rowToVerifier(rs: java.sql.ResultSet): VerifierRecord =
        VerifierRecord(
            did = rs.getString("did"),
            name = rs.getString("name"),
            description = rs.getString("description"),
            serviceEndpoint = rs.getString("service_endpoint"),
            status = AccreditationStatus.valueOf(rs.getString("status")),
            registeredAt = rs.getTimestamp("registered_at").toInstant().toKotlinInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant().toKotlinInstant(),
            metadata = json.decodeFromString(rs.getString("metadata")),
            revocationReason = rs.getString("revocation_reason"),
        )
}
