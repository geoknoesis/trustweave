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

    /** DatabaseWallet exposes no tagging API yet, so tag rows are inserted directly. */
    private fun tagCredential(dataSource: DataSource, credentialId: String, vararg tags: String) {
        dataSource.connection.use { conn ->
            tags.forEach { tag ->
                conn.prepareStatement("INSERT INTO credential_tags (credential_id, tag) VALUES (?, ?)").use { stmt ->
                    stmt.setString(1, credentialId)
                    stmt.setString(2, tag)
                    stmt.executeUpdate()
                }
            }
        }
    }

    /** DatabaseWallet exposes no collections API yet, so collection rows are inserted directly. */
    private fun createCollection(dataSource: DataSource, collectionId: String, walletId: String = "wallet-test") {
        dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT INTO collections (id, wallet_id, name) VALUES (?, ?, ?)").use { stmt ->
                stmt.setString(1, collectionId)
                stmt.setString(2, walletId)
                stmt.setString(3, "Collection $collectionId")
                stmt.executeUpdate()
            }
        }
    }

    private fun addToCollection(dataSource: DataSource, credentialId: String, collectionId: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO credential_collections (credential_id, collection_id) VALUES (?, ?)"
            ).use { stmt ->
                stmt.setString(1, credentialId)
                stmt.setString(2, collectionId)
                stmt.executeUpdate()
            }
        }
    }

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
                tagCredential(dataSource, "cred-tagged", "important")

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
                tagCredential(dataSource, "cred-both", "a", "b")
                tagCredential(dataSource, "cred-one", "a")

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
                createCollection(dataSource, "collection-1")
                addToCollection(dataSource, "cred-in", "collection-1")

                val results = wallet.query { byCollection("collection-1") }

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
                tagCredential(dataSource, "cred-match", "important")
                tagCredential(dataSource, "cred-wrong-type", "important")

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
