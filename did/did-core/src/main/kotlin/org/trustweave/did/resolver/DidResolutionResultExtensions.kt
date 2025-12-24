package org.trustweave.did.resolver

import org.trustweave.did.model.DidDocument
import org.trustweave.did.exception.DidException

/**
 * Extension functions for [DidResolutionResult] to improve ergonomics.
 */

/**
 * Returns true if the resolution result indicates the DID was not found.
 */
val DidResolutionResult.isNotFound: Boolean
    get() = this is DidResolutionResult.Failure.NotFound

/**
 * Returns true if the resolution result indicates an error occurred.
 */
val DidResolutionResult.hasError: Boolean
    get() = this is DidResolutionResult.Failure

/**
 * Gets the error code from resolution metadata, if any.
 */
val DidResolutionResult.errorCode: String?
    get() = when (this) {
        is DidResolutionResult.Success -> null
        is DidResolutionResult.Failure -> {
            when (this) {
                is DidResolutionResult.Failure.NotFound -> "NOT_FOUND"
                is DidResolutionResult.Failure.InvalidFormat -> "INVALID_FORMAT"
                is DidResolutionResult.Failure.MethodNotRegistered -> "METHOD_NOT_REGISTERED"
                is DidResolutionResult.Failure.ResolutionError -> "RESOLUTION_ERROR"
            }
        }
    }

/**
 * Gets the error message from resolution metadata, if any.
 */
val DidResolutionResult.errorMessage: String?
    get() = when (this) {
        is DidResolutionResult.Success -> null
        is DidResolutionResult.Failure.NotFound -> reason ?: "DID not found"
        is DidResolutionResult.Failure.InvalidFormat -> reason
        is DidResolutionResult.Failure.MethodNotRegistered -> "DID method '${method}' is not registered"
        is DidResolutionResult.Failure.ResolutionError -> reason
    }


/**
 * Checks if the resolution was successful (document is present).
 */
val DidResolutionResult.isSuccess: Boolean
    get() = this is DidResolutionResult.Success

