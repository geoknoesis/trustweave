package org.trustweave.did.base

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyManagementService
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** The in-memory caches in [AbstractDidMethod] are bounded; deactivation records are retained. */
class CacheBoundsTest {
    private class M(
        kms: KeyManagementService,
        private val max: Int,
        private val ttl: Duration = Duration.INFINITE,
        private val maxDeact: Int = 1000,
    ) : AbstractDidMethod("test", kms) {
        override val maxCachedDocuments get() = max
        override val cacheTtl get() = ttl
        override val maxDeactivatedRecords get() = maxDeact

        override suspend fun createDid(options: DidCreationOptions): DidDocument = throw UnsupportedOperationException()

        override suspend fun resolveDid(did: Did): DidResolutionResult = throw UnsupportedOperationException()

        suspend fun cache(id: String) = storeDocument(id, DidDocument(id = Did(id)))

        suspend fun markDeactivated(id: String) {
            updateMutex.lock()
            try {
                documentMetadata[id] = DidDocumentMetadata(deactivated = true)
            } finally {
                updateMutex.unlock()
            }
        }

        fun has(id: String) = getStoredDocument(id) != null

        fun count() = cachedDocumentCount()

        fun metaCount() = documentMetadata.size

        fun fetchedCount() = lastFetched.size
    }

    @Test
    fun `live cache is bounded and evicts all three maps together`() =
        runBlocking {
            val m = M(InMemoryKeyManagementService(), max = 10)
            repeat(100) { m.cache("did:test:$it") }
            assertTrue(m.count() <= 10, "count=${m.count()}")
            assertEquals(m.count(), m.metaCount())
            assertEquals(m.count(), m.fetchedCount())
            assertTrue(m.has("did:test:99"), "newest must survive")
            assertTrue(!m.has("did:test:0"), "oldest must be evicted")
        }

    @Test
    fun `ttl expires stale live entries`() =
        runBlocking {
            val m = M(InMemoryKeyManagementService(), max = 100, ttl = 20.milliseconds)
            m.cache("did:test:old")
            Thread.sleep(60)
            m.cache("did:test:new")
            assertNull(m.run { if (has("did:test:old")) 1 else null })
            assertTrue(m.has("did:test:new"))
        }

    @Test
    fun `deactivation records survive lru and ttl eviction`() =
        runBlocking<Unit> {
            val m = M(InMemoryKeyManagementService(), max = 5, ttl = 20.milliseconds)
            m.cache("did:test:dead")
            m.markDeactivated("did:test:dead")
            Thread.sleep(60)
            repeat(50) { m.cache("did:test:$it") }
            assertTrue(m.has("did:test:dead"), "deactivated record must never be silently evicted")
        }

    @Test
    fun `deactivation records are bounded only by their own generous cap`() =
        runBlocking {
            val m = M(InMemoryKeyManagementService(), max = 5, maxDeact = 3)
            repeat(6) {
                m.cache("did:test:d$it")
                m.markDeactivated("did:test:d$it")
                Thread.sleep(2)
            }
            m.cache("did:test:live")
            assertEquals(3, (0 until 6).count { m.has("did:test:d$it") })
        }
}
