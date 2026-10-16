package org.trustweave.credential.didcomm.storage.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.trustweave.credential.didcomm.models.DidCommMessage
import org.trustweave.credential.didcomm.storage.*
import org.trustweave.credential.didcomm.storage.encryption.MessageEncryption
import java.sql.*
import javax.sql.DataSource

/**
 * PostgreSQL-backed message storage.
 *
 * Stores messages in a PostgreSQL database with proper indexing
 * for efficient queries by DID, thread, and time.
 *
 * **Database Schema:**
 * - `didcomm_messages` - Main messages table
 * - `didcomm_message_dids` - Index for DID lookups
 * - `didcomm_message_threads` - Index for thread lookups
 *
 * **Example Usage:**
 * ```kotlin
 * val dataSource = HikariDataSource().apply {
 *     jdbcUrl = "jdbc:postgresql://localhost:5432/trustweave"
 *     username = "user"
 *     password = "pass"
 * }
 * val storage = PostgresDidCommMessageStorage(dataSource)
 * ```
 */
class PostgresDidCommMessageStorage(
    private val dataSource: DataSource,
    private val encryption: MessageEncryption? = null,
) : DidCommMessageStorage {
    init {
        createTables()
    }

    /** Replay ids live in this storage's own database, so every replica sharing it shares them. */
    override fun replayStore(): org.trustweave.credential.didcomm.DidCommReplayStore =
        org.trustweave.credential.didcomm
            .DatabaseDidCommReplayStore(dataSource)

    override fun setEncryption(encryption: MessageEncryption?) {
        // Encryption is fixed at construction; silently ignoring a requested
        // encryption change would leave messages stored with different
        // guarantees than the caller believes. Fail closed instead.
        if (encryption !== this.encryption) {
            throw UnsupportedOperationException(
                "PostgresDidCommMessageStorage encryption is set at construction and cannot be changed; " +
                    "create a new storage instance with the desired MessageEncryption.",
            )
        }
    }

    private val archivedMessages = mutableSetOf<String>()

    override suspend fun markAsArchived(
        messageIds: List<String>,
        archiveId: String,
    ) = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                UPDATE didcomm_messages
                SET archived = TRUE, archive_id = ?, archived_at = CURRENT_TIMESTAMP
                WHERE id = ANY(?)
            """,
                ).use { stmt ->
                    val messageIdArray = conn.createArrayOf("VARCHAR", messageIds.toTypedArray())
                    stmt.setString(1, archiveId)
                    stmt.setArray(2, messageIdArray)
                    stmt.executeUpdate()
                }
        }
        Unit
    }

    override suspend fun isArchived(messageId: String): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn
                    .prepareStatement(
                        """
                SELECT archived FROM didcomm_messages WHERE id = ?
            """,
                    ).use { stmt ->
                        stmt.setString(1, messageId)
                        stmt.executeQuery().use { rs ->
                            if (rs.next()) {
                                rs.getBoolean("archived")
                            } else {
                                false
                            }
                        }
                    }
            }
        }

    /**
     * The value stored in an indexed text column (sender, recipients, thread, type) for [value].
     *
     * Without encryption this is the value itself. With encryption it is a keyed blind index
     * ([MessageEncryption.blindIndex]) so equality lookups still work while the database never sees
     * the identifier; `null` when the configured encryption offers no blind index, in which case the
     * column is left empty rather than leaking plaintext.
     */
    private suspend fun indexKey(
        purpose: String,
        value: String?,
    ): String? {
        if (value == null) return null
        val enc = encryption ?: return value
        return enc.blindIndex(purpose, value)
    }

    override suspend fun store(message: DidCommMessage): String {
        val json =
            Json {
                prettyPrint = false
                encodeDefaults = false
            }
        val enc = encryption
        // Everything derived from the message is computed up front (these are suspend calls).
        val encrypted = enc?.encrypt(message)
        val fromKey = indexKey(PURPOSE_DID, message.from)
        val toKeys = message.to.mapNotNull { indexKey(PURPOSE_DID, it) }
        val thidKey = indexKey(PURPOSE_THREAD, message.thid)
        val pthidKey = indexKey(PURPOSE_THREAD, message.pthid)
        val typeKey = if (enc == null) message.type else (indexKey(PURPOSE_TYPE, message.type) ?: ENCRYPTED_TYPE_PLACEHOLDER)

        return withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val updated =
                    if (encrypted != null) {
                        // Encrypted rows carry opaque ciphertext plus the minimum indexed columns: blind
                        // indexes instead of DIDs/thread ids, and NO body / expiry / plaintext JSON.
                        conn
                            .prepareStatement(
                                """
                    INSERT INTO didcomm_messages (
                        id, type, from_did, to_dids, body, created_time,
                        expires_time, thid, pthid, message_json,
                        encrypted_data, key_version, iv, is_encrypted
                    ) VALUES (?, ?, ?, ?, NULL, ?, NULL, ?, ?, '{}'::jsonb, ?, ?, ?, TRUE)
                    ON CONFLICT (id) DO UPDATE SET
                        encrypted_data = EXCLUDED.encrypted_data,
                        key_version = EXCLUDED.key_version,
                        iv = EXCLUDED.iv,
                        is_encrypted = EXCLUDED.is_encrypted
                    WHERE didcomm_messages.from_did IS NOT DISTINCT FROM EXCLUDED.from_did
                """,
                            ).use { stmt ->
                                stmt.setString(1, message.id)
                                stmt.setString(2, typeKey)
                                stmt.setString(3, fromKey)
                                stmt.setArray(4, conn.createArrayOf("VARCHAR", toKeys.toTypedArray()))
                                stmt.setString(5, message.created)
                                stmt.setString(6, thidKey)
                                stmt.setString(7, pthidKey)
                                stmt.setBytes(8, encrypted.encryptedData)
                                stmt.setInt(9, encrypted.keyVersion)
                                stmt.setBytes(10, encrypted.iv)
                                stmt.executeUpdate()
                            }
                    } else {
                        val messageJson = json.encodeToString(DidCommMessage.serializer(), message)
                        conn
                            .prepareStatement(
                                """
                    INSERT INTO didcomm_messages (
                        id, type, from_did, to_dids, body, created_time,
                        expires_time, thid, pthid, message_json, is_encrypted
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        message_json = EXCLUDED.message_json,
                        body = EXCLUDED.body,
                        is_encrypted = EXCLUDED.is_encrypted
                    WHERE didcomm_messages.from_did IS NOT DISTINCT FROM EXCLUDED.from_did
                """,
                            ).use { stmt ->
                                stmt.setString(1, message.id)
                                stmt.setString(2, message.type)
                                stmt.setString(3, message.from)
                                stmt.setArray(4, conn.createArrayOf("VARCHAR", message.to.toTypedArray()))
                                stmt.setString(5, json.encodeToString(JsonObject.serializer(), message.body))
                                stmt.setString(6, message.created)
                                stmt.setString(7, message.expiresTime)
                                stmt.setString(8, message.thid)
                                stmt.setString(9, message.pthid)
                                stmt.setString(10, messageJson)
                                stmt.setBoolean(11, false)
                                stmt.executeUpdate()
                            }
                    }

                // The id is the primary key, so a different sender must not be able to overwrite (or
                // attach index rows to) an existing message: the conditional upsert touched no row.
                if (updated == 0) {
                    throw IllegalStateException("Message id '${message.id}' is already stored for a different sender")
                }

                // Index by DID
                fromKey?.let { indexMessageForDid(conn, message.id, it, "from") }
                toKeys.forEach { indexMessageForDid(conn, message.id, it, "to") }

                // Index by thread
                thidKey?.let { indexMessageForThread(conn, message.id, it) }
            }

            message.id
        }
    }

    /** Reads the current row of [rs] (columns: id, message_json, encrypted_data, key_version, iv, is_encrypted). */
    private suspend fun readMessage(rs: ResultSet): DidCommMessage {
        val isEncrypted = rs.getBoolean("is_encrypted")
        if (!isEncrypted) {
            return readJson.decodeFromString(DidCommMessage.serializer(), rs.getString("message_json"))
        }
        val enc =
            encryption
                ?: throw IllegalStateException("Row ${rs.getString("id")} is encrypted but this storage has no MessageEncryption")
        val id = rs.getString("id")
        val encrypted =
            org.trustweave.credential.didcomm.storage.encryption.EncryptedMessage(
                keyVersion = rs.getInt("key_version"),
                encryptedData = rs.getBytes("encrypted_data"),
                iv = rs.getBytes("iv"),
            )
        return enc.decrypt(encrypted, id)
    }

    private val readJson = Json { ignoreUnknownKeys = true }

    override suspend fun get(messageId: String): DidCommMessage? =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn
                    .prepareStatement(
                        """
                SELECT id, message_json, encrypted_data, key_version, iv, is_encrypted
                FROM didcomm_messages WHERE id = ?
            """,
                    ).use { stmt ->
                        stmt.setString(1, messageId)
                        stmt.executeQuery().use { rs -> if (rs.next()) readMessage(rs) else null }
                    }
            }
        }

    override suspend fun getMessagesForDid(
        did: String,
        limit: Int,
        offset: Int,
    ): List<DidCommMessage> {
        val key = indexKey(PURPOSE_DID, did) ?: return emptyList()
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn
                    .prepareStatement(
                        """
                SELECT m.id, m.message_json, m.encrypted_data, m.key_version, m.iv, m.is_encrypted
                FROM didcomm_messages m
                JOIN didcomm_message_dids d ON m.id = d.message_id
                WHERE d.did = ? AND d.role IN ('from', 'to')
                ORDER BY m.created_time DESC NULLS LAST
                LIMIT ? OFFSET ?
            """,
                    ).use { stmt ->
                        stmt.setString(1, key)
                        stmt.setInt(2, limit)
                        stmt.setInt(3, offset)
                        stmt.executeQuery().use { rs -> buildList { while (rs.next()) add(readMessage(rs)) } }
                    }
            }
        }
    }

    override suspend fun getThreadMessages(thid: String): List<DidCommMessage> {
        val key = indexKey(PURPOSE_THREAD, thid) ?: return emptyList()
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn
                    .prepareStatement(
                        """
                SELECT id, message_json, encrypted_data, key_version, iv, is_encrypted
                FROM didcomm_messages
                WHERE thid = ?
                ORDER BY created_time ASC NULLS LAST
            """,
                    ).use { stmt ->
                        stmt.setString(1, key)
                        stmt.executeQuery().use { rs -> buildList { while (rs.next()) add(readMessage(rs)) } }
                    }
            }
        }
    }

    override suspend fun delete(messageId: String): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement("DELETE FROM didcomm_messages WHERE id = ?").use { stmt ->
                    stmt.setString(1, messageId)
                    stmt.executeUpdate() > 0
                }
            }
        }

    override suspend fun deleteMessagesForDid(did: String): Int {
        val key = indexKey(PURPOSE_DID, did) ?: return 0
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn
                    .prepareStatement(
                        """
                DELETE FROM didcomm_messages
                WHERE id IN (
                    SELECT message_id FROM didcomm_message_dids WHERE did = ?
                )
            """,
                    ).use { stmt ->
                        stmt.setString(1, key)
                        stmt.executeUpdate()
                    }
            }
        }
    }

    override suspend fun deleteThreadMessages(thid: String): Int {
        val key = indexKey(PURPOSE_THREAD, thid) ?: return 0
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement("DELETE FROM didcomm_messages WHERE thid = ?").use { stmt ->
                    stmt.setString(1, key)
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun countMessagesForDid(did: String): Int {
        val key = indexKey(PURPOSE_DID, did) ?: return 0
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn
                    .prepareStatement("SELECT COUNT(*) FROM didcomm_message_dids WHERE did = ?")
                    .use { stmt ->
                        stmt.setString(1, key)
                        stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                    }
            }
        }
    }

    override suspend fun search(
        filter: MessageFilter,
        limit: Int,
        offset: Int,
    ): List<DidCommMessage> {
        // Build dynamic query based on filters (indexed text values go through the blind index)
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        filter.fromDid?.let {
            conditions.add("from_did = ?")
            params.add(indexKey(PURPOSE_DID, it) ?: return emptyList())
        }
        filter.toDid?.let {
            conditions.add("? = ANY(to_dids)")
            params.add(indexKey(PURPOSE_DID, it) ?: return emptyList())
        }
        filter.type?.let {
            conditions.add("type = ?")
            params.add(indexKey(PURPOSE_TYPE, it) ?: return emptyList())
        }
        filter.thid?.let {
            conditions.add("thid = ?")
            params.add(indexKey(PURPOSE_THREAD, it) ?: return emptyList())
        }
        filter.createdAfter?.let {
            conditions.add("created_time >= ?")
            params.add(it)
        }
        filter.createdBefore?.let {
            conditions.add("created_time <= ?")
            params.add(it)
        }

        val whereClause =
            if (conditions.isNotEmpty()) {
                "WHERE ${conditions.joinToString(" AND ")}"
            } else {
                ""
            }

        return withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = """
                SELECT id, message_json, encrypted_data, key_version, iv, is_encrypted
                FROM didcomm_messages
                $whereClause
                ORDER BY created_time DESC NULLS LAST
                LIMIT ? OFFSET ?
            """

                conn.prepareStatement(sql).use { stmt ->
                    params.forEachIndexed { index, param ->
                        stmt.setObject(index + 1, param)
                    }
                    stmt.setInt(params.size + 1, limit)
                    stmt.setInt(params.size + 2, offset)

                    stmt.executeQuery().use { rs -> buildList { while (rs.next()) add(readMessage(rs)) } }
                }
            }
        }
    }

    private fun createTables() {
        dataSource.connection.use { conn ->
            // Main messages table
            conn.createStatement().execute(
                """
                CREATE TABLE IF NOT EXISTS didcomm_messages (
                    id VARCHAR(255) PRIMARY KEY,
                    type VARCHAR(255) NOT NULL,
                    from_did VARCHAR(255),
                    to_dids VARCHAR(255)[],
                    body JSONB,
                    created_time VARCHAR(255),
                    expires_time VARCHAR(255),
                    thid VARCHAR(255),
                    pthid VARCHAR(255),
                    message_json JSONB NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """,
            )

            // Index for DID lookups
            conn.createStatement().execute(
                """
                CREATE TABLE IF NOT EXISTS didcomm_message_dids (
                    message_id VARCHAR(255) REFERENCES didcomm_messages(id) ON DELETE CASCADE,
                    did VARCHAR(255) NOT NULL,
                    role VARCHAR(10) NOT NULL CHECK (role IN ('from', 'to')),
                    PRIMARY KEY (message_id, did, role)
                )
            """,
            )

            // Index for thread lookups
            conn.createStatement().execute(
                """
                CREATE TABLE IF NOT EXISTS didcomm_message_threads (
                    message_id VARCHAR(255) REFERENCES didcomm_messages(id) ON DELETE CASCADE,
                    thid VARCHAR(255) NOT NULL,
                    PRIMARY KEY (message_id, thid)
                )
            """,
            )

            // Add encryption columns if they don't exist
            try {
                conn.createStatement().execute(
                    """
                    ALTER TABLE didcomm_messages
                    ADD COLUMN IF NOT EXISTS encrypted_data BYTEA,
                    ADD COLUMN IF NOT EXISTS key_version INT,
                    ADD COLUMN IF NOT EXISTS iv BYTEA,
                    ADD COLUMN IF NOT EXISTS is_encrypted BOOLEAN DEFAULT FALSE
                """,
                )
            } catch (e: SQLException) {
                // Columns may already exist, ignore
            }

            // Add archive columns if they don't exist
            try {
                conn.createStatement().execute(
                    """
                    ALTER TABLE didcomm_messages
                    ADD COLUMN IF NOT EXISTS archived BOOLEAN DEFAULT FALSE,
                    ADD COLUMN IF NOT EXISTS archive_id VARCHAR(255),
                    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP
                """,
                )
            } catch (e: SQLException) {
                // Columns may already exist, ignore
            }

            // Create archive index
            try {
                conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_messages_archived ON didcomm_messages(archived)")
                conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_messages_archive_id ON didcomm_messages(archive_id)")
            } catch (e: SQLException) {
                // Index may already exist
            }

            // Create indexes for performance
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_messages_from_did ON didcomm_messages(from_did)")
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_messages_thid ON didcomm_messages(thid)")
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_messages_created ON didcomm_messages(created_time)")
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_messages_type ON didcomm_messages(type)")
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_messages_key_version ON didcomm_messages(key_version)")
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_messages_is_encrypted ON didcomm_messages(is_encrypted)")
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_message_dids_did ON didcomm_message_dids(did)")
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_message_threads_thid ON didcomm_message_threads(thid)")
        }
    }

    private fun indexMessageForDid(
        conn: Connection,
        messageId: String,
        did: String,
        role: String,
    ) {
        conn
            .prepareStatement(
                """
            INSERT INTO didcomm_message_dids (message_id, did, role)
            VALUES (?, ?, ?)
            ON CONFLICT DO NOTHING
        """,
            ).use { stmt ->
                stmt.setString(1, messageId)
                stmt.setString(2, did)
                stmt.setString(3, role)
                stmt.executeUpdate()
            }
    }

    private fun indexMessageForThread(
        conn: Connection,
        messageId: String,
        thid: String,
    ) {
        conn
            .prepareStatement(
                """
            INSERT INTO didcomm_message_threads (message_id, thid)
            VALUES (?, ?)
            ON CONFLICT DO NOTHING
        """,
            ).use { stmt ->
                stmt.setString(1, messageId)
                stmt.setString(2, thid)
                stmt.executeUpdate()
            }
    }

    private companion object {
        const val PURPOSE_DID = "did"
        const val PURPOSE_THREAD = "thread"
        const val PURPOSE_TYPE = "type"
        const val ENCRYPTED_TYPE_PLACEHOLDER = "encrypted"
    }
}
