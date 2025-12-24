package org.trustweave.credential.exchange.result

import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.credential.exchange.ExchangeOperation
import org.trustweave.credential.exchange.model.ExchangeMessageType

/**
 * Result of an exchange operation.
 * 
 * Type-safe, exhaustive error handling similar to VerificationResult.
 * Uses sealed classes for compile-time exhaustiveness checking.
 * 
 * **Usage:**
 * ```kotlin
 * when (val result = exchangeService.offer(request)) {
 *     is ExchangeResult.Success -> {
 *         val response = result.value
 *         // Handle success
 *     }
 *     is ExchangeResult.Failure.ProtocolNotSupported -> {
 *         // Handle protocol not supported
 *     }
 *     // Compiler ensures all cases handled
 * }
 * ```
 */
sealed class ExchangeResult<out T> {
    /**
     * Successful operation result.
     */
    data class Success<T>(val value: T) : ExchangeResult<T>()
    
    /**
     * Failed operation result.
     */
    sealed class Failure : ExchangeResult<Nothing>() {
        abstract val errors: List<String>
        abstract val warnings: List<String>
        
        /**
         * Protocol is not registered or not available.
         */
        data class ProtocolNotSupported(
                val protocolName: ExchangeProtocolName,
                val availableProtocols: List<ExchangeProtocolName> = emptyList(),
            override val errors: List<String> = emptyList(),
            override val warnings: List<String> = emptyList()
        ) : Failure() {
            constructor(
                protocolName: ExchangeProtocolName,
                availableProtocols: List<ExchangeProtocolName> = emptyList()
            ) : this(
                protocolName = protocolName,
                availableProtocols = availableProtocols,
                errors = if (availableProtocols.isEmpty()) {
                    listOf("Protocol '${protocolName.value}' not registered. No protocols available.")
                } else {
                    listOf("Protocol '${protocolName.value}' not registered. Available: ${availableProtocols.joinToString(", ") { it.value }}")
                },
                warnings = emptyList()
            )
        }
        
        /**
         * Protocol does not support the requested operation.
         */
        data class OperationNotSupported(
                val protocolName: ExchangeProtocolName,
                val operation: ExchangeOperation,
            val supportedOperations: List<ExchangeOperation> = emptyList(),
            override val errors: List<String> = emptyList(),
            override val warnings: List<String> = emptyList()
        ) : Failure()
        
        /**
         * Request validation failed.
         */
        data class InvalidRequest(
            val field: String,
            val reason: String,
            override val errors: List<String> = emptyList(),
            override val warnings: List<String> = emptyList()
        ) : Failure()
        
        /**
         * Exchange message not found.
         */
        data class MessageNotFound(
            val messageId: String,
            val messageType: ExchangeMessageType? = null,
            override val errors: List<String> = emptyList(),
            override val warnings: List<String> = emptyList()
        ) : Failure()
        
        /**
         * Network or transport error.
         */
        data class NetworkError(
            val reason: String,
            override val errors: List<String> = emptyList(),
            override val warnings: List<String> = emptyList(),
            val cause: Throwable? = null
        ) : Failure()
        
        /**
         * Unknown or unexpected error.
         */
        data class Unknown(
            val reason: String,
            override val errors: List<String> = emptyList(),
            override val warnings: List<String> = emptyList(),
            val cause: Throwable? = null
        ) : Failure()
    }
    
    /**
     * True if result is success.
     */
    val isSuccess: Boolean 
        get() = this is Success
    
    /**
     * True if result is failure.
     */
    val isFailure: Boolean 
        get() = this is Failure
}

/**
 * Extension function for fluent success handling.
 */
inline fun <T> ExchangeResult<T>.onSuccess(action: (T) -> Unit): ExchangeResult<T> {
    if (this is ExchangeResult.Success) action(value)
    return this
}

/**
 * Extension function for fluent failure handling.
 */
inline fun <T> ExchangeResult<T>.onFailure(action: (ExchangeResult.Failure) -> Unit): ExchangeResult<T> {
    if (this is ExchangeResult.Failure) action(this)
    return this
}

/**
 * Extension function to get value or throw exception.
 */
fun <T> ExchangeResult<T>.getOrThrow(): T {
    return when (this) {
        is ExchangeResult.Success -> value
        is ExchangeResult.Failure -> throw IllegalStateException(
            "Exchange operation failed: ${errors.joinToString("; ")}"
        )
    }
}

/**
 * Extension function to get value or null.
 */
fun <T> ExchangeResult<T>.getOrNull(): T? {
    return when (this) {
        is ExchangeResult.Success -> value
        is ExchangeResult.Failure -> null
    }
}

/**
 * Map over success value.
 */
inline fun <T, R> ExchangeResult<T>.map(transform: (T) -> R): ExchangeResult<R> {
    return when (this) {
        is ExchangeResult.Success -> ExchangeResult.Success(transform(value))
        is ExchangeResult.Failure -> this
    }
}

/**
 * FlatMap (monadic bind).
 */
inline fun <T, R> ExchangeResult<T>.flatMap(transform: (T) -> ExchangeResult<R>): ExchangeResult<R> {
    return when (this) {
        is ExchangeResult.Success -> transform(value)
        is ExchangeResult.Failure -> this
    }
}

/**
 * Fold (pattern matching as expression).
 */
inline fun <T, R> ExchangeResult<T>.fold(
    onFailure: (ExchangeResult.Failure) -> R,
    onSuccess: (T) -> R
): R {
    return when (this) {
        is ExchangeResult.Success -> onSuccess(value)
        is ExchangeResult.Failure -> onFailure(this)
    }
}

/**
 * Recover from specific failures.
 */
inline fun <T> ExchangeResult<T>.recover(
    predicate: (ExchangeResult.Failure) -> Boolean,
    transform: (ExchangeResult.Failure) -> T
): ExchangeResult<T> {
    return when (this) {
        is ExchangeResult.Success -> this
        is ExchangeResult.Failure -> if (predicate(this)) {
            ExchangeResult.Success(transform(this))
        } else {
            this
        }
    }
}

/**
 * Recover from failures by providing alternative result.
 */
inline fun <T> ExchangeResult<T>.recoverCatching(
    transform: (ExchangeResult.Failure) -> ExchangeResult<T>
): ExchangeResult<T> {
    return when (this) {
        is ExchangeResult.Success -> this
        is ExchangeResult.Failure -> try {
            transform(this)
        } catch (e: Exception) {
            ExchangeResult.Failure.Unknown(
                reason = "Exception during recovery: ${e.message}",
                cause = e
            )
        }
    }
}

