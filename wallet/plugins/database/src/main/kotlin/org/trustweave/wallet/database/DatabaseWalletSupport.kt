package org.trustweave.wallet.database

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.trustweave.wallet.exception.WalletException
import java.sql.Connection
import javax.sql.DataSource

/**
 * Wallet-scoped JDBC helpers shared by [DatabaseWallet] and its internal operation groups
 * ([DatabaseWalletCollections], [DatabaseWalletTagging]).
 *
 * All operations are wallet-scoped: tags and collection memberships are only
 * visible/mutable through the wallet that owns the underlying credential row
 * (credential ids are globally unique primary keys, so junction rows are
 * scoped via joins against credentials.wallet_id).
 */
internal open class DatabaseWalletSupport(
    protected val walletId: String,
    protected val dataSource: DataSource,
    protected val json: Json,
    protected val metadataCodec: MetadataCodec,
) {
    /** Dialect probe for upsert statements (PostgreSQL `ON CONFLICT`, otherwise standard `MERGE`). */
    fun isPostgreSql(conn: Connection): Boolean = DatabaseWalletSchema.isPostgreSql(conn)

    /**
     * Acquires a connection, mapping acquisition failures (e.g. the pool was
     * closed) to [WalletException.StorageError] so callers see the
     * structured wallet error contract instead of a raw [java.sql.SQLException].
     */
    fun acquireConnection(operation: String): Connection =
        try {
            dataSource.connection
        } catch (e: java.sql.SQLException) {
            throw WalletException.StorageError(
                operation = operation,
                reason = "Failed to acquire database connection (is the wallet closed?): ${e.message}",
                cause = e,
            )
        }

    /**
     * Wraps [block] so failures surface as [WalletException.StorageError] with the
     * structured wallet error contract, consistent with the CredentialStorage methods.
     */
    inline fun <T> withStorageErrorHandling(
        operation: String,
        block: () -> T,
    ): T =
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
                cause = e,
            )
        }

    /** True when [credentialId] exists in THIS wallet (active or archived). */
    fun credentialExists(
        conn: Connection,
        credentialId: String,
    ): Boolean =
        conn.prepareStatement("SELECT 1 FROM credentials WHERE id = ? AND wallet_id = ?").use { stmt ->
            stmt.setString(1, credentialId)
            stmt.setString(2, walletId)
            stmt.executeQuery().use { it.next() }
        }

    /** True when [collectionId] exists and belongs to THIS wallet. */
    fun collectionExists(
        conn: Connection,
        collectionId: String,
    ): Boolean =
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
    fun upsertMetadataRow(
        conn: Connection,
        credentialId: String,
    ) {
        val metadataSql =
            if (isPostgreSql(conn)) {
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
}
