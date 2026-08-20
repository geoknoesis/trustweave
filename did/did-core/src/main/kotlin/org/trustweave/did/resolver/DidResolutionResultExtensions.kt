package org.trustweave.did.resolver

import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata

/**
 * Ergonomic accessors over [DidResolutionResult].
 */

/** True when the DID resolved to a document. */
val DidResolutionResult.isSuccess: Boolean
    get() = this is DidResolutionResult.Success

/** True when the DID exists but is deactivated (§4.4). */
val DidResolutionResult.isDeactivated: Boolean
    get() = this is DidResolutionResult.Deactivated

/** True when the DID could not be found. */
val DidResolutionResult.isNotFound: Boolean
    get() = this is DidResolutionResult.Failure.NotFound

/** True when resolution failed with an error. Deactivation is NOT an error. */
val DidResolutionResult.hasError: Boolean
    get() = this is DidResolutionResult.Failure

/** The resolved document, or null for deactivated and failed resolutions (§4). */
val DidResolutionResult.documentOrNull: DidDocument?
    get() = (this as? DidResolutionResult.Success)?.document

/** §4.2 resolution metadata for any outcome. */
val DidResolutionResult.resolutionMetadata: DidResolutionMetadata
    get() = when (this) {
        is DidResolutionResult.Success -> resolutionMetadata
        is DidResolutionResult.Deactivated -> resolutionMetadata
        is DidResolutionResult.Failure.NotFound -> resolutionMetadata
        is DidResolutionResult.Failure.InvalidFormat -> resolutionMetadata
        is DidResolutionResult.Failure.MethodNotRegistered -> resolutionMetadata
        is DidResolutionResult.Failure.ResolutionError -> resolutionMetadata
        is DidResolutionResult.Failure.OptionsError -> resolutionMetadata
    }

/** §4.3 document metadata; empty for failed resolutions, as §4 requires. */
val DidResolutionResult.documentMetadata: DidDocumentMetadata
    get() = when (this) {
        is DidResolutionResult.Success -> documentMetadata
        is DidResolutionResult.Deactivated -> documentMetadata
        is DidResolutionResult.Failure -> DidDocumentMetadata()
    }

/** The RFC 9457 error object, or null when resolution did not fail. */
val DidResolutionResult.error: DidResolutionError?
    get() = resolutionMetadata.error

/** The error type URI, or null when resolution did not fail. */
val DidResolutionResult.errorType: String?
    get() = error?.type

/** Human-readable failure text, or null when resolution did not fail. */
val DidResolutionResult.errorMessage: String?
    get() = error?.detail

/**
 * Returns the resolved [DidDocument] or throws.
 *
 * A deactivated DID throws — per §4.4 there is no document to return.
 */
fun DidResolutionResult.getOrThrow(): DidDocument = when (this) {
    is DidResolutionResult.Success -> document
    is DidResolutionResult.Deactivated -> throw IllegalStateException("DID is deactivated: ${did.value}")
    is DidResolutionResult.Failure -> throw IllegalStateException(
        errorMessage ?: "DID resolution failed: ${errorType ?: "unknown error"}"
    )
}
