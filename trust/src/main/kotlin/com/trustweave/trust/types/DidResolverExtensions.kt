package com.trustweave.trust.types

import com.trustweave.did.resolver.DidResolver
import com.trustweave.did.resolver.DidResolutionResult

/**
 * Type-safe extensions for DidResolver using type-safe Did identifier.
 *
 * These extensions provide compile-time type safety for DID identifiers,
 * preventing common errors like passing a KeyId where a Did is expected.
 *
 * **Example:**
 * ```kotlin
 * val did = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
 * val result = resolver.resolve(did)
 * ```
 */

/**
 * Resolves a DID to a DID resolution result.
 *
 * @param did Type-safe DID identifier
 * @return DidResolutionResult if resolved, null if not resolvable
 */
suspend fun DidResolver.resolve(did: Did): DidResolutionResult? {
    return resolve(did.value)
}

