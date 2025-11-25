package com.trustweave.core.util

import com.trustweave.core.exception.toTrustWeaveException

/**
 * Extension functions for Result<T> to improve error handling.
 */

/**
 * Maps the error in a Result using the provided transform function.
 * If the result is a failure, the error is transformed and wrapped in a new Result.
 * 
 * @param transform Function to transform the error
 * @return Result with transformed error, or the original success result
 */
inline fun <T> Result<T>.mapError(transform: (Throwable) -> Throwable): Result<T> {
    return fold(
        onSuccess = { Result.success(it) },
        onFailure = { throwable -> Result.failure(transform(throwable)) }
    )
}

/**
 * Gets the result value or throws a TrustWeaveException.
 * 
 * @return The result value
 * @throws TrustWeaveException if the result is a failure
 */
fun <T> Result<T>.getOrThrowException(): T {
    return getOrElse { throwable ->
        throw throwable.toTrustWeaveException()
    }
}

/**
 * Gets the result value or returns a default value based on the error.
 * 
 * @param transform Function to transform the error into a default value
 * @return The result value or the default value
 */
inline fun <T> Result<T>.getOrElse(transform: (Throwable) -> T): T {
    return fold(
        onSuccess = { it },
        onFailure = transform
    )
}

/**
 * Combines multiple Results into a single Result containing a list of values.
 * 
 * This function uses an "all-or-nothing" strategy: if any Result in the list
 * is a failure, the entire operation fails. This is implemented by using
 * getOrThrow() which will throw the first encountered exception, causing
 * runCatching to wrap it in a Result.failure.
 * 
 * For partial success scenarios, use mapNotNull or filter instead.
 * 
 * @param transform Function to transform the list of values
 * @return Result containing the transformed value, or failure if any input Result failed
 */
fun <T, R> List<Result<T>>.combine(transform: (List<T>) -> R): Result<R> = runCatching {
    // getOrThrow() will throw on first failure, short-circuiting the map operation.
    // This ensures we don't process remaining items if one has already failed.
    val values = map { it.getOrThrow() }
    transform(values)
}

/**
 * Maps a list of items to Results and combines them sequentially.
 * 
 * Note: Despite the name, this function executes sequentially, not in parallel.
 * For parallel execution, use coroutines with async/await.
 * 
 * @param transform Function to transform each item to a Result
 * @return Result containing the list of transformed values
 */
suspend fun <T, R> List<T>.mapSequential(
    transform: suspend (T) -> Result<R>
): Result<List<R>> = runCatching {
    map { transform(it).getOrThrow() }
}

/**
 * Executes a suspend block and automatically converts any exceptions to TrustWeaveException.
 * 
 * This is a convenience function that combines `runCatching` with automatic error conversion,
 * reducing boilerplate in API methods.
 * 
 * **Example:**
 * ```kotlin
 * suspend fun performOperation(): Result<ResultType> = trustweaveCatching {
 *     // operation that might throw
 *     someService.performOperation(options)
 * }
 * ```
 * 
 * **Note:** The error conversion transforms any `Throwable` to a `TrustWeaveException`,
 * so the type system allows this transformation.
 * 
 * @param block The suspend block to execute
 * @return Result with the block result or a TrustWeaveException
 */
suspend inline fun <T> trustweaveCatching(
    crossinline block: suspend () -> T
): Result<T> = runCatching {
    block()
}.mapError { it.toTrustWeaveException() }

/**
 * Utility functions for TrustWeave operations.
 */

/**
 * Executes the given action if this Result is a success.
 * 
 * **Example:**
 * ```kotlin
 * result
 *     .onSuccess { value -> println("Success: $value") }
 *     .onFailure { error -> println("Error: ${error.message}") }
 * ```
 * 
 * @param action Action to execute with the success value
 * @return This Result for chaining
 */
inline fun <T> Result<T>.onSuccess(action: (value: T) -> Unit): Result<T> {
    if (isSuccess) {
        getOrNull()?.let(action)
    }
    return this
}

/**
 * Executes the given action if this Result is a failure.
 * 
 * **Example:**
 * ```kotlin
 * result
 *     .onSuccess { value -> println("Success: $value") }
 *     .onFailure { error -> println("Error: ${error.message}") }
 * ```
 * 
 * @param action Action to execute with the failure exception
 * @return This Result for chaining
 */
inline fun <T> Result<T>.onFailure(action: (exception: Throwable) -> Unit): Result<T> {
    if (isFailure) {
        exceptionOrNull()?.let(action)
    }
    return this
}


