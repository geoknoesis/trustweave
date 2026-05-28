package org.trustweave.revocation.bitstring

import org.trustweave.credential.model.StatusPurpose
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BitstringStatusListManagerTest {

    private lateinit var dataSource: DataSource
    private lateinit var kms: InMemoryKeyManagementService
    private lateinit var manager: BitstringStatusListManager

    private val issuerDid = "did:key:z6MkTestBitstringIssuer"

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

    // -------------------------------------------------------------------------
    // Encoding round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `encodedList is valid gzip-compressed base64url without padding`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION,
            size = 256
        )
        val metadata = manager.getStatusList(statusListId)
        assertNotNull(metadata)

        // Fetch the raw encodedList from the DB via a helper round-trip through assign + check
        val credId = "did:example:credential-encode-test"
        manager.assignCredentialIndex(credId, statusListId)

        // Build VC to read encodedList
        val vc = manager.buildStatusListVc(statusListId)
        val encodedList = vc.credentialSubject.claims["encodedList"]
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        assertNotNull(encodedList, "encodedList claim must be present")

        // Must not contain padding characters
        assertFalse(encodedList.contains('='), "base64url must not have padding")

        // Must be decodable as base64url and then gzip-decompressible
        val gzipped = Base64.getUrlDecoder().decode(encodedList)
        val raw = GZIPInputStream(gzipped.inputStream()).use { it.readBytes() }
        assertTrue(raw.isNotEmpty(), "Decompressed bytes must not be empty")
        assertEquals(32, raw.size, "256 bits = 32 bytes")
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
    // Status list VC
    // -------------------------------------------------------------------------

    @Test
    fun `buildStatusListVc returns VC with correct types and encodedList claim`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )

        val vc = manager.buildStatusListVc(statusListId)

        assertTrue(
            vc.type.any { it.value == "VerifiableCredential" },
            "VC must include VerifiableCredential type"
        )
        assertTrue(
            vc.type.any { it.value == "BitstringStatusListCredential" },
            "VC must include BitstringStatusListCredential type"
        )
        assertEquals(issuerDid, vc.issuer.id.value, "Issuer DID must match")

        val encodedListClaim = vc.credentialSubject.claims["encodedList"]
        assertNotNull(encodedListClaim, "credentialSubject must contain encodedList")

        val typeClaim = vc.credentialSubject.claims["type"]
        assertNotNull(typeClaim, "credentialSubject must contain type")

        assertNotNull(vc.proof, "Signed VC must have a proof")
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
        assertTrue(metadata.size >= 128, "Size must be at least 128 after expansion")
    }
}
