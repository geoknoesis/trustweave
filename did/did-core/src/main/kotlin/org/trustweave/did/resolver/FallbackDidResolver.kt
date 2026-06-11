package org.trustweave.did.resolver

import org.trustweave.did.identifiers.Did
import org.trustweave.did.exception.DidException

/**
 * Composite resolver that delegates to [fallback] when the [primary] resolver does not
 * support the DID's method.
 *
 * **Fallback trigger — [DidResolutionResult.Failure.MethodNotRegistered] only.**
 * This is the one failure that means "the primary resolver cannot answer for this
 * method at all", e.g. a local [RegistryBasedResolver] without a driver for `did:ion`.
 *
 * **Failures that deliberately do NOT trigger fallback:**
 * - [DidResolutionResult.Failure.NotFound]: the method IS registered locally and that
 *   method authoritatively reported the DID as absent. Silently consulting another
 *   source could resurrect deleted/never-existing DIDs and produce trust decisions
 *   that diverge from the authoritative method driver.
 * - [DidResolutionResult.Failure.InvalidFormat]: a malformed DID is malformed for
 *   every resolver; retrying elsewhere only wastes a network round-trip.
 * - [DidResolutionResult.Failure.ResolutionError]: the method is registered but
 *   errored (network/driver failure). Falling back would mask operational problems
 *   and may return a different view of the DID than the configured driver. If you
 *   want try-everything redundancy semantics, use [ResolutionFallbackStrategy] instead.
 *
 * **Example Usage:**
 * ```kotlin
 * val registry: DidResolver = RegistryBasedResolver(didMethodRegistry)   // local methods
 * val universal: DidResolver = DefaultUniversalResolver("https://dev.uniresolver.io")
 *     .asDidResolver()                                                   // remote fallback
 *
 * // Locally registered methods are authoritative; anything else goes to the
 * // universal resolver. Wrap in a cache for production use:
 * val resolver = CachingDidResolver(FallbackDidResolver(registry, universal))
 *
 * val local = resolver.resolve(Did("did:key:z6Mk..."))   // resolved by the registry
 * val remote = resolver.resolve(Did("did:ion:EiD..."))   // not registered → universal
 * ```
 *
 * @param primary The authoritative resolver tried first (e.g. local registry/methods)
 * @param fallback The resolver consulted only when [primary] does not know the method
 */
class FallbackDidResolver(
    private val primary: DidResolver,
    private val fallback: DidResolver
) : DidResolver {

    override suspend fun resolve(did: Did): DidResolutionResult {
        return when (val result = primary.resolve(did)) {
            is DidResolutionResult.Failure.MethodNotRegistered -> fallback.resolve(did)
            else -> result
        }
    }
}

/**
 * Adapts a [UniversalResolver] (string-based, exception-throwing HTTP client interface)
 * to the [DidResolver] contract (typed, always returns a [DidResolutionResult]).
 *
 * [DidException]s and unexpected exceptions thrown by the universal resolver are
 * converted to [DidResolutionResult.Failure.ResolutionError] so composites such as
 * [FallbackDidResolver] and [CachingDidResolver] can treat the adapter like any other
 * resolver. [kotlinx.coroutines.CancellationException] is rethrown to preserve
 * structured-concurrency cancellation.
 *
 * **Example Usage:**
 * ```kotlin
 * val universal = DefaultUniversalResolver("https://dev.uniresolver.io").asDidResolver()
 * val resolver = CachingDidResolver(FallbackDidResolver(registry.asResolver(), universal))
 * ```
 */
fun UniversalResolver.asDidResolver(): DidResolver = DidResolver { did ->
    try {
        resolveDid(did.value)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: DidException) {
        DidResolutionResult.Failure.ResolutionError(
            did = did,
            reason = e.message ?: "Universal resolver error",
            cause = e,
            resolutionMetadata = DidResolutionMetadata(
                error = e.code,
                errorMessage = e.message ?: "Universal resolver error"
            )
        )
    } catch (e: Exception) {
        DidResolutionResult.Failure.ResolutionError(
            did = did,
            reason = e.message ?: "Unexpected universal resolver error",
            cause = e
        )
    }
}
