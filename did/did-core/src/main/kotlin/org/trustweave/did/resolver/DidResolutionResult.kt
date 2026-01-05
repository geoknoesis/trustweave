package org.trustweave.did.resolver

import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata

/**
 * Result of DID resolution following W3C DID Core specification.
 *
 * Sealed class for exhaustive handling of resolution outcomes.
 * Provides type-safe result handling with detailed error information.
 *
 * **Example Usage:**
 * ```kotlin
 * when (val result = resolver.resolveDid(did)) {
 *     is DidResolutionResult.Success -> {
 *         println("Resolved: ${result.document.id}")
 *     }
 *     is DidResolutionResult.Failure.NotFound -> {
 *         println("DID not found: ${result.did}")
 *     }
 *     is DidResolutionResult.Failure.InvalidFormat -> {
 *         println("Invalid format: ${result.reason}")
 *     }
 *     is DidResolutionResult.Failure.MethodNotRegistered -> {
 *         println("Method not registered: ${result.method}")
 *     }
 * }
 * ```
 */
sealed class DidResolutionResult {
    /**
     * DID resolution succeeded.
     *
     * @param document The resolved DID Document
     * @param documentMetadata Metadata about the document (e.g., created, updated timestamps)
     * @param resolutionMetadata Additional metadata about the resolution process
     */
    data class Success(
        val document: DidDocument,
        val documentMetadata: DidDocumentMetadata = DidDocumentMetadata(),
        val resolutionMetadata: DidResolutionMetadata = DidResolutionMetadata()
    ) : DidResolutionResult() {
        /**
         * Backward compatibility: access resolution metadata as map.
         */
        val resolutionMetadataMap: Map<String, Any?> get() = resolutionMetadata.toMap()
        
        /**
         * Constructor for backward compatibility with map-based metadata.
         */
        constructor(
            document: DidDocument,
            documentMetadata: DidDocumentMetadata,
            resolutionMetadataMap: Map<String, Any?>
        ) : this(
            document = document,
            documentMetadata = documentMetadata,
            resolutionMetadata = DidResolutionMetadata.fromMap(resolutionMetadataMap)
        )
    }

    /**
     * DID resolution failed.
     */
    sealed class Failure : DidResolutionResult() {
        /**
         * DID was not found.
         *
         * @param did Type-safe DID identifier that was not found
         * @param reason Optional reason for the failure
         * @param resolutionMetadata Additional metadata about the resolution attempt
         */
        data class NotFound(
            val did: Did,
            val reason: String? = null,
            val resolutionMetadata: DidResolutionMetadata = DidResolutionMetadata(
                error = "notFound",
                errorMessage = reason ?: "DID not found"
            )
        ) : Failure() {
            /**
             * Backward compatibility constructor.
             */
            constructor(
                did: Did,
                reason: String?,
                resolutionMetadataMap: Map<String, Any?>
            ) : this(
                did = did,
                reason = reason,
                resolutionMetadata = DidResolutionMetadata.fromMap(resolutionMetadataMap)
            )
            
            val resolutionMetadataMap: Map<String, Any?> get() = resolutionMetadata.toMap()
        }

        /**
         * DID format is invalid.
         *
         * @param did The invalid DID string (before validation)
         * @param reason Reason why the format is invalid
         * @param resolutionMetadata Additional metadata
         */
        data class InvalidFormat(
            val did: String,
            val reason: String,
            val resolutionMetadata: DidResolutionMetadata = DidResolutionMetadata(
                error = "invalidDid",
                errorMessage = reason
            )
        ) : Failure() {
            constructor(
                did: String,
                reason: String,
                resolutionMetadataMap: Map<String, Any?>
            ) : this(
                did = did,
                reason = reason,
                resolutionMetadata = DidResolutionMetadata.fromMap(resolutionMetadataMap)
            )
            
            val resolutionMetadataMap: Map<String, Any?> get() = resolutionMetadata.toMap()
        }

        /**
         * DID method is not registered.
         *
         * @param method The method name that is not registered
         * @param availableMethods List of available method names
         * @param resolutionMetadata Additional metadata
         */
        data class MethodNotRegistered(
            val method: String,
            val availableMethods: List<String> = emptyList(),
            val resolutionMetadata: DidResolutionMetadata = DidResolutionMetadata(
                error = "methodNotSupported",
                errorMessage = "DID method '$method' is not registered"
            )
        ) : Failure() {
            constructor(
                method: String,
                availableMethods: List<String>,
                resolutionMetadataMap: Map<String, Any?>
            ) : this(
                method = method,
                availableMethods = availableMethods,
                resolutionMetadata = DidResolutionMetadata.fromMap(resolutionMetadataMap)
            )
            
            val resolutionMetadataMap: Map<String, Any?> get() = resolutionMetadata.toMap()
        }

        /**
         * Resolution failed due to an unexpected error.
         *
         * @param did Type-safe DID identifier that failed to resolve
         * @param reason Error reason
         * @param cause Optional underlying exception
         * @param resolutionMetadata Additional metadata
         */
        data class ResolutionError(
            val did: Did,
            val reason: String,
            val cause: Throwable? = null,
            val resolutionMetadata: DidResolutionMetadata = DidResolutionMetadata(
                error = "resolutionError",
                errorMessage = reason
            )
        ) : Failure() {
            constructor(
                did: Did,
                reason: String,
                cause: Throwable?,
                resolutionMetadataMap: Map<String, Any?>
            ) : this(
                did = did,
                reason = reason,
                cause = cause,
                resolutionMetadata = DidResolutionMetadata.fromMap(resolutionMetadataMap)
            )
            
            val resolutionMetadataMap: Map<String, Any?> get() = resolutionMetadata.toMap()
        }
    }
}

