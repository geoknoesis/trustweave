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
 * the single responsibility principle. The resolver delegates to the appropriate
 * DID method based on the DID's method name.
 *
 * **Architecture:**
 * - Registry manages DID method instances
 * - Resolver handles resolution logic and error conversion
 * - Methods implement actual resolution logic
 *
 * **Error Handling:**
 * - Converts [DidException] to [DidResolutionResult.Failure]
 * - Provides detailed error metadata for debugging
 * - Never throws exceptions (always returns result)
 *
 * **Performance:**
 * - O(1) method lookup from registry
 * - Delegates actual resolution to the method implementation
 * - No caching (methods may implement their own caching)
 *
 * **Example Usage:**
 * ```kotlin
 * val registry = DidMethodRegistry()
 * registry.register(KeyDidMethod(kms))
 * registry.register(WebDidMethod())
 *
 * val resolver = RegistryBasedResolver(registry)
 * 
 * // Resolve a DID
 * val result = resolver.resolve(Did("did:key:z6Mk..."))
 * when (result) {
 *     is DidResolutionResult.Success -> {
 *         println("Resolved: ${result.document.id}")
 *     }
 *     is DidResolutionResult.Failure.MethodNotRegistered -> {
 *         println("Method not available: ${result.method}")
 *     }
 *     // ... handle other cases
 * }
 * ```
 *
 * @param registry The DID method registry to use for method lookup
 * @see DidMethodRegistry for registry operations
 * @see DidResolver for the resolver interface
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
                    resolutionMetadata = DidResolutionMetadata(
                        error = "methodNotSupported",
                        errorMessage = "DID method '${did.method}' is not registered",
                        properties = mapOf("did" to didString)
                    )
                )
            }

            // Use type-safe resolveDid(Did) method
            val result = method.resolveDid(did)
            
            // Convert map-based metadata to structured metadata if needed
            return when (result) {
                is DidResolutionResult.Success -> {
                    if (result.resolutionMetadataMap.isNotEmpty() && 
                        result.resolutionMetadata.contentType == "application/did+ld+json" &&
                        result.resolutionMetadata.error == null) {
                        // Convert from map if needed (backward compatibility)
                        result.copy(
                            resolutionMetadata = DidResolutionMetadata.fromMap(result.resolutionMetadataMap)
                        )
                    } else {
                        result
                    }
                }
                else -> result
            }
        } catch (e: DidException) {
            // Convert DidException to resolution result
            val properties = mutableMapOf<String, String>(
                "did" to didString
            )
            properties.putAll(e.context.mapValues { it.value?.toString() ?: "" })
            return DidResolutionResult.Failure.ResolutionError(
                did = did,
                reason = e.message ?: "Unknown error",
                cause = e,
                resolutionMetadata = DidResolutionMetadata(
                    error = e.code,
                    errorMessage = e.message ?: "Unknown error",
                    properties = properties
                )
            )
        } catch (e: Exception) {
            // Unexpected error
            return DidResolutionResult.Failure.ResolutionError(
                did = did,
                reason = e.message ?: "Unknown error during resolution",
                cause = e,
                resolutionMetadata = DidResolutionMetadata(
                    error = "resolutionError",
                    errorMessage = e.message ?: "Unknown error during resolution"
                )
            )
        }
    }
}

/**
 * Extension function to create a resolver from a registry.
 */
fun DidMethodRegistry.asResolver(): DidResolver = RegistryBasedResolver(this)

