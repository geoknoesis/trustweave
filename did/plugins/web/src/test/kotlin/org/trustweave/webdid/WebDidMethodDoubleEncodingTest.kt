package org.trustweave.webdid

import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * A segment is percent-decoded exactly once. Anything that is still percent-encoded afterwards
 * (a double encoding such as `%252e%252e`) would reach the URL as a literal `%2e%2e`, which many
 * servers and proxies decode a second time into `..`, so it must be refused.
 */
class WebDidMethodDoubleEncodingTest {
    private val method = WebDidMethod(InMemoryKeyManagementService(), OkHttpClient())

    @Test
    fun `double-encoded dot-dot path segments are rejected`() {
        for (did in listOf("did:web:example.com:%252e%252e:admin", "did:web:example.com:%252E%252E:admin")) {
            assertThrows<IllegalArgumentException>(did) { method.getDocumentUrl(did) }
        }
    }

    @Test
    fun `double-encoded slash is rejected`() {
        for (did in listOf("did:web:example.com:a%252Fb", "did:web:example.com:a%252fb", "did:web:example.com:a%255Cb")) {
            assertThrows<IllegalArgumentException>(did) { method.getDocumentUrl(did) }
        }
    }

    @Test
    fun `single-encoded dot-dot is still rejected`() {
        assertThrows<IllegalArgumentException> { method.getDocumentUrl("did:web:example.com:%2e%2e:admin") }
    }

    @Test
    fun `a double-encoded host is rejected`() {
        assertThrows<IllegalArgumentException> { method.getDocumentUrl("did:web:example.com%252F..:user") }
    }

    @Test
    fun `a percent-encoded port and ordinary paths still resolve and the url never leaves the path`() {
        assertEquals("https://example.com:8080/user/alice/did.json", method.getDocumentUrl("did:web:example.com%3A8080:user:alice"))
        val url = method.getDocumentUrl("did:web:example.com:user:alice")
        assertFalse(url.contains("%"), url)
        assertFalse(url.contains(".."), url)
    }
}
