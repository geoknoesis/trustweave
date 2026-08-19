package org.trustweave.did.dsl

import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.exception.DidException

/**
 * Extension functions for [DidResolver] to improve ergonomics.
 *
 * These extensions provide convenient methods for common resolution patterns.
 */

/**
 * Resolves a DID and returns the document, or throws if not found.
 *
 * @param did The DID to resolve
 * @return The resolved DID document
 * @throws DidException.DidNotFound if the DID cannot be resolved
 */
suspend fun DidResolver.resolveOrThrow(did: Did): DidDocument {
    return when (val result = resolve(did)) {
        is DidResolutionResult.Success -> result.document
        is DidResolutionResult.Failure.NotFound -> throw DidException.DidNotFound(
            did = did,
            availableMethods = emptyList()
        )
        is DidResolutionResult.Failure.InvalidFormat -> throw DidException.InvalidDidFormat(
            did = result.did,
            reason = result.reason
        )
        is DidResolutionResult.Failure.MethodNotRegistered -> throw DidException.DidMethodNotRegistered(
            method = result.method,
            availableMethods = result.availableMethods
        )
        is DidResolutionResult.Failure.ResolutionError -> throw DidException.DidResolutionFailed(
            did = result.did,
            reason = result.reason,
            cause = result.cause
        )
        is DidResolutionResult.Failure.OptionsError -> throw DidException.DidResolutionFailed(
            did = result.did ?: did,
            reason = result.reason
        )
        // §4.4: a deactivated DID resolves to no document. resolveOrThrow's whole contract is
        // "return the document or fail", and callers of resolveOrThrow may use the document for
        // verification/authorization, so a deactivated DID must fail here rather than silently
        // being treated as some other kind of missing document.
        is DidResolutionResult.Deactivated -> throw DidException.DidResolutionFailed(
            did = result.did,
            reason = "DID is deactivated"
        )
    }
}

/**
 * Resolves a DID and returns the document, or null if not found.
 *
 * @param did The DID to resolve
 * @return The resolved DID document, or null if resolution failed
 */
suspend fun DidResolver.resolveOrNull(did: Did): DidDocument? {
    return when (val result = resolve(did)) {
        is DidResolutionResult.Success -> result.document
        else -> null
    }
}

