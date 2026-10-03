package org.trustweave.wallet.database

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.wallet.CredentialMetadata
import java.sql.Connection
import javax.sql.DataSource

/** Tagging, metadata and notes operations of [DatabaseWallet] (`CredentialTagging`), behaviour unchanged. */
internal class DatabaseWalletTagging(
    walletId: String,
    dataSource: DataSource,
    json: Json,
    metadataCodec: MetadataCodec,
) : DatabaseWalletSupport(walletId, dataSource, json, metadataCodec) {
    suspend fun tagCredential(
        credentialId: String,
        tags: Set<String>,
    ): Boolean =
        withContext(Dispatchers.IO) {
            withStorageErrorHandling("tagCredential") {
                acquireConnection("tagCredential").use { conn ->
                    if (!credentialExists(conn, credentialId)) {
                        return@use false
                    }
                    val savedAutoCommit = conn.autoCommit
                    conn.autoCommit = false
                    try {
                        // Idempotent insert per tag: re-tagging is a no-op, not a PK violation.
                        val sql =
                            if (isPostgreSql(conn)) {
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
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        runCatching { conn.rollback() }
                        throw e
                    } finally {
                        conn.autoCommit = savedAutoCommit
                    }
                }
            }
        }

    suspend fun untagCredential(
        credentialId: String,
        tags: Set<String>,
    ): Boolean =
        withContext(Dispatchers.IO) {
            withStorageErrorHandling("untagCredential") {
                acquireConnection("untagCredential").use { conn ->
                    if (!credentialExists(conn, credentialId)) {
                        return@use false
                    }
                    if (tags.isEmpty()) {
                        return@use true
                    }
                    val placeholders = tags.joinToString(", ") { "?" }
                    conn
                        .prepareStatement(
                            "DELETE FROM credential_tags WHERE credential_id = ? AND tag IN ($placeholders)",
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

    suspend fun getTags(credentialId: String): Set<String> =
        withContext(Dispatchers.IO) {
            withStorageErrorHandling("getTags") {
                acquireConnection("getTags").use { conn ->
                    readTags(conn, credentialId)
                }
            }
        }

    private fun readTags(
        conn: Connection,
        credentialId: String,
    ): Set<String> =
        conn
            .prepareStatement(
                """
            SELECT ct.tag FROM credential_tags ct
            JOIN credentials c ON c.id = ct.credential_id
            WHERE ct.credential_id = ? AND c.wallet_id = ?
            """,
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

    suspend fun getAllTags(): Set<String> =
        withContext(Dispatchers.IO) {
            withStorageErrorHandling("getAllTags") {
                acquireConnection("getAllTags").use { conn ->
                    conn
                        .prepareStatement(
                            """
                    SELECT DISTINCT ct.tag FROM credential_tags ct
                    JOIN credentials c ON c.id = ct.credential_id
                    WHERE c.wallet_id = ?
                    """,
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

    suspend fun findByTag(tag: String): List<VerifiableCredential> =
        withContext(Dispatchers.IO) {
            withStorageErrorHandling("findByTag") {
                acquireConnection("findByTag").use { conn ->
                    conn
                        .prepareStatement(
                            """
                    SELECT c.credential_data FROM credentials c
                    JOIN credential_tags ct ON ct.credential_id = c.id
                    WHERE ct.tag = ? AND c.wallet_id = ?
                    """,
                        ).use { stmt ->
                            stmt.setString(1, tag)
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

    suspend fun addMetadata(
        credentialId: String,
        metadata: Map<String, Any>,
    ): Boolean =
        withContext(Dispatchers.IO) {
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
                        val existing =
                            conn
                                .prepareStatement(
                                    "SELECT metadata_json FROM credential_metadata WHERE credential_id = ? FOR UPDATE",
                                ).use { stmt ->
                                    stmt.setString(1, credentialId)
                                    stmt.executeQuery().use { rs -> if (rs.next()) rs.getString("metadata_json") else null }
                                }
                        val merged = metadataCodec.toMap(existing) + metadata
                        conn
                            .prepareStatement(
                                "UPDATE credential_metadata SET metadata_json = ?, updated_at = CURRENT_TIMESTAMP WHERE credential_id = ?",
                            ).use { stmt ->
                                stmt.setString(1, metadataCodec.toJson(merged))
                                stmt.setString(2, credentialId)
                                stmt.executeUpdate()
                            }
                        conn.commit()
                        true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        runCatching { conn.rollback() }
                        throw e
                    } finally {
                        conn.autoCommit = savedAutoCommit
                    }
                }
            }
        }

    suspend fun getMetadata(credentialId: String): CredentialMetadata? =
        withContext(Dispatchers.IO) {
            withStorageErrorHandling("getMetadata") {
                acquireConnection("getMetadata").use { conn ->
                    conn
                        .prepareStatement(
                            """
                    SELECT m.notes, m.metadata_json, m.created_at, m.updated_at
                    FROM credential_metadata m
                    JOIN credentials c ON c.id = m.credential_id
                    WHERE m.credential_id = ? AND c.wallet_id = ?
                    """,
                        ).use { stmt ->
                            stmt.setString(1, credentialId)
                            stmt.setString(2, walletId)
                            stmt.executeQuery().use { rs ->
                                if (rs.next()) {
                                    CredentialMetadata(
                                        credentialId = credentialId,
                                        notes = rs.getString("notes"),
                                        tags = readTags(conn, credentialId),
                                        metadata = metadataCodec.toMap(rs.getString("metadata_json")),
                                        createdAt = rs.instantOrNow("created_at"),
                                        updatedAt = rs.instantOrNow("updated_at"),
                                    )
                                } else {
                                    null
                                }
                            }
                        }
                }
            }
        }

    suspend fun updateNotes(
        credentialId: String,
        notes: String?,
    ): Boolean =
        withContext(Dispatchers.IO) {
            withStorageErrorHandling("updateNotes") {
                acquireConnection("updateNotes").use { conn ->
                    if (!credentialExists(conn, credentialId)) {
                        return@use false
                    }
                    upsertMetadataRow(conn, credentialId)
                    conn
                        .prepareStatement(
                            "UPDATE credential_metadata SET notes = ?, updated_at = CURRENT_TIMESTAMP WHERE credential_id = ?",
                        ).use { stmt ->
                            stmt.setString(1, notes)
                            stmt.setString(2, credentialId)
                            stmt.executeUpdate()
                        }
                    true
                }
            }
        }
}
