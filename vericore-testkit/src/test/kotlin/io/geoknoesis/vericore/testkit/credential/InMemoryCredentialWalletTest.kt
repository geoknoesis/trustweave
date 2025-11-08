package io.geoknoesis.vericore.testkit.credential

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.wallet.CredentialFilter
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*
import java.time.Instant

/**
 * Tests for InMemoryCredentialWallet (deprecated but still used).
 * This class is kept for backward compatibility.
 */
class InMemoryCredentialWalletTest {

    private lateinit var wallet: InMemoryCredentialWallet

    @BeforeTest
    fun setup() {
        wallet = InMemoryCredentialWallet()
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
    fun `test get credential`() = runBlocking {
        val credential = createTestCredential()
        val id = wallet.store(credential)

        val retrieved = wallet.get(id)

        assertNotNull(retrieved)
        assertEquals(credential.id, retrieved?.id)
    }

    @Test
    fun `test list credentials`() = runBlocking {
        val cred1 = createTestCredential("cred-1")
        val cred2 = createTestCredential("cred-2")
        wallet.store(cred1)
        wallet.store(cred2)

        val credentials = wallet.list(null)

        assertEquals(2, credentials.size)
    }

    @Test
    fun `test list with filter`() = runBlocking {
        val cred1 = createTestCredential("cred-1", issuer = "did:example:issuer1")
        val cred2 = createTestCredential("cred-2", issuer = "did:example:issuer2")
        wallet.store(cred1)
        wallet.store(cred2)

        val credentials = wallet.list(CredentialFilter(issuer = "did:example:issuer1"))

        assertEquals(1, credentials.size)
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
    fun `test query credentials`() = runBlocking {
        val cred1 = createTestCredential("cred-1", issuer = "did:example:issuer1")
        wallet.store(cred1)

        val credentials = wallet.query {
            byIssuer("did:example:issuer1")
        }

        assertEquals(1, credentials.size)
    }

    @Test
    fun `test clear credentials`() = runBlocking {
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

        wallet.delete("cred-1")
        assertEquals(0, wallet.size())
    }

    @Test
    fun `test wallet ID is set`() {
        assertNotNull(wallet.walletId)
        assertTrue(wallet.walletId.isNotEmpty())
    }

    private fun createTestCredential(
        id: String = "cred-${System.currentTimeMillis()}",
        issuer: String = "did:example:issuer",
        types: List<String> = listOf("VerifiableCredential", "TestCredential"),
        subjectId: String = "did:example:subject"
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuer,
            issuanceDate = Instant.now().toString(),
            credentialSubject = buildJsonObject {
                put("id", subjectId)
                put("name", "Test Subject")
            }
        )
    }
}

