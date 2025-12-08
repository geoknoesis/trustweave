package com.trustweave.did.resolver

import com.trustweave.did.identifiers.Did
import com.trustweave.did.DidMethod
import com.trustweave.did.exception.DidException
import com.trustweave.did.registry.DidMethodRegistry
import com.trustweave.did.validation.DidValidator

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
        val didString = did.value
        // Validate DID format
        val validationResult = DidValidator.validateFormat(didString)
        if (!validationResult.isValid()) {
            return DidResolutionResult.Failure.InvalidFormat(
                did = didString,
                reason = validationResult.errorMessage() ?: "Invalid DID format",
                resolutionMetadata = mapOf(
                    "error" to "invalidDid",
                    "errorMessage" to (validationResult.errorMessage() ?: "Invalid DID format")
                )
            )
        }

        try {
            val parsed = Did(didString)
            val method = registry.get(parsed.method)

            if (method == null) {
                return DidResolutionResult.Failure.MethodNotRegistered(
                    method = parsed.method,
                    availableMethods = registry.getAllMethodNames(),
                    resolutionMetadata = mapOf(
                        "error" to "methodNotSupported",
                        "errorMessage" to "DID method '${parsed.method}' is not registered",
                        "did" to didString
                    )
                )
            }

            // Use type-safe resolveDid(Did) method
            return method.resolveDid(parsed)
        } catch (e: IllegalArgumentException) {
            // Invalid DID format
            return DidResolutionResult.Failure.InvalidFormat(
                did = didString,
                reason = e.message ?: "Invalid DID format",
                resolutionMetadata = mapOf(
                    "error" to "invalidDid",
                    "errorMessage" to (e.message ?: "Invalid DID format")
                )
            )
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

