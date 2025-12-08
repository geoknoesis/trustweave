package com.trustweave.kms.util

import com.trustweave.core.identifiers.KeyId
import com.trustweave.kms.Algorithm
import com.trustweave.kms.results.DeleteKeyResult
import com.trustweave.kms.results.GenerateKeyResult
import com.trustweave.kms.results.GetPublicKeyResult
import com.trustweave.kms.results.SignResult
import org.slf4j.Logger

/**
 * Common error handling utilities for KMS implementations.
 * 
 * Provides standardized error handling patterns to reduce code duplication
 * across different KMS plugin implementations.
 */
object KmsErrorHandler {

    /**
     * Handles generic exceptions and logs them appropriately.
     */
    fun <T> handleGenericError(
        logger: Logger,
        context: Map<String, Any?>,
        operation: String,
        e: Exception
    ): T? {
        logger.error("Unexpected error during $operation", context, e)
        return null
    }

    /**
     * Creates a standardized error context map for logging.
     * 
     * This utility helps create consistent error context across all KMS plugins,
     * ensuring that error logs contain the same structured information.
     * 
     * @param keyId Optional key identifier
     * @param algorithm Optional algorithm used
     * @param operation Optional operation name (e.g., "generateKey", "sign")
     * @param additional Additional context key-value pairs
     * @return Map of error context for logging
     * 
     * **Example:**
     * ```kotlin
     * val context = KmsErrorHandler.createErrorContext(
     *     keyId = keyId,
     *     algorithm = algorithm,
     *     operation = "generateKey",
     *     additional = mapOf("requestId" to requestId)
     * )
     * logger.error("Operation failed", context, exception)
     * ```
     */
    fun createErrorContext(
        keyId: KeyId? = null,
        algorithm: Algorithm? = null,
        operation: String? = null,
        additional: Map<String, Any?> = emptyMap()
    ): Map<String, Any?> {
        return buildMap {
            keyId?.let { put("keyId", it.value) }
            algorithm?.let { put("algorithm", it.name) }
            operation?.let { put("operation", it) }
            putAll(additional)
        }
    }
    
    /**
     * Handles generic exceptions and converts them to appropriate Result failure types.
     * 
     * This is a generic handler that can be used for operations that don't have
     * provider-specific exception types.
     * 
     * @param logger Logger instance for error logging
     * @param keyId Optional key identifier
     * @param algorithm Optional algorithm
     * @param operation Operation name for logging
     * @param e Exception to handle
     * @return Appropriate failure result based on exception type
     */
    fun handleGenericException(
        logger: Logger,
        keyId: KeyId? = null,
        algorithm: Algorithm? = null,
        operation: String,
        e: Exception
    ): GenerateKeyResult.Failure? {
        val context = createErrorContext(keyId, algorithm, operation)
        logger.error("Unexpected error during $operation", context, e)
        
        return when {
            algorithm != null -> GenerateKeyResult.Failure.Error(
                algorithm = algorithm,
                reason = "Failed to $operation: ${e.message ?: "Unknown error"}",
                cause = e
            )
            else -> null
        }
    }
}

