package org.trustweave.did.resolver

import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolution.ResolutionOptions
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Decentralized Resolution Strategy.
 *
 * Implements multi-source resolution with fallback to maintain decentralization principles.
 * Tries multiple resolution sources in order:
 * 1. Local storage (fastest, but may be stale)
 * 2. Method-specific resolver (most authoritative)
 * 3. Universal Resolver (decentralized fallback)
 *
 * **Decentralization Benefits:**
 * - No single point of failure
 * - Multiple resolution sources
 * - Freshness checking for cached results
 * - Graceful degradation
 *
 * **Example Usage:**
 * ```kotlin
 * val strategy = DecentralizedResolutionStrategy(
 *     localResolver = RegistryBasedResolver(registry),
 *     universalResolver = DefaultUniversalResolver("https://dev.uniresolver.io"),
 *     methodSpecificResolvers = mapOf(
 *         "web" to WebDidResolver(),
 *         "key" to KeyDidResolver()
 *     )
 * )
 *
 * val result = strategy.resolve(Did("did:web:example.com"))
 * ```
 */
class DecentralizedResolutionStrategy(
    private val localResolver: DidResolver,
    private val universalResolver: DidResolver,
    private val methodSpecificResolvers: Map<String, DidResolver> = emptyMap(),
    private val maxCacheAge: Duration = 1.hours,
) : DidResolver {
    override suspend fun resolve(did: Did): DidResolutionResult = resolve(did, ResolutionOptions.EMPTY)

    /**
     * Resolves with DID Resolution 1.0 §4.1 options, forwarded unchanged to whichever of
     * [localResolver], [methodSpecificResolvers] or [universalResolver] ends up answering — same
     * three-stage precedence as the single-argument [resolve]. Each stage is itself a
     * [DidResolver] and independently responsible for judging support for the options it
     * receives (e.g. reporting `FEATURE_NOT_SUPPORTED` for a `versionId` it cannot honour); this
     * strategy does not interpret options itself beyond forwarding them.
     */
    override suspend fun resolve(
        did: Did,
        options: ResolutionOptions,
    ): DidResolutionResult {
        // 1. Try local storage first (fastest, but may be stale). A `Deactivated` verdict (§4.4)
        // is a terminal, authoritative answer — deactivation cannot be undone (W3C DID Core
        // §7.3), so it can never be "stale" in the sense that matters here — and short-circuits
        // unconditionally, without the freshness check that gates `Success`.
        localResolver
            .resolve(did, options)
            .takeIf { result ->
                result is DidResolutionResult.Deactivated ||
                    (result is DidResolutionResult.Success && isFresh(result))
            }?.let { result ->
                return result
            }

        // 2. Try method-specific resolver (most authoritative). A `Deactivated` verdict here is
        // just as authoritative as `Success` — both are terminal answers from the method's own
        // resolver — so both stop the fallback chain rather than falling through to the
        // (less-authoritative) universal resolver, which could resurrect a revoked DID's
        // document.
        methodSpecificResolvers[did.method]
            ?.resolve(did, options)
            ?.takeIf { it is DidResolutionResult.Success || it is DidResolutionResult.Deactivated }
            ?.let { result ->
                return result
            }

        // 3. Fall back to Universal Resolver (decentralized)
        return universalResolver.resolve(did, options)
    }

    /**
     * Checks if a cached result is fresh — dated by §4.2 `retrieved` (when the answer was obtained),
     * falling back to §4.3 `updated`/`created` for resolvers that omit it; those date the document.
     */
    private fun isFresh(result: DidResolutionResult.Success): Boolean {
        val obtainedAt =
            result.resolutionMetadata.retrieved
                ?: result.documentMetadata.updated ?: result.documentMetadata.created ?: return false
        return Clock.System.now() - obtainedAt < maxCacheAge
    }
}

/**
 * Resolution Fallback Strategy.
 *
 * Tries multiple resolvers in order until one succeeds.
 * Useful for redundancy and high availability.
 *
 * **Example Usage:**
 * ```kotlin
 * val fallbackStrategy = ResolutionFallbackStrategy(
 *     resolvers = listOf(
 *         primaryResolver,
 *         secondaryResolver,
 *         universalResolver
 *     )
 * )
 * ```
 */
class ResolutionFallbackStrategy(
    private val resolvers: List<DidResolver>,
) : DidResolver {
    override suspend fun resolve(did: Did): DidResolutionResult = resolveWith(did) { resolver -> resolver.resolve(did) }

    /**
     * Resolves with DID Resolution 1.0 §4.1 options, forwarded unchanged to every resolver this
     * strategy tries in turn — same try-until-success/deactivated precedence as the
     * single-argument [resolve].
     */
    override suspend fun resolve(
        did: Did,
        options: ResolutionOptions,
    ): DidResolutionResult = resolveWith(did) { resolver -> resolver.resolve(did, options) }

    private suspend fun resolveWith(
        did: Did,
        resolveOne: suspend (DidResolver) -> DidResolutionResult,
    ): DidResolutionResult {
        val errors = mutableListOf<String>()

        for (resolver in resolvers) {
            try {
                val result = resolveOne(resolver)
                // A deactivated DID (§4.4) is an authoritative, terminal answer from this
                // resolver, not an absence of information — surface it as-is (like Success)
                // instead of folding it into "errors and retry", so a stale or
                // less-authoritative fallback resolver cannot silently resurrect a deactivated
                // DID by returning an older document.
                if (result is DidResolutionResult.Success || result is DidResolutionResult.Deactivated) {
                    return result
                } else {
                    errors.add(
                        (result as? DidResolutionResult.Failure)?.let {
                            when (it) {
                                is DidResolutionResult.Failure.NotFound -> "Not found"
                                is DidResolutionResult.Failure.InvalidFormat -> "Invalid format: ${it.reason}"
                                is DidResolutionResult.Failure.MethodNotRegistered -> "Method not registered: ${it.method}"
                                is DidResolutionResult.Failure.ResolutionError -> "Resolution error: ${it.reason}"
                                is DidResolutionResult.Failure.OptionsError -> "Options error: ${it.reason}"
                            }
                        } ?: "Unknown error",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                errors.add("Exception: ${e.message ?: "Unknown error"}")
            }
        }

        // All resolvers failed
        return DidResolutionResult.Failure.ResolutionError(
            did = did,
            reason = "All resolution attempts failed: ${errors.joinToString(", ")}",
            resolutionMetadata =
                DidResolutionMetadata(
                    error = DidResolutionError.internalError("All resolution attempts failed"),
                    properties = mapOf("attemptedResolvers" to resolvers.size.toString()),
                ),
        )
    }
}
