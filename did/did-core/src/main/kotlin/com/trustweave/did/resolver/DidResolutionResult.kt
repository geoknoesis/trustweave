package com.trustweave.did.resolver

import com.trustweave.did.DidDocument
import com.trustweave.did.DidDocumentMetadata

/**
 * Result of DID resolution following W3C DID Core specification.
 *
 * This is the standard return type for DID resolution operations, containing
 * the resolved DID document (if found), document metadata, and resolution metadata.
 *
 * @param document The resolved DID Document (null if not found)
 * @param documentMetadata Metadata about the document (e.g., created, updated timestamps)
 * @param resolutionMetadata Metadata about the resolution process
 */
data class DidResolutionResult(
    val document: DidDocument?,
    val documentMetadata: DidDocumentMetadata = DidDocumentMetadata(),
    val resolutionMetadata: Map<String, Any?> = emptyMap()
)

