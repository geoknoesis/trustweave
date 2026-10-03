package org.trustweave.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValidationResultTest {
    private val invalid = ValidationResult.Invalid(code = "BAD", message = "bad value", field = "f", value = 7)
    private val otherInvalid = ValidationResult.Invalid(code = "WORSE", message = "worse", field = "g", value = null)

    @Test
    fun validReportsNoErrors() {
        assertTrue(ValidationResult.Valid.isValid())
        assertNull(ValidationResult.Valid.errorMessage())
        assertNull(ValidationResult.Valid.errorCode())
    }

    @Test
    fun invalidReportsCodeAndMessage() {
        assertFalse(invalid.isValid())
        assertEquals("bad value", invalid.errorMessage())
        assertEquals("BAD", invalid.errorCode())
    }

    @Test
    fun toResultCarriesValueOrFieldInTheFailure() {
        assertEquals(42, ValidationResult.Valid.toResult(42).getOrThrow())
        val failure = invalid.toResult(42).exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure.message!!.contains("'f'") && failure.message!!.contains("bad value"))
    }

    @Test
    fun combineReturnsTheFirstInvalidResult() {
        assertEquals(ValidationResult.Valid, ValidationResult.combine(ValidationResult.Valid, ValidationResult.Valid))
        assertEquals(invalid, ValidationResult.combine(ValidationResult.Valid, invalid, otherInvalid))
        assertEquals(otherInvalid, ValidationResult.combine(listOf(otherInvalid, invalid)))
        assertEquals(ValidationResult.Valid, ValidationResult.combine(emptyList()))
        assertEquals(ValidationResult.Valid, ValidationResult.combine())
    }
}
