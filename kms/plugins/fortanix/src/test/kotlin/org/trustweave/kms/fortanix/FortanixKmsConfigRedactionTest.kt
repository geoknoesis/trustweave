package org.trustweave.kms.fortanix

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FortanixKmsConfigRedactionTest {
    @Test
    fun `toString never contains credentials`() {
        val text = FortanixKmsConfig(apiEndpoint = "https://dsm.example.com", apiKey = "SECRET-API-KEY").toString()

        assertFalse("SECRET-API-KEY" in text, text)
        assertTrue("<redacted>" in text, text)
        assertTrue("dsm.example.com" in text, text)
    }
}
