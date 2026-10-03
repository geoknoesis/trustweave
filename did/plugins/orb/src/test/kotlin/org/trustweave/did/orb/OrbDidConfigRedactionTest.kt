package org.trustweave.did.orb

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrbDidConfigRedactionTest {
    @Test
    fun `toString redacts the auth header value`() {
        val text = OrbDidConfig(baseUrl = "https://orb.example", authHeader = "Authorization" to "Bearer TOP-SECRET").toString()
        assertFalse("TOP-SECRET" in text, text)
        assertTrue("Authorization=<redacted>" in text, text)
    }
}
