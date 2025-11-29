package com.trustweave.did.resolver

import com.trustweave.core.types.Did

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
 * ```
 */
fun interface DidResolver {
    /**
     * Resolves a DID to a DID resolution result.
     *
     * @param did Type-safe DID identifier
     * @return DidResolutionResult if resolved, null if not resolvable
     */
    suspend fun resolve(did: Did): DidResolutionResult?
}

