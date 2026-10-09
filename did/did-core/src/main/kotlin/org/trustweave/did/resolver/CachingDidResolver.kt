package org.trustweave.did.resolver

import org.trustweave.core.telemetry.Operation
import org.trustweave.core.telemetry.Telemetry
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolution.ResolutionOptions
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Caching decorator for any [DidResolver].
 *
 * Caches **successful and deactivated** resolutions keyed by DID string, with:
 * - a configurable time-to-live ([ttl], default 5 minutes);
 * - a configurable maximum size ([maxSize], default 1000) with least-recently-used eviction;
 * - requests carrying `versionId`, `versionTime`, `accept`, `expandRelativeUrls` or `additional`
 *   options bypass the cache (neither read nor written);
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

    /**
     * Race guard against re-inserting an answer fetched before an invalidation (which would
     * resurrect a revoked/stale document). A resolution records [generationOf] its key before
     * calling the delegate and only writes its result if that value is unchanged.
     *
     * Generations are striped by key hash: [invalidate] bumps only the stripe of the DID it
     * invalidates, so an invalidation (or a `noCache` resolve) for one DID cannot suppress the
     * cache writes of unrelated in-flight resolutions. [clear] bumps [clearGeneration], which
     * every key observes. Two DIDs sharing a stripe merely lose a cache write, never gain a stale one.
     */
    private val stripes = AtomicLongArray(GENERATION_STRIPES)
    private val clearGeneration = AtomicLong(0)

    private fun generationOf(key: String): Long =
        stripes.get(Math.floorMod(key.hashCode(), GENERATION_STRIPES)) + clearGeneration.get()

    /** Current number of cached entries (primarily for diagnostics and tests). */
    val size: Int get() = cache.size

    override suspend fun resolve(did: Did): DidResolutionResult =
        Telemetry.measure(Operation.DID_RESOLVE, mapOf("did.method" to did.method)) {
            val key = did.value

            readCache(key, clock.now())?.let { cached ->
                return@measure cached
            }

            val startGeneration = generationOf(key)
            val result = delegate.resolve(did)
            writeCache(key, startGeneration, result)
            if (result is DidResolutionResult.Failure) {
                Telemetry.rejected(
                    Operation.DID_RESOLVE,
                    result::class.simpleName ?: "Failure",
                    mapOf("did.method" to did.method),
                )
            }
            result
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

        // Options that change the shape of the returned document or its contentType, or that select
        // a non-latest version (`versionId`, `versionTime`) or carry method-specific `additional`
        // options, make a result unsafe to share under a DID-only cache key: caching an
        // `expandRelativeUrls` result would serve expanded documents to callers who did not ask for
        // expansion, and a versioned result would be served as the latest (or mask a deactivation).
        // Such requests bypass the cache entirely rather than poison it.
        val cacheable =
            options.accept == null &&
                !options.expandRelativeUrls &&
                options.versionId == null &&
                options.versionTime == null &&
                options.additional.isEmpty()

        if (cacheable && !options.noCache) {
            readCache(key, clock.now())?.let { return it }
        }

        // A `noCache` request must not leave a stale entry behind for other callers: if the fresh
        // answer is Deactivated while the cache holds a Success, everyone else would keep receiving
        // the revoked document until TTL expiry. Invalidate before reading the generation so this
        // request's own write is not suppressed by its own invalidation.
        if (cacheable && options.noCache) invalidate(did)
        val startGeneration = generationOf(key)

        // `noCache` has been honoured above, so it is stripped before delegating: the delegate is
        // ultimately a DID method, which would otherwise reject it as an unsupported
        // method-specific option (§4.4 step 3) even though this layer already acted on it.
        val result = delegate.resolve(did, options.copy(noCache = false))

        if (cacheable) writeCache(key, startGeneration, result)
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
        generationAtStart: Long,
        result: DidResolutionResult,
    ) {
        if (result is DidResolutionResult.Success || result is DidResolutionResult.Deactivated) {
            // Taken after the delegate returned so a slow resolution does not eat into the TTL.
            val now = clock.now()
            val expiresAt = expiryFor(now, result)
            if (expiresAt > now && generationOf(key) == generationAtStart) {
                val entry = CacheEntry(result, expiresAt)
                entry.lastAccess = accessCounter.incrementAndGet()
                cache[key] = entry
                // Re-check: an invalidate that raced with the insert above must not leave it behind.
                if (generationOf(key) != generationAtStart) cache.remove(key, entry)
                evictLeastRecentlyUsed()
            }
        }
    }

    /**
     * Removes the cached entry for [did], if present. The next [resolve] for this DID
     * will hit the delegate.
     */
    fun invalidate(did: Did) {
        stripes.incrementAndGet(Math.floorMod(did.value.hashCode(), GENERATION_STRIPES))
        cache.remove(did.value)
    }

    /** Removes all cached entries. */
    fun clear() {
        clearGeneration.incrementAndGet()
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

    private companion object {
        const val GENERATION_STRIPES = 64
    }
}
