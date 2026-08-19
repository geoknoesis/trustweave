package org.trustweave.did.resolver

import org.trustweave.did.exception.DidException
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.expandRelativeDidUrls
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.representation.DidMediaTypes
import org.trustweave.did.resolution.ResolutionOptions

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
 *     is DidResolutionResult.Deactivated -> {
 *         println("Deactivated: ${result.did}")
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

    override suspend fun resolve(did: Did): DidResolutionResult = resolve(did, ResolutionOptions.EMPTY)

    /**
     * Executes the DID Resolution 1.0 §4.4 algorithm.
     *
     * Step 1 (DID syntax validation) is enforced by the [Did] constructor and by
     * [DidMethodRegistry.resolve] for string input, so this method starts at step 2.
     */
    override suspend fun resolve(did: Did, options: ResolutionOptions): DidResolutionResult {
        // §4.4 step 2 — is the DID method supported?
        val method = registry.get(did.method)
            ?: return DidResolutionResult.Failure.MethodNotRegistered(
                method = did.method,
                availableMethods = registry.getAllMethodNames(),
                resolutionMetadata = DidResolutionMetadata(
                    error = DidResolutionError.methodNotSupported(
                        "DID method '${did.method}' is not registered"
                    ),
                    properties = mapOf("did" to did.value)
                )
            )

        // §4.4 step 4 — are the options valid? (checked before step 3 so that a contradictory
        // option set is reported as INVALID_OPTIONS rather than FEATURE_NOT_SUPPORTED)
        options.validate()?.let { error ->
            return DidResolutionResult.Failure.OptionsError(
                did = did,
                reason = error.detail ?: "Invalid resolution options",
                errorType = error.type
            )
        }

        // §4.4 step 3 — is the requested representation supported?
        val contentType = options.accept?.let { accept ->
            if (!DidMediaTypes.isSupportedDocumentType(accept)) {
                return DidResolutionResult.Failure.OptionsError(
                    did = did,
                    reason = "Representation not supported: '$accept'",
                    errorType = DidErrorType.REPRESENTATION_NOT_SUPPORTED
                )
            }
            DidMediaTypes.normalize(accept)
        } ?: DidMediaTypes.DID

        // §4.4 step 5 — execute the method's Resolve operation.
        val result = try {
            method.resolveDid(did, options)
        } catch (e: DidException) {
            // Map the exception subtype to its §11 error type so the RFC 9457 object asserts
            // the correct condition instead of always claiming INTERNAL_ERROR (HTTP 500) —
            // §12.1 requires 400 for an invalid DID and 404 for not-found.
            val error = when (e) {
                is DidException.DidNotFound -> DidResolutionError.notFound(e.message ?: "DID not found")
                is DidException.InvalidDidFormat -> DidResolutionError.invalidDid(e.message ?: "Invalid DID")
                is DidException.DidMethodNotRegistered ->
                    DidResolutionError.methodNotSupported(e.message ?: "DID method not registered")
                else -> DidResolutionError.internalError(e.message ?: "Unknown error")
            }
            return DidResolutionResult.Failure.ResolutionError(
                did = did,
                reason = e.message ?: "Unknown error",
                cause = e,
                resolutionMetadata = DidResolutionMetadata(
                    error = error,
                    properties = buildMap {
                        put("did", did.value)
                        e.context.forEach { (k, v) -> put(k, v?.toString() ?: "") }
                    }
                )
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return DidResolutionResult.Failure.ResolutionError(
                did = did,
                reason = e.message ?: "Unknown error during resolution",
                cause = e,
                resolutionMetadata = DidResolutionMetadata(
                    error = DidResolutionError.internalError(e.message ?: "Unknown error during resolution"),
                    properties = mapOf("did" to did.value)
                )
            )
        }

        if (result !is DidResolutionResult.Success) return result

        // §4 — the resolved document's `id` MUST equal the DID that was resolved.
        if (result.document.id != did) {
            return DidResolutionResult.Failure.ResolutionError(
                did = did,
                reason = "Resolved document id '${result.document.id.value}' does not match " +
                    "requested DID '${did.value}'",
                resolutionMetadata = DidResolutionMetadata(
                    error = DidResolutionError.invalidDidDocument(
                        "Resolved document id '${result.document.id.value}' does not match " +
                            "requested DID '${did.value}'"
                    )
                )
            )
        }

        // §4.4 — a deactivated DID returns no document.
        if (result.documentMetadata.deactivated) {
            return DidResolutionResult.Deactivated(
                did = did,
                documentMetadata = result.documentMetadata,
                resolutionMetadata = result.resolutionMetadata.copy(contentType = contentType)
            )
        }

        // §4.4 — expandRelativeUrls post-processing.
        val document =
            if (options.expandRelativeUrls) result.document.expandRelativeDidUrls() else result.document

        return result.copy(
            document = document,
            resolutionMetadata = result.resolutionMetadata.copy(contentType = contentType)
        )
    }
}

/**
 * Extension function to create a resolver from a registry.
 */
fun DidMethodRegistry.asResolver(): DidResolver = RegistryBasedResolver(this)
