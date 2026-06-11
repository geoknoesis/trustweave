package org.trustweave.core.util

import org.trustweave.core.exception.TrustWeaveException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * Comprehensive tests for ResultExtensions.
 */
class ResultExtensionsTest {

    @Test
    fun `test mapError transforms failure`() {
        val originalError = IllegalArgumentException("Original error")
        val result: Result<String> = Result.failure(originalError)

        val transformed = result.mapError {
            TrustWeaveException.InvalidOperation(message = "Transformed: ${it.message}")
        }

        assertTrue(transformed.isFailure)
        val error = transformed.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error is TrustWeaveException.InvalidOperation)
        assertEquals("Transformed: Original error", error.message)
    }

    @Test
    fun `test mapError preserves success`() {
        val result: Result<String> = Result.success("success")

        val transformed = result.mapError {
            TrustWeaveException.Unknown(message = "Should not be called")
        }

        assertTrue(transformed.isSuccess)
        assertEquals("success", transformed.getOrNull())
    }

    @Test
    fun `test getOrThrowException throws TrustWeaveException on failure`() {
        val result: Result<String> = Result.failure(IllegalArgumentException("Test error"))

        val exception = assertFailsWith<TrustWeaveException> {
            result.getOrThrowException()
        }

        assertTrue(exception is TrustWeaveException.InvalidOperation)
        assertEquals("INVALID_ARGUMENT", exception.code)
    }

    @Test
    fun `test getOrThrowException returns value on success`() {
        val result: Result<String> = Result.success("value")

        val value = result.getOrThrowException()

        assertEquals("value", value)
    }

    @Test
    fun `test getOrElse returns default on failure`() {
        val result: Result<String> = Result.failure(IllegalArgumentException("Error"))

        // Uses standard library getOrElse
        val value = result.getOrElse { "default" }

        assertEquals("default", value)
    }

    @Test
    fun `test getOrElse returns value on success`() {
        val result: Result<String> = Result.success("value")

        // Uses standard library getOrElse
        val value = result.getOrElse { "default" }

        assertEquals("value", value)
    }

    @Test
    fun `test combine succeeds when all results succeed`() {
        val results = listOf(
            Result.success(1),
            Result.success(2),
            Result.success(3)
        )

        val combined = results.combine { values ->
            values.sum()
        }

        assertTrue(combined.isSuccess)
        assertEquals(6, combined.getOrNull())
    }

    @Test
    fun `test combine fails when any result fails`() {
        val results = listOf(
            Result.success(1),
            Result.failure(IllegalArgumentException("Error")),
            Result.success(3)
        )

        val combined = results.combine { values ->
            values.sum()
        }

        assertTrue(combined.isFailure)
        assertNotNull(combined.exceptionOrNull())
    }

    @Test
    fun `test combine with empty list`() {
        val results = emptyList<Result<Int>>()

        val combined = results.combine { values ->
            values.sum()
        }

        assertTrue(combined.isSuccess)
        assertEquals(0, combined.getOrNull())
    }

    @Test
    fun `test mapSequential succeeds when all operations succeed`() = runBlocking {
        val items = listOf(1, 2, 3)

        val result = items.mapSequential { item ->
            Result.success(item * 2)
        }

        assertTrue(result.isSuccess)
        assertEquals(listOf(2, 4, 6), result.getOrNull())
    }

    @Test
    fun `test mapSequential fails when any operation fails`() = runBlocking {
        val items = listOf(1, 2, 3)

        val result = items.mapSequential { item ->
            if (item == 2) {
                Result.failure(IllegalArgumentException("Error at $item"))
            } else {
                Result.success(item * 2)
            }
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun `test mapSequential with empty list`() = runBlocking {
        val items = emptyList<Int>()

        val result = items.mapSequential { item ->
            Result.success(item * 2)
        }

        assertTrue(result.isSuccess)
        assertEquals(emptyList<Int>(), result.getOrNull())
    }

    // onSuccess / onFailure resolve to the Kotlin stdlib implementations.
    // The local redefinitions in ResultExtensions.kt were removed: they shadowed the
    // stdlib and skipped null success values (`getOrNull()?.let(action)`).

    @Test
    fun `test onSuccess executes action on success`() {
        var executed = false
        val result: Result<String> = Result.success("value")

        val returned = result.onSuccess {
            executed = true
            assertEquals("value", it)
        }

        assertTrue(executed)
        // Result.onSuccess returns the same Result instance for chaining
        assertEquals(result.isSuccess, returned.isSuccess)
        assertEquals(result.getOrNull(), returned.getOrNull())
    }

    @Test
    fun `test onSuccess executes action for null success value`() {
        // Regression: the removed local extension used `getOrNull()?.let(action)`,
        // which silently skipped a successful Result holding null.
        var executed = false
        val result: Result<String?> = Result.success(null)

        result.onSuccess {
            executed = true
            assertNull(it)
        }

        assertTrue(executed)
    }

    @Test
    fun `test onSuccess does not execute action on failure`() {
        var executed = false
        val result: Result<String> = Result.failure(IllegalArgumentException("Error"))

        result.onSuccess {
            executed = true
        }

        assertFalse(executed)
    }

    @Test
    fun `test onFailure executes action on failure`() {
        var executed = false
        val error = IllegalArgumentException("Error")
        val result: Result<String> = Result.failure(error)

        val returned = result.onFailure {
            executed = true
            assertEquals(error, it)
        }

        assertTrue(executed)
        // Result.onFailure returns the same Result instance for chaining
        assertEquals(result.isFailure, returned.isFailure)
        assertEquals(result.exceptionOrNull(), returned.exceptionOrNull())
    }

    @Test
    fun `test onFailure does not execute action on success`() {
        var executed = false
        val result: Result<String> = Result.success("value")

        result.onFailure {
            executed = true
        }

        assertFalse(executed)
    }

    @Test
    fun `test trustweaveCatching converts exceptions to TrustWeaveException`() = runBlocking {
        val result = trustweaveCatching {
            throw IllegalArgumentException("Test error")
        }

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error is TrustWeaveException.InvalidOperation)
        assertEquals("INVALID_ARGUMENT", error.code)
    }

    @Test
    fun `test trustweaveCatching returns success on no exception`() = runBlocking {
        val result = trustweaveCatching {
            "success"
        }

        assertTrue(result.isSuccess)
        assertEquals("success", result.getOrNull())
    }

    @Test
    fun `test trustweaveCatching preserves TrustWeaveException`() = runBlocking {
        val originalError = TrustWeaveException.ValidationFailed(
            field = "test",
            reason = "Invalid",
            value = null
        )

        val result = trustweaveCatching {
            throw originalError
        }

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertSame(originalError, error)
    }

    @Test
    fun `test trustweaveCatching with suspend function`() = runBlocking {
        suspend fun suspendOperation(): String {
            kotlinx.coroutines.delay(10)
            return "result"
        }

        val result = trustweaveCatching {
            suspendOperation()
        }

        assertTrue(result.isSuccess)
        assertEquals("result", result.getOrNull())
    }

    @Test
    fun `test mapError when transform throws`() {
        val originalError = IllegalArgumentException("Original error")
        val result: Result<String> = Result.failure(originalError)

        // When transform throws, the exception propagates (not wrapped in Result)
        val exception = assertFailsWith<IllegalStateException> {
            result.mapError {
                throw IllegalStateException("Transform failed")
            }
        }

        assertEquals("Transform failed", exception.message)
    }

    @Test
    fun `test combine with single element list`() {
        val results = listOf(Result.success(42))

        val combined = results.combine { values ->
            values.first() * 2
        }

        assertTrue(combined.isSuccess)
        assertEquals(84, combined.getOrNull())
    }

    @Test
    fun `test combine with single failing element`() {
        val results = listOf(Result.failure<Int>(IllegalArgumentException("Error")))

        val combined = results.combine { values ->
            values.sum()
        }

        assertTrue(combined.isFailure)
        assertNotNull(combined.exceptionOrNull())
    }

    // ===== Cancellation guards: helpers must never capture CancellationException =====

    @Test
    fun `trustweaveCatching rethrows CancellationException instead of capturing it`() = runBlocking {
        assertFailsWith<CancellationException> {
            trustweaveCatching<String> {
                throw CancellationException("cancelled")
            }
        }
        Unit
    }

    @Test
    fun `trustweaveCatching rethrows timeout from enclosing withTimeout`() = runBlocking {
        // An enclosing withTimeout cancels the body with TimeoutCancellationException;
        // capturing it in a Result would break the timeout. It must propagate.
        assertFailsWith<TimeoutCancellationException> {
            withTimeout(50.milliseconds) {
                trustweaveCatching {
                    delay(10_000)
                    "unreachable"
                }
            }
        }
        Unit
    }

    @Test
    fun `trustweaveCatching does not capture cancellation of the calling coroutine`() = runBlocking {
        var capturedCancellation = false
        val job = launch {
            val result = trustweaveCatching { delay(10_000) }
            // Unreachable when cancellation propagates correctly; reaching it means the
            // CancellationException was captured into the Result.
            capturedCancellation = result.isFailure
        }
        yield() // let the job suspend inside delay
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertFalse(capturedCancellation, "trustweaveCatching captured cancellation into a Result")
    }

    @Test
    fun `mapSequential rethrows CancellationException from transform`() = runBlocking {
        assertFailsWith<CancellationException> {
            listOf(1, 2, 3).mapSequential<Int, Int> {
                throw CancellationException("cancelled")
            }
        }
        Unit
    }

    @Test
    fun `mapSequential does not capture cancellation of the calling coroutine`() = runBlocking {
        var capturedCancellation = false
        val job = launch {
            val result = listOf(1, 2, 3).mapSequential {
                delay(10_000)
                Result.success(it)
            }
            capturedCancellation = result.isFailure
        }
        yield()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertFalse(capturedCancellation, "mapSequential captured cancellation into a Result")
    }

    @Test
    fun `combine rethrows CancellationException held in a failed Result`() {
        val results = listOf(Result.failure<Int>(CancellationException("cancelled")))

        assertFailsWith<CancellationException> {
            results.combine { it.sum() }
        }
    }

    @Test
    fun `getOrThrowException rethrows CancellationException without conversion`() {
        val cancellation = CancellationException("cancelled")
        val result: Result<String> = Result.failure(cancellation)

        val thrown = assertFailsWith<CancellationException> {
            result.getOrThrowException()
        }
        assertSame(cancellation, thrown)
    }
}

