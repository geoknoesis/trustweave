package org.trustweave.kms.cyberark

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CyberArkKmsConfigRedactionTest {
    @Test
    fun `toString never contains credentials`() {
        val text =
            CyberArkKmsConfig(
                conjurUrl = "https://conjur.example.com",
                account = "acct",
                apiKey = "SECRET-API-KEY",
                username = "svc",
                password = "SECRET-PASSWORD",
            ).toString()

        assertFalse("SECRET-API-KEY" in text, text)
        assertFalse("SECRET-PASSWORD" in text, text)
        assertTrue("<redacted>" in text, text)
        assertTrue("conjur.example.com" in text, text)
        assertTrue("svc" in text, text)
    }
}
