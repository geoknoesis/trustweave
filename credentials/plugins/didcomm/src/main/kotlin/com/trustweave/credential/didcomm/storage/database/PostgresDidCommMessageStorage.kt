package com.trustweave.credential.didcomm.storage.database

import com.trustweave.credential.didcomm.models.DidCommMessage
import com.trustweave.credential.didcomm.storage.*
import com.trustweave.credential.didcomm.storage.encryption.MessageEncryption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.sql.*
import java.util.Base64
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
    private val encryption: MessageEncryption? = null
) : DidCommMessageStorage {

    init {
        createTables()
    }

    override fun setEncryption(encryption: MessageEncryption?) {
        // Note: Encryption is set in constructor
        // To change encryption, create a new storage instance
    }

    private val archivedMessages = mutableSetOf<String>()

    override suspend fun markAsArchived(messageIds: List<String>, archiveId: String) = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                UPDATE didcomm_messages
                SET archived = TRUE, archive_id = ?, archived_at = CURRENT_TIMESTAMP
                WHERE id = ANY(?)
            """).use { stmt ->
                val messageIdArray = conn.createArrayOf("VARCHAR", messageIds.toTypedArray())
                stmt.setString(1, archiveId)
                stmt.setArray(2, messageIdArray)
                stmt.executeUpdate()
            }
        }
        Unit
    }

    override suspend fun isArchived(messageId: String): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                SELECT archived FROM didcomm_messages WHERE id = ?
            """).use { stmt ->
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

    override suspend fun store(message: DidCommMessage): String = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val json = Json { prettyPrint = false; encodeDefaults = false }

            if (encryption != null) {
                // Encrypt message
                val encrypted = encryption.encrypt(message)
                val encryptedDataBase64 = Base64.getEncoder().encodeToString(encrypted.encryptedData)
                val ivBase64 = Base64.getEncoder().encodeToString(encrypted.iv)

                conn.prepareStatement("""
                    INSERT INTO didcomm_messages (
                        id, type, from_did, to_dids, body, created_time,
                        expires_time, thid, pthid, message_json,
                        encrypted_data, key_version, iv, is_encrypted
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        message_json = EXCLUDED.message_json,
                        body = EXCLUDED.body,
                        encrypted_data = EXCLUDED.encrypted_data,
                        key_version = EXCLUDED.key_version,
                        iv = EXCLUDED.iv,
                        is_encrypted = EXCLUDED.is_encrypted
                """).use { stmt ->
                    stmt.setString(1, message.id)
                    stmt.setString(2, message.type)
                    stmt.setString(3, message.from)
                    stmt.setArray(4, conn.createArrayOf("VARCHAR", message.to.toTypedArray()))
                    stmt.setString(5, json.encodeToString(JsonObject.serializer(), message.body))
                    stmt.setString(6, message.created)
                    stmt.setString(7, message.expiresTime)
                    stmt.setString(8, message.thid)
                    stmt.setString(9, message.pthid)
                    stmt.setString(10, "{}") // Empty JSON for encrypted messages
                    stmt.setBytes(11, encrypted.encryptedData)
                    stmt.setInt(12, encrypted.keyVersion)
                    stmt.setBytes(13, encrypted.iv)
                    stmt.setBoolean(14, true)
                    stmt.executeUpdate()
                }
            } else {
                // Store unencrypted
                val messageJson = json.encodeToString(
                    DidCommMessage.serializer(),
                    message
                )

                conn.prepareStatement("""
                    INSERT INTO didcomm_messages (
                        id, type, from_did, to_dids, body, created_time,
                        expires_time, thid, pthid, message_json, is_encrypted
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        message_json = EXCLUDED.message_json,
                        body = EXCLUDED.body,
                        is_encrypted = EXCLUDED.is_encrypted
                """).use { stmt ->
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

            // Index by DID
            message.from?.let { from ->
                indexMessageForDid(conn, message.id, from, "from")
            }
            message.to.forEach { to ->
                indexMessageForDid(conn, message.id, to, "to")
            }

            // Index by thread
            message.thid?.let { thid ->
                indexMessageForThread(conn, message.id, thid)
            }
        }

        message.id
    }

    override suspend fun get(messageId: String): DidCommMessage? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                SELECT message_json, encrypted_data, key_version, iv, is_encrypted
                FROM didcomm_messages WHERE id = ?
            """).use { stmt ->
                stmt.setString(1, messageId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        val isEncrypted = rs.getBoolean("is_encrypted")
                        val json = Json { ignoreUnknownKeys = true }

                        if (isEncrypted && encryption != null) {
                            // Decrypt message
                            val encryptedData = rs.getBytes("encrypted_data")
                            val keyVersion = rs.getInt("key_version")
                            val iv = rs.getBytes("iv")

                            val encrypted = com.trustweave.credential.didcomm.storage.encryption.EncryptedMessage(
                                keyVersion = keyVersion,
                                encryptedData = encryptedData,
                                iv = iv
                            )

                            encryption.decrypt(encrypted)
                        } else {
                            // Read unencrypted
                            val messageJson = rs.getString("message_json")
                            json.decodeFromString(DidCommMessage.serializer(), messageJson)
                        }
                    } else {
                        null
                    }
                }
            }
        }
    }

    override suspend fun getMessagesForDid(
        did: String,
        limit: Int,
        offset: Int
    ): List<DidCommMessage> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                SELECT m.message_json, m.encrypted_data, m.key_version, m.iv, m.is_encrypted
                FROM didcomm_messages m
                JOIN didcomm_message_dids d ON m.id = d.message_id
                WHERE d.did = ? AND d.role IN ('from', 'to')
                ORDER BY m.created_time DESC NULLS LAST
                LIMIT ? OFFSET ?
            """).use { stmt ->
                stmt.setString(1, did)
                stmt.setInt(2, limit)
                stmt.setInt(3, offset)
                stmt.executeQuery().use { rs ->
                    val json = Json { ignoreUnknownKeys = true }
                    buildList {
                        while (rs.next()) {
                            val isEncrypted = rs.getBoolean("is_encrypted")

                            val message = if (isEncrypted && encryption != null) {
                                val encryptedData = rs.getBytes("encrypted_data")
                                val keyVersion = rs.getInt("key_version")
                                val iv = rs.getBytes("iv")

                                val encrypted = com.trustweave.credential.didcomm.storage.encryption.EncryptedMessage(
                                    keyVersion = keyVersion,
                                    encryptedData = encryptedData,
                                    iv = iv
                                )

                                encryption.decrypt(encrypted)
                            } else {
                                val messageJson = rs.getString("message_json")
                                json.decodeFromString(DidCommMessage.serializer(), messageJson)
                            }

                            add(message)
                        }
                    }
                }
            }
        }
    }

    override suspend fun getThreadMessages(thid: String): List<DidCommMessage> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                SELECT message_json, encrypted_data, key_version, iv, is_encrypted
                FROM didcomm_messages
                WHERE thid = ?
                ORDER BY created_time ASC NULLS LAST
            """).use { stmt ->
                stmt.setString(1, thid)
                stmt.executeQuery().use { rs ->
                    val json = Json { ignoreUnknownKeys = true }
                    buildList {
                        while (rs.next()) {
                            val isEncrypted = rs.getBoolean("is_encrypted")

                            val message = if (isEncrypted && encryption != null) {
                                val encryptedData = rs.getBytes("encrypted_data")
                                val keyVersion = rs.getInt("key_version")
                                val iv = rs.getBytes("iv")

                                val encrypted = com.trustweave.credential.didcomm.storage.encryption.EncryptedMessage(
                                    keyVersion = keyVersion,
                                    encryptedData = encryptedData,
                                    iv = iv
                                )

                                encryption.decrypt(encrypted)
                            } else {
                                val messageJson = rs.getString("message_json")
                                json.decodeFromString(DidCommMessage.serializer(), messageJson)
                            }

                            add(message)
                        }
                    }
                }
            }
        }
    }

    override suspend fun delete(messageId: String): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM didcomm_messages WHERE id = ?").use { stmt ->
                stmt.setString(1, messageId)
                stmt.executeUpdate() > 0
            }
        }
    }

    override suspend fun deleteMessagesForDid(did: String): Int = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                DELETE FROM didcomm_messages
                WHERE id IN (
                    SELECT message_id FROM didcomm_message_dids WHERE did = ?
                )
            """).use { stmt ->
                stmt.setString(1, did)
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun deleteThreadMessages(thid: String): Int = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM didcomm_messages WHERE thid = ?").use { stmt ->
                stmt.setString(1, thid)
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun countMessagesForDid(did: String): Int = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                SELECT COUNT(*) FROM didcomm_message_dids WHERE did = ?
            """).use { stmt ->
                stmt.setString(1, did)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt(1) else 0
                }
            }
        }
    }

    override suspend fun search(
        filter: MessageFilter,
        limit: Int,
        offset: Int
    ): List<DidCommMessage> = withContext(Dispatchers.IO) {
        // Build dynamic query based on filters
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()
        var paramIndex = 1

        filter.fromDid?.let {
            conditions.add("from_did = ?")
            params.add(it)
        }
        filter.toDid?.let {
            conditions.add("? = ANY(to_dids)")
            params.add(it)
        }
        filter.type?.let {
            conditions.add("type = ?")
            params.add(it)
        }
        filter.thid?.let {
            conditions.add("thid = ?")
            params.add(it)
        }
        filter.createdAfter?.let {
            conditions.add("created_time >= ?")
            params.add(it)
        }
        filter.createdBefore?.let {
            conditions.add("created_time <= ?")
            params.add(it)
        }

        val whereClause = if (conditions.isNotEmpty()) {
            "WHERE ${conditions.joinToString(" AND ")}"
        } else {
            ""
        }

        dataSource.connection.use { conn ->
            val sql = """
                SELECT message_json, encrypted_data, key_version, iv, is_encrypted
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

                stmt.executeQuery().use { rs ->
                    val json = Json { ignoreUnknownKeys = true }
                    buildList {
                        while (rs.next()) {
                            val messageJson = rs.getString("message_json")
                            add(json.decodeFromString(DidCommMessage.serializer(), messageJson))
                        }
                    }
                }
            }
        }
    }

    private fun createTables() {
        dataSource.connection.use { conn ->
            // Main messages table
            conn.createStatement().execute("""
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
            """)

            // Index for DID lookups
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS didcomm_message_dids (
                    message_id VARCHAR(255) REFERENCES didcomm_messages(id) ON DELETE CASCADE,
                    did VARCHAR(255) NOT NULL,
                    role VARCHAR(10) NOT NULL CHECK (role IN ('from', 'to')),
                    PRIMARY KEY (message_id, did, role)
                )
            """)

            // Index for thread lookups
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS didcomm_message_threads (
                    message_id VARCHAR(255) REFERENCES didcomm_messages(id) ON DELETE CASCADE,
                    thid VARCHAR(255) NOT NULL,
                    PRIMARY KEY (message_id, thid)
                )
            """)

            // Add encryption columns if they don't exist
            try {
                conn.createStatement().execute("""
                    ALTER TABLE didcomm_messages
                    ADD COLUMN IF NOT EXISTS encrypted_data BYTEA,
                    ADD COLUMN IF NOT EXISTS key_version INT,
                    ADD COLUMN IF NOT EXISTS iv BYTEA,
                    ADD COLUMN IF NOT EXISTS is_encrypted BOOLEAN DEFAULT FALSE
                """)
            } catch (e: SQLException) {
                // Columns may already exist, ignore
            }

            // Add archive columns if they don't exist
            try {
                conn.createStatement().execute("""
                    ALTER TABLE didcomm_messages
                    ADD COLUMN IF NOT EXISTS archived BOOLEAN DEFAULT FALSE,
                    ADD COLUMN IF NOT EXISTS archive_id VARCHAR(255),
                    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP
                """)
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

    private fun indexMessageForDid(conn: Connection, messageId: String, did: String, role: String) {
        conn.prepareStatement("""
            INSERT INTO didcomm_message_dids (message_id, did, role)
            VALUES (?, ?, ?)
            ON CONFLICT DO NOTHING
        """).use { stmt ->
            stmt.setString(1, messageId)
            stmt.setString(2, did)
            stmt.setString(3, role)
            stmt.executeUpdate()
        }
    }

    private fun indexMessageForThread(conn: Connection, messageId: String, thid: String) {
        conn.prepareStatement("""
            INSERT INTO didcomm_message_threads (message_id, thid)
            VALUES (?, ?)
            ON CONFLICT DO NOTHING
        """).use { stmt ->
            stmt.setString(1, messageId)
            stmt.setString(2, thid)
            stmt.executeUpdate()
        }
    }
}

