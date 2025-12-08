package com.trustweave.did.dsl

import com.trustweave.did.model.DidDocument
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.exception.DidException

/**
 * Extension functions for [DidResolutionResult] to improve ergonomics.
 *
 * These extensions provide convenient methods for extracting documents from resolution results.
 */

/**
 * Gets the document from a successful resolution result, or throws if resolution failed.
 *
 * @return The resolved DID document
 * @throws DidException if resolution failed
 */
fun DidResolutionResult.getOrThrow(): DidDocument = when (this) {
    is DidResolutionResult.Success -> document
    is DidResolutionResult.Failure.NotFound -> throw DidException.DidNotFound(
        did = did,
        availableMethods = emptyList()
    )
    is DidResolutionResult.Failure.InvalidFormat -> throw DidException.InvalidDidFormat(
        did = did,
        reason = reason
    )
    is DidResolutionResult.Failure.MethodNotRegistered -> throw DidException.DidMethodNotRegistered(
        method = method,
        availableMethods = availableMethods
    )
    is DidResolutionResult.Failure.ResolutionError -> throw DidException.DidResolutionFailed(
        did = did,
        reason = reason,
        cause = cause
    )
}

/**
 * Gets the document from a successful resolution result, or returns null if resolution failed.
 *
 * @return The resolved DID document, or null if resolution failed
 */
fun DidResolutionResult.getOrNull(): DidDocument? = when (this) {
    is DidResolutionResult.Success -> document
    else -> null
}

/**
 * Gets the document from a successful resolution result, or returns a default value.
 *
 * @param defaultValue The value to return if resolution failed
 * @return The resolved DID document, or the default value
 */
fun DidResolutionResult.getOrDefault(defaultValue: DidDocument): DidDocument = when (this) {
    is DidResolutionResult.Success -> document
    else -> defaultValue
}

