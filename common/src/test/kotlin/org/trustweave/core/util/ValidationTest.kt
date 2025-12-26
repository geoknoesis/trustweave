package org.trustweave.core.util

import org.trustweave.core.exception.TrustWeaveException
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for Validation infrastructure.
 */
class ValidationTest {

    @Test
    fun `test ValidationResult Valid`() {
        val result = ValidationResult.Valid

        assertTrue(result.isValid())
        assertNull(result.errorMessage())
        assertNull(result.errorCode())
    }

    @Test
    fun `test ValidationResult Invalid`() {
        val result = ValidationResult.Invalid(
            code = "TEST_ERROR",
            message = "Test validation failed",
            field = "testField",
            value = "testValue"
        )

        assertFalse(result.isValid())
        assertEquals("Test validation failed", result.errorMessage())
        assertEquals("TEST_ERROR", result.errorCode())
        assertEquals("TEST_ERROR", result.code)
        assertEquals("testField", result.field)
        assertEquals("testValue", result.value)
    }

    @Test
    fun `test ValidationResult Invalid with null value`() {
        val result = ValidationResult.Invalid(
            code = "TEST_ERROR",
            message = "Test validation failed",
            field = "testField",
            value = null
        )

        assertFalse(result.isValid())
        assertNull(result.value)
    }

    @Test
    fun `test isValid returns true for Valid`() {
        val result = ValidationResult.Valid

        assertTrue(result.isValid())
    }

    @Test
    fun `test isValid returns false for Invalid`() {
        val result = ValidationResult.Invalid(
            code = "ERROR",
            message = "Error",
            field = "field",
            value = null
        )

        assertFalse(result.isValid())
    }

    @Test
    fun `test errorMessage returns null for Valid`() {
        val result = ValidationResult.Valid

        assertNull(result.errorMessage())
    }

    @Test
    fun `test errorMessage returns message for Invalid`() {
        val result = ValidationResult.Invalid(
            code = "ERROR",
            message = "Test error message",
            field = "field",
            value = null
        )

        assertEquals("Test error message", result.errorMessage())
    }

    @Test
    fun `test errorCode returns null for Valid`() {
        val result = ValidationResult.Valid

        assertNull(result.errorCode())
    }

    @Test
    fun `test errorCode returns code for Invalid`() {
        val result = ValidationResult.Invalid(
            code = "ERROR_CODE",
            message = "Error message",
            field = "field",
            value = null
        )

        assertEquals("ERROR_CODE", result.errorCode())
    }

    @Test
    fun `test toResult returns success for Valid`() {
        val result = ValidationResult.Valid
        val value = "test value"

        val converted = result.toResult(value)

        assertTrue(converted.isSuccess)
        assertEquals(value, converted.getOrNull())
    }

    @Test
    fun `test toResult returns failure for Invalid`() {
        val result = ValidationResult.Invalid(
            code = "ERROR",
            message = "Validation failed",
            field = "field",
            value = "invalid"
        )

        val converted = result.toResult("test")

        assertTrue(converted.isFailure)
        val exception = converted.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception.message?.contains("Validation failed") == true)
    }

    @Test
    fun `test toException returns null for Valid`() {
        val result = ValidationResult.Valid

        assertNull(result.toException())
    }

    @Test
    fun `test toException returns ValidationFailed for Invalid`() {
        val result = ValidationResult.Invalid(
            code = "ERROR",
            message = "Validation failed",
            field = "email",
            value = "invalid@"
        )

        val exception = result.toException()

        assertNotNull(exception)
        assertEquals("VALIDATION_FAILED", exception.code)
        assertEquals("email", exception.field)
        assertEquals("Validation failed", exception.reason)
        assertEquals("invalid@", exception.value)
    }

    @Test
    fun `test combine with all Valid results`() {
        val results = listOf(
            ValidationResult.Valid,
            ValidationResult.Valid,
            ValidationResult.Valid
        )

        val combined = ValidationResult.combine(results)

        assertTrue(combined.isValid())
    }

    @Test
    fun `test combine with one Invalid result`() {
        val invalid = ValidationResult.Invalid(
            code = "ERROR",
            message = "First error",
            field = "field1",
            value = null
        )
        val results = listOf(
            ValidationResult.Valid,
            invalid,
            ValidationResult.Valid
        )

        val combined = ValidationResult.combine(results)

        assertFalse(combined.isValid())
        assertEquals(invalid, combined)
    }

    @Test
    fun `test combine with multiple Invalid results returns first`() {
        val firstInvalid = ValidationResult.Invalid(
            code = "ERROR1",
            message = "First error",
            field = "field1",
            value = null
        )
        val secondInvalid = ValidationResult.Invalid(
            code = "ERROR2",
            message = "Second error",
            field = "field2",
            value = null
        )
        val results = listOf(
            ValidationResult.Valid,
            firstInvalid,
            secondInvalid
        )

        val combined = ValidationResult.combine(results)

        assertFalse(combined.isValid())
        assertEquals(firstInvalid, combined)
    }

    @Test
    fun `test combine with vararg`() {
        val invalid = ValidationResult.Invalid(
            code = "ERROR",
            message = "Error",
            field = "field",
            value = null
        )

        val combined = ValidationResult.combine(
            ValidationResult.Valid,
            invalid,
            ValidationResult.Valid
        )

        assertFalse(combined.isValid())
        assertEquals(invalid, combined)
    }

    @Test
    fun `test combine with empty list`() {
        val combined = ValidationResult.combine(emptyList())

        assertTrue(combined.isValid())
    }

    @Test
    fun `test combine with empty vararg`() {
        val combined = ValidationResult.combine()

        assertTrue(combined.isValid())
    }
}

