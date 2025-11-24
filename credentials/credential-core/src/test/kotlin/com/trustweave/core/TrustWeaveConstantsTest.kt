package com.trustweave.core

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for TrustWeaveConstants.
 */
class TrustWeaveConstantsTest {

    @Test
    fun `test DEFAULT_JSON_MEDIA_TYPE constant`() {
        assertEquals("application/json", TrustWeaveConstants.DEFAULT_JSON_MEDIA_TYPE)
    }
}



