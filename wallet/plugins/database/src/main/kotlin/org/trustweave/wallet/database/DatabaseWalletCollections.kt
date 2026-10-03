package org.trustweave.wallet.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.wallet.CredentialCollection
import java.util.UUID
import javax.sql.DataSource

/** Collection operations of [DatabaseWallet] (`CredentialCollections`), behaviour unchanged. */
internal class DatabaseWalletCollections(
    walletId: String,
    dataSource: DataSource,
    json: Json,
    metadataCodec: MetadataCodec,
) : DatabaseWalletSupport(walletId, dataSource, json, metadataCodec) {
    suspend fun createCollection(
        name: String,
        description: String?,
    ): String =
        withContext(Dispatchers.IO) {
            withStorageErrorHandling("createCollection") {
                val id = UUID.randomUUID().toString()
                acquireConnection("createCollection").use { conn ->
                    conn
                        .prepareStatement(
                            "INSERT INTO collections (id, wallet_id, name, description) VALUES (?, ?, ?, ?)",
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

    suspend fun getCollection(collectionId: String): CredentialCollection? =
        withContext(Dispatchers.IO) {
            withStorageErrorHandling("getCollection") {
                acquireConnection("getCollection").use { conn ->
                    conn
                        .prepareStatement(
                            """
                    SELECT c.id, c.name, c.description, c.created_at,
                           (SELECT COUNT(*) FROM credential_collections cc WHERE cc.collection_id = c.id) AS credential_count
                    FROM collections c
                    WHERE c.id = ? AND c.wallet_id = ?
                    """,
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
                                        credentialCount = rs.getInt("credential_count"),
                                    )
                                } else {
                                    null
                                }
                            }
                        }
                }
            }
        }

    suspend fun listCollections(): List<CredentialCollection> =
        withContext(Dispatchers.IO) {
            withStorageErrorHandling("listCollections") {
                acquireConnection("listCollections").use { conn ->
                    conn
                        .prepareStatement(
                            """
                    SELECT c.id, c.name, c.description, c.created_at,
                           (SELECT COUNT(*) FROM credential_collections cc WHERE cc.collection_id = c.id) AS credential_count
                    FROM collections c
                    WHERE c.wallet_id = ?
                    """,
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
                                            credentialCount = rs.getInt("credential_count"),
                                        ),
                                    )
                                }
                                collections
                            }
                        }
                }
            }
        }

    suspend fun deleteCollection(collectionId: String): Boolean =
        withContext(Dispatchers.IO) {
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

    suspend fun addToCollection(
        credentialId: String,
        collectionId: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            withStorageErrorHandling("addToCollection") {
                acquireConnection("addToCollection").use { conn ->
                    if (!credentialExists(conn, credentialId) || !collectionExists(conn, collectionId)) {
                        return@use false
                    }
                    // Idempotent insert: re-adding an existing membership is a no-op, not an error.
                    val sql =
                        if (isPostgreSql(conn)) {
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

    suspend fun removeFromCollection(
        credentialId: String,
        collectionId: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            withStorageErrorHandling("removeFromCollection") {
                acquireConnection("removeFromCollection").use { conn ->
                    // Scoped to this wallet's collections so another wallet cannot detach memberships.
                    conn
                        .prepareStatement(
                            """
                    DELETE FROM credential_collections
                    WHERE credential_id = ? AND collection_id = ?
                      AND collection_id IN (SELECT id FROM collections WHERE wallet_id = ?)
                    """,
                        ).use { stmt ->
                            stmt.setString(1, credentialId)
                            stmt.setString(2, collectionId)
                            stmt.setString(3, walletId)
                            stmt.executeUpdate() > 0
                        }
                }
            }
        }

    suspend fun getCredentialsInCollection(collectionId: String): List<VerifiableCredential> =
        withContext(Dispatchers.IO) {
            withStorageErrorHandling("getCredentialsInCollection") {
                acquireConnection("getCredentialsInCollection").use { conn ->
                    conn
                        .prepareStatement(
                            """
                    SELECT cr.credential_data FROM credentials cr
                    JOIN credential_collections cc ON cc.credential_id = cr.id
                    WHERE cc.collection_id = ? AND cr.wallet_id = ?
                    """,
                        ).use { stmt ->
                            stmt.setString(1, collectionId)
                            stmt.setString(2, walletId)
                            stmt.executeQuery().use { rs ->
                                val credentials = mutableListOf<VerifiableCredential>()
                                while (rs.next()) {
                                    credentials.add(
                                        json.decodeFromString(VerifiableCredential.serializer(), rs.getString("credential_data")),
                                    )
                                }
                                credentials
                            }
                        }
                }
            }
        }
}
