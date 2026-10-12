package org.trustweave.did.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class InMemoryDocumentStorageBehaviourTest {
    private val alice = Did("did:example:alice")
    private val bob = Did("did:example:bob")
    private val aliceDoc = DidDocument(id = alice)

    @Test
    fun `reports its backend`() {
        assertEquals(StorageBackend.IN_MEMORY, InMemoryDocumentStorage().backend)
        val alias: InMemoryDidDocumentStorage = InMemoryDocumentStorage()
        assertEquals(StorageBackend.IN_MEMORY, alias.backend)
    }

    @Test
    fun `unknown DIDs are absent`() =
        runTest {
            val storage = InMemoryDocumentStorage()
            assertNull(storage.get(alice))
            assertFalse(storage.exists(alice))
            assertFalse(storage.delete(alice))
            assertEquals(0, storage.size())
        }

    @Test
    fun `store then get returns document and supplied metadata`() =
        runTest {
            val storage = InMemoryDocumentStorage()
            val meta = DidDocumentMetadata(created = Instant.fromEpochSeconds(100))
            storage.store(alice, aliceDoc, meta)

            val (doc, stored) = assertNotNull(storage.get(alice))
            assertEquals(aliceDoc, doc)
            assertEquals(meta, stored)
            assertTrue(storage.exists(alice))
            assertEquals(1, storage.size())
        }

    @Test
    fun `store without metadata defaults to empty metadata`() =
        runTest {
            val storage = InMemoryDocumentStorage()
            storage.store(alice, aliceDoc)
            assertEquals(DidDocumentMetadata(), storage.get(alice)!!.second)
        }

    @Test
    fun `storing again overwrites document and metadata`() =
        runTest {
            val storage = InMemoryDocumentStorage()
            storage.store(alice, aliceDoc, DidDocumentMetadata(created = Instant.fromEpochSeconds(1)))
            val replacement = aliceDoc.copy(controller = listOf(bob))
            storage.store(alice, replacement)

            val (doc, meta) = storage.get(alice)!!
            assertEquals(replacement, doc)
            assertEquals(DidDocumentMetadata(), meta)
            assertEquals(1, storage.size())
        }

    @Test
    fun `documents are keyed per DID`() =
        runTest {
            val storage = InMemoryDocumentStorage()
            storage.store(alice, aliceDoc)
            storage.store(bob, DidDocument(id = bob))
            assertEquals(2, storage.size())
            assertEquals(bob, storage.get(bob)!!.first.id)
            assertEquals(alice, storage.get(alice)!!.first.id)
        }

    @Test
    fun `update applies the updater and stamps the updated time while keeping other metadata`() =
        runTest {
            val storage = InMemoryDocumentStorage()
            val created = Instant.fromEpochSeconds(100)
            storage.store(alice, aliceDoc, DidDocumentMetadata(created = created))

            val before =
                kotlin.time.Clock.System
                    .now()
            val result = storage.update(alice) { it.copy(controller = listOf(bob)) }
            val after =
                kotlin.time.Clock.System
                    .now()

            assertEquals(listOf(bob), result.controller)
            val (doc, meta) = storage.get(alice)!!
            assertEquals(result, doc)
            assertEquals(created, meta.created)
            assertTrue(meta.updated!! in before..after)
        }

    @Test
    fun `update of an unknown DID fails with NotFound naming the DID`() =
        runTest {
            val storage = InMemoryDocumentStorage()
            val error = assertFailsWith<TrustWeaveException.NotFound> { storage.update(alice) { it } }
            assertEquals(alice.value, error.resource)
            assertNull(storage.get(alice))
        }

    @Test
    fun `a throwing updater leaves the stored document untouched and does not wedge the store`() =
        runTest {
            val storage = InMemoryDocumentStorage()
            storage.store(alice, aliceDoc)
            assertFailsWith<IllegalStateException> { storage.update(alice) { error("nope") } }

            assertEquals(aliceDoc, storage.get(alice)!!.first)
            assertNull(storage.get(alice)!!.second.updated)
            // The mutex must have been released: a later operation still completes.
            assertEquals(aliceDoc, storage.update(alice) { it })
        }

    @Test
    fun `deactivate replaces the document and stamps updated even for a never stored DID`() =
        runTest {
            val storage = InMemoryDocumentStorage()
            storage.store(alice, aliceDoc)
            val deactivated = aliceDoc.copy(controller = emptyList(), authentication = emptyList())
            storage.deactivate(alice, deactivated)
            assertEquals(deactivated, storage.get(alice)!!.first)
            assertNotNull(storage.get(alice)!!.second.updated)

            storage.deactivate(bob, DidDocument(id = bob))
            assertTrue(storage.exists(bob))
        }

    @Test
    fun `delete removes both document and metadata`() =
        runTest {
            val storage = InMemoryDocumentStorage()
            storage.store(alice, aliceDoc, DidDocumentMetadata(created = Instant.fromEpochSeconds(5)))
            assertTrue(storage.delete(alice))
            assertFalse(storage.exists(alice))
            assertNull(storage.get(alice))
            assertFalse(storage.delete(alice))

            // Re-storing after deletion must not resurrect old metadata.
            storage.store(alice, aliceDoc)
            assertEquals(DidDocumentMetadata(), storage.get(alice)!!.second)
        }

    @Test
    fun `clear empties the store`() =
        runTest {
            val storage = InMemoryDocumentStorage()
            storage.store(alice, aliceDoc)
            storage.store(bob, DidDocument(id = bob))
            storage.clear()
            assertEquals(0, storage.size())
            assertNull(storage.get(alice))
        }

    @Test
    fun `concurrent read-modify-write updates are not lost`() =
        runTest {
            val storage = InMemoryDocumentStorage()
            storage.store(alice, aliceDoc)
            val workers = 50

            withContext(Dispatchers.Default) {
                coroutineScope {
                    (1..workers)
                        .map { n ->
                            async {
                                storage.update(alice) { doc ->
                                    doc.copy(controller = doc.controller + Did("did:example:c$n"))
                                }
                            }
                        }.awaitAll()
                }
            }

            assertEquals(
                workers,
                storage
                    .get(alice)!!
                    .first.controller.size,
            )
        }
}
