package org.trustweave.core.util

import org.trustweave.core.exception.toTrustWeaveException
import kotlin.coroutines.cancellation.CancellationException

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
 * A captured [CancellationException] is rethrown as-is (never converted) so coroutine
 * cancellation always propagates instead of being disguised as a domain error.
 *
 * @return The result value
 * @throws TrustWeaveException if the result is a failure
 */
fun <T> Result<T>.getOrThrowException(): T {
    return getOrElse { throwable ->
        if (throwable is CancellationException) throw throwable
        throw throwable.toTrustWeaveException()
    }
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
fun <T, R> List<Result<T>>.combine(transform: (List<T>) -> R): Result<R> = try {
    // getOrThrow() will throw on first failure, short-circuiting the map operation.
    // This ensures we don't process remaining items if one has already failed.
    Result.success(transform(map { it.getOrThrow() }))
} catch (e: CancellationException) {
    // Never capture coroutine cancellation in a Result — rethrow so it propagates.
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}

/**
 * Maps a list of items to Results and combines them sequentially.
 *
 * This function processes items one by one, stopping at the first failure.
 * All items are processed in order, making it suitable for operations that
 * must complete sequentially (e.g., database transactions, file operations).
 *
 * **When to use:**
 * - Operations that must complete in order
 * - When you need to stop on first failure
 * - Sequential processing with early termination
 *
 * **When NOT to use:**
 * - For parallel processing (use `async`/`await` instead)
 * - When you need all results even if some fail (use `mapNotNull` instead)
 *
 * **Example:**
 * ```kotlin
 * val items = listOf(1, 2, 3)
 * val results = items.mapSequential { item ->
 *     Result.success(item * 2)
 * }
 * // Result.success([2, 4, 6])
 * ```
 *
 * **Cancellation:** if the calling coroutine is cancelled while a transform is suspended,
 * the [CancellationException] is rethrown — it is never captured in the returned Result.
 *
 * @param transform Function to transform each item to a Result
 * @return Result containing the list of transformed values, or failure if any transform fails
 */
suspend fun <T, R> List<T>.mapSequential(
    transform: suspend (T) -> Result<R>
): Result<List<R>> = try {
    Result.success(map { transform(it).getOrThrow() })
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
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
 * **Cancellation:** a [CancellationException] thrown by [block] (coroutine cancellation,
 * including enclosing `withTimeout`) is rethrown — never captured or converted — so
 * structured concurrency keeps working through this helper.
 *
 * @param block The suspend block to execute
 * @return Result with the block result or a TrustWeaveException
 */
suspend inline fun <T> trustweaveCatching(
    crossinline block: suspend () -> T
): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e.toTrustWeaveException())
}

// Note: this file intentionally does NOT define `Result.onSuccess` / `Result.onFailure`.
// The Kotlin standard library already provides them; redefining them here shadowed the
// stdlib versions inside this package and silently skipped `null` success values
// (`getOrNull()?.let(action)`). Use the stdlib `kotlin.onSuccess` / `kotlin.onFailure`.

