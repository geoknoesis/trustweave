package org.trustweave.revocation.bitstring

import org.trustweave.core.exception.ConfigException
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.revocation.StatusUpdate
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.credential.spi.proof.ProofEngineCapabilities
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64
import java.util.zip.GZIPInputStream
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BitstringStatusListManagerTest {

    private lateinit var dataSource: DataSource
    private lateinit var kms: InMemoryKeyManagementService
    private lateinit var manager: BitstringStatusListManager

    private val issuerDid = "did:key:z6MkTestBitstringIssuer"
    private val issuerKeyId = VerificationMethodId(Did(issuerDid), KeyId("#issuer-key-1"))

    @BeforeTest
    fun setUp() {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:bitstring_test_${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
            username = "sa"
            password = ""
            maximumPoolSize = 5
        }
        dataSource = HikariDataSource(config)
        kms = InMemoryKeyManagementService()
        manager = BitstringStatusListManagerFactory.create(
            dataSource = dataSource,
            kms = kms,
            issuerDid = issuerDid,
            bitsPerEntry = 1
        )
    }

    @AfterTest
    fun tearDown() {
        (dataSource as? HikariDataSource)?.close()
    }

    /**
     * Minimal [ProofEngine] test double implementing only the public SPI.
     *
     * Records the issuance request so tests can assert the manager delegates signing
     * with the configured issuer key (and never fabricates one).
     *
     * @param issuerOverride if set, the engine MISBEHAVES and returns a VC issued by
     *   this issuer instead of the requested one (exercises the manager's post-check)
     */
    private class RecordingProofEngine(
        private val issuerOverride: Issuer? = null
    ) : ProofEngine {
        var lastRequest: IssuanceRequest? = null

        override val format = ProofSuiteId.VC_LD
        override val formatName = "Recording VC-LD (test)"
        override val formatVersion = "test"
        override val capabilities = ProofEngineCapabilities()

        override suspend fun issue(request: IssuanceRequest): VerifiableCredential {
            lastRequest = request
            val keyId = requireNotNull(request.issuerKeyId) {
                "issuerKeyId must be provided by the manager"
            }
            return VerifiableCredential(
                type = request.type,
                issuer = issuerOverride ?: request.issuer,
                issuanceDate = request.issuedAt,
                credentialSubject = request.credentialSubject,
                proof = CredentialProof.LinkedDataProof(
                    type = "Ed25519Signature2020",
                    created = request.issuedAt,
                    verificationMethod = keyId.value,
                    proofPurpose = "assertionMethod",
                    proofValue = "ztest-signature"
                )
            )
        }

        override suspend fun verify(
            credential: VerifiableCredential,
            options: VerificationOptions
        ): VerificationResult = throw UnsupportedOperationException("Not used in tests")
    }

    private fun signingManager(
        engine: ProofEngine,
        keyId: VerificationMethodId = issuerKeyId
    ): BitstringStatusListManager = BitstringStatusListManagerFactory.create(
        dataSource = dataSource,
        kms = kms,
        issuerDid = issuerDid,
        bitsPerEntry = 1,
        proofEngine = engine,
        issuerKeyId = keyId
    )

    private fun readEncodedList(statusListId: StatusListId): String =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT encoded_list FROM bitstring_status_lists WHERE id = ?"
            ).apply {
                setString(1, statusListId.toString())
            }.executeQuery().let { rs ->
                check(rs.next()) { "Status list not found: $statusListId" }
                rs.getString("encoded_list")
            }
        }

    private fun writeEncodedList(statusListId: StatusListId, encodedList: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE bitstring_status_lists SET encoded_list = ? WHERE id = ?"
            ).apply {
                setString(1, encodedList)
                setString(2, statusListId.toString())
            }.executeUpdate()
        }
    }

    private fun decodeRawBitstring(encodedList: String): ByteArray {
        val gzipped = Base64.getUrlDecoder().decode(encodedList.removePrefix("u"))
        return GZIPInputStream(gzipped.inputStream()).use { it.readBytes() }
    }

    // -------------------------------------------------------------------------
    // Encoding round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `encodedList is multibase u-prefixed gzip-compressed base64url without padding`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION,
            size = 256
        )
        val encodedList = readEncodedList(statusListId)

        assertTrue(encodedList.startsWith("u"), "encodedList must carry the multibase base64url prefix 'u'")
        assertFalse(encodedList.contains('='), "base64url must not have padding")

        val raw = decodeRawBitstring(encodedList)
        assertEquals(
            16384,
            raw.size,
            "Requested 256 bits is below the spec minimum; bitstring must be 131072 bits = 16384 bytes"
        )
    }

    @Test
    fun `bit packing is MSB-first - the left-most bit of each byte is the lowest index`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        manager.updateStatusListBatch(
            statusListId,
            listOf(
                StatusUpdate(index = 0, revoked = true),
                StatusUpdate(index = 7, revoked = true),
                StatusUpdate(index = 8, revoked = true)
            )
        )

        val raw = decodeRawBitstring(readEncodedList(statusListId))
        assertEquals(
            0x81.toByte(),
            raw[0],
            "index 0 must set 0x80 (left-most bit) and index 7 must set 0x01, so byte 0 = 0x81"
        )
        assertEquals(0x80.toByte(), raw[1], "index 8 must set the left-most bit of byte 1 (0x80)")

        // Round-trip through the read path
        assertTrue(manager.checkStatusByIndex(statusListId, 0).revoked)
        assertFalse(manager.checkStatusByIndex(statusListId, 1).revoked)
        assertTrue(manager.checkStatusByIndex(statusListId, 7).revoked)
        assertTrue(manager.checkStatusByIndex(statusListId, 8).revoked)
        assertFalse(manager.checkStatusByIndex(statusListId, 9).revoked)
    }

    @Test
    fun `decoding rejects legacy un-prefixed encodedList values fail-closed`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        manager.assignCredentialIndex("legacy-cred", statusListId)
        manager.revokeCredential("legacy-cred", statusListId)

        // Strip the multibase prefix to simulate a list persisted by the legacy
        // (LSB-first) version of this class. Legacy data used the opposite bit order,
        // so reading it with the MSB-first decoder would mirror every bit within its
        // byte — a credential revoked at index 0 would read as NOT revoked. The 'u'
        // prefix is the format-version marker; un-prefixed values must be rejected.
        val encoded = readEncodedList(statusListId)
        assertTrue(encoded.startsWith("u"))
        writeEncodedList(statusListId, encoded.substring(1))

        val ex = assertFailsWith<TrustWeaveException.InvalidState> {
            manager.checkStatusByIndex(statusListId, 0)
        }
        assertEquals("STATUS_LIST_LEGACY_FORMAT", ex.code)
        assertTrue(
            ex.message.contains("regenerate", ignoreCase = true) ||
                ex.message.contains("migrate", ignoreCase = true),
            "Error must tell the operator to regenerate/migrate the status list: ${ex.message}"
        )
    }

    // -------------------------------------------------------------------------
    // Minimum size enforcement
    // -------------------------------------------------------------------------

    @Test
    fun `createStatusList enforces the spec minimum of 131072 bits`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION,
            size = 256
        )
        val metadata = manager.getStatusList(statusListId)
        assertNotNull(metadata)
        assertEquals(
            BitstringStatusListManager.MIN_STATUS_LIST_SIZE_BITS,
            metadata.size,
            "Sizes below the W3C minimum must be coerced up to 131072"
        )
    }

    // -------------------------------------------------------------------------
    // Fail-closed status checks
    // -------------------------------------------------------------------------

    @Test
    fun `checkStatusByIndex fails closed with RANGE_ERROR for out-of-range indices`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )

        val negative = assertFailsWith<TrustWeaveException.InvalidOperation> {
            manager.checkStatusByIndex(statusListId, -1)
        }
        assertEquals("RANGE_ERROR", negative.code)

        val tooLarge = assertFailsWith<TrustWeaveException.InvalidOperation> {
            manager.checkStatusByIndex(statusListId, BitstringStatusListManager.MIN_STATUS_LIST_SIZE_BITS)
        }
        assertEquals("RANGE_ERROR", tooLarge.code)
    }

    @Test
    fun `checkStatusByIndex fails closed when the status list is unknown`() = runBlocking {
        val ex = assertFailsWith<TrustWeaveException.InvalidState> {
            manager.checkStatusByIndex(StatusListId("https://example.com/status/unknown"), 0)
        }
        assertEquals("STATUS_LIST_UNAVAILABLE", ex.code)
    }

    @Test
    fun `checkStatusByCredentialId fails closed when the status list is unknown`() = runBlocking {
        val ex = assertFailsWith<TrustWeaveException.InvalidState> {
            manager.checkStatusByCredentialId("some-cred", StatusListId("unknown-list"))
        }
        assertEquals("STATUS_LIST_UNAVAILABLE", ex.code)
    }

    @Test
    fun `checkStatusByCredentialId fails closed for an unassigned credential`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        val ex = assertFailsWith<TrustWeaveException.NotFound> {
            manager.checkStatusByCredentialId("never-assigned", statusListId)
        }
        assertEquals("NOT_FOUND", ex.code)
    }

    @Test
    fun `assignCredentialIndex rejects an explicit out-of-range index`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        val ex = assertFailsWith<TrustWeaveException.InvalidOperation> {
            manager.assignCredentialIndex(
                "cred-oob",
                statusListId,
                index = BitstringStatusListManager.MIN_STATUS_LIST_SIZE_BITS + 1
            )
        }
        assertEquals("RANGE_ERROR", ex.code)
    }

    // -------------------------------------------------------------------------
    // Credential index assignment
    // -------------------------------------------------------------------------

    @Test
    fun `assignCredentialIndex auto-assigns sequential indices`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )

        val idx0 = manager.assignCredentialIndex("cred-a", statusListId)
        val idx1 = manager.assignCredentialIndex("cred-b", statusListId)
        val idx2 = manager.assignCredentialIndex("cred-c", statusListId)

        assertEquals(0, idx0)
        assertEquals(1, idx1)
        assertEquals(2, idx2)
    }

    @Test
    fun `assignCredentialIndex is idempotent for the same credential`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        val idx1 = manager.assignCredentialIndex("cred-idem", statusListId)
        val idx2 = manager.assignCredentialIndex("cred-idem", statusListId)
        assertEquals(idx1, idx2)
    }

    @Test
    fun `auto index assignment retries past an index claimed by a concurrent writer`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )

        // Simulate a concurrent writer that bound entry_index 0 WITHOUT advancing the
        // next-index counter — the next auto-assignment will collide on the
        // UNIQUE (status_list_id, entry_index) constraint and must retry with index 1.
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO bitstring_credential_indices (credential_id, status_list_id, entry_index) VALUES (?, ?, 0)"
            ).apply {
                setString(1, "concurrent-cred")
                setString(2, statusListId.toString())
            }.executeUpdate()
        }

        val idx = manager.assignCredentialIndex("late-cred", statusListId)
        assertEquals(1, idx, "Assignment must skip the already-claimed index and retry with the next one")
    }

    @Test
    fun `getCredentialIndex returns null for unassigned credential`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        val index = manager.getCredentialIndex("unknown-cred", statusListId)
        assertEquals(null, index)
    }

    // -------------------------------------------------------------------------
    // Revocation
    // -------------------------------------------------------------------------

    @Test
    fun `revokeCredential sets the bit and checkStatusByCredentialId returns revoked`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        manager.assignCredentialIndex("cred-revoke", statusListId)

        val before = manager.checkStatusByCredentialId("cred-revoke", statusListId)
        assertFalse(before.revoked, "Should not be revoked before revokeCredential")

        val result = manager.revokeCredential("cred-revoke", statusListId)
        assertTrue(result, "revokeCredential must return true")

        val after = manager.checkStatusByCredentialId("cred-revoke", statusListId)
        assertTrue(after.revoked, "Should be revoked after revokeCredential")
        assertFalse(after.suspended, "Should not be suspended")
    }

    @Test
    fun `unrevokeCredential clears the revocation bit`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        manager.assignCredentialIndex("cred-unrevoke", statusListId)
        manager.revokeCredential("cred-unrevoke", statusListId)
        manager.unrevokeCredential("cred-unrevoke", statusListId)

        val status = manager.checkStatusByCredentialId("cred-unrevoke", statusListId)
        assertFalse(status.revoked, "Should not be revoked after unrevokeCredential")
    }

    @Test
    fun `revokeCredentials batch revokes all provided credentials`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        val credIds = listOf("batch-1", "batch-2", "batch-3")
        credIds.forEach { manager.assignCredentialIndex(it, statusListId) }

        val results = manager.revokeCredentials(credIds, statusListId)

        credIds.forEach { id ->
            assertTrue(results[id] == true, "revokeCredentials must report success for $id")
            assertTrue(
                manager.checkStatusByCredentialId(id, statusListId).revoked,
                "$id must be revoked"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Suspension
    // -------------------------------------------------------------------------

    @Test
    fun `suspendCredential sets the bit on a suspension-purpose list`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.SUSPENSION
        )
        manager.assignCredentialIndex("cred-suspend", statusListId)
        manager.suspendCredential("cred-suspend", statusListId)

        val status = manager.checkStatusByCredentialId("cred-suspend", statusListId)
        assertTrue(status.suspended, "Should be suspended")
        assertFalse(status.revoked, "Should not be revoked")
    }

    // -------------------------------------------------------------------------
    // Stored bits_per_entry
    // -------------------------------------------------------------------------

    @Test
    fun `status checks use the stored bits_per_entry not the reading manager's constructor value`() = runBlocking {
        // Manager A creates and writes a 2-bits-per-entry list (entry n: bit 2n = revoked,
        // bit 2n+1 = suspended).
        val managerA = BitstringStatusListManagerFactory.create(
            dataSource = dataSource,
            kms = kms,
            issuerDid = issuerDid,
            bitsPerEntry = 2
        )
        val statusListId = managerA.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        managerA.assignCredentialIndex("mp-cred-0", statusListId)
        managerA.assignCredentialIndex("mp-cred-1", statusListId)
        managerA.suspendCredential("mp-cred-0", statusListId) // sets bit 1
        managerA.revokeCredential("mp-cred-1", statusListId) // sets bit 2

        // Manager B was constructed with bitsPerEntry = 1. If it used its constructor
        // value it would read entry 1 as raw bit 1 (the suspension bit of entry 0) and
        // report wrong statuses; it must use the persisted bits_per_entry = 2 instead.
        val managerB = manager
        val entry0 = managerB.checkStatusByIndex(statusListId, 0)
        assertFalse(entry0.revoked, "Entry 0 was never revoked")
        assertTrue(entry0.suspended, "Entry 0 was suspended")

        val entry1 = managerB.checkStatusByIndex(statusListId, 1)
        assertTrue(entry1.revoked, "Entry 1 was revoked")
        assertFalse(entry1.suspended, "Entry 1 was never suspended")

        // Write path must honor the stored bits_per_entry too.
        assertTrue(managerB.unrevokeCredential("mp-cred-1", statusListId))
        val entry1After = managerB.checkStatusByIndex(statusListId, 1)
        assertFalse(entry1After.revoked, "Entry 1 must be un-revoked")
        val entry0After = managerB.checkStatusByIndex(statusListId, 0)
        assertTrue(entry0After.suspended, "Entry 0 suspension must be untouched by the un-revocation")
    }

    // -------------------------------------------------------------------------
    // Status list VC signing
    // -------------------------------------------------------------------------

    @Test
    fun `buildStatusListVc without configured signer fails closed with ConfigException`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        val ex = assertFailsWith<ConfigException> {
            manager.buildStatusListVc(statusListId)
        }
        assertTrue(
            ex.message.contains("proof engine", ignoreCase = true),
            "Error must clearly explain the missing signer configuration: ${ex.message}"
        )
    }

    @Test
    fun `buildStatusListVc signs via the configured proof engine with the issuer's key`() = runBlocking {
        val engine = RecordingProofEngine()
        val signing = signingManager(engine)

        val statusListId = signing.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        val vc = signing.buildStatusListVc(statusListId)

        assertTrue(
            vc.type.any { it.value == "VerifiableCredential" },
            "VC must include VerifiableCredential type"
        )
        assertTrue(
            vc.type.any { it.value == "BitstringStatusListCredential" },
            "VC must include BitstringStatusListCredential type"
        )
        assertEquals(issuerDid, vc.issuer.id.value, "Issuer DID must match")

        val encodedList = (vc.credentialSubject.claims["encodedList"] as? JsonPrimitive)?.content
        assertNotNull(encodedList, "credentialSubject must contain encodedList")
        assertTrue(encodedList.startsWith("u"), "encodedList claim must carry the multibase prefix")

        val typeClaim = vc.credentialSubject.claims["type"]
        assertNotNull(typeClaim, "credentialSubject must contain type")

        val proof = vc.proof as? CredentialProof.LinkedDataProof
        assertNotNull(proof, "Signed VC must have a LinkedDataProof")
        assertEquals(
            issuerKeyId.value,
            proof.verificationMethod,
            "VC must be signed with the configured issuer key, never a fabricated throwaway key"
        )
        assertEquals(
            issuerKeyId,
            engine.lastRequest?.issuerKeyId,
            "Manager must delegate signing to the proof engine with the configured key"
        )
    }

    @Test
    fun `buildStatusListVc refuses to sign when the configured key belongs to a different DID`() = runBlocking {
        val engine = RecordingProofEngine()
        val signing = signingManager(
            engine,
            keyId = VerificationMethodId(Did("did:key:z6MkSomeOtherIssuer"), KeyId("#key-1"))
        )
        val statusListId = signing.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        val ex = assertFailsWith<ConfigException> {
            signing.buildStatusListVc(statusListId)
        }
        assertTrue(ex.message.contains("refusing to sign"), "Mismatch must be explained: ${ex.message}")
    }

    @Test
    fun `buildStatusListVc rejects a signed VC whose issuer does not match the status list issuer`() = runBlocking {
        // Misbehaving engine: ignores the requested issuer and signs as someone else.
        val engine = RecordingProofEngine(
            issuerOverride = Issuer.from("did:key:z6MkRogueIssuer")
        )
        val signing = signingManager(engine)
        val statusListId = signing.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        val ex = assertFailsWith<TrustWeaveException.InvalidState> {
            signing.buildStatusListVc(statusListId)
        }
        assertEquals("STATUS_LIST_VC_ISSUER_MISMATCH", ex.code)
    }

    // -------------------------------------------------------------------------
    // Statistics & metadata
    // -------------------------------------------------------------------------

    @Test
    fun `getStatusListStatistics reflects revokedCount correctly`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION,
            size = 128
        )
        manager.assignCredentialIndex("stats-1", statusListId)
        manager.assignCredentialIndex("stats-2", statusListId)
        manager.revokeCredential("stats-1", statusListId)

        val stats = manager.getStatusListStatistics(statusListId)
        assertNotNull(stats)
        assertEquals(1, stats.revokedCount, "Only one credential was revoked")
        assertEquals(2, stats.usedIndices, "Two indices were assigned")
    }

    // -------------------------------------------------------------------------
    // List & delete
    // -------------------------------------------------------------------------

    @Test
    fun `listStatusLists filters by issuerDid`() = runBlocking {
        val did1 = "did:key:issuer-list-1"
        val did2 = "did:key:issuer-list-2"
        manager.createStatusList(issuerDid = did1, purpose = StatusPurpose.REVOCATION)
        manager.createStatusList(issuerDid = did2, purpose = StatusPurpose.REVOCATION)

        val forDid1 = manager.listStatusLists(issuerDid = did1)
        assertEquals(1, forDid1.size, "Expected exactly 1 status list for did1")
        assertEquals(did1, forDid1[0].issuerDid)
    }

    @Test
    fun `deleteStatusList removes the status list`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        assertTrue(manager.deleteStatusList(statusListId), "deleteStatusList must return true")
        assertEquals(null, manager.getStatusList(statusListId), "Status list must not exist after deletion")
    }

    // -------------------------------------------------------------------------
    // expandStatusList
    // -------------------------------------------------------------------------

    @Test
    fun `expandStatusList increases the recorded size`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION,
            size = 64
        )
        manager.expandStatusList(statusListId, additionalSize = 64)

        val metadata = manager.getStatusList(statusListId)
        assertNotNull(metadata)
        assertEquals(
            BitstringStatusListManager.MIN_STATUS_LIST_SIZE_BITS + 64,
            metadata.size,
            "Expansion must add to the (minimum-coerced) size"
        )
    }
}
