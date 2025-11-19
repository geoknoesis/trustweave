package com.geoknoesis.vericore.core

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
 * Gets the result value or throws a VeriCoreError.
 * 
 * @return The result value
 * @throws VeriCoreError if the result is a failure
 */
fun <T> Result<T>.getOrThrowError(): T {
    return getOrElse { throwable ->
        throw throwable.toVeriCoreError()
    }
}

/**
 * Gets the result value or returns a default value based on the error.
 * 
 * @param transform Function to transform the error into a default value
 * @return The result value or the default value
 */
inline fun <T> Result<T>.getOrElse(transform: (Throwable) -> T): T {
    return getOrNull() ?: transform(exceptionOrNull()!!)
}

/**
 * Combines multiple Results into a single Result containing a list of values.
 * 
 * @param transform Function to transform the list of values
 * @return Result containing the transformed value
 */
fun <T, R> List<Result<T>>.combine(transform: (List<T>) -> R): Result<R> = runCatching {
    val values = map { it.getOrThrow() }
    transform(values)
}

/**
 * Maps a list of items to Results and combines them.
 * 
 * @param transform Function to transform each item to a Result
 * @return Result containing the list of transformed values
 */
suspend fun <T, R> List<T>.mapAsync(
    transform: suspend (T) -> Result<R>
): Result<List<R>> = runCatching {
    map { transform(it).getOrThrow() }
}

/**
 * Executes a suspend block and automatically converts any exceptions to VeriCoreError.
 * 
 * This is a convenience function that combines `runCatching` with automatic error conversion,
 * reducing boilerplate in API methods.
 * 
 * **Example:**
 * ```kotlin
 * suspend fun createDid(): Result<DidDocument> = vericoreCatching {
 *     // operation that might throw
 *     didMethod.createDid(options)
 * }
 * ```
 * 
 * @param block The suspend block to execute
 * @return Result with the block result or a VeriCoreError
 */
suspend inline fun <T> vericoreCatching(
    crossinline block: suspend () -> T
): Result<T> = runCatching {
    block()
}.mapError { it.toVeriCoreError() }

/**
 * Utility functions for VeriCore operations.
 */

/**
 * Executes the given action if this Result is a success.
 * 
 * **Example:**
 * ```kotlin
 * vericore.createDid()
 *     .onSuccess { did -> println("Created: ${did.id}") }
 *     .onFailure { error -> println("Error: ${error.message}") }
 * ```
 * 
 * @param action Action to execute with the success value
 * @return This Result for chaining
 */
inline fun <T> Result<T>.onSuccess(action: (value: T) -> Unit): Result<T> {
    if (isSuccess) {
        action(getOrNull()!!)
    }
    return this
}

/**
 * Executes the given action if this Result is a failure.
 * 
 * **Example:**
 * ```kotlin
 * vericore.createDid()
 *     .onSuccess { did -> println("Created: ${did.id}") }
 *     .onFailure { error -> println("Error: ${error.message}") }
 * ```
 * 
 * @param action Action to execute with the failure exception
 * @return This Result for chaining
 */
inline fun <T> Result<T>.onFailure(action: (exception: Throwable) -> Unit): Result<T> {
    if (isFailure) {
        action(exceptionOrNull()!!)
    }
    return this
}

/**
 * Normalizes a key ID by extracting the fragment identifier.
 * 
 * Key IDs can be provided in multiple formats:
 * - Full DID URL: `did:key:z6Mk...#key-1` → returns `key-1`
 * - Fragment only: `#key-1` → returns `key-1`
 * - Plain key ID: `key-1` → returns `key-1`
 * 
 * **Example:**
 * ```kotlin
 * normalizeKeyId("did:key:z6Mk...#key-1") // returns "key-1"
 * normalizeKeyId("#key-1")                // returns "key-1"
 * normalizeKeyId("key-1")                  // returns "key-1"
 * normalizeKeyId("did:key:z6Mk...")       // returns "did:key:z6Mk..."
 * ```
 * 
 * @param keyId The key ID to normalize (may include DID URL or fragment)
 * @return The normalized key ID (fragment part if present, otherwise the original keyId)
 */
fun normalizeKeyId(keyId: String): String {
    if (keyId.isEmpty()) return keyId
    
    val fragmentIndex = keyId.indexOf('#')
    return if (fragmentIndex >= 0 && fragmentIndex < keyId.length - 1) {
        // Has # and content after it
        keyId.substring(fragmentIndex + 1)
    } else {
        // No # or # is at the end (edge case)
        keyId
    }
}

