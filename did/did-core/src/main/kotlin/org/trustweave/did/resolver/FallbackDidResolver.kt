package org.trustweave.did.resolver

import org.trustweave.did.identifiers.Did
import org.trustweave.did.exception.DidException
import org.trustweave.did.model.expandRelativeDidUrls
import org.trustweave.did.representation.DidMediaTypes
import org.trustweave.did.resolution.ResolutionOptions

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

    /**
     * Resolves with DID Resolution 1.0 §4.1 options, forwarded unchanged to whichever of
     * [primary] / [fallback] ends up handling the DID — same trigger rule as the single-argument
     * [resolve].
     */
    override suspend fun resolve(did: Did, options: ResolutionOptions): DidResolutionResult {
        return when (val result = primary.resolve(did, options)) {
            is DidResolutionResult.Failure.MethodNotRegistered -> fallback.resolve(did, options)
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
fun UniversalResolver.asDidResolver(): DidResolver = object : DidResolver {

    override suspend fun resolve(did: Did): DidResolutionResult = resolveViaUniversal(did)

    /**
     * Resolves with DID Resolution 1.0 §4.1 options.
     *
     * [UniversalResolver.resolveDid] takes no options at all, so this override applies the same
     * checks [RegistryBasedResolver] applies before delegating: §4.4 step 4 (options valid), then
     * §4.4 step 3 for both method-specific options (which this adapter cannot satisfy) and the
     * requested representation (`accept`) — mirroring [DidMediaTypes.isSupportedDocumentType].
     * Without this override, [DidResolver]'s two-arg default would silently discard `accept`
     * and `expandRelativeUrls` before falling through to [resolve], letting an unsupported
     * `accept` slip through as [DidResolutionResult.Success] instead of failing with
     * `REPRESENTATION_NOT_SUPPORTED`. `expandRelativeUrls` itself — once past those checks — is
     * honoured below by expanding the returned document, mirroring [RegistryBasedResolver]'s
     * identical post-processing step for the local-registry route.
     */
    override suspend fun resolve(did: Did, options: ResolutionOptions): DidResolutionResult {
        options.validate()?.let { error ->
            return DidResolutionResult.Failure.OptionsError(
                did = did,
                reason = error.detail ?: "Invalid resolution options",
                errorType = error.type
            )
        }
        val unsupported = options.methodSpecificOptions()
        if (unsupported.isNotEmpty()) {
            return DidResolutionResult.Failure.OptionsError(
                did = did,
                reason = "Resolution options not supported by this resolver: " +
                    unsupported.sorted().joinToString(", ")
            )
        }
        options.accept?.let { accept ->
            if (!DidMediaTypes.isSupportedDocumentType(accept)) {
                return DidResolutionResult.Failure.OptionsError(
                    did = did,
                    reason = "Representation not supported: '$accept'",
                    errorType = DidErrorType.REPRESENTATION_NOT_SUPPORTED
                )
            }
        }
        val result = resolveViaUniversal(did)
        // §4.4 — expandRelativeUrls post-processing, mirroring RegistryBasedResolver.
        return if (options.expandRelativeUrls && result is DidResolutionResult.Success) {
            result.copy(document = result.document.expandRelativeDidUrls())
        } else {
            result
        }
    }

    /**
     * Adapts [resolveDid]'s result to the [DidResolver] contract, applying the same §4/§4.4
     * defence-in-depth [RegistryBasedResolver] applies to the local-registry route — this is the
     * single choke point both first-party [UniversalResolver] implementations
     * ([DefaultUniversalResolver], [org.trustweave.godiddy.resolver.GodiddyResolver]) flow
     * through when used as a [DidResolver] via [asDidResolver], so fixing it here covers both
     * without duplicating the checks at each implementation.
     */
    private suspend fun resolveViaUniversal(did: Did): DidResolutionResult = try {
        when (val result = resolveDid(did.value)) {
            is DidResolutionResult.Success ->
                when {
                    // §4.4 defence in depth. The two first-party UniversalResolver
                    // implementations (DefaultUniversalResolver, GodiddyResolver) both check
                    // documentMetadata.deactivated before returning Success, so this should be
                    // unreachable for them — but a third-party UniversalResolver on the
                    // classpath might not apply that ordering itself. Re-checking here means
                    // this adapter never hands a caller a Success that carries a revoked
                    // document, regardless of what the wrapped implementation does. Checked
                    // before the id-equality check below for the same reason
                    // RegistryBasedResolver orders them this way: a tombstone document a
                    // method may leave minimal must still report Deactivated/410, not
                    // INVALID_DID_DOCUMENT.
                    result.documentMetadata.deactivated ->
                        DidResolutionResult.Deactivated(
                            did = did,
                            documentMetadata = result.documentMetadata,
                            resolutionMetadata = result.resolutionMetadata,
                        )
                    // §4 — the resolved document's `id` MUST be string-equal to the DID that
                    // was resolved. Without this, a wrong — or compromised — upstream
                    // UniversalResolver could hand back a completely different DID's document
                    // and it would surface as Success, with no signal to the caller that the
                    // document does not belong to the DID it asked for. Mirrors
                    // RegistryBasedResolver's identical guard.
                    result.document.id != did ->
                        DidResolutionResult.Failure.ResolutionError(
                            did = did,
                            reason =
                                "Resolved document id '${result.document.id.value}' does not match " +
                                    "requested DID '${did.value}'",
                            resolutionMetadata =
                                DidResolutionMetadata(
                                    error =
                                        DidResolutionError.invalidDidDocument(
                                            "Resolved document id '${result.document.id.value}' does not " +
                                                "match requested DID '${did.value}'",
                                        ),
                                ),
                        )
                    else -> result
                }
            else -> result
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: DidException) {
        // Map the exception subtype to its §11 error type so the RFC 9457 object asserts the
        // correct condition instead of always claiming INTERNAL_ERROR (HTTP 500) — §12.1
        // requires 400 for an invalid DID and 404 for not-found.
        val error = when (e) {
            is DidException.DidNotFound -> DidResolutionError.notFound(e.message ?: "DID not found")
            is DidException.InvalidDidFormat -> DidResolutionError.invalidDid(e.message ?: "Invalid DID")
            is DidException.DidMethodNotRegistered ->
                DidResolutionError.methodNotSupported(e.message ?: "DID method not registered")
            else -> DidResolutionError.internalError(e.message ?: "Universal resolver error")
        }
        DidResolutionResult.Failure.ResolutionError(
            did = did,
            reason = e.message ?: "Universal resolver error",
            cause = e,
            resolutionMetadata = DidResolutionMetadata(
                error = error
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
