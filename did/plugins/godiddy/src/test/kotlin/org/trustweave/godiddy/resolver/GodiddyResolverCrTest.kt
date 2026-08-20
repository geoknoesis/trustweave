package org.trustweave.godiddy.resolver

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.godiddy.GodiddyClient
import org.trustweave.godiddy.GodiddyConfig
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [GodiddyResolver]'s DID Resolution 1.0 §4.4 deactivated-DID handling and
 * `nextVersionId` propagation.
 *
 * [GodiddyClient] has no injectable HTTP engine seam (it builds a fixed Ktor CIO client), so
 * these tests spin a local JDK `HttpServer` — the same technique
 * `DefaultUniversalResolverCrTest` in `did-core` uses — and point [GodiddyConfig.baseUrl] at
 * loopback, driving the real Ktor client over the real network stack rather than adding a
 * mocking library.
 */
class GodiddyResolverCrTest {

    private fun startServer(body: String): HttpServer {
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        val bytes = body.toByteArray(Charsets.UTF_8)
        server.createContext("/1.0.0/universal-resolver/identifiers/") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return server
    }

    @Test
    fun `deactivated upstream body maps to Deactivated`() = runBlocking {
        val body = """{"didDocument":null,"didDocumentMetadata":{"deactivated":true}}"""
        val server = startServer(body)
        val client = GodiddyClient(GodiddyConfig(baseUrl = "http://localhost:${server.address.port}"))
        try {
            val resolver = GodiddyResolver(client)

            val result = resolver.resolveDid("did:example:deactivated")

            assertTrue(result is DidResolutionResult.Deactivated, "expected Deactivated, got $result")
            assertTrue(result.documentMetadata.deactivated)
        } finally {
            client.close()
            server.stop(0)
        }
    }

    // Critical fix: an upstream body carrying BOTH a non-null didDocument AND
    // didDocumentMetadata.deactivated: true must still yield Deactivated, never Success. The test
    // above uses "didDocument": null; every pre-existing deactivation test for this class did the
    // same, which is exactly why "document != null -> Success" being checked before
    // "deactivated -> Deactivated" survived undetected — a revoked DID's document would otherwise
    // flow into a caller as a plain Success and could go on to verify a credential and be cached.
    @Test
    fun `deactivated upstream body with a non-null document still maps to Deactivated`() = runBlocking {
        val body = """{"didDocument":{"id":"did:example:still-live"},"didDocumentMetadata":{"deactivated":true}}"""
        val server = startServer(body)
        val client = GodiddyClient(GodiddyConfig(baseUrl = "http://localhost:${server.address.port}"))
        try {
            val resolver = GodiddyResolver(client)

            val result = resolver.resolveDid("did:example:still-live")

            assertTrue(result is DidResolutionResult.Deactivated, "expected Deactivated, got $result")
            assertTrue(result.documentMetadata.deactivated)
        } finally {
            client.close()
            server.stop(0)
        }
    }

    @Test
    fun `upstream nextVersionId lands in document metadata`() = runBlocking {
        val body =
            """{"didDocument":{"id":"did:example:versioned"},"didDocumentMetadata":{"versionId":"3","nextVersionId":"4"}}"""
        val server = startServer(body)
        val client = GodiddyClient(GodiddyConfig(baseUrl = "http://localhost:${server.address.port}"))
        try {
            val resolver = GodiddyResolver(client)

            val result = resolver.resolveDid("did:example:versioned")

            assertTrue(result is DidResolutionResult.Success, "expected Success, got $result")
            assertEquals("4", result.documentMetadata.nextVersionId)
        } finally {
            client.close()
            server.stop(0)
        }
    }
}
