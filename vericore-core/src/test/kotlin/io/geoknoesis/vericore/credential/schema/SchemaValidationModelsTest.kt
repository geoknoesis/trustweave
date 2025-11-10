package io.geoknoesis.vericore.credential.schema

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.spi.SchemaFormat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for schema validation models (SchemaValidationResult, SchemaValidationError, SchemaRegistrationResult).
 */
class SchemaValidationModelsTest {

    @Test
    fun `test SchemaValidationResult with all fields`() {
        val errors = listOf(
            SchemaValidationError(
                path = "$.credentialSubject.name",
                message = "Name is required",
                code = "REQUIRED_FIELD"
            ),
            SchemaValidationError(
                path = "$.credentialSubject.email",
                message = "Invalid email format",
                code = "INVALID_FORMAT"
            )
        )
        
        val warnings = listOf("Field 'age' is deprecated")
        
        val result = SchemaValidationResult(
            valid = false,
            errors = errors,
            warnings = warnings
        )
        
        assertFalse(result.valid)
        assertEquals(2, result.errors.size)
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun `test SchemaValidationResult with defaults`() {
        val result = SchemaValidationResult(valid = true)
        
        assertTrue(result.valid)
        assertTrue(result.errors.isEmpty())
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `test SchemaValidationError with all fields`() {
        val error = SchemaValidationError(
            path = "$.credentialSubject.name",
            message = "Name is required",
            code = "REQUIRED_FIELD"
        )
        
        assertEquals("$.credentialSubject.name", error.path)
        assertEquals("Name is required", error.message)
        assertEquals("REQUIRED_FIELD", error.code)
    }

    @Test
    fun `test SchemaValidationError with defaults`() {
        val error = SchemaValidationError(
            path = "$.credentialSubject.name",
            message = "Name is required"
        )
        
        assertEquals("$.credentialSubject.name", error.path)
        assertEquals("Name is required", error.message)
        assertNull(error.code)
    }

    @Test
    fun `test SchemaRegistrationResult success`() {
        val result = SchemaRegistrationResult(
            success = true,
            schemaId = "https://example.com/schemas/person"
        )
        
        assertTrue(result.success)
        assertEquals("https://example.com/schemas/person", result.schemaId)
        assertNull(result.error)
    }

    @Test
    fun `test SchemaRegistrationResult failure`() {
        val result = SchemaRegistrationResult(
            success = false,
            error = "Schema validation failed"
        )
        
        assertFalse(result.success)
        assertNull(result.schemaId)
        assertEquals("Schema validation failed", result.error)
    }

    @Test
    fun `test SchemaRegistrationResult with defaults`() {
        val result = SchemaRegistrationResult(success = false)
        
        assertFalse(result.success)
        assertNull(result.schemaId)
        assertNull(result.error)
    }
}



