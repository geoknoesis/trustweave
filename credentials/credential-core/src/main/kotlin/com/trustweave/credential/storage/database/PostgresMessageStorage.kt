package com.trustweave.credential.storage.database

import com.trustweave.credential.storage.*
import com.trustweave.credential.storage.encryption.EncryptedMessage
import com.trustweave.credential.storage.encryption.MessageEncryption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*
import java.sql.PreparedStatement
import java.sql.*
import java.util.Base64
import javax.sql.DataSource

/**
 * Generic PostgreSQL-backed message storage.
 * 
 * Works with any protocol message that implements ProtocolMessage.
 * 
 * **Example Usage:**
 * ```kotlin
 * // DIDComm
 * val storage = PostgresMessageStorage(
 *     serializer = DidCommMessage.serializer(),
 *     dataSource = dataSource,
 *     tableName = "didcomm_messages"
 * )
 * 
 * // OIDC4VCI
 * val oidcStorage = PostgresMessageStorage(
 *     serializer = Oidc4VciOffer.serializer(),
 *     dataSource = dataSource,
 *     tableName = "oidc4vci_offers"
 * )
 * ```
 */
class PostgresMessageStorage<T : ProtocolMessage>(
    private val serializer: KSerializer<T>,
    private val dataSource: DataSource,
    private val tableName: String = "protocol_messages",
    private val encryption: MessageEncryption? = null
) : ProtocolMessageStorage<T> {
    
    private val json = Json { 
        prettyPrint = false
        encodeDefaults = false
        ignoreUnknownKeys = true
    }
    
    init {
        createTables()
    }
    
    override suspend fun store(message: T): String = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val messageJson = json.encodeToString(serializer, message)
            
            if (encryption != null) {
                // Encrypt message
                val plaintext = messageJson.toByteArray(Charsets.UTF_8)
                val encrypted = encryption.encrypt(plaintext)
                
                conn.prepareStatement("""
                    INSERT INTO $tableName (
                        id, type, from_participant, to_participants, body, created_time,
                        expires_time, thread_id, parent_thread_id, message_json,
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
                    setMessageParameters(stmt, message, messageJson, encrypted)
                    stmt.executeUpdate()
                }
            } else {
                // Store unencrypted
                conn.prepareStatement("""
                    INSERT INTO $tableName (
                        id, type, from_participant, to_participants, body, created_time,
                        expires_time, thread_id, parent_thread_id, message_json, is_encrypted
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        message_json = EXCLUDED.message_json,
                        body = EXCLUDED.body,
                        is_encrypted = EXCLUDED.is_encrypted
                """).use { stmt ->
                    setMessageParameters(stmt, message, messageJson, null)
                    stmt.executeUpdate()
                }
            }
            
            // Index by participant
            message.from?.let { indexMessageForParticipant(conn, message.messageId, it, "from") }
            message.to.forEach { indexMessageForParticipant(conn, message.messageId, it, "to") }
            
            // Index by thread
            message.threadId?.let { indexMessageForThread(conn, message.messageId, it) }
            
            message.messageId
        }
    }
    
    override suspend fun get(messageId: String): T? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                SELECT message_json, encrypted_data, key_version, iv, is_encrypted
                FROM $tableName WHERE id = ?
            """).use { stmt ->
                stmt.setString(1, messageId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        val isEncrypted = rs.getBoolean("is_encrypted")
                        
                        if (isEncrypted && encryption != null) {
                            // Decrypt message
                            val encryptedData = rs.getBytes("encrypted_data")
                            val keyVersion = rs.getInt("key_version")
                            val iv = rs.getBytes("iv")
                            
                            val encrypted = EncryptedMessage(
                                keyVersion = keyVersion,
                                encryptedData = encryptedData,
                                iv = iv
                            )
                            
                            val plaintext = encryption.decrypt(encrypted)
                            val jsonString = String(plaintext, Charsets.UTF_8)
                            json.decodeFromString(serializer, jsonString)
                        } else {
                            // Read unencrypted
                            val messageJson = rs.getString("message_json")
                            json.decodeFromString(serializer, messageJson)
                        }
                    } else {
                        null
                    }
                }
            }
        }
    }
    
    override suspend fun getMessagesForParticipant(
        participantId: String,
        limit: Int,
        offset: Int
    ): List<T> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                SELECT m.message_json, m.encrypted_data, m.key_version, m.iv, m.is_encrypted
                FROM $tableName m
                JOIN ${tableName}_participants p ON m.id = p.message_id
                WHERE p.participant_id = ? AND p.role IN ('from', 'to')
                ORDER BY m.created_time DESC NULLS LAST
                LIMIT ? OFFSET ?
            """).use { stmt ->
                stmt.setString(1, participantId)
                stmt.setInt(2, limit)
                stmt.setInt(3, offset)
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            val message = deserializeMessage(rs)
                            if (message != null) add(message)
                        }
                    }
                }
            }
        }
    }
    
    override suspend fun getThreadMessages(threadId: String): List<T> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                SELECT message_json, encrypted_data, key_version, iv, is_encrypted
                FROM $tableName
                WHERE thread_id = ?
                ORDER BY created_time ASC NULLS LAST
            """).use { stmt ->
                stmt.setString(1, threadId)
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            val message = deserializeMessage(rs)
                            if (message != null) add(message)
                        }
                    }
                }
            }
        }
    }
    
    override suspend fun delete(messageId: String): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM $tableName WHERE id = ?").use { stmt ->
                stmt.setString(1, messageId)
                stmt.executeUpdate() > 0
            }
        }
    }
    
    override suspend fun deleteMessagesForParticipant(participantId: String): Int = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                DELETE FROM $tableName
                WHERE id IN (
                    SELECT message_id FROM ${tableName}_participants WHERE participant_id = ?
                )
            """).use { stmt ->
                stmt.setString(1, participantId)
                stmt.executeUpdate()
            }
        }
    }
    
    override suspend fun deleteThreadMessages(threadId: String): Int = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM $tableName WHERE thread_id = ?").use { stmt ->
                stmt.setString(1, threadId)
                stmt.executeUpdate()
            }
        }
    }
    
    override suspend fun countMessagesForParticipant(participantId: String): Int = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                SELECT COUNT(*) FROM $tableName m
                JOIN ${tableName}_participants p ON m.id = p.message_id
                WHERE p.participant_id = ? AND p.role IN ('from', 'to')
            """).use { stmt ->
                stmt.setString(1, participantId)
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
    ): List<T> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val (whereClause, params) = buildWhereClause(filter)
            val sql = """
                SELECT message_json, encrypted_data, key_version, iv, is_encrypted
                FROM $tableName
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
                    buildList {
                        while (rs.next()) {
                            val message = deserializeMessage(rs)
                            if (message != null) add(message)
                        }
                    }
                }
            }
        }
    }
    
    override fun setEncryption(encryption: MessageEncryption?) {
        // Note: Encryption is set in constructor
        // To change encryption, create a new storage instance
    }
    
    override suspend fun markAsArchived(messageIds: List<String>, archiveId: String): Unit = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                UPDATE $tableName
                SET archived = TRUE, archive_id = ?, archived_at = CURRENT_TIMESTAMP
                WHERE id = ANY(?)
            """).use { stmt ->
                val messageIdArray = conn.createArrayOf("VARCHAR", messageIds.toTypedArray())
                stmt.setString(1, archiveId)
                stmt.setArray(2, messageIdArray)
                stmt.executeUpdate()
            }
        }
    }
    
    override suspend fun isArchived(messageId: String): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                SELECT archived FROM $tableName WHERE id = ?
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
    
    // Helper methods
    
    private fun setMessageParameters(
        stmt: PreparedStatement,
        message: T,
        messageJson: String,
        encrypted: EncryptedMessage?
    ) {
        var index = 1
        stmt.setString(index++, message.messageId)
        stmt.setString(index++, message.messageType)
        stmt.setString(index++, message.from)
        stmt.setArray(index++, (stmt.connection as Connection).createArrayOf("VARCHAR", message.to.toTypedArray()))
        stmt.setString(index++, "{}") // Body placeholder
        stmt.setString(index++, message.created)
        stmt.setString(index++, message.expiresTime)
        stmt.setString(index++, message.threadId)
        stmt.setString(index++, message.parentThreadId)
        stmt.setString(index++, messageJson)
        
        if (encrypted != null) {
            stmt.setBytes(index++, encrypted.encryptedData)
            stmt.setInt(index++, encrypted.keyVersion)
            stmt.setBytes(index++, encrypted.iv)
            stmt.setBoolean(index++, true)
        } else {
            stmt.setBoolean(index++, false)
        }
    }
    
    private suspend fun deserializeMessage(rs: ResultSet): T? {
        return try {
            val isEncrypted = rs.getBoolean("is_encrypted")
            
            if (isEncrypted && encryption != null) {
                val encryptedData = rs.getBytes("encrypted_data")
                val keyVersion = rs.getInt("key_version")
                val iv = rs.getBytes("iv")
                
                val encrypted = EncryptedMessage(
                    keyVersion = keyVersion,
                    encryptedData = encryptedData,
                    iv = iv
                )
                
                val plaintext = encryption.decrypt(encrypted)
                val jsonString = String(plaintext, Charsets.UTF_8)
                json.decodeFromString(serializer, jsonString)
            } else {
                val messageJson = rs.getString("message_json")
                json.decodeFromString(serializer, messageJson)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun buildWhereClause(filter: MessageFilter): Pair<String, List<Any>> {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()
        
        filter.fromParticipant?.let {
            conditions.add("from_participant = ?")
            params.add(it)
        }
        filter.toParticipant?.let {
            conditions.add("? = ANY(to_participants)")
            params.add(it)
        }
        filter.type?.let {
            conditions.add("type = ?")
            params.add(it)
        }
        filter.threadId?.let {
            conditions.add("thread_id = ?")
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
        
        return whereClause to params
    }
    
    private fun createTables() {
        dataSource.connection.use { conn ->
            // Create main messages table
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS $tableName (
                    id VARCHAR(255) PRIMARY KEY,
                    type VARCHAR(255) NOT NULL,
                    from_participant VARCHAR(255),
                    to_participants VARCHAR(255)[],
                    body JSONB,
                    created_time VARCHAR(255),
                    expires_time VARCHAR(255),
                    thread_id VARCHAR(255),
                    parent_thread_id VARCHAR(255),
                    message_json JSONB NOT NULL,
                    encrypted_data BYTEA,
                    key_version INT,
                    iv BYTEA,
                    is_encrypted BOOLEAN DEFAULT FALSE,
                    archived BOOLEAN DEFAULT FALSE,
                    archive_id VARCHAR(255),
                    archived_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """)
            
            // Create participants index table
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS ${tableName}_participants (
                    message_id VARCHAR(255) NOT NULL,
                    participant_id VARCHAR(255) NOT NULL,
                    role VARCHAR(50) NOT NULL,
                    PRIMARY KEY (message_id, participant_id, role),
                    FOREIGN KEY (message_id) REFERENCES $tableName(id) ON DELETE CASCADE
                )
            """)
            
            // Create threads index table
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS ${tableName}_threads (
                    message_id VARCHAR(255) NOT NULL,
                    thread_id VARCHAR(255) NOT NULL,
                    PRIMARY KEY (message_id, thread_id),
                    FOREIGN KEY (message_id) REFERENCES $tableName(id) ON DELETE CASCADE
                )
            """)
            
            // Create indexes
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_${tableName}_from ON $tableName(from_participant)")
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_${tableName}_thread ON $tableName(thread_id)")
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_${tableName}_created ON $tableName(created_time)")
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_${tableName}_type ON $tableName(type)")
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_${tableName}_key_version ON $tableName(key_version)")
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_${tableName}_archived ON $tableName(archived)")
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_${tableName}_participants_id ON ${tableName}_participants(participant_id)")
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_${tableName}_threads_id ON ${tableName}_threads(thread_id)")
        }
    }
    
    private fun indexMessageForParticipant(
        conn: Connection,
        messageId: String,
        participantId: String,
        role: String
    ) {
        conn.prepareStatement("""
            INSERT INTO ${tableName}_participants (message_id, participant_id, role)
            VALUES (?, ?, ?)
            ON CONFLICT DO NOTHING
        """).use { stmt ->
            stmt.setString(1, messageId)
            stmt.setString(2, participantId)
            stmt.setString(3, role)
            stmt.executeUpdate()
        }
    }
    
    private fun indexMessageForThread(conn: Connection, messageId: String, threadId: String) {
        conn.prepareStatement("""
            INSERT INTO ${tableName}_threads (message_id, thread_id)
            VALUES (?, ?)
            ON CONFLICT DO NOTHING
        """).use { stmt ->
            stmt.setString(1, messageId)
            stmt.setString(2, threadId)
            stmt.executeUpdate()
        }
    }
}

