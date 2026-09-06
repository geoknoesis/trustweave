package org.trustweave.core.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class TransportSecurityTest {
    @Test fun `public plaintext and embedded credentials are rejected`() {
        assertFailsWith<IllegalArgumentException> { TransportSecurity.requireSecureForPublicHosts("http://8.8.8.8", "test") }
        val error =
            assertFailsWith<IllegalArgumentException> {
                TransportSecurity.requireSecureForPublicHosts("https://user:private-value@example.com", "test")
            }
        assertFalse(error.message.orEmpty().contains("private-value"))
    }

    @Test fun `secure endpoints and explicit loopback are accepted`() {
        for (url in listOf("https://service_name.example", "http://127.0.0.1:8200", "http://[::1]:8200")) {
            assertEquals(url, TransportSecurity.requireSecureForPublicHosts(url, "test"))
        }
    }

    @Test fun `non HTTP and malformed endpoints fail closed`() {
        for (url in listOf("file:///secret", "ftp://example.com", "https:///missing", "garbage")) {
            assertFailsWith<IllegalArgumentException> { TransportSecurity.requireSecureForPublicHosts(url, "test") }
        }
    }
}
