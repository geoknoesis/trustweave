package io.geoknoesis.vericore.testkit.credential

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.wallet.CredentialFilter
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Branch coverage tests for BasicWallet.
 */
class BasicWalletBranchCoverageTest {

    private lateinit var wallet: BasicWallet

    @BeforeEach
    fun setup() {
        wallet = BasicWallet()
    }

    @Test
    fun `test BasicWallet store with credential ID`() = runBlocking {
        val credential = createTestCredential(id = "custom-id-123")
        val id = wallet.store(credential)
        
        assertEquals("custom-id-123", id)
        assertNotNull(wallet.get("custom-id-123"))
    }

    @Test
    fun `test BasicWallet store without credential ID generates UUID`() = runBlocking {
        val credential = createTestCredential(id = null)
        val id = wallet.store(credential)
        
        assertNotNull(id)
        assertTrue(id.isNotBlank())
    }

    @Test
    fun `test BasicWallet list with null filter returns all`() = runBlocking {
        val cred1 = createTestCredential(id = "cred-1", issuerDid = "did:key:issuer1")
        val cred2 = createTestCredential(id = "cred-2", issuerDid = "did:key:issuer2")
        wallet.store(cred1)
        wallet.store(cred2)
        
        val all = wallet.list(null)
        
        assertEquals(2, all.size)
    }

    @Test
    fun `test BasicWallet list with issuer filter matches`() = runBlocking {
        val cred1 = createTestCredential(id = "cred-1", issuerDid = "did:key:issuer1")
        val cred2 = createTestCredential(id = "cred-2", issuerDid = "did:key:issuer2")
        wallet.store(cred1)
        wallet.store(cred2)
        
        val filtered = wallet.list(CredentialFilter(issuer = "did:key:issuer1"))
        
        assertEquals(1, filtered.size)
        assertEquals("cred-1", filtered.first().id)
    }

    @Test
    fun `test BasicWallet list with issuer filter does not match`() = runBlocking {
        val cred1 = createTestCredential(id = "cred-1", issuerDid = "did:key:issuer1")
        wallet.store(cred1)
        
        val filtered = wallet.list(CredentialFilter(issuer = "did:key:issuer2"))
        
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `test BasicWallet list with type filter matches`() = runBlocking {
        val cred1 = createTestCredential(id = "cred-1", types = listOf("VerifiableCredential", "PersonCredential"))
        val cred2 = createTestCredential(id = "cred-2", types = listOf("VerifiableCredential", "DegreeCredential"))
        wallet.store(cred1)
        wallet.store(cred2)
        
        val filtered = wallet.list(CredentialFilter(type = listOf("PersonCredential")))
        
        assertEquals(1, filtered.size)
        assertTrue(filtered.first().type.contains("PersonCredential"))
    }

    @Test
    fun `test BasicWallet list with type filter does not match`() = runBlocking {
        val cred1 = createTestCredential(id = "cred-1", types = listOf("VerifiableCredential", "PersonCredential"))
        wallet.store(cred1)
        
        val filtered = wallet.list(CredentialFilter(type = listOf("DegreeCredential")))
        
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `test BasicWallet list with subjectId filter matches`() = runBlocking {
        val cred1 = createTestCredential(
            id = "cred-1",
            subject = buildJsonObject {
                put("id", "did:key:subject1")
                put("name", "John")
            }
        )
        val cred2 = createTestCredential(
            id = "cred-2",
            subject = buildJsonObject {
                put("id", "did:key:subject2")
                put("name", "Jane")
            }
        )
        wallet.store(cred1)
        wallet.store(cred2)
        
        val filtered = wallet.list(CredentialFilter(subjectId = "did:key:subject1"))
        
        assertEquals(1, filtered.size)
    }

    @Test
    fun `test BasicWallet list with subjectId filter does not match`() = runBlocking {
        val cred1 = createTestCredential(
            id = "cred-1",
            subject = buildJsonObject {
                put("id", "did:key:subject1")
            }
        )
        wallet.store(cred1)
        
        val filtered = wallet.list(CredentialFilter(subjectId = "did:key:subject2"))
        
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `test BasicWallet list with expired filter true`() = runBlocking {
        val pastDate = java.time.Instant.now().minusSeconds(86400).toString()
        val cred1 = createTestCredential(id = "cred-1", expirationDate = pastDate)
        val futureDate = java.time.Instant.now().plusSeconds(86400).toString()
        val cred2 = createTestCredential(id = "cred-2", expirationDate = futureDate)
        wallet.store(cred1)
        wallet.store(cred2)
        
        val filtered = wallet.list(CredentialFilter(expired = true))
        
        assertEquals(1, filtered.size)
        assertEquals("cred-1", filtered.first().id)
    }

    @Test
    fun `test BasicWallet list with expired filter false`() = runBlocking {
        val pastDate = java.time.Instant.now().minusSeconds(86400).toString()
        val cred1 = createTestCredential(id = "cred-1", expirationDate = pastDate)
        val futureDate = java.time.Instant.now().plusSeconds(86400).toString()
        val cred2 = createTestCredential(id = "cred-2", expirationDate = futureDate)
        wallet.store(cred1)
        wallet.store(cred2)
        
        val filtered = wallet.list(CredentialFilter(expired = false))
        
        assertEquals(1, filtered.size)
        assertEquals("cred-2", filtered.first().id)
    }

    @Test
    fun `test BasicWallet list with expired filter null expiration`() = runBlocking {
        val cred1 = createTestCredential(id = "cred-1", expirationDate = null)
        wallet.store(cred1)
        
        val filtered = wallet.list(CredentialFilter(expired = false))
        
        assertEquals(1, filtered.size)
    }

    @Test
    fun `test BasicWallet list with expired filter invalid date`() = runBlocking {
        val cred1 = createTestCredential(id = "cred-1", expirationDate = "invalid-date")
        wallet.store(cred1)
        
        val filtered = wallet.list(CredentialFilter(expired = true))
        
        // Invalid dates should not match expired=true
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `test BasicWallet list with revoked filter true`() = runBlocking {
        val cred1 = createTestCredential(
            id = "cred-1",
            credentialStatus = io.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "status-list-1",
                type = "StatusList2021Entry"
            )
        )
        val cred2 = createTestCredential(id = "cred-2", credentialStatus = null)
        wallet.store(cred1)
        wallet.store(cred2)
        
        val filtered = wallet.list(CredentialFilter(revoked = true))
        
        assertEquals(1, filtered.size)
        assertEquals("cred-1", filtered.first().id)
    }

    @Test
    fun `test BasicWallet list with revoked filter false`() = runBlocking {
        val cred1 = createTestCredential(
            id = "cred-1",
            credentialStatus = io.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "status-list-1",
                type = "StatusList2021Entry"
            )
        )
        val cred2 = createTestCredential(id = "cred-2", credentialStatus = null)
        wallet.store(cred1)
        wallet.store(cred2)
        
        val filtered = wallet.list(CredentialFilter(revoked = false))
        
        assertEquals(1, filtered.size)
        assertEquals("cred-2", filtered.first().id)
    }

    @Test
    fun `test BasicWallet list with multiple filters`() = runBlocking {
        val cred1 = createTestCredential(
            id = "cred-1",
            issuerDid = "did:key:issuer1",
            types = listOf("VerifiableCredential", "PersonCredential")
        )
        val cred2 = createTestCredential(
            id = "cred-2",
            issuerDid = "did:key:issuer1",
            types = listOf("VerifiableCredential", "DegreeCredential")
        )
        wallet.store(cred1)
        wallet.store(cred2)
        
        val filtered = wallet.list(
            CredentialFilter(
                issuer = "did:key:issuer1",
                type = listOf("PersonCredential")
            )
        )
        
        assertEquals(1, filtered.size)
        assertEquals("cred-1", filtered.first().id)
    }

    @Test
    fun `test BasicWallet delete returns true when found`() = runBlocking {
        val credential = createTestCredential(id = "cred-1")
        wallet.store(credential)
        
        val deleted = wallet.delete("cred-1")
        
        assertTrue(deleted)
        assertNull(wallet.get("cred-1"))
    }

    @Test
    fun `test BasicWallet delete returns false when not found`() = runBlocking {
        val deleted = wallet.delete("nonexistent")
        
        assertFalse(deleted)
    }

    @Test
    fun `test BasicWallet clear removes all credentials`() = runBlocking {
        wallet.store(createTestCredential(id = "cred-1"))
        wallet.store(createTestCredential(id = "cred-2"))
        assertEquals(2, wallet.size())
        
        wallet.clear()
        
        assertEquals(0, wallet.size())
        assertTrue(wallet.list(null).isEmpty())
    }

    @Test
    fun `test BasicWallet size returns correct count`() = runBlocking {
        assertEquals(0, wallet.size())
        
        wallet.store(createTestCredential(id = "cred-1"))
        assertEquals(1, wallet.size())
        
        wallet.store(createTestCredential(id = "cred-2"))
        assertEquals(2, wallet.size())
    }

    @Test
    fun `test BasicWallet query with multiple filters`() = runBlocking {
        val futureDate = java.time.Instant.now().plusSeconds(86400).toString()
        val cred1 = createTestCredential(
            id = "cred-1",
            issuerDid = "did:key:issuer1",
            expirationDate = futureDate
        )
        val cred2 = createTestCredential(
            id = "cred-2",
            issuerDid = "did:key:issuer2",
            expirationDate = futureDate
        )
        wallet.store(cred1)
        wallet.store(cred2)
        
        val results = wallet.query {
            byIssuer("did:key:issuer1")
            notExpired()
        }
        
        assertEquals(1, results.size)
        assertEquals("cred-1", results.first().id)
    }

    private fun createTestCredential(
        id: String? = "https://example.com/credentials/1",
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


