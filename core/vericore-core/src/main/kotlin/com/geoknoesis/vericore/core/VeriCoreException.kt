package com.geoknoesis.vericore.core

/**
 * Common exception types for VeriCore.
 */

/**
 * Base exception for VeriCore operations.
 */
open class VeriCoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when a requested resource is not found.
 */
class NotFoundException(message: String, cause: Throwable? = null) : VeriCoreException(message, cause)

/**
 * Exception thrown when an operation is invalid or cannot be performed.
 */
class InvalidOperationException(message: String, cause: Throwable? = null) : VeriCoreException(message, cause)

