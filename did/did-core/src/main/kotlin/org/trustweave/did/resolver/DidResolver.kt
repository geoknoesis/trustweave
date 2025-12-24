package org.trustweave.did.resolver

import org.trustweave.did.identifiers.Did

/**
 * Functional interface for resolving DIDs to DID documents.
 *
 * This is the native DID resolver interface used throughout the codebase.
 * All modules (did:core, credentials:core, trust) use this interface.
 *
 * **Example Usage**:
 * ```kotlin
 * val resolver: DidResolver = DidResolver { did ->
 *     // Resolve DID and return DidResolutionResult
 *     DidResolutionResult.Success(document = didDocument)
 * }
 *
 * val did = Did("did:key:123")
 * val result = resolver.resolve(did)
 * when (result) {
 *     is DidResolutionResult.Success -> println(result.document.id)
 *     is DidResolutionResult.Failure.NotFound -> println("Not found")
 *     // ... handle other cases
 * }
 * ```
 */
fun interface DidResolver {
    /**
     * Resolves a DID to a DID resolution result.
     *
     * This method always returns a [DidResolutionResult], never null.
     * If a DID cannot be resolved, it returns [DidResolutionResult.Failure.NotFound].
     *
     * @param did Type-safe DID identifier
     * @return DidResolutionResult - always non-null (use NotFound for unresolvable DIDs)
     */
    suspend fun resolve(did: Did): DidResolutionResult
}

