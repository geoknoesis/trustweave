package com.trustweave.did.dsl

import com.trustweave.did.identifiers.Did
import com.trustweave.did.model.DidDocument
import com.trustweave.did.resolver.DidResolver
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.exception.DidException

/**
 * Extension functions for fluent DID operations.
 *
 * These extensions work standalone without orchestration context and can be used
 * with just the `did-core` module.
 *
 * **Example Usage:**
 * ```kotlin
 * val resolver = RegistryBasedResolver(registry)
 * val document = Did("did:key:...").resolveWith(resolver).getOrThrow()
 * ```
 */

/**
 * Resolves this DID using the provided resolver.
 *
 * @param resolver The DID resolver to use
 * @return DidResolutionResult (always non-null)
 */
suspend fun Did.resolveWith(resolver: DidResolver): DidResolutionResult {
    return resolver.resolve(this)
}

/**
 * Resolves this DID and returns the document, or throws if not found.
 *
 * @param resolver The DID resolver to use
 * @return The resolved DID document
 * @throws DidException.DidNotFound if the DID cannot be resolved
 */
suspend fun Did.resolveOrThrow(resolver: DidResolver): DidDocument {
    return when (val result = resolver.resolve(this)) {
        is DidResolutionResult.Success -> result.document
        is DidResolutionResult.Failure.NotFound -> throw DidException.DidNotFound(
            did = this,
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
 * Resolves this DID and returns the document, or null if not found.
 *
 * @param resolver The DID resolver to use
 * @return The resolved DID document, or null if resolution failed
 */
suspend fun Did.resolveOrNull(resolver: DidResolver): DidDocument? {
    return when (val result = resolver.resolve(this)) {
        is DidResolutionResult.Success -> result.document
        else -> null
    }
}

