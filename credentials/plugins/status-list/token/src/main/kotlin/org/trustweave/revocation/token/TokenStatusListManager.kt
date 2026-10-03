package org.trustweave.revocation.token

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.util.Base64URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import org.trustweave.core.exception.ConfigException
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.revocation.RevocationStatus
import org.trustweave.credential.revocation.StatusListMetadata
import org.trustweave.credential.revocation.StatusListStatistics
import org.trustweave.credential.revocation.StatusUpdate
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult
import java.sql.Connection
import java.sql.Timestamp
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource

/**
 * IETF Token Status List implementation of [CredentialRevocationManager].
 *
 * Produces a compact JWT with `typ = "statuslist+jwt"` per the IETF draft
 * `draft-looker-oauth-jwt-cwt-status-list`.
 *
 * **Encoding:**
 * - Each credential occupies [bitsPerEntry] bits in a byte array.
 * - `bitsPerEntry = 1`: 8 credentials per byte (bit 0 = entry 0 of each byte).
 * - `bitsPerEntry = 2`: 4 credentials per byte.
 * - The byte array is ZLIB-compressed (RFC 1950) and base64url-encoded without padding →
 *   `status_list.lst` JWT claim, as `draft-ietf-oauth-status-list` requires
 *   (see [TokenStatusListCodec], which also reads legacy uncompressed lists).
 *
 * **JWT structure:**
 * ```
 * {
 *   "typ": "statuslist+jwt",
 *   "alg": "EdDSA" | "ES256"
 * }
 * {
 *   "iss": "<issuerDid>",
 *   "sub": "<statusListUri>",
 *   "iat": <unix seconds>,
 *   "status_list": {
 *     "bits": 1,
 *     "lst": "<base64url>"
 *   }
 * }
 * ```
 *
 * **Storage:** Three SQL tables (`token_status_lists`, `token_credential_indices`,
 * `token_next_index`) are created on construction if absent.
 *
 * **Signing:** the token is signed with the issuer's real key, [issuerKeyId] (a verification
 * method published in the issuer's DID document, held in [kms]); that id is the JWT `kid`. With no
 * [issuerKeyId], [buildStatusListToken] fails with a [ConfigException]: a token signed by a key
 * nobody can resolve verifies for no one, so none is ever fabricated.
 *
 * **Fail-closed semantics** (as in the Bitstring manager): a status lookup against an unknown
 * status list, an out-of-range index, or a credential whose index cannot be determined throws
 * instead of reporting "not revoked".
 *
 * Note: tokens issued by earlier versions carried an uncompressed `lst`; [TokenStatusListCodec.decode]
 * still reads those. The tokens built here use the spec's ZLIB form.
 *
 * @param issuerKeyId Issuer verification method used to sign tokens (`kid`)
 */
class TokenStatusListManager(
    private val dataSource: DataSource,
    private val kms: KeyManagementService,
    private val issuerDid: String,
    private val statusListUri: String,
    private val bitsPerEntry: Int = 1,
    private val issuerKeyId: VerificationMethodId? = null,
) : CredentialRevocationManager {
    /** Binary-compatible constructor from before [issuerKeyId] existed. */
    constructor(
        dataSource: DataSource,
        kms: KeyManagementService,
        issuerDid: String,
        statusListUri: String,
        bitsPerEntry: Int,
    ) : this(dataSource, kms, issuerDid, statusListUri, bitsPerEntry, null)

    private val logger = LoggerFactory.getLogger(TokenStatusListManager::class.java)

    init {
        require(bitsPerEntry == 1 || bitsPerEntry == 2) {
            "bitsPerEntry must be 1 or 2, got $bitsPerEntry"
        }
        initializeSchema()
    }

    private val json =
        Json {
            prettyPrint = false
            encodeDefaults = false
            ignoreUnknownKeys = true
        }

    // -------------------------------------------------------------------------
    // Schema initialisation
    // -------------------------------------------------------------------------

    private fun initializeSchema() {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn
                    .prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS token_status_lists (
                            id VARCHAR(255) PRIMARY KEY,
                            issuer_did VARCHAR(255) NOT NULL,
                            purpose VARCHAR(50) NOT NULL,
                            size INT NOT NULL,
                            bits_per_entry INT NOT NULL DEFAULT 1,
                            status_array BYTEA NOT NULL,
                            status_list_token TEXT,
                            status_list_uri VARCHAR(500),
                            created_at TIMESTAMP NOT NULL,
                            updated_at TIMESTAMP NOT NULL
                        )
                        """.trimIndent(),
                    ).execute()

                conn
                    .prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS token_credential_indices (
                            credential_id VARCHAR(255) NOT NULL,
                            status_list_id VARCHAR(255) NOT NULL REFERENCES token_status_lists(id),
                            entry_index INT NOT NULL,
                            PRIMARY KEY (credential_id, status_list_id)
                        )
                        """.trimIndent(),
                    ).execute()

                conn
                    .prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS token_next_index (
                            status_list_id VARCHAR(255) PRIMARY KEY REFERENCES token_status_lists(id),
                            next_index INT NOT NULL DEFAULT 0
                        )
                        """.trimIndent(),
                    ).execute()

                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw TrustWeaveException.InvalidState(
                    message = "Failed to initialise token status list schema: ${e.message}",
                    cause = e,
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
        customId: String?,
    ): StatusListId =
        withContext(Dispatchers.IO) {
            val id = customId ?: UUID.randomUUID().toString()
            val byteCount = bytesRequired(size)
            val statusArray = ByteArray(byteCount)
            val now = Clock.System.now()

            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    conn
                        .prepareStatement(
                            """
                            INSERT INTO token_status_lists
                                (id, issuer_did, purpose, size, bits_per_entry, status_array, status_list_token,
                                 status_list_uri, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, ?)
                            """.trimIndent(),
                        ).apply {
                            setString(1, id)
                            setString(2, issuerDid)
                            setString(3, purpose.name)
                            setInt(4, size)
                            setInt(5, bitsPerEntry)
                            setBytes(6, statusArray)
                            setString(7, statusListUri)
                            setTimestamp(8, Timestamp(now.toEpochMilliseconds()))
                            setTimestamp(9, Timestamp(now.toEpochMilliseconds()))
                        }.executeUpdate()

                    conn
                        .prepareStatement(
                            "INSERT INTO token_next_index (status_list_id, next_index) VALUES (?, 0)",
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
                        message = "Failed to create token status list: ${e.message}",
                        cause = e,
                    )
                }
            }

            StatusListId(id)
        }

    override suspend fun revokeCredential(
        credentialId: String,
        statusListId: StatusListId,
    ): Boolean =
        withContext(Dispatchers.IO) {
            updateEntry(credentialId, statusListId.toString(), revoked = true, suspended = null)
        }

    override suspend fun suspendCredential(
        credentialId: String,
        statusListId: StatusListId,
    ): Boolean =
        withContext(Dispatchers.IO) {
            updateEntry(credentialId, statusListId.toString(), revoked = null, suspended = true)
        }

    override suspend fun unrevokeCredential(
        credentialId: String,
        statusListId: StatusListId,
    ): Boolean =
        withContext(Dispatchers.IO) {
            updateEntry(credentialId, statusListId.toString(), revoked = false, suspended = null)
        }

    override suspend fun unsuspendCredential(
        credentialId: String,
        statusListId: StatusListId,
    ): Boolean =
        withContext(Dispatchers.IO) {
            updateEntry(credentialId, statusListId.toString(), revoked = null, suspended = false)
        }

    override suspend fun checkRevocationStatus(credential: VerifiableCredential): RevocationStatus =
        withContext(Dispatchers.IO) {
            val credentialStatus =
                credential.credentialStatus
                    ?: return@withContext RevocationStatus(revoked = false, suspended = false)

            // Fail closed: once a credential declares a status entry, an undeterminable index
            // must surface as an error, never as "valid".
            val statusListId = credentialStatus.statusListCredential ?: credentialStatus.id
            val index =
                credentialStatus.statusListIndex?.toIntOrNull()
                    ?: credential.id?.toString()?.let { getCredentialIndex(it, statusListId) }
                    ?: throw TrustWeaveException.InvalidState(
                        code = "STATUS_LIST_INDEX_UNKNOWN",
                        message =
                            "Credential declares a status entry in status list $statusListId but its " +
                                "status list index cannot be determined; failing closed instead of reporting " +
                                "a valid status",
                        context = mapOf("statusListId" to statusListId.toString()),
                    )

            checkStatusByIndex(statusListId, index)
        }

    override suspend fun checkStatusByIndex(
        statusListId: StatusListId,
        index: Int,
    ): RevocationStatus =
        withContext(Dispatchers.IO) {
            val row = loadRow(statusListId.toString()) ?: throw statusListUnavailable(statusListId)

            if (index < 0 || index >= row.size) {
                throw TrustWeaveException.InvalidOperation(
                    code = "RANGE_ERROR",
                    message = "Status list index $index is out of range [0, ${row.size - 1}] for status list $statusListId",
                    context = mapOf("statusListId" to statusListId.toString(), "index" to index, "capacity" to row.size),
                )
            }

            val purpose = parsePurpose(row.purpose)
            val (revoked, suspended) = readBits(row.statusArray, index, purpose)

            RevocationStatus(
                revoked = revoked,
                suspended = suspended,
                statusListId = statusListId,
                index = index,
            )
        }

    override suspend fun checkStatusByCredentialId(
        credentialId: String,
        statusListId: StatusListId,
    ): RevocationStatus =
        withContext(Dispatchers.IO) {
            if (loadRow(statusListId.toString()) == null) throw statusListUnavailable(statusListId)
            // Fail closed: without an index assignment the status cannot be determined.
            val index =
                getCredentialIndex(credentialId, statusListId)
                    ?: throw TrustWeaveException.NotFound(
                        resource = "status list index for credential '$credentialId' in status list '$statusListId'",
                    )
            checkStatusByIndex(statusListId, index)
        }

    private fun statusListUnavailable(statusListId: StatusListId): TrustWeaveException =
        TrustWeaveException.InvalidState(
            code = "STATUS_LIST_UNAVAILABLE",
            message =
                "Status list $statusListId could not be retrieved (unknown to this manager). " +
                    "Failing closed: credential status is unknown, not valid.",
            context = mapOf("statusListId" to statusListId.toString()),
        )

    override suspend fun getCredentialIndex(
        credentialId: String,
        statusListId: StatusListId,
    ): Int? =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn
                    .prepareStatement(
                        "SELECT entry_index FROM token_credential_indices WHERE credential_id = ? AND status_list_id = ?",
                    ).apply {
                        setString(1, credentialId)
                        setString(2, statusListId.toString())
                    }.executeQuery()
                    .let { rs ->
                        if (rs.next()) rs.getInt("entry_index") else null
                    }
            }
        }

    override suspend fun assignCredentialIndex(
        credentialId: String,
        statusListId: StatusListId,
        index: Int?,
    ): Int =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    val sid = statusListId.toString()
                    // Idempotent: return existing index if already assigned
                    val existing =
                        conn
                            .prepareStatement(
                                "SELECT entry_index FROM token_credential_indices WHERE credential_id = ? AND status_list_id = ?",
                            ).apply {
                                setString(1, credentialId)
                                setString(2, sid)
                            }.executeQuery()
                    if (existing.next()) {
                        conn.rollback()
                        return@withContext existing.getInt("entry_index")
                    }

                    val assignedIndex =
                        if (index != null) {
                            val count =
                                conn
                                    .prepareStatement(
                                        "SELECT COUNT(*) AS cnt FROM token_credential_indices WHERE status_list_id = ? AND entry_index = ?",
                                    ).apply {
                                        setString(1, sid)
                                        setInt(2, index)
                                    }.executeQuery()
                                    .let { rs ->
                                        rs.next()
                                        rs.getInt("cnt")
                                    }
                            if (count > 0) {
                                throw IllegalArgumentException("Index $index already assigned in status list $statusListId")
                            }
                            index
                        } else {
                            getNextAvailableIndex(sid, conn)
                        }

                    conn
                        .prepareStatement(
                            "INSERT INTO token_credential_indices (credential_id, status_list_id, entry_index) VALUES (?, ?, ?)",
                        ).apply {
                            setString(1, credentialId)
                            setString(2, sid)
                            setInt(3, assignedIndex)
                        }.executeUpdate()

                    conn.commit()
                    assignedIndex
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                }
            }
        }

    override suspend fun revokeCredentials(
        credentialIds: List<String>,
        statusListId: StatusListId,
    ): Map<String, Boolean> =
        withContext(Dispatchers.IO) {
            val row =
                loadRow(statusListId.toString())
                    ?: return@withContext credentialIds.associateWith { false }

            if (parsePurpose(row.purpose) != StatusPurpose.REVOCATION) {
                return@withContext credentialIds.associateWith { false }
            }

            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    val arr = row.statusArray.copyOf()
                    val results = mutableMapOf<String, Boolean>()

                    for (credentialId in credentialIds) {
                        val entryIndex = getOrAssignIndex(credentialId, statusListId.toString(), conn)
                        setBit(arr, entryIndex, bitOffset = 0, value = true)
                        results[credentialId] = true
                    }

                    persistStatusArray(statusListId.toString(), arr, conn)
                    conn.commit()
                    results
                } catch (e: CancellationException) {
                    conn.rollback()
                    throw e
                } catch (e: Exception) {
                    conn.rollback()
                    logger.error(
                        "Failed to revoke credentials in token status list {}: {}",
                        statusListId,
                        e.message,
                        e,
                    )
                    throw TrustWeaveException.InvalidState(
                        message = "Failed to revoke credentials in token status list $statusListId: ${e.message}",
                        cause = e,
                    )
                }
            }
        }

    override suspend fun updateStatusListBatch(
        statusListId: StatusListId,
        updates: List<StatusUpdate>,
    ) = withContext(Dispatchers.IO) {
        val row =
            loadRow(statusListId.toString())
                ?: throw IllegalArgumentException("Status list not found: $statusListId")

        val purpose = parsePurpose(row.purpose)
        val arr = row.statusArray.copyOf()

        for (update in updates) {
            if (bitsPerEntry == 2) {
                update.revoked?.let { setBit(arr, update.index, bitOffset = 0, value = it) }
                update.suspended?.let { setBit(arr, update.index, bitOffset = 1, value = it) }
            } else {
                when {
                    update.revoked != null && purpose == StatusPurpose.REVOCATION ->
                        setBit(arr, update.index, bitOffset = 0, value = update.revoked!!)
                    update.suspended != null && purpose == StatusPurpose.SUSPENSION ->
                        setBit(arr, update.index, bitOffset = 0, value = update.suspended!!)
                }
            }
        }

        dataSource.connection.use { conn ->
            persistStatusArray(statusListId.toString(), arr, conn)
        }
    }

    override suspend fun getStatusListStatistics(statusListId: StatusListId): StatusListStatistics? =
        withContext(Dispatchers.IO) {
            val row = loadRow(statusListId.toString()) ?: return@withContext null

            val usedIndices =
                dataSource.connection.use { conn ->
                    conn
                        .prepareStatement(
                            "SELECT COUNT(*) AS cnt FROM token_credential_indices WHERE status_list_id = ?",
                        ).apply {
                            setString(1, statusListId.toString())
                        }.executeQuery()
                        .let { rs ->
                            rs.next()
                            rs.getInt("cnt")
                        }
                }

            val purpose = parsePurpose(row.purpose)
            val arr = row.statusArray
            val entriesPerByte = 8 / bitsPerEntry
            val totalCapacity = arr.size * entriesPerByte

            var revokedCount = 0
            var suspendedCount = 0
            for (idx in 0 until totalCapacity) {
                val (rev, sus) = readBits(arr, idx, purpose)
                if (rev) revokedCount++
                if (sus) suspendedCount++
            }

            StatusListStatistics(
                statusListId = statusListId,
                issuerDid = row.issuerDid,
                purpose = purpose,
                totalCapacity = totalCapacity,
                usedIndices = usedIndices,
                revokedCount = revokedCount,
                suspendedCount = suspendedCount,
                availableIndices = totalCapacity - usedIndices,
                lastUpdated = row.updatedAt,
            )
        }

    override suspend fun getStatusList(statusListId: StatusListId): StatusListMetadata? =
        withContext(Dispatchers.IO) {
            val row = loadRow(statusListId.toString()) ?: return@withContext null
            StatusListMetadata(
                id = statusListId,
                issuerDid = row.issuerDid,
                purpose = parsePurpose(row.purpose),
                size = row.size,
                createdAt = row.createdAt,
                lastUpdated = row.updatedAt,
            )
        }

    override suspend fun listStatusLists(issuerDid: String?): List<StatusListMetadata> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql =
                    if (issuerDid != null) {
                        "SELECT id, issuer_did, purpose, size, created_at, updated_at FROM token_status_lists WHERE issuer_did = ?"
                    } else {
                        "SELECT id, issuer_did, purpose, size, created_at, updated_at FROM token_status_lists"
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
                            lastUpdated = updatedAt,
                        ),
                    )
                }
                results
            }
        }

    override suspend fun deleteStatusList(statusListId: StatusListId): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    val id = statusListId.toString()
                    conn
                        .prepareStatement(
                            "DELETE FROM token_credential_indices WHERE status_list_id = ?",
                        ).apply { setString(1, id) }
                        .executeUpdate()
                    conn
                        .prepareStatement(
                            "DELETE FROM token_next_index WHERE status_list_id = ?",
                        ).apply { setString(1, id) }
                        .executeUpdate()
                    val deleted =
                        conn
                            .prepareStatement(
                                "DELETE FROM token_status_lists WHERE id = ?",
                            ).apply { setString(1, id) }
                            .executeUpdate() > 0
                    conn.commit()
                    deleted
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    conn.rollback()
                    false
                }
            }
        }

    override suspend fun expandStatusList(
        statusListId: StatusListId,
        additionalSize: Int,
    ) = withContext(Dispatchers.IO) {
        val row =
            loadRow(statusListId.toString())
                ?: throw IllegalArgumentException("Status list not found: $statusListId")

        val additionalBytes = bytesRequired(additionalSize)
        val newArray = row.statusArray + ByteArray(additionalBytes)
        val newSize = row.size + additionalSize

        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn
                    .prepareStatement(
                        "UPDATE token_status_lists SET status_array = ?, size = ?, updated_at = ? WHERE id = ?",
                    ).apply {
                        setBytes(1, newArray)
                        setInt(2, newSize)
                        setTimestamp(3, Timestamp(Clock.System.now().toEpochMilliseconds()))
                        setString(4, statusListId.toString())
                    }.executeUpdate()
                conn.commit()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
        Unit
    }

    // -------------------------------------------------------------------------
    // Token JWT building and signing
    // -------------------------------------------------------------------------

    /**
     * Build and sign a Token Status List JWT, then persist it.
     *
     * The JWT header carries `typ = "statuslist+jwt"` and the algorithm is chosen based
     * on the key generated by [kms]: `EdDSA` for Ed25519 keys, `ES256` for EC/P-256 keys.
     *
     * The `status_list` claim contains `bits` and `lst` (ZLIB-compressed, base64url, no padding).
     *
     * @param statusListId ID of the status list to encode
     * @param ttlSeconds Optional time-to-live in seconds added as `exp` claim
     * @return [TokenStatusListToken] wrapping the compact JWT
     */
    suspend fun buildStatusListToken(
        statusListId: StatusListId,
        ttlSeconds: Long? = null,
    ): TokenStatusListToken =
        withContext(Dispatchers.IO) {
            val row =
                loadRow(statusListId.toString())
                    ?: throw IllegalArgumentException("Status list not found: $statusListId")

            val lstEncoded = TokenStatusListCodec.encode(row.statusArray)

            val now = Clock.System.now()
            val iatSeconds = now.epochSeconds
            val expSeconds = ttlSeconds?.let { iatSeconds + it }

            val payloadObj =
                buildJsonObject {
                    put("iss", row.issuerDid)
                    put("sub", row.statusListUri ?: statusListUri)
                    put("iat", iatSeconds)
                    expSeconds?.let { put("exp", it) }
                    ttlSeconds?.let { put("ttl", it) }
                    put(
                        "status_list",
                        buildJsonObject {
                            put("bits", bitsPerEntry)
                            put("lst", lstEncoded)
                        },
                    )
                }
            val payloadJson = json.encodeToString(JsonObject.serializer(), payloadObj)

            val signingKey =
                issuerKeyId ?: throw ConfigException.InvalidFormat(
                    parseError =
                        "No issuer signing key configured for token status lists. Provide the issuer's " +
                            "verification method ID (issuerKeyId) when constructing TokenStatusListManager; " +
                            "tokens are never signed with fabricated keys.",
                    field = "issuerKeyId",
                )
            if (signingKey.did.value != row.issuerDid) {
                throw ConfigException.InvalidFormat(
                    parseError =
                        "Configured issuerKeyId belongs to '${signingKey.did.value}' but the status list is " +
                            "issued by '${row.issuerDid}'; refusing to sign.",
                    field = "issuerKeyId",
                )
            }
            val kmsKeyId = KeyId(signingKey.keyId.value.removePrefix("#"))
            val publicKey = kms.getPublicKey(kmsKeyId)
            if (publicKey !is GetPublicKeyResult.Success || publicKey.keyHandle.algorithm != Algorithm.Ed25519.name) {
                throw ConfigException.InvalidFormat(
                    parseError =
                        "Issuer signing key '${signingKey.value}' must be an Ed25519 key present in the " +
                            "configured KMS to sign token status lists (got: $publicKey).",
                    field = "issuerKeyId",
                )
            }

            val header =
                JWSHeader
                    .Builder(JWSAlgorithm.EdDSA)
                    .type(com.nimbusds.jose.JOSEObjectType("statuslist+jwt"))
                    .keyID(signingKey.value)
                    .build()

            val signingInput = "${header.toBase64URL()}.${Base64URL.encode(payloadJson)}"
            val signResult = kms.sign(kmsKeyId, signingInput.toByteArray(Charsets.US_ASCII))
            val rawSignature =
                when (signResult) {
                    is SignResult.Success -> signResult.signature
                    else ->
                        throw TrustWeaveException.InvalidState(
                            message = "Failed to sign token status list JWT with '${signingKey.value}': $signResult",
                        )
                }

            val signatureB64 =
                Base64URL(
                    Base64.getUrlEncoder().withoutPadding().encodeToString(rawSignature),
                )

            val jwt = "${header.toBase64URL()}.${Base64URL.encode(payloadJson)}.$signatureB64"
            val effectiveUri = row.statusListUri ?: statusListUri

            // Persist the token
            dataSource.connection.use { conn ->
                conn
                    .prepareStatement(
                        "UPDATE token_status_lists SET status_list_token = ?, updated_at = ? WHERE id = ?",
                    ).apply {
                        setString(1, jwt)
                        setTimestamp(2, Timestamp(Clock.System.now().toEpochMilliseconds()))
                        setString(3, statusListId.toString())
                    }.executeUpdate()
            }

            TokenStatusListToken(
                jwt = jwt,
                statusListId = statusListId.toString(),
                uri = effectiveUri,
            )
        }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun updateEntry(
        credentialId: String,
        statusListId: String,
        revoked: Boolean?,
        suspended: Boolean?,
    ): Boolean {
        val row = loadRow(statusListId) ?: return false
        val purpose = parsePurpose(row.purpose)

        if (bitsPerEntry == 1) {
            if (revoked != null && purpose != StatusPurpose.REVOCATION) return false
            if (suspended != null && purpose != StatusPurpose.SUSPENSION) return false
        }

        return dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val entryIndex = getOrAssignIndex(credentialId, statusListId, conn)
                val arr = row.statusArray.copyOf()

                if (bitsPerEntry == 2) {
                    revoked?.let { setBit(arr, entryIndex, bitOffset = 0, value = it) }
                    suspended?.let { setBit(arr, entryIndex, bitOffset = 1, value = it) }
                } else {
                    revoked?.let { setBit(arr, entryIndex, bitOffset = 0, value = it) }
                    suspended?.let { setBit(arr, entryIndex, bitOffset = 0, value = it) }
                }

                persistStatusArray(statusListId, arr, conn)
                conn.commit()
                true
            } catch (e: CancellationException) {
                conn.rollback()
                throw e
            } catch (e: Exception) {
                conn.rollback()
                logger.error(
                    "Failed to update status entry for credential {} in token status list {}: {}",
                    credentialId,
                    statusListId,
                    e.message,
                    e,
                )
                throw TrustWeaveException.InvalidState(
                    message =
                        "Failed to update status entry for credential $credentialId " +
                            "in token status list $statusListId: ${e.message}",
                    cause = e,
                )
            }
        }
    }

    private fun getOrAssignIndex(
        credentialId: String,
        statusListId: String,
        conn: Connection,
    ): Int {
        val existing =
            conn
                .prepareStatement(
                    "SELECT entry_index FROM token_credential_indices WHERE credential_id = ? AND status_list_id = ?",
                ).apply {
                    setString(1, credentialId)
                    setString(2, statusListId)
                }.executeQuery()

        if (existing.next()) return existing.getInt("entry_index")

        val next = getNextAvailableIndex(statusListId, conn)
        conn
            .prepareStatement(
                "INSERT INTO token_credential_indices (credential_id, status_list_id, entry_index) VALUES (?, ?, ?)",
            ).apply {
                setString(1, credentialId)
                setString(2, statusListId)
                setInt(3, next)
            }.executeUpdate()
        return next
    }

    private fun getNextAvailableIndex(
        statusListId: String,
        conn: Connection,
    ): Int {
        val rs =
            conn
                .prepareStatement(
                    "SELECT next_index FROM token_next_index WHERE status_list_id = ?",
                ).apply {
                    setString(1, statusListId)
                }.executeQuery()

        val next = if (rs.next()) rs.getInt("next_index") else 0

        // Portable upsert: `MERGE ... KEY (...)` is H2-only and is a syntax error on PostgreSQL.
        // See BitstringStatusListManager.getNextAvailableIndex for the same fix.
        val updated =
            conn
                .prepareStatement(
                    "UPDATE token_next_index SET next_index = ? WHERE status_list_id = ?",
                ).apply {
                    setInt(1, next + 1)
                    setString(2, statusListId)
                }.executeUpdate()

        if (updated == 0) {
            conn
                .prepareStatement(
                    "INSERT INTO token_next_index (status_list_id, next_index) VALUES (?, ?)",
                ).apply {
                    setString(1, statusListId)
                    setInt(2, next + 1)
                }.executeUpdate()
        }

        return next
    }

    private fun persistStatusArray(
        statusListId: String,
        arr: ByteArray,
        conn: Connection,
    ) {
        conn
            .prepareStatement(
                "UPDATE token_status_lists SET status_array = ?, updated_at = ? WHERE id = ?",
            ).apply {
                setBytes(1, arr)
                setTimestamp(2, Timestamp(Clock.System.now().toEpochMilliseconds()))
                setString(3, statusListId)
            }.executeUpdate()
    }

    // -------------------------------------------------------------------------
    // Bit-packing helpers
    // -------------------------------------------------------------------------

    /**
     * Number of bytes required to hold [entries] entries at [bitsPerEntry] bits each.
     */
    private fun bytesRequired(entries: Int): Int {
        val totalBits = entries * bitsPerEntry
        return (totalBits + 7) / 8
    }

    /**
     * Set a single bit within the packed byte array.
     *
     * For `bitsPerEntry = 1`:  bit position = `entryIndex`.
     * For `bitsPerEntry = 2`:  bit position = `entryIndex * 2 + bitOffset` (0 = revocation, 1 = suspension).
     */
    private fun setBit(
        arr: ByteArray,
        entryIndex: Int,
        bitOffset: Int,
        value: Boolean,
    ) {
        val bitPos = entryIndex * bitsPerEntry + bitOffset
        val byteIdx = bitPos / 8
        val bitIdx = bitPos % 8
        if (value) {
            arr[byteIdx] = (arr[byteIdx].toInt() or (1 shl bitIdx)).toByte()
        } else {
            arr[byteIdx] = (arr[byteIdx].toInt() and (1 shl bitIdx).inv()).toByte()
        }
    }

    /**
     * Read the status bits for a given credential entry index.
     *
     * @return `Pair(revoked, suspended)`
     */
    private fun readBits(
        arr: ByteArray,
        entryIndex: Int,
        purpose: StatusPurpose,
    ): Pair<Boolean, Boolean> {
        fun getBit(bitPos: Int): Boolean {
            val byteIdx = bitPos / 8
            val bitIdx = bitPos % 8
            if (byteIdx >= arr.size) return false
            return (arr[byteIdx].toInt() and (1 shl bitIdx)) != 0
        }

        return when {
            bitsPerEntry == 2 -> {
                val revBit = getBit(entryIndex * 2)
                val susBit = getBit(entryIndex * 2 + 1)
                Pair(revBit, susBit)
            }
            purpose == StatusPurpose.REVOCATION -> Pair(getBit(entryIndex), false)
            else -> Pair(false, getBit(entryIndex))
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
        val statusArray: ByteArray,
        val statusListUri: String?,
        val createdAt: Instant,
        val updatedAt: Instant,
    )

    private fun loadRow(statusListId: String): StatusListRow? {
        return dataSource.connection.use { conn ->
            val rs =
                conn
                    .prepareStatement(
                        """
                        SELECT id, issuer_did, purpose, size, bits_per_entry, status_array, status_list_uri,
                               created_at, updated_at
                        FROM token_status_lists WHERE id = ?
                        """.trimIndent(),
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
                statusArray = rs.getBytes("status_array") ?: ByteArray(0),
                statusListUri = rs.getString("status_list_uri"),
                createdAt = rs.getTimestamp("created_at")?.toKotlinInstant() ?: Clock.System.now(),
                updatedAt = rs.getTimestamp("updated_at")?.toKotlinInstant() ?: Clock.System.now(),
            )
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private fun parsePurpose(value: String): StatusPurpose =
        try {
            StatusPurpose.valueOf(value.uppercase())
        } catch (e: IllegalArgumentException) {
            throw TrustWeaveException.InvalidState(
                code = "STATUS_LIST_CORRUPT",
                message = "Stored status purpose '$value' is not recognised; failing closed",
                cause = e,
            )
        }

    private fun Timestamp.toKotlinInstant(): Instant = Instant.fromEpochMilliseconds(this.time)
}
