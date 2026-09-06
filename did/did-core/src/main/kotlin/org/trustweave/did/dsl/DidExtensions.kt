package org.trustweave.did.dsl

import org.trustweave.did.exception.DidException
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver

/**
 * Extension functions for fluent DID operations.
 *
 * These extensions work standalone without orchestration context and can be used
 * with just the `did-core` module.
 *
 * **Example Usage:**
 * ```kotlin
 * val resolver = RegistryBasedResolver(registry)
 *
 * // Document or throw
 * val document = Did("did:key:...").resolveOrThrow(resolver)
 *
 * // Document or null
 * val doc = Did("did:key:...").resolveOrNull(resolver)
 *
 * // Sealed result + diagnostics (`errorMessage`, `errorCode`, …)
 * when (val res = Did("did:key:...").resolveWith(resolver)) {
 *     is DidResolutionResult.Success -> println("Resolved: ${res.document.id}")
 *     is DidResolutionResult.Failure -> println("Failed: ${res.errorMessage}")
 * }
 * ```
 */

/**
 * Resolves this DID using the provided resolver.
 *
 * @param resolver The DID resolver to use
 * @return DidResolutionResult (always non-null)
 */
suspend fun Did.resolveWith(resolver: DidResolver): DidResolutionResult = resolver.resolve(this)

/**
 * Resolves this DID and returns the document, or throws if not found.
 *
 * @param resolver The DID resolver to use
 * @return The resolved DID document
 * @throws DidException if resolution failed
 */
suspend fun Did.resolveOrThrow(resolver: DidResolver): DidDocument {
    val result = resolveWith(resolver)
    return when (result) {
        is DidResolutionResult.Success -> result.document
        is DidResolutionResult.Failure.NotFound -> throw DidException.DidNotFound(
            did = result.did,
            availableMethods = emptyList(),
        )
        is DidResolutionResult.Failure.InvalidFormat -> throw DidException.InvalidDidFormat(
            did = result.did,
            reason = result.reason,
        )
        is DidResolutionResult.Failure.MethodNotRegistered -> throw DidException.DidMethodNotRegistered(
            method = result.method,
            availableMethods = result.availableMethods,
        )
        is DidResolutionResult.Failure.ResolutionError -> throw DidException.DidResolutionFailed(
            did = result.did,
            reason = result.reason,
            cause = result.cause,
        )
        is DidResolutionResult.Failure.OptionsError -> throw DidException.DidResolutionFailed(
            did = result.did ?: this,
            reason = result.reason,
        )
        // §4.4: a deactivated DID resolves to no document. resolveOrThrow's whole contract is
        // "return the document or fail", and callers of resolveOrThrow may use the document for
        // verification/authorization, so a deactivated DID must fail here rather than silently
        // being treated as some other kind of missing document.
        is DidResolutionResult.Deactivated -> throw DidException.DidResolutionFailed(
            did = result.did,
            reason = "DID is deactivated",
        )
    }
}

/**
 * Resolves this DID and returns the document, or null if it could not be resolved.
 *
 * A deactivated DID (§4.4) is **not** folded into `null`. `null` here means "no usable
 * document" only for the ordinary not-found/error cases; a deactivated DID is a revoked
 * identity, not an absent one, and this convenience API's caller may use the returned document
 * for verification/authorization — so, like [resolveOrThrow], this throws instead of silently
 * returning `null` for a revoked DID.
 *
 * @param resolver The DID resolver to use
 * @return The resolved DID document, or null if resolution failed (not found, invalid, etc.)
 * @throws DidException.DidResolutionFailed if the DID is deactivated
 */
suspend fun Did.resolveOrNull(resolver: DidResolver): DidDocument? =
    when (val result = resolveWith(resolver)) {
        is DidResolutionResult.Success -> result.document
        // §4.4: see the KDoc above — deactivation must not be indistinguishable from "absent".
        is DidResolutionResult.Deactivated -> throw DidException.DidResolutionFailed(
            did = result.did,
            reason = "DID is deactivated",
        )
        else -> null
    }

/**
 * Resolves this DID and returns the document, or the default value.
 *
 * A deactivated DID (§4.4) throws rather than yielding [default] — see [resolveOrNull], which
 * this delegates to.
 *
 * @param resolver The DID resolver to use
 * @param default The default document to return if resolution failed for a reason other than
 *   deactivation
 * @return The resolved DID document, or the default if resolution failed
 * @throws DidException.DidResolutionFailed if the DID is deactivated
 */
suspend fun Did.resolveOrDefault(
    resolver: DidResolver,
    default: DidDocument,
): DidDocument = resolveOrNull(resolver) ?: default

/**
 * Resolves this DID and executes the block if successful.
 *
 * @param resolver The DID resolver to use
 * @param block The block to execute with the resolved document
 * @return The resolution result
 */
suspend inline fun Did.resolveWith(
    resolver: DidResolver,
    block: (DidDocument) -> Unit,
): DidResolutionResult {
    val result = resolveWith(resolver)
    if (result is DidResolutionResult.Success) {
        block(result.document)
    }
    return result
}
