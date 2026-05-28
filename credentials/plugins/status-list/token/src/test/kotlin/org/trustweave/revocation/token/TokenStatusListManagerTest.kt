package org.trustweave.revocation.token

import org.trustweave.credential.model.StatusPurpose
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import java.util.Base64
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TokenStatusListManagerTest {

    private lateinit var dataSource: DataSource
    private lateinit var kms: InMemoryKeyManagementService
    private lateinit var manager: TokenStatusListManager

    private val issuerDid = "did:key:z6MkTestTokenIssuer"
    private val statusListUri = "https://example.com/statuslists/test"

    @BeforeTest
    fun setUp() {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:token_test_${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
            username = "sa"
            password = ""
            maximumPoolSize = 5
        }
        dataSource = HikariDataSource(config)
        kms = InMemoryKeyManagementService()
        manager = TokenStatusListManagerFactory.create(
            dataSource = dataSource,
            kms = kms,
            issuerDid = issuerDid,
            statusListUri = statusListUri,
            bitsPerEntry = 1
        )
    }

    @AfterTest
    fun tearDown() {
        (dataSource as? HikariDataSource)?.close()
    }

    // -------------------------------------------------------------------------
    // JWT structure
    // -------------------------------------------------------------------------

    @Test
    fun `buildStatusListToken returns a three-part JWT string`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        val token = manager.buildStatusListToken(statusListId)
        assertTrue(token.jwt.isNotBlank(), "JWT must not be blank")

        val parts = token.jwt.split(".")
        assertEquals(3, parts.size, "Compact JWT must have exactly 3 dot-separated parts")
    }

    @Test
    fun `buildStatusListToken JWT header contains typ statuslist+jwt`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        val token = manager.buildStatusListToken(statusListId)

        // Decode header manually (base64url, no padding)
        val headerJson = String(Base64.getUrlDecoder().decode(token.jwt.substringBefore(".")), Charsets.UTF_8)
        assertTrue(headerJson.contains("statuslist+jwt"), "Header 'typ' must be 'statuslist+jwt'")
    }

    @Test
    fun `buildStatusListToken JWT payload contains required claims`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        val token = manager.buildStatusListToken(statusListId)

        val parts = token.jwt.split(".")
        val payloadJson = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)

        assertTrue(payloadJson.contains("\"iss\""), "Payload must contain 'iss'")
        assertTrue(payloadJson.contains("\"sub\""), "Payload must contain 'sub'")
        assertTrue(payloadJson.contains("\"iat\""), "Payload must contain 'iat'")
        assertTrue(payloadJson.contains("\"status_list\""), "Payload must contain 'status_list'")
        assertTrue(payloadJson.contains("\"bits\""), "status_list must contain 'bits'")
        assertTrue(payloadJson.contains("\"lst\""), "status_list must contain 'lst'")
    }

    @Test
    fun `buildStatusListToken JWT payload issuer matches issuerDid`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        val token = manager.buildStatusListToken(statusListId)

        val parts = token.jwt.split(".")
        val payloadJson = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        assertTrue(payloadJson.contains(issuerDid), "Payload 'iss' must match issuerDid")
    }

    @Test
    fun `buildStatusListToken status_list lst is valid base64url without padding`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION,
            size = 8
        )
        val token = manager.buildStatusListToken(statusListId)

        val parts = token.jwt.split(".")
        val payloadJson = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)

        val lstStart = payloadJson.indexOf("\"lst\":\"") + "\"lst\":\"".length
        val lstEnd = payloadJson.indexOf("\"", lstStart)
        val lst = payloadJson.substring(lstStart, lstEnd)

        assertFalse(lst.contains('='), "lst must not contain padding")
        // Must be decodable
        val decoded = Base64.getUrlDecoder().decode(lst)
        assertTrue(decoded.isNotEmpty(), "Decoded lst must not be empty")
        assertEquals(1, decoded.size, "8-entry list at 1 bit/entry = 1 byte")
    }

    @Test
    fun `buildStatusListToken with ttlSeconds includes exp claim`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        val token = manager.buildStatusListToken(statusListId, ttlSeconds = 3600L)

        val parts = token.jwt.split(".")
        val payloadJson = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        assertTrue(payloadJson.contains("\"exp\""), "Payload must contain 'exp' when ttlSeconds is set")
        assertTrue(payloadJson.contains("\"ttl\""), "Payload must contain 'ttl' when ttlSeconds is set")
    }

    // -------------------------------------------------------------------------
    // Bit-packing round-trip (1 bit per entry)
    // -------------------------------------------------------------------------

    @Test
    fun `revokeCredential sets the correct bit in the status array`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION,
            size = 8
        )
        manager.assignCredentialIndex("cred-bit-0", statusListId) // index 0
        manager.assignCredentialIndex("cred-bit-1", statusListId) // index 1

        manager.revokeCredential("cred-bit-0", statusListId)

        val token = manager.buildStatusListToken(statusListId)
        val parts = token.jwt.split(".")
        val payloadJson = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)

        val lstStart = payloadJson.indexOf("\"lst\":\"") + "\"lst\":\"".length
        val lstEnd = payloadJson.indexOf("\"", lstStart)
        val arr = Base64.getUrlDecoder().decode(payloadJson.substring(lstStart, lstEnd))

        // Bit 0 of byte 0 must be set
        assertTrue((arr[0].toInt() and 0x01) != 0, "Bit 0 must be set for revoked credential at index 0")
        // Bit 1 of byte 0 must be clear
        assertTrue((arr[0].toInt() and 0x02) == 0, "Bit 1 must be clear for non-revoked credential at index 1")
    }

    @Test
    fun `unrevokeCredential clears the bit`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        manager.assignCredentialIndex("cred-unrevoke-tok", statusListId)
        manager.revokeCredential("cred-unrevoke-tok", statusListId)
        manager.unrevokeCredential("cred-unrevoke-tok", statusListId)

        val status = manager.checkStatusByCredentialId("cred-unrevoke-tok", statusListId)
        assertFalse(status.revoked, "Should not be revoked after unrevokeCredential")
    }

    // -------------------------------------------------------------------------
    // Bit-packing round-trip (2 bits per entry)
    // -------------------------------------------------------------------------

    @Test
    fun `2-bit mode revoke and suspend set independent bits`() = runBlocking {
        val manager2 = TokenStatusListManagerFactory.create(
            dataSource = dataSource,
            kms = kms,
            issuerDid = issuerDid,
            statusListUri = statusListUri,
            bitsPerEntry = 2
        )

        val statusListId = manager2.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION,
            size = 4
        )
        manager2.assignCredentialIndex("dual-cred", statusListId)
        manager2.revokeCredential("dual-cred", statusListId)
        manager2.suspendCredential("dual-cred", statusListId)

        val status = manager2.checkStatusByCredentialId("dual-cred", statusListId)
        assertTrue(status.revoked, "Should be revoked")
        assertTrue(status.suspended, "Should also be suspended in 2-bit mode")
    }

    // -------------------------------------------------------------------------
    // Revocation check
    // -------------------------------------------------------------------------

    @Test
    fun `checkStatusByCredentialId returns not revoked for fresh credential`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        manager.assignCredentialIndex("fresh-cred", statusListId)
        val status = manager.checkStatusByCredentialId("fresh-cred", statusListId)
        assertFalse(status.revoked, "Fresh credential must not be revoked")
        assertFalse(status.suspended, "Fresh credential must not be suspended")
    }

    @Test
    fun `revokeCredentials batch sets all entries`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        val ids = listOf("batch-tok-1", "batch-tok-2", "batch-tok-3")
        ids.forEach { manager.assignCredentialIndex(it, statusListId) }

        val results = manager.revokeCredentials(ids, statusListId)
        ids.forEach { id ->
            assertEquals(true, results[id], "revokeCredentials result for $id must be true")
            assertTrue(
                manager.checkStatusByCredentialId(id, statusListId).revoked,
                "$id must be revoked"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Statistics & metadata
    // -------------------------------------------------------------------------

    @Test
    fun `getStatusListStatistics reflects usedIndices and revokedCount`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION,
            size = 16
        )
        manager.assignCredentialIndex("stat-a", statusListId)
        manager.assignCredentialIndex("stat-b", statusListId)
        manager.revokeCredential("stat-a", statusListId)

        val stats = manager.getStatusListStatistics(statusListId)
        assertNotNull(stats)
        assertEquals(2, stats.usedIndices)
        assertEquals(1, stats.revokedCount)
    }

    // -------------------------------------------------------------------------
    // Delete & expand
    // -------------------------------------------------------------------------

    @Test
    fun `deleteStatusList returns true and removes the entry`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION
        )
        assertTrue(manager.deleteStatusList(statusListId))
        assertEquals(null, manager.getStatusList(statusListId))
    }

    @Test
    fun `expandStatusList increases recorded size`() = runBlocking {
        val statusListId = manager.createStatusList(
            issuerDid = issuerDid,
            purpose = StatusPurpose.REVOCATION,
            size = 8
        )
        manager.expandStatusList(statusListId, additionalSize = 8)
        val metadata = manager.getStatusList(statusListId)
        assertNotNull(metadata)
        assertTrue(metadata.size >= 16, "Size must be at least 16 after expansion")
    }
}
