package org.trustweave.kms.util

import org.slf4j.Logger
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import kotlin.coroutines.cancellation.CancellationException

/** Shared logging and failure conversion for KMS implementations. */
object KmsErrorHandler {
    /**
     * Logs an ordinary failure and returns null.
     * Cancellation and interruption propagate without being logged as operation failures.
     */
    fun <T> handleGenericError(
        logger: Logger,
        context: Map<String, Any?>,
        operation: String,
        e: Exception,
    ): T? {
        e.rethrowControlFlowException()
        logger.error("Unexpected error during {}: {}", operation, context, e)
        return null
    }

    /** Creates logging context; [additional] fields override standard fields with the same name. */
    fun createErrorContext(
        keyId: KeyId? = null,
        algorithm: Algorithm? = null,
        operation: String? = null,
        additional: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> =
        buildMap {
            keyId?.let { put("keyId", it.value) }
            algorithm?.let { put("algorithm", it.name) }
            operation?.let { put("operation", it) }
            putAll(additional)
        }

    /**
     * Logs an ordinary exception and converts it to a key-generation failure when [algorithm]
     * is supplied, otherwise returns null. Cancellation and interruption propagate unchanged.
     */
    fun handleGenericException(
        logger: Logger,
        keyId: KeyId? = null,
        algorithm: Algorithm? = null,
        operation: String,
        e: Exception,
    ): GenerateKeyResult.Failure? {
        e.rethrowControlFlowException()
        val context = createErrorContext(keyId, algorithm, operation)
        logger.error("Unexpected error during {}: {}", operation, context, e)
        return algorithm?.let {
            GenerateKeyResult.Failure.Error(
                algorithm = it,
                reason = "Failed to $operation: ${e.message ?: "Unknown error"}",
                cause = e,
            )
        }
    }

    private fun Exception.rethrowControlFlowException() {
        when (this) {
            is CancellationException -> throw this
            is InterruptedException -> {
                Thread.currentThread().interrupt()
                throw this
            }
        }
    }
}
