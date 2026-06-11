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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
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

    private fun encodeRawBitstring(bytes: ByteArray): String {
        val gzipped = ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { gzip -> gzip.write(bytes) }
            baos.toByteArray()
        }
        return "u" + Base64.getUrlEncoder().withoutPadding().encodeToString(gzipped)
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

    @Test
    fun `expandStatusList applied twice accumulates size`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        manager.expandStatusList(statusListId, additionalSize = 64)
        manager.expandStatusList(statusListId, additionalSize = 64)

        val metadata = manager.getStatusList(statusListId)
        assertNotNull(metadata)
        assertEquals(
            BitstringStatusListManager.MIN_STATUS_LIST_SIZE_BITS + 128,
            metadata.size,
            "Each expansion must build on the size committed by the previous one"
        )
    }

    @Test
    fun `expandStatusList preserves the multibase prefix MSB-first bit order and existing bits`() = runBlocking {
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

        manager.expandStatusList(statusListId, additionalSize = 64)

        val encodedList = readEncodedList(statusListId)
        assertTrue(encodedList.startsWith("u"), "Expanded encodedList must keep the multibase 'u' prefix")
        assertFalse(encodedList.contains('='), "Expanded encodedList must stay base64url without padding")

        val raw = decodeRawBitstring(encodedList)
        assertEquals(
            (BitstringStatusListManager.MIN_STATUS_LIST_SIZE_BITS + 64) / 8,
            raw.size,
            "Expanded bitstring must cover the new size"
        )
        assertEquals(
            0x81.toByte(),
            raw[0],
            "Expansion must keep MSB-first packing: index 0 = 0x80, index 7 = 0x01"
        )
        assertEquals(0x80.toByte(), raw[1], "Index 8 must still be the left-most bit of byte 1")

        // Round-trip through the read path after expansion
        assertTrue(manager.checkStatusByIndex(statusListId, 0).revoked)
        assertTrue(manager.checkStatusByIndex(statusListId, 7).revoked)
        assertTrue(manager.checkStatusByIndex(statusListId, 8).revoked)
        assertFalse(manager.checkStatusByIndex(statusListId, 9).revoked)
    }

    // -------------------------------------------------------------------------
    // Concurrency: lost-update protection (row lock + in-transaction re-read)
    // -------------------------------------------------------------------------

    /**
     * Holds the status list row lock (the same `SELECT ... FOR UPDATE` the manager's
     * write paths take), runs [operation] concurrently, then — while the lock is still
     * held — revokes index 0 directly and commits, releasing the lock.
     *
     * A correct implementation re-reads `encoded_list` INSIDE its write transaction
     * under the row lock, so it must observe the committed index-0 revocation no matter
     * when it started. The pre-fix implementations read a snapshot before their write
     * transaction and would overwrite (lose) the index-0 bit.
     */
    private suspend fun withRowLockedAndIndexZeroRevoked(
        statusListId: StatusListId,
        operation: suspend () -> Unit
    ): Unit = coroutineScope {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val lockedEncodedList = conn.prepareStatement(
                    "SELECT encoded_list FROM bitstring_status_lists WHERE id = ? FOR UPDATE"
                ).apply {
                    setString(1, statusListId.toString())
                }.executeQuery().let { rs ->
                    check(rs.next()) { "Status list not found: $statusListId" }
                    rs.getString("encoded_list")
                }

                // Start the manager operation while the lock is held; it must block on
                // its locked re-read instead of decoding a stale snapshot.
                val concurrentOperation = async(Dispatchers.IO) { operation() }

                // Give a (broken) unlocked implementation time to take its stale snapshot.
                delay(250)

                // Concurrent writer revokes index 0 (MSB-first: left-most bit of byte 0)
                // and commits, releasing the row lock.
                val raw = decodeRawBitstring(lockedEncodedList)
                raw[0] = (raw[0].toInt() or 0x80).toByte()
                conn.prepareStatement(
                    "UPDATE bitstring_status_lists SET encoded_list = ? WHERE id = ?"
                ).apply {
                    setString(1, encodeRawBitstring(raw))
                    setString(2, statusListId.toString())
                }.executeUpdate()
                conn.commit()

                concurrentOperation.await()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }

    @Test
    fun `updateStatusListBatch does not lose an interleaved concurrent update`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )

        withRowLockedAndIndexZeroRevoked(statusListId) {
            manager.updateStatusListBatch(
                statusListId,
                listOf(StatusUpdate(index = 1, revoked = true))
            )
        }

        assertTrue(
            manager.checkStatusByIndex(statusListId, 0).revoked,
            "The interleaved index-0 revocation must not be overwritten by the batch write"
        )
        assertTrue(
            manager.checkStatusByIndex(statusListId, 1).revoked,
            "The batch update itself must be applied"
        )
    }

    @Test
    fun `expandStatusList does not lose an interleaved concurrent update`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )

        withRowLockedAndIndexZeroRevoked(statusListId) {
            manager.expandStatusList(statusListId, additionalSize = 64)
        }

        assertTrue(
            manager.checkStatusByIndex(statusListId, 0).revoked,
            "The interleaved index-0 revocation must survive the expansion"
        )
        val metadata = manager.getStatusList(statusListId)
        assertNotNull(metadata)
        assertEquals(
            BitstringStatusListManager.MIN_STATUS_LIST_SIZE_BITS + 64,
            metadata.size,
            "Expansion must still grow the size"
        )
        assertTrue(
            readEncodedList(statusListId).startsWith("u"),
            "Expanded encodedList must keep the multibase 'u' prefix"
        )
    }

    // -------------------------------------------------------------------------
    // Concurrency: stale-size truncation protection (locked size re-read)
    // -------------------------------------------------------------------------

    private fun readSize(statusListId: StatusListId): Int =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT size FROM bitstring_status_lists WHERE id = ?"
            ).apply {
                setString(1, statusListId.toString())
            }.executeQuery().let { rs ->
                check(rs.next()) { "Status list not found: $statusListId" }
                rs.getInt("size")
            }
        }

    /**
     * Holds the status list row lock (the same `SELECT ... FOR UPDATE` the manager's
     * write paths take), runs [operation] concurrently, then — while the lock is still
     * held — expands the list by 64 bits, revokes the FIRST TAIL bit (index = old size)
     * directly, commits both `encoded_list` and `size`, and releases the lock.
     *
     * A correct implementation re-reads `size` together with `encoded_list` UNDER the
     * row lock and re-encodes at that locked size, so the expanded tail (including its
     * revoked bit) survives. A broken implementation re-encoding at the stale
     * pre-transaction size would TRUNCATE the tail: the size column would say S+64
     * while `encoded_list` covers only S bits, and the tail revocation would read
     * back fail-open as "not revoked".
     *
     * @return the index of the tail bit revoked by the concurrent expansion
     */
    private suspend fun withRowLockedAndTailGrownAndRevoked(
        statusListId: StatusListId,
        operation: suspend () -> Unit
    ): Int = coroutineScope {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val rs = conn.prepareStatement(
                    "SELECT encoded_list, size FROM bitstring_status_lists WHERE id = ? FOR UPDATE"
                ).apply {
                    setString(1, statusListId.toString())
                }.executeQuery()
                check(rs.next()) { "Status list not found: $statusListId" }
                val lockedEncodedList = rs.getString("encoded_list")
                val lockedSize = rs.getInt("size")

                // Start the manager operation while the lock is held; it must block on
                // its locked re-read instead of working from a stale snapshot.
                val concurrentOperation = async(Dispatchers.IO) { operation() }

                // Give a (broken) stale-size implementation time to take its
                // pre-transaction snapshot of the row.
                delay(250)

                // Concurrent expansion: grow by 64 bits, revoke the first tail bit
                // (index = old size; MSB-first packing), commit and release the lock.
                val newSize = lockedSize + 64
                val grown = decodeRawBitstring(lockedEncodedList).copyOf((newSize + 7) / 8)
                grown[lockedSize / 8] =
                    (grown[lockedSize / 8].toInt() or (1 shl (7 - (lockedSize % 8)))).toByte()
                conn.prepareStatement(
                    "UPDATE bitstring_status_lists SET encoded_list = ?, size = ? WHERE id = ?"
                ).apply {
                    setString(1, encodeRawBitstring(grown))
                    setInt(2, newSize)
                    setString(3, statusListId.toString())
                }.executeUpdate()
                conn.commit()

                concurrentOperation.await()
                lockedSize
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }

    private suspend fun assertTailSurvived(
        statusListId: StatusListId,
        tailIndex: Int,
        expectedSizeBits: Int
    ) {
        assertEquals(
            expectedSizeBits,
            readSize(statusListId),
            "The concurrently expanded size must not be rolled back"
        )
        assertEquals(
            (expectedSizeBits + 7) / 8,
            decodeRawBitstring(readEncodedList(statusListId)).size,
            "Re-encode must cover the expanded size, not truncate to the stale pre-transaction size"
        )
        assertTrue(
            manager.checkStatusByIndex(statusListId, tailIndex).revoked,
            "The tail bit revoked by the concurrent expansion must survive the re-encode " +
                "(a truncated list would read it back fail-open as not revoked)"
        )
    }

    @Test
    fun `updateStatusListBatch re-encodes at the locked size and does not truncate a concurrent expansion`() =
        runBlocking {
            val statusListId = manager.createStatusList(
                issuerDid = issuerDid,
                purpose = StatusPurpose.REVOCATION
            )

            val tailIndex = withRowLockedAndTailGrownAndRevoked(statusListId) {
                manager.updateStatusListBatch(
                    statusListId,
                    listOf(StatusUpdate(index = 1, revoked = true))
                )
            }

            assertTrue(
                manager.checkStatusByIndex(statusListId, 1).revoked,
                "The batch update itself must be applied"
            )
            assertTailSurvived(
                statusListId,
                tailIndex,
                expectedSizeBits = BitstringStatusListManager.MIN_STATUS_LIST_SIZE_BITS + 64
            )
        }

    @Test
    fun `revokeCredential re-encodes at the locked size and does not truncate a concurrent expansion`() =
        runBlocking {
            val statusListId = manager.createStatusList(
                issuerDid = issuerDid,
                purpose = StatusPurpose.REVOCATION
            )
            manager.assignCredentialIndex("stale-size-cred", statusListId)

            val tailIndex = withRowLockedAndTailGrownAndRevoked(statusListId) {
                manager.revokeCredential("stale-size-cred", statusListId)
            }

            assertTrue(
                manager.checkStatusByCredentialId("stale-size-cred", statusListId).revoked,
                "The revocation itself must be applied"
            )
            assertTailSurvived(
                statusListId,
                tailIndex,
                expectedSizeBits = BitstringStatusListManager.MIN_STATUS_LIST_SIZE_BITS + 64
            )
        }

    @Test
    fun `revokeCredentials re-encodes at the locked size and does not truncate a concurrent expansion`() =
        runBlocking {
            val statusListId = manager.createStatusList(
                issuerDid = issuerDid,
                purpose = StatusPurpose.REVOCATION
            )
            manager.assignCredentialIndex("stale-size-batch", statusListId)

            val tailIndex = withRowLockedAndTailGrownAndRevoked(statusListId) {
                manager.revokeCredentials(listOf("stale-size-batch"), statusListId)
            }

            assertTrue(
                manager.checkStatusByCredentialId("stale-size-batch", statusListId).revoked,
                "The batch revocation itself must be applied"
            )
            assertTailSurvived(
                statusListId,
                tailIndex,
                expectedSizeBits = BitstringStatusListManager.MIN_STATUS_LIST_SIZE_BITS + 64
            )
        }

    @Test
    fun `expandStatusList builds on the locked size and does not truncate a concurrent expansion`() =
        runBlocking {
            val statusListId = manager.createStatusList(
                issuerDid = issuerDid,
                purpose = StatusPurpose.REVOCATION
            )

            val tailIndex = withRowLockedAndTailGrownAndRevoked(statusListId) {
                manager.expandStatusList(statusListId, additionalSize = 64)
            }

            // The concurrent +64 expansion AND this method's own +64 must both apply.
            assertTailSurvived(
                statusListId,
                tailIndex,
                expectedSizeBits = BitstringStatusListManager.MIN_STATUS_LIST_SIZE_BITS + 128
            )
        }
}
