package org.trustweave.trust.dsl.wallet

import kotlinx.coroutines.runBlocking
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.testkit.credential.BasicWallet
import org.trustweave.testkit.credential.InMemoryWallet
import org.trustweave.wallet.CredentialFilter
import org.trustweave.wallet.CredentialQueryBuilder
import org.trustweave.wallet.Wallet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class WalletQueryDslTest {
    private val now = Clock.System.now()

    private suspend fun populatedWallet(): InMemoryWallet {
        val wallet = InMemoryWallet()
        wallet.store(walletCredential("a", issuer = "did:key:issuer-a", types = listOf("VerifiableCredential", "Degree")))
        wallet.store(walletCredential("b", issuer = "did:key:issuer-b", types = listOf("VerifiableCredential", "License")))
        wallet.store(
            walletCredential("c", issuer = "did:key:issuer-a", subject = "urn:uuid:42", types = listOf("Degree"), expiresAt = now - 1.days),
        )
        wallet.store(
            walletCredential("d", issuer = "did:key:issuer-c", types = listOf("Passport"), expiresAt = now + 30.days, signed = false),
        )
        return wallet
    }

    private suspend fun Wallet.ids(block: QueryBuilder.() -> Unit) = query(block).mapNotNull { it.id?.value }.sorted()

    @Test
    fun `an empty query returns every credential`() =
        runBlocking {
            assertEquals(listOf("a", "b", "c", "d"), populatedWallet().ids { })
        }

    @Test
    fun `issuer and type filters narrow the result and combine as a conjunction`() =
        runBlocking {
            val wallet = populatedWallet()

            assertEquals(listOf("a", "c"), wallet.ids { issuer("did:key:issuer-a") })
            assertEquals(listOf("a", "c"), wallet.ids { type("Degree") })
            assertEquals(listOf("a", "b", "c"), wallet.ids { types("Degree", "License") })
            assertEquals(
                listOf("a"),
                wallet.ids {
                    issuer("did:key:issuer-a")
                    type("Degree")
                    notExpired()
                },
            )
        }

    @Test
    fun `subject accepts both DIDs and plain URIs`() =
        runBlocking {
            val wallet = populatedWallet()

            assertEquals(listOf("c"), wallet.ids { subject("urn:uuid:42") })
            assertEquals(listOf("a", "b", "d"), wallet.ids { subject("did:key:subject-a") })
        }

    @Test
    fun `expiry filters separate expired from current credentials`() =
        runBlocking {
            val wallet = populatedWallet()

            assertEquals(listOf("c"), wallet.ids { expired() })
            assertEquals(listOf("a", "b", "d"), wallet.ids { notExpired() })
        }

    @Test
    fun `valid requires a proof and an unexpired credential and revoked matches nothing offline`() =
        runBlocking {
            val wallet = populatedWallet()

            assertEquals(listOf("a", "b"), wallet.ids { valid() })
            assertEquals(listOf("a", "b", "c", "d"), wallet.ids { notRevoked() })
            assertTrue(wallet.ids { revoked() }.isEmpty())
        }

    @Test
    fun `tag and collection filters use the wallet organization data`() =
        runBlocking {
            val wallet = populatedWallet()
            wallet.tagCredential("a", setOf("work"))
            wallet.tagCredential("b", setOf("work", "personal"))
            val collection = wallet.createCollection("favourites")
            wallet.addToCollection("b", collection)
            wallet.addToCollection("c", collection)

            assertEquals(listOf("a", "b"), wallet.ids { tag("work") })
            assertEquals(listOf("b"), wallet.ids { tag("personal") })
            assertEquals(listOf("b", "c"), wallet.ids { collection(collection) })
            assertEquals(
                listOf("b"),
                wallet.ids {
                    tag("work")
                    collection(collection)
                },
            )
            assertTrue(wallet.ids { tag("unknown") }.isEmpty())
        }

    @Test
    fun `blank or malformed filter arguments are rejected up front`() {
        val query = QueryBuilder(BasicWallet())

        assertFailsWith<IllegalArgumentException> { query.issuer(" ") }
        assertTrue(assertFailsWith<IllegalArgumentException> { query.issuer("issuer-a") }.message!!.contains("must start with 'did:'"))
        assertFailsWith<IllegalArgumentException> { query.type("") }
        assertFailsWith<IllegalArgumentException> { query.subject("") }
        assertTrue(assertFailsWith<IllegalArgumentException> { query.subject("12345") }.message!!.contains("valid DID"))
    }

    /** A wallet with storage only: it has no tag or collection data to filter on. */
    private class StorageOnlyWallet(
        private val items: List<VerifiableCredential>,
    ) : Wallet {
        override val walletId = "storage-only"

        override suspend fun store(credential: VerifiableCredential) = credential.id!!.value

        override suspend fun get(credentialId: String) = items.firstOrNull { it.id?.value == credentialId }

        override suspend fun list(filter: CredentialFilter?) = items

        override suspend fun delete(credentialId: String) = false

        override suspend fun query(query: CredentialQueryBuilder.() -> Unit) = items
    }

    @Test
    fun `unfiltered queries still work on a wallet that cannot organize`() =
        runBlocking {
            val wallet = StorageOnlyWallet(listOf(walletCredential("a", issuer = "did:key:x"), walletCredential("b")))

            assertEquals(listOf("a"), wallet.ids { issuer("did:key:x") })
        }
}
