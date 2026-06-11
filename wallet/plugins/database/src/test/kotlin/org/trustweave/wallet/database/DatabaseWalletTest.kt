package org.trustweave.wallet.database

import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import org.trustweave.wallet.exception.WalletException
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [DatabaseWallet] against an in-memory H2 database (exercising the
 * standard-SQL MERGE upsert branch; PostgreSQL uses ON CONFLICT instead).
 *
 * Covers the P1 findings: tag/collection query filtering (previously a silent
 * no-op) and connection-pool ownership on [DatabaseWallet.close].
 */
class DatabaseWalletTest {

    private val issuerDid = "did:key:z6MkTestIssuer"

    private fun newDataSource(): HikariDataSource {
        val config = HikariConfig()
        config.jdbcUrl = "jdbc:h2:mem:wallet-test-${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
        config.maximumPoolSize = 2
        return HikariDataSource(config)
    }

    private fun newWallet(dataSource: DataSource, ownsDataSource: Boolean = false): DatabaseWallet =
        DatabaseWallet.create(
            walletId = "wallet-test",
            walletDid = "did:key:z6MkWallet",
            holderDid = "did:key:z6MkHolder",
            dataSource = dataSource,
            ownsDataSource = ownsDataSource
        )

    private fun credential(id: String, type: String = "TestCredential"): VerifiableCredential =
        VerifiableCredential(
            id = CredentialId(id),
            type = listOf(CredentialType.Custom(type)),
            issuer = Issuer.fromDid(Did(issuerDid)),
            credentialSubject = CredentialSubject.fromIri("did:key:z6MkTestSubject"),
            issuanceDate = Clock.System.now(),
            proof = null
        )

    // ========== Sanity ==========

    @Test
    fun `store and get round-trip`() {
        runBlocking {
            newDataSource().use { dataSource ->
                val wallet = newWallet(dataSource)
                val id = wallet.store(credential("cred-roundtrip"))

                assertEquals("cred-roundtrip", id)
                assertEquals("cred-roundtrip", wallet.get(id)?.id?.value)
            }
        }
    }

    @Test
    fun `store upserts on re-store of the same credential`() {
        runBlocking {
            newDataSource().use { dataSource ->
                val wallet = newWallet(dataSource)
                wallet.store(credential("cred-upsert", type = "FirstType"))
                wallet.store(credential("cred-upsert", type = "SecondType"))

                val stored = wallet.get("cred-upsert")
                assertNotNull(stored)
                assertEquals("SecondType", stored.type.first().value)
                assertEquals(1, wallet.list(null).size)
            }
        }
    }

    @Test
    fun `store rejects a credential id already owned by another wallet`() {
        runBlocking {
            newDataSource().use { dataSource ->
                val walletA = newWallet(dataSource)
                val walletB = DatabaseWallet.create(
                    walletId = "wallet-other",
                    walletDid = "did:key:z6MkOtherWallet",
                    holderDid = "did:key:z6MkOtherHolder",
                    dataSource = dataSource
                )
                walletA.store(credential("cred-shared-id"))

                assertFailsWith<WalletException.StorageError> {
                    walletB.store(credential("cred-shared-id"))
                }
            }
        }
    }

    // ========== query byTag / byCollection (P1: silent no-op) ==========

    @Test
    fun `query byTag returns only credentials carrying the tag`() {
        runBlocking {
            newDataSource().use { dataSource ->
                val wallet = newWallet(dataSource)
                wallet.store(credential("cred-tagged"))
                wallet.store(credential("cred-untagged"))
                assertTrue(wallet.tagCredential("cred-tagged", setOf("important")))

                val results = wallet.query { byTag("important") }

                assertEquals(listOf("cred-tagged"), results.map { it.id?.value })
            }
        }
    }

    @Test
    fun `query byTag with unknown tag returns empty list instead of all credentials`() {
        runBlocking {
            newDataSource().use { dataSource ->
                val wallet = newWallet(dataSource)
                wallet.store(credential("cred-1"))
                wallet.store(credential("cred-2"))

                val results = wallet.query { byTag("does-not-exist") }

                assertTrue(results.isEmpty(), "Unknown tag must match nothing, got ${results.size} credentials")
            }
        }
    }

    @Test
    fun `multiple byTag calls require all tags`() {
        runBlocking {
            newDataSource().use { dataSource ->
                val wallet = newWallet(dataSource)
                wallet.store(credential("cred-both"))
                wallet.store(credential("cred-one"))
                wallet.tagCredential("cred-both", setOf("a", "b"))
                wallet.tagCredential("cred-one", setOf("a"))

                val results = wallet.query {
                    byTag("a")
                    byTag("b")
                }

                assertEquals(listOf("cred-both"), results.map { it.id?.value })
            }
        }
    }

    @Test
    fun `query byCollection returns only credentials in the collection`() {
        runBlocking {
            newDataSource().use { dataSource ->
                val wallet = newWallet(dataSource)
                wallet.store(credential("cred-in"))
                wallet.store(credential("cred-out"))
                val collectionId = wallet.createCollection("Collection 1")
                assertTrue(wallet.addToCollection("cred-in", collectionId))

                val results = wallet.query { byCollection(collectionId) }

                assertEquals(listOf("cred-in"), results.map { it.id?.value })
            }
        }
    }

    @Test
    fun `query byTag combines with standard predicate filters`() {
        runBlocking {
            newDataSource().use { dataSource ->
                val wallet = newWallet(dataSource)
                wallet.store(credential("cred-match", type = "PersonCredential"))
                wallet.store(credential("cred-wrong-type", type = "DegreeCredential"))
                wallet.tagCredential("cred-match", setOf("important"))
                wallet.tagCredential("cred-wrong-type", setOf("important"))

                val results = wallet.query {
                    byTag("important")
                    byType("PersonCredential")
                }

                assertEquals(listOf("cred-match"), results.map { it.id?.value })
            }
        }
    }

    @Test
    fun `query without tag or collection filters returns all matches`() {
        runBlocking {
            newDataSource().use { dataSource ->
                val wallet = newWallet(dataSource)
                wallet.store(credential("cred-1"))
                wallet.store(credential("cred-2"))

                val results = wallet.query { byIssuer(issuerDid) }

                assertEquals(setOf("cred-1", "cred-2"), results.map { it.id?.value }.toSet())
            }
        }
    }

    // ========== Tagging write API (CredentialTagging) ==========

    @Test
    fun `tagCredential then untagCredential round-trips through query byTag`() {
        runBlocking {
            newDataSource().use { dataSource ->
                val wallet = newWallet(dataSource)
                wallet.store(credential("cred-lifecycle"))

                assertTrue(wallet.tagCredential("cred-lifecycle", setOf("important", "work")))
                assertEquals(setOf("important", "work"), wallet.getTags("cred-lifecycle"))
                assertEquals(listOf("cred-lifecycle"), wallet.query { byTag("important") }.map { it.id?.value })
                assertEquals(listOf("cred-lifecycle"), wallet.findByTag("work").map { it.id?.value })

                assertTrue(wallet.untagCredential("cred-lifecycle", setOf("important")))
                assertEquals(setOf("work"), wallet.getTags("cred-lifecycle"))
                assertTrue(wallet.query { byTag("important") }.isEmpty())
                assertEquals(setOf("work"), wallet.getAllTags())
            }
        }
    }

    @Test
    fun `tagCredential is idempotent`() {
        runBlocking {
            newDataSource().use { dataSource ->
                val wallet = newWallet(dataSource)
                wallet.store(credential("cred-idempotent"))

                assertTrue(wallet.tagCredential("cred-idempotent", setOf("dup")))
                assertTrue(wallet.tagCredential("cred-idempotent", setOf("dup")))

                assertEquals(setOf("dup"), wallet.getTags("cred-idempotent"))
            }
        }
    }

    @Test
    fun `tagCredential returns false for an unknown credential`() {
        runBlocking {
            newDataSource().use { dataSource ->
                val wallet = newWallet(dataSource)

                assertFalse(wallet.tagCredential("no-such-credential", setOf("tag")))
                assertFalse(wallet.untagCredential("no-such-credential", setOf("tag")))
            }
        }
    }

    @Test
    fun `tags are isolated between wallets sharing the same database`() {
        runBlocking {
            newDataSource().use { dataSource ->
                val walletA = newWallet(dataSource)
                val walletB = DatabaseWallet.create(
                    walletId = "wallet-other",
                    walletDid = "did:key:z6MkOtherWallet",
                    holderDid = "did:key:z6MkOtherHolder",
                    dataSource = dataSource
                )
                walletA.store(credential("cred-a"))
                walletA.tagCredential("cred-a", setOf("shared-tag"))

                // Wallet B cannot tag, see, or find wallet A's credential
                assertFalse(walletB.tagCredential("cred-a", setOf("hijack")))
                assertTrue(walletB.getTags("cred-a").isEmpty())
                assertTrue(walletB.getAllTags().isEmpty())
                assertTrue(walletB.findByTag("shared-tag").isEmpty())
                assertTrue(walletB.query { byTag("shared-tag") }.isEmpty())

                // Wallet A still sees its own tag
                assertEquals(setOf("shared-tag"), walletA.getAllTags())
            }
        }
    }

    // ========== Metadata write API (CredentialTagging) ==========

    @Test
    fun `addMetadata merges values and getMetadata returns notes and tags`() {
        runBlocking {
            newDataSource().use { dataSource ->
                val wallet = newWallet(dataSource)
                wallet.store(credential("cred-meta"))
                wallet.tagCredential("cred-meta", setOf("meta-tag"))

                assertTrue(wallet.addMetadata("cred-meta", mapOf("source" to "issuer.com", "attempt" to 1L)))
                assertTrue(wallet.addMetadata("cred-meta", mapOf("attempt" to 2L)))
                assertTrue(wallet.updateNotes("cred-meta", "some notes"))

                val metadata = wallet.getMetadata("cred-meta")
                assertNotNull(metadata)
                assertEquals("cred-meta", metadata.credentialId)
                assertEquals("some notes", metadata.notes)
                assertEquals(setOf("meta-tag"), metadata.tags)
                assertEquals("issuer.com", metadata.metadata["source"])
                assertEquals(2L, metadata.metadata["attempt"])

                assertFalse(wallet.addMetadata("no-such-credential", mapOf("k" to "v")))
                assertNull(wallet.getMetadata("no-such-credential"))
            }
        }
    }

    // ========== Collections write API (CredentialCollections) ==========

    @Test
    fun `collection lifecycle - create add list remove delete`() {
        runBlocking {
            newDataSource().use { dataSource ->
                val wallet = newWallet(dataSource)
                wallet.store(credential("cred-coll"))

                val collectionId = wallet.createCollection("Work", "Work credentials")
                val collection = wallet.getCollection(collectionId)
                assertNotNull(collection)
                assertEquals("Work", collection.name)
                assertEquals("Work credentials", collection.description)
                assertEquals(0, collection.credentialCount)

                assertTrue(wallet.addToCollection("cred-coll", collectionId))
                // Idempotent re-add
                assertTrue(wallet.addToCollection("cred-coll", collectionId))
                assertEquals(1, wallet.getCollection(collectionId)?.credentialCount)
                assertEquals(
                    listOf("cred-coll"),
                    wallet.getCredentialsInCollection(collectionId).map { it.id?.value }
                )
                assertEquals(listOf(collectionId), wallet.listCollections().map { it.id })

                assertTrue(wallet.removeFromCollection("cred-coll", collectionId))
                assertFalse(wallet.removeFromCollection("cred-coll", collectionId))
                assertTrue(wallet.getCredentialsInCollection(collectionId).isEmpty())

                assertTrue(wallet.deleteCollection(collectionId))
                assertNull(wallet.getCollection(collectionId))
                assertFalse(wallet.deleteCollection(collectionId))
            }
        }
    }

    @Test
    fun `addToCollection returns false for unknown credential or collection`() {
        runBlocking {
            newDataSource().use { dataSource ->
                val wallet = newWallet(dataSource)
                wallet.store(credential("cred-known"))
                val collectionId = wallet.createCollection("Known")

                assertFalse(wallet.addToCollection("no-such-credential", collectionId))
                assertFalse(wallet.addToCollection("cred-known", "no-such-collection"))
            }
        }
    }

    @Test
    fun `collections are isolated between wallets sharing the same database`() {
        runBlocking {
            newDataSource().use { dataSource ->
                val walletA = newWallet(dataSource)
                val walletB = DatabaseWallet.create(
                    walletId = "wallet-other",
                    walletDid = "did:key:z6MkOtherWallet",
                    holderDid = "did:key:z6MkOtherHolder",
                    dataSource = dataSource
                )
                walletA.store(credential("cred-a"))
                val collectionA = walletA.createCollection("A's collection")
                walletA.addToCollection("cred-a", collectionA)

                // Wallet B sees neither the collection nor its contents
                assertNull(walletB.getCollection(collectionA))
                assertTrue(walletB.listCollections().isEmpty())
                assertTrue(walletB.getCredentialsInCollection(collectionA).isEmpty())
                assertTrue(walletB.query { byCollection(collectionA) }.isEmpty())

                // Wallet B cannot mutate A's collection
                assertFalse(walletB.addToCollection("cred-a", collectionA))
                assertFalse(walletB.removeFromCollection("cred-a", collectionA))
                assertFalse(walletB.deleteCollection(collectionA))
                assertEquals(1, walletA.getCollection(collectionA)?.credentialCount)
            }
        }
    }

    @Test
    fun `deleting a credential cleans up its tags and collection memberships`() {
        runBlocking {
            newDataSource().use { dataSource ->
                val wallet = newWallet(dataSource)
                wallet.store(credential("cred-cascade"))
                val collectionId = wallet.createCollection("Cascade")
                wallet.addToCollection("cred-cascade", collectionId)
                wallet.tagCredential("cred-cascade", setOf("cascade-tag"))

                assertTrue(wallet.delete("cred-cascade"))

                assertTrue(wallet.getAllTags().isEmpty())
                assertEquals(0, wallet.getCollection(collectionId)?.credentialCount)
                assertTrue(wallet.getCredentialsInCollection(collectionId).isEmpty())
            }
        }
    }

    // ========== close() ownership (P1: Hikari pool leak) ==========

    @Test
    fun `close shuts down the pool when the wallet owns it`() {
        runBlocking {
            val dataSource = newDataSource()
            val wallet = newWallet(dataSource, ownsDataSource = true)
            wallet.store(credential("cred-before-close"))

            wallet.close()

            assertTrue(dataSource.isClosed, "Wallet-owned HikariDataSource must be closed by wallet.close()")
            assertFailsWith<WalletException.StorageError> {
                wallet.store(credential("cred-after-close"))
            }
        }
    }

    @Test
    fun `close does not touch an injected DataSource`() {
        runBlocking {
            newDataSource().use { dataSource ->
                val wallet = newWallet(dataSource, ownsDataSource = false)
                wallet.store(credential("cred-1"))

                wallet.close()

                assertFalse(dataSource.isClosed, "close() must not shut down an externally managed pool")
                // The injected pool keeps working after the wallet is closed
                assertNotNull(wallet.get("cred-1"))
            }
        }
    }

    @Test
    fun `close is idempotent`() {
        runBlocking {
            val dataSource = newDataSource()
            val wallet = newWallet(dataSource, ownsDataSource = true)

            wallet.close()
            wallet.close()

            assertTrue(dataSource.isClosed)
        }
    }
}
