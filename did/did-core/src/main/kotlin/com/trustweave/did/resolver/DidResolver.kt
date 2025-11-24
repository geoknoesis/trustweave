package com.trustweave.did.resolver

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
 *     DidResolutionResult(document = didDocument)
 * }
 * 
 * val result = resolver.resolve("did:key:123")
 * ```
 */
fun interface DidResolver {
    /**
     * Resolves a DID to a DID resolution result.
     * 
     * @param did The DID string to resolve
     * @return DidResolutionResult if resolved, null if not resolvable
     */
    suspend fun resolve(did: String): DidResolutionResult?
}

