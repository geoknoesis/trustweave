package org.trustweave.did.dsl

import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.exception.DidException

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
 * // Functional style
 * val document = Did("did:key:...")
 *     .resolveWith(resolver)
 *     .getOrThrow()
 * 
 * // Safe access
 * val doc = Did("did:key:...")
 *     .resolveWith(resolver)
 *     .getOrNull()
 * 
 * // With callbacks
 * Did("did:key:...")
 *     .resolveWith(resolver)
 *     .onSuccess { println("Resolved: ${it.id}") }
 *     .onFailure { println("Failed: ${it.reason}") }
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
 * @throws DidException if resolution failed
 */
suspend fun Did.resolveOrThrow(resolver: DidResolver): DidDocument {
    val result = resolveWith(resolver)
    return when (result) {
        is DidResolutionResult.Success -> result.document
        is DidResolutionResult.Failure.NotFound -> throw DidException.DidNotFound(
            did = result.did,
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
    return when (val result = resolveWith(resolver)) {
        is DidResolutionResult.Success -> result.document
        else -> null
    }
}

/**
 * Resolves this DID and returns the document, or the default value.
 *
 * @param resolver The DID resolver to use
 * @param default The default document to return if resolution fails
 * @return The resolved DID document, or the default if resolution failed
 */
suspend fun Did.resolveOrDefault(
    resolver: DidResolver,
    default: DidDocument
): DidDocument {
    return resolveOrNull(resolver) ?: default
}

/**
 * Resolves this DID and executes the block if successful.
 *
 * @param resolver The DID resolver to use
 * @param block The block to execute with the resolved document
 * @return The resolution result
 */
suspend inline fun Did.resolveWith(
    resolver: DidResolver,
    block: (DidDocument) -> Unit
): DidResolutionResult {
    val result = resolveWith(resolver)
    // Use onSuccess extension from DidResolutionResultExtensions
    if (result is DidResolutionResult.Success) {
        block(result.document)
    }
    return result
}

