package org.trustweave.did.registrar.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegistrarOptionsRedactionTest {
    @Test
    fun `UpdateDidOptions and DeactivateDidOptions redact secret in toString`() {
        val secret = Secret(recoveryKey = "rec-key-secret", methodSpecificSecrets = mapOf("apiKey" to "super-secret-value"))
        val update = UpdateDidOptions(secret = secret)
        val deactivate = DeactivateDidOptions(secret = secret)

        assertFalse(update.toString().contains("super-secret-value"))
        assertTrue(update.toString().contains("[REDACTED]"))
        assertFalse(deactivate.toString().contains("super-secret-value"))
        assertTrue(deactivate.toString().contains("[REDACTED]"))
    }
}
