package org.trustweave.did.resolver

import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests for [CachingDidResolver]: hit/miss, TTL expiry (injected clock),
 * `nextUpdate` honoring, LRU eviction, failure non-caching, invalidate/clear.
 */
class CachingDidResolverTest {

    /** Deterministic, manually advanced clock. */
    private class MutableClock(start: Instant) : Clock {
        var current: Instant = start
        override fun now(): Instant = current
        fun advance(duration: Duration) {
            current += duration
        }
    }

    /** Delegate stub that counts calls per DID. */
    private class CountingResolver(
        private val handler: (Did) -> DidResolutionResult
    ) : DidResolver {
        val calls = mutableMapOf<String, Int>()
        val totalCalls: Int get() = calls.values.sum()

        override suspend fun resolve(did: Did): DidResolutionResult {
            calls.merge(did.value, 1, Int::plus)
            return handler(did)
        }
    }

    private val epoch = Instant.parse("2026-01-01T00:00:00Z")

    private fun success(
        did: Did,
        nextUpdate: Instant? = null,
        deactivated: Boolean = false
    ): DidResolutionResult.Success =
        DidResolutionResult.Success(
            document = DidDocument(id = did),
            documentMetadata = DidDocumentMetadata(nextUpdate = nextUpdate, deactivated = deactivated)
        )

    private fun notFound(did: Did): DidResolutionResult =
        DidResolutionResult.Failure.NotFound(did = did, reason = "not found")

    // ─── Hit / miss ───

    @Test
    fun `second resolve of the same DID is a cache hit`() = runBlocking {
        val did = Did("did:example:hit")
        val delegate = CountingResolver { success(it) }
        val resolver = CachingDidResolver(delegate, clock = MutableClock(epoch))

        val first = resolver.resolve(did)
        val second = resolver.resolve(did)

        assertEquals(1, delegate.totalCalls, "delegate must be called once")
        assertSame(first, second, "cached Success instance must be returned")
    }

    @Test
    fun `different DIDs are independent cache entries`() = runBlocking {
        val delegate = CountingResolver { success(it) }
        val resolver = CachingDidResolver(delegate, clock = MutableClock(epoch))

        resolver.resolve(Did("did:example:a"))
        resolver.resolve(Did("did:example:b"))
        resolver.resolve(Did("did:example:a"))
        resolver.resolve(Did("did:example:b"))

        assertEquals(1, delegate.calls["did:example:a"])
        assertEquals(1, delegate.calls["did:example:b"])
        assertEquals(2, resolver.size)
    }

    // ─── TTL expiry ───

    @Test
    fun `entry expires after ttl and delegate is consulted again`() = runBlocking {
        val did = Did("did:example:ttl")
        val clock = MutableClock(epoch)
        val delegate = CountingResolver { success(it) }
        val resolver = CachingDidResolver(delegate, ttl = 5.minutes, clock = clock)

        resolver.resolve(did)
        clock.advance(4.minutes + 59.seconds)
        resolver.resolve(did)
        assertEquals(1, delegate.totalCalls, "entry must still be fresh just before ttl")

        clock.advance(2.seconds) // now past the 5-minute ttl
        resolver.resolve(did)
        assertEquals(2, delegate.totalCalls, "expired entry must trigger re-resolution")
    }

    // ─── nextUpdate ───

    @Test
    fun `nextUpdate earlier than ttl shortens the cache lifetime`() = runBlocking {
        val did = Did("did:example:nextupdate")
        val clock = MutableClock(epoch)
        val delegate = CountingResolver { success(it, nextUpdate = clock.now() + 1.minutes) }
        val resolver = CachingDidResolver(delegate, ttl = 5.minutes, clock = clock)

        resolver.resolve(did)
        clock.advance(30.seconds)
        resolver.resolve(did)
        assertEquals(1, delegate.totalCalls, "entry must be fresh before nextUpdate")

        clock.advance(31.seconds) // past nextUpdate (1 min), well within ttl (5 min)
        resolver.resolve(did)
        assertEquals(2, delegate.totalCalls, "nextUpdate must override the longer ttl")
    }

    @Test
    fun `nextUpdate in the past means the result is not cached`() = runBlocking {
        val did = Did("did:example:stale")
        val clock = MutableClock(epoch)
        val delegate = CountingResolver { success(it, nextUpdate = clock.now() - 1.minutes) }
        val resolver = CachingDidResolver(delegate, clock = clock)

        resolver.resolve(did)
        resolver.resolve(did)

        assertEquals(2, delegate.totalCalls, "already-due-for-update document must not be cached")
        assertEquals(0, resolver.size)
    }

    @Test
    fun `nextUpdate later than ttl does not extend the cache lifetime`() = runBlocking {
        val did = Did("did:example:longnext")
        val clock = MutableClock(epoch)
        val delegate = CountingResolver { success(it, nextUpdate = clock.now() + 60.minutes) }
        val resolver = CachingDidResolver(delegate, ttl = 5.minutes, clock = clock)

        resolver.resolve(did)
        clock.advance(6.minutes)
        resolver.resolve(did)

        assertEquals(2, delegate.totalCalls, "ttl must still bound entries with a distant nextUpdate")
    }

    // ─── Failures and deactivated documents ───

    @Test
    fun `failures are never cached`() = runBlocking {
        val did = Did("did:example:missing")
        val delegate = CountingResolver { notFound(it) }
        val resolver = CachingDidResolver(delegate, clock = MutableClock(epoch))

        val first = resolver.resolve(did)
        val second = resolver.resolve(did)

        assertTrue(first is DidResolutionResult.Failure.NotFound)
        assertTrue(second is DidResolutionResult.Failure.NotFound)
        assertEquals(2, delegate.totalCalls, "failures must hit the delegate every time")
        assertEquals(0, resolver.size)
    }

    @Test
    fun `success after a non-cached failure is cached`() = runBlocking {
        val did = Did("did:example:flaky")
        var fail = true
        val delegate = CountingResolver { if (fail) notFound(it) else success(it) }
        val resolver = CachingDidResolver(delegate, clock = MutableClock(epoch))

        resolver.resolve(did)
        fail = false
        resolver.resolve(did)
        resolver.resolve(did)

        assertEquals(2, delegate.totalCalls, "the eventual success must be cached")
    }

    @Test
    fun `deactivated documents are cached normally`() = runBlocking {
        // Deactivation is terminal (W3C DID Core §7.3), so it is safe to cache the
        // deactivated result like any other success.
        val did = Did("did:example:deactivated")
        val delegate = CountingResolver { success(it, deactivated = true) }
        val resolver = CachingDidResolver(delegate, clock = MutableClock(epoch))

        resolver.resolve(did)
        val second = resolver.resolve(did)

        assertEquals(1, delegate.totalCalls)
        assertTrue((second as DidResolutionResult.Success).documentMetadata.deactivated)
    }

    // ─── LRU eviction ───

    @Test
    fun `least recently used entry is evicted when maxSize is exceeded`() = runBlocking {
        val delegate = CountingResolver { success(it) }
        val resolver = CachingDidResolver(delegate, maxSize = 2, clock = MutableClock(epoch))

        val a = Did("did:example:a")
        val b = Did("did:example:b")
        val c = Did("did:example:c")

        resolver.resolve(a)
        resolver.resolve(b)
        resolver.resolve(a) // touch a → b is now least recently used
        resolver.resolve(c) // exceeds maxSize=2 → evicts b

        assertEquals(2, resolver.size)

        resolver.resolve(a)
        assertEquals(1, delegate.calls["did:example:a"], "a must still be cached")
        resolver.resolve(c)
        assertEquals(1, delegate.calls["did:example:c"], "c must still be cached")
        resolver.resolve(b)
        assertEquals(2, delegate.calls["did:example:b"], "b must have been evicted")
    }

    // ─── invalidate / clear ───

    @Test
    fun `invalidate drops a single entry`() = runBlocking {
        val a = Did("did:example:a")
        val b = Did("did:example:b")
        val delegate = CountingResolver { success(it) }
        val resolver = CachingDidResolver(delegate, clock = MutableClock(epoch))

        resolver.resolve(a)
        resolver.resolve(b)
        resolver.invalidate(a)

        resolver.resolve(a)
        resolver.resolve(b)

        assertEquals(2, delegate.calls["did:example:a"], "invalidated entry must be re-resolved")
        assertEquals(1, delegate.calls["did:example:b"], "other entries must be untouched")
    }

    @Test
    fun `clear drops all entries`() = runBlocking {
        val a = Did("did:example:a")
        val b = Did("did:example:b")
        val delegate = CountingResolver { success(it) }
        val resolver = CachingDidResolver(delegate, clock = MutableClock(epoch))

        resolver.resolve(a)
        resolver.resolve(b)
        resolver.clear()
        assertEquals(0, resolver.size)

        resolver.resolve(a)
        resolver.resolve(b)
        assertEquals(2, delegate.calls["did:example:a"])
        assertEquals(2, delegate.calls["did:example:b"])
    }

    // ─── Constructor validation ───

    @Test
    fun `non-positive ttl and maxSize are rejected`() {
        val delegate = CountingResolver { success(it) }
        assertThrows<IllegalArgumentException> { CachingDidResolver(delegate, ttl = 0.seconds) }
        assertThrows<IllegalArgumentException> { CachingDidResolver(delegate, maxSize = 0) }
    }
}
