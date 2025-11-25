package com.trustweave.did.resolver

import com.trustweave.did.DidDocument
import com.trustweave.did.exception.DidException

/**
 * Extension functions for [DidResolutionResult] to improve ergonomics.
 */

/**
 * Returns true if the resolution result indicates the DID was not found.
 */
val DidResolutionResult.isNotFound: Boolean
    get() = document == null && 
            (resolutionMetadata["error"] == "notFound" || 
             resolutionMetadata["error"] == "DID_NOT_FOUND")

/**
 * Returns true if the resolution result indicates an error occurred.
 */
val DidResolutionResult.hasError: Boolean
    get() = resolutionMetadata.containsKey("error") && document == null

/**
 * Gets the error code from resolution metadata, if any.
 */
val DidResolutionResult.errorCode: String?
    get() = resolutionMetadata["error"] as? String

/**
 * Gets the error message from resolution metadata, if any.
 */
val DidResolutionResult.errorMessage: String?
    get() = resolutionMetadata["errorMessage"] as? String ?: 
            resolutionMetadata["error"] as? String

/**
 * Requires that the resolution result contains a document.
 * 
 * @throws DidException.DidNotFound if document is null
 * @return The resolved DID document
 */
fun DidResolutionResult.requireDocument(): DidDocument {
    return document ?: throw DidException.DidNotFound(
        did = resolutionMetadata["did"] as? String ?: "unknown",
        availableMethods = (resolutionMetadata["availableMethods"] as? List<*>)?.mapNotNull { it.toString() } ?: emptyList()
    )
}

/**
 * Gets the document or returns null if not found.
 * 
 * This is a convenience function that makes the nullable nature explicit.
 */
fun DidResolutionResult.getDocumentOrNull(): DidDocument? = document

/**
 * Checks if the resolution was successful (document is present).
 */
val DidResolutionResult.isSuccess: Boolean
    get() = document != null && !hasError

