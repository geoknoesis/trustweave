package com.trustweave.core

/**
 * Common exception types for TrustWeave.
 */

/**
 * Base exception for TrustWeave operations.
 */
open class TrustWeaveException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when a requested resource is not found.
 */
class NotFoundException(message: String, cause: Throwable? = null) : TrustWeaveException(message, cause)

/**
 * Exception thrown when an operation is invalid or cannot be performed.
 */
class InvalidOperationException(message: String, cause: Throwable? = null) : TrustWeaveException(message, cause)

