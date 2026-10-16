package org.trustweave.trust.dsl.wallet

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.trustweave.testkit.credential.BasicWallet
import org.trustweave.testkit.credential.InMemoryWallet
import org.trustweave.wallet.CredentialOrganization
import org.trustweave.wallet.Wallet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WalletOrganizeDslTest {
    private suspend fun walletWith(vararg ids: String): InMemoryWallet {
        val wallet = InMemoryWallet()
        ids.forEach { wallet.store(walletCredential(it)) }
        return wallet
    }

    @Test
    fun `a collection with credentials and tags is created in one step`() =
        runBlocking {
            val wallet = walletWith("a", "b", "c")

            val result =
                wallet.organize {
                    collection("Education", "degrees and transcripts") {
                        add("a", "b")
                        tag("a", "degree", "verified")
                    }
                }

            assertTrue(result.success)
            assertEquals(1, result.collectionsCreated)
            val created = wallet.listCollections().single()
            assertEquals("Education", created.name)
            assertEquals("degrees and transcripts", created.description)
            assertEquals(setOf("a", "b"), wallet.getCredentialsInCollection(created.id).mapNotNull { it.id?.value }.toSet())
            assertEquals(setOf("degree", "verified"), wallet.getTags("a"))
            assertTrue(wallet.getTags("b").isEmpty())
        }

    @Test
    fun `standalone tag untag metadata and notes are applied in order`() =
        runBlocking {
            val wallet = walletWith("a")

            val result =
                wallet.organize {
                    tag("a", "one", "two", "three")
                    untag("a", "two")
                    metadata("a") {
                        "source" to "registrar"
                        "rank" to 3
                    }
                    notes("a", "checked by hand")
                }

            assertTrue(result.success)
            assertEquals(0, result.collectionsCreated)
            assertEquals(setOf("one", "three"), wallet.getTags("a"))
            val meta = assertNotNull(wallet.getMetadata("a"))
            assertEquals("registrar", meta.metadata["source"])
            assertEquals(3, meta.metadata["rank"])
            assertEquals("checked by hand", meta.notes)
        }

    @Test
    fun `an empty block is a successful no-op`() =
        runBlocking {
            val result = walletWith("a").organize { }

            assertTrue(result.success)
            assertEquals(0, result.collectionsCreated)
            assertTrue(result.errors.isEmpty())
        }

    @Test
    fun `a wallet without organization support is rejected with guidance`() =
        runBlocking {
            val error = assertFailsWith<IllegalStateException> { BasicWallet().organize { tag("a", "x") } }

            assertTrue(error.message!!.contains("does not support organization"), error.message)
            assertTrue(error.message!!.contains("enableOrganization()"))
        }

    /** Delegates to a real wallet but fails for chosen credentials, to exercise per-operation error capture. */
    private class FlakyWallet(
        private val inner: InMemoryWallet,
        private val failingCredential: String,
        private val failure: Throwable = IllegalStateException("backend down"),
    ) : Wallet by inner,
        CredentialOrganization by inner {
        override suspend fun tagCredential(
            credentialId: String,
            tags: Set<String>,
        ): Boolean {
            if (credentialId == failingCredential) throw failure
            return inner.tagCredential(credentialId, tags)
        }

        override suspend fun untagCredential(
            credentialId: String,
            tags: Set<String>,
        ): Boolean {
            if (credentialId == failingCredential) throw failure
            return inner.untagCredential(credentialId, tags)
        }

        override suspend fun addToCollection(
            credentialId: String,
            collectionId: String,
        ): Boolean {
            if (credentialId == failingCredential) throw failure
            return inner.addToCollection(credentialId, collectionId)
        }

        override suspend fun addMetadata(
            credentialId: String,
            metadata: Map<String, Any>,
        ): Boolean {
            if (credentialId == failingCredential) throw failure
            return inner.addMetadata(credentialId, metadata)
        }

        override suspend fun createCollection(
            name: String,
            description: String?,
        ): String {
            if (name == failingCredential) throw failure
            return inner.createCollection(name, description)
        }
    }

    @Test
    fun `failures are collected per operation and the remaining operations still run`() =
        runBlocking {
            val inner = walletWith("good", "bad")
            val wallet = FlakyWallet(inner, failingCredential = "bad")

            val result =
                wallet.organize {
                    collection("Mixed") {
                        add("bad", "good")
                        tag("bad", "x")
                        tag("good", "ok")
                    }
                    tag("bad", "y")
                    untag("bad", "y")
                    tag("good", "later")
                    metadata("bad") { "k" to "v" }
                }

            assertFalse(result.success)
            assertEquals(1, result.collectionsCreated)
            assertEquals(
                listOf(
                    "Failed to add credential bad to collection Mixed: backend down",
                    "Failed to tag credential bad: backend down",
                    "Failed to tag credential bad: backend down",
                    "Failed to untag credential bad: backend down",
                    "Failed to update metadata for credential bad: backend down",
                ),
                result.errors,
            )
            assertEquals(setOf("ok", "later"), inner.getTags("good"))
            assertEquals(1, inner.getCredentialsInCollection(inner.listCollections().single().id).size)
        }

    @Test
    fun `a collection that cannot be created is reported and does not abort the rest`() =
        runBlocking {
            val inner = walletWith("a")
            val wallet = FlakyWallet(inner, failingCredential = "Broken")

            val result =
                wallet.organize {
                    collection("Broken") { add("a") }
                    tag("a", "still-applied")
                }

            assertEquals(0, result.collectionsCreated)
            assertEquals(listOf("Failed to create collection Broken: backend down"), result.errors)
            assertEquals(setOf("still-applied"), inner.getTags("a"))
        }

    @Test
    fun `cancellation is never swallowed as an operation error`() {
        val inner = InMemoryWallet()
        val wallet = FlakyWallet(inner, failingCredential = "a", failure = CancellationException("stop"))

        assertFailsWith<CancellationException> {
            runBlocking {
                inner.store(walletCredential("a"))
                wallet.organize { tag("a", "x") }
            }
        }
    }

    @Test
    fun `boolean failures from the wallet are reported as errors`(): Unit =
        runBlocking {
            val wallet = walletWith("a")

            val result =
                wallet.organize {
                    collection("Mixed") {
                        add("a", "missing")
                        tag("missing", "x")
                    }
                    tag("missing", "y")
                    untag("missing", "y")
                    notes("missing", "n")
                    metadata("missing") { "k" to "v" }
                }

            assertFalse(result.success)
            assertEquals(1, result.collectionsCreated)
            assertEquals(6, result.errors.size, result.errors.toString())
        }
}
