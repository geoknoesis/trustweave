package org.trustweave.did.resolution

import org.trustweave.did.model.DidDocument
import org.trustweave.did.representation.APPLICATION_DID_MEDIA_TYPE

/**
 * Types aligned with [DID Resolution v0.3](https://www.w3.org/TR/did-resolution/) for
 * resolve/dereference inputs and outputs.
 *
 * **resolve(did, options)** output: [org.trustweave.did.resolver.DidResolutionResult.Success]
 * already carries `document`, `documentMetadata`, and `resolutionMetadata`, matching
 * Resolution v0.3 §4.1. HTTP binding: use [APPLICATION_DID_MEDIA_TYPE] for Accept/Content-Type
 * when serving or requesting DID documents.
 *
 * **dereference(didUrl, options)** is not yet implemented; [DereferenceResult] and
 * [ResolutionOptions] are provided for future API alignment.
 */

/**
 * Options for DID resolution or dereference (Resolution v0.3).
 * Extend as needed (e.g. accept, versionId).
 */
data class ResolutionOptions(
    val accept: String? = APPLICATION_DID_MEDIA_TYPE
)

/**
 * Result of dereferencing a DID URL per Resolution v0.3 §4.2.
 * Resource may be a DID document, a verification method, a service, or other content.
 */
data class DereferenceResult(
    val content: DereferenceContent,
    val dereferenceMetadata: Map<String, Any?> = emptyMap()
)

sealed class DereferenceContent {
    data class Document(val document: DidDocument) : DereferenceContent()
    data class Resource(val data: Any) : DereferenceContent()
}
