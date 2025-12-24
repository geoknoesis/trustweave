package org.trustweave.revocation.database

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.revocation.StatusListMetadata
import org.trustweave.credential.revocation.StatusListStatistics
import org.trustweave.credential.revocation.StatusUpdate
import org.trustweave.credential.revocation.RevocationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import java.util.Base64
import java.util.BitSet
import java.util.UUID
import javax.sql.DataSource

/**
 * Database-backed status list manager implementation.
 *
 * Provides persistent status list management using a relational database.
 * Supports PostgreSQL, MySQL, and H2 databases.
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
    private val dataSource: DataSource
) : CredentialRevocationManager {

    private val json = Json {
        prettyPrint = false
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    init {
        initializeSchema()
    }

    /**
     * Initialize database schema.
     */
    private fun initializeSchema() {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                // Status lists table
                conn.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS status_lists (
                        id VARCHAR(255) PRIMARY KEY,
                        issuer_did VARCHAR(255) NOT NULL,
                        purpose VARCHAR(50) NOT NULL,
                        size INT NOT NULL,
                        encoded_list TEXT NOT NULL,
                        status_list_data TEXT NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_issuer (issuer_did),
                        INDEX idx_purpose (purpose)
                    )
                """).execute()

                // Credential indices table
                conn.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS credential_indices (
                        credential_id VARCHAR(255) NOT NULL,
                        status_list_id VARCHAR(255) NOT NULL,
                        index_value INT NOT NULL,
                        PRIMARY KEY (credential_id, status_list_id),
                        FOREIGN KEY (status_list_id) REFERENCES status_lists(id) ON DELETE CASCADE,
                        INDEX idx_status_list (status_list_id),
                        INDEX idx_index (status_list_id, index_value),
                        INDEX idx_credential (credential_id)
                    )
                """).execute()

                // Next index tracking
                conn.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS status_list_next_index (
                        status_list_id VARCHAR(255) PRIMARY KEY,
                        next_index INT NOT NULL DEFAULT 0,
                        FOREIGN KEY (status_list_id) REFERENCES status_lists(id) ON DELETE CASCADE
                    )
                """).execute()

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
        customId: String?
    ): StatusListId = withContext(Dispatchers.IO) {
        val id = customId ?: UUID.randomUUID().toString()
        val bitSet = BitSet(size)
        val encodedList = encodeBitSet(bitSet, size)
        val statusListJson = buildJsonObject {
            put("id", id)
            put("type", buildJsonArray {
                add("VerifiableCredential")
                add("StatusList2021Credential")
            })
            put("issuer", issuerDid)
            put("credentialSubject", buildJsonObject {
                put("id", id)
                put("type", "StatusList2021")
                put("statusPurpose", purpose.name.lowercase())
                put("encodedList", encodedList)
            })
            put("issuanceDate", Clock.System.now().toString())
        }

        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val stmt = conn.prepareStatement("""
                    INSERT INTO status_lists (id, issuer_did, purpose, size, encoded_list, status_list_data)
                    VALUES (?, ?, ?, ?, ?, ?)
                """)
                stmt.setString(1, id)
                stmt.setString(2, issuerDid)
                stmt.setString(3, purpose.name)
                stmt.setInt(4, size)
                stmt.setString(5, encodedList)
                stmt.setString(6, json.encodeToString(JsonObject.serializer(), statusListJson))
                stmt.executeUpdate()

                val nextIndexStmt = conn.prepareStatement("""
                    INSERT INTO status_list_next_index (status_list_id, next_index)
                    VALUES (?, 0)
                """)
                nextIndexStmt.setString(1, id)
                nextIndexStmt.executeUpdate()

                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw RuntimeException("Failed to create status list: ${e.message}", e)
            }
        }

        StatusListId(id)
    }

    override suspend fun revokeCredential(
        credentialId: String,
        statusListId: StatusListId
    ): Boolean = withContext(Dispatchers.IO) {
        updateCredentialStatus(credentialId, statusListId.toString(), revoked = true, suspended = null)
    }

    override suspend fun suspendCredential(
        credentialId: String,
        statusListId: StatusListId
    ): Boolean = withContext(Dispatchers.IO) {
        updateCredentialStatus(credentialId, statusListId.toString(), revoked = null, suspended = true)
    }

    override suspend fun unrevokeCredential(
        credentialId: String,
        statusListId: StatusListId
    ): Boolean = withContext(Dispatchers.IO) {
        updateCredentialStatus(credentialId, statusListId.toString(), revoked = false, suspended = null)
    }

    override suspend fun unsuspendCredential(
        credentialId: String,
        statusListId: StatusListId
    ): Boolean = withContext(Dispatchers.IO) {
        updateCredentialStatus(credentialId, statusListId.toString(), revoked = null, suspended = false)
    }

    private suspend fun updateCredentialStatus(
        credentialId: String,
        statusListId: String,
        revoked: Boolean?,
        suspended: Boolean?
    ): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                // Get status list
                val statusListJson = getStatusListFromDb(statusListId) ?: return@withContext false
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
                val newEncodedList = encodeBitSet(bitSet, bitSet.size())
                val updatedStatusListJson = updateEncodedList(statusListJson, newEncodedList)

                // Save to database
                val updateStmt = conn.prepareStatement("""
                    UPDATE status_lists
                    SET encoded_list = ?, status_list_data = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                """)
                updateStmt.setString(1, newEncodedList)
                updateStmt.setString(2, json.encodeToString(JsonObject.serializer(), updatedStatusListJson))
                updateStmt.setString(3, statusListId)
                updateStmt.executeUpdate()

                conn.commit()
                true
            } catch (e: Exception) {
                conn.rollback()
                false
            }
        }
    }

    override suspend fun checkRevocationStatus(
        credential: VerifiableCredential
    ): RevocationStatus = withContext(Dispatchers.IO) {
        val credentialStatus = credential.credentialStatus ?: return@withContext RevocationStatus(
            revoked = false,
            suspended = false
        )

        val statusListId = credentialStatus.statusListCredential ?: credentialStatus.id

        val index = credentialStatus.statusListIndex?.toIntOrNull()
            ?: getCredentialIndex(credential.id?.toString() ?: return@withContext RevocationStatus(
                revoked = false,
                suspended = false,
                statusListId = statusListId
            ), statusListId)
            ?: return@withContext RevocationStatus(
                revoked = false,
                suspended = false,
                statusListId = statusListId
            )

        checkStatusByIndex(statusListId, index)
    }

    override suspend fun checkStatusByIndex(
        statusListId: StatusListId,
        index: Int
    ): RevocationStatus = withContext(Dispatchers.IO) {
        val statusListJson = getStatusListFromDb(statusListId.toString()) ?: return@withContext RevocationStatus(
            revoked = false,
            suspended = false,
            statusListId = statusListId
        )

        val encodedList = extractEncodedList(statusListJson)
        val bitSet = decodeBitSet(encodedList)
        val purpose = extractPurpose(statusListJson)
        val isSet = bitSet.get(index)

        RevocationStatus(
            revoked = isSet && purpose == StatusPurpose.REVOCATION,
            suspended = isSet && purpose == StatusPurpose.SUSPENSION,
            statusListId = statusListId,
            index = index
        )
    }

    override suspend fun checkStatusByCredentialId(
        credentialId: String,
        statusListId: StatusListId
    ): RevocationStatus = withContext(Dispatchers.IO) {
        val index = getCredentialIndex(credentialId, statusListId)
            ?: return@withContext RevocationStatus(
                revoked = false,
                suspended = false,
                statusListId = statusListId
            )

        checkStatusByIndex(statusListId, index)
    }

    override suspend fun getCredentialIndex(
        credentialId: String,
        statusListId: StatusListId
    ): Int? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val stmt = conn.prepareStatement("""
                SELECT index_value FROM credential_indices
                WHERE credential_id = ? AND status_list_id = ?
            """)
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
        index: Int?
    ): Int = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val assignedIndex = if (index != null) {
                    // Check if index is already assigned
                    val checkStmt = conn.prepareStatement("""
                        SELECT COUNT(*) as count FROM credential_indices
                        WHERE status_list_id = ? AND index_value = ?
                    """)
                    checkStmt.setString(1, statusListId.toString())
                    checkStmt.setInt(2, index)
                    val rs = checkStmt.executeQuery()
                    if (rs.next() && rs.getInt("count") > 0) {
                        throw IllegalArgumentException("Index $index is already assigned in status list $statusListId")
                    }
                    index
                } else {
                    // Auto-assign next available index
                    getNextAvailableIndex(statusListId.toString(), conn)
                }

                val insertStmt = conn.prepareStatement("""
                    INSERT INTO credential_indices (credential_id, status_list_id, index_value)
                    VALUES (?, ?, ?)
                    ON CONFLICT (credential_id, status_list_id) DO UPDATE SET index_value = ?
                """)
                insertStmt.setString(1, credentialId)
                insertStmt.setString(2, statusListId.toString())
                insertStmt.setInt(3, assignedIndex)
                insertStmt.setInt(4, assignedIndex)
                insertStmt.executeUpdate()

                conn.commit()
                assignedIndex
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }

    override suspend fun updateStatusListBatch(
        statusListId: StatusListId,
        updates: List<StatusUpdate>
    ) = withContext(Dispatchers.IO) {
        val statusListJson = getStatusListFromDb(statusListId.toString())
            ?: throw IllegalArgumentException("Status list not found: $statusListId")

        val encodedList = extractEncodedList(statusListJson)
        val bitSet = decodeBitSet(encodedList)
        val purpose = extractPurpose(statusListJson)

        for (update in updates) {
            val revoked = update.revoked
            val suspended = update.suspended
            if (revoked != null && purpose == StatusPurpose.REVOCATION) {
                bitSet.set(update.index, revoked)
            } else if (suspended != null && purpose == StatusPurpose.SUSPENSION) {
                bitSet.set(update.index, suspended)
            }
        }

        val newEncodedList = encodeBitSet(bitSet, bitSet.size())
        val updatedStatusListJson = updateEncodedList(statusListJson, newEncodedList)

        updateStatusListInDb(statusListId.toString(), updatedStatusListJson, newEncodedList)
    }

    override suspend fun getStatusList(statusListId: StatusListId): StatusListMetadata? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val stmt = conn.prepareStatement("""
                SELECT id, issuer_did, purpose, size, created_at, updated_at
                FROM status_lists WHERE id = ?
            """)
            stmt.setString(1, statusListId.toString())
            val rs = stmt.executeQuery()
            if (rs.next()) {
                val createdAt = rs.getTimestamp("created_at")?.let { ts ->
                    val javaInstant = ts.toInstant()
                    Instant.fromEpochSeconds(javaInstant.epochSecond, javaInstant.nano)
                } ?: Clock.System.now()
                val lastUpdated = rs.getTimestamp("updated_at")?.let { ts ->
                    val javaInstant = ts.toInstant()
                    Instant.fromEpochSeconds(javaInstant.epochSecond, javaInstant.nano)
                } ?: Clock.System.now()
                val purposeStr = rs.getString("purpose")
                val purpose = try {
                    StatusPurpose.valueOf(purposeStr.uppercase())
                } catch (e: Exception) {
                    StatusPurpose.REVOCATION // Default fallback
                }
                StatusListMetadata(
                    id = statusListId,
                    issuerDid = rs.getString("issuer_did"),
                    purpose = purpose,
                    size = rs.getInt("size"),
                    createdAt = createdAt,
                    lastUpdated = lastUpdated
                )
            } else {
                null
            }
        }
    }

    override suspend fun listStatusLists(issuerDid: String?): List<StatusListMetadata> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val sql = if (issuerDid != null) {
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
                val createdAt = rs.getTimestamp("created_at")?.let { ts ->
                    val javaInstant = ts.toInstant()
                    Instant.fromEpochSeconds(javaInstant.epochSecond, javaInstant.nano)
                } ?: Clock.System.now()
                val lastUpdated = rs.getTimestamp("updated_at")?.let { ts ->
                    val javaInstant = ts.toInstant()
                    Instant.fromEpochSeconds(javaInstant.epochSecond, javaInstant.nano)
                } ?: Clock.System.now()
                val purposeStr = rs.getString("purpose")
                val purpose = try {
                    StatusPurpose.valueOf(purposeStr.uppercase())
                } catch (e: Exception) {
                    StatusPurpose.REVOCATION // Default fallback
                }
                results.add(StatusListMetadata(
                    id = id,
                    issuerDid = rs.getString("issuer_did"),
                    purpose = purpose,
                    size = rs.getInt("size"),
                    createdAt = createdAt,
                    lastUpdated = lastUpdated
                ))
            }
            results
        }
    }

    override suspend fun deleteStatusList(statusListId: StatusListId): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val stmt = conn.prepareStatement("DELETE FROM status_lists WHERE id = ?")
                stmt.setString(1, statusListId.toString())
                val deleted = stmt.executeUpdate() > 0
                conn.commit()
                deleted
            } catch (e: Exception) {
                conn.rollback()
                false
            }
        }
    }

    override suspend fun getStatusListStatistics(statusListId: StatusListId): StatusListStatistics? = withContext(Dispatchers.IO) {
        val statusListJson = getStatusListFromDb(statusListId.toString()) ?: return@withContext null

        dataSource.connection.use { conn ->
            val usedIndicesStmt = conn.prepareStatement("""
                SELECT COUNT(*) as count FROM credential_indices WHERE status_list_id = ?
            """)
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
                lastUpdated = Clock.System.now()
            )
        }
    }

    override suspend fun revokeCredentials(
        credentialIds: List<String>,
        statusListId: StatusListId
    ): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val statusListJson = getStatusListFromDb(statusListId.toString()) ?: return@withContext credentialIds.associateWith { false }

        val purpose = extractPurpose(statusListJson)
        if (purpose != StatusPurpose.REVOCATION) {
            return@withContext credentialIds.associateWith { false }
        }

        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val encodedList = extractEncodedList(statusListJson)
                val bitSet = decodeBitSet(encodedList)
                val results = mutableMapOf<String, Boolean>()

                for (credentialId in credentialIds) {
                    val index = getOrAssignIndex(credentialId, statusListId.toString(), conn)
                    bitSet.set(index, true)
                    results[credentialId] = true
                }

                val newEncodedList = encodeBitSet(bitSet, bitSet.size())
                val updatedStatusListJson = updateEncodedList(statusListJson, newEncodedList)
                updateStatusListInDb(statusListId.toString(), updatedStatusListJson, newEncodedList, conn)

                conn.commit()
                results
            } catch (e: Exception) {
                conn.rollback()
                credentialIds.associateWith { false }
            }
        }
    }

    override suspend fun expandStatusList(
        statusListId: StatusListId,
        additionalSize: Int
    ): Unit = withContext(Dispatchers.IO) {
        val statusListJson = getStatusListFromDb(statusListId.toString())
            ?: throw IllegalArgumentException("Status list not found: $statusListId")

        val encodedList = extractEncodedList(statusListJson)
        val currentBitSet = decodeBitSet(encodedList)
        val currentSize = currentBitSet.size()
        val newSize = currentSize + additionalSize
        val newBitSet = BitSet(newSize)

        // Copy existing bits
        for (i in 0 until currentSize) {
            if (currentBitSet.get(i)) {
                newBitSet.set(i, true)
            }
        }

        val newEncodedList = encodeBitSet(newBitSet, newSize)
        val updatedStatusListJson = updateEncodedList(statusListJson, newEncodedList)

        updateStatusListInDb(statusListId.toString(), updatedStatusListJson, newEncodedList)
        
        // Update size in database
        dataSource.connection.use { conn ->
            val stmt = conn.prepareStatement("""
                UPDATE status_lists SET size = ? WHERE id = ?
            """)
            stmt.setInt(1, newSize)
            stmt.setString(2, statusListId.toString())
            stmt.executeUpdate()
        }
    }

    // Helper methods

    private fun getStatusListFromDb(statusListId: String): JsonObject? {
        return dataSource.connection.use { conn ->
            val stmt = conn.prepareStatement("SELECT status_list_data FROM status_lists WHERE id = ?")
            stmt.setString(1, statusListId)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                val jsonData = rs.getString("status_list_data")
                json.decodeFromString(JsonObject.serializer(), jsonData)
            } else {
                null
            }
        }
    }

    private fun updateStatusListInDb(
        statusListId: String,
        statusListJson: JsonObject,
        encodedList: String,
        conn: java.sql.Connection? = null
    ) {
        val connection = conn ?: dataSource.connection
        try {
            val stmt = connection.prepareStatement("""
                UPDATE status_lists
                SET encoded_list = ?, status_list_data = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
            """)
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
        val credentialSubject = statusListJson["credentialSubject"]?.jsonObject
            ?: throw IllegalArgumentException("Missing credentialSubject in status list")
        val statusPurposeStr = credentialSubject["statusPurpose"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing statusPurpose in status list")
        return try {
            StatusPurpose.valueOf(statusPurposeStr.uppercase())
        } catch (e: Exception) {
            StatusPurpose.REVOCATION // Default fallback
        }
    }

    private fun extractEncodedList(statusListJson: JsonObject): String {
        val credentialSubject = statusListJson["credentialSubject"]?.jsonObject
            ?: throw IllegalArgumentException("Missing credentialSubject in status list")
        return credentialSubject["encodedList"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing encodedList in status list")
    }

    private fun extractIssuerDid(statusListJson: JsonObject): String {
        return statusListJson["issuer"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing issuer in status list")
    }

    private fun updateEncodedList(statusListJson: JsonObject, newEncodedList: String): JsonObject {
        val credentialSubject = statusListJson["credentialSubject"]?.jsonObject
            ?: throw IllegalArgumentException("Missing credentialSubject in status list")
        val updatedCredentialSubject = buildJsonObject {
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
        conn: java.sql.Connection
    ): Int {
        // Try to get existing index
        val getStmt = conn.prepareStatement("""
            SELECT index_value FROM credential_indices
            WHERE credential_id = ? AND status_list_id = ?
        """)
        getStmt.setString(1, credentialId)
        getStmt.setString(2, statusListId)
        val rs = getStmt.executeQuery()
        if (rs.next()) {
            return rs.getInt("index_value")
        }

        // Assign new index
        return getNextAvailableIndex(statusListId, conn).also { index ->
            val insertStmt = conn.prepareStatement("""
                INSERT INTO credential_indices (credential_id, status_list_id, index_value)
                VALUES (?, ?, ?)
            """)
            insertStmt.setString(1, credentialId)
            insertStmt.setString(2, statusListId)
            insertStmt.setInt(3, index)
            insertStmt.executeUpdate()
        }
    }

    private fun getNextAvailableIndex(statusListId: String, conn: java.sql.Connection): Int {
        // Get current next index
        val getStmt = conn.prepareStatement("""
            SELECT next_index FROM status_list_next_index WHERE status_list_id = ?
        """)
        getStmt.setString(1, statusListId)
        val rs = getStmt.executeQuery()

        var next = if (rs.next()) {
            rs.getInt("next_index")
        } else {
            0
        }

        // Find next available index (skip already assigned ones)
        while (true) {
            val checkStmt = conn.prepareStatement("""
                SELECT COUNT(*) as count FROM credential_indices
                WHERE status_list_id = ? AND index_value = ?
            """)
            checkStmt.setString(1, statusListId)
            checkStmt.setInt(2, next)
            val checkRs = checkStmt.executeQuery()
            if (checkRs.next() && checkRs.getInt("count") == 0) {
                break
            }
            next++
        }

        // Update next index
        val updateStmt = conn.prepareStatement("""
            INSERT INTO status_list_next_index (status_list_id, next_index)
            VALUES (?, ?)
            ON CONFLICT (status_list_id) DO UPDATE SET next_index = ?
        """)
        updateStmt.setString(1, statusListId)
        updateStmt.setInt(2, next + 1)
        updateStmt.setInt(3, next + 1)
        updateStmt.executeUpdate()

        return next
    }

    private fun encodeBitSet(bitSet: BitSet, size: Int): String {
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
