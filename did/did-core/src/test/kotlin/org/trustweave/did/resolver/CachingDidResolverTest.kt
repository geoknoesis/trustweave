package org.trustweave.did.resolver

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.resolution.ResolutionOptions
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Tests for [CachingDidResolver]: hit/miss, TTL expiry (injected clock),
 * `nextUpdate` honoring, LRU eviction, failure non-caching, invalidate/clear.
 */
class CachingDidResolverTest {
    /** Deterministic, manually advanced clock. */
    private class MutableClock(
        start: Instant,
    ) : Clock {
        var current: Instant = start

        override fun now(): Instant = current

        fun advance(duration: Duration) {
            current += duration
        }
    }

    /** Delegate stub that counts calls per DID. */
    private class CountingResolver(
        private val handler: (Did) -> DidResolutionResult,
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
    ): DidResolutionResult.Success =
        DidResolutionResult.Success(
            document = DidDocument(id = did),
            documentMetadata = DidDocumentMetadata(nextUpdate = nextUpdate),
        )

    private fun deactivated(
        did: Did,
        nextUpdate: Instant? = null,
    ): DidResolutionResult.Deactivated =
        DidResolutionResult.Deactivated(
            did = did,
            documentMetadata = DidDocumentMetadata(deactivated = true, nextUpdate = nextUpdate),
        )

    private fun notFound(did: Did): DidResolutionResult = DidResolutionResult.Failure.NotFound(did = did, reason = "not found")

    // ─── Hit / miss ───

    @Test
    fun `second resolve of the same DID is a cache hit`() =
        runBlocking<Unit> {
            val did = Did("did:example:hit")
            val delegate = CountingResolver { success(it) }
            val resolver = CachingDidResolver(delegate, clock = MutableClock(epoch))

            val first = resolver.resolve(did)
            val second = resolver.resolve(did)

            assertEquals(1, delegate.totalCalls, "delegate must be called once")
            assertSame(first, second, "cached Success instance must be returned")
        }

    @Test
    fun `different DIDs are independent cache entries`() =
        runBlocking<Unit> {
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
    fun `entry expires after ttl and delegate is consulted again`() =
        runBlocking<Unit> {
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
    fun `nextUpdate earlier than ttl shortens the cache lifetime`() =
        runBlocking<Unit> {
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
    fun `nextUpdate in the past means the result is not cached`() =
        runBlocking<Unit> {
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
    fun `nextUpdate later than ttl does not extend the cache lifetime`() =
        runBlocking<Unit> {
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
    fun `failures are never cached`() =
        runBlocking<Unit> {
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
    fun `success after a non-cached failure is cached`() =
        runBlocking<Unit> {
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
    fun `deactivated results are cached and served without re-hitting the delegate`() =
        runBlocking<Unit> {
            // Deactivation is terminal (W3C DID Core §7.3), so a Deactivated result is cached
            // exactly like a Success — served from cache on the second lookup.
            val did = Did("did:example:deactivated")
            val delegate = CountingResolver { deactivated(it) }
            val resolver = CachingDidResolver(delegate, clock = MutableClock(epoch))

            val first = resolver.resolve(did)
            val second = resolver.resolve(did)

            assertEquals(1, delegate.totalCalls, "delegate must be called once")
            assertSame(first, second, "cached Deactivated instance must be returned")
            assertTrue((second as DidResolutionResult.Deactivated).documentMetadata.deactivated)
            assertEquals(1, resolver.size)
        }

    @Test
    fun `deactivated entry expires after ttl and delegate is consulted again`() =
        runBlocking<Unit> {
            val did = Did("did:example:deactivated-ttl")
            val clock = MutableClock(epoch)
            val delegate = CountingResolver { deactivated(it) }
            val resolver = CachingDidResolver(delegate, ttl = 5.minutes, clock = clock)

            resolver.resolve(did)
            clock.advance(4.minutes + 59.seconds)
            resolver.resolve(did)
            assertEquals(1, delegate.totalCalls, "entry must still be fresh just before ttl")

            clock.advance(2.seconds) // now past the 5-minute ttl
            resolver.resolve(did)
            assertEquals(2, delegate.totalCalls, "expired entry must trigger re-resolution")
        }

    // ─── LRU eviction ───

    @Test
    fun `least recently used entry is evicted when maxSize is exceeded`() =
        runBlocking<Unit> {
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
    fun `invalidate drops a single entry`() =
        runBlocking<Unit> {
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
    fun `clear drops all entries`() =
        runBlocking<Unit> {
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

    // ─── ResolutionOptions forwarding / noCache (finding I1) ───

    /** Delegate stub that records both call counts and the options each 2-arg call received. */
    private class RecordingOptionsResolver(
        private val handler: (Did) -> DidResolutionResult,
    ) : DidResolver {
        var oneArgCalls = 0
        val optionCalls = mutableListOf<ResolutionOptions>()

        override suspend fun resolve(did: Did): DidResolutionResult {
            oneArgCalls++
            return handler(did)
        }

        override suspend fun resolve(
            did: Did,
            options: ResolutionOptions,
        ): DidResolutionResult {
            optionCalls.add(options)
            return handler(did)
        }
    }

    @Test
    fun `resolve with options forwards them unchanged to the delegate`() =
        runBlocking<Unit> {
            val did = Did("did:example:a")
            val delegate = RecordingOptionsResolver { success(it) }
            val resolver = CachingDidResolver(delegate, clock = MutableClock(epoch))
            val options = ResolutionOptions(accept = "application/did+ld+json")

            resolver.resolve(did, options)

            assertEquals(listOf(options), delegate.optionCalls)
        }

    @Test
    fun `resolve with empty options still hits the cache`() =
        runBlocking<Unit> {
            val did = Did("did:example:a")
            val delegate = RecordingOptionsResolver { success(it) }
            val resolver = CachingDidResolver(delegate, clock = MutableClock(epoch))

            resolver.resolve(did, ResolutionOptions.EMPTY)
            resolver.resolve(did, ResolutionOptions.EMPTY)

            assertEquals(1, delegate.optionCalls.size, "second call must be served from cache, not the delegate")
        }

    @Test
    fun `noCache bypasses a fresh cache entry and forces a delegate round trip`() =
        runBlocking<Unit> {
            val did = Did("did:example:a")
            val delegate = RecordingOptionsResolver { success(it) }
            val resolver = CachingDidResolver(delegate, clock = MutableClock(epoch))

            resolver.resolve(did) // 1-arg call populates the cache
            resolver.resolve(did, ResolutionOptions(noCache = true))

            assertEquals(1, delegate.oneArgCalls)
            assertEquals(1, delegate.optionCalls.size, "noCache must not be served from the cache")
        }

    @Test
    fun `invalid options short-circuit before touching cache or delegate`() =
        runBlocking<Unit> {
            val did = Did("did:example:a")
            val delegate = RecordingOptionsResolver { success(it) }
            val resolver = CachingDidResolver(delegate, clock = MutableClock(epoch))
            val options =
                ResolutionOptions(
                    versionId = "3",
                    versionTime = Instant.parse("2021-05-10T17:00:00Z"),
                )

            val result = resolver.resolve(did, options)

            assertTrue(result is DidResolutionResult.Failure.OptionsError)
            assertEquals(0, delegate.oneArgCalls)
            assertEquals(0, delegate.optionCalls.size)
        }

    // ─── Versioned requests, TTL timing, invalidate races ───

    @Test
    fun `versioned and latest requests do not share a cache entry`() =
        runBlocking<Unit> {
            val did = Did("did:example:v")
            val delegate =
                RecordingOptionsResolver { d -> success(d) }
            val resolver = CachingDidResolver(delegate, clock = MutableClock(epoch))

            resolver.resolve(did, ResolutionOptions(versionId = "1"))
            resolver.resolve(did) // latest: must not be served from the versioned result
            resolver.resolve(did, ResolutionOptions(versionTime = Instant.parse("2020-01-01T00:00:00Z")))
            resolver.resolve(did, ResolutionOptions(additional = mapOf("x" to "y")))

            assertEquals(1, delegate.oneArgCalls)
            assertEquals(3, delegate.optionCalls.size)
            assertEquals(1, resolver.size, "only the latest result may be cached")
        }

    @Test
    fun `a cached latest result is not served for a versioned request`() =
        runBlocking<Unit> {
            val did = Did("did:example:v2")
            val delegate = RecordingOptionsResolver { d -> success(d) }
            val resolver = CachingDidResolver(delegate, clock = MutableClock(epoch))

            resolver.resolve(did)
            resolver.resolve(did, ResolutionOptions(versionId = "1"))

            assertEquals(1, delegate.optionCalls.size)
        }

    @Test
    fun `a versioned success does not mask a later deactivation of the latest`() =
        runBlocking<Unit> {
            val did = Did("did:example:v3")
            var deactivatedNow = false
            val delegate =
                RecordingOptionsResolver { d ->
                    if (deactivatedNow) deactivated(d) else success(d)
                }
            val resolver = CachingDidResolver(delegate, ttl = 10.minutes, clock = MutableClock(epoch))

            resolver.resolve(did, ResolutionOptions(versionId = "1"))
            deactivatedNow = true
            val latest = resolver.resolve(did)

            assertTrue(latest is DidResolutionResult.Deactivated)
        }

    @Test
    fun `ttl is measured from when the delegate returns`() =
        runBlocking<Unit> {
            val did = Did("did:example:slow")
            val clock = MutableClock(epoch)
            val delegate =
                CountingResolver { d ->
                    clock.advance(4.minutes) // slow resolution
                    success(d)
                }
            val resolver = CachingDidResolver(delegate, ttl = 5.minutes, clock = clock)

            resolver.resolve(did)
            clock.advance(2.minutes) // 2 min after the result arrived: still fresh
            resolver.resolve(did)

            assertEquals(1, delegate.totalCalls)
        }

    @Test
    fun `an invalidate during an in-flight resolve prevents the stale write`() =
        runBlocking<Unit> {
            val did = Did("did:example:race")
            lateinit var resolver: CachingDidResolver
            val delegate =
                CountingResolver { d ->
                    resolver.invalidate(d) // concurrent invalidate while resolving
                    success(d)
                }
            resolver = CachingDidResolver(delegate, clock = MutableClock(epoch))

            resolver.resolve(did)
            resolver.resolve(did)

            assertEquals(2, delegate.totalCalls, "result fetched before the invalidate must not be cached")
        }
}
