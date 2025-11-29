package com.trustweave.core.util

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for Validation infrastructure.
 */
class ValidationTest {

    @Test
    fun `test ValidationResult Valid`() {
        val result = ValidationResult.Valid

        assertTrue(result.isValid())
        assertNull(result.errorMessage())
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
}

