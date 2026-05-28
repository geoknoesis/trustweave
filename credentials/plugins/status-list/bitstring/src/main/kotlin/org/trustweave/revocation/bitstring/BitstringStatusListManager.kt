package org.trustweave.revocation.bitstring

import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.revocation.RevocationStatus
import org.trustweave.credential.revocation.StatusListMetadata
import org.trustweave.credential.revocation.StatusListStatistics
import org.trustweave.credential.revocation.StatusUpdate
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.identifiers.Iri
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.SignResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.sql.Connection
import java.sql.Timestamp
import java.util.Base64
import java.util.BitSet
import java.util.UUID
import java.util.zip.GZIPOutputStream
import javax.sql.DataSource

/**
 * W3C Bitstring Status List v1.0 implementation of [CredentialRevocationManager].
 *
 * Encodes credential status as a GZIP-compressed, base64url-encoded bitstring per the
 * W3C Bitstring Status List specification (https://www.w3.org/TR/vc-bitstring-status-list/).
 *
 * **Encoding:**
 * - Allocates a [BitSet] of `size` bits (default 131072).
 * - GZIP-compresses the raw byte array.
 * - base64url-encodes (no padding) the gzipped bytes → `encodedList`.
 *
 * **Multi-purpose (2 bits per entry):**
 * When `bitsPerEntry = 2`, each credential occupies two adjacent bit positions:
 * - even index = revocation bit, odd index = suspension bit.
 *
 * **Storage:** HikariCP [DataSource] backed by three tables initialised on construction:
 * `bitstring_status_lists`, `bitstring_credential_indices`, `bitstring_next_index`.
 *
 * **Signing:** The status list VC is signed via [KeyManagementService]; the first
 * Ed25519 key found in the issuer DID document is used (generation is delegated to
 * the provided [kms] instance).
 */
class BitstringStatusListManager(
    private val dataSource: DataSource,
    private val kms: KeyManagementService,
    private val issuerDid: String,
    private val bitsPerEntry: Int = 1
) : CredentialRevocationManager {

    private val logger = LoggerFactory.getLogger(BitstringStatusListManager::class.java)

    init {
        require(bitsPerEntry == 1 || bitsPerEntry == 2) {
            "bitsPerEntry must be 1 or 2, got $bitsPerEntry"
        }
        initializeSchema()
    }

    // -------------------------------------------------------------------------
    // Schema initialisation
    // -------------------------------------------------------------------------

    private fun initializeSchema() {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(
                    """
                    CREATE TABLE IF NOT EXISTS bitstring_status_lists (
                        id VARCHAR(255) PRIMARY KEY,
                        issuer_did VARCHAR(255) NOT NULL,
                        purpose VARCHAR(50) NOT NULL,
                        size INT NOT NULL,
                        bits_per_entry INT NOT NULL DEFAULT 1,
                        encoded_list TEXT NOT NULL,
                        status_list_vc TEXT,
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL
                    )
                    """.trimIndent()
                ).execute()

                conn.prepareStatement(
                    """
                    CREATE TABLE IF NOT EXISTS bitstring_credential_indices (
                        credential_id VARCHAR(255) NOT NULL,
                        status_list_id VARCHAR(255) NOT NULL REFERENCES bitstring_status_lists(id),
                        entry_index INT NOT NULL,
                        PRIMARY KEY (credential_id, status_list_id)
                    )
                    """.trimIndent()
                ).execute()

                conn.prepareStatement(
                    """
                    CREATE TABLE IF NOT EXISTS bitstring_next_index (
                        status_list_id VARCHAR(255) PRIMARY KEY REFERENCES bitstring_status_lists(id),
                        next_index INT NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                ).execute()

                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw TrustWeaveException.InvalidState(
                    message = "Failed to initialise bitstring status list schema: ${e.message}",
                    cause = e
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // CredentialRevocationManager
    // -------------------------------------------------------------------------

    override suspend fun createStatusList(
        issuerDid: String,
        purpose: StatusPurpose,
        size: Int,
        customId: String?
    ): StatusListId = withContext(Dispatchers.IO) {
        val id = customId ?: UUID.randomUUID().toString()
        val bitSet = BitSet(size)
        val encodedList = encodeBitSet(bitSet, size)
        val now = Clock.System.now()

        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(
                    """
                    INSERT INTO bitstring_status_lists
                        (id, issuer_did, purpose, size, bits_per_entry, encoded_list, status_list_vc, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?)
                    """.trimIndent()
                ).apply {
                    setString(1, id)
                    setString(2, issuerDid)
                    setString(3, purpose.name)
                    setInt(4, size)
                    setInt(5, bitsPerEntry)
                    setString(6, encodedList)
                    setTimestamp(7, Timestamp(now.toEpochMilliseconds()))
                    setTimestamp(8, Timestamp(now.toEpochMilliseconds()))
                }.executeUpdate()

                conn.prepareStatement(
                    "INSERT INTO bitstring_next_index (status_list_id, next_index) VALUES (?, 0)"
                ).apply {
                    setString(1, id)
                }.executeUpdate()

                conn.commit()
            } catch (e: CancellationException) {
                conn.rollback()
                throw e
            } catch (e: Exception) {
                conn.rollback()
                throw TrustWeaveException.InvalidState(
                    message = "Failed to create bitstring status list: ${e.message}",
                    cause = e
                )
            }
        }

        StatusListId(id)
    }

    override suspend fun revokeCredential(
        credentialId: String,
        statusListId: StatusListId
    ): Boolean = withContext(Dispatchers.IO) {
        updateCredentialStatus(credentialId, statusListId.toString(), revoked = true, suspended = null)
    }

    override suspend fun suspendCredential(
        credentialId: String,
        statusListId: StatusListId
    ): Boolean = withContext(Dispatchers.IO) {
        updateCredentialStatus(credentialId, statusListId.toString(), revoked = null, suspended = true)
    }

    override suspend fun unrevokeCredential(
        credentialId: String,
        statusListId: StatusListId
    ): Boolean = withContext(Dispatchers.IO) {
        updateCredentialStatus(credentialId, statusListId.toString(), revoked = false, suspended = null)
    }

    override suspend fun unsuspendCredential(
        credentialId: String,
        statusListId: StatusListId
    ): Boolean = withContext(Dispatchers.IO) {
        updateCredentialStatus(credentialId, statusListId.toString(), revoked = null, suspended = false)
    }

    override suspend fun checkRevocationStatus(
        credential: VerifiableCredential
    ): RevocationStatus = withContext(Dispatchers.IO) {
        val credentialStatus = credential.credentialStatus
            ?: return@withContext RevocationStatus(revoked = false, suspended = false)

        val statusListId = credentialStatus.statusListCredential ?: credentialStatus.id
        val index = credentialStatus.statusListIndex?.toIntOrNull()
            ?: getCredentialIndex(
                credential.id?.toString()
                    ?: return@withContext RevocationStatus(
                        revoked = false,
                        suspended = false,
                        statusListId = statusListId
                    ),
                statusListId
            )
            ?: return@withContext RevocationStatus(
                revoked = false,
                suspended = false,
                statusListId = statusListId
            )

        checkStatusByIndex(statusListId, index)
    }

    override suspend fun checkStatusByIndex(
        statusListId: StatusListId,
        index: Int
    ): RevocationStatus = withContext(Dispatchers.IO) {
        val row = loadStatusListRow(statusListId.toString())
            ?: return@withContext RevocationStatus(
                revoked = false,
                suspended = false,
                statusListId = statusListId
            )

        val bitSet = decodeBitSet(row.encodedList)
        val purpose = parsePurpose(row.purpose)

        val (revoked, suspended) = when {
            bitsPerEntry == 2 -> {
                val revBit = index * 2
                val susBit = index * 2 + 1
                Pair(bitSet.get(revBit), bitSet.get(susBit))
            }
            purpose == StatusPurpose.REVOCATION -> Pair(bitSet.get(index), false)
            else -> Pair(false, bitSet.get(index))
        }

        RevocationStatus(
            revoked = revoked,
            suspended = suspended,
            statusListId = statusListId,
            index = index
        )
    }

    override suspend fun checkStatusByCredentialId(
        credentialId: String,
        statusListId: StatusListId
    ): RevocationStatus = withContext(Dispatchers.IO) {
        val index = getCredentialIndex(credentialId, statusListId)
            ?: return@withContext RevocationStatus(
                revoked = false,
                suspended = false,
                statusListId = statusListId
            )
        checkStatusByIndex(statusListId, index)
    }

    override suspend fun getCredentialIndex(
        credentialId: String,
        statusListId: StatusListId
    ): Int? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT entry_index FROM bitstring_credential_indices WHERE credential_id = ? AND status_list_id = ?"
            ).apply {
                setString(1, credentialId)
                setString(2, statusListId.toString())
            }.executeQuery().let { rs ->
                if (rs.next()) rs.getInt("entry_index") else null
            }
        }
    }

    override suspend fun assignCredentialIndex(
        credentialId: String,
        statusListId: StatusListId,
        index: Int?
    ): Int = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val sid = statusListId.toString()
                // Idempotent: return existing index if already assigned
                val existing = conn.prepareStatement(
                    "SELECT entry_index FROM bitstring_credential_indices WHERE credential_id = ? AND status_list_id = ?"
                ).apply {
                    setString(1, credentialId)
                    setString(2, sid)
                }.executeQuery()
                if (existing.next()) {
                    conn.rollback()
                    return@withContext existing.getInt("entry_index")
                }

                val assignedIndex = if (index != null) {
                    val count = conn.prepareStatement(
                        "SELECT COUNT(*) AS cnt FROM bitstring_credential_indices WHERE status_list_id = ? AND entry_index = ?"
                    ).apply {
                        setString(1, sid)
                        setInt(2, index)
                    }.executeQuery().let { rs -> rs.next(); rs.getInt("cnt") }
                    if (count > 0) {
                        throw IllegalArgumentException("Index $index is already assigned in status list $statusListId")
                    }
                    index
                } else {
                    getNextAvailableIndex(sid, conn)
                }

                conn.prepareStatement(
                    "INSERT INTO bitstring_credential_indices (credential_id, status_list_id, entry_index) VALUES (?, ?, ?)"
                ).apply {
                    setString(1, credentialId)
                    setString(2, sid)
                    setInt(3, assignedIndex)
                }.executeUpdate()

                conn.commit()
                assignedIndex
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }

    override suspend fun revokeCredentials(
        credentialIds: List<String>,
        statusListId: StatusListId
    ): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val row = loadStatusListRow(statusListId.toString())
            ?: return@withContext credentialIds.associateWith { false }

        if (parsePurpose(row.purpose) != StatusPurpose.REVOCATION) {
            return@withContext credentialIds.associateWith { false }
        }

        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val bitSet = decodeBitSet(row.encodedList)
                val results = mutableMapOf<String, Boolean>()

                for (credentialId in credentialIds) {
                    val entryIndex = getOrAssignIndex(credentialId, statusListId.toString(), conn)
                    val bitIndex = if (bitsPerEntry == 2) entryIndex * 2 else entryIndex
                    bitSet.set(bitIndex, true)
                    results[credentialId] = true
                }

                val newEncodedList = encodeBitSet(bitSet, bitSet.size())
                persistEncodedList(statusListId.toString(), newEncodedList, conn)

                conn.commit()
                results
            } catch (e: CancellationException) {
                conn.rollback()
                throw e
            } catch (e: Exception) {
                conn.rollback()
                logger.error(
                    "Failed to revoke credentials in bitstring status list {}: {}",
                    statusListId,
                    e.message,
                    e
                )
                throw TrustWeaveException.InvalidState(
                    message = "Failed to revoke credentials in bitstring status list $statusListId: ${e.message}",
                    cause = e
                )
            }
        }
    }

    override suspend fun updateStatusListBatch(
        statusListId: StatusListId,
        updates: List<StatusUpdate>
    ) = withContext(Dispatchers.IO) {
        val row = loadStatusListRow(statusListId.toString())
            ?: throw IllegalArgumentException("Status list not found: $statusListId")

        val purpose = parsePurpose(row.purpose)
        val bitSet = decodeBitSet(row.encodedList)

        for (update in updates) {
            if (bitsPerEntry == 2) {
                update.revoked?.let { bitSet.set(update.index * 2, it) }
                update.suspended?.let { bitSet.set(update.index * 2 + 1, it) }
            } else {
                when {
                    update.revoked != null && purpose == StatusPurpose.REVOCATION ->
                        bitSet.set(update.index, update.revoked!!)
                    update.suspended != null && purpose == StatusPurpose.SUSPENSION ->
                        bitSet.set(update.index, update.suspended!!)
                }
            }
        }

        val newEncodedList = encodeBitSet(bitSet, bitSet.size())
        dataSource.connection.use { conn ->
            persistEncodedList(statusListId.toString(), newEncodedList, conn)
        }
    }

    override suspend fun getStatusListStatistics(
        statusListId: StatusListId
    ): StatusListStatistics? = withContext(Dispatchers.IO) {
        val row = loadStatusListRow(statusListId.toString()) ?: return@withContext null

        val usedIndices = dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT COUNT(*) AS cnt FROM bitstring_credential_indices WHERE status_list_id = ?"
            ).apply {
                setString(1, statusListId.toString())
            }.executeQuery().let { rs -> rs.next(); rs.getInt("cnt") }
        }

        val bitSet = decodeBitSet(row.encodedList)
        val purpose = parsePurpose(row.purpose)

        StatusListStatistics(
            statusListId = statusListId,
            issuerDid = row.issuerDid,
            purpose = purpose,
            totalCapacity = if (bitsPerEntry == 2) bitSet.size() / 2 else bitSet.size(),
            usedIndices = usedIndices,
            revokedCount = if (purpose == StatusPurpose.REVOCATION || bitsPerEntry == 2) {
                countBits(bitSet, bitsPerEntry, revocationBit = true)
            } else {
                0
            },
            suspendedCount = if (purpose == StatusPurpose.SUSPENSION || bitsPerEntry == 2) {
                countBits(bitSet, bitsPerEntry, revocationBit = false)
            } else {
                0
            },
            availableIndices = (if (bitsPerEntry == 2) bitSet.size() / 2 else bitSet.size()) - usedIndices,
            lastUpdated = row.updatedAt
        )
    }

    override suspend fun getStatusList(statusListId: StatusListId): StatusListMetadata? = withContext(Dispatchers.IO) {
        val row = loadStatusListRow(statusListId.toString()) ?: return@withContext null
        StatusListMetadata(
            id = statusListId,
            issuerDid = row.issuerDid,
            purpose = parsePurpose(row.purpose),
            size = row.size,
            createdAt = row.createdAt,
            lastUpdated = row.updatedAt
        )
    }

    override suspend fun listStatusLists(issuerDid: String?): List<StatusListMetadata> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val sql = if (issuerDid != null) {
                "SELECT id, issuer_did, purpose, size, created_at, updated_at FROM bitstring_status_lists WHERE issuer_did = ?"
            } else {
                "SELECT id, issuer_did, purpose, size, created_at, updated_at FROM bitstring_status_lists"
            }
            val stmt = conn.prepareStatement(sql)
            if (issuerDid != null) stmt.setString(1, issuerDid)

            val rs = stmt.executeQuery()
            val results = mutableListOf<StatusListMetadata>()
            while (rs.next()) {
                val createdAt = rs.getTimestamp("created_at")?.toKotlinInstant() ?: Clock.System.now()
                val updatedAt = rs.getTimestamp("updated_at")?.toKotlinInstant() ?: Clock.System.now()
                results.add(
                    StatusListMetadata(
                        id = StatusListId(rs.getString("id")),
                        issuerDid = rs.getString("issuer_did"),
                        purpose = parsePurpose(rs.getString("purpose")),
                        size = rs.getInt("size"),
                        createdAt = createdAt,
                        lastUpdated = updatedAt
                    )
                )
            }
            results
        }
    }

    override suspend fun deleteStatusList(statusListId: StatusListId): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val id = statusListId.toString()
                conn.prepareStatement(
                    "DELETE FROM bitstring_credential_indices WHERE status_list_id = ?"
                ).apply { setString(1, id) }.executeUpdate()
                conn.prepareStatement(
                    "DELETE FROM bitstring_next_index WHERE status_list_id = ?"
                ).apply { setString(1, id) }.executeUpdate()
                val deleted = conn.prepareStatement(
                    "DELETE FROM bitstring_status_lists WHERE id = ?"
                ).apply { setString(1, id) }.executeUpdate() > 0
                conn.commit()
                deleted
            } catch (e: Exception) {
                conn.rollback()
                false
            }
        }
    }

    override suspend fun expandStatusList(
        statusListId: StatusListId,
        additionalSize: Int
    ) = withContext(Dispatchers.IO) {
        val row = loadStatusListRow(statusListId.toString())
            ?: throw IllegalArgumentException("Status list not found: $statusListId")

        val currentBitSet = decodeBitSet(row.encodedList)
        val currentSize = currentBitSet.size()
        val newSize = currentSize + additionalSize
        val newBitSet = BitSet(newSize)

        for (i in 0 until currentSize) {
            if (currentBitSet.get(i)) newBitSet.set(i, true)
        }

        val newEncodedList = encodeBitSet(newBitSet, newSize)

        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                persistEncodedList(statusListId.toString(), newEncodedList, conn)
                conn.prepareStatement(
                    "UPDATE bitstring_status_lists SET size = ? WHERE id = ?"
                ).apply {
                    setInt(1, newSize)
                    setString(2, statusListId.toString())
                }.executeUpdate()
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
        Unit
    }

    // -------------------------------------------------------------------------
    // Status list VC building and signing
    // -------------------------------------------------------------------------

    /**
     * Build a signed [VerifiableCredential] for the given status list.
     *
     * The VC type is `["VerifiableCredential", "BitstringStatusListCredential"]`.
     * The `credentialSubject` contains `type = "BitstringStatusList"`, `statusPurpose`,
     * and `encodedList` as specified by W3C Bitstring Status List v1.0.
     *
     * Signing uses the first Ed25519 key generated from [kms]; the raw signature
     * is stored as a base64url-encoded `jws` proof value.
     */
    suspend fun buildStatusListVc(statusListId: StatusListId): VerifiableCredential = withContext(Dispatchers.IO) {
        val row = loadStatusListRow(statusListId.toString())
            ?: throw IllegalArgumentException("Status list not found: $statusListId")

        val purpose = parsePurpose(row.purpose)

        val subjectClaims = buildJsonObject {
            put("type", "BitstringStatusList")
            put("statusPurpose", purpose.stringValue)
            put("encodedList", row.encodedList)
        }

        val vc = VerifiableCredential(
            context = listOf(
                "https://www.w3.org/2018/credentials/v1",
                "https://www.w3.org/ns/credentials/status/bitstring-status-list/v1"
            ),
            id = null,
            type = listOf(
                CredentialType.VerifiableCredential,
                CredentialType.Custom("BitstringStatusListCredential")
            ),
            issuer = Issuer.from(row.issuerDid),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject.fromIri(
                Iri(statusListId.toString()),
                claims = subjectClaims
            )
        )

        // Build a canonical JSON string for the signing payload using a simple JsonObject
        val vcPayload = buildJsonObject {
            put("@context", buildJsonArray {
                add("https://www.w3.org/2018/credentials/v1")
                add("https://www.w3.org/ns/credentials/status/bitstring-status-list/v1")
            })
            put("type", buildJsonArray {
                add("VerifiableCredential")
                add("BitstringStatusListCredential")
            })
            put("issuer", row.issuerDid)
            vc.issuanceDate?.let { put("issuanceDate", it.toString()) }
            put("credentialSubject", buildJsonObject {
                put("id", statusListId.toString())
                put("type", "BitstringStatusList")
                put("statusPurpose", purpose.stringValue)
                put("encodedList", row.encodedList)
            })
        }
        val vcPayloadBytes = vcPayload.toString().toByteArray(Charsets.UTF_8)

        val keyResult = kms.generateKey(Algorithm.Ed25519)
        val keyHandle = when (keyResult) {
            is GenerateKeyResult.Success -> keyResult.keyHandle
            else -> throw TrustWeaveException.InvalidState(
                message = "Failed to generate signing key for status list VC"
            )
        }

        val signResult = kms.sign(keyHandle.id, vcPayloadBytes)
        val signature = when (signResult) {
            is SignResult.Success -> signResult.signature
            else -> throw TrustWeaveException.InvalidState(
                message = "Failed to sign status list VC"
            )
        }

        val signatureB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signature)

        val signedVc = vc.copy(
            proof = org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof(
                type = "Ed25519Signature2020",
                created = Clock.System.now(),
                verificationMethod = "${row.issuerDid}#${keyHandle.id.value}",
                proofPurpose = "assertionMethod",
                proofValue = signatureB64
            )
        )

        // Persist the signed VC as the canonical JSON payload
        val signedVcJson = buildJsonObject {
            put("@context", buildJsonArray {
                add("https://www.w3.org/2018/credentials/v1")
                add("https://www.w3.org/ns/credentials/status/bitstring-status-list/v1")
            })
            put("type", buildJsonArray {
                add("VerifiableCredential")
                add("BitstringStatusListCredential")
            })
            put("issuer", row.issuerDid)
            signedVc.issuanceDate?.let { put("issuanceDate", it.toString()) }
            put("credentialSubject", buildJsonObject {
                put("id", statusListId.toString())
                put("type", "BitstringStatusList")
                put("statusPurpose", purpose.stringValue)
                put("encodedList", row.encodedList)
            })
            put("proof", buildJsonObject {
                put("type", "Ed25519Signature2020")
                put("proofPurpose", "assertionMethod")
                put("verificationMethod", "${row.issuerDid}#${keyHandle.id.value}")
                put("proofValue", signatureB64)
            })
        }.toString()

        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE bitstring_status_lists SET status_list_vc = ?, updated_at = ? WHERE id = ?"
            ).apply {
                setString(1, signedVcJson)
                setTimestamp(2, Timestamp(Clock.System.now().toEpochMilliseconds()))
                setString(3, statusListId.toString())
            }.executeUpdate()
        }

        signedVc
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun updateCredentialStatus(
        credentialId: String,
        statusListId: String,
        revoked: Boolean?,
        suspended: Boolean?
    ): Boolean {
        val row = loadStatusListRow(statusListId) ?: return false
        val purpose = parsePurpose(row.purpose)

        // Validate purpose vs operation when single-bit mode
        if (bitsPerEntry == 1) {
            if (revoked != null && purpose != StatusPurpose.REVOCATION) return false
            if (suspended != null && purpose != StatusPurpose.SUSPENSION) return false
        }

        return dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val entryIndex = getOrAssignIndex(credentialId, statusListId, conn)
                val bitSet = decodeBitSet(row.encodedList)

                if (bitsPerEntry == 2) {
                    revoked?.let { bitSet.set(entryIndex * 2, it) }
                    suspended?.let { bitSet.set(entryIndex * 2 + 1, it) }
                } else {
                    revoked?.let { bitSet.set(entryIndex, it) }
                    suspended?.let { bitSet.set(entryIndex, it) }
                }

                val newEncodedList = encodeBitSet(bitSet, bitSet.size())
                persistEncodedList(statusListId, newEncodedList, conn)
                conn.commit()
                true
            } catch (e: CancellationException) {
                conn.rollback()
                throw e
            } catch (e: Exception) {
                conn.rollback()
                logger.error(
                    "Failed to update status entry for credential {} in bitstring status list {}: {}",
                    credentialId,
                    statusListId,
                    e.message,
                    e
                )
                throw TrustWeaveException.InvalidState(
                    message = "Failed to update status entry for credential $credentialId " +
                        "in bitstring status list $statusListId: ${e.message}",
                    cause = e
                )
            }
        }
    }

    private fun getOrAssignIndex(credentialId: String, statusListId: String, conn: Connection): Int {
        val existing = conn.prepareStatement(
            "SELECT entry_index FROM bitstring_credential_indices WHERE credential_id = ? AND status_list_id = ?"
        ).apply {
            setString(1, credentialId)
            setString(2, statusListId)
        }.executeQuery()

        if (existing.next()) return existing.getInt("entry_index")

        val next = getNextAvailableIndex(statusListId, conn)
        conn.prepareStatement(
            "INSERT INTO bitstring_credential_indices (credential_id, status_list_id, entry_index) VALUES (?, ?, ?)"
        ).apply {
            setString(1, credentialId)
            setString(2, statusListId)
            setInt(3, next)
        }.executeUpdate()
        return next
    }

    private fun getNextAvailableIndex(statusListId: String, conn: Connection): Int {
        val rs = conn.prepareStatement(
            "SELECT next_index FROM bitstring_next_index WHERE status_list_id = ?"
        ).apply {
            setString(1, statusListId)
        }.executeQuery()

        val next = if (rs.next()) rs.getInt("next_index") else 0

        conn.prepareStatement(
            """
            MERGE INTO bitstring_next_index (status_list_id, next_index)
            KEY (status_list_id)
            VALUES (?, ?)
            """.trimIndent()
        ).apply {
            setString(1, statusListId)
            setInt(2, next + 1)
        }.executeUpdate()

        return next
    }

    private fun persistEncodedList(statusListId: String, encodedList: String, conn: Connection) {
        conn.prepareStatement(
            "UPDATE bitstring_status_lists SET encoded_list = ?, updated_at = ? WHERE id = ?"
        ).apply {
            setString(1, encodedList)
            setTimestamp(2, Timestamp(Clock.System.now().toEpochMilliseconds()))
            setString(3, statusListId)
        }.executeUpdate()
    }

    // -------------------------------------------------------------------------
    // Encoding helpers
    // -------------------------------------------------------------------------

    /**
     * Encode a [BitSet] of [size] bits to a GZIP-compressed, base64url (no-padding) string.
     *
     * Bit ordering follows the W3C spec: bit 0 of the first byte = entry 0.
     */
    private fun encodeBitSet(bitSet: BitSet, size: Int): String {
        val byteCount = (size + 7) / 8
        val bytes = ByteArray(byteCount)
        // TODO: cancellation gap — this loop iterates over the full status list (default 131072)
        // without checking for cooperative cancellation. As a plain non-suspend function it cannot
        // reach coroutineContext; if made suspend, add coroutineContext.ensureActive() every ~8192 iterations.
        for (i in 0 until size) {
            if (bitSet.get(i)) {
                bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (i % 8))).toByte()
            }
        }

        val gzipped = ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { gzip -> gzip.write(bytes) }
            baos.toByteArray()
        }

        return Base64.getUrlEncoder().withoutPadding().encodeToString(gzipped)
    }

    /**
     * Decode a GZIP-compressed, base64url-encoded string back to a [BitSet].
     */
    private fun decodeBitSet(encoded: String): BitSet {
        val gzipped = Base64.getUrlDecoder().decode(encoded)
        val bytes = java.util.zip.GZIPInputStream(gzipped.inputStream()).use { it.readBytes() }
        val bitSet = BitSet(bytes.size * 8)
        // TODO: cancellation gap — this nested loop iterates over the full status list
        // without checking for cooperative cancellation. As a plain non-suspend function it cannot
        // reach coroutineContext; if made suspend, add coroutineContext.ensureActive() every ~8192 iterations.
        for (i in bytes.indices) {
            for (j in 0..7) {
                if ((bytes[i].toInt() and (1 shl j)) != 0) {
                    bitSet.set(i * 8 + j)
                }
            }
        }
        return bitSet
    }

    // -------------------------------------------------------------------------
    // DB row helper
    // -------------------------------------------------------------------------

    private data class StatusListRow(
        val id: String,
        val issuerDid: String,
        val purpose: String,
        val size: Int,
        val bitsPerEntry: Int,
        val encodedList: String,
        val createdAt: Instant,
        val updatedAt: Instant
    )

    private fun loadStatusListRow(statusListId: String): StatusListRow? {
        return dataSource.connection.use { conn ->
            val rs = conn.prepareStatement(
                """
                SELECT id, issuer_did, purpose, size, bits_per_entry, encoded_list, created_at, updated_at
                FROM bitstring_status_lists WHERE id = ?
                """.trimIndent()
            ).apply {
                setString(1, statusListId)
            }.executeQuery()

            if (!rs.next()) return@use null

            StatusListRow(
                id = rs.getString("id"),
                issuerDid = rs.getString("issuer_did"),
                purpose = rs.getString("purpose"),
                size = rs.getInt("size"),
                bitsPerEntry = rs.getInt("bits_per_entry"),
                encodedList = rs.getString("encoded_list"),
                createdAt = rs.getTimestamp("created_at")?.toKotlinInstant() ?: Clock.System.now(),
                updatedAt = rs.getTimestamp("updated_at")?.toKotlinInstant() ?: Clock.System.now()
            )
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private fun parsePurpose(value: String): StatusPurpose = try {
        StatusPurpose.valueOf(value.uppercase())
    } catch (e: IllegalArgumentException) {
        logger.warn("Unknown status purpose '{}' in stored status list; defaulting to REVOCATION", value)
        StatusPurpose.REVOCATION
    }

    private fun countBits(bitSet: BitSet, bitsPerEntry: Int, revocationBit: Boolean): Int {
        if (bitsPerEntry == 1) return bitSet.cardinality()
        var count = 0
        val offset = if (revocationBit) 0 else 1
        var i = offset
        while (i < bitSet.size()) {
            if (bitSet.get(i)) count++
            i += 2
        }
        return count
    }

    private fun Timestamp.toKotlinInstant(): Instant =
        Instant.fromEpochMilliseconds(this.time)
}
