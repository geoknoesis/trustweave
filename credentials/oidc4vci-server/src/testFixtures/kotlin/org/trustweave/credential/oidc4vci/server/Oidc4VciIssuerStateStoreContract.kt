package org.trustweave.credential.oidc4vci.server

import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract every [Oidc4VciIssuerStateStore] must satisfy. Extend it and return a fresh, empty store
 * from [newStore]:
 *
 * ```kotlin
 * class JdbcStateStoreTest : Oidc4VciIssuerStateStoreContract() {
 *     override fun newStore() = JdbcOidc4VciIssuerStateStore(freshDataSource())
 * }
 * ```
 */
abstract class Oidc4VciIssuerStateStoreContract {
    /** A new store with no entries; each test gets its own. */
    protected abstract fun newStore(): Oidc4VciIssuerStateStore

    private fun offer(issuedAt: Long = 1_000L) =
        OfferState(listOf("UniversityDegree"), txCode = null, txCodeValue = null, issuedAt = issuedAt)

    private fun token(
        issuedAt: Long = 1_000L,
        cNonce: String = "n0",
    ) = TokenEntry(offer(), issuedAt = issuedAt, cNonce = cNonce, cNonceIssuedAt = issuedAt)

    private fun deferred(issuedAt: Long = 1_000L) = DeferredEntry("{\"vc\":1}", issuedAt)

    @Test
    fun `an offer is consumed exactly once`() {
        val store = newStore()
        assertTrue(store.putOffer("code", offer(), 10))
        assertEquals(1, store.offerCount())
        assertEquals(offer(), store.consumeOffer("code"))
        assertNull(store.consumeOffer("code"))
        assertEquals(0, store.offerCount())
        assertNull(store.consumeOffer("never-existed"))
    }

    @Test
    fun `concurrent consumers of one offer or deferred credential see exactly one winner`() {
        val store = newStore()
        store.putOffer("code", offer(), 10)
        store.putDeferred("tx", deferred(), 10)
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        try {
            for (consume in listOf<(Oidc4VciIssuerStateStore) -> Any?>({ it.consumeOffer("code") }, { it.consumeDeferred("tx") })) {
                val start = CountDownLatch(1)
                val results =
                    (1..threads).map {
                        pool.submit(
                            Callable {
                                start.await()
                                consume(store)
                            },
                        )
                    }
                start.countDown()
                assertEquals(1, results.count { it.get() != null })
            }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `puts refuse at capacity instead of evicting and replacing a key is not growth`() {
        val store = newStore()
        assertTrue(store.putOffer("a", offer(), 2))
        assertTrue(store.putOffer("b", offer(), 2))
        assertFalse(store.putOffer("c", offer(), 2))
        assertEquals(2, store.offerCount())
        assertNotNull(store.consumeOffer("a"), "a live entry must not have been evicted")
        assertTrue(store.putOffer("c", offer(), 2))

        assertTrue(store.putToken("t1", token(), 1))
        assertFalse(store.putToken("t2", token(), 1))
        assertEquals(1, store.tokenCount())

        assertTrue(store.putDeferred("d1", deferred(), 1))
        assertFalse(store.putDeferred("d2", deferred(), 1))
        assertEquals(1, store.deferredCount())
    }

    @Test
    fun `capacity is enforced atomically under concurrent inserts`() {
        val store = newStore()
        val pool = Executors.newFixedThreadPool(8)
        try {
            val start = CountDownLatch(1)
            val results =
                (1..40).map { n ->
                    pool.submit(
                        Callable {
                            start.await()
                            store.putToken("t$n", token(), 5)
                        },
                    )
                }
            start.countDown()
            assertEquals(5, results.count { it.get() })
            assertEquals(5, store.tokenCount())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `tokens can be read and removed`() {
        val store = newStore()
        assertNull(store.getToken("t"))
        store.putToken("t", token(), 10)
        assertEquals(token(), store.getToken("t"))
        store.removeToken("t")
        assertNull(store.getToken("t"))
        store.removeToken("t") // no-op
    }

    @Test
    fun `updateToken applies atomically and reports an unknown token as null`() {
        val store = newStore()
        assertNull(store.updateToken("missing") { it to "never" })
        store.putToken("t", token(cNonce = "n0"), 10)

        val result = store.updateToken("t") { it.copy(cNonce = "n1") to "done" }

        assertEquals("done", result)
        assertEquals("n1", store.getToken("t")?.cNonce)
    }

    @Test
    fun `concurrent single-use nonce consumption has exactly one winner`() {
        val store = newStore()
        store.putToken("t", token(cNonce = "n0"), 10)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val start = CountDownLatch(1)
            val results =
                (1..16).map {
                    pool.submit(
                        Callable {
                            start.await()
                            store.updateToken("t") { e ->
                                if (e.cNonce == "n0") e.copy(cNonce = "rotated") to true else e to false
                            }
                        },
                    )
                }
            start.countDown()
            assertEquals(1, results.count { it.get() == true })
            assertEquals("rotated", store.getToken("t")?.cNonce)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `purgeExpired drops only entries at or before each cutoff and counts them`() {
        val store = newStore()
        store.putOffer("old-offer", offer(issuedAt = 100), 10)
        store.putOffer("new-offer", offer(issuedAt = 900), 10)
        store.putToken("old-token", token(issuedAt = 100), 10)
        store.putToken("new-token", token(issuedAt = 900), 10)
        store.putDeferred("old-d", deferred(issuedAt = 100), 10)
        store.putDeferred("new-d", deferred(issuedAt = 900), 10)

        val dropped = store.purgeExpired(offersIssuedAtOrBefore = 100, tokensIssuedAtOrBefore = 100, deferredIssuedAtOrBefore = 100)

        assertEquals(3, dropped)
        assertNull(store.consumeOffer("old-offer"))
        assertNotNull(store.consumeOffer("new-offer"))
        assertNull(store.getToken("old-token"))
        assertNotNull(store.getToken("new-token"))
        assertNull(store.consumeDeferred("old-d"))
        assertNotNull(store.consumeDeferred("new-d"))
        assertEquals(0, store.purgeExpired(100, 100, 100))
    }

    @Test
    fun `each kind of entry is purged against its own cutoff`() {
        val store = newStore()
        store.putOffer("o", offer(issuedAt = 500), 10)
        store.putToken("t", token(issuedAt = 500), 10)
        store.putDeferred("d", deferred(issuedAt = 500), 10)

        assertEquals(1, store.purgeExpired(offersIssuedAtOrBefore = 500, tokensIssuedAtOrBefore = 0, deferredIssuedAtOrBefore = 0))
        assertEquals(0, store.offerCount())
        assertEquals(1, store.tokenCount())
        assertEquals(1, store.deferredCount())
    }

    @Test
    fun `deferred credentials round trip once`() {
        val store = newStore()
        store.putDeferred("tx", deferred(), 10)
        assertEquals(deferred(), store.consumeDeferred("tx"))
        assertNull(store.consumeDeferred("tx"))
    }
}
