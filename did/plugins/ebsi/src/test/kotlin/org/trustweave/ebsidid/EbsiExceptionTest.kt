package org.trustweave.ebsidid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EbsiExceptionTest {
    @Test
    fun `http error message keeps status only and never the upstream body`() {
        val e = EbsiException.httpError(502, "SECRET-TOKEN-abc <html>stack trace</html>")
        assertEquals("EBSI API returned HTTP 502", e.message)
        assertEquals(502, e.httpStatus)
        assertEquals("EBSI_HTTP_ERROR", e.code)
        assertFalse(e.toString().contains("SECRET-TOKEN"))
    }

    @Test
    fun `logged body is capped and stripped of control characters`() {
        val sanitized = sanitizeUpstreamBody("a\r\nforged line\u0000" + "x".repeat(2000))
        assertFalse(sanitized.any { it.isISOControl() })
        assertTrue(sanitized.length < 600)
        assertTrue(sanitized.contains("truncated"))
        assertEquals("short", sanitizeUpstreamBody("short"))
    }
}
