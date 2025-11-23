package com.trustweave.core

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for TrustWeaveException classes.
 */
class TrustWeaveExceptionTest {

    @Test
    fun `test TrustWeaveException with message`() {
        val exception = TrustWeaveException("Test error")
        
        assertEquals("Test error", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun `test TrustWeaveException with message and cause`() {
        val cause = RuntimeException("Underlying error")
        val exception = TrustWeaveException("Test error", cause)
        
        assertEquals("Test error", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `test NotFoundException`() {
        val exception = NotFoundException("Resource not found")
        
        assertEquals(NotFoundException::class, exception::class)
        assertEquals("Resource not found", exception.message)
    }

    @Test
    fun `test NotFoundException with cause`() {
        val cause = RuntimeException("Underlying error")
        val exception = NotFoundException("Resource not found", cause)
        
        assertEquals("Resource not found", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `test InvalidOperationException`() {
        val exception = InvalidOperationException("Invalid operation")
        
        assertEquals(InvalidOperationException::class, exception::class)
        assertEquals("Invalid operation", exception.message)
    }

    @Test
    fun `test InvalidOperationException with cause`() {
        val cause = IllegalArgumentException("Invalid argument")
        val exception = InvalidOperationException("Invalid operation", cause)
        
        assertEquals("Invalid operation", exception.message)
        assertEquals(cause, exception.cause)
    }
}
