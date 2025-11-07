package io.geoknoesis.vericore.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class VeriCoreExceptionTest {

    @Test
    fun `VeriCoreException should work`() {
        val exception = VeriCoreException("Test error")
        assertEquals("Test error", exception.message)
    }

    @Test
    fun `NotFoundException should work`() {
        val exception = NotFoundException("Resource not found")
        assertEquals("Resource not found", exception.message)
    }

    @Test
    fun `InvalidOperationException should work`() {
        val exception = InvalidOperationException("Invalid operation")
        assertEquals("Invalid operation", exception.message)
    }
}

