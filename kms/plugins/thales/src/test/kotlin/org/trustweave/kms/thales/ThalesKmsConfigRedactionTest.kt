package org.trustweave.kms.thales

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThalesKmsConfigRedactionTest {
    @Test
    fun `toString never contains credentials`() {
        val text =
            ThalesKmsConfig(
                baseUrl = "https://ciphertrust.example.com",
                apiKey = "SECRET-API-KEY",
                username = "svc",
                password = "SECRET-PASSWORD",
                clientId = "client",
                clientSecret = "SECRET-CLIENT",
                accessToken = "SECRET-TOKEN",
            ).toString()

        assertFalse("SECRET-API-KEY" in text, text)
        assertFalse("SECRET-PASSWORD" in text, text)
        assertFalse("SECRET-CLIENT" in text, text)
        assertFalse("SECRET-TOKEN" in text, text)
        assertTrue("<redacted>" in text, text)
        assertTrue("ciphertrust.example.com" in text, text)
        assertTrue("client" in text, text)
    }
}
