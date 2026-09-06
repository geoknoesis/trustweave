package org.trustweave.did.resolver

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolution.ResolutionOptions
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Caching decorator for any [DidResolver].
 *
 * Caches **successful and deactivated** resolutions keyed by DID string, with:
 * - a configurable time-to-live ([ttl], default 5 minutes);
 * - a configurable maximum size ([maxSize], default 1000) with least-recently-used eviction;
 * - `documentMetadata.nextUpdate` honored when it is earlier than the TTL expiry
 *   (the entry expires at `nextUpdate`); a `nextUpdate` in the past means the document
 *   is already due for an update, so the result is returned but **not cached**.
 *
 * **What is cached:**
 * - [DidResolutionResult.Success] and [DidResolutionResult.Deactivated] results. Failures
 *   ([DidResolutionResult.Failure]) are never cached — a transient network error or an
 *   unregistered method must not be sticky, and a later retry may succeed.
 * - [DidResolutionResult.Deactivated] is cached exactly like [DidResolutionResult.Success]:
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
 * val cached = resolver.resolve(Did("did:web:example.com"))   // hit → cached Success or Deactivated
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
    private val clock: Clock = Clock.System,
) : DidResolver {
    init {
        require(ttl.isPositive()) { "ttl must be positive, got $ttl" }
        require(maxSize > 0) { "maxSize must be positive, got $maxSize" }
    }

    private class CacheEntry(
        val result: DidResolutionResult,
        val expiresAt: Instant,
    ) {
        init {
            require(result is DidResolutionResult.Success || result is DidResolutionResult.Deactivated) {
                "CacheEntry only caches Success or Deactivated results, got ${result::class.simpleName}"
            }
        }

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

        readCache(key, now)?.let { return it }

        val result = delegate.resolve(did)
        writeCache(key, now, result)
        return result
    }

    /**
     * Resolves with DID Resolution 1.0 §4.1 options.
     *
     * `noCache` (§13.2) is honoured by this layer directly — a `noCache` request bypasses the
     * cached entry and is not itself cached-around, i.e. it forces a delegate round trip — while
     * every other option, including any method-specific option this layer does not understand
     * (`versionId`, `versionTime`), is forwarded to [delegate] unchanged. This layer has no basis
     * to judge support for those; the delegate — ultimately the DID method — does.
     */
    override suspend fun resolve(
        did: Did,
        options: ResolutionOptions,
    ): DidResolutionResult {
        options.validate()?.let { error ->
            return DidResolutionResult.Failure.OptionsError(
                did = did,
                reason = error.detail ?: "Invalid resolution options",
                errorType = error.type,
            )
        }

        val key = did.value
        val now = clock.now()

        // Options that change the shape of the returned document or its contentType make a result
        // unsafe to share under a DID-only cache key: caching an `expandRelativeUrls` result would
        // serve expanded documents to callers who did not ask for expansion, and vice versa.
        // Such requests bypass the cache entirely rather than poison it.
        val cacheable = options.accept == null && !options.expandRelativeUrls

        if (cacheable && !options.noCache) {
            readCache(key, now)?.let { return it }
        }

        // `noCache` has been honoured above, so it is stripped before delegating: the delegate is
        // ultimately a DID method, which would otherwise reject it as an unsupported
        // method-specific option (§4.4 step 3) even though this layer already acted on it.
        val result = delegate.resolve(did, options.copy(noCache = false))

        if (cacheable) {
            // A `noCache` request must not leave a stale entry behind for other callers: if the
            // fresh answer is Deactivated while the cache holds a Success, everyone else would keep
            // receiving the revoked document until TTL expiry.
            if (options.noCache) invalidate(did)
            writeCache(key, now, result)
        }
        return result
    }

    /** Returns the cached, still-fresh result for [key], if any; evicts an expired entry found. */
    private fun readCache(
        key: String,
        now: Instant,
    ): DidResolutionResult? {
        val entry = cache[key] ?: return null
        if (now < entry.expiresAt) {
            entry.lastAccess = accessCounter.incrementAndGet()
            return entry.result
        }
        // Expired — remove only if still the same entry (avoid clobbering a
        // fresher entry written by a concurrent resolver).
        cache.remove(key, entry)
        return null
    }

    /** Caches [result] for [key] if it is a cacheable variant and its expiry is in the future. */
    private fun writeCache(
        key: String,
        now: Instant,
        result: DidResolutionResult,
    ) {
        if (result is DidResolutionResult.Success || result is DidResolutionResult.Deactivated) {
            val expiresAt = expiryFor(now, result)
            if (expiresAt > now) {
                val entry = CacheEntry(result, expiresAt)
                entry.lastAccess = accessCounter.incrementAndGet()
                cache[key] = entry
                evictLeastRecentlyUsed()
            }
        }
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
     * Computes the expiry instant for a cacheable ([DidResolutionResult.Success] or
     * [DidResolutionResult.Deactivated]) result: `now + ttl`, capped by
     * `documentMetadata.nextUpdate` when that is earlier. A `nextUpdate` at or before
     * [now] yields a non-future expiry, which the caller treats as "do not cache".
     *
     * Uses the [documentMetadata] extension property (not a member) so it reads correctly
     * for either variant.
     */
    private fun expiryFor(
        now: Instant,
        result: DidResolutionResult,
    ): Instant {
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
