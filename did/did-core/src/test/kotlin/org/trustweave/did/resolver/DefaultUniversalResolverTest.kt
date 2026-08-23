package org.trustweave.did.resolver

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.did.exception.DidException
import org.trustweave.did.identifiers.Did
import java.net.InetSocketAddress
import kotlin.test.*

/**
 * Tests for DefaultUniversalResolver.
 *
 * Tests URL validation, error handling, and protocol adapter integration.
 */
class DefaultUniversalResolverTest {

    @Test
    fun `test URL validation for invalid baseUrl`() = runBlocking<Unit> {
        // The constructor validates the URL and throws IllegalArgumentException or DidException.InvalidDidFormat
        val exception = assertThrows<Throwable> {
            DefaultUniversalResolver(
                baseUrl = "not-a-valid-url",
                timeout = 5
            )
        }

        // Should be either IllegalArgumentException or DidException.InvalidDidFormat
        assertTrue(
            exception is IllegalArgumentException || exception is DidException.InvalidDidFormat,
            "Expected IllegalArgumentException or DidException.InvalidDidFormat, got ${exception::class.simpleName}"
        )
        if (exception is DidException.InvalidDidFormat) {
            assertTrue(exception.reason.contains("Invalid base URL format") || exception.reason.contains("base URL"))
        } else {
            assertTrue(exception.message?.contains("Invalid base URL format") == true || exception.message?.contains("base URL") == true)
        }
    }

    @Test
    fun `test URL validation for blank DID`() = runBlocking<Unit> {
        val resolver = DefaultUniversalResolver(
            baseUrl = "https://dev.uniresolver.io",
            timeout = 5
        )

        val exception = assertThrows<DidException.InvalidDidFormat> {
            resolver.resolveDid("")
        }

        assertTrue(exception.message?.contains("DID cannot be blank") == true)
    }

    @Test
    fun `test URL validation for DID not starting with did prefix`() = runBlocking<Unit> {
        val resolver = DefaultUniversalResolver(
            baseUrl = "https://dev.uniresolver.io",
            timeout = 5
        )

        val exception = assertThrows<DidException.InvalidDidFormat> {
            resolver.resolveDid("not-a-did")
        }

        assertTrue(exception.message?.contains("DID must start with 'did:'") == true)
    }

    @Test
    fun `test protocol adapter URL building`() {
        val adapter = StandardUniversalResolverAdapter()
        
        val url = adapter.buildResolveUrl("https://dev.uniresolver.io", "did:test:123")
        assertEquals("https://dev.uniresolver.io/1.0/identifiers/did%3Atest%3A123", url)
    }

    @Test
    fun `test protocol adapter methods URL building`() {
        val adapter = StandardUniversalResolverAdapter()
        
        val url = adapter.buildMethodsUrl("https://dev.uniresolver.io")
        assertEquals("https://dev.uniresolver.io/1.0/methods", url)
    }

    @Test
    fun `test protocol adapter extractDidDocument with didDocument key`() {
        val adapter = StandardUniversalResolverAdapter()
        val json = buildJsonObject {
            put("didDocument", buildJsonObject {
                put("id", "did:test:123")
            })
            put("didDocumentMetadata", buildJsonObject { })
        }

        val extracted = adapter.extractDidDocument(json)
        assertNotNull(extracted)
        assertEquals("did:test:123", extracted["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test protocol adapter extractDidDocument without didDocument key`() {
        val adapter = StandardUniversalResolverAdapter()
        val json = buildJsonObject {
            put("id", "did:test:123")
        }

        // When the "didDocument" key is absent, the adapter returns null so that
        // performResolution produces NotFound instead of treating the metadata
        // envelope as the DID document.
        val extracted = adapter.extractDidDocument(json)
        assertNull(extracted)
    }

    @Test
    fun `test protocol adapter extractDocumentMetadata`() {
        val adapter = StandardUniversalResolverAdapter()
        val json = buildJsonObject {
            put("didDocument", buildJsonObject {
                put("id", "did:test:123")
            })
            put("didDocumentMetadata", buildJsonObject {
                put("created", "2024-01-01T00:00:00Z")
            })
        }

        val metadata = adapter.extractDocumentMetadata(json)
        assertNotNull(metadata)
        assertEquals("2024-01-01T00:00:00Z", metadata["created"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test protocol adapter extractResolutionMetadata`() {
        val adapter = StandardUniversalResolverAdapter()
        val json = buildJsonObject {
            put("didResolutionMetadata", buildJsonObject {
                put("contentType", "application/did+ld+json")
            })
        }

        val metadata = adapter.extractResolutionMetadata(json)
        assertEquals("application/did+ld+json", metadata["contentType"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test protocol adapter extractResolutionMetadata with missing key`() {
        val adapter = StandardUniversalResolverAdapter()
        val json = buildJsonObject {
            put("didDocument", buildJsonObject {
                put("id", "did:test:123")
            })
        }

        val metadata = adapter.extractResolutionMetadata(json)
        assertTrue(metadata.isEmpty())
    }

    @Test
    fun `test protocol adapter configureAuth with API key`() {
        val adapter = StandardUniversalResolverAdapter()
        val requestBuilder = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create("https://example.com"))

        adapter.configureAuth(requestBuilder, "test-api-key")
        
        val request = requestBuilder.build()
        val authHeader = request.headers().firstValue("Authorization")
        assertTrue(authHeader.isPresent)
        assertEquals("Bearer test-api-key", authHeader.get())
    }

    @Test
    fun `test protocol adapter configureAuth without API key`() {
        val adapter = StandardUniversalResolverAdapter()
        val requestBuilder = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create("https://example.com"))

        adapter.configureAuth(requestBuilder, null)
        
        val request = requestBuilder.build()
        val authHeader = request.headers().firstValue("Authorization")
        assertFalse(authHeader.isPresent)
    }

    @Test
    fun `test getSupportedMethods returns null for invalid URL`() = runBlocking<Unit> {
        // Constructor will throw exception for invalid URL, so we can't test this scenario
        // Instead, test with a valid URL that might fail during the actual HTTP call
        // This test verifies that getSupportedMethods handles failures gracefully
        val resolver = DefaultUniversalResolver(
            baseUrl = "https://invalid-resolver-that-does-not-exist-12345.com",
            timeout = 1
        )

        // Should return null gracefully if the HTTP call fails
        val methods = resolver.getSupportedMethods()
        assertNull(methods)
    }

    @Test
    fun `test resolver with custom protocol adapter`() {
        val customAdapter = object : UniversalResolverProtocolAdapter {
            override fun buildResolveUrl(baseUrl: String, did: String): String {
                return "$baseUrl/custom/path/$did"
            }

            override fun buildMethodsUrl(baseUrl: String): String? {
                return "$baseUrl/custom/methods"
            }

            override fun configureAuth(requestBuilder: java.net.http.HttpRequest.Builder, apiKey: String?) {
                // No-op
            }

            override fun extractDidDocument(jsonResponse: JsonObject): JsonObject? {
                return jsonResponse["document"]?.jsonObject
            }

            override fun extractDocumentMetadata(jsonResponse: JsonObject): JsonObject? {
                return jsonResponse["metadata"]?.jsonObject
            }

            override fun extractResolutionMetadata(jsonResponse: JsonObject): JsonObject {
                return buildJsonObject { }
            }

            override val providerName: String = "custom-adapter"
        }

        val resolver = DefaultUniversalResolver(
            baseUrl = "https://example.com",
            protocolAdapter = customAdapter
        )

        assertEquals("custom-adapter", customAdapter.providerName)
        assertEquals("https://example.com/custom/path/did:test:123", 
            customAdapter.buildResolveUrl("https://example.com", "did:test:123"))
    }

    @Test
    fun `test resolver baseUrl property`() {
        val resolver = DefaultUniversalResolver("https://dev.uniresolver.io")
        assertEquals("https://dev.uniresolver.io", resolver.baseUrl)
    }

    // ─── Composed effect: a bare errorMessage on a failure response still surfaces as the
    // reason, even though DidResolutionMetadata.fromMap (fix round 2) deliberately does not
    // synthesize an error object for it (see fromMap's kdoc: it is shared and context-free, so it
    // must stay silent on a bare errorMessage to avoid mislabeling a would-be Success). This
    // exercises the actual resolveDid() HTTP path end-to-end, which round 1's tests never did —
    // that gap is why the round-1 regression slipped through.
    //
    // What changed since: the caller here — the NotFound branch below — is already committed to
    // a failure once document == null, so it now synthesizes a NOT_FOUND error object itself
    // (finding I3: every Failure carries a non-null resolutionMetadata.error, enforced by an init
    // check on DidResolutionResult.Failure since this round). fromMap's own no-synthesis
    // behaviour is unchanged; only what the caller does with its output changed. ───

    @Test
    fun `resolveDid surfaces a bare errorMessage as the NotFound reason`() = runBlocking<Unit> {
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        try {
            val body = """{"didResolutionMetadata":{"errorMessage":"DID did:x:y does not exist"}}"""
                .toByteArray(Charsets.UTF_8)
            server.createContext("/1.0/identifiers/") { exchange ->
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.start()
            val baseUrl = "http://localhost:${server.address.port}"
            val resolver = DefaultUniversalResolver(baseUrl = baseUrl, timeout = 5)

            val result = resolver.resolveDid("did:example:missing")

            assertTrue(result is DidResolutionResult.Failure.NotFound)
            assertEquals("DID did:x:y does not exist", (result as DidResolutionResult.Failure.NotFound).reason)
            assertEquals(
                DidErrorType.NOT_FOUND,
                result.resolutionMetadata.error?.type,
                "no structured error code was in the response, so the caller must synthesize one (§4)"
            )
            assertEquals("DID did:x:y does not exist", result.resolutionMetadata.error?.detail)
        } finally {
            server.stop(0)
        }
    }
}

