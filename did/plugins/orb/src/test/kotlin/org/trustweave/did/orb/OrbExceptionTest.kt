package org.trustweave.did.orb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrbExceptionTest {
    @Test
    fun `http error message keeps status only and never the upstream body`() {
        val e = OrbException.httpError(500, "SECRET-TOKEN-abc internal path /etc/orb")
        assertEquals("Orb node returned HTTP 500", e.message)
        assertEquals(500, e.httpStatus)
        assertFalse(e.toString().contains("SECRET-TOKEN"))
    }

    @Test
    fun `a transport failure with no HTTP status does not leak the detail either`() {
        val e = OrbException.httpError(-1, "Network error: connect to https://user:pw@host failed")
        assertFalse(e.message.contains("user:pw"))
        assertTrue(e.message.contains("no HTTP response"))
    }

    @Test
    fun `logged body is capped and stripped of control characters`() {
        val sanitized = sanitizeUpstreamBody("a\r\nforged\u0000" + "x".repeat(2000))
        assertFalse(sanitized.any { it.isISOControl() })
        assertTrue(sanitized.length < 600)
    }
}
