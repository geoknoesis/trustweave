package org.trustweave.webdid

import org.trustweave.testkit.kms.InMemoryKeyManagementService
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

/**
 * Tests for the W3C did:web DID-to-URL transformation:
 * - bare domain → /.well-known/did.json
 * - path-based → {path}/did.json (well-known is ONLY for bare domains)
 * - percent-encoded ports in the host segment
 */
class WebDidMethodUrlTest {

    private val method = WebDidMethod(InMemoryKeyManagementService(), OkHttpClient())

    @Test
    fun `bare domain resolves to well-known location`() {
        assertEquals(
            "https://example.com/.well-known/did.json",
            method.getDocumentUrl("did:web:example.com")
        )
    }

    @Test
    fun `path-based did uses path plus did json without well-known`() {
        assertEquals(
            "https://example.com/user/alice/did.json",
            method.getDocumentUrl("did:web:example.com:user:alice")
        )
    }

    @Test
    fun `single path segment`() {
        assertEquals(
            "https://w3c-ccg.github.io/user/did.json",
            method.getDocumentUrl("did:web:w3c-ccg.github.io:user")
        )
    }

    @Test
    fun `percent-encoded port on bare domain`() {
        assertEquals(
            "https://example.com:8080/.well-known/did.json",
            method.getDocumentUrl("did:web:example.com%3A8080")
        )
    }

    @Test
    fun `percent-encoded port with path`() {
        assertEquals(
            "https://example.com:8080/user/did.json",
            method.getDocumentUrl("did:web:example.com%3A8080:user")
        )
    }

    @Test
    fun `non did-web identifier is rejected`() {
        assertThrows<IllegalArgumentException> {
            method.getDocumentUrl("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        }
    }

    // ── Security: percent-decoding must not introduce URL metacharacters ──

    @Test
    fun `percent-encoded userinfo at-sign in host is rejected`() {
        // Would otherwise build https://foo@evil.com/... — actual fetch host is evil.com
        assertThrows<IllegalArgumentException> {
            method.getDocumentUrl("did:web:foo%40evil.com")
        }
    }

    @Test
    fun `percent-encoded slash in path segment is rejected`() {
        // Would otherwise inject extra path levels: https://example.com/path/../etc/did.json
        assertThrows<IllegalArgumentException> {
            method.getDocumentUrl("did:web:example.com:path%2F..%2Fetc")
        }
    }

    @Test
    fun `percent-encoded slash in host is rejected`() {
        assertThrows<IllegalArgumentException> {
            method.getDocumentUrl("did:web:evil.com%2Fexample.com")
        }
    }

    @Test
    fun `percent-encoded question mark in path segment is rejected`() {
        assertThrows<IllegalArgumentException> {
            method.getDocumentUrl("did:web:example.com:user%3Fadmin=true")
        }
    }

    @Test
    fun `percent-encoded hash in path segment is rejected`() {
        assertThrows<IllegalArgumentException> {
            method.getDocumentUrl("did:web:example.com:user%23frag")
        }
    }

    @Test
    fun `percent-encoded whitespace in host is rejected`() {
        assertThrows<IllegalArgumentException> {
            method.getDocumentUrl("did:web:exa%20mple.com")
        }
    }

    @Test
    fun `percent-encoded control character in path segment is rejected`() {
        assertThrows<IllegalArgumentException> {
            method.getDocumentUrl("did:web:example.com:user%0Aalice")
        }
    }

    @Test
    fun `literal at-sign in path segment is allowed`() {
        assertEquals(
            "https://example.com/user@org/did.json",
            method.getDocumentUrl("did:web:example.com:user%40org")
        )
    }

    // ── Empty method-specific-id segments ──

    @Test
    fun `trailing empty segment is rejected`() {
        assertThrows<IllegalArgumentException> {
            method.getDocumentUrl("did:web:example.com:")
        }
    }

    @Test
    fun `embedded empty segment is rejected`() {
        assertThrows<IllegalArgumentException> {
            method.getDocumentUrl("did:web:example.com::user")
        }
    }
}
