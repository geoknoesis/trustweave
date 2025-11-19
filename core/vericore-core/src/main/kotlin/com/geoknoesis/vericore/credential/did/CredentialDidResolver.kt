package com.geoknoesis.vericore.credential.did

/**
 * Result returned by DID resolver implementations used by credential tooling.
 *
 * @param document The resolved DID document (if available)
 * @param raw      Raw resolution payload that can be inspected by callers
 * @param metadata Optional metadata associated with the resolution
 * @param isResolvable Indicates whether the DID could be resolved
 */
data class CredentialDidResolution(
    val document: Any? = null,
    val raw: Any? = null,
    val metadata: Map<String, Any?> = emptyMap(),
    val isResolvable: Boolean = false
)

/**
 * Functional interface describing the minimal contract for resolving DIDs.
 *
 * The resolver returns a [CredentialDidResolution] describing the outcome or
 * `null` when the DID could not be resolved.
 */
fun interface CredentialDidResolver {
    suspend fun resolve(did: String): CredentialDidResolution?
}
