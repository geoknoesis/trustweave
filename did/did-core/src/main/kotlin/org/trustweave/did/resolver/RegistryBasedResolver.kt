package org.trustweave.did.resolver

import org.trustweave.did.identifiers.Did
import org.trustweave.did.DidMethod
import org.trustweave.did.exception.DidException
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.validation.DidValidator

/**
 * Resolver implementation that uses a [DidMethodRegistry] to resolve DIDs.
 *
 * This separates resolution concerns from registry management, following
 * the single responsibility principle.
 *
 * **Example Usage:**
 * ```kotlin
 * val registry = DidMethodRegistry()
 * registry.register(KeyDidMethod(kms))
 *
 * val resolver = RegistryBasedResolver(registry)
 * val result = resolver.resolve("did:key:...")
 * ```
 */
class RegistryBasedResolver(
    private val registry: DidMethodRegistry
) : DidResolver {

    override suspend fun resolve(did: Did): DidResolutionResult {
        // DID is already parsed and validated (Did constructor validates)
        // No need to re-parse or re-validate
        val didString = did.value
        
        try {
            val method = registry.get(did.method)

            if (method == null) {
                return DidResolutionResult.Failure.MethodNotRegistered(
                    method = did.method,
                    availableMethods = registry.getAllMethodNames(),
                    resolutionMetadata = mapOf(
                        "error" to "methodNotSupported",
                        "errorMessage" to "DID method '${did.method}' is not registered",
                        "did" to didString
                    )
                )
            }

            // Use type-safe resolveDid(Did) method
            return method.resolveDid(did)
        } catch (e: DidException) {
            // Convert DidException to resolution result
            val errorMetadata = mutableMapOf<String, Any?>(
                "error" to e.code,
                "errorMessage" to (e.message ?: "Unknown error"),
                "did" to didString
            )
            errorMetadata.putAll(e.context.map { (k, v) -> k to v })
            return DidResolutionResult.Failure.ResolutionError(
                did = did,
                reason = e.message ?: "Unknown error",
                cause = e,
                resolutionMetadata = errorMetadata
            )
        } catch (e: Exception) {
            // Unexpected error
            return DidResolutionResult.Failure.ResolutionError(
                did = did,
                reason = e.message ?: "Unknown error during resolution",
                cause = e,
                resolutionMetadata = mapOf(
                    "error" to "resolutionError",
                    "errorMessage" to (e.message ?: "Unknown error during resolution")
                )
            )
        }
    }
}

/**
 * Extension function to create a resolver from a registry.
 */
fun DidMethodRegistry.asResolver(): DidResolver = RegistryBasedResolver(this)

