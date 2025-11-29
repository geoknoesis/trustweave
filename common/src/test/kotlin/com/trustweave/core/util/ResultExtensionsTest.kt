package com.trustweave.core.util

import com.trustweave.core.exception.TrustWeaveException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

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

        val value = result.getOrElse { "default" }

        assertEquals("default", value)
    }

    @Test
    fun `test getOrElse returns value on success`() {
        val result: Result<String> = Result.success("value")

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
}

