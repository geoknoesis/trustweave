package org.trustweave.wallet.database

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import org.slf4j.LoggerFactory
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.wallet.CredentialCollection
import org.trustweave.wallet.CredentialFilter
import org.trustweave.wallet.CredentialMetadata
import org.trustweave.wallet.CredentialOrganization
import org.trustweave.wallet.CredentialPage
import org.trustweave.wallet.CredentialQueryBuilder
import org.trustweave.wallet.CredentialRecordStorage
import org.trustweave.wallet.CredentialStorage
import org.trustweave.wallet.PagedCredentialStorage
import org.trustweave.wallet.StoredCredentialRecord
import org.trustweave.wallet.Wallet
import org.trustweave.wallet.WalletStatistics
import org.trustweave.wallet.WalletStatusResolver
import org.trustweave.wallet.exception.WalletException
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
    private val ownsDataSource: Boolean = false,
    private val statusResolver: WalletStatusResolver? = null,
) : Wallet,
    CredentialStorage,
    CredentialOrganization,
    CredentialRecordStorage,
    PagedCredentialStorage {
    private val json =
        Json {
            prettyPrint = false
            encodeDefaults = false
            ignoreUnknownKeys = true
        }

    private val metadataCodec = MetadataCodec(json)

    private val support = DatabaseWalletSupport(walletId, dataSource, json, metadataCodec)
    private val collections = DatabaseWalletCollections(walletId, dataSource, json, metadataCodec)
    private val tagging = DatabaseWalletTagging(walletId, dataSource, json, metadataCodec)

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
            statusResolver: WalletStatusResolver? = null,
        ): DatabaseWallet {
            val wallet = DatabaseWallet(walletId, walletDid, holderDid, dataSource, ownsDataSource, statusResolver)
            try {
                wallet.initializeSchema()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw WalletException.StorageError(
                    operation = "initializeSchema",
                    reason = "Failed to initialize wallet schema: ${e.message}",
                    cause = e,
                )
            }
            return wallet
        }
    }

    /**
     * Initialize database schema (tables for credentials, collections, tags, metadata).
     */
    private fun initializeSchema() {
        dataSource.connection.use { conn -> DatabaseWalletSchema.initialize(conn) }
    }

    /**
     * Dialect probe for upsert statements. PostgreSQL gets `ON CONFLICT … DO UPDATE`;
     * everything else (H2 in particular) gets standard SQL `MERGE` — H2 rejects
     * `ON CONFLICT` even in PostgreSQL compatibility mode.
     */
    private fun isPostgreSql(conn: Connection): Boolean = support.isPostgreSql(conn)

    /**
     * Acquires a connection, mapping acquisition failures (e.g. the pool was
     * closed via [close]) to [WalletException.StorageError] so callers see the
     * structured wallet error contract instead of a raw [java.sql.SQLException].
     */
    private fun acquireConnection(operation: String): Connection = support.acquireConnection(operation)

    private fun upsertMetadataRow(
        conn: Connection,
        credentialId: String,
    ) = support.upsertMetadataRow(conn, credentialId)

    // CredentialStorage implementation
    override suspend fun store(credential: VerifiableCredential): String =
        withContext(Dispatchers.IO) {
            val rawId = credential.id?.value ?: UUID.randomUUID().toString()
            if (rawId.length > 255) {
                throw WalletException.StorageError(
                    operation = "store",
                    reason = "Credential ID exceeds maximum length of 255 characters",
                )
            }
            // Reject credentials whose ID contains control characters outright. Silently sanitizing
            // would diverge the stored JSON `id` field from the database primary key.
            val safeId = rawId.replace(Regex("[\\r\\n\\t\\x00-\\x1F\\x7F]"), "?").take(255)
            if (rawId != safeId) {
                throw WalletException.StorageError(
                    operation = "store",
                    reason = "Credential ID contains invalid characters",
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
                    val upsertSql =
                        if (isPostgreSql(conn)) {
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
                    val upsertCount =
                        conn.prepareStatement(upsertSql).use { upsertStmt ->
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
                            reason = "Credential '$safeId' could not be stored: the credential ID may conflict with another wallet",
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
                        cause = e,
                    )
                } finally {
                    conn.autoCommit = savedAutoCommit
                }
            }
        }

    override suspend fun get(credentialId: String): VerifiableCredential? =
        withContext(Dispatchers.IO) {
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
                    conn
                        .prepareStatement(
                            """
                    SELECT credential_data FROM credentials
                    WHERE id = ? AND wallet_id = ?
                """,
                        ).use { stmt ->
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
                    cause = e,
                )
            }
        }

    override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> =
        withContext(Dispatchers.IO) {
            try {
                val rawResults = mutableListOf<VerifiableCredential>()

                dataSource.connection.use { conn ->
                    conn
                        .prepareStatement(
                            """
                    SELECT credential_data FROM credentials
                    WHERE wallet_id = ? AND archived = FALSE
                    ORDER BY id
                """,
                        ).use { stmt ->
                            stmt.setString(1, walletId)
                            stmt.executeQuery().use { rs ->
                                while (rs.next()) {
                                    val credentialJson = rs.getString("credential_data")
                                    rawResults.add(json.decodeFromString(VerifiableCredential.serializer(), credentialJson))
                                }
                            }
                        }
                }

                // The complete ordered result is filtered; the List API never silently truncates.
                if (filter == null) rawResults else rawResults.filter { matchesFilter(it, filter, statusResolver) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw WalletException.StorageError(
                    operation = "list",
                    reason = "Failed to list credentials: ${e.message}",
                    cause = e,
                )
            }
        }

    override suspend fun delete(credentialId: String): Boolean =
        withContext(Dispatchers.IO) {
            val rawCredentialId = credentialId
            val safeCredentialId =
                rawCredentialId
                    .replace(Regex("[\\r\\n\\t\\x00-\\x1F\\x7F]"), "?")
                    .take(255)
            if (rawCredentialId != safeCredentialId) {
                throw WalletException.StorageError(
                    operation = "delete",
                    reason = "Credential ID contains invalid characters",
                )
            }
            acquireConnection("delete").use { conn ->
                val savedAutoCommit = conn.autoCommit
                conn.autoCommit = false
                try {
                    val deleted =
                        conn
                            .prepareStatement(
                                """
                    DELETE FROM credentials WHERE id = ? AND wallet_id = ?
                """,
                            ).use { stmt ->
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
                        cause = e,
                    )
                } finally {
                    conn.autoCommit = savedAutoCommit
                }
            }
        }

    override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> =
        withContext(Dispatchers.IO) {
            val builder = CredentialQueryBuilder()
            builder.query()

            val predicate = builder.toPredicate()
            val requestedTags = builder.requestedTags
            val requestedCollections = builder.requestedCollections

            try {
                val rawResults = mutableListOf<VerifiableCredential>()

                dataSource.connection.use { conn ->
                    val sql = buildTagCollectionQuerySql(requestedTags, requestedCollections)

                    conn.prepareStatement(sql).use { stmt ->
                        bindTagCollectionQuery(stmt, walletId, requestedTags, requestedCollections)
                        stmt.executeQuery().use { rs ->
                            while (rs.next()) {
                                val credentialJson = rs.getString("credential_data")
                                rawResults.add(json.decodeFromString(VerifiableCredential.serializer(), credentialJson))
                            }
                        }
                    }
                }

                rawResults.filter(predicate)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw WalletException.StorageError(
                    operation = "query",
                    reason = "Failed to query credentials: ${e.message}",
                    cause = e,
                )
            }
        }

    // ----- CredentialOrganization: delegated to the internal operation groups -----

    override suspend fun createCollection(
        name: String,
        description: String?,
    ): String = collections.createCollection(name, description)

    override suspend fun getCollection(collectionId: String): CredentialCollection? = collections.getCollection(collectionId)

    override suspend fun listCollections(): List<CredentialCollection> = collections.listCollections()

    override suspend fun deleteCollection(collectionId: String): Boolean = collections.deleteCollection(collectionId)

    override suspend fun addToCollection(
        credentialId: String,
        collectionId: String,
    ): Boolean = collections.addToCollection(credentialId, collectionId)

    override suspend fun removeFromCollection(
        credentialId: String,
        collectionId: String,
    ): Boolean = collections.removeFromCollection(credentialId, collectionId)

    override suspend fun getCredentialsInCollection(collectionId: String): List<VerifiableCredential> =
        collections.getCredentialsInCollection(collectionId)

    override suspend fun tagCredential(
        credentialId: String,
        tags: Set<String>,
    ): Boolean = tagging.tagCredential(credentialId, tags)

    override suspend fun untagCredential(
        credentialId: String,
        tags: Set<String>,
    ): Boolean = tagging.untagCredential(credentialId, tags)

    override suspend fun getTags(credentialId: String): Set<String> = tagging.getTags(credentialId)

    override suspend fun getAllTags(): Set<String> = tagging.getAllTags()

    override suspend fun findByTag(tag: String): List<VerifiableCredential> = tagging.findByTag(tag)

    override suspend fun addMetadata(
        credentialId: String,
        metadata: Map<String, Any>,
    ): Boolean = tagging.addMetadata(credentialId, metadata)

    override suspend fun getMetadata(credentialId: String): CredentialMetadata? = tagging.getMetadata(credentialId)

    override suspend fun updateNotes(
        credentialId: String,
        notes: String?,
    ): Boolean = tagging.updateNotes(credentialId, notes)

    /**
     * One page of stored records ordered by storage ID, starting after the [after] cursor.
     *
     * On PostgreSQL the JSON parts of [filter] are pushed into the query (containment index);
     * status and expiry filters are applied to the fetched page after the result set is closed.
     *
     * @param limit page size, 1..500
     * @param after cursor returned by the previous page, or null for the first page
     */
    override suspend fun pageRecords(
        limit: Int,
        after: String?,
        filter: CredentialFilter?,
    ): CredentialPage =
        withContext(Dispatchers.IO) {
            require(limit in 1..500) { "Page size must be between 1 and 500" }
            require(after == null || after.length <= 2048) { "Invalid cursor" }
            val scanned =
                dataSource.connection.use { conn ->
                    val pageQuery = buildPageQuery(after, filter, isPostgreSql(conn))
                    conn
                        .prepareStatement(
                            "SELECT id, credential_data FROM credentials WHERE wallet_id = ? AND archived = FALSE" +
                                pageQuery.extraSql + " ORDER BY id LIMIT ?",
                        ).use { statement ->
                            statement.setString(1, walletId)
                            pageQuery.values.forEachIndexed { index, value -> statement.setString(index + 2, value) }
                            statement.setInt(pageQuery.values.size + 2, limit + 1)
                            statement.executeQuery().use { rows ->
                                buildList {
                                    while (rows.next()) {
                                        add(
                                            StoredCredentialRecord(
                                                rows.getString(1),
                                                json.decodeFromString(VerifiableCredential.serializer(), rows.getString(2)),
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                }
            val page = scanned.take(limit)
            CredentialPage(
                page.filter { filter == null || matchesFilter(it.credential, filter, statusResolver) },
                if (scanned.size > limit) page.last().storageId else null,
            )
        }

    override suspend fun listRecords(filter: CredentialFilter?): List<StoredCredentialRecord> =
        withContext(Dispatchers.IO) {
            val records = mutableListOf<StoredCredentialRecord>()
            dataSource.connection.use { connection ->
                connection
                    .prepareStatement(
                        "SELECT id, credential_data FROM credentials WHERE wallet_id = ? AND archived = FALSE ORDER BY id",
                    ).use { statement ->
                        statement.setString(1, walletId)
                        statement.executeQuery().use { rows ->
                            while (rows.next()) {
                                val credential = json.decodeFromString(VerifiableCredential.serializer(), rows.getString("credential_data"))
                                if (filter == null ||
                                    matchesFilter(credential, filter, statusResolver)
                                ) {
                                    records.add(StoredCredentialRecord(rows.getString("id"), credential))
                                }
                            }
                        }
                    }
            }
            records
        }

    /** Count in SQL without deserializing credentials or resolving remote status. */
    suspend fun countCredentials(includeArchived: Boolean = true): Int =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                val condition = if (includeArchived) "" else " AND archived = FALSE"
                connection.prepareStatement("SELECT COUNT(*) FROM credentials WHERE wallet_id = ?$condition").use { statement ->
                    statement.setString(1, walletId)
                    statement.executeQuery().use { rows ->
                        rows.next()
                        rows.getInt(1)
                    }
                }
            }
        }

    /** Live statistics scan bounded pages; use countCredentials for a cheap storage count. */
    override suspend fun getStatistics(): WalletStatistics =
        withContext(Dispatchers.IO) {
            val (total, activeCount) =
                dataSource.connection.use { connection ->
                    connection
                        .prepareStatement(
                            "SELECT COUNT(*), COALESCE(SUM(CASE WHEN archived = FALSE THEN 1 ELSE 0 END), 0) FROM credentials WHERE wallet_id = ?",
                        ).use { statement ->
                            statement.setString(1, walletId)
                            statement.executeQuery().use { rows ->
                                rows.next()
                                rows.getInt(1) to rows.getInt(2)
                            }
                        }
                }
            val accumulator = StatisticsAccumulator(statusResolver)
            var cursor: String? = null
            var remaining = activeCount
            do {
                val page = pageRecords(limit = minOf(500, remaining.coerceAtLeast(1)), after = cursor)
                page.records.forEach { accumulator.add(it.credential) }
                remaining -= page.records.size
                cursor = page.nextCursor
            } while (cursor != null && remaining > 0)
            WalletStatistics(
                totalCredentials = total,
                archivedCount = total - activeCount,
                validCredentials = accumulator.valid,
                expiredCredentials = accumulator.expired,
                revokedCredentials = accumulator.revoked,
                unknownStatusCredentials = accumulator.unknown,
            )
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
                    cause = e,
                )
            }
        }
    }
}
