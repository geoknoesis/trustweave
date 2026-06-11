package org.trustweave.revocation.bitstring

import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.revocation.RevocationStatus
import org.trustweave.credential.revocation.StatusListMetadata
import org.trustweave.credential.revocation.StatusListStatistics
import org.trustweave.credential.revocation.StatusUpdate
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.core.exception.ConfigException
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.serialization.SerializationModule
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.kms.KeyManagementService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.sql.Connection
import java.sql.SQLException
import java.sql.SQLIntegrityConstraintViolationException
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
 * - Allocates a [BitSet] of `size` bits (minimum 131072 bits = 16KB, enforced per spec).
 * - Packs bits MSB-first: the left-most bit of each byte is the lowest index
 *   (index 0 = bit 7 of byte 0), as required by the spec.
 * - GZIP-compresses the raw byte array.
 * - base64url-encodes (no padding) the gzipped bytes and prepends the multibase
 *   prefix `u` → `encodedList`.
 *
 * Decoding accepts only spec-compliant `u`-prefixed values; the prefix doubles as the
 * format-version marker. Legacy un-prefixed values were written by pre-MSB-first
 * versions of this class using the opposite (LSB-first) bit order and are REJECTED
 * fail-closed (`STATUS_LIST_LEGACY_FORMAT`) — they must be regenerated/migrated,
 * never reinterpreted.
 *
 * **Multi-purpose (2 bits per entry):**
 * When `bitsPerEntry = 2`, each credential occupies two adjacent bit positions:
 * - even index = revocation bit, odd index = suspension bit.
 *
 * **Storage:** HikariCP [DataSource] backed by three tables initialised on construction:
 * `bitstring_status_lists`, `bitstring_credential_indices`, `bitstring_next_index`.
 *
 * **Signing:** The status list VC is signed via the configured [proofEngine] using the
 * issuer's real [issuerKeyId] (a verification method published in the issuer's DID
 * document). If no engine/key is configured, [buildStatusListVc] fails with a
 * [ConfigException] — keys are never fabricated.
 *
 * **Fail-closed semantics:** Status lookups against an unknown status list, or with an
 * out-of-range index, throw instead of reporting "not revoked".
 *
 * @param dataSource JDBC [DataSource]
 * @param kms Retained for source compatibility; no longer used for signing
 * @param issuerDid DID of the issuer used in status list VCs
 * @param bitsPerEntry 1 for single-purpose lists, 2 for combined revocation + suspension
 * @param proofEngine Proof engine (wired to the issuer's KMS) used to sign status list VCs
 * @param issuerKeyId Issuer verification method used as the signing key for status list VCs
 */
class BitstringStatusListManager(
    private val dataSource: DataSource,
    @Suppress("unused") private val kms: KeyManagementService,
    private val issuerDid: String,
    private val bitsPerEntry: Int = 1,
    private val proofEngine: ProofEngine? = null,
    private val issuerKeyId: VerificationMethodId? = null
) : CredentialRevocationManager {

    companion object {
        /**
         * Spec minimum: the uncompressed bitstring MUST be at least 16KB (131,072 bits).
         */
        const val MIN_STATUS_LIST_SIZE_BITS: Int = 131_072

        /** Multibase prefix for base64url-no-pad, required on `encodedList` by the spec. */
        private const val MULTIBASE_BASE64URL_NO_PAD_PREFIX: String = "u"

        /**
         * Maximum attempts to claim a free index when concurrent writers race on the
         * `UNIQUE (status_list_id, entry_index)` constraint.
         */
        private const val MAX_INDEX_ASSIGNMENT_RETRIES: Int = 5

        private val vcJson = Json {
            serializersModule = SerializationModule.default
            ignoreUnknownKeys = true
        }
    }

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

                // Each bit position may be bound to at most ONE credential: two concurrent
                // assignments racing to the same index must not both succeed (one retries).
                conn.prepareStatement(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS ux_bitstring_indices_list_entry
                    ON bitstring_credential_indices (status_list_id, entry_index)
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
        val effectiveSize = if (size < MIN_STATUS_LIST_SIZE_BITS) {
            logger.warn(
                "Requested status list size {} is below the W3C Bitstring Status List minimum of {} bits; " +
                    "using the minimum instead",
                size,
                MIN_STATUS_LIST_SIZE_BITS
            )
            MIN_STATUS_LIST_SIZE_BITS
        } else {
            size
        }
        val bitSet = BitSet(effectiveSize)
        val encodedList = encodeBitSet(bitSet, effectiveSize)
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
                    setInt(4, effectiveSize)
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

        // Fail closed: once a credential declares a status entry, an undeterminable
        // index or an unknown status list must surface as an error, never as "valid".
        val statusListId = credentialStatus.statusListCredential ?: credentialStatus.id
        val index = credentialStatus.statusListIndex?.toIntOrNull()
            ?: credential.id?.toString()?.let { getCredentialIndex(it, statusListId) }
            ?: throw TrustWeaveException.InvalidState(
                code = "STATUS_LIST_INDEX_UNKNOWN",
                message = "Credential declares a status entry in status list $statusListId " +
                    "but its status list index cannot be determined; " +
                    "failing closed instead of reporting a valid status",
                context = mapOf(
                    "statusListId" to statusListId.toString(),
                    "credentialId" to credential.id?.toString()
                )
            )

        checkStatusByIndex(statusListId, index)
    }

    override suspend fun checkStatusByIndex(
        statusListId: StatusListId,
        index: Int
    ): RevocationStatus = withContext(Dispatchers.IO) {
        val row = loadStatusListRow(statusListId.toString())
            ?: throw statusListUnavailable(statusListId)

        requireIndexInRange(index, row, statusListId)

        val bitSet = decodeBitSet(row.encodedList)
        val purpose = parsePurpose(row.purpose)

        val (revoked, suspended) = when {
            row.bitsPerEntry == 2 -> {
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
        if (loadStatusListRow(statusListId.toString()) == null) {
            throw statusListUnavailable(statusListId)
        }
        // Fail closed: without an index assignment the status cannot be determined.
        val index = getCredentialIndex(credentialId, statusListId)
            ?: throw TrustWeaveException.NotFound(
                resource = "status list index for credential '$credentialId' " +
                    "in status list '$statusListId'"
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
        val row = loadStatusListRow(statusListId.toString())
            ?: throw IllegalArgumentException("Status list not found: $statusListId")
        index?.let { requireIndexInRange(it, row, statusListId) }

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
                    conn.prepareStatement(
                        "INSERT INTO bitstring_credential_indices (credential_id, status_list_id, entry_index) VALUES (?, ?, ?)"
                    ).apply {
                        setString(1, credentialId)
                        setString(2, sid)
                        setInt(3, index)
                    }.executeUpdate()
                    index
                } else {
                    insertNextAvailableIndex(credentialId, sid, row, conn)
                }

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
                // Lock the status list row and re-read encoded_list AND size INSIDE this
                // transaction: decoding a pre-transaction snapshot would let a concurrent
                // update be silently overwritten (lost update), and re-encoding at a stale
                // size would truncate a concurrently expanded tail region.
                val locked = lockAndReadStatusList(statusListId.toString(), conn)
                val lockedRow = row.copy(size = locked.size, encodedList = locked.encodedList)
                val bitSet = decodeBitSet(locked.encodedList)
                val results = mutableMapOf<String, Boolean>()

                for (credentialId in credentialIds) {
                    val entryIndex = getOrAssignIndex(credentialId, statusListId.toString(), lockedRow, conn)
                    requireIndexInRange(entryIndex, lockedRow, statusListId)
                    val bitIndex = if (lockedRow.bitsPerEntry == 2) entryIndex * 2 else entryIndex
                    bitSet.set(bitIndex, true)
                    results[credentialId] = true
                }

                val newEncodedList = encodeBitSet(bitSet, locked.size)
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

    /**
     * Apply a batch of status updates in a single transaction.
     *
     * Concurrency: the status list row is locked and both `encoded_list` and `size`
     * are re-read INSIDE the write transaction via [lockAndReadStatusList], so
     * concurrent writers serialize on the row instead of overwriting each other's
     * bits from a stale pre-transaction snapshot (lost update), and the re-encode
     * uses the locked size so a concurrent [expandStatusList] is never truncated
     * back to the stale pre-transaction size.
     *
     * Index validation deliberately uses the pre-transaction size: indices valid
     * against the old size remain valid under the lock (size only grows), and
     * validating before the transaction lets RANGE_ERROR surface as-is without
     * writing anything for a partially invalid batch.
     */
    override suspend fun updateStatusListBatch(
        statusListId: StatusListId,
        updates: List<StatusUpdate>
    ) = withContext(Dispatchers.IO) {
        val row = loadStatusListRow(statusListId.toString())
            ?: throw IllegalArgumentException("Status list not found: $statusListId")

        val purpose = parsePurpose(row.purpose)
        // Validate every index before opening the transaction so RANGE_ERROR surfaces
        // as-is and nothing is written for a partially invalid batch.
        for (update in updates) {
            requireIndexInRange(update.index, row, statusListId)
        }

        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                // Lock the status list row and re-read encoded_list AND size INSIDE this
                // transaction: decoding a pre-transaction snapshot would let a concurrent
                // update be silently overwritten (lost update), and re-encoding at a stale
                // size would truncate a concurrently expanded tail region.
                val locked = lockAndReadStatusList(statusListId.toString(), conn)
                val bitSet = decodeBitSet(locked.encodedList)

                for (update in updates) {
                    if (row.bitsPerEntry == 2) {
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

                val newEncodedList = encodeBitSet(bitSet, locked.size)
                persistEncodedList(statusListId.toString(), newEncodedList, conn)
                conn.commit()
            } catch (e: CancellationException) {
                conn.rollback()
                throw e
            } catch (e: Exception) {
                conn.rollback()
                logger.error(
                    "Failed to apply batch status updates to bitstring status list {}: {}",
                    statusListId,
                    e.message,
                    e
                )
                throw TrustWeaveException.InvalidState(
                    message = "Failed to apply batch status updates to bitstring status list " +
                        "$statusListId: ${e.message}",
                    cause = e
                )
            }
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
            totalCapacity = if (row.bitsPerEntry == 2) bitSet.size() / 2 else bitSet.size(),
            usedIndices = usedIndices,
            revokedCount = if (purpose == StatusPurpose.REVOCATION || row.bitsPerEntry == 2) {
                countBits(bitSet, row.bitsPerEntry, revocationBit = true)
            } else {
                0
            },
            suspendedCount = if (purpose == StatusPurpose.SUSPENSION || row.bitsPerEntry == 2) {
                countBits(bitSet, row.bitsPerEntry, revocationBit = false)
            } else {
                0
            },
            availableIndices = (if (row.bitsPerEntry == 2) bitSet.size() / 2 else bitSet.size()) - usedIndices,
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

    /**
     * Grow the status list by [additionalSize] bits, preserving all existing bits.
     *
     * Concurrency: the status list row is locked and both `encoded_list` and `size`
     * are re-read INSIDE the write transaction via [lockAndReadStatusList], so a
     * concurrent status update or expansion committed after the initial existence
     * check is never overwritten from a stale snapshot (lost update / size rollback).
     *
     * The expanded list is re-encoded through [encodeBitSet], so the multibase `u`
     * prefix and MSB-first bit ordering of `encodedList` are preserved.
     */
    override suspend fun expandStatusList(
        statusListId: StatusListId,
        additionalSize: Int
    ) = withContext(Dispatchers.IO) {
        if (loadStatusListRow(statusListId.toString()) == null) {
            throw IllegalArgumentException("Status list not found: $statusListId")
        }

        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                // Lock the status list row and re-read encoded_list AND size INSIDE this
                // transaction: expanding a pre-transaction snapshot would silently discard
                // bits set by a concurrent writer (lost update), and a stale size would
                // undo a concurrent expansion committed after the existence check above.
                val locked = lockAndReadStatusList(statusListId.toString(), conn)
                val currentBitSet = decodeBitSet(locked.encodedList)
                val currentSize = locked.size

                val newSize = currentSize + additionalSize
                val newBitSet = BitSet(newSize)
                for (i in 0 until currentSize) {
                    if (currentBitSet.get(i)) newBitSet.set(i, true)
                }

                val newEncodedList = encodeBitSet(newBitSet, newSize)
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
     * Signing is delegated to the configured [proofEngine] using the issuer's real
     * [issuerKeyId] (a verification method published in the issuer's DID document),
     * so the resulting credential is verifiable by third parties. Keys are never
     * fabricated for signing.
     *
     * @throws ConfigException if no [proofEngine]/[issuerKeyId] is configured, or the
     *   configured key does not belong to the status list's issuer DID
     * @throws IllegalArgumentException if the status list does not exist
     */
    suspend fun buildStatusListVc(statusListId: StatusListId): VerifiableCredential = withContext(Dispatchers.IO) {
        val engine = proofEngine ?: throw ConfigException.InvalidFormat(
            parseError = "No proof engine configured for signing status list credentials. " +
                "Provide a ProofEngine (wired to the issuer's KMS) when constructing " +
                "BitstringStatusListManager; status list credentials are never signed " +
                "with fabricated keys.",
            field = "proofEngine"
        )
        val signingKeyId = issuerKeyId ?: throw ConfigException.InvalidFormat(
            parseError = "No issuer signing key configured for status list credentials. " +
                "Provide the issuer's verification method ID (issuerKeyId) when " +
                "constructing BitstringStatusListManager.",
            field = "issuerKeyId"
        )

        val row = loadStatusListRow(statusListId.toString())
            ?: throw IllegalArgumentException("Status list not found: $statusListId")

        if (signingKeyId.did.value != row.issuerDid) {
            throw ConfigException.InvalidFormat(
                parseError = "Configured issuerKeyId belongs to '${signingKeyId.did.value}' " +
                    "but the status list is issued by '${row.issuerDid}'; refusing to sign.",
                field = "issuerKeyId"
            )
        }

        val purpose = parsePurpose(row.purpose)

        val subjectClaims = buildJsonObject {
            put("type", "BitstringStatusList")
            put("statusPurpose", purpose.stringValue)
            put("encodedList", row.encodedList)
        }

        val request = IssuanceRequest(
            format = engine.format,
            issuer = Issuer.from(row.issuerDid),
            issuerKeyId = signingKeyId,
            credentialSubject = CredentialSubject.fromIri(
                Iri(statusListId.toString()),
                claims = subjectClaims
            ),
            type = listOf(
                CredentialType.VerifiableCredential,
                CredentialType.Custom("BitstringStatusListCredential")
            )
        )

        val signedVc = engine.issue(request)
        // Post-check: never trust the engine to have honored the request — the published
        // status list VC must be issued by the status list's issuer DID.
        if (signedVc.issuer.id.value != row.issuerDid) {
            throw TrustWeaveException.InvalidState(
                code = "STATUS_LIST_VC_ISSUER_MISMATCH",
                message = "Proof engine returned a status list credential issued by " +
                    "'${signedVc.issuer.id.value}' but status list $statusListId is issued by " +
                    "'${row.issuerDid}'; refusing to publish"
            )
        }
        if (signedVc.proof == null) {
            throw TrustWeaveException.InvalidState(
                code = "STATUS_LIST_VC_UNSIGNED",
                message = "Proof engine returned an unsigned status list credential " +
                    "for $statusListId; refusing to publish"
            )
        }

        val signedVcJson = vcJson.encodeToString(VerifiableCredential.serializer(), signedVc)

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

        // Validate purpose vs operation when single-bit mode.
        // Always use the list's persisted bits_per_entry, not this manager's constructor
        // value — a manager configured differently must not misinterpret an existing list.
        if (row.bitsPerEntry == 1) {
            if (revoked != null && purpose != StatusPurpose.REVOCATION) return false
            if (suspended != null && purpose != StatusPurpose.SUSPENSION) return false
        }

        return dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                // Lock the status list row and re-read encoded_list AND size INSIDE this
                // transaction: decoding a pre-transaction snapshot would let a concurrent
                // update be silently overwritten (lost update), and re-encoding at a stale
                // size would truncate a concurrently expanded tail region.
                val locked = lockAndReadStatusList(statusListId, conn)
                val lockedRow = row.copy(size = locked.size, encodedList = locked.encodedList)
                val entryIndex = getOrAssignIndex(credentialId, statusListId, lockedRow, conn)
                requireIndexInRange(entryIndex, lockedRow, StatusListId(statusListId))
                val bitSet = decodeBitSet(locked.encodedList)

                if (row.bitsPerEntry == 2) {
                    revoked?.let { bitSet.set(entryIndex * 2, it) }
                    suspended?.let { bitSet.set(entryIndex * 2 + 1, it) }
                } else {
                    revoked?.let { bitSet.set(entryIndex, it) }
                    suspended?.let { bitSet.set(entryIndex, it) }
                }

                val newEncodedList = encodeBitSet(bitSet, locked.size)
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

    private fun getOrAssignIndex(
        credentialId: String,
        statusListId: String,
        row: StatusListRow,
        conn: Connection
    ): Int {
        val existing = conn.prepareStatement(
            "SELECT entry_index FROM bitstring_credential_indices WHERE credential_id = ? AND status_list_id = ?"
        ).apply {
            setString(1, credentialId)
            setString(2, statusListId)
        }.executeQuery()

        if (existing.next()) return existing.getInt("entry_index")

        return insertNextAvailableIndex(credentialId, statusListId, row, conn)
    }

    /**
     * Claim the next free index for [credentialId] and insert the assignment row.
     *
     * Concurrency: the `UNIQUE (status_list_id, entry_index)` constraint guarantees a bit
     * position can never be bound to two credentials. If a concurrent writer claimed the
     * candidate index first, the insert fails with a unique violation; we roll back to a
     * savepoint (keeping the counter advance) and retry with the next index, bounded by
     * [MAX_INDEX_ASSIGNMENT_RETRIES]. Must be called inside an open transaction.
     */
    private fun insertNextAvailableIndex(
        credentialId: String,
        statusListId: String,
        row: StatusListRow,
        conn: Connection
    ): Int {
        repeat(MAX_INDEX_ASSIGNMENT_RETRIES) {
            val next = getNextAvailableIndex(statusListId, conn)
            requireIndexInRange(next, row, StatusListId(statusListId))
            val savepoint = conn.setSavepoint()
            try {
                conn.prepareStatement(
                    "INSERT INTO bitstring_credential_indices (credential_id, status_list_id, entry_index) VALUES (?, ?, ?)"
                ).apply {
                    setString(1, credentialId)
                    setString(2, statusListId)
                    setInt(3, next)
                }.executeUpdate()
                return next
            } catch (e: SQLException) {
                if (!isUniqueViolation(e)) throw e
                // A concurrent writer bound this index first; discard the failed insert
                // (but keep the counter advance) and retry with the next index.
                conn.rollback(savepoint)
            }
        }
        throw TrustWeaveException.InvalidState(
            code = "STATUS_LIST_INDEX_CONTENTION",
            message = "Could not assign a status list index for credential '$credentialId' " +
                "in status list '$statusListId' after $MAX_INDEX_ASSIGNMENT_RETRIES attempts " +
                "due to concurrent index assignments",
            context = mapOf(
                "statusListId" to statusListId,
                "credentialId" to credentialId
            )
        )
    }

    private fun isUniqueViolation(e: SQLException): Boolean =
        e is SQLIntegrityConstraintViolationException || e.sqlState?.startsWith("23") == true

    private fun getNextAvailableIndex(statusListId: String, conn: Connection): Int {
        // FOR UPDATE serializes concurrent assigners on the per-list counter row.
        val rs = conn.prepareStatement(
            "SELECT next_index FROM bitstring_next_index WHERE status_list_id = ? FOR UPDATE"
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

    /**
     * The authoritative `(encoded_list, size)` pair of a status list row, read under
     * a `SELECT ... FOR UPDATE` row lock inside an open write transaction.
     */
    private data class LockedStatusListState(
        val encodedList: String,
        val size: Int
    )

    /**
     * Re-read `encoded_list` AND `size` with a row lock inside the caller's open
     * transaction, so concurrent writers serialize on the status list row instead of
     * working from a stale snapshot read before the transaction began. Re-reading both
     * columns together prevents two distinct lost-update hazards:
     *
     * - stale `encoded_list`: a concurrent status update would be silently overwritten;
     * - stale `size`: re-encoding at a pre-transaction size after a concurrent
     *   [expandStatusList] would truncate the expanded tail region (and any bits a
     *   third party set there), shrinking `encoded_list` below the committed `size`.
     */
    private fun lockAndReadStatusList(statusListId: String, conn: Connection): LockedStatusListState {
        val rs = conn.prepareStatement(
            "SELECT encoded_list, size FROM bitstring_status_lists WHERE id = ? FOR UPDATE"
        ).apply {
            setString(1, statusListId)
        }.executeQuery()
        if (!rs.next()) throw statusListUnavailable(StatusListId(statusListId))
        return LockedStatusListState(
            encodedList = rs.getString("encoded_list"),
            size = rs.getInt("size")
        )
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
     * Encode a [BitSet] of [size] bits to a multibase (`u`-prefixed), GZIP-compressed,
     * base64url (no-padding) string.
     *
     * Bit ordering follows the W3C spec: the LEFT-MOST bit of each byte (the most
     * significant bit) is the lowest index, i.e. entry 0 = bit 7 of byte 0 (0x80).
     */
    private fun encodeBitSet(bitSet: BitSet, size: Int): String {
        val byteCount = (size + 7) / 8
        val bytes = ByteArray(byteCount)
        // TODO: cancellation gap — this loop iterates over the full status list (default 131072)
        // without checking for cooperative cancellation. As a plain non-suspend function it cannot
        // reach coroutineContext; if made suspend, add coroutineContext.ensureActive() every ~8192 iterations.
        for (i in 0 until size) {
            if (bitSet.get(i)) {
                bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (7 - (i % 8)))).toByte()
            }
        }

        val gzipped = ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { gzip -> gzip.write(bytes) }
            baos.toByteArray()
        }

        return MULTIBASE_BASE64URL_NO_PAD_PREFIX +
            Base64.getUrlEncoder().withoutPadding().encodeToString(gzipped)
    }

    /**
     * Decode a GZIP-compressed, base64url-encoded string back to a [BitSet],
     * reading bits MSB-first (left-most bit of each byte = lowest index).
     *
     * Only spec-compliant values carrying the multibase `u` prefix are accepted; the
     * prefix is the format-version marker. Legacy un-prefixed values were written
     * LSB-first by pre-MSB-first versions of this class, so reading them with the
     * current decoder would mirror every bit within its byte — e.g. a credential
     * revoked at index 0 would silently read as NOT revoked. Such values are
     * rejected fail-closed ([TrustWeaveException.InvalidState] with code
     * `STATUS_LIST_LEGACY_FORMAT`); a raw GZIP payload base64url-encodes to
     * `H4s...`, so the prefix detection is unambiguous.
     */
    private fun decodeBitSet(encoded: String): BitSet {
        if (!encoded.startsWith(MULTIBASE_BASE64URL_NO_PAD_PREFIX)) {
            throw TrustWeaveException.InvalidState(
                code = "STATUS_LIST_LEGACY_FORMAT",
                message = "Stored encodedList lacks the multibase 'u' prefix that marks the " +
                    "current MSB-first bitstring format. Un-prefixed values were written by a " +
                    "legacy version of this library using the opposite (LSB-first) bit order; " +
                    "reading them with the current decoder would invert bit positions and could " +
                    "report revoked credentials as valid. Failing closed: regenerate or migrate " +
                    "this status list to the current format."
            )
        }
        val payload = encoded.substring(MULTIBASE_BASE64URL_NO_PAD_PREFIX.length)
        val gzipped = Base64.getUrlDecoder().decode(payload)
        val bytes = java.util.zip.GZIPInputStream(gzipped.inputStream()).use { it.readBytes() }
        val bitSet = BitSet(bytes.size * 8)
        // TODO: cancellation gap — this nested loop iterates over the full status list
        // without checking for cooperative cancellation. As a plain non-suspend function it cannot
        // reach coroutineContext; if made suspend, add coroutineContext.ensureActive() every ~8192 iterations.
        for (i in bytes.indices) {
            for (j in 0..7) {
                if ((bytes[i].toInt() and (1 shl (7 - j))) != 0) {
                    bitSet.set(i * 8 + j)
                }
            }
        }
        return bitSet
    }

    // -------------------------------------------------------------------------
    // Fail-closed helpers
    // -------------------------------------------------------------------------

    /**
     * Error raised when a referenced status list cannot be obtained and verified.
     *
     * Per W3C Bitstring Status List v1.0, an unavailable status list must surface
     * as an error — never as a "valid" status. Remote fetch + signature verification
     * of external status list credentials is not implemented; unknown lists fail closed.
     */
    private fun statusListUnavailable(statusListId: StatusListId): TrustWeaveException =
        TrustWeaveException.InvalidState(
            code = "STATUS_LIST_UNAVAILABLE",
            message = "Status list $statusListId could not be retrieved and verified " +
                "(unknown locally; remote fetch and verification is not supported). " +
                "Failing closed: credential status is unknown, not valid.",
            context = mapOf("statusListId" to statusListId.toString())
        )

    /**
     * Validate that [index] addresses an entry within the status list (RANGE_ERROR
     * per W3C Bitstring Status List v1.0 otherwise).
     */
    private fun requireIndexInRange(index: Int, row: StatusListRow, statusListId: StatusListId) {
        val capacity = row.size / row.bitsPerEntry
        if (index < 0 || index >= capacity) {
            throw TrustWeaveException.InvalidOperation(
                code = "RANGE_ERROR",
                message = "Status list index $index is out of range [0, ${capacity - 1}] " +
                    "for status list $statusListId",
                context = mapOf(
                    "statusListId" to statusListId.toString(),
                    "index" to index,
                    "capacity" to capacity
                )
            )
        }
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
