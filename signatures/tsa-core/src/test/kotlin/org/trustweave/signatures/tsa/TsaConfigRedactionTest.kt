package org.trustweave.signatures.tsa

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TsaConfigRedactionTest {
    @Test
    fun `TsaConfig toString redacts username and password`() {
        val cfg =
            TsaConfig(
                endpointUrl = "https://tsa.example.com",
                username = "tsa-admin",
                password = "tsa-super-secret-password",
            )
        val rendered = cfg.toString()
        assertFalse(rendered.contains("tsa-admin"))
        assertFalse(rendered.contains("tsa-super-secret-password"))
        assertTrue(rendered.contains("[REDACTED]"))
    }
}
