package org.trustweave.did.resolver

import org.trustweave.did.identifiers.Did
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Caching decorator for any [DidResolver].
 *
 * Caches **successful** resolutions keyed by DID string, with:
 * - a configurable time-to-live ([ttl], default 5 minutes);
 * - a configurable maximum size ([maxSize], default 1000) with least-recently-used eviction;
 * - `documentMetadata.nextUpdate` honored when it is earlier than the TTL expiry
 *   (the entry expires at `nextUpdate`); a `nextUpdate` in the past means the document
 *   is already due for an update, so the result is returned but **not cached**.
 *
 * **What is cached:**
 * - Only [DidResolutionResult.Success] results. Failures ([DidResolutionResult.Failure])
 *   are never cached — a transient network error or an unregistered method must not be
 *   sticky, and a later retry may succeed.
 * - Documents whose metadata reports `deactivated = true` ARE cached normally:
 *   deactivation is terminal per W3C DID Core §7.3, so a cached deactivated result can
 *   never become stale in a way that grants access it shouldn't (the failure mode is
 *   only re-confirming deactivation slightly late, which the TTL already bounds).
 *
 * **Concurrency:** coroutine-friendly and thread-safe without blocking locks around the
 * suspending [resolve] call. State lives in a [ConcurrentHashMap]; if two coroutines
 * concurrently resolve the same uncached DID, both will hit the delegate and the last
 * writer wins — duplicate resolution is accepted as a deliberate trade-off to avoid
 * holding a lock across a suspension point. LRU eviction is approximate: access order
 * is tracked with a monotonic counter and the least-recently-used entry is evicted on
 * insert when the bound is exceeded; concurrent races may transiently overshoot the
 * bound by a small number of entries.
 *
 * **Example Usage:**
 * ```kotlin
 * val registry = RegistryBasedResolver(didMethodRegistry)
 * val universal = DefaultUniversalResolver("https://dev.uniresolver.io").asDidResolver()
 *
 * // Compose: cache on top of local-registry-with-universal-fallback
 * val resolver = CachingDidResolver(FallbackDidResolver(registry, universal))
 *
 * val result = resolver.resolve(Did("did:web:example.com"))   // miss → delegate
 * val cached = resolver.resolve(Did("did:web:example.com"))   // hit → cached Success
 *
 * resolver.invalidate(Did("did:web:example.com"))             // drop one entry
 * resolver.clear()                                            // drop everything
 * ```
 *
 * @param delegate The resolver whose successful results are cached
 * @param ttl Maximum lifetime of a cache entry (must be positive; default 5 minutes)
 * @param maxSize Maximum number of cached entries (must be positive; default 1000)
 * @param clock Time source, injectable for testing (defaults to [Clock.System])
 */
class CachingDidResolver(
    private val delegate: DidResolver,
    private val ttl: Duration = 5.minutes,
    private val maxSize: Int = 1000,
    private val clock: Clock = Clock.System
) : DidResolver {

    init {
        require(ttl.isPositive()) { "ttl must be positive, got $ttl" }
        require(maxSize > 0) { "maxSize must be positive, got $maxSize" }
    }

    private class CacheEntry(
        val result: DidResolutionResult.Success,
        val expiresAt: Instant
    ) {
        @Volatile
        var lastAccess: Long = 0
    }

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val accessCounter = AtomicLong(0)

    /** Current number of cached entries (primarily for diagnostics and tests). */
    val size: Int get() = cache.size

    override suspend fun resolve(did: Did): DidResolutionResult {
        val key = did.value
        val now = clock.now()

        cache[key]?.let { entry ->
            if (now < entry.expiresAt) {
                entry.lastAccess = accessCounter.incrementAndGet()
                return entry.result
            }
            // Expired — remove only if still the same entry (avoid clobbering a
            // fresher entry written by a concurrent resolver).
            cache.remove(key, entry)
        }

        val result = delegate.resolve(did)
        if (result is DidResolutionResult.Success) {
            val expiresAt = expiryFor(now, result)
            if (expiresAt > now) {
                val entry = CacheEntry(result, expiresAt)
                entry.lastAccess = accessCounter.incrementAndGet()
                cache[key] = entry
                evictLeastRecentlyUsed()
            }
        }
        return result
    }

    /**
     * Removes the cached entry for [did], if present. The next [resolve] for this DID
     * will hit the delegate.
     */
    fun invalidate(did: Did) {
        cache.remove(did.value)
    }

    /** Removes all cached entries. */
    fun clear() {
        cache.clear()
    }

    /**
     * Computes the expiry instant for a successful result: `now + ttl`, capped by
     * `documentMetadata.nextUpdate` when that is earlier. A `nextUpdate` at or before
     * [now] yields a non-future expiry, which the caller treats as "do not cache".
     */
    private fun expiryFor(now: Instant, result: DidResolutionResult.Success): Instant {
        val ttlExpiry = now + ttl
        val nextUpdate = result.documentMetadata.nextUpdate
        return if (nextUpdate != null && nextUpdate < ttlExpiry) nextUpdate else ttlExpiry
    }

    /**
     * Evicts least-recently-used entries while the cache exceeds [maxSize].
     * O(n) scan per eviction — acceptable for the intended bound (~1000 entries)
     * and only paid on inserts that overflow the cache.
     */
    private fun evictLeastRecentlyUsed() {
        while (cache.size > maxSize) {
            val lru = cache.entries.minByOrNull { it.value.lastAccess } ?: return
            cache.remove(lru.key, lru.value)
        }
    }
}
