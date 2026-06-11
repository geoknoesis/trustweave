package org.trustweave.testkit.credential

import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests that `query { byTag(...) }` and `query { byCollection(...) }` actually
 * filter results instead of silently returning all credentials.
 *
 * Regression tests for the P1 finding where requestedTags/requestedCollections
 * were populated by CredentialQueryBuilder but never read by any implementation.
 */
class InMemoryWalletQueryFilterTest {

    private lateinit var wallet: InMemoryWallet

    @BeforeEach
    fun setup() {
        wallet = InMemoryWallet("query-filter-wallet")
    }

    private fun credential(
        id: String,
        type: String = "PersonCredential",
        issuerDid: String = "did:key:issuer"
    ): VerifiableCredential =
        VerifiableCredential(
            id = CredentialId(id),
            type = listOf(CredentialType.Custom(type)),
            issuer = Issuer.fromDid(Did(issuerDid)),
            credentialSubject = CredentialSubject.fromIri("did:key:subject"),
            issuanceDate = Clock.System.now(),
            proof = null
        )

    @Test
    fun `byTag returns only credentials carrying the tag`() = runBlocking {
        val taggedId = wallet.store(credential("cred-tagged"))
        wallet.store(credential("cred-untagged"))
        wallet.tagCredential(taggedId, setOf("important"))

        val results = wallet.query { byTag("important") }

        assertEquals(listOf("cred-tagged"), results.map { it.id?.value })
    }

    @Test
    fun `byTag with unknown tag returns empty list instead of all credentials`() = runBlocking {
        wallet.store(credential("cred-1"))
        wallet.store(credential("cred-2"))

        val results = wallet.query { byTag("does-not-exist") }

        assertTrue(results.isEmpty(), "Unknown tag must match nothing, got ${results.size} credentials")
    }

    @Test
    fun `multiple byTag calls require all tags (AND semantics)`() = runBlocking {
        val bothId = wallet.store(credential("cred-both"))
        val oneId = wallet.store(credential("cred-one"))
        wallet.tagCredential(bothId, setOf("a", "b"))
        wallet.tagCredential(oneId, setOf("a"))

        val results = wallet.query {
            byTag("a")
            byTag("b")
        }

        assertEquals(listOf("cred-both"), results.map { it.id?.value })
    }

    @Test
    fun `byCollection returns only credentials in the collection`() = runBlocking {
        val inId = wallet.store(credential("cred-in"))
        wallet.store(credential("cred-out"))
        val collectionId = wallet.createCollection("My Collection")
        wallet.addToCollection(inId, collectionId)

        val results = wallet.query { byCollection(collectionId) }

        assertEquals(listOf("cred-in"), results.map { it.id?.value })
    }

    @Test
    fun `byCollection with unknown collection returns empty list`() = runBlocking {
        wallet.store(credential("cred-1"))

        val results = wallet.query { byCollection("no-such-collection") }

        assertTrue(results.isEmpty())
    }

    @Test
    fun `byTag combines with standard predicate filters`() = runBlocking {
        val matchId = wallet.store(credential("cred-match", type = "PersonCredential"))
        val wrongTypeId = wallet.store(credential("cred-wrong-type", type = "DegreeCredential"))
        wallet.tagCredential(matchId, setOf("important"))
        wallet.tagCredential(wrongTypeId, setOf("important"))

        val results = wallet.query {
            byTag("important")
            byType("PersonCredential")
        }

        assertEquals(listOf("cred-match"), results.map { it.id?.value })
    }

    @Test
    fun `query without tag or collection filters still returns all matches`() = runBlocking {
        wallet.store(credential("cred-1"))
        wallet.store(credential("cred-2"))

        val results = wallet.query { byIssuer("did:key:issuer") }

        assertEquals(setOf("cred-1", "cred-2"), results.map { it.id?.value }.toSet())
    }
}
