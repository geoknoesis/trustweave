package com.trustweave.did.resolver

import com.trustweave.did.Did
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
    
    override suspend fun resolve(did: String): DidResolutionResult? {
        // Validate DID format
        val validationResult = DidValidator.validateFormat(did)
        if (!validationResult.isValid()) {
            return DidResolutionResult(
                document = null,
                documentMetadata = com.trustweave.did.DidDocumentMetadata(),
                resolutionMetadata = mapOf(
                    "error" to "invalidDid",
                    "errorMessage" to (validationResult.errorMessage() ?: "Invalid DID format"),
                    "did" to did
                )
            )
        }
        
        try {
            val parsed = Did.parse(did)
            val method = registry.get(parsed.method)
            
            if (method == null) {
                return DidResolutionResult(
                    document = null,
                    documentMetadata = com.trustweave.did.DidDocumentMetadata(),
                    resolutionMetadata = mapOf(
                        "error" to "methodNotSupported",
                        "errorMessage" to "DID method '${parsed.method}' is not registered",
                        "did" to did,
                        "method" to parsed.method,
                        "availableMethods" to registry.getAllMethodNames()
                    )
                )
            }
            
            return method.resolveDid(did)
        } catch (e: IllegalArgumentException) {
            // Invalid DID format
            return DidResolutionResult(
                document = null,
                documentMetadata = com.trustweave.did.DidDocumentMetadata(),
                resolutionMetadata = mapOf<String, Any?>(
                    "error" to "invalidDid",
                    "errorMessage" to (e.message ?: "Invalid DID format"),
                    "did" to did
                )
            )
        } catch (e: DidException) {
            // Convert DidException to resolution result
            val errorMetadata = mutableMapOf<String, Any?>(
                "error" to e.code,
                "errorMessage" to (e.message ?: "Unknown error"),
                "did" to did
            )
            errorMetadata.putAll(e.context.map { (k, v) -> k to v })
            return DidResolutionResult(
                document = null,
                documentMetadata = com.trustweave.did.DidDocumentMetadata(),
                resolutionMetadata = errorMetadata
            )
        } catch (e: Exception) {
            // Unexpected error
            return DidResolutionResult(
                document = null,
                documentMetadata = com.trustweave.did.DidDocumentMetadata(),
                resolutionMetadata = mapOf<String, Any?>(
                    "error" to "resolutionError",
                    "errorMessage" to (e.message ?: "Unknown error during resolution"),
                    "did" to did
                )
            )
        }
    }
}

/**
 * Extension function to create a resolver from a registry.
 */
fun DidMethodRegistry.asResolver(): DidResolver = RegistryBasedResolver(this)

