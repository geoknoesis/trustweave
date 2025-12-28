package org.trustweave.credential.oidc4vp.exception

import kotlin.test.*

/**
 * Tests for OIDC4VP exceptions.
 */
class Oidc4VpExceptionTest {

    @Test
    fun `test HttpRequestFailed exception`() {
        val exception = Oidc4VpException.HttpRequestFailed(
            url = "https://example.com/api",
            statusCode = 404,
            reason = "Not found",
            cause = null
        )
        
        assertEquals("OIDC4VP_HTTP_REQUEST_FAILED", exception.code)
        assertTrue(exception.message.contains("HTTP request failed"))
        assertTrue(exception.message.contains("404"))
        assertEquals("https://example.com/api", exception.context["url"])
        assertEquals(404, exception.context["statusCode"])
    }

    @Test
    fun `test AuthorizationRequestFetchFailed exception`() {
        val exception = Oidc4VpException.AuthorizationRequestFetchFailed(
            requestUri = "https://verifier.example.com/request/123",
            reason = "Network error",
            cause = null
        )
        
        assertEquals("OIDC4VP_AUTHORIZATION_REQUEST_FETCH_FAILED", exception.code)
        assertTrue(exception.message.contains("Failed to fetch"))
        assertTrue(exception.message.contains("request/123"))
        assertEquals("Network error", exception.context["reason"])
    }

    @Test
    fun `test MetadataFetchFailed exception`() {
        val exception = Oidc4VpException.MetadataFetchFailed(
            verifierUrl = "https://verifier.example.com",
            reason = "Connection timeout"
        )
        
        assertEquals("OIDC4VP_METADATA_FETCH_FAILED", exception.code)
        assertTrue(exception.message.contains("Failed to fetch"))
        assertEquals("Connection timeout", exception.context["reason"])
    }

    @Test
    fun `test PresentationSubmissionFailed exception`() {
        val exception = Oidc4VpException.PresentationSubmissionFailed(
            reason = "Invalid VP token",
            verifierUrl = "https://verifier.example.com"
        )
        
        assertEquals("OIDC4VP_PRESENTATION_SUBMISSION_FAILED", exception.code)
        assertTrue(exception.message.contains("presentation submission failed"))
        assertEquals("Invalid VP token", exception.context["reason"])
    }

    @Test
    fun `test UrlParseFailed exception`() {
        val exception = Oidc4VpException.UrlParseFailed(
            url = "invalid-url",
            reason = "Missing scheme"
        )
        
        assertEquals("OIDC4VP_URL_PARSE_FAILED", exception.code)
        assertTrue(exception.message.contains("Failed to parse"))
        assertEquals("invalid-url", exception.context["url"])
        assertEquals("Missing scheme", exception.context["reason"])
    }

    @Test
    fun `test exceptions are TrustWeaveException instances`() {
        val exceptions = listOf(
            Oidc4VpException.HttpRequestFailed("https://example.com", null, "Error"),
            Oidc4VpException.AuthorizationRequestFetchFailed("https://example.com", "Error"),
            Oidc4VpException.MetadataFetchFailed("https://example.com", "Error"),
            Oidc4VpException.PresentationSubmissionFailed("Error"),
            Oidc4VpException.UrlParseFailed("url", "Error")
        )
        
        exceptions.forEach { exception ->
            assertTrue(exception is org.trustweave.core.exception.TrustWeaveException)
        }
    }
}

