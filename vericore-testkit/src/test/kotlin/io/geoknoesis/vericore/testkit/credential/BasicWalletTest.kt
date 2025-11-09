package io.geoknoesis.vericore.testkit.credential

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.wallet.CredentialFilter
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*
import java.time.Instant

class BasicWalletTest {

    private lateinit var wallet: BasicWallet

    @BeforeTest
    fun setup() {
        wallet = BasicWallet()
    }

    @AfterTest
    fun cleanup() {
        wallet.clear()
    }

    @Test
    fun `test store credential`() = runBlocking {
        val credential = createTestCredential()

        val id = wallet.store(credential)

        assertNotNull(id)
        assertEquals(credential.id, id)
    }

    @Test
    fun `test store credential without ID generates UUID`() = runBlocking {
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:example:issuer",
            issuanceDate = Instant.now().toString(),
            credentialSubject = buildJsonObject { put("id", "did:example:subject") }
        )

        val id = wallet.store(credential)

        assertNotNull(id)
        assertTrue(id.isNotEmpty())
    }

    @Test
    fun `test get credential`() = runBlocking {
        val credential = createTestCredential()
        val id = wallet.store(credential)

        val retrieved = wallet.get(id)

        assertNotNull(retrieved)
        assertEquals(credential.id, retrieved?.id)
        assertEquals(credential.issuer, retrieved?.issuer)
    }

    @Test
    fun `test get non-existent credential returns null`() = runBlocking {
        val retrieved = wallet.get("non-existent-id")

        assertNull(retrieved)
    }

    @Test
    fun `test list all credentials`() = runBlocking {
        val cred1 = createTestCredential("cred-1")
        val cred2 = createTestCredential("cred-2")
        wallet.store(cred1)
        wallet.store(cred2)

        val credentials = wallet.list(null)

        assertEquals(2, credentials.size)
        assertTrue(credentials.any { it.id == "cred-1" })
        assertTrue(credentials.any { it.id == "cred-2" })
    }

    @Test
    fun `test list with issuer filter`() = runBlocking {
        val cred1 = createTestCredential("cred-1", issuer = "did:example:issuer1")
        val cred2 = createTestCredential("cred-2", issuer = "did:example:issuer2")
        wallet.store(cred1)
        wallet.store(cred2)

        val credentials = wallet.list(CredentialFilter(issuer = "did:example:issuer1"))

        assertEquals(1, credentials.size)
        assertEquals("cred-1", credentials.first().id)
    }

    @Test
    fun `test list with type filter`() = runBlocking {
        val cred1 = createTestCredential("cred-1", types = listOf("VerifiableCredential", "TypeA"))
        val cred2 = createTestCredential("cred-2", types = listOf("VerifiableCredential", "TypeB"))
        wallet.store(cred1)
        wallet.store(cred2)

        val credentials = wallet.list(CredentialFilter(type = listOf("TypeA")))

        assertEquals(1, credentials.size)
        assertEquals("cred-1", credentials.first().id)
    }

    @Test
    fun `test list with subject filter`() = runBlocking {
        val cred1 = createTestCredential("cred-1", subjectId = "did:example:subject1")
        val cred2 = createTestCredential("cred-2", subjectId = "did:example:subject2")
        wallet.store(cred1)
        wallet.store(cred2)

        val credentials = wallet.list(CredentialFilter(subjectId = "did:example:subject1"))

        assertEquals(1, credentials.size)
        assertEquals("cred-1", credentials.first().id)
    }

    @Test
    fun `test list with expired filter`() = runBlocking {
        val pastDate = Instant.now().minusSeconds(86400).toString()
        val futureDate = Instant.now().plusSeconds(86400).toString()
        val cred1 = createTestCredential("cred-1", expirationDate = pastDate)
        val cred2 = createTestCredential("cred-2", expirationDate = futureDate)
        wallet.store(cred1)
        wallet.store(cred2)

        val expired = wallet.list(CredentialFilter(expired = true))
        val notExpired = wallet.list(CredentialFilter(expired = false))

        assertEquals(1, expired.size)
        assertEquals("cred-1", expired.first().id)
        assertEquals(1, notExpired.size)
        assertEquals("cred-2", notExpired.first().id)
    }

    @Test
    fun `test list with revoked filter`() = runBlocking {
        val cred1 = createTestCredential("cred-1", revoked = true)
        val cred2 = createTestCredential("cred-2", revoked = false)
        wallet.store(cred1)
        wallet.store(cred2)

        val revoked = wallet.list(CredentialFilter(revoked = true))
        val notRevoked = wallet.list(CredentialFilter(revoked = false))

        assertEquals(1, revoked.size)
        assertEquals("cred-1", revoked.first().id)
        assertEquals(1, notRevoked.size)
        assertEquals("cred-2", notRevoked.first().id)
    }

    @Test
    fun `test delete credential`() = runBlocking {
        val credential = createTestCredential()
        val id = wallet.store(credential)

        val deleted = wallet.delete(id)

        assertTrue(deleted)
        assertNull(wallet.get(id))
    }

    @Test
    fun `test delete non-existent credential returns false`() = runBlocking {
        val deleted = wallet.delete("non-existent-id")

        assertFalse(deleted)
    }

    @Test
    fun `test query credentials`() = runBlocking {
        val cred1 = createTestCredential("cred-1", issuer = "did:example:issuer1")
        val cred2 = createTestCredential("cred-2", issuer = "did:example:issuer2")
        wallet.store(cred1)
        wallet.store(cred2)

        val credentials = wallet.query {
            byIssuer("did:example:issuer1")
        }

        assertEquals(1, credentials.size)
        assertEquals("cred-1", credentials.first().id)
    }

    @Test
    fun `test query with multiple filters`() = runBlocking {
        val futureDate = Instant.now().plusSeconds(86400).toString()
        val cred1 = createTestCredential("cred-1", issuer = "did:example:issuer1", expirationDate = futureDate)
        val cred2 = createTestCredential("cred-2", issuer = "did:example:issuer1", expirationDate = futureDate)
        val cred3 = createTestCredential("cred-3", issuer = "did:example:issuer2", expirationDate = futureDate)
        wallet.store(cred1)
        wallet.store(cred2)
        wallet.store(cred3)

        val credentials = wallet.query {
            byIssuer("did:example:issuer1")
            notExpired()
        }

        assertEquals(2, credentials.size)
        assertTrue(credentials.all { it.issuer == "did:example:issuer1" })
    }

    @Test
    fun `test clear all credentials`() = runBlocking {
        wallet.store(createTestCredential("cred-1"))
        wallet.store(createTestCredential("cred-2"))

        wallet.clear()

        assertEquals(0, wallet.list(null).size)
        assertEquals(0, wallet.size())
    }

    @Test
    fun `test size returns correct count`() = runBlocking {
        assertEquals(0, wallet.size())

        wallet.store(createTestCredential("cred-1"))
        assertEquals(1, wallet.size())

        wallet.store(createTestCredential("cred-2"))
        assertEquals(2, wallet.size())

        wallet.delete("cred-1")
        assertEquals(1, wallet.size())
    }

    @Test
    fun `test wallet ID is set`() {
        assertNotNull(wallet.walletId)
        assertTrue(wallet.walletId.isNotEmpty())
    }

    @Test
    fun `test wallet ID can be customized`() {
        val customWallet = BasicWallet("custom-wallet-id")

        assertEquals("custom-wallet-id", customWallet.walletId)
    }

    @Test
    fun `test capabilities are basic storage only`() {
        val capabilities = wallet.capabilities

        assertTrue(capabilities.credentialStorage)
        assertTrue(capabilities.credentialQuery)
        assertFalse(capabilities.collections)
        assertFalse(capabilities.tags)
        assertFalse(capabilities.metadata)
        assertFalse(capabilities.archive)
        assertFalse(capabilities.refresh)
        assertFalse(capabilities.createPresentation)
        assertFalse(capabilities.selectiveDisclosure)
        assertFalse(capabilities.didManagement)
        assertFalse(capabilities.keyManagement)
        assertFalse(capabilities.credentialIssuance)
    }

    private fun createTestCredential(
        id: String = "cred-${System.currentTimeMillis()}",
        issuer: String = "did:example:issuer",
        types: List<String> = listOf("VerifiableCredential", "TestCredential"),
        subjectId: String = "did:example:subject",
        expirationDate: String? = null,
        revoked: Boolean = false
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuer,
            issuanceDate = Instant.now().toString(),
            expirationDate = expirationDate,
            credentialSubject = buildJsonObject {
                put("id", subjectId)
                put("name", "Test Subject")
            },
            credentialStatus = if (revoked) {
                io.geoknoesis.vericore.credential.models.CredentialStatus(
                    id = "https://example.com/status/1",
                    type = "StatusList2021Entry",
                    statusListIndex = "1"
                )
            } else null
        )
    }
}


