package org.trustweave.wallet.database

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.wallet.*
import org.trustweave.wallet.exception.WalletException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.slf4j.LoggerFactory
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

    companion object {
        private val logger = LoggerFactory.getLogger(DatabaseWallet::class.java)

        /**
         * Factory function that initializes the database schema before returning the wallet.
         * Use this instead of the constructor to ensure schema errors are wrapped as [WalletException.StorageError].
         */
        fun create(
            walletId: String,
            walletDid: String,
            holderDid: String,
            dataSource: DataSource,
        ): DatabaseWallet {
            val wallet = DatabaseWallet(walletId, walletDid, holderDid, dataSource)
            try {
                wallet.initializeSchema()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw WalletException.StorageError(
                    operation = "initializeSchema",
                    reason = "Failed to initialize wallet schema: ${e.message}",
                    cause = e
                )
            }
            return wallet
        }
    }

    /**
     * Initialize database schema (tables for credentials, collections, tags, metadata).
     */
    private fun initializeSchema() {
        dataSource.connection.use { conn ->
            val savedAutoCommit = conn.autoCommit
            conn.autoCommit = false
            try {
                // Credentials table
                conn.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS credentials (
                        id VARCHAR(255) PRIMARY KEY,
                        wallet_id VARCHAR(255) NOT NULL,
                        credential_data TEXT NOT NULL,
                        archived BOOLEAN DEFAULT FALSE,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """).use { it.execute() }
                conn.prepareStatement("CREATE INDEX IF NOT EXISTS idx_credentials_wallet_id ON credentials(wallet_id)").use { it.execute() }
                conn.prepareStatement("CREATE INDEX IF NOT EXISTS idx_credentials_archived ON credentials(archived)").use { it.execute() }

                // Collections table
                conn.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS collections (
                        id VARCHAR(255) PRIMARY KEY,
                        wallet_id VARCHAR(255) NOT NULL,
                        name VARCHAR(255) NOT NULL,
                        description TEXT,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """).use { it.execute() }
                conn.prepareStatement("CREATE INDEX IF NOT EXISTS idx_collections_wallet_id ON collections(wallet_id)").use { it.execute() }

                // Credential collections junction table
                conn.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS credential_collections (
                        credential_id VARCHAR(255) NOT NULL,
                        collection_id VARCHAR(255) NOT NULL,
                        PRIMARY KEY (credential_id, collection_id),
                        FOREIGN KEY (credential_id) REFERENCES credentials(id) ON DELETE CASCADE,
                        FOREIGN KEY (collection_id) REFERENCES collections(id) ON DELETE CASCADE
                    )
                """).use { it.execute() }
                conn.prepareStatement("CREATE INDEX IF NOT EXISTS idx_cred_collections_credential_id ON credential_collections(credential_id)").use { it.execute() }
                conn.prepareStatement("CREATE INDEX IF NOT EXISTS idx_cred_collections_collection_id ON credential_collections(collection_id)").use { it.execute() }

                // Tags table
                conn.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS credential_tags (
                        credential_id VARCHAR(255) NOT NULL,
                        tag VARCHAR(255) NOT NULL,
                        PRIMARY KEY (credential_id, tag),
                        FOREIGN KEY (credential_id) REFERENCES credentials(id) ON DELETE CASCADE
                    )
                """).use { it.execute() }
                conn.prepareStatement("CREATE INDEX IF NOT EXISTS idx_credential_tags_credential_id ON credential_tags(credential_id)").use { it.execute() }
                conn.prepareStatement("CREATE INDEX IF NOT EXISTS idx_credential_tags_tag ON credential_tags(tag)").use { it.execute() }

                // Metadata table
                conn.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS credential_metadata (
                        credential_id VARCHAR(255) PRIMARY KEY,
                        notes TEXT,
                        metadata_json TEXT,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (credential_id) REFERENCES credentials(id) ON DELETE CASCADE
                    )
                """).use { it.execute() }

                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = savedAutoCommit
            }
        }
    }

    // CredentialStorage implementation
    override suspend fun store(credential: VerifiableCredential): String = withContext(Dispatchers.IO) {
        val rawId = credential.id?.value ?: UUID.randomUUID().toString()
        if (rawId.length > 255) {
            throw WalletException.StorageError(
                operation = "store",
                reason = "Credential ID exceeds maximum length of 255 characters"
            )
        }
        // Reject credentials whose ID contains control characters outright. Silently sanitizing
        // would diverge the stored JSON `id` field from the database primary key.
        val safeId = rawId.replace(Regex("[\\r\\n\\t\\x00-\\x1F\\x7F]"), "?").take(255)
        if (rawId != safeId) {
            throw WalletException.StorageError(
                operation = "store",
                reason = "Credential ID contains invalid characters"
            )
        }
        val credentialJson = json.encodeToString(VerifiableCredential.serializer(), credential)

        dataSource.connection.use { conn ->
            val savedAutoCommit = conn.autoCommit
            conn.autoCommit = false
            try {
                // Upsert: a single atomic statement avoids a race condition where two concurrent
                // store() calls both see alreadyExists=false and both attempt INSERT, which would
                // cause a primary-key constraint violation on the second call.
                // H2 2.x and PostgreSQL both support the ON CONFLICT … DO UPDATE syntax.
                val upsertCount = conn.prepareStatement("""
                    INSERT INTO credentials (id, wallet_id, credential_data, archived)
                    VALUES (?, ?, ?, FALSE)
                    ON CONFLICT (id) DO UPDATE
                        SET credential_data = EXCLUDED.credential_data
                        WHERE credentials.wallet_id = EXCLUDED.wallet_id
                """).use { upsertStmt ->
                    upsertStmt.setString(1, safeId)
                    upsertStmt.setString(2, walletId)
                    upsertStmt.setString(3, credentialJson)
                    upsertStmt.executeUpdate()
                }

                // If 0 rows were affected, the ON CONFLICT DO UPDATE was skipped because
                // credentials.wallet_id != EXCLUDED.wallet_id — meaning the credential ID already
                // belongs to a different wallet. Fail immediately; a subsequent SELECT would
                // introduce a TOCTOU race and still produce an ambiguous result.
                if (upsertCount == 0) {
                    conn.rollback()
                    throw WalletException.StorageError(
                        operation = "store",
                        reason = "Credential '$safeId' could not be stored: the credential ID may conflict with another wallet"
                    )
                }

                // SEC-10: Single upsert for metadata: insert with created_at + updated_at on new
                // rows; on conflict (re-store) update only updated_at, preserving the original
                // created_at. The prior code ran UPDATE first then INSERT, which silently did
                // nothing on a new credential (the UPDATE matched 0 rows) and on a re-store the
                // INSERT was skipped by DO NOTHING even though updated_at had just been bumped —
                // effectively the two statements were semantically inverted.
                conn.prepareStatement("""
                    INSERT INTO credential_metadata (credential_id, created_at, updated_at)
                    VALUES (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (credential_id) DO UPDATE SET updated_at = EXCLUDED.updated_at
                """).use { metadataStmt ->
                    metadataStmt.setString(1, safeId)
                    metadataStmt.executeUpdate()
                }

                conn.commit()
                safeId
            } catch (e: CancellationException) {
                runCatching { conn.rollback() }
                throw e
            } catch (e: WalletException) {
                // Already a structured wallet exception (e.g. conflict on upsert — rollback was
                // already called before throwing); re-throw as-is to preserve the original message.
                throw e
            } catch (e: Exception) {
                runCatching { conn.rollback() }
                throw WalletException.StorageError(
                    operation = "store",
                    reason = "Failed to store credential '$safeId': ${e.message}",
                    cause = e
                )
            } finally {
                conn.autoCommit = savedAutoCommit
            }
        }
    }

    override suspend fun get(credentialId: String): VerifiableCredential? = withContext(Dispatchers.IO) {
        val rawCredentialId = credentialId
        val safeCredentialId = rawCredentialId.replace(Regex("[\\r\\n\\t\\x00-\\x1F\\x7F]"), "?").take(255)
        // Reject IDs that contain control characters outright, consistent with store() behaviour.
        // Silently looking up the sanitized key would return the wrong row when the sanitised
        // form happens to match a legitimately stored ID.
        if (rawCredentialId != safeCredentialId) {
            return@withContext null
        }
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement("""
                    SELECT credential_data FROM credentials
                    WHERE id = ? AND wallet_id = ?
                """).use { stmt ->
                    stmt.setString(1, safeCredentialId)
                    stmt.setString(2, walletId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            val credentialJson = rs.getString("credential_data")
                            json.decodeFromString(VerifiableCredential.serializer(), credentialJson)
                        } else {
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw WalletException.StorageError(
                operation = "get",
                reason = "Failed to retrieve credential: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> = withContext(Dispatchers.IO) {
        try {
            val rawResults = mutableListOf<VerifiableCredential>()

            dataSource.connection.use { conn ->
                // TODO: LIMIT applied before in-memory filter — callers with >1000 credentials and a
                //   filter may get incomplete results. Full fix requires pushing filter predicates to
                //   SQL WHERE clauses or adding cursor-based pagination, neither of which is currently
                //   supported by CredentialFilter's API. Until then, a warning is emitted when the
                //   raw result set hits the limit so operators are alerted to potential data loss.
                conn.prepareStatement("""
                    SELECT credential_data FROM credentials
                    WHERE wallet_id = ? AND archived = FALSE
                    LIMIT 1000
                """).use { stmt ->
                    stmt.setString(1, walletId)
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val credentialJson = rs.getString("credential_data")
                            rawResults.add(json.decodeFromString(VerifiableCredential.serializer(), credentialJson))
                        }
                    }
                }
            }

            if (rawResults.size >= 1000) {
                logger.warn(
                    "list() returned exactly {} credentials — results may be truncated. " +
                        "Implement SQL-level filtering or pagination to avoid silent data loss.",
                    rawResults.size
                )
            }

            // Apply in-memory filter after the truncation check so the warning fires on raw size.
            if (filter == null) rawResults else rawResults.filter { matchesFilter(it, filter) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw WalletException.StorageError(
                operation = "list",
                reason = "Failed to list credentials: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun delete(credentialId: String): Boolean = withContext(Dispatchers.IO) {
        val rawCredentialId = credentialId
        val safeCredentialId = rawCredentialId
            .replace(Regex("[\\r\\n\\t\\x00-\\x1F\\x7F]"), "?").take(255)
        if (rawCredentialId != safeCredentialId) {
            throw WalletException.StorageError(
                operation = "delete",
                reason = "Credential ID contains invalid characters"
            )
        }
        dataSource.connection.use { conn ->
            val savedAutoCommit = conn.autoCommit
            conn.autoCommit = false
            try {
                val deleted = conn.prepareStatement("""
                    DELETE FROM credentials WHERE id = ? AND wallet_id = ?
                """).use { stmt ->
                    stmt.setString(1, safeCredentialId)
                    stmt.setString(2, walletId)
                    stmt.executeUpdate() > 0
                }

                conn.commit()
                deleted
            } catch (e: CancellationException) {
                runCatching { conn.rollback() }
                throw e
            } catch (e: Exception) {
                runCatching { conn.rollback() }
                throw WalletException.StorageError(
                    operation = "delete",
                    reason = "Failed to delete credential '$safeCredentialId': ${e.message}",
                    cause = e
                )
            } finally {
                conn.autoCommit = savedAutoCommit
            }
        }
    }

    override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> = withContext(Dispatchers.IO) {
        val builder = CredentialQueryBuilder()
        builder.query()

        val predicate = builder.toPredicate()
        val allCredentials = list(null)  // note: limited to 1000 rows
        if (allCredentials.size >= 1000) {
            logger.warn(
                "query() fetched {} credentials from list() — results may be truncated at 1000. " +
                    "Implement SQL predicate pushdown for complete results.",
                allCredentials.size
            )
        }
        allCredentials.filter(predicate)
    }

    /**
     * Check if credential matches filter criteria.
     */
    private fun matchesFilter(credential: VerifiableCredential, filter: CredentialFilter): Boolean {
        if (filter.issuer != null && credential.issuer.id.value != filter.issuer) return false
        filter.type?.let { filterTypes ->
            if (!filterTypes.any { filterType -> credential.type.any { it.value == filterType } }) return false
        }
        if (filter.subjectId != null) {
            val subjectId = credential.credentialSubject.id?.value
            if (subjectId != filter.subjectId) return false
        }
        if (filter.expired != null) {
            val effectiveExpiry = if (credential.isVc2 && !credential.isVc1) {
                credential.validUntil
            } else {
                credential.validUntil ?: credential.expirationDate
            }
            val isExpired = effectiveExpiry?.let { Clock.System.now() > it } ?: false
            if (isExpired != filter.expired) return false
        }
        if (filter.revoked != null) {
            if (filter.revoked == true) {
                logger.warn(
                    "CredentialFilter.revoked only checks for credentialStatus presence, not actual revocation. " +
                        "Use a CredentialRevocationManager for definitive status."
                )
            }
            // NOTE: DatabaseWallet does not have access to a live revocation manager,
            // so this check uses credentialStatus != null as a proxy meaning
            // "hasStatusEntry" — not "is actually revoked".
            // filter.revoked=true  → returns only credentials that have a status entry
            // filter.revoked=false → returns only credentials that have no status entry
            val hasStatusEntry = credential.credentialStatus != null
            if (hasStatusEntry != filter.revoked) return false
        }
        return true
    }

    /**
     * Get wallet statistics.
     */
    override suspend fun getStatistics(): WalletStatistics = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { conn ->
                val sql = """
                    SELECT
                        COUNT(*) AS total,
                        SUM(CASE WHEN archived = TRUE THEN 1 ELSE 0 END) AS archived_count
                    FROM credentials
                    WHERE wallet_id = ?
                """.trimIndent()
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, walletId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            val total = rs.getLong("total").toInt()
                            val archived = rs.getLong("archived_count").toInt()
                            WalletStatistics(
                                totalCredentials = total,
                                validCredentials = total - archived, // approximate: counts non-archived, not semantically valid (may include expired/revoked)
                                expiredCredentials = 0, // Would require parsing all credentials
                                revokedCredentials = 0, // Would require parsing all credentials
                                archivedCount = archived
                            )
                        } else {
                            WalletStatistics(totalCredentials = 0, archivedCount = 0, validCredentials = 0)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw WalletException.StorageError(
                operation = "getStatistics",
                reason = "Failed to retrieve wallet statistics: ${e.message}",
                cause = e
            )
        }
    }
}

