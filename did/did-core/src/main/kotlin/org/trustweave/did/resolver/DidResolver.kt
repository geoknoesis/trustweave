package org.trustweave.did.resolver

import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolution.ResolutionOptions

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

    /**
     * Resolves a DID with DID Resolution 1.0 §4.1 resolution options.
     *
     * Default behaviour mirrors [org.trustweave.did.DidMethodResolver.resolveDid]: options are
     * validated (§4.4 step 4), unsupported method-specific options produce FEATURE_NOT_SUPPORTED
     * (§4.4 step 3), and anything else delegates to [resolve].
     */
    suspend fun resolve(did: Did, options: ResolutionOptions): DidResolutionResult {
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
        return resolve(did)
    }
}

