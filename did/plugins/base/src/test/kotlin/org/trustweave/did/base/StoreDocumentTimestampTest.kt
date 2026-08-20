package org.trustweave.did.base

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.KeyManagementService
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Timestamp-fidelity tests for [AbstractDidMethod.storeDocument].
 *
 * DID Resolution 1.0 §4.3 defines `updated` as "the timestamp of the last Update operation for
 * the document version which was resolved". `storeDocument` runs on **every successful resolve**
 * as a cache-store — a cache-store is not an Update operation, so it must not touch `updated`.
 * The library used to bump `updated` to `now` on every store, which made a live DID report a
 * fabricated timestamp that advanced on every read.
 *
 * The "when did we last fetch this?" question is real, but it is [DidResolutionMetadata.retrieved]
 * (§4.2 resolution-process metadata), not §4.3 document metadata. These tests pin both halves:
 * `updated` only moves for genuine Update operations, while the last-fetch bookkeeping keeps
 * advancing so that cache-freshness checks still work.
 */
class StoreDocumentTimestampTest {
    private companion object {
        const val DID = "did:test:abc123"
    }

    /**
     * Minimal concrete [AbstractDidMethod] whose `resolveDid` mirrors the real
     * `resolveFromHttp` / `resolveFromBlockchain` shape: fetch the document from a (simulated)
     * remote, cache it with [storeDocument], then report the stored metadata.
     */
    private class TestDidMethod(
        kms: KeyManagementService,
        private val remote: DidDocument,
    ) : AbstractDidMethod("test", kms) {
        override suspend fun createDid(options: DidCreationOptions): DidDocument =
            throw UnsupportedOperationException("not needed for these tests")

        override suspend fun resolveDid(did: Did): DidResolutionResult {
            storeDocument(did, remote)
            val metadata = getDocumentMetadata(did)
            return DidMethodUtils.createSuccessResolutionResult(
                remote,
                method,
                metadata?.created,
                metadata?.updated,
                metadata?.deactivated ?: false,
                retrieved = getLastFetched(did),
            )
        }

        suspend fun cacheStore(document: DidDocument) = storeDocument(document.id.value, document)

        fun metadataOf(did: Any): DidDocumentMetadata? = getDocumentMetadata(did)

        fun lastFetchedOf(did: Any) = getLastFetched(did)
    }

    private fun document(did: String): DidDocument =
        DidMethodUtils.buildDidDocument(
            did = did,
            verificationMethod =
                listOf(
                    DidMethodUtils.createVerificationMethod(
                        did = did,
                        keyHandle =
                            KeyHandle(
                                id = KeyId("key-1"),
                                algorithm = "Ed25519",
                                publicKeyMultibase = "z6Mk",
                            ),
                        algorithm = "Ed25519",
                    ),
                ),
        )

    private fun method(): TestDidMethod = TestDidMethod(InMemoryKeyManagementService(), document(DID))

    @Test
    fun `a cache-store seeds created once and never sets updated`() =
        runBlocking {
            val method = method()
            val doc = document(DID)

            val before = Clock.System.now()
            method.cacheStore(doc)
            val after = Clock.System.now()

            val first = method.metadataOf(DID)
            assertNotNull(first, "first store must seed document metadata")
            val created = first.created
            assertNotNull(created, "§4.3: created SHOULD be present")
            assertTrue(created in before..after, "created ($created) must fall in the store's wall-clock window")
            assertNull(
                first.updated,
                "§4.3: `updated` is the timestamp of the last Update operation — a cache-store is not one",
            )

            delay(10)
            method.cacheStore(doc)
            val second = method.metadataOf(DID)
            assertEquals(created, second?.created, "a re-store must not re-seed `created`")
            assertNull(second?.updated, "a re-store must not fabricate an `updated` timestamp")

            delay(10)
            method.cacheStore(doc)
            val third = method.metadataOf(DID)
            assertEquals(created, third?.created, "a third store must still not re-seed `created`")
            assertNull(third?.updated, "a third store must still not fabricate an `updated` timestamp")
        }

    @Test
    fun `a cache-store advances the internal last-fetched bookkeeping`() =
        runBlocking {
            val method = method()
            val doc = document(DID)

            method.cacheStore(doc)
            val firstFetch = method.lastFetchedOf(DID)
            assertNotNull(firstFetch, "a cache-store must record when the document was fetched")

            delay(10)
            method.cacheStore(doc)
            val secondFetch = method.lastFetchedOf(DID)
            assertNotNull(secondFetch)
            assertTrue(
                secondFetch > firstFetch,
                "last-fetched must advance on every cache-store ($secondFetch must be after $firstFetch)",
            )
        }

    @Test
    fun `repeated resolves of a live DID do not advance updated but do advance retrieved`() =
        runBlocking {
            val method = method()

            val first = method.resolveDid(Did(DID))
            assertTrue(first is DidResolutionResult.Success, "expected Success, got $first")
            delay(10)
            val second = method.resolveDid(Did(DID))
            assertTrue(second is DidResolutionResult.Success, "expected Success, got $second")
            delay(10)
            val third = method.resolveDid(Did(DID))
            assertTrue(third is DidResolutionResult.Success, "expected Success, got $third")

            assertNull(first.documentMetadata.updated, "a resolve is not an Update operation (§4.3)")
            assertNull(second.documentMetadata.updated, "`updated` must not appear on a second resolve")
            assertNull(third.documentMetadata.updated, "`updated` must not appear on a third resolve")

            assertEquals(
                first.documentMetadata.created,
                third.documentMetadata.created,
                "`created` must stay pinned at the first store, not drift to each resolve's fetch time",
            )

            // The fetch time is still reported — as §4.2 resolution-process metadata, where it
            // belongs — so cache-freshness checks keep working.
            val firstRetrieved = first.resolutionMetadata.retrieved
            val thirdRetrieved = third.resolutionMetadata.retrieved
            assertNotNull(firstRetrieved, "§4.2 `retrieved` must report when the document was fetched")
            assertNotNull(thirdRetrieved)
            assertTrue(
                thirdRetrieved > firstRetrieved,
                "`retrieved` must advance across resolves ($thirdRetrieved must be after $firstRetrieved)",
            )
        }

    @Test
    fun `a real updateDid sets updated and a later cache-store leaves it alone`() =
        runBlocking {
            val method = method()
            val doc = document(DID)

            // Seed the store via an ordinary resolve so updateDid has a current document.
            method.resolveDid(Did(DID))
            assertNull(method.metadataOf(DID)?.updated, "precondition: no Update operation has happened yet")

            val beforeUpdate = Clock.System.now()
            method.updateDid(Did(DID)) { current -> current.copy(alsoKnownAs = emptyList()) }
            val afterUpdate = Clock.System.now()

            val updated = method.metadataOf(DID)?.updated
            assertNotNull(updated, "updateDid is a genuine Update operation and MUST set `updated`")
            assertTrue(
                updated in beforeUpdate..afterUpdate,
                "expected updated ($updated) to fall in the updateDid wall-clock window " +
                    "[$beforeUpdate, $afterUpdate]",
            )

            // A later cache-store (i.e. another resolve) must not move the Update timestamp.
            delay(10)
            method.cacheStore(doc)
            assertEquals(updated, method.metadataOf(DID)?.updated, "a cache-store must not move `updated`")

            delay(10)
            val result = method.resolveDid(Did(DID))
            assertTrue(result is DidResolutionResult.Success)
            assertEquals(
                updated,
                result.documentMetadata.updated,
                "a resolve after an Update must report the Update timestamp, not the fetch time",
            )
        }
}
