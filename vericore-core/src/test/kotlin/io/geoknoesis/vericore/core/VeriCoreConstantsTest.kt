package io.geoknoesis.vericore.core

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for VeriCoreConstants.
 */
class VeriCoreConstantsTest {

    @Test
    fun `test DEFAULT_JSON_MEDIA_TYPE constant`() {
        assertEquals("application/json", VeriCoreConstants.DEFAULT_JSON_MEDIA_TYPE)
    }
}



