package io.geoknoesis.vericore.testkit.credential

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.wallet.CredentialFilter
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for InMemoryWallet.
 * Tests all conditional branches in wallet operations.
 */
class InMemoryWalletBranchCoverageTest {

    private lateinit var wallet: InMemoryWallet

    @BeforeEach
    fun setup() {
        wallet = InMemoryWallet("test-wallet")
    }

    // ========== store() Branch Coverage ==========

    @Test
    fun `test branch store with credential id`() = runBlocking {
        val credential = createTestCredential(id = "cred-123")
        
        val result = wallet.store(credential)
        
        assertEquals("cred-123", result)
        assertNotNull(wallet.get("cred-123"))
    }

    @Test
    fun `test branch store without credential id generates UUID`() = runBlocking {
        val credential = createTestCredential(id = null)
        
        val result = wallet.store(credential)
        
        assertNotNull(result)
        assertTrue(result.isNotBlank())
        assertNotNull(wallet.get(result))
    }

    @Test
    fun `test branch store creates metadata if not exists`() = runBlocking {
        val credential = createTestCredential(id = "cred-new")
        
        wallet.store(credential)
        
        val metadata = wallet.getMetadata("cred-new")
        assertNotNull(metadata)
    }

    // ========== get() Branch Coverage ==========

    @Test
    fun `test branch get from credentials`() = runBlocking {
        val credential = createTestCredential(id = "cred-1")
        wallet.store(credential)
        
        val result = wallet.get("cred-1")
        
        assertNotNull(result)
        assertEquals("cred-1", result?.id)
    }

    @Test
    fun `test branch get from archived credentials`() = runBlocking {
        val credential = createTestCredential(id = "cred-archived")
        wallet.store(credential)
        wallet.archive("cred-archived")
        
        val result = wallet.get("cred-archived")
        
        assertNotNull(result)
        assertEquals("cred-archived", result?.id)
    }

    @Test
    fun `test branch get returns null for nonexistent`() = runBlocking {
        val result = wallet.get("nonexistent")
        
        assertNull(result)
    }

    // ========== list() Branch Coverage ==========

    @Test
    fun `test branch list with null filter`() = runBlocking {
        val cred1 = createTestCredential(id = "cred-1")
        val cred2 = createTestCredential(id = "cred-2")
        wallet.store(cred1)
        wallet.store(cred2)
        
        val result = wallet.list(null)
        
        assertEquals(2, result.size)
    }

    @Test
    fun `test branch list with issuer filter matching`() = runBlocking {
        val cred1 = createTestCredential(id = "cred-1", issuerDid = "did:key:issuer1")
        val cred2 = createTestCredential(id = "cred-2", issuerDid = "did:key:issuer2")
        wallet.store(cred1)
        wallet.store(cred2)
        
        val result = wallet.list(CredentialFilter(issuer = "did:key:issuer1"))
        
        assertEquals(1, result.size)
        assertEquals("cred-1", result.first().id)
    }

    @Test
    fun `test branch list with issuer filter not matching`() = runBlocking {
        val cred1 = createTestCredential(id = "cred-1", issuerDid = "did:key:issuer1")
        wallet.store(cred1)
        
        val result = wallet.list(CredentialFilter(issuer = "did:key:different"))
        
        assertTrue(result.isEmpty())
    }

    @Test
    fun `test branch list with type filter matching`() = runBlocking {
        val cred1 = createTestCredential(id = "cred-1", types = listOf("VerifiableCredential", "PersonCredential"))
        val cred2 = createTestCredential(id = "cred-2", types = listOf("VerifiableCredential", "EmailCredential"))
        wallet.store(cred1)
        wallet.store(cred2)
        
        val result = wallet.list(CredentialFilter(type = listOf("PersonCredential")))
        
        assertEquals(1, result.size)
        assertEquals("cred-1", result.first().id)
    }

    @Test
    fun `test branch list with subjectId filter matching`() = runBlocking {
        val cred1 = createTestCredential(
            id = "cred-1",
            subject = buildJsonObject { put("id", "did:key:subject1") }
        )
        val cred2 = createTestCredential(
            id = "cred-2",
            subject = buildJsonObject { put("id", "did:key:subject2") }
        )
        wallet.store(cred1)
        wallet.store(cred2)
        
        val result = wallet.list(CredentialFilter(subjectId = "did:key:subject1"))
        
        assertEquals(1, result.size)
    }

    @Test
    fun `test branch list with expired filter true`() = runBlocking {
        val cred1 = createTestCredential(
            id = "cred-1",
            expirationDate = java.time.Instant.now().minusSeconds(86400).toString()
        )
        val cred2 = createTestCredential(
            id = "cred-2",
            expirationDate = java.time.Instant.now().plusSeconds(86400).toString()
        )
        wallet.store(cred1)
        wallet.store(cred2)
        
        val result = wallet.list(CredentialFilter(expired = true))
        
        assertEquals(1, result.size)
        assertEquals("cred-1", result.first().id)
    }

    @Test
    fun `test branch list with expired filter false`() = runBlocking {
        val cred1 = createTestCredential(
            id = "cred-1",
            expirationDate = java.time.Instant.now().minusSeconds(86400).toString()
        )
        val cred2 = createTestCredential(
            id = "cred-2",
            expirationDate = java.time.Instant.now().plusSeconds(86400).toString()
        )
        wallet.store(cred1)
        wallet.store(cred2)
        
        val result = wallet.list(CredentialFilter(expired = false))
        
        assertEquals(1, result.size)
        assertEquals("cred-2", result.first().id)
    }

    @Test
    fun `test branch list with expired filter null and no expiration date`() = runBlocking {
        val cred1 = createTestCredential(id = "cred-1", expirationDate = null)
        wallet.store(cred1)
        
        val result = wallet.list(CredentialFilter(expired = false))
        
        assertEquals(1, result.size) // No expiration date means not expired
    }

    @Test
    fun `test branch list with revoked filter true`() = runBlocking {
        val cred1 = createTestCredential(
            id = "cred-1",
            credentialStatus = io.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry"
            )
        )
        val cred2 = createTestCredential(id = "cred-2", credentialStatus = null)
        wallet.store(cred1)
        wallet.store(cred2)
        
        val result = wallet.list(CredentialFilter(revoked = true))
        
        assertEquals(1, result.size)
        assertEquals("cred-1", result.first().id)
    }

    @Test
    fun `test branch list with revoked filter false`() = runBlocking {
        val cred1 = createTestCredential(
            id = "cred-1",
            credentialStatus = io.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry"
            )
        )
        val cred2 = createTestCredential(id = "cred-2", credentialStatus = null)
        wallet.store(cred1)
        wallet.store(cred2)
        
        val result = wallet.list(CredentialFilter(revoked = false))
        
        assertEquals(1, result.size)
        assertEquals("cred-2", result.first().id)
    }

    // ========== delete() Branch Coverage ==========

    @Test
    fun `test branch delete from credentials`() = runBlocking {
        val credential = createTestCredential(id = "cred-1")
        wallet.store(credential)
        
        val result = wallet.delete("cred-1")
        
        assertTrue(result)
        assertNull(wallet.get("cred-1"))
    }

    @Test
    fun `test branch delete from archived credentials`() = runBlocking {
        val credential = createTestCredential(id = "cred-archived")
        wallet.store(credential)
        wallet.archive("cred-archived")
        
        val result = wallet.delete("cred-archived")
        
        assertTrue(result)
        assertNull(wallet.get("cred-archived"))
    }

    @Test
    fun `test branch delete returns false for nonexistent`() = runBlocking {
        val result = wallet.delete("nonexistent")
        
        assertFalse(result)
    }

    // ========== getCollection() Branch Coverage ==========

    @Test
    fun `test branch getCollection returns null for nonexistent`() = runBlocking {
        val result = wallet.getCollection("nonexistent")
        
        assertNull(result)
    }

    @Test
    fun `test branch getCollection calculates credentialCount dynamically`() = runBlocking {
        val collectionId = wallet.createCollection("Test Collection", "Description")
        val cred1 = createTestCredential(id = "cred-1")
        val cred2 = createTestCredential(id = "cred-2")
        wallet.store(cred1)
        wallet.store(cred2)
        wallet.addToCollection("cred-1", collectionId)
        wallet.addToCollection("cred-2", collectionId)
        
        val collection = wallet.getCollection(collectionId)
        
        assertNotNull(collection)
        assertEquals(2, collection?.credentialCount)
    }

    // ========== deleteCollection() Branch Coverage ==========

    @Test
    fun `test branch deleteCollection removes collection`() = runBlocking {
        val collectionId = wallet.createCollection("Test Collection", "Description")
        
        val result = wallet.deleteCollection(collectionId)
        
        assertTrue(result)
        assertNull(wallet.getCollection(collectionId))
    }

    @Test
    fun `test branch deleteCollection returns false for nonexistent`() = runBlocking {
        val result = wallet.deleteCollection("nonexistent")
        
        assertFalse(result)
    }

    // ========== addToCollection() Branch Coverage ==========

    @Test
    fun `test branch addToCollection returns false when credential not found`() = runBlocking {
        val collectionId = wallet.createCollection("Test Collection", "Description")
        
        val result = wallet.addToCollection("nonexistent", collectionId)
        
        assertFalse(result)
    }

    @Test
    fun `test branch addToCollection returns false when collection not found`() = runBlocking {
        val credential = createTestCredential(id = "cred-1")
        wallet.store(credential)
        
        val result = wallet.addToCollection("cred-1", "nonexistent")
        
        assertFalse(result)
    }

    // ========== getCredentialsInCollection() Branch Coverage ==========

    @Test
    fun `test branch getCredentialsInCollection returns empty for nonexistent collection`() = runBlocking {
        val result = wallet.getCredentialsInCollection("nonexistent")
        
        assertTrue(result.isEmpty())
    }

    @Test
    fun `test branch getCredentialsInCollection gets from both credentials and archived`() = runBlocking {
        val collectionId = wallet.createCollection("Test Collection", "Description")
        val cred1 = createTestCredential(id = "cred-1")
        val cred2 = createTestCredential(id = "cred-2")
        wallet.store(cred1)
        wallet.store(cred2)
        wallet.addToCollection("cred-1", collectionId)
        wallet.addToCollection("cred-2", collectionId)
        wallet.archive("cred-2")
        
        val result = wallet.getCredentialsInCollection(collectionId)
        
        assertEquals(2, result.size)
    }

    // ========== tagCredential() Branch Coverage ==========

    @Test
    fun `test branch tagCredential returns false when credential not found`() = runBlocking {
        val result = wallet.tagCredential("nonexistent", setOf("tag1"))
        
        assertFalse(result)
    }

    // ========== getTags() Branch Coverage ==========

    @Test
    fun `test branch getTags returns empty set when credential not found`() = runBlocking {
        val result = wallet.getTags("nonexistent")
        
        assertTrue(result.isEmpty())
    }

    // ========== Helper Methods ==========

    private fun createTestCredential(
        id: String? = "cred-${System.currentTimeMillis()}",
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        issuerDid: String = "did:key:issuer",
        subject: JsonObject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        },
        issuanceDate: String = java.time.Instant.now().toString(),
        expirationDate: String? = null,
        credentialStatus: io.geoknoesis.vericore.credential.models.CredentialStatus? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            expirationDate = expirationDate,
            credentialStatus = credentialStatus
        )
    }
}

