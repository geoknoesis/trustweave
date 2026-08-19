package org.trustweave.did.resolver

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [DefaultUniversalResolver]'s conformance to the DID Resolution 1.0 §12.1 HTTP
 * binding: the `Accept: application/did-resolution` request header, the HTTP 410 → `Deactivated`
 * mapping, the §12.1 status → error-type table, and `nextVersionId` propagation.
 *
 * Reuses the JDK `HttpServer` fixture shape already established in
 * [DefaultUniversalResolverTest] (`resolveDid surfaces a bare errorMessage as the NotFound
 * reason`): bind port 0, register the real `/1.0/identifiers/` context path that
 * [StandardUniversalResolverAdapter.buildResolveUrl] produces, stop the server in a `finally`
 * block, and drive the real `HttpClient.sendAsync` path. Kept in a separate file — per this
 * task's plan — rather than growing the existing (already large) test class further.
 */
class DefaultUniversalResolverCrTest {

    /**
     * Starts a fixture server that answers any request under the `/1.0/identifiers/` path with
     * [status] and [body]. An empty [body] sends no response body at all (JDK
     * `sendResponseHeaders(status, -1)`), exercising the "absent body" case. When
     * [capturedAccept] is supplied, the inbound `Accept` header is recorded into it.
     */
    private fun startServer(
        status: Int,
        body: String,
        capturedAccept: AtomicReference<String?>? = null
    ): HttpServer {
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        val bytes = body.toByteArray(Charsets.UTF_8)
        server.createContext("/1.0/identifiers/") { exchange ->
            capturedAccept?.set(exchange.requestHeaders.getFirst("Accept"))
            exchange.responseHeaders.add("Content-Type", "application/json")
            if (bytes.isEmpty()) {
                exchange.sendResponseHeaders(status, -1)
                exchange.responseBody.close()
            } else {
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        server.start()
        return server
    }

    @Test
    fun `resolve request asks for application did-resolution`() = runBlocking {
        val capturedAccept = AtomicReference<String?>(null)
        val body = """{"didDocument":{"id":"did:example:123"},"didDocumentMetadata":{}}"""
        val server = startServer(200, body, capturedAccept)
        try {
            val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

            resolver.resolveDid("did:example:123")

            assertEquals("application/did-resolution", capturedAccept.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `HTTP 410 maps to Deactivated`() = runBlocking {
        val body = """{"didDocument":null,"didDocumentMetadata":{"deactivated":true}}"""
        val server = startServer(410, body)
        try {
            val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

            val result = resolver.resolveDid("did:example:deactivated")

            assertTrue(result is DidResolutionResult.Deactivated, "expected Deactivated, got $result")
            assertTrue(result.documentMetadata.deactivated)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `HTTP 410 with absent body still produces Deactivated`() = runBlocking {
        val server = startServer(410, "")
        try {
            val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

            val result = resolver.resolveDid("did:example:deactivated-empty")

            assertTrue(result is DidResolutionResult.Deactivated, "expected Deactivated, got $result")
            assertTrue(result.documentMetadata.deactivated)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `HTTP 410 with malformed body still produces Deactivated`() = runBlocking {
        val server = startServer(410, "not-json{{{")
        try {
            val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

            val result = resolver.resolveDid("did:example:deactivated-malformed")

            assertTrue(result is DidResolutionResult.Deactivated, "expected Deactivated, got $result")
            assertTrue(result.documentMetadata.deactivated)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `HTTP 501 maps to METHOD_NOT_SUPPORTED`() = runBlocking {
        val server = startServer(501, "")
        try {
            val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

            val result = resolver.resolveDid("did:example:unsupported-method")

            assertEquals(DidErrorType.METHOD_NOT_SUPPORTED, result.errorType)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `HTTP 400 maps to INVALID_DID`() = runBlocking {
        val server = startServer(400, "")
        try {
            val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

            val result = resolver.resolveDid("did:example:bad")

            assertEquals(DidErrorType.INVALID_DID, result.errorType)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `legacy string error in 200 body is upgraded to NOT_FOUND`() = runBlocking {
        val body = """{"didResolutionMetadata":{"error":"notFound"},"didDocumentMetadata":{}}"""
        val server = startServer(200, body)
        try {
            val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

            val result = resolver.resolveDid("did:example:legacy-notfound")

            assertEquals(DidErrorType.NOT_FOUND, result.errorType)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `upstream nextVersionId lands in document metadata`() = runBlocking {
        val body = """{"didDocument":{"id":"did:example:versioned"},"didDocumentMetadata":{"versionId":"3","nextVersionId":"4"}}"""
        val server = startServer(200, body)
        try {
            val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

            val result = resolver.resolveDid("did:example:versioned")

            assertTrue(result is DidResolutionResult.Success, "expected Success, got $result")
            assertEquals("4", result.documentMetadata.nextVersionId)
        } finally {
            server.stop(0)
        }
    }
}
