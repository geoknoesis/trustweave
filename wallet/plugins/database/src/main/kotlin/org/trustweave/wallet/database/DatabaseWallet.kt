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
import kotlinx.datetime.toKotlinInstant
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

/**
 * Database-backed wallet implementation.
 *
 * Stores credentials, collections, tags, and metadata in a relational database.
 * Supports PostgreSQL (via `INSERT ... ON CONFLICT ... DO UPDATE`) and H2
 * (via standard SQL `MERGE`). MySQL is NOT supported: it implements neither
 * the PostgreSQL upsert syntax nor standard SQL MERGE.
 *
 * Implements [CredentialOrganization] (collections + tagging): tags and collection
 * memberships are stored in the `credential_tags` / `credential_collections` tables,
 * are wallet-scoped, and feed the `byTag` / `byCollection` query filters.
 *
 * **Resource ownership:** when [ownsDataSource] is true (e.g. wallets built by
 * [DatabaseWalletFactory], which creates a dedicated connection pool), [close]
 * shuts the pool down. When a [DataSource] is injected by the caller,
 * [ownsDataSource] must be false and [close] is a no-op — the caller manages
 * the pool's lifecycle.
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
    private val dataSource: DataSource,
    private val ownsDataSource: Boolean = false
) : Wallet, CredentialStorage, CredentialOrganization {

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
         *
         * @param ownsDataSource true if the returned wallet owns [dataSource] and must
         *   close it in [DatabaseWallet.close]; false (default) when the caller manages it.
         */
        fun create(
            walletId: String,
            walletDid: String,
            holderDid: String,
            dataSource: DataSource,
            ownsDataSource: Boolean = false,
        ): DatabaseWallet {
            val wallet = DatabaseWallet(walletId, walletDid, holderDid, dataSource, ownsDataSource)
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

    /**
     * Dialect probe for upsert statements. PostgreSQL gets `ON CONFLICT … DO UPDATE`;
     * everything else (H2 in particular) gets standard SQL `MERGE` — H2 rejects
     * `ON CONFLICT` even in PostgreSQL compatibility mode.
     */
    private fun isPostgreSql(conn: Connection): Boolean =
        conn.metaData.databaseProductName?.contains("PostgreSQL", ignoreCase = true) == true

    /**
     * Acquires a connection, mapping acquisition failures (e.g. the pool was
     * closed via [close]) to [WalletException.StorageError] so callers see the
     * structured wallet error contract instead of a raw [java.sql.SQLException].
     */
    private fun acquireConnection(operation: String): Connection =
        try {
            dataSource.connection
        } catch (e: java.sql.SQLException) {
            throw WalletException.StorageError(
                operation = operation,
                reason = "Failed to acquire database connection (is the wallet closed?): ${e.message}",
                cause = e
            )
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

        acquireConnection("store").use { conn ->
            val savedAutoCommit = conn.autoCommit
            conn.autoCommit = false
            try {
                // Upsert: a single atomic statement avoids a race condition where two concurrent
                // store() calls both see alreadyExists=false and both attempt INSERT, which would
                // cause a primary-key constraint violation on the second call.
                // Dialect note: PostgreSQL uses ON CONFLICT … DO UPDATE (its MERGE support only
                // arrived in PG 15 and has weaker concurrency guarantees); H2 does not parse
                // ON CONFLICT at all (not even in MODE=PostgreSQL), so it gets a standard SQL
                // MERGE with identical semantics. Both variants update 0 rows when the id is
                // already owned by a different wallet, which the conflict check below relies on.
                val upsertSql = if (isPostgreSql(conn)) {
                    """
                    INSERT INTO credentials (id, wallet_id, credential_data, archived)
                    VALUES (?, ?, ?, FALSE)
                    ON CONFLICT (id) DO UPDATE
                        SET credential_data = EXCLUDED.credential_data
                        WHERE credentials.wallet_id = EXCLUDED.wallet_id
                    """
                } else {
                    """
                    MERGE INTO credentials c
                    USING (VALUES (?, ?, ?)) AS src(id, wallet_id, credential_data)
                    ON c.id = src.id
                    WHEN MATCHED AND c.wallet_id = src.wallet_id THEN
                        UPDATE SET credential_data = src.credential_data
                    WHEN NOT MATCHED THEN
                        INSERT (id, wallet_id, credential_data, archived)
                        VALUES (src.id, src.wallet_id, src.credential_data, FALSE)
                    """
                }
                val upsertCount = conn.prepareStatement(upsertSql).use { upsertStmt ->
                    upsertStmt.setString(1, safeId)
                    upsertStmt.setString(2, walletId)
                    upsertStmt.setString(3, credentialJson)
                    upsertStmt.executeUpdate()
                }

                // If 0 rows were affected, the conditional update was skipped because the
                // existing row's wallet_id differs from ours — meaning the credential ID already
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
                upsertMetadataRow(conn, safeId)

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
        acquireConnection("delete").use { conn ->
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
        val requestedTags = builder.requestedTags
        val requestedCollections = builder.requestedCollections

        try {
            val rawResults = mutableListOf<VerifiableCredential>()

            dataSource.connection.use { conn ->
                // Tag/collection filters are pushed down to SQL because tags and
                // collections are wallet-level metadata stored in the credential_tags /
                // credential_collections tables, keyed by the database credential id —
                // they cannot be evaluated against the credential JSON in memory.
                // Semantics: a credential must carry ALL requested tags and belong to
                // ALL requested collections (AND, consistent with predicate chaining).
                val sql = buildString {
                    append("SELECT credential_data FROM credentials WHERE wallet_id = ? AND archived = FALSE")
                    if (requestedTags.isNotEmpty()) {
                        val placeholders = requestedTags.joinToString(", ") { "?" }
                        append(
                            " AND id IN (SELECT credential_id FROM credential_tags WHERE tag IN ($placeholders)" +
                                " GROUP BY credential_id HAVING COUNT(DISTINCT tag) = ?)"
                        )
                    }
                    if (requestedCollections.isNotEmpty()) {
                        val placeholders = requestedCollections.joinToString(", ") { "?" }
                        append(
                            " AND id IN (SELECT credential_id FROM credential_collections WHERE collection_id IN ($placeholders)" +
                                " GROUP BY credential_id HAVING COUNT(DISTINCT collection_id) = ?)"
                        )
                    }
                    append(" LIMIT 1000")
                }

                conn.prepareStatement(sql).use { stmt ->
                    var index = 1
                    stmt.setString(index++, walletId)
                    requestedTags.forEach { stmt.setString(index++, it) }
                    if (requestedTags.isNotEmpty()) {
                        stmt.setInt(index++, requestedTags.size)
                    }
                    requestedCollections.forEach { stmt.setString(index++, it) }
                    if (requestedCollections.isNotEmpty()) {
                        stmt.setInt(index++, requestedCollections.size)
                    }
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
                    "query() fetched {} credentials — results may be truncated at 1000. " +
                        "Implement full SQL predicate pushdown for complete results.",
                    rawResults.size
                )
            }

            rawResults.filter(predicate)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw WalletException.StorageError(
                operation = "query",
                reason = "Failed to query credentials: ${e.message}",
                cause = e
            )
        }
    }

    // -------------------------------------------------------------------------
    // CredentialOrganization implementation (collections + tagging)
    //
    // All operations are wallet-scoped: tags and collection memberships are only
    // visible/mutable through the wallet that owns the underlying credential row
    // (credential ids are globally unique primary keys, so junction rows are
    // scoped via joins against credentials.wallet_id).
    // -------------------------------------------------------------------------

    /**
     * Wraps [block] so failures surface as [WalletException.StorageError] with the
     * structured wallet error contract, consistent with the CredentialStorage methods.
     */
    private inline fun <T> withStorageErrorHandling(operation: String, block: () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: WalletException) {
            throw e
        } catch (e: Exception) {
            throw WalletException.StorageError(
                operation = operation,
                reason = "Failed to $operation: ${e.message}",
                cause = e
            )
        }

    /** True when [credentialId] exists in THIS wallet (active or archived). */
    private fun credentialExists(conn: Connection, credentialId: String): Boolean =
        conn.prepareStatement("SELECT 1 FROM credentials WHERE id = ? AND wallet_id = ?").use { stmt ->
            stmt.setString(1, credentialId)
            stmt.setString(2, walletId)
            stmt.executeQuery().use { it.next() }
        }

    /** True when [collectionId] exists and belongs to THIS wallet. */
    private fun collectionExists(conn: Connection, collectionId: String): Boolean =
        conn.prepareStatement("SELECT 1 FROM collections WHERE id = ? AND wallet_id = ?").use { stmt ->
            stmt.setString(1, collectionId)
            stmt.setString(2, walletId)
            stmt.executeQuery().use { it.next() }
        }

    /**
     * Ensure a `credential_metadata` row exists for [credentialId]: insert with
     * created_at + updated_at on new rows; on conflict only bump updated_at,
     * preserving the original created_at (and any existing notes/metadata).
     */
    private fun upsertMetadataRow(conn: Connection, credentialId: String) {
        val metadataSql = if (isPostgreSql(conn)) {
            """
            INSERT INTO credential_metadata (credential_id, created_at, updated_at)
            VALUES (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (credential_id) DO UPDATE SET updated_at = EXCLUDED.updated_at
            """
        } else {
            """
            MERGE INTO credential_metadata m
            USING (VALUES (?)) AS src(credential_id)
            ON m.credential_id = src.credential_id
            WHEN MATCHED THEN
                UPDATE SET updated_at = CURRENT_TIMESTAMP
            WHEN NOT MATCHED THEN
                INSERT (credential_id, created_at, updated_at)
                VALUES (src.credential_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """
        }
        conn.prepareStatement(metadataSql).use { metadataStmt ->
            metadataStmt.setString(1, credentialId)
            metadataStmt.executeUpdate()
        }
    }

    /** Serialize a metadata map to JSON. Non-primitive values are stored via toString(). */
    private fun metadataMapToJson(metadata: Map<String, Any>): String {
        val obj = buildJsonObject {
            metadata.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, value)
                    is Boolean -> put(key, value)
                    is Number -> put(key, JsonPrimitive(value))
                    is JsonElement -> put(key, value)
                    else -> put(key, value.toString())
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    /** Deserialize a metadata JSON column back into a map of primitives. */
    private fun metadataJsonToMap(metadataJson: String?): Map<String, Any> {
        if (metadataJson.isNullOrBlank()) return emptyMap()
        val obj = json.decodeFromString(JsonObject.serializer(), metadataJson)
        return obj.mapValues { (_, value) ->
            when {
                value is JsonPrimitive && value.isString -> value.content
                value is JsonPrimitive && value.booleanOrNull != null -> value.booleanOrNull as Any
                value is JsonPrimitive && value.longOrNull != null -> value.longOrNull as Any
                value is JsonPrimitive && value.doubleOrNull != null -> value.doubleOrNull as Any
                else -> value
            }
        }
    }

    private fun java.sql.ResultSet.instantOrNow(column: String): Instant =
        getTimestamp(column)?.toInstant()?.toKotlinInstant() ?: Clock.System.now()

    // ----- CredentialCollections -----

    override suspend fun createCollection(name: String, description: String?): String = withContext(Dispatchers.IO) {
        withStorageErrorHandling("createCollection") {
            val id = UUID.randomUUID().toString()
            acquireConnection("createCollection").use { conn ->
                conn.prepareStatement(
                    "INSERT INTO collections (id, wallet_id, name, description) VALUES (?, ?, ?, ?)"
                ).use { stmt ->
                    stmt.setString(1, id)
                    stmt.setString(2, walletId)
                    stmt.setString(3, name)
                    stmt.setString(4, description)
                    stmt.executeUpdate()
                }
            }
            id
        }
    }

    override suspend fun getCollection(collectionId: String): CredentialCollection? = withContext(Dispatchers.IO) {
        withStorageErrorHandling("getCollection") {
            acquireConnection("getCollection").use { conn ->
                conn.prepareStatement(
                    """
                    SELECT c.id, c.name, c.description, c.created_at,
                           (SELECT COUNT(*) FROM credential_collections cc WHERE cc.collection_id = c.id) AS credential_count
                    FROM collections c
                    WHERE c.id = ? AND c.wallet_id = ?
                    """
                ).use { stmt ->
                    stmt.setString(1, collectionId)
                    stmt.setString(2, walletId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            CredentialCollection(
                                id = rs.getString("id"),
                                name = rs.getString("name"),
                                description = rs.getString("description"),
                                createdAt = rs.instantOrNow("created_at"),
                                credentialCount = rs.getInt("credential_count")
                            )
                        } else {
                            null
                        }
                    }
                }
            }
        }
    }

    override suspend fun listCollections(): List<CredentialCollection> = withContext(Dispatchers.IO) {
        withStorageErrorHandling("listCollections") {
            acquireConnection("listCollections").use { conn ->
                conn.prepareStatement(
                    """
                    SELECT c.id, c.name, c.description, c.created_at,
                           (SELECT COUNT(*) FROM credential_collections cc WHERE cc.collection_id = c.id) AS credential_count
                    FROM collections c
                    WHERE c.wallet_id = ?
                    """
                ).use { stmt ->
                    stmt.setString(1, walletId)
                    stmt.executeQuery().use { rs ->
                        val collections = mutableListOf<CredentialCollection>()
                        while (rs.next()) {
                            collections.add(
                                CredentialCollection(
                                    id = rs.getString("id"),
                                    name = rs.getString("name"),
                                    description = rs.getString("description"),
                                    createdAt = rs.instantOrNow("created_at"),
                                    credentialCount = rs.getInt("credential_count")
                                )
                            )
                        }
                        collections
                    }
                }
            }
        }
    }

    override suspend fun deleteCollection(collectionId: String): Boolean = withContext(Dispatchers.IO) {
        withStorageErrorHandling("deleteCollection") {
            acquireConnection("deleteCollection").use { conn ->
                // Junction rows are removed by the ON DELETE CASCADE foreign key.
                conn.prepareStatement("DELETE FROM collections WHERE id = ? AND wallet_id = ?").use { stmt ->
                    stmt.setString(1, collectionId)
                    stmt.setString(2, walletId)
                    stmt.executeUpdate() > 0
                }
            }
        }
    }

    override suspend fun addToCollection(credentialId: String, collectionId: String): Boolean = withContext(Dispatchers.IO) {
        withStorageErrorHandling("addToCollection") {
            acquireConnection("addToCollection").use { conn ->
                if (!credentialExists(conn, credentialId) || !collectionExists(conn, collectionId)) {
                    return@use false
                }
                // Idempotent insert: re-adding an existing membership is a no-op, not an error.
                val sql = if (isPostgreSql(conn)) {
                    """
                    INSERT INTO credential_collections (credential_id, collection_id)
                    VALUES (?, ?)
                    ON CONFLICT DO NOTHING
                    """
                } else {
                    """
                    MERGE INTO credential_collections cc
                    USING (VALUES (?, ?)) AS src(credential_id, collection_id)
                    ON cc.credential_id = src.credential_id AND cc.collection_id = src.collection_id
                    WHEN NOT MATCHED THEN
                        INSERT (credential_id, collection_id) VALUES (src.credential_id, src.collection_id)
                    """
                }
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, credentialId)
                    stmt.setString(2, collectionId)
                    stmt.executeUpdate()
                }
                true
            }
        }
    }

    override suspend fun removeFromCollection(credentialId: String, collectionId: String): Boolean = withContext(Dispatchers.IO) {
        withStorageErrorHandling("removeFromCollection") {
            acquireConnection("removeFromCollection").use { conn ->
                // Scoped to this wallet's collections so another wallet cannot detach memberships.
                conn.prepareStatement(
                    """
                    DELETE FROM credential_collections
                    WHERE credential_id = ? AND collection_id = ?
                      AND collection_id IN (SELECT id FROM collections WHERE wallet_id = ?)
                    """
                ).use { stmt ->
                    stmt.setString(1, credentialId)
                    stmt.setString(2, collectionId)
                    stmt.setString(3, walletId)
                    stmt.executeUpdate() > 0
                }
            }
        }
    }

    override suspend fun getCredentialsInCollection(collectionId: String): List<VerifiableCredential> = withContext(Dispatchers.IO) {
        withStorageErrorHandling("getCredentialsInCollection") {
            acquireConnection("getCredentialsInCollection").use { conn ->
                conn.prepareStatement(
                    """
                    SELECT cr.credential_data FROM credentials cr
                    JOIN credential_collections cc ON cc.credential_id = cr.id
                    WHERE cc.collection_id = ? AND cr.wallet_id = ?
                    """
                ).use { stmt ->
                    stmt.setString(1, collectionId)
                    stmt.setString(2, walletId)
                    stmt.executeQuery().use { rs ->
                        val credentials = mutableListOf<VerifiableCredential>()
                        while (rs.next()) {
                            credentials.add(
                                json.decodeFromString(VerifiableCredential.serializer(), rs.getString("credential_data"))
                            )
                        }
                        credentials
                    }
                }
            }
        }
    }

    // ----- CredentialTagging -----

    override suspend fun tagCredential(credentialId: String, tags: Set<String>): Boolean = withContext(Dispatchers.IO) {
        withStorageErrorHandling("tagCredential") {
            acquireConnection("tagCredential").use { conn ->
                if (!credentialExists(conn, credentialId)) {
                    return@use false
                }
                val savedAutoCommit = conn.autoCommit
                conn.autoCommit = false
                try {
                    // Idempotent insert per tag: re-tagging is a no-op, not a PK violation.
                    val sql = if (isPostgreSql(conn)) {
                        """
                        INSERT INTO credential_tags (credential_id, tag)
                        VALUES (?, ?)
                        ON CONFLICT DO NOTHING
                        """
                    } else {
                        """
                        MERGE INTO credential_tags t
                        USING (VALUES (?, ?)) AS src(credential_id, tag)
                        ON t.credential_id = src.credential_id AND t.tag = src.tag
                        WHEN NOT MATCHED THEN
                            INSERT (credential_id, tag) VALUES (src.credential_id, src.tag)
                        """
                    }
                    conn.prepareStatement(sql).use { stmt ->
                        tags.forEach { tag ->
                            stmt.setString(1, credentialId)
                            stmt.setString(2, tag)
                            stmt.executeUpdate()
                        }
                    }
                    conn.commit()
                    true
                } catch (e: Exception) {
                    runCatching { conn.rollback() }
                    throw e
                } finally {
                    conn.autoCommit = savedAutoCommit
                }
            }
        }
    }

    override suspend fun untagCredential(credentialId: String, tags: Set<String>): Boolean = withContext(Dispatchers.IO) {
        withStorageErrorHandling("untagCredential") {
            acquireConnection("untagCredential").use { conn ->
                if (!credentialExists(conn, credentialId)) {
                    return@use false
                }
                if (tags.isEmpty()) {
                    return@use true
                }
                val placeholders = tags.joinToString(", ") { "?" }
                conn.prepareStatement(
                    "DELETE FROM credential_tags WHERE credential_id = ? AND tag IN ($placeholders)"
                ).use { stmt ->
                    var index = 1
                    stmt.setString(index++, credentialId)
                    tags.forEach { stmt.setString(index++, it) }
                    stmt.executeUpdate()
                }
                true
            }
        }
    }

    override suspend fun getTags(credentialId: String): Set<String> = withContext(Dispatchers.IO) {
        withStorageErrorHandling("getTags") {
            acquireConnection("getTags").use { conn ->
                readTags(conn, credentialId)
            }
        }
    }

    private fun readTags(conn: Connection, credentialId: String): Set<String> =
        conn.prepareStatement(
            """
            SELECT ct.tag FROM credential_tags ct
            JOIN credentials c ON c.id = ct.credential_id
            WHERE ct.credential_id = ? AND c.wallet_id = ?
            """
        ).use { stmt ->
            stmt.setString(1, credentialId)
            stmt.setString(2, walletId)
            stmt.executeQuery().use { rs ->
                val tags = mutableSetOf<String>()
                while (rs.next()) {
                    tags.add(rs.getString("tag"))
                }
                tags
            }
        }

    override suspend fun getAllTags(): Set<String> = withContext(Dispatchers.IO) {
        withStorageErrorHandling("getAllTags") {
            acquireConnection("getAllTags").use { conn ->
                conn.prepareStatement(
                    """
                    SELECT DISTINCT ct.tag FROM credential_tags ct
                    JOIN credentials c ON c.id = ct.credential_id
                    WHERE c.wallet_id = ?
                    """
                ).use { stmt ->
                    stmt.setString(1, walletId)
                    stmt.executeQuery().use { rs ->
                        val tags = mutableSetOf<String>()
                        while (rs.next()) {
                            tags.add(rs.getString("tag"))
                        }
                        tags
                    }
                }
            }
        }
    }

    override suspend fun findByTag(tag: String): List<VerifiableCredential> = withContext(Dispatchers.IO) {
        withStorageErrorHandling("findByTag") {
            acquireConnection("findByTag").use { conn ->
                conn.prepareStatement(
                    """
                    SELECT c.credential_data FROM credentials c
                    JOIN credential_tags ct ON ct.credential_id = c.id
                    WHERE ct.tag = ? AND c.wallet_id = ?
                    """
                ).use { stmt ->
                    stmt.setString(1, tag)
                    stmt.setString(2, walletId)
                    stmt.executeQuery().use { rs ->
                        val credentials = mutableListOf<VerifiableCredential>()
                        while (rs.next()) {
                            credentials.add(
                                json.decodeFromString(VerifiableCredential.serializer(), rs.getString("credential_data"))
                            )
                        }
                        credentials
                    }
                }
            }
        }
    }

    override suspend fun addMetadata(credentialId: String, metadata: Map<String, Any>): Boolean = withContext(Dispatchers.IO) {
        withStorageErrorHandling("addMetadata") {
            acquireConnection("addMetadata").use { conn ->
                if (!credentialExists(conn, credentialId)) {
                    return@use false
                }
                val savedAutoCommit = conn.autoCommit
                conn.autoCommit = false
                try {
                    upsertMetadataRow(conn, credentialId)
                    // Merge with existing metadata (new keys win), mirroring InMemoryWallet.
                    // FOR UPDATE: the read-modify-write must hold the row lock, or two
                    // concurrent addMetadata calls can lose each other's keys.
                    val existing = conn.prepareStatement(
                        "SELECT metadata_json FROM credential_metadata WHERE credential_id = ? FOR UPDATE"
                    ).use { stmt ->
                        stmt.setString(1, credentialId)
                        stmt.executeQuery().use { rs -> if (rs.next()) rs.getString("metadata_json") else null }
                    }
                    val merged = metadataJsonToMap(existing) + metadata
                    conn.prepareStatement(
                        "UPDATE credential_metadata SET metadata_json = ?, updated_at = CURRENT_TIMESTAMP WHERE credential_id = ?"
                    ).use { stmt ->
                        stmt.setString(1, metadataMapToJson(merged))
                        stmt.setString(2, credentialId)
                        stmt.executeUpdate()
                    }
                    conn.commit()
                    true
                } catch (e: Exception) {
                    runCatching { conn.rollback() }
                    throw e
                } finally {
                    conn.autoCommit = savedAutoCommit
                }
            }
        }
    }

    override suspend fun getMetadata(credentialId: String): CredentialMetadata? = withContext(Dispatchers.IO) {
        withStorageErrorHandling("getMetadata") {
            acquireConnection("getMetadata").use { conn ->
                conn.prepareStatement(
                    """
                    SELECT m.notes, m.metadata_json, m.created_at, m.updated_at
                    FROM credential_metadata m
                    JOIN credentials c ON c.id = m.credential_id
                    WHERE m.credential_id = ? AND c.wallet_id = ?
                    """
                ).use { stmt ->
                    stmt.setString(1, credentialId)
                    stmt.setString(2, walletId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            CredentialMetadata(
                                credentialId = credentialId,
                                notes = rs.getString("notes"),
                                tags = readTags(conn, credentialId),
                                metadata = metadataJsonToMap(rs.getString("metadata_json")),
                                createdAt = rs.instantOrNow("created_at"),
                                updatedAt = rs.instantOrNow("updated_at")
                            )
                        } else {
                            null
                        }
                    }
                }
            }
        }
    }

    override suspend fun updateNotes(credentialId: String, notes: String?): Boolean = withContext(Dispatchers.IO) {
        withStorageErrorHandling("updateNotes") {
            acquireConnection("updateNotes").use { conn ->
                if (!credentialExists(conn, credentialId)) {
                    return@use false
                }
                upsertMetadataRow(conn, credentialId)
                conn.prepareStatement(
                    "UPDATE credential_metadata SET notes = ?, updated_at = CURRENT_TIMESTAMP WHERE credential_id = ?"
                ).use { stmt ->
                    stmt.setString(1, notes)
                    stmt.setString(2, credentialId)
                    stmt.executeUpdate()
                }
                true
            }
        }
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

    /**
     * Close the connection pool — but only when this wallet owns it.
     *
     * Wallets created by [DatabaseWalletFactory] own their pool ([ownsDataSource] = true);
     * wallets constructed with an externally managed [DataSource] must not close it.
     * Idempotent: closing an already-closed pool is a no-op for HikariCP.
     */
    override fun close() {
        if (ownsDataSource && dataSource is AutoCloseable) {
            try {
                dataSource.close()
            } catch (e: Exception) {
                throw WalletException.StorageError(
                    operation = "close",
                    reason = "Failed to close wallet-owned DataSource: ${e.message}",
                    cause = e
                )
            }
        }
    }
}

