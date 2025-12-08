package com.trustweave.testkit.credential

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.wallet.CredentialFilter
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.credential.identifiers.StatusListId
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.StatusPurpose
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.vc.CredentialStatus
import com.trustweave.did.identifiers.Did
import com.trustweave.core.identifiers.Iri

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
        assertEquals(credential.id?.value, id)
    }

    @Test
    fun `test store credential without ID generates UUID`() = runBlocking {
        val credential = VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.fromDid(Did("did:example:issuer")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject.fromIri(Iri("did:example:subject"), emptyMap())
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
        assertEquals(credential.id?.value, retrieved?.id?.value)
        assertEquals(credential.issuer.id.value, retrieved?.issuer?.id?.value)
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
        assertTrue(credentials.any { it.id?.value == "cred-1" })
        assertTrue(credentials.any { it.id?.value == "cred-2" })
    }

    @Test
    fun `test list with issuer filter`() = runBlocking {
        val cred1 = createTestCredential("cred-1", issuer = "did:example:issuer1")
        val cred2 = createTestCredential("cred-2", issuer = "did:example:issuer2")
        wallet.store(cred1)
        wallet.store(cred2)

        val credentials = wallet.list(CredentialFilter(issuer = "did:example:issuer1"))

        assertEquals(1, credentials.size)
        assertEquals("cred-1", credentials.first().id?.value)
    }

    @Test
    fun `test list with type filter`() = runBlocking {
        val cred1 = createTestCredential("cred-1", types = listOf("VerifiableCredential", "TypeA"))
        val cred2 = createTestCredential("cred-2", types = listOf("VerifiableCredential", "TypeB"))
        wallet.store(cred1)
        wallet.store(cred2)

        val credentials = wallet.list(CredentialFilter(type = listOf("TypeA")))

        assertEquals(1, credentials.size)
        assertEquals("cred-1", credentials.first().id?.value)
    }

    @Test
    fun `test list with subject filter`() = runBlocking {
        val cred1 = createTestCredential("cred-1", subjectId = "did:example:subject1")
        val cred2 = createTestCredential("cred-2", subjectId = "did:example:subject2")
        wallet.store(cred1)
        wallet.store(cred2)

        val credentials = wallet.list(CredentialFilter(subjectId = "did:example:subject1"))

        assertEquals(1, credentials.size)
        assertEquals("cred-1", credentials.first().id?.value)
    }

    @Test
    fun `test list with expired filter`() = runBlocking {
        val pastDate = Clock.System.now().minus(kotlin.time.Duration.parse("PT24H")).toString()
        val futureDate = Clock.System.now().plus(kotlin.time.Duration.parse("PT24H")).toString()
        val cred1 = createTestCredential("cred-1", expirationDate = pastDate)
        val cred2 = createTestCredential("cred-2", expirationDate = futureDate)
        wallet.store(cred1)
        wallet.store(cred2)

        val expired = wallet.list(CredentialFilter(expired = true))
        val notExpired = wallet.list(CredentialFilter(expired = false))

        assertEquals(1, expired.size)
        assertEquals("cred-1", expired.first().id?.value)
        assertEquals(1, notExpired.size)
        assertEquals("cred-2", notExpired.first().id?.value)
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
        assertEquals("cred-1", revoked.first().id?.value)
        assertEquals(1, notRevoked.size)
        assertEquals("cred-2", notRevoked.first().id?.value)
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
        assertEquals("cred-1", credentials.first().id?.value)
    }

    @Test
    fun `test query with multiple filters`() = runBlocking {
        val futureDate = Clock.System.now().plus(kotlin.time.Duration.parse("PT24H")).toString()
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
        assertTrue(credentials.all { it.issuer.id.value == "did:example:issuer1" })
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
        val claims = buildJsonObject {
            put("name", "Test Subject")
        }.toMutableMap()
        return VerifiableCredential(
            id = CredentialId(id),
            type = types.map { CredentialType.Custom(it) },
            issuer = Issuer.fromDid(Did(issuer)),
            issuanceDate = Clock.System.now(),
            expirationDate = expirationDate?.let { Instant.parse(it) },
            credentialSubject = CredentialSubject.fromIri(Iri(subjectId), claims = claims),
            credentialStatus = if (revoked) {
                CredentialStatus(
                    id = StatusListId("https://example.com/status/1"),
                    type = "StatusList2021Entry",
                    statusPurpose = StatusPurpose.REVOCATION,
                    statusListIndex = "1"
                )
            } else null
        )
    }
}



