package com.trustweave.did.resolver

import com.trustweave.core.types.Did
import com.trustweave.did.DidDocument
import com.trustweave.did.DidDocumentMetadata

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
        val resolutionMetadata: Map<String, Any?> = emptyMap()
    ) : DidResolutionResult()

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
            val resolutionMetadata: Map<String, Any?> = emptyMap()
        ) : Failure()

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
            val resolutionMetadata: Map<String, Any?> = emptyMap()
        ) : Failure()

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
            val resolutionMetadata: Map<String, Any?> = emptyMap()
        ) : Failure()

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
            val resolutionMetadata: Map<String, Any?> = emptyMap()
        ) : Failure()
    }
}

