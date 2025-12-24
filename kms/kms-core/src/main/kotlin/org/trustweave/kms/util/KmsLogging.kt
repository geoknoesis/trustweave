package org.trustweave.kms.util

import org.slf4j.Logger

/**
 * Logging utilities and conventions for KMS operations.
 * 
 * This object provides standardized logging patterns to ensure consistency
 * across all KMS plugins.
 * 
 * **Logging Levels:**
 * - **DEBUG**: Detailed diagnostic information (key lookups, cache hits, etc.)
 * - **INFO**: Important operational events (key generation, deletion, successful operations)
 * - **WARN**: Warning conditions (validation failures, algorithm incompatibilities)
 * - **ERROR**: Error conditions (API failures, unexpected exceptions)
 * 
 * **Logging Format:**
 * Use SLF4J parameterized logging for better performance and structured logging:
 * ```kotlin
 * logger.info("Operation completed: keyId={}, algorithm={}", keyId.value, algorithm.name)
 * logger.error("Operation failed: keyId={}, errorCode={}", keyId.value, errorCode, exception)
 * ```
 * 
 * **Context Information:**
 * Always include relevant context in logs:
 * - keyId: The key identifier
 * - algorithm: The algorithm being used
 * - operation: The operation being performed
 * - errorCode/statusCode: HTTP or API error codes when available
 * - requestId/correlationId: Request tracking IDs when available
 */
object KmsLogging {
    /**
     * Logs a successful key generation operation.
     */
    fun logKeyGenerated(
        logger: Logger,
        keyId: String,
        algorithm: String,
        additionalContext: Map<String, Any?> = emptyMap()
    ) {
        val context = buildString {
            append("keyId=$keyId, algorithm=$algorithm")
            additionalContext.forEach { (key, value) ->
                append(", $key=$value")
            }
        }
        logger.info("Generated key: {}", context)
    }

    /**
     * Logs a successful signing operation.
     */
    fun logSigningSuccess(
        logger: Logger,
        keyId: String,
        algorithm: String,
        dataSize: Int,
        signatureSize: Int
    ) {
        logger.debug(
            "Successfully signed data: keyId={}, algorithm={}, dataSize={}, signatureSize={}",
            keyId, algorithm, dataSize, signatureSize
        )
    }

    /**
     * Logs a key deletion operation.
     */
    fun logKeyDeleted(
        logger: Logger,
        keyId: String,
        additionalContext: Map<String, Any?> = emptyMap()
    ) {
        val context = buildString {
            append("keyId=$keyId")
            additionalContext.forEach { (key, value) ->
                append(", $key=$value")
            }
        }
        logger.info("Deleted key: {}", context)
    }

    /**
     * Logs a key not found condition (debug level, as this is often expected).
     */
    fun logKeyNotFound(
        logger: Logger,
        keyId: String,
        operation: String,
        additionalContext: Map<String, Any?> = emptyMap()
    ) {
        val context = buildString {
            append("keyId=$keyId, operation=$operation")
            additionalContext.forEach { (key, value) ->
                append(", $key=$value")
            }
        }
        logger.debug("Key not found: {}", context)
    }

    /**
     * Logs an error condition with full context.
     */
    fun logError(
        logger: Logger,
        message: String,
        keyId: String? = null,
        algorithm: String? = null,
        errorCode: String? = null,
        statusCode: Int? = null,
        requestId: String? = null,
        exception: Throwable? = null
    ) {
        val context = buildString {
            keyId?.let { append("keyId=$it") }
            algorithm?.let { if (isNotEmpty()) append(", ") else Unit; append("algorithm=$it") }
            errorCode?.let { if (isNotEmpty()) append(", ") else Unit; append("errorCode=$it") }
            statusCode?.let { if (isNotEmpty()) append(", ") else Unit; append("statusCode=$it") }
            requestId?.let { if (isNotEmpty()) append(", ") else Unit; append("requestId=$it") }
        }
        
        if (exception != null) {
            logger.error("$message: {}", context, exception)
        } else {
            logger.error("$message: {}", context)
        }
    }

    /**
     * Logs a warning condition (validation failures, incompatibilities, etc.).
     */
    fun logWarning(
        logger: Logger,
        message: String,
        keyId: String? = null,
        algorithm: String? = null,
        reason: String? = null
    ) {
        val context = buildString {
            keyId?.let { append("keyId=$it") }
            algorithm?.let { if (isNotEmpty()) append(", ") else Unit; append("algorithm=$it") }
            reason?.let { if (isNotEmpty()) append(", ") else Unit; append("reason=$it") }
        }
        logger.warn("$message: {}", context)
    }
}

