package com.trustweave.wallet.database

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.wallet.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import java.time.Instant as JavaInstant
import java.util.UUID
import javax.sql.DataSource

/**
 * Database-backed wallet implementation.
 *
 * Stores credentials, collections, tags, and metadata in a relational database.
 * Supports PostgreSQL, MySQL, and H2 databases.
 *
 * **Example:**
 * ```kotlin
 * val wallet = DatabaseWallet(
 *     walletId = "wallet-1",
 *     walletDid = "did:key:wallet-1",
 *     holderDid = "did:key:holder",
 *     dataSource = dataSource
 * )
 * ```
 */
class DatabaseWallet(
    override val walletId: String,
    val walletDid: String,
    val holderDid: String,
    private val dataSource: DataSource
) : Wallet, CredentialStorage {

    private val json = Json {
        prettyPrint = false
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    init {
        // Initialize database schema
        initializeSchema()
    }

    /**
     * Initialize database schema (tables for credentials, collections, tags, metadata).
     */
    private fun initializeSchema() {
        dataSource.connection.use { conn ->
            // Credentials table
            conn.prepareStatement("""
                CREATE TABLE IF NOT EXISTS credentials (
                    id VARCHAR(255) PRIMARY KEY,
                    wallet_id VARCHAR(255) NOT NULL,
                    credential_data TEXT NOT NULL,
                    archived BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_wallet_id (wallet_id),
                    INDEX idx_archived (archived)
                )
            """).execute()

            // Collections table
            conn.prepareStatement("""
                CREATE TABLE IF NOT EXISTS collections (
                    id VARCHAR(255) PRIMARY KEY,
                    wallet_id VARCHAR(255) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    description TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_wallet_id (wallet_id)
                )
            """).execute()

            // Credential collections junction table
            conn.prepareStatement("""
                CREATE TABLE IF NOT EXISTS credential_collections (
                    credential_id VARCHAR(255) NOT NULL,
                    collection_id VARCHAR(255) NOT NULL,
                    PRIMARY KEY (credential_id, collection_id),
                    FOREIGN KEY (credential_id) REFERENCES credentials(id) ON DELETE CASCADE,
                    FOREIGN KEY (collection_id) REFERENCES collections(id) ON DELETE CASCADE,
                    INDEX idx_credential_id (credential_id),
                    INDEX idx_collection_id (collection_id)
                )
            """).execute()

            // Tags table
            conn.prepareStatement("""
                CREATE TABLE IF NOT EXISTS credential_tags (
                    credential_id VARCHAR(255) NOT NULL,
                    tag VARCHAR(255) NOT NULL,
                    PRIMARY KEY (credential_id, tag),
                    FOREIGN KEY (credential_id) REFERENCES credentials(id) ON DELETE CASCADE,
                    INDEX idx_credential_id (credential_id),
                    INDEX idx_tag (tag)
                )
            """).execute()

            // Metadata table
            conn.prepareStatement("""
                CREATE TABLE IF NOT EXISTS credential_metadata (
                    credential_id VARCHAR(255) PRIMARY KEY,
                    notes TEXT,
                    metadata_json TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    FOREIGN KEY (credential_id) REFERENCES credentials(id) ON DELETE CASCADE
                )
            """).execute()

            conn.commit()
        }
    }

    // CredentialStorage implementation
    override suspend fun store(credential: VerifiableCredential): String = withContext(Dispatchers.IO) {
        val id = credential.id?.value ?: UUID.randomUUID().toString()
        val credentialJson = json.encodeToString(VerifiableCredential.serializer(), credential)

        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                // Delete existing credential if present
                val deleteStmt = conn.prepareStatement("DELETE FROM credentials WHERE id = ? AND wallet_id = ?")
                deleteStmt.setString(1, id)
                deleteStmt.setString(2, walletId)
                deleteStmt.executeUpdate()

                // Insert credential
                val stmt = conn.prepareStatement("""
                    INSERT INTO credentials (id, wallet_id, credential_data, archived)
                    VALUES (?, ?, ?, FALSE)
                """)
                stmt.setString(1, id)
                stmt.setString(2, walletId)
                stmt.setString(3, credentialJson)
                stmt.executeUpdate()

                // Initialize metadata if not exists
                val checkMetadataStmt = conn.prepareStatement("SELECT COUNT(*) as count FROM credential_metadata WHERE credential_id = ?")
                checkMetadataStmt.setString(1, id)
                val checkRs = checkMetadataStmt.executeQuery()
                val exists = checkRs.next() && checkRs.getInt("count") > 0

                if (!exists) {
                    val metadataStmt = conn.prepareStatement("""
                        INSERT INTO credential_metadata (credential_id, created_at, updated_at)
                        VALUES (?, ?, ?)
                    """)
                    metadataStmt.setString(1, id)
                    val now = Clock.System.now()
                    val javaInstant = JavaInstant.ofEpochSecond(now.epochSeconds)
                    metadataStmt.setTimestamp(2, java.sql.Timestamp.from(javaInstant))
                    metadataStmt.setTimestamp(3, java.sql.Timestamp.from(javaInstant))
                    metadataStmt.executeUpdate()
                }

                conn.commit()
                id
            } catch (e: Exception) {
                conn.rollback()
                throw RuntimeException("Failed to store credential: ${e.message}", e)
            }
        }
    }

    override suspend fun get(credentialId: String): VerifiableCredential? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val stmt = conn.prepareStatement("""
                SELECT credential_data FROM credentials
                WHERE id = ? AND wallet_id = ?
            """)
            stmt.setString(1, credentialId)
            stmt.setString(2, walletId)

            val rs = stmt.executeQuery()
            if (rs.next()) {
                val credentialJson = rs.getString("credential_data")
                json.decodeFromString(VerifiableCredential.serializer(), credentialJson)
            } else {
                null
            }
        }
    }

    override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> = withContext(Dispatchers.IO) {
        val credentials = mutableListOf<VerifiableCredential>()

        dataSource.connection.use { conn ->
            val stmt = conn.prepareStatement("""
                SELECT credential_data FROM credentials
                WHERE wallet_id = ? AND archived = FALSE
            """)
            stmt.setString(1, walletId)

            val rs = stmt.executeQuery()
            while (rs.next()) {
                val credentialJson = rs.getString("credential_data")
                val credential = json.decodeFromString(VerifiableCredential.serializer(), credentialJson)

                // Apply filter if provided
                if (filter == null || matchesFilter(credential, filter)) {
                    credentials.add(credential)
                }
            }
        }

        credentials
    }

    override suspend fun delete(credentialId: String): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val stmt = conn.prepareStatement("""
                    DELETE FROM credentials WHERE id = ? AND wallet_id = ?
                """)
                stmt.setString(1, credentialId)
                stmt.setString(2, walletId)
                val deleted = stmt.executeUpdate() > 0

                conn.commit()
                deleted
            } catch (e: Exception) {
                conn.rollback()
                false
            }
        }
    }

    override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> = withContext(Dispatchers.IO) {
        val builder = CredentialQueryBuilder()
        builder.query()

        // Use reflection to call createPredicate()
        val predicateMethod = builder::class.java.getMethod("createPredicate")
        @Suppress("UNCHECKED_CAST")
        val predicate = predicateMethod.invoke(builder) as (VerifiableCredential) -> Boolean

        // Get all credentials and filter
        val allCredentials = list(null)
        allCredentials.filter(predicate)
    }

    /**
     * Check if credential matches filter criteria.
     */
    private fun matchesFilter(credential: VerifiableCredential, filter: CredentialFilter): Boolean {
        if (filter.issuer != null && credential.issuer.id.value != filter.issuer) return false
        if (filter.type != null) {
            val filterTypes = filter.type
            if (filterTypes != null && !filterTypes.any { filterType -> credential.type.any { it.value == filterType } }) return false
        }
        if (filter.subjectId != null) {
            val subjectId = credential.credentialSubject.id.value
            if (subjectId != filter.subjectId) return false
        }
        if (filter.expired != null) {
            val isExpired = credential.expirationDate?.let {
                Clock.System.now() > it
            } ?: false
            if (isExpired != filter.expired) return false
        }
        if (filter.revoked != null) {
            val isRevoked = credential.credentialStatus != null
            if (isRevoked != filter.revoked) return false
        }
        return true
    }

    /**
     * Get wallet statistics.
     */
    override suspend fun getStatistics(): WalletStatistics = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val totalStmt = conn.prepareStatement("""
                SELECT COUNT(*) as total FROM credentials WHERE wallet_id = ?
            """)
            totalStmt.setString(1, walletId)
            val totalRs = totalStmt.executeQuery()
            val total = if (totalRs.next()) totalRs.getInt("total") else 0

            val archivedStmt = conn.prepareStatement("""
                SELECT COUNT(*) as archived FROM credentials
                WHERE wallet_id = ? AND archived = TRUE
            """)
            archivedStmt.setString(1, walletId)
            val archivedRs = archivedStmt.executeQuery()
            val archived = if (archivedRs.next()) archivedRs.getInt("archived") else 0

            WalletStatistics(
                totalCredentials = total,
                validCredentials = (total - archived),
                expiredCredentials = 0, // Would require parsing all credentials
                revokedCredentials = 0,  // Would require parsing all credentials
                archivedCount = archived
            )
        }
    }
}

