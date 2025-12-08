package com.trustweave.did.dsl

import com.trustweave.did.identifiers.Did
import com.trustweave.did.model.DidDocument
import com.trustweave.did.resolver.DidResolver
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.exception.DidException

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

