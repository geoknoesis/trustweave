package org.trustweave.revocation.database

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.revocation.RevocationStatus
import org.trustweave.credential.revocation.StatusListMetadata
import org.trustweave.credential.revocation.StatusListStatistics
import org.trustweave.credential.revocation.StatusUpdate
import java.util.Base64
import java.util.BitSet
import java.util.UUID
import javax.sql.DataSource

/**
 * Database-backed status list manager implementation.
 *
 * Provides persistent status list management using a relational database.
 * Supports PostgreSQL, MySQL, and H2: the schema uses portable DDL only.
 *
 * **Example:**
 * ```kotlin
 * val manager = DatabaseStatusListManager(dataSource = dataSource)
 *
 * val statusList = manager.createStatusList(
 *     issuerDid = "did:key:...",
 *     purpose = StatusPurpose.REVOCATION
 * )
 *
 * manager.revokeCredential("cred-123", statusList)
 * ```
 */
class DatabaseStatusListManager(
    private val dataSource: DataSource,
) : CredentialRevocationManager {
    private val json =
        Json {
            prettyPrint = false
            encodeDefaults = false
            ignoreUnknownKeys = true
        }

    init {
        initializeSchema()
    }

    /**
     * Initialize database schema.
     *
     * Creates the schema.
     *
     * Indexes are declared as separate `CREATE INDEX` statements rather than inline `INDEX name
     * (col)` clauses. The inline form is MySQL-only: PostgreSQL rejects it with "syntax error at
     * or near \"(\"", so every caller on Postgres — the database this manager is most often
     * pointed at — failed at construction, since [initializeSchema] runs from `init`.
     *
     * `CREATE INDEX IF NOT EXISTS` is supported by PostgreSQL 9.5+, MySQL 8.0.29+ and H2. Index
     * creation is deliberately outside the transaction guard below only in the sense that it is
     * idempotent; a partially-created schema re-runs cleanly.
     */
    private fun initializeSchema() {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn
                    .prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS status_lists (
                            id VARCHAR(255) PRIMARY KEY,
                            issuer_did VARCHAR(255) NOT NULL,
                            purpose VARCHAR(50) NOT NULL,
                            size INT NOT NULL,
                            encoded_list TEXT NOT NULL,
                            status_list_data TEXT NOT NULL,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                        """.trimIndent(),
                    ).execute()

                conn
                    .prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS credential_indices (
                            credential_id VARCHAR(255) NOT NULL,
                            status_list_id VARCHAR(255) NOT NULL,
                            index_value INT NOT NULL,
                            PRIMARY KEY (credential_id, status_list_id),
                            FOREIGN KEY (status_list_id) REFERENCES status_lists(id) ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    ).execute()

                conn
                    .prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS status_list_next_index (
                            status_list_id VARCHAR(255) PRIMARY KEY,
                            next_index INT NOT NULL DEFAULT 0,
                            FOREIGN KEY (status_list_id) REFERENCES status_lists(id) ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    ).execute()

                listOf(
                    "CREATE INDEX IF NOT EXISTS idx_status_lists_issuer ON status_lists (issuer_did)",
                    "CREATE INDEX IF NOT EXISTS idx_status_lists_purpose ON status_lists (purpose)",
                    "CREATE INDEX IF NOT EXISTS idx_credential_indices_list ON credential_indices (status_list_id)",
                    "CREATE INDEX IF NOT EXISTS idx_credential_indices_entry ON credential_indices (status_list_id, index_value)",
                    "CREATE INDEX IF NOT EXISTS idx_credential_indices_credential ON credential_indices (credential_id)",
                ).forEach { conn.prepareStatement(it).execute() }

                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw RuntimeException("Failed to initialize database schema: ${e.message}", e)
            }
        }
    }

    override suspend fun createStatusList(
        issuerDid: String,
        purpose: StatusPurpose,
        size: Int,
        customId: String?,
    ): StatusListId =
        withContext(Dispatchers.IO) {
            val id = customId ?: UUID.randomUUID().toString()
            val bitSet = BitSet(size)
            val encodedList = encodeBitSet(bitSet, size)
            val statusListJson =
                buildJsonObject {
                    put("id", id)
                    put(
                        "type",
                        buildJsonArray {
                            add("VerifiableCredential")
                            add("StatusList2021Credential")
                        },
                    )
                    put("issuer", issuerDid)
                    put(
                        "credentialSubject",
                        buildJsonObject {
                            put("id", id)
                            put("type", "StatusList2021")
                            put("statusPurpose", purpose.name.lowercase())
                            put("encodedList", encodedList)
                        },
                    )
                    put("issuanceDate", Clock.System.now().toString())
                }

            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    val stmt =
                        conn.prepareStatement(
                            """
                    INSERT INTO status_lists (id, issuer_did, purpose, size, encoded_list, status_list_data)
                    VALUES (?, ?, ?, ?, ?, ?)
                """,
                        )
                    stmt.setString(1, id)
                    stmt.setString(2, issuerDid)
                    stmt.setString(3, purpose.name)
                    stmt.setInt(4, size)
                    stmt.setString(5, encodedList)
                    stmt.setString(6, json.encodeToString(JsonObject.serializer(), statusListJson))
                    stmt.executeUpdate()

                    val nextIndexStmt =
                        conn.prepareStatement(
                            """
                    INSERT INTO status_list_next_index (status_list_id, next_index)
                    VALUES (?, 0)
                """,
                        )
                    nextIndexStmt.setString(1, id)
                    nextIndexStmt.executeUpdate()

                    conn.commit()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    conn.rollback()
                    throw RuntimeException("Failed to create status list: ${e.message}", e)
                }
            }

            StatusListId(id)
        }

    override suspend fun revokeCredential(
        credentialId: String,
        statusListId: StatusListId,
    ): Boolean =
        withContext(Dispatchers.IO) {
            updateCredentialStatus(credentialId, statusListId.toString(), revoked = true, suspended = null)
        }

    override suspend fun suspendCredential(
        credentialId: String,
        statusListId: StatusListId,
    ): Boolean =
        withContext(Dispatchers.IO) {
            updateCredentialStatus(credentialId, statusListId.toString(), revoked = null, suspended = true)
        }

    override suspend fun unrevokeCredential(
        credentialId: String,
        statusListId: StatusListId,
    ): Boolean =
        withContext(Dispatchers.IO) {
            updateCredentialStatus(credentialId, statusListId.toString(), revoked = false, suspended = null)
        }

    override suspend fun unsuspendCredential(
        credentialId: String,
        statusListId: StatusListId,
    ): Boolean =
        withContext(Dispatchers.IO) {
            updateCredentialStatus(credentialId, statusListId.toString(), revoked = null, suspended = false)
        }

    private suspend fun updateCredentialStatus(
        credentialId: String,
        statusListId: String,
        revoked: Boolean?,
        suspended: Boolean?,
    ): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    // Get status list
                    val size = lockStatusList(statusListId, conn)
                    val statusListJson = getStatusListFromDb(statusListId, conn) ?: return@withContext false
                    val purpose = extractPurpose(statusListJson)
                    val encodedList = extractEncodedList(statusListJson)

                    // Validate purpose matches operation
                    if (revoked != null && purpose != StatusPurpose.REVOCATION) return@withContext false
                    if (suspended != null && purpose != StatusPurpose.SUSPENSION) return@withContext false

                    // Get or assign index
                    val index = getOrAssignIndex(credentialId, statusListId, conn)

                    // Load and update bit set
                    val bitSet = decodeBitSet(encodedList)
                    if (revoked != null) {
                        bitSet.set(index, revoked)
                    } else if (suspended != null) {
                        bitSet.set(index, suspended)
                    }

                    // Update encoded list
                    val newEncodedList = encodeBitSet(bitSet, size)
                    val updatedStatusListJson = updateEncodedList(statusListJson, newEncodedList)

                    // Save to database
                    val updateStmt =
                        conn.prepareStatement(
                            """
                    UPDATE status_lists
                    SET encoded_list = ?, status_list_data = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                """,
                        )
                    updateStmt.setString(1, newEncodedList)
                    updateStmt.setString(2, json.encodeToString(JsonObject.serializer(), updatedStatusListJson))
                    updateStmt.setString(3, statusListId)
                    updateStmt.executeUpdate()

                    conn.commit()
                    true
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    conn.rollback()
                    throw cancelled
                } catch (e: Exception) {
                    conn.rollback()
                    false
                }
            }
        }

    override suspend fun checkRevocationStatus(credential: VerifiableCredential): RevocationStatus =
        withContext(Dispatchers.IO) {
            val credentialStatus =
                credential.credentialStatus ?: return@withContext RevocationStatus(
                    revoked = false,
                    suspended = false,
                )

            val statusListId = credentialStatus.statusListCredential ?: credentialStatus.id

            val explicitIndex = credentialStatus.statusListIndex
            val index =
                if (explicitIndex != null) {
                    requireNotNull(explicitIndex.toIntOrNull()) { "Invalid status list index" }
                } else {
                    val credentialId = requireNotNull(credential.id) { "Status-bearing credential has no index or identifier" }
                    requireNotNull(getCredentialIndex(credentialId.toString(), statusListId)) { "Credential status index is unknown" }
                }

            checkStatusByIndex(statusListId, index)
        }

    override suspend fun checkStatusByIndex(
        statusListId: StatusListId,
        index: Int,
    ): RevocationStatus =
        withContext(Dispatchers.IO) {
            val metadata = requireNotNull(getStatusList(statusListId)) { "Status list is unknown" }
            require(index >= 0 && index < metadata.size) { "Status list index is out of bounds" }
            val statusListJson = requireNotNull(getStatusListFromDb(statusListId.toString())) { "Status list is unknown" }

            val encodedList = extractEncodedList(statusListJson)
            val bitSet = decodeBitSet(encodedList)
            val purpose = extractPurpose(statusListJson)
            val isSet = bitSet.get(index)

            RevocationStatus(
                revoked = isSet && purpose == StatusPurpose.REVOCATION,
                suspended = isSet && purpose == StatusPurpose.SUSPENSION,
                statusListId = statusListId,
                index = index,
            )
        }

    override suspend fun checkStatusByCredentialId(
        credentialId: String,
        statusListId: StatusListId,
    ): RevocationStatus =
        withContext(Dispatchers.IO) {
            val index = requireNotNull(getCredentialIndex(credentialId, statusListId)) { "Credential status index is unknown" }

            checkStatusByIndex(statusListId, index)
        }

    override suspend fun getCredentialIndex(
        credentialId: String,
        statusListId: StatusListId,
    ): Int? =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val stmt =
                    conn.prepareStatement(
                        """
                SELECT index_value FROM credential_indices
                WHERE credential_id = ? AND status_list_id = ?
            """,
                    )
                stmt.setString(1, credentialId)
                stmt.setString(2, statusListId.toString())
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    rs.getInt("index_value")
                } else {
                    null
                }
            }
        }

    override suspend fun assignCredentialIndex(
        credentialId: String,
        statusListId: StatusListId,
        index: Int?,
    ): Int =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    val size = lockStatusList(statusListId.toString(), conn)
                    val existing =
                        conn
                            .prepareStatement(
                                "SELECT index_value FROM credential_indices WHERE credential_id = ? AND status_list_id = ?",
                            ).use { statement ->
                                statement.setString(1, credentialId)
                                statement.setString(2, statusListId.toString())
                                statement.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else null }
                            }
                    if (existing != null) {
                        require(index == null || index == existing) { "Credential already has a different status index" }
                        conn.commit()
                        return@withContext existing
                    }
                    val assignedIndex = index ?: getNextAvailableIndex(statusListId.toString(), conn)
                    require(assignedIndex in 0 until size) { "Status index is outside the list" }
                    conn
                        .prepareStatement(
                            "SELECT COUNT(*) FROM credential_indices WHERE status_list_id = ? AND index_value = ?",
                        ).use { statement ->
                            statement.setString(1, statusListId.toString())
                            statement.setInt(2, assignedIndex)
                            statement.executeQuery().use { rows ->
                                check(rows.next())
                                require(rows.getInt(1) == 0) { "Status index is already assigned" }
                            }
                        }
                    conn
                        .prepareStatement(
                            "INSERT INTO credential_indices (credential_id, status_list_id, index_value) VALUES (?, ?, ?)",
                        ).use { statement ->
                            statement.setString(1, credentialId)
                            statement.setString(2, statusListId.toString())
                            statement.setInt(3, assignedIndex)
                            statement.executeUpdate()
                        }

                    conn.commit()
                    assignedIndex
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                }
            }
        }

    override suspend fun updateStatusListBatch(
        statusListId: StatusListId,
        updates: List<StatusUpdate>,
    ): Unit =
        withContext(Dispatchers.IO) {
            mutateStatusList(statusListId.toString()) { conn, size, document ->
                val purpose = extractPurpose(document)
                for (update in updates) {
                    require(update.index in 0 until size) { "Status index is outside the list" }
                    require(
                        (purpose == StatusPurpose.REVOCATION && update.revoked != null && update.suspended == null) ||
                            (purpose == StatusPurpose.SUSPENSION && update.suspended != null && update.revoked == null),
                    ) { "Status update does not match list purpose" }
                }
                val bits = decodeBitSet(extractEncodedList(document))
                for (update in updates) bits.set(update.index, update.revoked ?: checkNotNull(update.suspended))
                val encoded = encodeBitSet(bits, size)
                updateStatusListInDb(statusListId.toString(), updateEncodedList(document, encoded), encoded, conn)
            }
        }

    override suspend fun getStatusList(statusListId: StatusListId): StatusListMetadata? =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val stmt =
                    conn.prepareStatement(
                        """
                SELECT id, issuer_did, purpose, size, created_at, updated_at
                FROM status_lists WHERE id = ?
            """,
                    )
                stmt.setString(1, statusListId.toString())
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    val createdAt =
                        rs.getTimestamp("created_at")?.let { ts ->
                            val javaInstant = ts.toInstant()
                            Instant.fromEpochSeconds(javaInstant.epochSecond, javaInstant.nano)
                        } ?: Clock.System.now()
                    val lastUpdated =
                        rs.getTimestamp("updated_at")?.let { ts ->
                            val javaInstant = ts.toInstant()
                            Instant.fromEpochSeconds(javaInstant.epochSecond, javaInstant.nano)
                        } ?: Clock.System.now()
                    val purposeStr = rs.getString("purpose")
                    val purpose =
                        try {
                            StatusPurpose.valueOf(purposeStr.uppercase())
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (e: Exception) {
                            StatusPurpose.REVOCATION // Default fallback
                        }
                    StatusListMetadata(
                        id = statusListId,
                        issuerDid = rs.getString("issuer_did"),
                        purpose = purpose,
                        size = rs.getInt("size"),
                        createdAt = createdAt,
                        lastUpdated = lastUpdated,
                    )
                } else {
                    null
                }
            }
        }

    override suspend fun listStatusLists(issuerDid: String?): List<StatusListMetadata> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql =
                    if (issuerDid != null) {
                        "SELECT id, issuer_did, purpose, size, created_at, updated_at FROM status_lists WHERE issuer_did = ?"
                    } else {
                        "SELECT id, issuer_did, purpose, size, created_at, updated_at FROM status_lists"
                    }
                val stmt = conn.prepareStatement(sql)
                if (issuerDid != null) {
                    stmt.setString(1, issuerDid)
                }
                val rs = stmt.executeQuery()
                val results = mutableListOf<StatusListMetadata>()
                while (rs.next()) {
                    val id = StatusListId(rs.getString("id"))
                    val createdAt =
                        rs.getTimestamp("created_at")?.let { ts ->
                            val javaInstant = ts.toInstant()
                            Instant.fromEpochSeconds(javaInstant.epochSecond, javaInstant.nano)
                        } ?: Clock.System.now()
                    val lastUpdated =
                        rs.getTimestamp("updated_at")?.let { ts ->
                            val javaInstant = ts.toInstant()
                            Instant.fromEpochSeconds(javaInstant.epochSecond, javaInstant.nano)
                        } ?: Clock.System.now()
                    val purposeStr = rs.getString("purpose")
                    val purpose =
                        try {
                            StatusPurpose.valueOf(purposeStr.uppercase())
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (e: Exception) {
                            StatusPurpose.REVOCATION // Default fallback
                        }
                    results.add(
                        StatusListMetadata(
                            id = id,
                            issuerDid = rs.getString("issuer_did"),
                            purpose = purpose,
                            size = rs.getInt("size"),
                            createdAt = createdAt,
                            lastUpdated = lastUpdated,
                        ),
                    )
                }
                results
            }
        }

    override suspend fun deleteStatusList(statusListId: StatusListId): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    val stmt = conn.prepareStatement("DELETE FROM status_lists WHERE id = ?")
                    stmt.setString(1, statusListId.toString())
                    val deleted = stmt.executeUpdate() > 0
                    conn.commit()
                    deleted
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    conn.rollback()
                    throw cancelled
                } catch (e: Exception) {
                    conn.rollback()
                    false
                }
            }
        }

    override suspend fun getStatusListStatistics(statusListId: StatusListId): StatusListStatistics? =
        withContext(Dispatchers.IO) {
            val statusListJson = getStatusListFromDb(statusListId.toString()) ?: return@withContext null

            dataSource.connection.use { conn ->
                val usedIndicesStmt =
                    conn.prepareStatement(
                        """
                SELECT COUNT(*) as count FROM credential_indices WHERE status_list_id = ?
            """,
                    )
                usedIndicesStmt.setString(1, statusListId.toString())
                val usedRs = usedIndicesStmt.executeQuery()
                val usedIndices = if (usedRs.next()) usedRs.getInt("count") else 0

                val encodedList = extractEncodedList(statusListJson)
                val bitSet = decodeBitSet(encodedList)
                val totalCapacity = bitSet.size()
                val purpose = extractPurpose(statusListJson)
                val revokedCount = if (purpose == StatusPurpose.REVOCATION) bitSet.cardinality() else 0
                val suspendedCount = if (purpose == StatusPurpose.SUSPENSION) bitSet.cardinality() else 0
                val availableIndices = totalCapacity - usedIndices

                StatusListStatistics(
                    statusListId = statusListId,
                    issuerDid = extractIssuerDid(statusListJson),
                    purpose = purpose,
                    totalCapacity = totalCapacity,
                    usedIndices = usedIndices,
                    revokedCount = revokedCount,
                    suspendedCount = suspendedCount,
                    availableIndices = availableIndices,
                    lastUpdated = Clock.System.now(),
                )
            }
        }

    override suspend fun revokeCredentials(
        credentialIds: List<String>,
        statusListId: StatusListId,
    ): Map<String, Boolean> =
        withContext(Dispatchers.IO) {
            try {
                mutateStatusList(statusListId.toString()) { conn, size, document ->
                    require(extractPurpose(document) == StatusPurpose.REVOCATION) { "List is not for revocation" }
                    val bits = decodeBitSet(extractEncodedList(document))
                    for (credentialId in credentialIds) bits.set(getOrAssignIndex(credentialId, statusListId.toString(), conn), true)
                    val encoded = encodeBitSet(bits, size)
                    updateStatusListInDb(statusListId.toString(), updateEncodedList(document, encoded), encoded, conn)
                }
                credentialIds.associateWith { true }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                credentialIds.associateWith { false }
            }
        }

    override suspend fun expandStatusList(
        statusListId: StatusListId,
        additionalSize: Int,
    ): Unit =
        withContext(Dispatchers.IO) {
            require(additionalSize > 0) { "Additional size must be positive" }
            mutateStatusList(statusListId.toString()) { conn, size, document ->
                require(additionalSize <= Int.MAX_VALUE - size - 7) { "Expanded size is too large" }
                val newSize = size + additionalSize
                val encoded = encodeBitSet(decodeBitSet(extractEncodedList(document)), newSize)
                updateStatusListInDb(statusListId.toString(), updateEncodedList(document, encoded), encoded, conn)
                conn.prepareStatement("UPDATE status_lists SET size = ? WHERE id = ?").use { statement ->
                    statement.setInt(1, newSize)
                    statement.setString(2, statusListId.toString())
                    check(statement.executeUpdate() == 1) { "Status list disappeared" }
                }
            }
        }

    /** All mutations read the current bitmap only after acquiring the cross-process row lock. */
    private fun mutateStatusList(
        id: String,
        mutation: (java.sql.Connection, Int, JsonObject) -> Unit,
    ) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val size = lockStatusList(id, conn)
                val document = checkNotNull(getStatusListFromDb(id, conn))
                mutation(conn, size, document)
                conn.commit()
            } catch (failure: Throwable) {
                conn.rollback()
                throw failure
            }
        }
    }

    // Helper methods

    private fun getStatusListFromDb(statusListId: String): JsonObject? =
        dataSource.connection.use { conn -> getStatusListFromDb(statusListId, conn) }

    private fun getStatusListFromDb(
        statusListId: String,
        conn: java.sql.Connection,
    ): JsonObject? =
        conn.prepareStatement("SELECT status_list_data FROM status_lists WHERE id = ?").use { statement ->
            statement.setString(1, statusListId)
            statement.executeQuery().use { rows ->
                if (rows.next()) json.decodeFromString(JsonObject.serializer(), rows.getString(1)) else null
            }
        }

    private fun updateStatusListInDb(
        statusListId: String,
        statusListJson: JsonObject,
        encodedList: String,
        conn: java.sql.Connection? = null,
    ) {
        val connection = conn ?: dataSource.connection
        try {
            val stmt =
                connection.prepareStatement(
                    """
                UPDATE status_lists
                SET encoded_list = ?, status_list_data = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
            """,
                )
            stmt.setString(1, encodedList)
            stmt.setString(2, json.encodeToString(JsonObject.serializer(), statusListJson))
            stmt.setString(3, statusListId)
            stmt.executeUpdate()
        } finally {
            if (conn == null) {
                connection.close()
            }
        }
    }

    private fun extractPurpose(statusListJson: JsonObject): StatusPurpose {
        val credentialSubject =
            statusListJson["credentialSubject"]?.jsonObject
                ?: throw IllegalArgumentException("Missing credentialSubject in status list")
        val statusPurposeStr =
            credentialSubject["statusPurpose"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Missing statusPurpose in status list")
        return try {
            StatusPurpose.valueOf(statusPurposeStr.uppercase())
        } catch (e: Exception) {
            StatusPurpose.REVOCATION // Default fallback
        }
    }

    private fun extractEncodedList(statusListJson: JsonObject): String {
        val credentialSubject =
            statusListJson["credentialSubject"]?.jsonObject
                ?: throw IllegalArgumentException("Missing credentialSubject in status list")
        return credentialSubject["encodedList"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing encodedList in status list")
    }

    private fun extractIssuerDid(statusListJson: JsonObject): String =
        statusListJson["issuer"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing issuer in status list")

    private fun updateEncodedList(
        statusListJson: JsonObject,
        newEncodedList: String,
    ): JsonObject {
        val credentialSubject =
            statusListJson["credentialSubject"]?.jsonObject
                ?: throw IllegalArgumentException("Missing credentialSubject in status list")
        val updatedCredentialSubject =
            buildJsonObject {
                credentialSubject.forEach { (key, value) ->
                    if (key == "encodedList") {
                        put(key, newEncodedList)
                    } else {
                        put(key, value)
                    }
                }
            }
        return buildJsonObject {
            statusListJson.forEach { (key, value) ->
                if (key == "credentialSubject") {
                    put(key, updatedCredentialSubject)
                } else {
                    put(key, value)
                }
            }
        }
    }

    private fun getOrAssignIndex(
        credentialId: String,
        statusListId: String,
        conn: java.sql.Connection,
    ): Int {
        lockStatusList(statusListId, conn)
        // Try to get existing index
        val getStmt =
            conn.prepareStatement(
                """
            SELECT index_value FROM credential_indices
            WHERE credential_id = ? AND status_list_id = ?
        """,
            )
        getStmt.setString(1, credentialId)
        getStmt.setString(2, statusListId)
        val rs = getStmt.executeQuery()
        if (rs.next()) {
            return rs.getInt("index_value")
        }

        // Assign new index
        return getNextAvailableIndex(statusListId, conn).also { index ->
            val insertStmt =
                conn.prepareStatement(
                    """
                INSERT INTO credential_indices (credential_id, status_list_id, index_value)
                VALUES (?, ?, ?)
            """,
                )
            insertStmt.setString(1, credentialId)
            insertStmt.setString(2, statusListId)
            insertStmt.setInt(3, index)
            insertStmt.executeUpdate()
        }
    }

    private fun lockStatusList(
        statusListId: String,
        conn: java.sql.Connection,
    ): Int =
        conn.prepareStatement("SELECT size FROM status_lists WHERE id = ? FOR UPDATE").use { statement ->
            statement.setString(1, statusListId)
            statement.executeQuery().use { rows ->
                require(rows.next()) { "Status list not found" }
                rows.getInt(1)
            }
        }

    private fun getNextAvailableIndex(
        statusListId: String,
        conn: java.sql.Connection,
    ): Int {
        val size = lockStatusList(statusListId, conn)
        // Get current next index
        val getStmt =
            conn.prepareStatement(
                """
            SELECT next_index FROM status_list_next_index WHERE status_list_id = ?
        """,
            )
        getStmt.setString(1, statusListId)
        val rs = getStmt.executeQuery()

        var next =
            if (rs.next()) {
                rs.getInt("next_index")
            } else {
                0
            }

        // Find next available index (skip already assigned ones)
        while (next < size) {
            val checkStmt =
                conn.prepareStatement(
                    """
                SELECT COUNT(*) as count FROM credential_indices
                WHERE status_list_id = ? AND index_value = ?
            """,
                )
            checkStmt.setString(1, statusListId)
            checkStmt.setInt(2, next)
            val checkRs = checkStmt.executeQuery()
            if (checkRs.next() && checkRs.getInt("count") == 0) {
                break
            }
            next++
        }

        require(next in 0 until size) { "Status list is full" }
        conn
            .prepareStatement(
                "UPDATE status_list_next_index SET next_index = ? WHERE status_list_id = ?",
            ).use { statement ->
                statement.setInt(1, next + 1)
                statement.setString(2, statusListId)
                check(statement.executeUpdate() == 1) { "Status list allocation state is missing" }
            }

        return next
    }

    private fun encodeBitSet(
        bitSet: BitSet,
        size: Int,
    ): String {
        val bytes = ByteArray((size + 7) / 8)
        for (i in 0 until size) {
            if (bitSet.get(i)) {
                val byteIndex = i / 8
                val bitIndex = i % 8
                bytes[byteIndex] = (bytes[byteIndex].toInt() or (1 shl bitIndex)).toByte()
            }
        }
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun decodeBitSet(encoded: String): BitSet {
        val bytes = Base64.getDecoder().decode(encoded)
        val bitSet = BitSet(bytes.size * 8)
        for (i in bytes.indices) {
            for (j in 0..7) {
                if ((bytes[i].toInt() and (1 shl j)) != 0) {
                    bitSet.set(i * 8 + j)
                }
            }
        }
        return bitSet
    }
}
